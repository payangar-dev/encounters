package com.payangar.encounters.config.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.payangar.encounters.config.WeightedMob;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Custom YACL controller for {@link WeightedMob} list entries. Each entry is
 * rendered by a {@link ControllerWidget} subclass — the right-hand value text
 * shows a one-line summary (id, weight, optional label, NBT marker) and a click
 * (or Enter/Space when focused) opens {@link WeightedMobEditScreen} where the
 * user edits the four fields individually.
 *
 * <p>The sub-screen pushes its result back via {@link Option#requestSet}; the
 * widget's {@code drawValueText} reads through {@link #formatValue()} on every
 * frame, so the new label appears automatically.
 */
public class WeightedMobController implements Controller<WeightedMob> {

    private final Option<WeightedMob> option;

    public WeightedMobController(Option<WeightedMob> option) {
        this.option = option;
    }

    @Override
    public Option<WeightedMob> option() {
        return option;
    }

    @Override
    public Component formatValue() {
        WeightedMob mob = option.pendingValue();
        if (mob == null || mob.id == null || mob.id.isBlank()) {
            return Component.translatable("encounters.config.weightedMob.empty");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(mob.id).append(" \u00d7").append(mob.weight);
        if (mob.label != null && !mob.label.isBlank()) {
            sb.append(" \u2014 ").append(mob.label);
        }
        if (mob.nbt != null && !mob.nbt.isBlank()) {
            sb.append(" \u2699");
        }
        return Component.literal(sb.toString());
    }

    @Override
    public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> dim) {
        return new WeightedMobWidget(this, screen, dim);
    }

    public static class WeightedMobWidget extends ControllerWidget<WeightedMobController> {

        public WeightedMobWidget(WeightedMobController control, YACLScreen screen, Dimension<Integer> dim) {
            super(control, screen, dim);
        }

        @Override
        protected int getHoveredControlWidth() {
            return getUnhoveredControlWidth();
        }

        private void openEditScreen() {
            playDownSound();
            Minecraft.getInstance().setScreen(
                    WeightedMobEditScreen.build(control.option(), screen));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isMouseOver(mouseX, mouseY) && isAvailable()) {
                openEditScreen();
                return true;
            }
            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!focused) return false;
            if (keyCode == InputConstants.KEY_RETURN
                    || keyCode == InputConstants.KEY_SPACE
                    || keyCode == InputConstants.KEY_NUMPADENTER) {
                openEditScreen();
                return true;
            }
            return false;
        }

        @Override
        public boolean canReset() {
            return false;
        }
    }
}
