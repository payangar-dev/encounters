package com.payangar.encounters.event.cinematic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * A running encounter cinematic. Implementations are scheduled by
 * {@link CinematicTicker}, which calls {@link #tick()} every server tick on
 * the level returned by {@link #level()} and removes them once
 * {@link #isFinished()} returns true.
 *
 * <p>The ticker also enforces an abandonment policy: when no player has been
 * within {@link CinematicTicker#ABANDON_RADIUS} blocks of {@link #anchor()}
 * for {@link CinematicTicker#ABANDON_TICKS} consecutive ticks, the cinematic
 * is removed and {@link #onAbandoned()} is invoked. The default no-op is
 * appropriate when there is no shared lock to release; multi-stage events
 * (e.g. portal invasions holding a global lock) should override it.</p>
 */
public interface Cinematic {

    ServerLevel level();

    Vec3 anchor();

    void tick();

    boolean isFinished();

    default void onAbandoned() {}
}
