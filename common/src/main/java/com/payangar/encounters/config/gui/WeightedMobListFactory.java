package com.payangar.encounters.config.gui;

import com.payangar.encounters.config.WeightedMob;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.controller.ControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigField;
import dev.isxander.yacl3.config.v2.api.autogen.ListGroup;
import dev.isxander.yacl3.config.v2.api.autogen.OptionAccess;

import java.util.List;

/**
 * Wires {@link WeightedMobController} into YACL's {@code @ListGroup} machinery.
 * Implements both the value factory (used when the user clicks "+") and the
 * controller factory (used to render each entry).
 */
public class WeightedMobListFactory
        implements ListGroup.ValueFactory<WeightedMob>, ListGroup.ControllerFactory<WeightedMob> {

    @Override
    public WeightedMob provideNewValue() {
        return new WeightedMob("minecraft:zombie", 1, null, null);
    }

    @Override
    public ControllerBuilder<WeightedMob> createController(
            ListGroup annotation,
            ConfigField<List<WeightedMob>> field,
            OptionAccess storage,
            Option<WeightedMob> option) {
        return WeightedMobControllerBuilder.create(option);
    }
}
