package com.payangar.encounters.event;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.config.WeightedMob;
import com.payangar.encounters.event.cinematic.CinematicTicker;
import com.payangar.encounters.event.cinematic.LightningCinematic;
import com.payangar.encounters.platform.Services;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class LightningOverchargeEvent {

    public static final String ID = "lightning_overcharge";
    public static final String OWN_BOLT_TAG = "encounters_overcharged";
    private static final double SPAWN_RADIUS = 2.5;

    private static MobRoster cachedRoster;
    private static List<WeightedMob> cachedSource;

    private LightningOverchargeEvent() {}

    /**
     * Called by each loader's lightning hook when any LightningBolt spawns.
     * Returns true if the bolt should be cancelled and replaced by our own
     * encounter bolt. Returns false to leave the vanilla bolt untouched.
     */
    public static boolean onLightningSpawn(ServerLevel level, LightningBolt bolt) {
        if (bolt.getTags().contains(OWN_BOLT_TAG)) return false;

        EncountersConfig config = EncountersConfig.get();
        if (!config.lightningOverchargeEnabled) return false;
        if (config.lightningOverchargeChance <= 0.0) return false;
        if (!isNaturalStormBolt(level, bolt)) return false;

        // Bail out *before* cancelling the vanilla bolt if the roster is empty —
        // otherwise the player would just see the lightning vanish.
        if (roster(config).isEmpty()) return false;

        RandomSource rng = level.getRandom();
        if (rng.nextDouble() >= config.lightningOverchargeChance) return false;

        Vec3 pos = bolt.position();
        // Defer to next tick to avoid reentrancy during entity add/join events.
        level.getServer().execute(() -> trigger(level, pos));
        return true;
    }

    /**
     * Force-triggers the event at a given position, bypassing all config gates.
     * Used by the debug command.
     */
    public static int forceTrigger(ServerLevel level, Vec3 pos) {
        return trigger(level, pos);
    }

    private static int trigger(ServerLevel level, Vec3 pos) {
        spawnEncounterLightning(level, pos);

        EncountersConfig config = EncountersConfig.get();
        MobRoster roster = roster(config);
        if (roster.isEmpty()) return 0;

        RandomSource rng = level.getRandom();
        int min = Math.max(1, config.lightningOverchargeGroupMin);
        int max = Math.max(min, config.lightningOverchargeGroupMax);
        int count = min + rng.nextInt(max - min + 1);

        LightningCinematic cinematic = new LightningCinematic(level, pos);

        Map<String, Integer> breakdown = new LinkedHashMap<>();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Optional<ResolvedMob> pick = roster.pick(rng);
            if (pick.isEmpty()) continue;
            ResolvedMob mob = pick.get();
            Entity entity = spawnOne(level, mob, pos, rng);
            if (entity != null) {
                // Cinematic lockdown: mobs are frozen and untouchable until
                // phase 1 ends. Persistence prevents vanilla despawn so the
                // encounter lingers as an explorable threat.
                entity.setInvulnerable(true);
                if (entity instanceof Mob m) {
                    m.setNoAi(true);
                    m.setPersistenceRequired();
                }
                cinematic.registerSpawnedMob(entity);
                breakdown.merge(mob.displayName(), 1, Integer::sum);
                spawned++;
            }
        }

        if (spawned > 0) {
            CinematicTicker.start(cinematic);
            String summary = formatBreakdown(breakdown);
            Constants.LOG.info("[{}] triggered at ({}, {}, {}): {}",
                    ID, (int) pos.x, (int) pos.y, (int) pos.z, summary);
        }
        return spawned;
    }

    /**
     * Spawns the encounter's lightning bolt. Marked visual-only so its tick
     * phase skips damage, fire spawning and vanilla conversions — our freshly
     * spawned mobs would otherwise be fried by the bolt that summoned them.
     * The tag ensures our own listener ignores this bolt (no recursion).
     * Single hook point for future customization (color, size, particles…).
     */
    private static void spawnEncounterLightning(ServerLevel level, Vec3 pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(pos.x, pos.y, pos.z, 0f, 0f);
        bolt.setVisualOnly(true);
        bolt.addTag(OWN_BOLT_TAG);
        level.addFreshEntity(bolt);
    }

    private static Entity spawnOne(ServerLevel level, ResolvedMob mob, Vec3 center, RandomSource rng) {
        double angle = rng.nextDouble() * Math.PI * 2.0;
        double r = rng.nextDouble() * SPAWN_RADIUS;
        double x = center.x + Math.cos(angle) * r;
        double z = center.z + Math.sin(angle) * r;
        double y = center.y;

        CompoundTag tag = mob.nbtCopy();
        boolean userProvidedNbt = !tag.isEmpty();
        tag.putString("id", mob.id().toString());

        Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
            e.moveTo(x, y, z, rng.nextFloat() * 360f, 0f);
            return e;
        });

        if (entity == null) {
            Constants.LOG.warn("[{}] failed to load entity '{}'", ID, mob.id());
            return null;
        }

        // Mirror vanilla /summon: only call finalizeSpawn when the user did NOT
        // provide custom NBT (otherwise we would overwrite equipment/attributes).
        if (!userProvidedNbt && entity instanceof Mob m) {
            Services.PLATFORM.finalizeMobSpawn(m, level,
                    level.getCurrentDifficultyAt(m.blockPosition()),
                    MobSpawnType.EVENT);
        }

        if (!level.tryAddFreshEntityWithPassengers(entity)) {
            Constants.LOG.warn("[{}] refused to add entity '{}' (duplicate UUID?)", ID, mob.id());
            return null;
        }
        return entity;
    }

    private static boolean isNaturalStormBolt(ServerLevel level, LightningBolt bolt) {
        if (level.dimension() != Level.OVERWORLD) return false;
        if (!level.isThundering()) return false;
        // Exclude trident Channeling (and any other player-induced bolts).
        // Natural bolts and /summon-ed ones both have cause == null, which is intentional:
        // /summon is a legitimate way to test the event during a thunderstorm.
        if (bolt.getCause() != null) return false;
        return true;
    }

    private static MobRoster roster(EncountersConfig config) {
        if (cachedRoster == null || cachedSource != config.lightningOverchargeMobs) {
            cachedRoster = MobRoster.resolve(config.lightningOverchargeMobs, ID);
            cachedSource = config.lightningOverchargeMobs;
        }
        return cachedRoster;
    }

    public static void invalidateRoster() {
        cachedRoster = null;
        cachedSource = null;
    }

    private static String formatBreakdown(Map<String, Integer> breakdown) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> e : breakdown.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getValue()).append("\u00d7 ").append(e.getKey());
            first = false;
        }
        return sb.toString();
    }
}
