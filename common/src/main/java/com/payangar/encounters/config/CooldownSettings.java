package com.payangar.encounters.config;

import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * Capability block for events that impose a world-wide cooldown after the
 * last instance ends. The timestamp is stored centrally in
 * {@link com.payangar.encounters.event.ActiveEncounterTracker} and updated
 * automatically on cinematic unregister. Composed into per-event
 * {@code *Settings} via {@code @ConfigEntry.Gui.CollapsibleObject}.
 *
 * <p>An event whose scanner consults {@code cooldownElapsed(...)} must expose
 * this block in its {@code *Settings}; otherwise the block is omitted — the
 * code drives composition, not the config.</p>
 */
public class CooldownSettings {
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 72000)
    public int cooldownTicks = 6000;
}
