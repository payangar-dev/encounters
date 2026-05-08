package com.payangar.encounters.network;

import com.payangar.encounters.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Facade used by gameplay code to fire portal-invasion sync packets
 * without depending on the loader-specific {@link PortalSyncDispatcher}.
 * The dispatcher is set once at boot by each loader's mod bootstrap.
 *
 * <p>If the dispatcher is unset (e.g. dedicated server with no players,
 * very early init), the calls are silently no-op'd.</p>
 */
public final class EncountersNetwork {

    private static PortalSyncDispatcher dispatcher;

    private EncountersNetwork() {}

    public static void setDispatcher(PortalSyncDispatcher d) {
        dispatcher = d;
    }

    public static void sendInvasionStart(ServerLevel level, BlockPos anchor, List<BlockPos> portalBlocks) {
        if (dispatcher == null) {
            Constants.LOG.warn("[network] no portal sync dispatcher; skipping invasion start packet");
            return;
        }
        dispatcher.sendInvasionStart(level, anchor, portalBlocks);
    }

    public static void sendInvasionEnd(ServerLevel level, BlockPos anchor) {
        if (dispatcher == null) return;
        dispatcher.sendInvasionEnd(level, anchor);
    }
}
