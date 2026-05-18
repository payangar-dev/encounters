package com.payangar.encounters.config;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.gui.screens.Screen;

/**
 * Screen factory for the Cloth Config AutoConfig-generated GUI. The entire
 * screen is derived from the annotations on {@link EncountersConfig} and its
 * sub-Settings. Loader modules call this from their ModMenu /
 * IConfigScreenFactory hooks.
 */
public final class ConfigScreenBuilder {

    private ConfigScreenBuilder() {}

    public static Screen create(Screen parent) {
        return AutoConfig.getConfigScreen(EncountersConfig.class, parent).get();
    }
}
