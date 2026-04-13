package com.payangar.encounters.config.gui;

import com.payangar.encounters.config.WeightedMob;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.controller.ControllerBuilder;

public class WeightedMobControllerBuilder implements ControllerBuilder<WeightedMob> {

    private final Option<WeightedMob> option;

    private WeightedMobControllerBuilder(Option<WeightedMob> option) {
        this.option = option;
    }

    public static WeightedMobControllerBuilder create(Option<WeightedMob> option) {
        return new WeightedMobControllerBuilder(option);
    }

    @Override
    public Controller<WeightedMob> build() {
        return new WeightedMobController(option);
    }
}
