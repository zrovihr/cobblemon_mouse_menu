package com.cobblemonmousemenu.net;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.PokemonStats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemonmousemenu.CobblemonMouseMenu;
import com.cobblemonmousemenu.matchup.MatchupCalculator;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Common (both-sides) networking setup for the trainer-team feature.
 *
 * Runs from the main entrypoint, so it executes on a dedicated server AND in the singleplayer
 * integrated server. Registering the payload TYPES here is what makes Fabric advertise the
 * channels on connect, which in turn is what lets the client's {@code canSend(...)} check work.
 */
public final class TrainerTeamNetworking {

    private TrainerTeamNetworking() {
    }

    public static void registerCommon() {
        // Declare the payload types on both directions. Advertised to peers on connect.
        PayloadTypeRegistry.playC2S().register(RequestTrainerTeamPayload.TYPE, RequestTrainerTeamPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PartyItemSwapPayload.TYPE, PartyItemSwapPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestMatchupPayload.TYPE, RequestMatchupPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(TrainerTeamPayload.TYPE, TrainerTeamPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(MatchupPayload.TYPE, MatchupPayload.STREAM_CODEC);

        // Server side: unified take/give held item on the player's OWN party Pokemon.
        ServerPlayNetworking.registerGlobalReceiver(PartyItemSwapPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            Pokemon pokemon = party.get(payload.slot());
            if (pokemon == null) {
                return;
            }

            ItemStack hand = player.getMainHandItem();
            ItemStack previous;
            if (hand.isEmpty()) {
                // Nothing in hand: take the held item back.
                previous = pokemon.removeHeldItem();
            } else {
                // Give one of the held stack to the Pokemon (decrements hand) and reclaim the old item.
                previous = pokemon.swapHeldItem(hand, true, true);
            }

            if (!previous.isEmpty() && !player.getInventory().add(previous)) {
                player.drop(previous, false);
            }
            CobblemonMouseMenu.LOGGER.debug("[PartyItemSwap] slot {} for {} -> returned {}",
                    payload.slot(), player.getGameProfile().getName(), previous);
        });

        // Server side: answer a request by reading the NPC's (server-only) party and sending it back.
        // Fabric invokes play payload handlers on the server thread, so entity lookup here is safe.
        ServerPlayNetworking.registerGlobalReceiver(RequestTrainerTeamPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            ServerLevel level = player.serverLevel();

            Entity entity = level.getEntity(payload.entityId());
            // Only reveal NPC trainers. Never expose another player's hidden party.
            if (!(entity instanceof NPCEntity npc)) {
                return;
            }

            NPCPartyStore party = npc.getParty();
            List<TeamEntry> team = new ArrayList<>();
            if (party != null) {
                // PartyStore is Iterable<Pokemon> and already skips empty slots.
                for (Pokemon pokemon : party) {
                    team.add(toEntry(pokemon));
                }
            }

            // Cobblemon NPCs don't carry an RCT-style battle format; leave it blank.
            ServerPlayNetworking.send(player, new TrainerTeamPayload(payload.entityId(), "", team));
            CobblemonMouseMenu.LOGGER.debug("[TrainerTeam] served {} mon(s) for NPC {} to {}",
                    team.size(), payload.entityId(), player.getGameProfile().getName());
        });

        // Server side: rank the player's OWN roster (party + PC) against the enemy team the client
        // sent up. The PC only exists server-side, so this read must happen here; we reply with just
        // the ranked top results rather than the player's whole box.
        ServerPlayNetworking.registerGlobalReceiver(RequestMatchupPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();

            MatchupCalculator calc = new MatchupCalculator(payload.enemyTeam());
            if (!calc.hasEnemies()) {
                ServerPlayNetworking.send(player, new MatchupPayload(payload.entityId(), new ArrayList<>()));
                return;
            }

            // Optional level cap (e.g. an RCT/Cobbleverse battle restriction): 0 = no cap.
            int maxLevel = payload.maxLevel();

            // Party first (these are usable right now), then every box in the PC.
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            for (Pokemon pokemon : party) {
                if (maxLevel > 0 && pokemon.getLevel() > maxLevel) {
                    continue;
                }
                calc.consider(pokemon, "Party");
            }
            PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
            for (PCBox box : pc.getBoxes()) {
                String label = "Box " + (box.getBoxNumber() + 1);
                for (Pokemon pokemon : box) {
                    if (maxLevel > 0 && pokemon.getLevel() > maxLevel) {
                        continue;
                    }
                    calc.consider(pokemon, label);
                }
            }

            List<MatchupEntry> ranked = calc.rank(12);
            ServerPlayNetworking.send(player, new MatchupPayload(payload.entityId(), ranked));
            CobblemonMouseMenu.LOGGER.debug("[Matchup] ranked {} counter(s) vs {} enemy mon(s) for {}",
                    ranked.size(), payload.enemyTeam().size(), player.getGameProfile().getName());
        });
    }

    /** Flatten a live Cobblemon Pokemon into the client-safe card model. */
    private static TeamEntry toEntry(Pokemon pokemon) {
        List<String> moveIds = new ArrayList<>();
        for (Move move : pokemon.getMoveSet().getMoves()) {
            moveIds.add(move.getName());
        }
        ItemStack held = pokemon.heldItem();
        String heldName = held.isEmpty() ? "" : held.getHoverName().getString();

        return new TeamEntry(
                pokemon.getSpecies().getResourceIdentifier().toString(),
                pokemon.getDisplayName(false).getString(),
                pokemon.getLevel(),
                pokemon.getShiny(),
                pokemon.getGender().name(),
                pokemon.getAbility().getName(),
                pokemon.getNature().getName().getPath(),
                heldName,
                moveIds,
                new ArrayList<>(pokemon.getAspects()),
                statList(pokemon.getIvs()),
                statList(pokemon.getEvs())
        );
    }

    /** Read a Cobblemon stat block into the canonical 6-int order (HP, Atk, Def, SpA, SpD, Spe). */
    private static List<Integer> statList(PokemonStats stats) {
        List<Integer> list = new ArrayList<>(6);
        list.add(stats.getOrDefault(Stats.HP));
        list.add(stats.getOrDefault(Stats.ATTACK));
        list.add(stats.getOrDefault(Stats.DEFENCE));
        list.add(stats.getOrDefault(Stats.SPECIAL_ATTACK));
        list.add(stats.getOrDefault(Stats.SPECIAL_DEFENCE));
        list.add(stats.getOrDefault(Stats.SPEED));
        return list;
    }
}
