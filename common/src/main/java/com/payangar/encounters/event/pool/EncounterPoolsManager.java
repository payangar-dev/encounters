package com.payangar.encounters.event.pool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.payangar.encounters.Constants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime registry of {@link SpawnPool} entries loaded from the datapack
 * directory {@code data/<namespace>/encounters/spawn_pools/}.
 *
 * <p>Hooks into the vanilla resource reload pipeline as a
 * {@link SimpleJsonResourceReloadListener} so the registry is rebuilt on
 * server start AND on every {@code /reload}. The active map is stored in a
 * {@code volatile} field; pool consumers (event triggers, scanners) read
 * the latest snapshot without locking — the cost of a stale read for a
 * single tick is harmless given that triggers re-resolve the roster every
 * time they fire.</p>
 *
 * <p>Lookup is {@link #get(ResourceLocation)}; missing pools return
 * {@link SpawnPool#EMPTY} so callers never need a null check. Per-pool
 * parse failures are logged but never abort the reload — surviving pools
 * stay loadable, broken pools become empty.</p>
 */
public final class EncounterPoolsManager extends SimpleJsonResourceReloadListener {

    /** Subdirectory under {@code data/<namespace>/} where pool JSON files live. */
    public static final String DIRECTORY = "encounters/spawn_pools";

    /**
     * Identifier used by Fabric reload-ordering. NeoForge does not consume it
     * but logs it for traceability when a listener registers.
     */
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "spawn_pools");

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final EncounterPoolsManager INSTANCE = new EncounterPoolsManager();

    private volatile Map<ResourceLocation, SpawnPool> pools = Map.of();

    private EncounterPoolsManager() {
        super(GSON, DIRECTORY);
    }

    public static EncounterPoolsManager getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the pool registered under {@code id}, or
     * {@link SpawnPool#EMPTY} when no such pool exists. The returned pool
     * is the same instance shared by every caller this reload cycle —
     * pools are immutable so sharing is safe.
     */
    public SpawnPool get(ResourceLocation id) {
        return pools.getOrDefault(id, SpawnPool.EMPTY);
    }

    /** Snapshot of every loaded pool. Useful for debug commands. */
    public Map<ResourceLocation, SpawnPool> getAll() {
        return pools;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, SpawnPool> next = new HashMap<>(resources.size());
        int kept = 0;
        int failed = 0;
        for (Map.Entry<ResourceLocation, JsonElement> e : resources.entrySet()) {
            ResourceLocation poolId = e.getKey();
            DataResult<SpawnPool> result = SpawnPool.CODEC.parse(JsonOps.INSTANCE, e.getValue());
            SpawnPool parsed = result.resultOrPartial(err ->
                    Constants.LOG.error("[encounters] spawn pool '{}' failed to parse: {}", poolId, err))
                    .orElse(null);
            if (parsed != null) {
                next.put(poolId, parsed);
                kept++;
            } else {
                failed++;
            }
        }
        this.pools = Map.copyOf(next);
        Constants.LOG.info("[encounters] reloaded {} spawn pool(s){}",
                kept, failed > 0 ? ", " + failed + " failed" : "");
    }
}
