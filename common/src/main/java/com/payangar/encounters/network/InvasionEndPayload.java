package com.payangar.encounters.network;

import com.payangar.encounters.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: a portal invasion has finished. The {@code anchor} matches the
 * one previously sent in {@link InvasionStartPayload} so the client can
 * key the corresponding fade-out.
 */
public record InvasionEndPayload(BlockPos anchor) implements CustomPacketPayload {

    public static final Type<InvasionEndPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "invasion_end"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InvasionEndPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, InvasionEndPayload::anchor,
                    InvasionEndPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
