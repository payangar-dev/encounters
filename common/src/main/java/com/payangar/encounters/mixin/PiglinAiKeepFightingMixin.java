package com.payangar.encounters.mixin;

import com.payangar.encounters.event.ally.EncounterAllies;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Keeps encounter-spawned piglins committed to their target despite vanilla
 * pacification rules.
 *
 * <p>Vanilla flow that breaks our spawn-time force-aggro: the FIGHT activity
 * runs {@code StopAttackingIfTargetInvalid}, whose predicate calls
 * {@code !PiglinAi.isNearestValidAttackTarget}, which in turn calls
 * {@code findNearestValidAttackTarget(piglin).filter(t -> t == target)}.
 * {@code findNearestValidAttackTarget} returns {@link Optional#empty()} when
 * the player wears gold (and a few other cases), so the predicate decides
 * the target is "invalid", removes {@code ATTACK_TARGET} from the brain,
 * the FIGHT activity ends, and the piglin returns to IDLE — visually a
 * single attack windup followed by an immediate disengage.</p>
 *
 * <p>For mobs carrying an encounter group tag we override
 * {@code findNearestValidAttackTarget} at HEAD: if there's a live player
 * already targeted, return it; otherwise fall back to the brain's nearest
 * visible player. Vanilla's gold-armor / loved-item filters never run, so
 * the validation in the FIGHT loop always sees the player as valid.</p>
 */
@Mixin(PiglinAi.class)
public abstract class PiglinAiKeepFightingMixin {

    @Inject(method = "findNearestValidAttackTarget(Lnet/minecraft/world/entity/monster/piglin/Piglin;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true)
    private static void encounters$forceInvasionPiglinsToAttack(
            Piglin piglin,
            CallbackInfoReturnable<Optional<? extends LivingEntity>> cir) {
        if (!EncounterAllies.isEncounterMob(piglin)) return;

        // Prefer the currently locked-on target so a piglin actively fighting
        // a player keeps fighting that same player rather than re-acquiring.
        LivingEntity current = piglin.getTarget();
        if (current instanceof Player p && p.isAlive()) {
            cir.setReturnValue(Optional.of(current));
            return;
        }

        // No live target — fall back to the brain's nearest visible player.
        Optional<Player> nearest = piglin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER);
        if (nearest.isPresent() && nearest.get().isAlive()) {
            cir.setReturnValue(Optional.of(nearest.get()));
            return;
        }

        // No player available either. Vanilla's fall-through would consult
        // NEAREST_VISIBLE_NEMESIS, which can return a wither_skeleton — and
        // the encounter often spawns wither_skeletons as allies (Cinder Knight,
        // Ash Sentinel, Iron Charger rider). Returning empty keeps the FIGHT
        // activity from acquiring an ally as target. The MobSetTargetMixin /
        // LivingEntityCanAttackMixin catch the same case if it slips through
        // another path, this is just the most direct fix.
        cir.setReturnValue(Optional.empty());
    }
}
