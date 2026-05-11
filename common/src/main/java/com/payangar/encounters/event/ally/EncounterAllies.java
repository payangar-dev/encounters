package com.payangar.encounters.event.ally;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.Collection;
import java.util.UUID;

/**
 * Allied-group bookkeeping for encounter mobs. Each group spawned by an event
 * shares one scoreboard team named {@code encounters_g_<shortId>} with
 * {@code allowFriendlyFire = false}.
 *
 * <p>Vanilla {@link Entity#isAlliedTo(Entity)} consults the team, so
 * {@code LivingEntity#canAttack} naturally returns {@code false} between two
 * group mates — no mixin needed. The same team also makes Iron's Spells'
 * {@code DamageSources#isFriendlyFireBetween} skip damage between mobs and the
 * encounter-owned magma-bomb caster.</p>
 *
 * <p>Groups stay separated by id on purpose: two concurrent encounters remain
 * hostile to each other, preserving emergent cross-group fights.</p>
 */
public final class EncounterAllies {

    public static final String NAME_PREFIX = "encounters_g_";

    private EncounterAllies() {}

    /** Mints a fresh, unique team name. */
    public static String newGroupName() {
        return NAME_PREFIX + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Looks up the named team or creates it with {@code allowFriendlyFire=false}
     * on first use. Idempotent.
     */
    public static PlayerTeam ensureTeam(ServerLevel level, String teamName) {
        Scoreboard sb = level.getScoreboard();
        PlayerTeam team = sb.getPlayerTeam(teamName);
        if (team == null) {
            team = sb.addPlayerTeam(teamName);
            team.setAllowFriendlyFire(false);
        }
        return team;
    }

    /** Adds {@code entity} to the named team. Creates the team on first call. */
    public static void addToGroup(ServerLevel level, String teamName, Entity entity) {
        if (entity == null) return;
        PlayerTeam team = ensureTeam(level, teamName);
        level.getScoreboard().addPlayerToTeam(entity.getScoreboardName(), team);
    }

    /** Convenience: mints a fresh team and adds every member. Returns the team name. */
    public static String formGroup(ServerLevel level, Collection<? extends Entity> members) {
        String name = newGroupName();
        if (members.isEmpty()) return name;
        PlayerTeam team = ensureTeam(level, name);
        Scoreboard sb = level.getScoreboard();
        for (Entity m : members) {
            sb.addPlayerToTeam(m.getScoreboardName(), team);
        }
        return name;
    }

    /** Removes the team and detaches every member from it. Safe to call when the team no longer exists. */
    public static void disbandGroup(ServerLevel level, String teamName) {
        Scoreboard sb = level.getScoreboard();
        PlayerTeam team = sb.getPlayerTeam(teamName);
        if (team != null) {
            sb.removePlayerTeam(team);
        }
    }

    /** True iff {@code entity} belongs to any encounter team — i.e. was spawned by an event. */
    public static boolean isEncounterMob(Entity entity) {
        if (entity == null) return false;
        Team team = entity.getTeam();
        return team != null && team.getName().startsWith(NAME_PREFIX);
    }

    /**
     * True iff {@code a} and {@code b} share the same encounter team. Used by
     * the {@code setTarget} / {@code canAttack} mixins to short-circuit any
     * targeting between group members.
     *
     * <p>Vanilla {@code LivingEntity#canAttack} does <em>not</em> consult
     * {@code isAlliedTo}, so without these short-circuits the goal-driven
     * AI of vanilla mobs (e.g. {@code WitherSkeleton}'s
     * {@code NearestAttackableTargetGoal<AbstractPiglin>}) would happily
     * acquire a same-team target and start attacking it — friendly fire
     * being blocked by the team only at the {@code hurt} stage doesn't
     * stop the engagement, just the damage, leading to mobs swinging
     * uselessly at each other.</p>
     */
    public static boolean areAllies(Entity a, Entity b) {
        if (a == null || b == null || a == b) return false;
        Team ta = a.getTeam();
        if (ta == null) return false;
        Team tb = b.getTeam();
        return ta == tb && ta.getName().startsWith(NAME_PREFIX);
    }
}
