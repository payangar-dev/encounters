package com.payangar.encounters.event;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.platform.Services;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Shared spawn primitive for every encounter event. Loads an entity from a
 * {@link ResolvedMob}'s SNBT, applies the global drop-equipment override,
 * adds it to the level, and force-aggroes the mob (and every passenger) on
 * the nearest player so encounter mobs always engage regardless of their
 * vanilla pacification rules.
 */
public final class EncounterSpawner {

    /** Search radius (in blocks) for the initial force-aggro target. */
    private static final double FORCE_AGGRO_RADIUS = 64.0;

    private EncounterSpawner() {}

    public static Entity spawn(ServerLevel level, ResolvedMob mob, Vec3 spawnPos, RandomSource rng, String eventId) {
        CompoundTag tag = mob.nbtCopy();
        boolean userProvidedNbt = !tag.isEmpty();
        tag.putString("id", mob.id().toString());

        Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
            e.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, rng.nextFloat() * 360f, 0f);
            return e;
        });

        if (entity == null) {
            Constants.LOG.warn("[{}] failed to load entity '{}'", eventId, mob.id());
            return null;
        }

        // Mirror vanilla /summon: only call finalizeSpawn when the user did NOT
        // provide custom NBT (otherwise we would overwrite equipment/attributes).
        if (!userProvidedNbt && entity instanceof Mob m) {
            Services.PLATFORM.finalizeMobSpawn(m, level,
                    level.getCurrentDifficultyAt(m.blockPosition()),
                    MobSpawnType.EVENT);
        }

        // Must run after loadEntityRecursive/finalizeSpawn to override both
        // user-provided HandDropChances/ArmorDropChances NBT and vanilla defaults.
        if (!EncountersConfig.get().general.mobsDropEquipment && entity instanceof Mob m) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                m.setDropChance(slot, 0f);
            }
        }

        preventZombification(entity);

        if (!level.tryAddFreshEntityWithPassengers(entity)) {
            Constants.LOG.warn("[{}] refused to add entity '{}' (duplicate UUID?)", eventId, mob.id());
            return null;
        }

        forceAggroRecursive(level, entity, spawnPos);
        return entity;
    }

    /**
     * Encounter mobs spawn in the overworld and stay there. Vanilla would
     * convert piglins to zombified_piglin (15 s) and hoglins to zoglin
     * (5 min), silently swapping the configured mob for a different one
     * and breaking the allies tag along the way. We always opt in to the
     * vanilla {@code IsImmuneToZombification} flag for the affected types.
     */
    private static void preventZombification(Entity entity) {
        if (entity instanceof AbstractPiglin piglin) {
            piglin.setImmuneToZombification(true);
        } else if (entity instanceof Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        }
        // Recurse into passengers so a Hoglin-mounted Piglin Brute also gets it.
        for (Entity p : entity.getPassengers()) {
            preventZombification(p);
        }
    }

    /**
     * Force-aggroes the entity (and every passenger) on the nearest live
     * player within {@link #FORCE_AGGRO_RADIUS}. Sets both the legacy
     * {@code Mob#target} field (covers old AI Goal mobs) and the
     * {@code ATTACK_TARGET} brain memory (covers brain-driven mobs like
     * piglins and hoglins) so the mob immediately enters its FIGHT
     * activity regardless of vanilla pacification rules — gold armor on
     * the player, Zombified Piglin neutrality, and the like are all
     * superseded by the encounter directive.
     *
     * <p>If the player later dies or moves out of sight the brain may drop
     * the target naturally; per-event tickers can re-aggro periodically.</p>
     */
    private static void forceAggroRecursive(ServerLevel level, Entity entity, Vec3 anchor) {
        if (entity instanceof Mob mob) {
            Player nearest = level.getNearestPlayer(anchor.x, anchor.y, anchor.z, FORCE_AGGRO_RADIUS, true);
            if (nearest != null && nearest.isAlive()) {
                applyAggro(mob, nearest);
            }
        }
        for (Entity p : entity.getPassengers()) {
            forceAggroRecursive(level, p, anchor);
        }
    }

    /**
     * Sets {@code target} on the given mob. Public so per-event tickers can
     * re-aggro mobs that lost their target mid-fight.
     */
    public static void applyAggro(Mob mob, LivingEntity target) {
        mob.setTarget(target);
        Brain<?> brain = mob.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
        }
    }
}
