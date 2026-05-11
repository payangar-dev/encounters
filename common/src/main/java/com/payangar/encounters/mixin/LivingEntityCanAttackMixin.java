package com.payangar.encounters.mixin;

import com.payangar.encounters.event.ally.EncounterAllies;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Backstop for the allies system: vetoes any {@code canAttack} check between
 * two members of the same encounter group. {@link MobSetTargetMixin} catches
 * the goal-driven path, but Brain-based mobs (piglins, hoglins, axolotls...)
 * set their {@code ATTACK_TARGET} memory directly without going through
 * {@code Mob#setTarget}; almost every brain task still validates a candidate
 * via {@code canAttack}, so a HEAD-cancel here closes that loophole.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityCanAttackMixin {

    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void encounters$preventAllyAttack(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (EncounterAllies.areAllies((LivingEntity) (Object) this, target)) {
            cir.setReturnValue(false);
        }
    }
}
