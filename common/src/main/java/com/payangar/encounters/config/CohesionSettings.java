package com.payangar.encounters.config;

import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * Capability block for events whose mobs are pulled back toward a group
 * leader by {@link com.payangar.encounters.event.cohesion.GroupCohesion}.
 * Composed into per-event {@code *Settings} via
 * {@code @ConfigEntry.Gui.CollapsibleObject}.
 */
public class CohesionSettings {
    @ConfigEntry.Gui.Tooltip
    public boolean enabled = true;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 4, max = 32)
    public int radius = 12;
}
