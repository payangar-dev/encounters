package com.payangar.encounters.network;

import com.payangar.encounters.platform.services.INetworkBridge;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric-side fan-out: send a payload to every player in {@code level}.
 * Players without the Encounters payload type registered (vanilla or
 * incompatible Fabric build) silently drop the packet — Fabric's
 * networking API is non-fatal in that case.
 */
public final class FabricNetworkBridge implements INetworkBridge {

    @Override
    public void sendToLevel(ServerLevel level, CustomPacketPayload payload) {
        for (ServerPlayer player : level.players()) {
            if (ServerPlayNetworking.canSend(player, payload.type().id())) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
