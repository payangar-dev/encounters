package com.payangar.encounters;

import com.payangar.encounters.command.EncountersCommands;
import com.payangar.encounters.event.FabricLightningListener;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class FabricMod implements ModInitializer {

    @Override
    public void onInitialize() {
        Constants.LOG.info("Initializing {} on Fabric", Constants.MOD_NAME);
        Encounters.init();
        FabricLightningListener.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                EncountersCommands.register(dispatcher));
    }
}
