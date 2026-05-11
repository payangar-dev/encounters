package com.payangar.encounters;

import com.payangar.encounters.client.InvasionPortalClientState;
import com.payangar.encounters.client.NeoForgeEncountersClient;
import com.payangar.encounters.command.EncountersCommands;
import com.payangar.encounters.config.ConfigScreenBuilder;
import com.payangar.encounters.event.NeoForgeLightningListener;
import com.payangar.encounters.network.InvasionEndPayload;
import com.payangar.encounters.network.InvasionStartPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(Constants.MOD_ID)
public class NeoForgeMod {

    public NeoForgeMod(IEventBus modEventBus, ModContainer modContainer) {
        Constants.LOG.info("Initializing {} on NeoForge", Constants.MOD_NAME);
        Encounters.init();
        modEventBus.addListener(NeoForgeMod::onRegisterPayloads);
        NeoForgeLightningListener.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(NeoForgeMod::onRegisterCommands);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForgeEncountersClient.register(NeoForge.EVENT_BUS);
            NeoForge.EVENT_BUS.addListener(NeoForgeMod::onClientLoggingOut);
            modContainer.registerExtensionPoint(
                    IConfigScreenFactory.class,
                    (container, parent) -> ConfigScreenBuilder.create(parent));
        }
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(InvasionStartPayload.TYPE, InvasionStartPayload.CODEC,
                NeoForgeMod::handleInvasionStart);
        registrar.playToClient(InvasionEndPayload.TYPE, InvasionEndPayload.CODEC,
                NeoForgeMod::handleInvasionEnd);
    }

    private static void handleInvasionStart(InvasionStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                InvasionPortalClientState.onInvasionStart(payload.anchor(), payload.portalBlocks()));
    }

    private static void handleInvasionEnd(InvasionEndPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                InvasionPortalClientState.onInvasionEnd(payload.anchor()));
    }

    private static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        InvasionPortalClientState.clear();
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        EncountersCommands.register(event.getDispatcher());
    }
}
