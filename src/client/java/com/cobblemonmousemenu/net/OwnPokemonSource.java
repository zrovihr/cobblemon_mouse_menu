package com.cobblemonmousemenu.net;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemonmousemenu.CobblemonMouseMenuClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.UUID;

/**
 * The player's OWN sent-out Pokemon in the world. Picking one and clicking opens that Pokemon's
 * Cobblemon summary screen (read entirely client-side; no packet).
 *
 * <p>{@link PokemonEntity} is a {@code Mob}, so it already appears in the projection-pick scan in
 * {@link TrainerTeamClient}. This source just claims the ones owned by the local player.</p>
 */
public final class OwnPokemonSource implements TrainerSource {

    @Override
    public boolean isTrainer(Entity entity) {
        if (!(entity instanceof PokemonEntity pe)) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return false;
        }
        UUID owner = pe.getOwnerUUID();
        return owner != null && owner.equals(client.player.getUUID());
    }

    @Override
    public boolean canShow(Entity entity) {
        return entity instanceof PokemonEntity && CobblemonClient.INSTANCE != null;
    }

    @Override
    public void show(Entity entity) {
        if (!(entity instanceof PokemonEntity pe) || CobblemonClient.INSTANCE == null) {
            return;
        }
        Pokemon target = pe.getPokemon();
        if (target == null) {
            return;
        }

        // Sent-out Pokemon stay in the party, so match by uuid and open the summary at that slot.
        List<Pokemon> party = CobblemonClient.INSTANCE.getStorage().getParty().getSlots();
        for (int i = 0; i < party.size(); i++) {
            Pokemon p = party.get(i);
            if (p != null && p.getUuid().equals(target.getUuid())) {
                CobblemonMouseMenuClient.openSummaryForSlot(i);
                return;
            }
        }

        // Fallback (e.g. pastured / not in party): open a one-Pokemon summary directly.
        CobblemonMouseMenuClient.closeMouseMenu();
        Minecraft.getInstance().mouseHandler.grabMouse();
        Summary.Companion.open(List.of(target), true, 0);
    }

    @Override
    public String hoverLabel() {
        return "Click to inspect";
    }
}
