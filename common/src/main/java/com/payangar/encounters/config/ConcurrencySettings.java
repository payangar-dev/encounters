package com.payangar.encounters.config;

import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * Capability block for events that cap how many instances can run at the
 * same time and how close together they may stand. Lookup goes through
 * {@link com.payangar.encounters.event.ActiveEncounterTracker} which is the
 * single source of truth for presently-active cinematics. Composed into
 * per-event {@code *Settings} via {@code @ConfigEntry.Gui.CollapsibleObject}.
 */
public class ConcurrencySettings {
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 8)
    public int maxConcurrent = 1;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 1024)
    public int minDistanceBetween = 256;
}
