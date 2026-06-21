package com.cobblemonmousemenu.matchup;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemonmousemenu.net.MatchupEntry;
import com.cobblemonmousemenu.net.TeamEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Scores the player's own Pokemon as counters against an enemy trainer team — a lightweight, in-mod
 * approximation of a Showdown-style damage calc, scoped to type matchup + base-stat damage.
 *
 * <p>Reuses Cobblemon's own data so it never drifts from the running game: the type chart comes from
 * {@link AIUtility#getDamageMultiplier} and the damage math mirrors the formula Cobblemon's battle AI
 * uses ({@code StrongBattleAI.calculateDamage}) — {@code (((2L/5)+2)*power*atk/def)/50 + 2}, then STAB
 * and type effectiveness. Held items, abilities, weather and screens are intentionally out of scope.</p>
 *
 * <p>Runs server-side only (the PC is server-only). Build it with the enemy team, feed it each of the
 * player's mons via {@link #consider}, then {@link #rank} the result.</p>
 */
public final class MatchupCalculator {

    private static final Stat[] STAT_ORDER = {
            Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    };

    /** Cap a single matchup's offence/defence contribution so a big OHKO roll can't dominate the score. */
    private static final double CONTRIB_CAP = 150.0;

    private final List<Combatant> enemies = new ArrayList<>();
    private final List<MatchupEntry> results = new ArrayList<>();

    public MatchupCalculator(List<TeamEntry> enemyTeam) {
        for (TeamEntry e : enemyTeam) {
            Combatant c = fromEntry(e);
            if (c != null) {
                enemies.add(c);
            }
        }
    }

    /** True if there's anything to score against (no enemies = nothing to do). */
    public boolean hasEnemies() {
        return !enemies.isEmpty();
    }

    /** Score one of the player's mons (live Pokemon) and record a ranked entry for it. */
    public void consider(Pokemon mon, String location) {
        Combatant me = fromPokemon(mon);
        if (me == null || enemies.isEmpty()) {
            return;
        }

        double bestDmgPct = 0;
        MoveInfo bestMove = null;
        String bestTarget = "";
        double worstIncoming = 0;
        boolean outspeedsAll = true;
        double scoreSum = 0;

        for (Combatant enemy : enemies) {
            // Offence: our hardest-hitting move on this enemy.
            double myBest = 0;
            MoveInfo myBestMove = null;
            for (MoveInfo m : me.moves) {
                double pct = damagePercent(me, m, enemy);
                if (pct > myBest) {
                    myBest = pct;
                    myBestMove = m;
                }
            }
            // Defence: the enemy's hardest-hitting move on us.
            double theirBest = 0;
            for (MoveInfo m : enemy.moves) {
                theirBest = Math.max(theirBest, damagePercent(enemy, m, me));
            }

            if (myBest > bestDmgPct) {
                bestDmgPct = myBest;
                bestMove = myBestMove;
                bestTarget = enemy.name;
            }
            worstIncoming = Math.max(worstIncoming, theirBest);
            if (me.speed < enemy.speed) {
                outspeedsAll = false;
            }
            scoreSum += Math.min(myBest, CONTRIB_CAP) - Math.min(theirBest, CONTRIB_CAP);
        }

        double score = scoreSum / enemies.size();
        if (outspeedsAll) {
            score += 10; // moving first is worth a small, flat edge
        }

        results.add(new MatchupEntry(
                mon.getSpecies().getResourceIdentifier().toString(),
                mon.getDisplayName(false).getString(),
                mon.getLevel(),
                mon.getShiny(),
                mon.getGender().name(),
                new ArrayList<>(mon.getAspects()),
                location,
                bestMove == null ? "" : bestMove.id,
                bestMove == null ? "" : bestMove.type.getName(),
                (int) Math.round(bestDmgPct),
                bestTarget,
                (int) Math.round(worstIncoming),
                outspeedsAll,
                (int) Math.round(score)
        ));
    }

    /** Best leads first; trimmed to {@code limit}. */
    public List<MatchupEntry> rank(int limit) {
        results.sort(Comparator.comparingInt(MatchupEntry::score).reversed());
        return results.size() > limit ? new ArrayList<>(results.subList(0, limit)) : results;
    }

    // ---------------------------------------------------------------------------------------------
    // Damage
    // ---------------------------------------------------------------------------------------------

    /** Estimated damage of {@code move} from {@code attacker} onto {@code defender}, as a % of the defender's max HP. */
    private static double damagePercent(Combatant attacker, MoveInfo move, Combatant defender) {
        if (defender.hp <= 0) {
            return 0;
        }
        double typeMult = 1.0;
        for (ElementalType dt : defender.types) {
            typeMult *= AIUtility.INSTANCE.getDamageMultiplier(move.type, dt);
        }
        if (typeMult <= 0) {
            return 0; // immune
        }
        double ratio = move.physical
                ? (double) attacker.attack / Math.max(1, defender.defence)
                : (double) attacker.specialAttack / Math.max(1, defender.specialDefence);
        double base = (((2.0 * attacker.level / 5.0) + 2.0) * move.power * ratio) / 50.0 + 2.0;
        double stab = move.stab ? 1.5 : 1.0;
        double dmg = base * stab * typeMult;
        return dmg / defender.hp * 100.0;
    }

    // ---------------------------------------------------------------------------------------------
    // Combatant construction
    // ---------------------------------------------------------------------------------------------

    private static final class Combatant {
        String name;
        int level;
        List<ElementalType> types;
        int hp, attack, defence, specialAttack, specialDefence, speed;
        List<MoveInfo> moves;
    }

    private static final class MoveInfo {
        String id;
        ElementalType type;
        double power;
        boolean physical;
        boolean stab;
    }

    /** Build from a live (server-side) Pokemon — stats already include nature/form. */
    private static Combatant fromPokemon(Pokemon p) {
        Combatant c = new Combatant();
        c.name = p.getDisplayName(false).getString();
        c.level = p.getLevel();
        c.types = new ArrayList<>();
        for (ElementalType t : p.getTypes()) {
            c.types.add(t);
        }
        c.hp = p.getHp();
        c.attack = p.getAttack();
        c.defence = p.getDefence();
        c.specialAttack = p.getSpecialAttack();
        c.specialDefence = p.getSpecialDefence();
        c.speed = p.getSpeed();
        c.moves = new ArrayList<>();
        for (Move m : p.getMoveSet().getMoves()) {
            MoveInfo mi = moveInfo(m.getName(), c.types);
            if (mi != null) {
                c.moves.add(mi);
            }
        }
        return c;
    }

    /** Build from a client-supplied enemy {@link TeamEntry} — recompute final stats from base+IV+EV+nature. */
    private static Combatant fromEntry(TeamEntry e) {
        Species species = resolveSpecies(e.speciesId());
        if (species == null) {
            return null;
        }
        Combatant c = new Combatant();
        c.name = e.name();
        c.level = e.level();
        c.types = new ArrayList<>();
        for (ElementalType t : species.getTypes()) {
            c.types.add(t);
        }

        int[] base = new int[6];
        for (int i = 0; i < 6; i++) {
            base[i] = species.getBaseStats().getOrDefault(STAT_ORDER[i], 0);
        }
        Nature nature = e.nature() == null || e.nature().isEmpty() ? null : Natures.INSTANCE.getNature(e.nature());
        c.hp = finalStat(0, base[0], statAt(e.ivs(), 0), statAt(e.evs(), 0), e.level(), nature);
        c.attack = finalStat(1, base[1], statAt(e.ivs(), 1), statAt(e.evs(), 1), e.level(), nature);
        c.defence = finalStat(2, base[2], statAt(e.ivs(), 2), statAt(e.evs(), 2), e.level(), nature);
        c.specialAttack = finalStat(3, base[3], statAt(e.ivs(), 3), statAt(e.evs(), 3), e.level(), nature);
        c.specialDefence = finalStat(4, base[4], statAt(e.ivs(), 4), statAt(e.evs(), 4), e.level(), nature);
        c.speed = finalStat(5, base[5], statAt(e.ivs(), 5), statAt(e.evs(), 5), e.level(), nature);

        c.moves = new ArrayList<>();
        for (String moveId : e.moveIds()) {
            MoveInfo mi = moveInfo(moveId, c.types);
            if (mi != null) {
                c.moves.add(mi);
            }
        }
        return c;
    }

    /** Resolve a move id into damage-relevant fields; returns null for status / zero-power moves. */
    private static MoveInfo moveInfo(String moveId, List<ElementalType> attackerTypes) {
        MoveTemplate tmpl = Moves.INSTANCE.getByNameOrDummy(moveId);
        double power = tmpl.getPower();
        String category = tmpl.getDamageCategory().getName();
        boolean physical = "physical".equalsIgnoreCase(category);
        boolean special = "special".equalsIgnoreCase(category);
        if (power <= 0 || (!physical && !special)) {
            return null; // status move or non-damaging — irrelevant to the damage estimate
        }
        MoveInfo mi = new MoveInfo();
        mi.id = moveId;
        mi.type = tmpl.getElementalType();
        mi.power = power;
        mi.physical = physical;
        mi.stab = false;
        for (ElementalType t : attackerTypes) {
            if (Objects.equals(t.getName(), mi.type.getName())) {
                mi.stab = true;
                break;
            }
        }
        return mi;
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers (mirror TrainerTeamScreen.computeStat so both sides agree)
    // ---------------------------------------------------------------------------------------------

    /** Standard Cobblemon/Pokemon stat formula. i==0 is HP. */
    private static int finalStat(int i, int base, int iv, int ev, int level, Nature nature) {
        int common = (int) Math.floor((2.0 * base + iv + Math.floor(ev / 4.0)) * level / 100.0);
        if (i == 0) {
            return common + level + 10;
        }
        double mod = 1.0;
        if (nature != null) {
            if (Objects.equals(nature.getIncreasedStat(), STAT_ORDER[i])) mod = 1.1;
            else if (Objects.equals(nature.getDecreasedStat(), STAT_ORDER[i])) mod = 0.9;
        }
        return (int) Math.floor((common + 5) * mod);
    }

    private static int statAt(List<Integer> list, int i) {
        return (list != null && i < list.size()) ? list.get(i) : 0;
    }

    private static Species resolveSpecies(String speciesId) {
        if (speciesId == null || speciesId.isEmpty()) {
            return null;
        }
        String key = speciesId.contains(":") ? speciesId.substring(speciesId.indexOf(':') + 1) : speciesId;
        return PokemonSpecies.INSTANCE.getByName(key.toLowerCase(Locale.ROOT));
    }
}
