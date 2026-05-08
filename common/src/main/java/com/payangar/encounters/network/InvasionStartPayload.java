package com.payangar.encounters.network;

import com.payangar.encounters.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * S2C: a portal invasion has started. Carries the unique anchor (the
 * portal's bottom-row centre block) used as a key on the client tracker,
 * plus the list of nether portal block positions that should glow red
 * for the duration of the cinematic.
 */
public record InvasionStartPayload(BlockPos anchor, List<BlockPos> portalBlocks) implements CustomPacketPayload {

    public static final Type<InvasionStartPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "invasion_start"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InvasionStartPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, InvasionStartPayload::anchor,
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), InvasionStartPayload::portalBlocks,
                    InvasionStartPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
