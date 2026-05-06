package com.payangar.encounters.event;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.config.WeightedMob;
import com.payangar.encounters.event.ally.EncounterAllies;
import com.payangar.encounters.event.cinematic.CinematicTicker;
import com.payangar.encounters.event.cinematic.LightningCinematic;
import com.payangar.encounters.event.cohesion.GroupCohesion;
import com.payangar.encounters.event.cohesion.GroupCohesionTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class LightningOverchargeEvent {

    public static final String ID = "lightning_overcharge";
    public static final String OWN_BOLT_TAG = "encounters_overcharged";
    private static final double SPAWN_RADIUS = 2.5;
    private static final int MAX_PLACEMENT_ATTEMPTS = 24;

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
        List<Mob> groupMembers = new ArrayList<>();

        // Track which block columns are already claimed so two mobs never land on
        // the same block (collision pile-up). The center column is reserved up
        // front — it's the lightning strike point, and spawning a mob there would
        // force it to intersect with whatever else the impact produces.
        Set<Long> usedColumns = new HashSet<>();
        usedColumns.add(columnKey(pos.x, pos.z));

        Map<String, Integer> breakdown = new LinkedHashMap<>();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Optional<ResolvedMob> pick = roster.pick(rng);
            if (pick.isEmpty()) continue;
            ResolvedMob mob = pick.get();
            Vec3 spawnPos = findFreeSpawnPos(pos, rng, usedColumns);
            if (spawnPos == null) {
                Constants.LOG.debug("[{}] no free column within radius {} for mob #{}/{} — skipping",
                        ID, SPAWN_RADIUS, i + 1, count);
                continue;
            }
            Entity entity = EncounterSpawner.spawn(level, mob, spawnPos, rng, ID);
            if (entity != null) {
                // Cinematic lockdown: mobs are frozen and untouchable until
                // phase 1 ends. Persistence prevents vanilla despawn so the
                // encounter lingers as an explorable threat.
                entity.setInvulnerable(true);
                if (entity instanceof Mob m) {
                    m.setNoAi(true);
                    m.setPersistenceRequired();
                    groupMembers.add(m);
                }
                cinematic.registerSpawnedMob(entity);
                breakdown.merge(mob.displayName(), 1, Integer::sum);
                spawned++;
            }
        }

        if (spawned > 0) {
            // Allied tagging: members share a group tag so the Mob#setTarget
            // mixin can veto any targeting between them — no intra-group
            // retaliation even after friendly-fire AoE or stray arrows.
            EncounterAllies.tagGroup(groupMembers);
            CinematicTicker.start(cinematic);
            if (groupMembers.size() >= 2) {
                GroupCohesionTicker.start(new GroupCohesion(level, groupMembers,
                        () -> EncountersConfig.get().lightningOverchargeGroupCohesionEnabled,
                        () -> EncountersConfig.get().lightningOverchargeGroupCohesionRadius));
            }
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

    /**
     * Picks a random position within {@link #SPAWN_RADIUS} whose block column
     * has not been claimed yet by another mob in the same group (nor by the
     * reserved center column). Returns {@code null} if no free column was
     * found within {@link #MAX_PLACEMENT_ATTEMPTS} — the caller should skip
     * that mob rather than stack it on top of an existing one.
     */
    private static Vec3 findFreeSpawnPos(Vec3 center, RandomSource rng, Set<Long> usedColumns) {
        for (int attempt = 0; attempt < MAX_PLACEMENT_ATTEMPTS; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double r = rng.nextDouble() * SPAWN_RADIUS;
            double x = center.x + Math.cos(angle) * r;
            double z = center.z + Math.sin(angle) * r;
            if (usedColumns.add(columnKey(x, z))) {
                return new Vec3(x, center.y, z);
            }
        }
        return null;
    }

    private static long columnKey(double x, double z) {
        return BlockPos.asLong(Mth.floor(x), 0, Mth.floor(z));
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
