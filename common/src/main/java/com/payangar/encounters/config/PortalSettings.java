package com.payangar.encounters.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * Nether Portal Invasion event settings. Composes the capability blocks the
 * scanner code actually consumes: scanner ticking, concurrency cap, post-event
 * cooldown, and wave cohesion.
 */
@Config(name = "portal")
public class PortalSettings implements ConfigData {
    @ConfigEntry.Gui.Tooltip
    public boolean enabled = true;

    @ConfigEntry.Gui.CollapsibleObject
    public ScannerSettings scanner = scannerDefaults();

    @ConfigEntry.Gui.CollapsibleObject
    public ConcurrencySettings concurrency = concurrencyDefaults();

    @ConfigEntry.Gui.CollapsibleObject
    public CooldownSettings cooldown = cooldownDefaults();

    @ConfigEntry.Gui.CollapsibleObject
    public CohesionSettings cohesion = new CohesionSettings();

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 12)
    public int minWaves = 4;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 12)
    public int maxWaves = 6;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 10)
    public int firstWaveSize = 2;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 5)
    public int waveSizeStep = 1;

    @ConfigEntry.Gui.Tooltip
    public boolean magmaBombEnabled = true;

    private static ScannerSettings scannerDefaults() {
        ScannerSettings s = new ScannerSettings();
        s.triggerChance = 0.05;
        s.scanIntervalTicks = 100;
        return s;
    }

    private static ConcurrencySettings concurrencyDefaults() {
        ConcurrencySettings c = new ConcurrencySettings();
        c.maxConcurrent = 1;
        c.minDistanceBetween = 256;
        return c;
    }

    private static CooldownSettings cooldownDefaults() {
        CooldownSettings c = new CooldownSettings();
        c.cooldownTicks = 12000;
        return c;
    }
}
