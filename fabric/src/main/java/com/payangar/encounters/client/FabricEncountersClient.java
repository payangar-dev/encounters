package com.payangar.encounters.client;

import com.payangar.encounters.network.InvasionEndPayload;
import com.payangar.encounters.network.InvasionStartPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * Fabric client bootstrap — wires the S2C payload receivers, the
 * per-frame overlay render hook, the per-tick fade advance, and a
 * disconnect cleanup that drops any leftover invasion state.
 */
public final class FabricEncountersClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(InvasionStartPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        InvasionPortalClientState.onInvasionStart(payload.anchor(), payload.portalBlocks())));

        ClientPlayNetworking.registerGlobalReceiver(InvasionEndPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        InvasionPortalClientState.onInvasionEnd(payload.anchor())));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null) {
                InvasionPortalClientState.tickFade();
            }
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx ->
                InvasionPortalRenderer.render(
                        ctx.matrixStack(),
                        ctx.camera(),
                        ctx.tickCounter().getGameTimeDeltaPartialTick(false)));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> InvasionPortalClientState.clear());
    }
}
