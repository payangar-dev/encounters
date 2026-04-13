package com.payangar.encounters.config;

import net.minecraft.client.gui.screens.Screen;

/**
 * Screen factory for the YACL-generated config UI. The entire screen is
 * derived from the @AutoGen annotations on {@link EncountersConfig}.
 * Loader modules call this from their ModMenu / IConfigScreenFactory hooks.
 */
public final class ConfigScreenBuilder {

    private ConfigScreenBuilder() {}

    public static Screen create(Screen parent) {
        return EncountersConfig.handler().generateGui().generateScreen(parent);
    }
}
