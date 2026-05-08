package com.payangar.encounters.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.payangar.encounters.Constants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Per-frame overlay that paints two cross-faded layers on every portal
 * block in an active {@link com.payangar.encounters.event.portal.PortalInvasion}:
 * the vanilla violet sprite at alpha {@code 1 - fade} and the
 * encounters red sprite at alpha {@code fade}. The vanilla portal's
 * own chunk-mesh contribution is suppressed for these positions by
 * {@link com.payangar.encounters.mixin.client.BlockRenderDispatcherMixin},
 * so the overlay fully owns the rendering — no double-up.
 *
 * <p>Quads are flat planes outset by {@value #OUTSET} from the vanilla
 * model's outer faces ({@code 0.625} / {@code 0.375} in block-local
 * coords); small enough to read as the same plane, far enough to dodge
 * z-fighting on most GPUs.</p>
 *
 * <p>Both sprites live in the block atlas, so a single shader/texture
 * binding and a single draw call cover both layers.</p>
 *
 * <p>Renders without lightmap modulation — portals are emissive in
 * vanilla and the overlay should read the same way.</p>
 */
public final class InvasionPortalRenderer {

    private static final ResourceLocation RED_SPRITE_ID =
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "block/nether_portal_red");
    private static final ResourceLocation VIOLET_SPRITE_ID =
            ResourceLocation.withDefaultNamespace("block/nether_portal");

    /** Distance outside the vanilla portal model's outer faces ({@code 0.625} / {@code 0.375}). */
    private static final float OUTSET = 0.001f;

    private static final float FRONT_OFFSET = 0.625f + OUTSET;
    private static final float BACK_OFFSET = 0.375f - OUTSET;

    /**
     * Per-vertex alpha cap for the violet (vanilla) sprite layer. Tuned so
     * that {@code fade=0} produces the same on-screen brightness as the
     * vanilla portal — without it, the overlay quad would render at vertex
     * alpha 255, making the portal look brighter than vanilla and creating
     * a visible "pop" when an invasion starts/ends.
     */
    private static final int MAX_ALPHA_VIOLET = 40;

    /**
     * Per-vertex alpha cap for the red (encounters) sprite layer. Independent
     * from the violet cap because the red texture's intrinsic per-pixel
     * alpha distribution is not identical, and visually balancing the two
     * cross-fade endpoints requires tuning each side separately.
     */
    private static final int MAX_ALPHA_RED = 150;

    private InvasionPortalRenderer() {}

    public static void render(PoseStack poseStack, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        Iterable<InvasionPortalClientState.Entry> entries = InvasionPortalClientState.activeEntries();
        if (!entries.iterator().hasNext()) return;

        TextureAtlas atlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite redSprite = atlas.getSprite(RED_SPRITE_ID);
        TextureAtlasSprite violetSprite = atlas.getSprite(VIOLET_SPRITE_ID);
        if (redSprite == null || violetSprite == null) return;

        Vec3 camPos = camera.getPosition();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        boolean any = false;
        for (InvasionPortalClientState.Entry entry : entries) {
            float fade = entry.renderFade(partialTick);
            int redAlpha = clampAlpha(fade, MAX_ALPHA_RED);
            int violetAlpha = clampAlpha(1f - fade, MAX_ALPHA_VIOLET);
            if (redAlpha == 0 && violetAlpha == 0) continue;

            for (BlockPos pos : entry.portalBlocks) {
                BlockState state = level.getBlockState(pos);
                if (!state.is(Blocks.NETHER_PORTAL)) continue;
                Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
                if (violetAlpha > 0) {
                    emitPortalQuads(builder, matrix, pos, axis, violetAlpha,
                            violetSprite.getU0(), violetSprite.getU1(),
                            violetSprite.getV0(), violetSprite.getV1());
                }
                if (redAlpha > 0) {
                    emitPortalQuads(builder, matrix, pos, axis, redAlpha,
                            redSprite.getU0(), redSprite.getU1(),
                            redSprite.getV0(), redSprite.getV1());
                }
                any = true;
            }
        }

        MeshData mesh = builder.build();
        if (any && mesh != null) {
            BufferUploader.drawWithShader(mesh);
        } else if (mesh != null) {
            mesh.close();
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static int clampAlpha(float v, int cap) {
        return Math.min(cap, Math.max(0, Math.round(v * cap)));
    }

    /**
     * Adds eight vertices (two double-sided quads) for one portal block.
     * For {@code axis=X} the portal slice lies in the XY plane at fixed Z;
     * for {@code axis=Z} it's in the YZ plane at fixed X.
     */
    private static void emitPortalQuads(BufferBuilder b, Matrix4f m, BlockPos pos, Direction.Axis axis,
                                        int alpha, float u0, float u1, float v0, float v1) {
        float x = pos.getX();
        float y = pos.getY();
        float z = pos.getZ();

        if (axis == Direction.Axis.X) {
            float zFront = z + FRONT_OFFSET;
            float zBack = z + BACK_OFFSET;
            // +Z face (front)
            b.addVertex(m, x,         y,         zFront).setColor(255, 255, 255, alpha).setUv(u0, v1);
            b.addVertex(m, x + 1f,    y,         zFront).setColor(255, 255, 255, alpha).setUv(u1, v1);
            b.addVertex(m, x + 1f,    y + 1f,    zFront).setColor(255, 255, 255, alpha).setUv(u1, v0);
            b.addVertex(m, x,         y + 1f,    zFront).setColor(255, 255, 255, alpha).setUv(u0, v0);
            // -Z face (back) — wound opposite
            b.addVertex(m, x,         y,         zBack).setColor(255, 255, 255, alpha).setUv(u1, v1);
            b.addVertex(m, x,         y + 1f,    zBack).setColor(255, 255, 255, alpha).setUv(u1, v0);
            b.addVertex(m, x + 1f,    y + 1f,    zBack).setColor(255, 255, 255, alpha).setUv(u0, v0);
            b.addVertex(m, x + 1f,    y,         zBack).setColor(255, 255, 255, alpha).setUv(u0, v1);
        } else {
            float xFront = x + FRONT_OFFSET;
            float xBack = x + BACK_OFFSET;
            // +X face (front)
            b.addVertex(m, xFront, y,         z).setColor(255, 255, 255, alpha).setUv(u1, v1);
            b.addVertex(m, xFront, y + 1f,    z).setColor(255, 255, 255, alpha).setUv(u1, v0);
            b.addVertex(m, xFront, y + 1f,    z + 1f).setColor(255, 255, 255, alpha).setUv(u0, v0);
            b.addVertex(m, xFront, y,         z + 1f).setColor(255, 255, 255, alpha).setUv(u0, v1);
            // -X face (back) — wound opposite
            b.addVertex(m, xBack,  y,         z).setColor(255, 255, 255, alpha).setUv(u0, v1);
            b.addVertex(m, xBack,  y,         z + 1f).setColor(255, 255, 255, alpha).setUv(u1, v1);
            b.addVertex(m, xBack,  y + 1f,    z + 1f).setColor(255, 255, 255, alpha).setUv(u1, v0);
            b.addVertex(m, xBack,  y + 1f,    z).setColor(255, 255, 255, alpha).setUv(u0, v0);
        }
    }
}
