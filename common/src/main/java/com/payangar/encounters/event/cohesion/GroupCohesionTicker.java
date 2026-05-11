package com.payangar.encounters.event.cohesion;

import com.payangar.encounters.platform.Services;
import net.minecraft.server.level.ServerLevel;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-server registry of active group cohesion trackers. Throttled to once
 * per second per level — pathfinding is cheap to re-issue at that cadence and
 * groups feel coherent without burning CPU every tick.
 */
public final class GroupCohesionTicker {

    private static final int TICK_INTERVAL = 20;

    private static final CopyOnWriteArrayList<GroupCohesion> ACTIVE = new CopyOnWriteArrayList<>();
    private static boolean registered = false;

    private GroupCohesionTicker() {}

    public static synchronized void initialize() {
        if (registered) return;
        Services.PLATFORM.registerServerLevelTickListener(GroupCohesionTicker::onLevelTick);
        registered = true;
    }

    public static void start(GroupCohesion group) {
        ACTIVE.add(group);
    }

    /** Drops every active group cohesion tracker. Used at server stop. */
    public static void clear() {
        ACTIVE.clear();
    }

    /** Drops every cohesion tracker bound to the given level. Used at level unload. */
    public static void clearLevel(ServerLevel level) {
        ACTIVE.removeIf(g -> g.level() == level);
    }

    private static void onLevelTick(ServerLevel level) {
        if (ACTIVE.isEmpty()) return;
        if (level.getGameTime() % TICK_INTERVAL != 0) return;

        Iterator<GroupCohesion> it = ACTIVE.iterator();
        while (it.hasNext()) {
            GroupCohesion g = it.next();
            if (g.level() != level) continue;
            g.tick();
            if (g.isFinished()) {
                ACTIVE.remove(g);
            }
        }
    }
}
