package com.payangar.encounters.mixin.compat.epicfight;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.payangar.encounters.compat.epicfight.EpicFightScaleBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import yesman.epicfight.api.utils.math.OpenMatrix4f;

/**
 * EpicFight's {@code LivingEntityPatch#getModelMatrix(float)} ignores the
 * vanilla {@code minecraft:generic.scale} attribute. This mixin multiplies the
 * returned matrix by that attribute so spawned mobs carrying the attribute
 * render at the correct size. Silently skipped when EpicFight is absent.
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch", remap = false)
public abstract class LivingEntityPatchMixin {

    @ModifyReturnValue(method = "getModelMatrix", at = @At("RETURN"), remap = false)
    private OpenMatrix4f encounters$applyVanillaScale(OpenMatrix4f matrix) {
        return (OpenMatrix4f) EpicFightScaleBridge.applyVanillaScale(this, matrix);
    }
}
