package com.cobblemonmousemenu.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * One ranked counter suggestion: one of the player's own Pokemon (from party or PC) scored against the
 * whole enemy trainer team.
 *
 * <p>Computed entirely on the server (the PC is server-only) by {@code MatchupCalculator}, then sent to
 * the client for display. Identifiers (species, move) are raw so the client can localise and render
 * portraits/type colours exactly like {@link TeamEntry}.</p>
 *
 * @param speciesId        resource id, e.g. {@code "cobblemon:garchomp"} (portrait + types)
 * @param name             display name (may include a nickname)
 * @param level            level
 * @param shiny            drives the shiny portrait variant
 * @param gender           "MALE"/"FEMALE"/"GENDERLESS"
 * @param aspects          Cobblemon aspects (portrait variant)
 * @param location         where the mon lives: {@code "Party"} or {@code "Box 3"}
 * @param bestMoveId       the recommended move id (highest single-target damage)
 * @param bestMoveType     that move's type id (for the chip colour), e.g. {@code "ground"}
 * @param bestDmgPct       best move's damage as a percent of the hardest-hit enemy's HP
 * @param bestTarget       display name of that hardest-hit enemy
 * @param worstIncomingPct the worst enemy hit on this mon, as a percent of its own HP
 * @param outspeedsAll     true if this mon is at least as fast as every enemy mon
 * @param score            overall lead score (higher is a better lead); drives ranking + verdict
 */
public record MatchupEntry(String speciesId, String name, int level, boolean shiny, String gender,
                           List<String> aspects, String location,
                           String bestMoveId, String bestMoveType, int bestDmgPct, String bestTarget,
                           int worstIncomingPct, boolean outspeedsAll, int score) {

    public static final StreamCodec<RegistryFriendlyByteBuf, MatchupEntry> STREAM_CODEC =
            StreamCodec.of(MatchupEntry::write, MatchupEntry::read);

    private static void write(RegistryFriendlyByteBuf buf, MatchupEntry e) {
        buf.writeUtf(e.speciesId());
        buf.writeUtf(e.name());
        buf.writeVarInt(e.level());
        buf.writeBoolean(e.shiny());
        buf.writeUtf(e.gender());
        buf.writeVarInt(e.aspects().size());
        for (String s : e.aspects()) {
            buf.writeUtf(s);
        }
        buf.writeUtf(e.location());
        buf.writeUtf(e.bestMoveId());
        buf.writeUtf(e.bestMoveType());
        buf.writeVarInt(e.bestDmgPct());
        buf.writeUtf(e.bestTarget());
        buf.writeVarInt(e.worstIncomingPct());
        buf.writeBoolean(e.outspeedsAll());
        buf.writeVarInt(e.score());
    }

    private static MatchupEntry read(RegistryFriendlyByteBuf buf) {
        String speciesId = buf.readUtf();
        String name = buf.readUtf();
        int level = buf.readVarInt();
        boolean shiny = buf.readBoolean();
        String gender = buf.readUtf();
        int n = buf.readVarInt();
        List<String> aspects = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            aspects.add(buf.readUtf());
        }
        String location = buf.readUtf();
        String bestMoveId = buf.readUtf();
        String bestMoveType = buf.readUtf();
        int bestDmgPct = buf.readVarInt();
        String bestTarget = buf.readUtf();
        int worstIncomingPct = buf.readVarInt();
        boolean outspeedsAll = buf.readBoolean();
        int score = buf.readVarInt();
        return new MatchupEntry(speciesId, name, level, shiny, gender, aspects, location,
                bestMoveId, bestMoveType, bestDmgPct, bestTarget, worstIncomingPct, outspeedsAll, score);
    }
}
