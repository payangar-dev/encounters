package com.payangar.encounters.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Patrol Skirmish event settings. Scanner-driven, concurrency-capped, with a
 * post-event cooldown. Uses its own per-faction leash instead of
 * {@link CohesionSettings}.
 */
@Config(name = "skirmish")
public class SkirmishSettings implements ConfigData {
    @ConfigEntry.Gui.Tooltip
    public boolean enabled = true;

    @ConfigEntry.Gui.CollapsibleObject
    public ScannerSettings scanner = scannerDefaults();

    @ConfigEntry.Gui.CollapsibleObject
    public ConcurrencySettings concurrency = concurrencyDefaults();

    @ConfigEntry.Gui.CollapsibleObject
    public CooldownSettings cooldown = cooldownDefaults();

    @ConfigEntry.Gui.Tooltip
    public boolean dayOnly = true;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1200, max = 72000)
    public int timeoutTicks = 24000;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 200, max = 12000)
    public int rewardWaitMaxTicks = 2400;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 12)
    public int mobsPerSide = 5;

    @ConfigEntry.Gui.Tooltip
    public double caravanChancePerSide = 0.65;

    @ConfigEntry.Gui.Tooltip
    public List<String> biomes = defaultBiomes();

    private static ScannerSettings scannerDefaults() {
        ScannerSettings s = new ScannerSettings();
        s.triggerChance = 0.15;
        s.scanIntervalTicks = 1200;
        return s;
    }

    private static ConcurrencySettings concurrencyDefaults() {
        ConcurrencySettings c = new ConcurrencySettings();
        c.maxConcurrent = 2;
        c.minDistanceBetween = 256;
        return c;
    }

    private static CooldownSettings cooldownDefaults() {
        CooldownSettings c = new CooldownSettings();
        c.cooldownTicks = 6000;
        return c;
    }

    public static List<String> defaultBiomes() {
        List<String> list = new ArrayList<>();
        list.add("minecraft:plains");
        list.add("minecraft:sunflower_plains");
        list.add("minecraft:savanna");
        list.add("minecraft:savanna_plateau");
        list.add("minecraft:windswept_savanna");
        list.add("minecraft:taiga");
        list.add("minecraft:snowy_taiga");
        list.add("minecraft:forest");
        list.add("minecraft:flower_forest");
        list.add("minecraft:dark_forest");
        list.add("minecraft:birch_forest");
        list.add("minecraft:old_growth_birch_forest");
        list.add("minecraft:meadow");
        return list;
    }
}
