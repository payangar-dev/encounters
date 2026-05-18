package com.payangar.encounters.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * Lightning Overcharge event settings. The event is passive (event-hook driven
 * by lightning bolts) so it has no {@link ScannerSettings}; it is not yet
 * concurrency-tracked so it has no {@link TrackerSettings} either. Both can be
 * grafted on without schema rewrites — Cloth fills missing nested blocks with
 * defaults on the next save.
 */
@Config(name = "lightning")
public class LightningSettings implements ConfigData {
    @ConfigEntry.Gui.Tooltip
    public boolean enabled = true;

    @ConfigEntry.Gui.Tooltip
    public double chancePerBolt = 0.05;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 20)
    public int groupMin = 2;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 20)
    public int groupMax = 5;

    @ConfigEntry.Gui.CollapsibleObject
    public CohesionSettings cohesion = new CohesionSettings();
}
