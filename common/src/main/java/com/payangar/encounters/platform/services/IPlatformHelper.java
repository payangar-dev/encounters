package com.payangar.encounters.platform.services;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

import java.nio.file.Path;
import java.util.function.Consumer;

public interface IPlatformHelper {

    String getPlatformName();

    boolean isModLoaded(String modId);

    Path getConfigDir();

    /**
     * Finalize a freshly-created mob spawn. On NeoForge this fires
     * FinalizeSpawnEvent; on Fabric it calls Mob#finalizeSpawn directly.
     */
    void finalizeMobSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType);

    /**
     * Register a callback fired at the end of every server level tick.
     * Used by the cinematic ticker to advance active encounters.
     */
    void registerServerLevelTickListener(Consumer<ServerLevel> listener);

    /**
     * Register a callback fired when the server is shutting down. Used to
     * clear process-wide state (active cinematics, group cohesion trackers,
     * invasion locks) so the next server start has a clean slate.
     */
    void registerServerStoppingListener(Consumer<MinecraftServer> listener);

    /**
     * Register a callback fired when a level is unloaded. Used to drop
     * any per-level state still held by global statics so we don't leak a
     * dangling reference to a dead {@link ServerLevel}.
     */
    void registerServerLevelUnloadListener(Consumer<ServerLevel> listener);

    /**
     * Register a listener fired when a player right-clicks an entity.
     * On Fabric this wraps {@code UseEntityCallback}; on NeoForge it wraps
     * {@code PlayerInteractEvent.EntityInteract}.
     */
    void registerEntityInteractListener(EntityInteractListener listener);

    /**
     * Register a server-side {@link PreparableReloadListener} fired at server
     * start and on {@code /reload}. Used by the mod to (re)load its datapack
     * content (spawn pools, mob templates, etc.).
     *
     * <p>{@code id} is a stable identifier used by Fabric for reload-order
     * dependencies. NeoForge does not consume it but it is logged for
     * consistency.</p>
     */
    void registerReloadListener(ResourceLocation id, PreparableReloadListener listener);
}
