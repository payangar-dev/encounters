package com.payangar.encounters.network;

import com.payangar.encounters.platform.services.INetworkBridge;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge fan-out via {@link PacketDistributor#sendToPlayersInDimension}.
 * Players whose connection didn't negotiate the optional Encounters payload
 * type (vanilla, older builds) silently drop the packet — the registrar is
 * built with {@code .optional()} on the mod side.
 */
public final class NeoForgeNetworkBridge implements INetworkBridge {

    @Override
    public void sendToLevel(ServerLevel level, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersInDimension(level, payload);
    }
}
