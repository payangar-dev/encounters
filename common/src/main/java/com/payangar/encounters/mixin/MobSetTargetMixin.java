package com.payangar.encounters.mixin;

import com.payangar.encounters.event.ally.EncounterAllies;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents any {@link Mob} from locking onto an ally within the same encounter
 * group. Vanilla {@code LivingEntity#canAttack} does not consult
 * {@code isAlliedTo}, so {@code NearestAttackableTargetGoal} and the like
 * can otherwise pick a same-team target — typically a wither_skeleton
 * targeting a piglin via {@code NearestAttackableTargetGoal<AbstractPiglin>}
 * inside an invasion. Friendly fire being blocked at the {@code hurt} stage
 * doesn't help: the engagement still happens, the attacker swings uselessly
 * but the target's AI may retaliate via {@code HURT_BY} memories, and on
 * long invasions the loop eventually kills one of the two.
 *
 * <p>The early-cancel here funnels every targeting path through one
 * {@link EncounterAllies#areAllies} check, which only fires for entities
 * actually carrying an {@code encounters_g_} team — vanilla scoreboard
 * teams unrelated to encounters are not affected.</p>
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
