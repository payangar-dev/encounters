package com.payangar.encounters;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * Promotes {@link Constants#LOG} between {@code INFO} (default) and
 * {@code DEBUG} at runtime. Bound to {@code general.debug} in the config —
 * when the user flips the toggle in the GUI, the save listener re-applies
 * the level immediately, no restart needed.
 *
 * <p>Relies on Log4j2 (Minecraft's logging backend) for the level mutation.
 * If a future loader switches to a different SLF4J binding, this needs to
 * be reworked, but at MC 1.21.1 on both Fabric and NeoForge it's safe.</p>
 */
public final class LogLevelControl {

    private LogLevelControl() {}

    public static void apply(boolean debug) {
        Configurator.setLevel(Constants.MOD_NAME, debug ? Level.DEBUG : Level.INFO);
    }
}
