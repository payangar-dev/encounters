package com.payangar.encounters.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Loader-specific dispatch of S2C portal-invasion sync packets. Each
 * loader provides an implementation that broadcasts a payload to every
 * player in the given {@link ServerLevel}.
 *
 * <p>Invasions are rare and short-lived, so level-wide broadcasts are
 * cheap enough; using {@code tracking}-style scoping would risk a
 * client receiving START but missing END after walking away from the
 * portal area, leaving a stale entry in the client tracker.</p>
 */
public interface PortalSyncDispatcher {

    void sendToLevel(ServerLevel level, CustomPacketPayload payload);

    default void sendInvasionStart(ServerLevel level, BlockPos anchor, List<BlockPos> portalBlocks) {
        sendToLevel(level, new InvasionStartPayload(anchor, portalBlocks));
    }

    default void sendInvasionEnd(ServerLevel level, BlockPos anchor) {
        sendToLevel(level, new InvasionEndPayload(anchor));
    }
}
