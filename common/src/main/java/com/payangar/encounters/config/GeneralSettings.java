package com.payangar.encounters.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * Cross-event settings: behaviours shared by every encounter event.
 * Rendered as the first tab of the config screen.
 */
@Config(name = "general")
public class GeneralSettings implements ConfigData {
    @ConfigEntry.Gui.Tooltip
    public boolean mobsDropEquipment = false;

    /**
     * When enabled, promotes the {@code encounters} logger from {@code INFO}
     * to {@code DEBUG} so verbose diagnostics (scanner rejection reasons,
     * roster filter decisions, …) become visible without restarting. Toggled
     * live via the GUI through {@link com.payangar.encounters.LogLevelControl}.
     */
    @ConfigEntry.Gui.Tooltip
    public boolean debug = false;
}
