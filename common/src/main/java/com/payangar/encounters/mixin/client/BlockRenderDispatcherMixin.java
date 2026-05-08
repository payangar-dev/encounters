package com.payangar.encounters.mixin.client;

import com.payangar.encounters.client.InvasionPortalClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the chunk-mesh contribution of any {@link Blocks#NETHER_PORTAL}
 * block whose position is currently flagged as hidden by {@link
 * InvasionPortalClientState}. The {@link
 * com.payangar.encounters.client.InvasionPortalRenderer} then draws
 * both the violet (vanilla sprite) and red (encounters sprite) layers
 * itself with cross-faded alphas — so the player sees a true
 * violet → red transition instead of two superimposed translucent
 * layers.
 *
 * <p>Hidden positions are added on invasion start and removed once the
 * fade has fully decayed back to zero after invasion end. Each toggle
 * triggers a section re-mesh so the vanilla mesh actually drops/picks
 * the affected blocks.</p>
 *
 * <p>Runs from the chunk-meshing thread, so the lookup is backed by a
 * {@code ConcurrentHashMap.newKeySet} on the state side.</p>
 */
@Mixin(BlockRenderDispatcher.class)
public abstract class BlockRenderDispatcherMixin {

    @Inject(
            method = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void encounters$skipHiddenInvasionPortal(
            BlockState state, BlockPos pos, BlockAndTintGetter level,
            PoseStack poseStack, VertexConsumer consumer, boolean checkSides, RandomSource random,
            CallbackInfo ci) {
        if (state.is(Blocks.NETHER_PORTAL) && InvasionPortalClientState.shouldHide(pos)) {
            ci.cancel();
        }
    }
}
