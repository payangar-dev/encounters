package com.payangar.encounters.event.skirmish;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.event.ActiveEncounterTracker;
import com.payangar.encounters.event.MobRoster;
import com.payangar.encounters.event.cinematic.CinematicTicker;
import com.payangar.encounters.event.pool.EncounterPoolsManager;
import com.payangar.encounters.event.pool.SpawnPool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/**
 * Identity, roster caches, reward loot table and force-trigger entry point
 * for the patrol skirmish event. All scanner-driven gating (enabled, interval,
 * trigger chance, concurrency cap, post-event cooldown, day-only, biome,
 * clearing footprint) lives in {@link SkirmishScanner}; the post-event
 * cooldown timestamp is owned by {@link ActiveEncounterTracker}. This class
 * only holds what is intrinsically patrol-skirmish-specific.
 *
 * <p>Two rosters are cached separately because each faction draws from its
 * own mob pool. Both invalidate together on config reload via the single
 * {@link #invalidateRoster} entry point registered in
 * {@link com.payangar.encounters.event.EncounterRegistry}.</p>
 */
public final class PatrolSkirmishEvent {

    public static final String ID = "patrol_skirmish";

    /** Datapack pool location: {@code data/encounters/spawn_pools/patrol_skirmish_villager.json}. */
    public static final ResourceLocation VILLAGER_POOL_ID =
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "patrol_skirmish_villager");
    /** Datapack pool location: {@code data/encounters/spawn_pools/patrol_skirmish_illager.json}. */
    public static final ResourceLocation ILLAGER_POOL_ID =
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "patrol_skirmish_illager");

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
    private static SpawnPool cachedVillagerPool;

    private static MobRoster cachedIllagerRoster;
    private static SpawnPool cachedIllagerPool;

    private PatrolSkirmishEvent() {}

    public static MobRoster villagerRoster() {
        SpawnPool pool = EncounterPoolsManager.getInstance().get(VILLAGER_POOL_ID);
        if (cachedVillagerRoster == null || cachedVillagerPool != pool) {
            cachedVillagerRoster = MobRoster.resolve(pool, ID + ".villager");
            cachedVillagerPool = pool;
        }
        return cachedVillagerRoster;
    }

    public static MobRoster illagerRoster() {
        SpawnPool pool = EncounterPoolsManager.getInstance().get(ILLAGER_POOL_ID);
        if (cachedIllagerRoster == null || cachedIllagerPool != pool) {
            cachedIllagerRoster = MobRoster.resolve(pool, ID + ".illager");
            cachedIllagerPool = pool;
        }
        return cachedIllagerRoster;
    }

    public static void invalidateRoster() {
        cachedVillagerRoster = null;
        cachedVillagerPool = null;
        cachedIllagerRoster = null;
        cachedIllagerPool = null;
    }

    /**
     * Starts a skirmish anchored at {@code pos} on {@code level}. Used by the
     * debug command (bypasses every scanner gate) and by {@link SkirmishScanner}
     * once its audio tease elapses (the scanner applies the gates upstream).
     *
     * <p>Sanity checks kept here for resilience to both call paths: overworld
     * dimension, both rosters non-empty, concurrency cap. The minimum-distance
     * gate is intentionally <em>not</em> rechecked — the debug command may
     * force two adjacent skirmishes for testing.</p>
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
        if (villagerRoster().isEmpty()) {
            Constants.LOG.warn("[{}] refused: villager roster is empty", ID);
            return false;
        }
        if (illagerRoster().isEmpty()) {
            Constants.LOG.warn("[{}] refused: illager roster is empty", ID);
            return false;
        }
        int activeCount = ActiveEncounterTracker.activeCount(level, ID);
        if (activeCount >= config.skirmish.concurrency.maxConcurrent) {
            Constants.LOG.info("[{}] refused: max concurrent reached ({}/{})",
                    ID, activeCount, config.skirmish.concurrency.maxConcurrent);
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
}
