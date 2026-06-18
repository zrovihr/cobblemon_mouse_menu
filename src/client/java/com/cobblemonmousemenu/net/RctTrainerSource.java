package com.cobblemonmousemenu.net;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.gitlab.srcmc.rctapi.api.models.PokemonModel;
import com.gitlab.srcmc.rctmod.api.RCTMod;
import com.gitlab.srcmc.rctmod.api.data.pack.TrainerMobData;
import com.gitlab.srcmc.rctmod.api.data.pack.TrainerTeam;
import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Radical Cobblemon Trainers ({@code rctmod}) overworld trainers.
 *
 * <p>RCT syncs its trainer definitions to the client (this is how RCT's own {@code TrainerInfoWidget}
 * shows teams), so the whole read is client-side — no packet. This class is the ONLY place that
 * references RCT types; it must never be loaded when {@code rctmod} is absent (see
 * {@link TrainerTeamClient#init()} which guards construction with isModLoaded).</p>
 */
public final class RctTrainerSource implements TrainerSource {

    @Override
    public boolean isTrainer(Entity entity) {
        return entity instanceof TrainerMob;
    }

    @Override
    public boolean canShow(Entity entity) {
        return team(entity) != null;
    }

    @Override
    public void show(Entity entity) {
        TrainerTeam team = team(entity);
        if (team == null) {
            return;
        }
        Component name = team.getName().getComponent(); // rctapi util.Text -> MutableComponent
        String format = battleFormat(team);
        List<TeamEntry> entries = new ArrayList<>();
        for (PokemonModel m : team.getTeam()) {
            List<String> moves = m.getMoveset() == null ? List.of() : new ArrayList<>(m.getMoveset());
            List<String> aspects = m.getAspects() == null ? new ArrayList<>() : new ArrayList<>(m.getAspects());
            String[] held = m.getHeldItems();
            String heldItem = (held != null && held.length > 0 && held[0] != null) ? held[0] : "";
            entries.add(new TeamEntry(
                    m.getSpecies(),
                    displayName(m.getSpecies()),
                    m.getLevel(),
                    m.isShiny(),
                    m.getGender() == null ? "" : m.getGender(),
                    m.getAbility() == null ? "" : m.getAbility(),
                    m.getNature() == null ? "" : m.getNature(),
                    heldItem,
                    moves,
                    aspects,
                    stats(m.getIVs()),
                    stats(m.getEVs())
            ));
        }
        TrainerTeamClient.showTeam(entity.getId(), name, format, entries);
    }

    private static List<Integer> stats(PokemonModel.StatsModel s) {
        if (s == null) {
            return List.of(0, 0, 0, 0, 0, 0);
        }
        return List.of(s.getHP(), s.getAtk(), s.getDef(), s.getSpA(), s.getSpD(), s.getSpe());
    }

    /** Map RCT's BattleFormat enum to a friendly "1v1"/"2v2" label. */
    private static String battleFormat(TrainerTeam team) {
        if (team.getBattleFormat() == null) {
            return "";
        }
        String n = team.getBattleFormat().name();
        if (n.contains("SINGLE")) return "1v1";
        if (n.contains("DOUBLE")) return "2v2";
        if (n.contains("TRIPLE")) return "3v3";
        if (n.contains("MULTI")) return "2v2";
        return n;
    }

    private static TrainerTeam team(Entity entity) {
        if (!(entity instanceof TrainerMob mob)) {
            return null;
        }
        TrainerMobData data = RCTMod.getInstance().getTrainerManager().getData(mob.getTrainerId());
        return data == null ? null : data.getTrainerTeam();
    }

    /** RCT stores species as a Cobblemon id string (e.g. "pikachu"); resolve to a display name. */
    private static String displayName(String species) {
        if (species == null || species.isEmpty()) {
            return "?";
        }
        String key = species.contains(":") ? species.substring(species.indexOf(':') + 1) : species;
        Species s = PokemonSpecies.INSTANCE.getByName(key);
        if (s != null) {
            return s.getTranslatedName().getString();
        }
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }
}
