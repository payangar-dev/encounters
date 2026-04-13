package com.payangar.encounters.config.gui;

import com.payangar.encounters.config.WeightedMob;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds an ad-hoc YACL screen that edits the four fields of a single
 * {@link WeightedMob}. On save, packs the form values into a fresh
 * {@code WeightedMob} and pushes it back into the parent {@link Option} via
 * {@link Option#requestSet}, so the parent screen still owns commit semantics.
 */
public final class WeightedMobEditScreen {

    private WeightedMobEditScreen() {}

    public static Screen build(Option<WeightedMob> sourceOption, Screen parent) {
        WeightedMob current = sourceOption.pendingValue();

        // Single-element arrays act as mutable holders captured by the lambdas
        // below — the form has no persistent backing field beyond the screen's
        // lifetime, the actual write-back happens once on save().
        final String[] idHolder = { current.id != null ? current.id : "minecraft:zombie" };
        final int[] weightHolder = { Math.max(1, current.weight) };
        final String[] labelHolder = { current.label != null ? current.label : "" };
        final String[] nbtHolder = { current.nbt != null ? current.nbt : "" };

        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("encounters.config.editMob.title"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("encounters.config.editMob.category"))
                        .option(Option.<String>createBuilder()
                                .name(Component.translatable("encounters.config.editMob.id"))
                                .description(OptionDescription.of(
                                        Component.translatable("encounters.config.editMob.id.desc")))
                                .binding(idHolder[0], () -> idHolder[0], v -> idHolder[0] = v)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("encounters.config.editMob.weight"))
                                .description(OptionDescription.of(
                                        Component.translatable("encounters.config.editMob.weight.desc")))
                                .binding(weightHolder[0], () -> weightHolder[0], v -> weightHolder[0] = v)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(1, 100)
                                        .step(1))
                                .build())
                        .option(Option.<String>createBuilder()
                                .name(Component.translatable("encounters.config.editMob.label"))
                                .description(OptionDescription.of(
                                        Component.translatable("encounters.config.editMob.label.desc")))
                                .binding(labelHolder[0], () -> labelHolder[0], v -> labelHolder[0] = v)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .option(Option.<String>createBuilder()
                                .name(Component.translatable("encounters.config.editMob.nbt"))
                                .description(OptionDescription.of(
                                        Component.translatable("encounters.config.editMob.nbt.desc")))
                                .binding(nbtHolder[0], () -> nbtHolder[0], v -> nbtHolder[0] = v)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .build())
                .save(() -> {
                    WeightedMob updated = new WeightedMob(
                            idHolder[0],
                            weightHolder[0],
                            nbtHolder[0].isBlank() ? null : nbtHolder[0],
                            labelHolder[0].isBlank() ? null : labelHolder[0]
                    );
                    sourceOption.requestSet(updated);
                })
                .build()
                .generateScreen(parent);
    }
}
