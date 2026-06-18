package com.cobblemonmousemenu.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * One trainer-team member, normalised across sources (Cobblemon NPC packet vs RCT client data) so
 * the screen doesn't care where it came from.
 *
 * <p>Identifiers (species, moves, ability, nature) are sent raw and localised/resolved on the
 * client, so things like move types/colours and translated names work regardless of source.</p>
 *
 * <p>{@code ivs}/{@code evs} are 6 ints in stat order: HP, Attack, Defence, Sp.Atk, Sp.Def, Speed.
 * Final stats are computed on the client from the species base stats + level + nature.</p>
 *
 * @param speciesId resource id, e.g. {@code "cobblemon:gengar"} (for portrait + types)
 * @param name      display name (may include a nickname)
 * @param ability   ability id, e.g. {@code "levitate"} (client localises)
 * @param nature    nature id, e.g. {@code "modest"} (client localises)
 * @param heldItem  display string, empty if none
 * @param moveIds   move ids, e.g. {@code "shadowball"} (client resolves name + type)
 * @param aspects   Cobblemon aspects (drives portrait variant, incl. shiny)
 * @param ivs       individual values, 6 ints (HP, Atk, Def, SpA, SpD, Spe)
 * @param evs       effort values, 6 ints (HP, Atk, Def, SpA, SpD, Spe)
 */
public record TeamEntry(String speciesId, String name, int level, boolean shiny, String gender,
                        String ability, String nature, String heldItem,
                        List<String> moveIds, List<String> aspects,
                        List<Integer> ivs, List<Integer> evs) {

    public static final StreamCodec<RegistryFriendlyByteBuf, TeamEntry> STREAM_CODEC =
            StreamCodec.of(TeamEntry::write, TeamEntry::read);

    private static void write(RegistryFriendlyByteBuf buf, TeamEntry e) {
        buf.writeUtf(e.speciesId());
        buf.writeUtf(e.name());
        buf.writeVarInt(e.level());
        buf.writeBoolean(e.shiny());
        buf.writeUtf(e.gender());
        buf.writeUtf(e.ability());
        buf.writeUtf(e.nature());
        buf.writeUtf(e.heldItem());
        writeStrings(buf, e.moveIds());
        writeStrings(buf, e.aspects());
        writeInts(buf, e.ivs());
        writeInts(buf, e.evs());
    }

    private static TeamEntry read(RegistryFriendlyByteBuf buf) {
        String speciesId = buf.readUtf();
        String name = buf.readUtf();
        int level = buf.readVarInt();
        boolean shiny = buf.readBoolean();
        String gender = buf.readUtf();
        String ability = buf.readUtf();
        String nature = buf.readUtf();
        String heldItem = buf.readUtf();
        List<String> moveIds = readStrings(buf);
        List<String> aspects = readStrings(buf);
        List<Integer> ivs = readInts(buf);
        List<Integer> evs = readInts(buf);
        return new TeamEntry(speciesId, name, level, shiny, gender, ability, nature, heldItem,
                moveIds, aspects, ivs, evs);
    }

    private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> list) {
        buf.writeVarInt(list.size());
        for (String s : list) {
            buf.writeUtf(s);
        }
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(buf.readUtf());
        }
        return list;
    }

    private static void writeInts(RegistryFriendlyByteBuf buf, List<Integer> list) {
        buf.writeVarInt(list.size());
        for (int v : list) {
            buf.writeVarInt(v);
        }
    }

    private static List<Integer> readInts(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Integer> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(buf.readVarInt());
        }
        return list;
    }
}
