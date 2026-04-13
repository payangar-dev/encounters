package com.payangar.encounters.platform;

import com.payangar.encounters.platform.services.ISpellCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fabric stub — Iron's Spells 'n Spellbooks has no Fabric port, so there is
 * nothing to cast. Every method is a no-op.
 */
public class NoOpSpellCompat implements ISpellCompat {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void castShockwave(ServerLevel level, LivingEntity caster, int spellLevel) {
    }
}
