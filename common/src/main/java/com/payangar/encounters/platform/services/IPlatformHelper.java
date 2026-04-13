package com.payangar.encounters.platform.services;

import net.minecraft.server.level.ServerLevel;
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
}
