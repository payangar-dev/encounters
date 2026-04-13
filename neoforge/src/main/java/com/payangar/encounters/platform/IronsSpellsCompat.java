package com.payangar.encounters.platform;

import com.payangar.encounters.platform.services.ISpellCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/**
 * NeoForge bridge to Iron's Spells 'n Spellbooks.
 *
 * <p>This class must <strong>not</strong> import or reference any
 * io.redspace.* types — it is loaded unconditionally by the Java
 * ServiceLoader at mod init, and would crash when Iron's Spells is absent.
 * All mod-specific code lives in {@link IronsSpellsDirectCast}, which is
 * only classloaded when {@link #isAvailable()} returns true.</p>
 */
public class IronsSpellsCompat implements ISpellCompat {

    public static final String MOD_ID = "irons_spellbooks";

    @Override
    public boolean isAvailable() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    @Override
    public void castShockwave(ServerLevel level, LivingEntity caster, int spellLevel) {
        if (!isAvailable()) return;
        IronsSpellsDirectCast.shockwave(level, caster, spellLevel);
    }
}
