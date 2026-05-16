package com.payangar.encounters.platform;

import com.payangar.encounters.Constants;
import com.payangar.encounters.platform.services.EntityInteractListener;
import com.payangar.encounters.platform.services.IPlatformHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.nio.file.Path;
import java.util.function.Consumer;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public void finalizeMobSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType) {
        EventHooks.finalizeMobSpawn(mob, level, difficulty, spawnType, null);
    }

    @Override
    public void registerServerLevelTickListener(Consumer<ServerLevel> listener) {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            Level level = event.getLevel();
            if (level instanceof ServerLevel serverLevel) {
                listener.accept(serverLevel);
            }
        });
    }

    @Override
    public void registerServerStoppingListener(Consumer<MinecraftServer> listener) {
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> listener.accept(event.getServer()));
    }

    @Override
    public void registerServerLevelUnloadListener(Consumer<ServerLevel> listener) {
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                listener.accept(serverLevel);
            }
        });
    }

    @Override
    public void registerEntityInteractListener(EntityInteractListener listener) {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> {
            InteractionResult result = listener.onInteract(event.getEntity(), event.getTarget(), event.getHand());
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
    }

    @Override
    public void registerReloadListener(ResourceLocation id, PreparableReloadListener listener) {
        // NeoForge fires AddReloadListenerEvent once per server start (and on /reload).
        // The id is not consumed by the platform but logged so reload ordering issues
        // can be traced back to a specific module if they ever surface.
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> {
            event.addListener(listener);
            Constants.LOG.debug("Registered reload listener '{}' on NeoForge", id);
        });
    }
}
