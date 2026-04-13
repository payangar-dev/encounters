package com.payangar.encounters.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.payangar.encounters.event.LightningOverchargeEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class EncountersCommands {

    private EncountersCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("encounters")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("trigger")
                                .then(Commands.literal(LightningOverchargeEvent.ID)
                                        .executes(ctx -> triggerLightningAtSource(ctx))
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(ctx -> triggerLightning(ctx, Vec3Argument.getVec3(ctx, "pos")))
                                        )
                                )
                        )
        );
    }

    private static int triggerLightningAtSource(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Vec3 ground = findGroundBelow(src.getLevel(), src.getPosition());
        if (ground == null) {
            src.sendFailure(Component.literal(
                    "Cannot trigger " + LightningOverchargeEvent.ID + ": no solid block found below"));
            return 0;
        }
        return triggerLightning(ctx, ground);
    }

    /**
     * Scans downward from the given position looking for the first block with
     * collision and returns the position on top of it. Used to snap the debug
     * command to real ground so the cinematic has something to anchor to —
     * running the event in mid-air produces no sculk floor and falling mobs.
     */
    private static Vec3 findGroundBelow(ServerLevel level, Vec3 from) {
        int x = (int) Math.floor(from.x);
        int z = (int) Math.floor(from.z);
        int startY = (int) Math.floor(from.y);
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = startY; y >= minY; y--) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (state.blocksMotion()) {
                return new Vec3(from.x, y + 1, from.z);
            }
        }
        return null;
    }

    private static int triggerLightning(CommandContext<CommandSourceStack> ctx, Vec3 pos) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        int spawned = LightningOverchargeEvent.forceTrigger(level, pos);
        if (spawned > 0) {
            final int count = spawned;
            src.sendSuccess(() -> Component.literal(
                    "Triggered " + LightningOverchargeEvent.ID + ": " + count + " mob(s) spawned"), true);
        } else {
            src.sendFailure(Component.literal(
                    "Triggered " + LightningOverchargeEvent.ID + " but no mobs spawned (empty roster?)"));
        }
        return spawned;
    }
}
