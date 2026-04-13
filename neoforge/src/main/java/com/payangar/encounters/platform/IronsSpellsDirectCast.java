package com.payangar.encounters.platform;

import com.payangar.encounters.Constants;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Direct calls into Iron's Spells 'n Spellbooks. Loaded only when the mod is
 * known to be present — referenced exclusively from {@link IronsSpellsCompat}
 * behind an isAvailable() check, so the JVM verifier never tries to link these
 * classes on a setup without Iron's Spells.
 *
 * <p>We bypass {@code AbstractSpell#castSpell} (which requires a ServerPlayer)
 * by calling {@code onCast} directly — it accepts any {@link LivingEntity} as
 * caster, so our invisible armor-stand works as the origin.</p>
 */
final class IronsSpellsDirectCast {

    private IronsSpellsDirectCast() {}

    static void shockwave(ServerLevel level, LivingEntity caster, int spellLevel) {
        try {
            SpellRegistry.SHOCKWAVE_SPELL.get()
                    .onCast(level, spellLevel, caster, CastSource.MOB, null);
        } catch (Throwable t) {
            Constants.LOG.warn("[lightning_overcharge] failed to cast Shockwave", t);
        }
    }
}
