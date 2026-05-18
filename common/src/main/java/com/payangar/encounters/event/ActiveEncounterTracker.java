package com.payangar.encounters.event;

import com.payangar.encounters.Constants;
import com.payangar.encounters.event.cinematic.Cinematic;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Central registry for the two cross-event observability concerns:
 *
 * <ol>
 *   <li><b>Present cinematics</b> — who is running right now?
 *       Used for {@code maxConcurrent} / {@code minDistanceBetween} gating.
 *       API: {@link #activeCount(ServerLevel, String)} /
 *       {@link #nearestActiveDistance(ServerLevel, String, Vec3)}.</li>
 *   <li><b>Post-event cooldown</b> — when did the last instance end?
 *       API: {@link #cooldownElapsed(ServerLevel, String, int)}. The timestamp
 *       is bumped automatically by {@link #unregister(Cinematic)}.</li>
 * </ol>
 *
 * <p>The two concerns are exposed via deliberately separate APIs. An event
 * that wires a concurrency cap will only call {@code activeCount}; one with a
 * cooldown will only call {@code cooldownElapsed}; one with both calls both.
 * Code decides which concerns to consume — the config only carries the
 * tunings of what the code has wired in.</p>
 *
 * <p>The cooldown ledger is keyed on {@code (level, eventId)} so cross-dim
 * cooldowns don't bleed into each other. Entries are dropped on
 * {@link #clearLevel(ServerLevel)} and {@link #clear()}.</p>
 *
 * <p><b>Performance.</b> With a realistic upper bound of ~20 active
 * cinematics across all event families, a linear scan over a single
 * {@link CopyOnWriteArraySet} is strictly faster than maintaining bucketed
 * indices: no hash overhead, no pointer chasing, cache-friendly. The cooldown
 * ledger is a small {@link ConcurrentHashMap} — at most one entry per
 * (level × eventId), trivial cost.</p>
 */
public final class ActiveEncounterTracker {

    private static final CopyOnWriteArraySet<Cinematic> ACTIVE = new CopyOnWriteArraySet<>();

    /** Last-end tick per (level, eventId). Populated by {@link #unregister}. */
    private static final ConcurrentHashMap<CooldownKey, Long> LAST_END_TICK = new ConcurrentHashMap<>();

    private record CooldownKey(ResourceKey<Level> dim, String eventId) {}

    private ActiveEncounterTracker() {}

    // ---------- Presence registry ----------

    /**
     * Add {@code encounter} to the active set. Callers must ensure
     * {@link #unregister} is invoked on every cleanup path — including
     * exceptional ones — to avoid leaking a reference into the set.
     */
    public static void register(Cinematic encounter) {
        ACTIVE.add(encounter);
    }

    /**
     * Drop {@code encounter} from the active set and bump the post-event
     * cooldown timestamp for its {@code (level, eventId)} pair. Events that
     * don't read the cooldown stay unaffected — the write is invisible to them.
     */
    public static void unregister(Cinematic encounter) {
        ACTIVE.remove(encounter);
        ServerLevel level = encounter.level();
        if (level == null) return;
        CooldownKey key = new CooldownKey(level.dimension(), encounter.eventId());
        LAST_END_TICK.put(key, level.getGameTime());
    }

    /** Drops every tracked encounter and every cooldown timestamp. Used at server stop. */
    public static void clear() {
        int n = ACTIVE.size();
        ACTIVE.clear();
        LAST_END_TICK.clear();
        if (n > 0) Constants.LOG.debug("ActiveEncounterTracker: cleared {} encounter(s) at shutdown", n);
    }

    /** Drops every tracked encounter and cooldown timestamp bound to {@code level}. */
    public static void clearLevel(ServerLevel level) {
        int before = ACTIVE.size();
        ACTIVE.removeIf(c -> c.level() == level);
        int removed = before - ACTIVE.size();
        LAST_END_TICK.keySet().removeIf(k -> k.dim().equals(level.dimension()));
        if (removed > 0) Constants.LOG.debug("ActiveEncounterTracker: cleared {} encounter(s) on level {}", removed, level.dimension().location());
    }

    // ---------- Concern 1: present cinematics ----------

    /**
     * Number of tracked encounters with {@code eventId} currently bound to
     * {@code level}. Standard call for per-event concurrency caps.
     */
    public static int activeCount(ServerLevel level, String eventId) {
        int count = 0;
        for (Cinematic c : ACTIVE) {
            if (c.level() == level && eventId.equals(c.eventId())) count++;
        }
        return count;
    }

    /**
     * Distance from {@code pos} to the nearest tracked encounter with
     * {@code eventId} on the same level. Returns {@link Double#MAX_VALUE} when
     * no encounter of that family is tracked on the level — callers can
     * compare against a threshold without a null check.
     */
    public static double nearestActiveDistance(ServerLevel level, String eventId, Vec3 pos) {
        double minSqr = Double.MAX_VALUE;
        for (Cinematic c : ACTIVE) {
            if (c.level() != level || !eventId.equals(c.eventId())) continue;
            double d = c.anchor().distanceToSqr(pos);
            if (d < minSqr) minSqr = d;
        }
        return minSqr == Double.MAX_VALUE ? Double.MAX_VALUE : Math.sqrt(minSqr);
    }

    /**
     * Number of tracked encounters bound to {@code level}, all event families
     * combined. Diagnostic helper — per-event gating should use
     * {@link #activeCount(ServerLevel, String)}.
     */
    public static int activeCount(ServerLevel level) {
        int count = 0;
        for (Cinematic c : ACTIVE) {
            if (c.level() == level) count++;
        }
        return count;
    }

    /**
     * Distance from {@code pos} to the nearest tracked encounter on the same
     * level, all event families combined. Returns {@link Double#MAX_VALUE}
     * when no encounter is tracked on the level. Diagnostic helper.
     */
    public static double nearestActiveDistance(ServerLevel level, Vec3 pos) {
        double minSqr = Double.MAX_VALUE;
        for (Cinematic c : ACTIVE) {
            if (c.level() != level) continue;
            double d = c.anchor().distanceToSqr(pos);
            if (d < minSqr) minSqr = d;
        }
        return minSqr == Double.MAX_VALUE ? Double.MAX_VALUE : Math.sqrt(minSqr);
    }

    // ---------- Concern 2: post-event cooldown ----------

    /**
     * Whether at least {@code cooldownTicks} server ticks have elapsed since
     * the last {@code eventId} encounter ended on {@code level}. Returns
     * {@code true} when no encounter of that family has ever ended on the
     * level (trivially satisfied at server start).
     *
     * <p>An event whose scanner does not wire in a cooldown simply does not
     * call this — the timestamp is still written by {@link #unregister} but
     * stays inert.</p>
     */
    public static boolean cooldownElapsed(ServerLevel level, String eventId, int cooldownTicks) {
        if (cooldownTicks <= 0) return true;
        Long last = LAST_END_TICK.get(new CooldownKey(level.dimension(), eventId));
        if (last == null) return true;
        return (level.getGameTime() - last) >= cooldownTicks;
    }
}
