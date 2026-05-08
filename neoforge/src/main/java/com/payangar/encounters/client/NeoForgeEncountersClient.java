package com.payangar.encounters.client;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * NeoForge client bootstrap — registers the per-frame overlay render
 * hook and the per-tick fade advance on the game event bus. Network
 * receiver registration lives on the mod event bus and is wired in
 * {@link com.payangar.encounters.NeoForgeMod}.
 */
public final class NeoForgeEncountersClient {

    private NeoForgeEncountersClient() {}

    public static void register(IEventBus gameBus) {
        gameBus.addListener(NeoForgeEncountersClient::onRenderLevelStage);
        gameBus.addListener(NeoForgeEncountersClient::onClientTick);
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        InvasionPortalRenderer.render(
                event.getPoseStack(),
                event.getCamera(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level != null) {
            InvasionPortalClientState.tickFade();
        }
    }
}
