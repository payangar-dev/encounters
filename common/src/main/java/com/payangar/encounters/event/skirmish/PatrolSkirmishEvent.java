package com.payangar.encounters.event.skirmish;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.config.WeightedMob;
import com.payangar.encounters.event.ActiveEncounterTracker;
import com.payangar.encounters.event.MobRoster;
import com.payangar.encounters.event.cinematic.CinematicTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Coordinates the patrol_skirmish event — a bilateral encounter between a
 * villager-side patrol (golems, guards) and an illager-side patrol.
 *
 * <p>This class currently exposes only the event identity and roster caches.
 * Trigger logic, multi-instance bookkeeping and the cinematic reward are
 * built up in later steps.</p>
 *
 * <p>Two rosters are cached separately because each faction draws from its
 * own mob pool. Both invalidate together on config reload via the single
 * {@link #invalidateRoster} entry point registered in
 * {@link com.payangar.encounters.event.EncounterRegistry}.</p>
 */
public final class PatrolSkirmishEvent {

    public static final String ID = "patrol_skirmish";

    /**
     * Loot table rolled into the reward chest placed by the cascade leader at
     * the end of a successful skirmish. The default at
     * {@code data/encounters/loot_table/reward/patrol_skirmish_thanks.json}
     * mixes emeralds, experience bottles, enchanted books and rare staples
     * (golden apple, diamond, totem) — datapack overrides replace the table
     * entirely. When Lootr is installed, the placed chest gets its
     * per-player instanced behaviour automatically — we just place a vanilla
     * {@code minecraft:chest} block with this loot table reference.
     */
    public static final ResourceKey<LootTable> REWARD_LOOT_TABLE = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "reward/patrol_skirmish_thanks"));

    private static MobRoster cachedVillagerRoster;
    private static List<WeightedMob> cachedVillagerSource;

    private static MobRoster cachedIllagerRoster;
    private static List<WeightedMob> cachedIllagerSource;

    /**
     * Server tick at which the last skirmish on any level ended. Combined
     * with {@link EncountersConfig#patrolSkirmishCooldownTicks} to enforce a
     * world-wide cooldown between skirmishes. Stays at {@link Long#MIN_VALUE}
     * until the first skirmish ends, so the cooldown is trivially satisfied
     * at server start.
     */
    private static volatile long lastSkirmishEndTick = Long.MIN_VALUE;

    private PatrolSkirmishEvent() {}

    public static MobRoster villagerRoster(EncountersConfig config) {
        if (cachedVillagerRoster == null || cachedVillagerSource != config.patrolSkirmishVillagerMobs) {
            cachedVillagerRoster = MobRoster.resolve(config.patrolSkirmishVillagerMobs, ID + ".villager");
            cachedVillagerSource = config.patrolSkirmishVillagerMobs;
        }
        return cachedVillagerRoster;
    }

    public static MobRoster illagerRoster(EncountersConfig config) {
        if (cachedIllagerRoster == null || cachedIllagerSource != config.patrolSkirmishIllagerMobs) {
            cachedIllagerRoster = MobRoster.resolve(config.patrolSkirmishIllagerMobs, ID + ".illager");
            cachedIllagerSource = config.patrolSkirmishIllagerMobs;
        }
        return cachedIllagerRoster;
    }

    public static void invalidateRoster() {
        cachedVillagerRoster = null;
        cachedVillagerSource = null;
        cachedIllagerRoster = null;
        cachedIllagerSource = null;
    }

    /**
     * Whether the periodic scanner is allowed to start a skirmish on
     * {@code level} right now. Gates on the per-level concurrency cap and
     * the world-wide post-skirmish cooldown.
     */
    public static boolean canScannerTrigger(ServerLevel level, EncountersConfig config) {
        if (ActiveEncounterTracker.activeCount(level, ID) >= config.patrolSkirmishMaxConcurrent) return false;
        long now = level.getGameTime();
        long cooldown = config.patrolSkirmishCooldownTicks;
        return (now - lastSkirmishEndTick) >= cooldown;
    }

    /**
     * Starts a skirmish anchored at {@code pos} on {@code level}. Respects the
     * dimension and roster sanity checks plus the per-level concurrency cap;
     * bypasses the post-skirmish cooldown so the debug command stays usable
     * during testing.
     *
     * @return {@code true} if the skirmish was started, {@code false} if
     *         blocked by a sanity check (see logs for the reason).
     */
    public static synchronized boolean forceTrigger(ServerLevel level, Vec3 pos) {
        if (level.dimension() != Level.OVERWORLD) {
            Constants.LOG.warn("[{}] refused: not in overworld (dim={})", ID, level.dimension().location());
            return false;
        }
        EncountersConfig config = EncountersConfig.get();
        if (villagerRoster(config).isEmpty()) {
            Constants.LOG.warn("[{}] refused: villager roster is empty", ID);
            return false;
        }
        if (illagerRoster(config).isEmpty()) {
            Constants.LOG.warn("[{}] refused: illager roster is empty", ID);
            return false;
        }
        int activeCount = ActiveEncounterTracker.activeCount(level, ID);
        if (activeCount >= config.patrolSkirmishMaxConcurrent) {
            Constants.LOG.info("[{}] refused: max concurrent reached ({}/{})",
                    ID, activeCount, config.patrolSkirmishMaxConcurrent);
            return false;
        }

        BlockPos anchor = BlockPos.containing(pos);
        PatrolSkirmish skirmish = new PatrolSkirmish(level, anchor);
        ActiveEncounterTracker.register(skirmish);
        CinematicTicker.start(skirmish);
        Constants.LOG.info("[{}] skirmish started at ({}, {}, {})",
                ID, anchor.getX(), anchor.getY(), anchor.getZ());
        return true;
    }

    /**
     * Called by {@link PatrolSkirmish} when it finishes or is abandoned.
     * Drops the tracker entry and records the cooldown timestamp so the next
     * scanner trigger waits {@link EncountersConfig#patrolSkirmishCooldownTicks}.
     */
    static synchronized void releaseSkirmish(PatrolSkirmish skirmish) {
        ActiveEncounterTracker.unregister(skirmish);
        lastSkirmishEndTick = skirmish.level().getGameTime();
        Constants.LOG.info("[{}] skirmish released at tick {}", ID, lastSkirmishEndTick);
    }

    /** Drops the cooldown timestamp. Used at server stop. */
    public static void releaseAll() {
        lastSkirmishEndTick = Long.MIN_VALUE;
    }
}
