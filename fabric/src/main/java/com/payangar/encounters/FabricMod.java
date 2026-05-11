package com.payangar.encounters;

import com.payangar.encounters.command.EncountersCommands;
import com.payangar.encounters.event.FabricLightningListener;
import com.payangar.encounters.network.InvasionEndPayload;
import com.payangar.encounters.network.InvasionStartPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class FabricMod implements ModInitializer {

    @Override
    public void onInitialize() {
        Constants.LOG.info("Initializing {} on Fabric", Constants.MOD_NAME);
        Encounters.init();
        registerPayloads();
        FabricLightningListener.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                EncountersCommands.register(dispatcher));
    }

    private static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(InvasionStartPayload.TYPE, InvasionStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(InvasionEndPayload.TYPE, InvasionEndPayload.CODEC);
    }
}
