package com.payangar.encounters.platform.services;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Loader-specific bridge to external spell mods (currently only Iron's Spells
 * 'n Spellbooks on NeoForge). Fabric ships a no-op implementation because
 * Iron's Spells has no Fabric port.
 *
 * <p>Implementations are wired via {@link java.util.ServiceLoader}, same
 * pattern as {@link IPlatformHelper}.</p>
 */
public interface ISpellCompat {

    /** True if a compatible spell mod is currently loaded. */
    boolean isAvailable();

    /**
     * Triggers a shockwave spell centered on the caster. No-op when
     * unavailable.
     */
    void castShockwave(ServerLevel level, LivingEntity caster, int spellLevel);
}
