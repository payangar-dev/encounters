package com.payangar.encounters.event.cinematic;

import com.payangar.encounters.platform.Services;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-server registry of running cinematics. Ticks every active cinematic
 * on the level it belongs to, prunes finished ones, and enforces the
 * abandonment policy (see {@link Cinematic}).
 */
public final class CinematicTicker {

    /** Players must come within this many blocks of the anchor to keep a cinematic engaged. */
    static final double ABANDON_RADIUS = 96.0;
    /** After this many consecutive ticks with no nearby player, the cinematic is abandoned. */
    static final int ABANDON_TICKS = 600;

    private static final CopyOnWriteArrayList<Cinematic> ACTIVE = new CopyOnWriteArrayList<>();
    private static final Map<Cinematic, Integer> ABANDON_COUNTERS = new ConcurrentHashMap<>();
    private static boolean registered = false;

    private CinematicTicker() {}

    public static synchronized void initialize() {
        if (registered) return;
        Services.PLATFORM.registerServerLevelTickListener(CinematicTicker::onLevelTick);
        registered = true;
    }

    public static void start(Cinematic cinematic) {
        ACTIVE.add(cinematic);
    }

    /** Drops every active cinematic without invoking {@code onAbandoned}. Used at server stop. */
    public static void clear() {
        ACTIVE.clear();
        ABANDON_COUNTERS.clear();
    }

    /** Drops every cinematic bound to the given level. Used at level unload. */
    public static void clearLevel(ServerLevel level) {
        ACTIVE.removeIf(c -> c.level() == level);
        ABANDON_COUNTERS.keySet().removeIf(c -> c.level() == level);
    }

    private static void onLevelTick(ServerLevel level) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Cinematic> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Cinematic c = it.next();
            if (c.level() != level) continue;

            if (checkAbandoned(c)) {
                c.onAbandoned();
                ACTIVE.remove(c);
                ABANDON_COUNTERS.remove(c);
                continue;
            }

            c.tick();
            if (c.isFinished()) {
                ACTIVE.remove(c);
                ABANDON_COUNTERS.remove(c);
            }
        }
    }

    private static boolean checkAbandoned(Cinematic c) {
        Vec3 anchor = c.anchor();
        double radiusSqr = ABANDON_RADIUS * ABANDON_RADIUS;
        // Manual iteration with early-exit avoids the predicate-filter list allocation
        // that level.getPlayers(filter) does — over a long invasion this matters.
        for (ServerPlayer p : c.level().players()) {
            if (p.isAlive() && p.position().distanceToSqr(anchor) <= radiusSqr) {
                ABANDON_COUNTERS.remove(c);
                return false;
            }
        }
        return ABANDON_COUNTERS.merge(c, 1, Integer::sum) >= ABANDON_TICKS;
    }
}
