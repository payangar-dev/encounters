package com.payangar.encounters.platform;

import com.payangar.encounters.platform.services.IPlatformHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
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
}
