package com.payangar.encounters.config;

import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * Capability block for events driven by a periodic per-level scanner (as
 * opposed to passive event-hook driven). Composed into per-event
 * {@code *Settings} via {@code @ConfigEntry.Gui.CollapsibleObject}.
 */
public class ScannerSettings {
    @ConfigEntry.Gui.Tooltip
    public double triggerChance = 0.05;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 20, max = 12000)
    public int scanIntervalTicks = 100;
}
