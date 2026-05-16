package com.payangar.encounters.platform;

import com.payangar.encounters.platform.services.EntityInteractListener;
import com.payangar.encounters.platform.services.IPlatformHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public void finalizeMobSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType) {
        mob.finalizeSpawn(level, difficulty, spawnType, null);
    }

    @Override
    public void registerServerLevelTickListener(Consumer<ServerLevel> listener) {
        ServerTickEvents.END_WORLD_TICK.register(listener::accept);
    }

    @Override
    public void registerServerStoppingListener(Consumer<MinecraftServer> listener) {
        ServerLifecycleEvents.SERVER_STOPPING.register(listener::accept);
    }

    @Override
    public void registerServerLevelUnloadListener(Consumer<ServerLevel> listener) {
        ServerWorldEvents.UNLOAD.register((server, world) -> listener.accept(world));
    }

    @Override
    public void registerEntityInteractListener(EntityInteractListener listener) {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                listener.onInteract(player, entity, hand));
    }

    @Override
    public void registerReloadListener(ResourceLocation id, PreparableReloadListener listener) {
        // Fabric's reload API needs a listener that exposes a stable id.
        // We wrap the loader-agnostic listener and forward its reload() call.
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return id;
            }

            @Override
            public CompletableFuture<Void> reload(PreparationBarrier barrier,
                                                  ResourceManager manager,
                                                  ProfilerFiller preparationsProfiler,
                                                  ProfilerFiller reloadProfiler,
                                                  Executor backgroundExecutor,
                                                  Executor gameExecutor) {
                return listener.reload(barrier, manager, preparationsProfiler, reloadProfiler,
                        backgroundExecutor, gameExecutor);
            }

            @Override
            public String getName() {
                return listener.getName();
            }
        });
    }
}
