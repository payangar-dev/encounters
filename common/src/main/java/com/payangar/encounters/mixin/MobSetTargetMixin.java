package com.payangar.encounters.mixin;

import com.payangar.encounters.event.ally.EncounterAllies;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents any {@link Mob} from locking onto an ally within the same
 * encounter group. {@code HurtByTargetGoal#start} and every other target
 * goal funnel through {@code Mob#setTarget}, so a single HEAD-cancel
 * catches retaliation from friendly-fire AoE, stray arrows, modded AI
 * picks, or command-driven targeting alike.
 *
 * <p>Non-encounter entities (players, wild mobs) carry no group tag, so
 * {@link EncounterAllies#areAllies} short-circuits to {@code false} and
 * the mixin is a no-op for them.</p>
 */
@Mixin(Mob.class)
public abstract class MobSetTargetMixin {

    @Inject(method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void encounters$preventAllyTarget(LivingEntity target, CallbackInfo ci) {
        if (target == null) return;
        if (EncounterAllies.areAllies((Mob) (Object) this, target)) {
            ci.cancel();
        }
    }
}
