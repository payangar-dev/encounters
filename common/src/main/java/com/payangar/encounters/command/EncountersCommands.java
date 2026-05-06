package com.payangar.encounters.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.payangar.encounters.event.LightningOverchargeEvent;
import com.payangar.encounters.event.portal.NetherPortalInvasionEvent;
import com.payangar.encounters.event.portal.PortalGeometry;
import com.payangar.encounters.event.portal.PortalGeometry.PortalSite;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

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
                                .then(Commands.literal(NetherPortalInvasionEvent.ID)
                                        .executes(ctx -> triggerPortalInvasionAtSource(ctx))
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(ctx -> triggerPortalInvasion(ctx, Vec3Argument.getVec3(ctx, "pos")))
                                        )
                                )
                        )
        );
    }

    // ---------- Lightning ----------

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

    // ---------- Portal invasion ----------

    private static final int PORTAL_SEARCH_RADIUS = 16;

    private static int triggerPortalInvasionAtSource(CommandContext<CommandSourceStack> ctx) {
        return triggerPortalInvasion(ctx, ctx.getSource().getPosition());
    }

    private static int triggerPortalInvasion(CommandContext<CommandSourceStack> ctx, Vec3 from) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();

        Optional<BlockPos> portalBlock = PortalGeometry.findNearbyPortalBlock(level, from, PORTAL_SEARCH_RADIUS);
        if (portalBlock.isEmpty()) {
            src.sendFailure(Component.literal(
                    "Cannot trigger " + NetherPortalInvasionEvent.ID +
                            ": no nether portal within " + PORTAL_SEARCH_RADIUS + " blocks"));
            return 0;
        }

        Optional<PortalSite> maybeSite = PortalGeometry.analyze(level, portalBlock.get());
        if (maybeSite.isEmpty()) {
            src.sendFailure(Component.literal(
                    "Cannot trigger " + NetherPortalInvasionEvent.ID +
                            ": failed to analyze portal at " + portalBlock.get().toShortString()));
            return 0;
        }
        PortalSite site = maybeSite.get();

        Optional<Direction> face = PortalGeometry.chooseSpawnFace(level, site, level.getRandom());
        if (face.isEmpty()) {
            src.sendFailure(Component.literal(
                    "Cannot trigger " + NetherPortalInvasionEvent.ID +
                            ": no valid spawn face on portal (both sides blocked)"));
            return 0;
        }

        boolean started = NetherPortalInvasionEvent.forceTrigger(level, site, face.get());
        if (started) {
            src.sendSuccess(() -> Component.literal(
                    "Triggered " + NetherPortalInvasionEvent.ID + " (facing " + face.get() + ")"), true);
            return 1;
        }
        src.sendFailure(Component.literal(
                "Cannot trigger " + NetherPortalInvasionEvent.ID +
                        " (already running, wrong dimension, or empty roster — see logs)"));
        return 0;
    }

    // ---------- Helpers ----------

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
}
