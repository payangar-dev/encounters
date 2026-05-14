package com.payangar.encounters.event;

import com.payangar.encounters.Constants;
import com.payangar.encounters.event.cinematic.Cinematic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Tracks multi-instance encounters that are currently active in the world.
 * Encounters opt in by calling {@link #register} when they start and
 * {@link #unregister} when they finish; prospective spawners use
 * {@link #activeCount(ServerLevel, String)} and
 * {@link #nearestActiveDistance(ServerLevel, String, Vec3)} to enforce a
 * per-event concurrency cap and a minimum distance between concurrent
 * encounters of the same family.
 *
 * <p>Membership is keyed on {@link Cinematic} for its {@link Cinematic#level()},
 * {@link Cinematic#anchor()} and {@link Cinematic#eventId()} accessors. The
 * {@code eventId}-scoped overloads are the standard call site; the
 * eventId-less variants count or measure across <em>every</em> event family
 * and are kept as diagnostic helpers (e.g. for a future "list all active
 * encounters" debug command).</p>
 *
 * <p>Opting in is independent of
 * {@link com.payangar.encounters.event.cinematic.CinematicTicker} — short,
 * single-shot events do not need to register; multi-instance ones (e.g.
 * patrol skirmishes, portal invasions) do.</p>
 *
 * <p><b>Performance.</b> With a realistic upper bound of ~20 active
 * cinematics across all event families, a linear scan over a single
 * {@link CopyOnWriteArraySet} is strictly faster than maintaining bucketed
 * indices: no hash overhead, no pointer chasing, cache-friendly. The set
 * never holds enough entries to make {@code O(N)} relevant.</p>
 */
public final class ActiveEncounterTracker {

    private static final CopyOnWriteArraySet<Cinematic> ACTIVE = new CopyOnWriteArraySet<>();

    private ActiveEncounterTracker() {}

    /**
     * Add {@code encounter} to the active set. Callers must ensure
     * {@link #unregister} is invoked on every cleanup path — including
     * exceptional ones — to avoid leaking a reference into the set.
     */
    public static void register(Cinematic encounter) {
        ACTIVE.add(encounter);
    }

    public static void unregister(Cinematic encounter) {
        ACTIVE.remove(encounter);
    }

    /** Drops every tracked encounter. Used at server stop. */
    public static void clear() {
        int n = ACTIVE.size();
        ACTIVE.clear();
        if (n > 0) Constants.LOG.debug("ActiveEncounterTracker: cleared {} encounter(s) at shutdown", n);
    }

    /** Drops every tracked encounter bound to the given level. Used at level unload. */
    public static void clearLevel(ServerLevel level) {
        int before = ACTIVE.size();
        ACTIVE.removeIf(c -> c.level() == level);
        int removed = before - ACTIVE.size();
        if (removed > 0) Constants.LOG.debug("ActiveEncounterTracker: cleared {} encounter(s) on level {}", removed, level.dimension().location());
    }

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
     * when no encounter is tracked on the level. Diagnostic helper — per-event
     * spacing should use
     * {@link #nearestActiveDistance(ServerLevel, String, Vec3)}.
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
}
