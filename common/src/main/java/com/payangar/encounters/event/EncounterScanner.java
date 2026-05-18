package com.payangar.encounters.event;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.platform.Services;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Base class for periodic, scanner-driven encounter triggers. Owns the
 * check chain every scanner shares (overworld + enabled + tick interval +
 * trigger chance > 0 + capability gates) so its sub-classes only carry the
 * event-specific bits: their {@link #canTrigger} guard set, their candidate
 * picking logic, and their commit strategy.
 *
 * <p><b>Code drives composition, not config.</b> A scanner that wants a
 * concurrency cap and a post-event cooldown explicitly consults
 * {@link ActiveEncounterTracker#activeCount(ServerLevel, String)} and
 * {@link ActiveEncounterTracker#cooldownElapsed(ServerLevel, String, int)}
 * in its {@link #canTrigger} override. A scanner with neither simply returns
 * {@code true}. The presence or absence of a {@code ConcurrencySettings} /
 * {@code CooldownSettings} block in the config follows what the code reads,
 * never dictates it.</p>
 *
 * <p>Sub-classes register their tick hook through {@link #initialize()} once
 * at bootstrap. They are written as singletons (one {@code INSTANCE} field
 * per scanner) because the tick callback is bound to a method reference.</p>
 *
 * <p>Verbose rejection logs go through {@link Constants#LOG} at {@code DEBUG}
 * level. The {@code general.debug} config toggle promotes the {@code encounters}
 * logger from INFO to DEBUG so they become visible without restart.</p>
 */
public abstract class EncounterScanner {

    protected final String eventId;
    private boolean registered = false;

    protected EncounterScanner(String eventId) {
        this.eventId = eventId;
    }

    /**
     * Registers this scanner's tick listener with the platform. Idempotent —
     * a second call is a silent no-op.
     */
    public final synchronized void initialize() {
        if (registered) return;
        Services.PLATFORM.registerServerLevelTickListener(this::onTick);
        registered = true;
    }

    private void onTick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        if (!preTick(level)) return;

        EncountersConfig config = EncountersConfig.get();
        if (!enabled(config)) {
            Constants.LOG.debug("[{}] scanner skipped: event disabled", eventId);
            return;
        }

        int interval = Math.max(20, scanIntervalTicks(config));
        if (level.getGameTime() % interval != 0) return; // not a scan tick — silent

        if (triggerChance(config) <= 0.0) {
            Constants.LOG.debug("[{}] scanner skipped: trigger chance is zero", eventId);
            return;
        }

        if (!canTrigger(level, config)) return; // canTrigger logs its own reason

        scan(level, config);
    }

    /**
     * Hook for sub-classes that maintain tick-driven state outside the scan
     * proper (e.g. SkirmishScanner's pending audio teases). Runs every server
     * tick on every overworld level. Returns {@code false} to skip the scan
     * entirely on this tick (e.g. while a tease is in flight). Default no-op.
     */
    protected boolean preTick(ServerLevel level) {
        return true;
    }

    protected abstract boolean enabled(EncountersConfig config);
    protected abstract int scanIntervalTicks(EncountersConfig config);
    protected abstract double triggerChance(EncountersConfig config);

    /**
     * Sub-class decides which capability gates to read — concurrency cap,
     * post-event cooldown, day-only, etc. Implementations must log a DEBUG
     * line with the reason when returning {@code false}; the base class will
     * not log on their behalf.
     */
    protected abstract boolean canTrigger(ServerLevel level, EncountersConfig config);

    /**
     * Performs the event-specific scan (pick candidate, validate, commit).
     * Called after every common pre-check has passed. Implementations log
     * DEBUG lines for their own per-candidate rejections.
     */
    protected abstract void scan(ServerLevel level, EncountersConfig config);

    /** Drops sub-class state at server stop. Default no-op. */
    public void clear() {}

    /** Drops sub-class state bound to {@code level}. Default no-op. */
    public void clearLevel(ServerLevel level) {}
}
