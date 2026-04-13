package com.payangar.encounters.event.cinematic;

import com.payangar.encounters.platform.Services;
import net.minecraft.server.level.ServerLevel;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-server registry of running cinematics. Ticks every active cinematic
 * on the level it belongs to, and prunes finished ones.
 */
public final class CinematicTicker {

    private static final CopyOnWriteArrayList<LightningCinematic> ACTIVE = new CopyOnWriteArrayList<>();
    private static boolean registered = false;

    private CinematicTicker() {}

    public static synchronized void initialize() {
        if (registered) return;
        Services.PLATFORM.registerServerLevelTickListener(CinematicTicker::onLevelTick);
        registered = true;
    }

    public static void start(LightningCinematic cinematic) {
        ACTIVE.add(cinematic);
    }

    private static void onLevelTick(ServerLevel level) {
        if (ACTIVE.isEmpty()) return;
        Iterator<LightningCinematic> it = ACTIVE.iterator();
        while (it.hasNext()) {
            LightningCinematic c = it.next();
            if (c.level() != level) continue;
            c.tick();
            if (c.isFinished()) {
                ACTIVE.remove(c);
            }
        }
    }
}
