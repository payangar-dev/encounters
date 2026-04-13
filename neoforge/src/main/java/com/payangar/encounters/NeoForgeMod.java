package com.payangar.encounters;

import com.payangar.encounters.command.EncountersCommands;
import com.payangar.encounters.config.ConfigScreenBuilder;
import com.payangar.encounters.event.NeoForgeLightningListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(Constants.MOD_ID)
public class NeoForgeMod {

    public NeoForgeMod(IEventBus modEventBus, ModContainer modContainer) {
        Constants.LOG.info("Initializing {} on NeoForge", Constants.MOD_NAME);
        Encounters.init();
        NeoForgeLightningListener.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(NeoForgeMod::onRegisterCommands);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modContainer.registerExtensionPoint(
                    IConfigScreenFactory.class,
                    (container, parent) -> ConfigScreenBuilder.create(parent));
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        EncountersCommands.register(event.getDispatcher());
    }
}
