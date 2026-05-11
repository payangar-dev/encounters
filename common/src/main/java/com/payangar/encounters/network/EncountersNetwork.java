package com.payangar.encounters.network;

import com.payangar.encounters.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Thin wrapper around {@link com.payangar.encounters.platform.services.INetworkBridge}
 * exposing typed convenience methods. The bridge itself is loaded via
 * {@link java.util.ServiceLoader}; no boot-time injection required.
 */
public final class EncountersNetwork {

    private EncountersNetwork() {}

    public static void sendInvasionStart(ServerLevel level, BlockPos anchor, List<BlockPos> portalBlocks) {
        Services.NETWORK.sendInvasionStart(level, anchor, portalBlocks);
    }

    public static void sendInvasionEnd(ServerLevel level, BlockPos anchor) {
        Services.NETWORK.sendInvasionEnd(level, anchor);
    }
}
