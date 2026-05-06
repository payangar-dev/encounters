package com.payangar.encounters.event.cohesion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Tracks a single encounter group and keeps its members anchored around a
 * leader. The first surviving member is the leader; followers that drift
 * outside the configured radius are issued a navigation order back toward it.
 *
 * <p>Followers in active combat ({@code getTarget() != null}) and AI-locked
 * mobs (e.g. during the cinematic IMPACT phase) are left alone, so cohesion
 * never overrides combat or the spawn lockdown.</p>
 *
 * <p>Cohesion settings (enabled toggle and radius) are read through suppliers
 * passed at construction time so the class stays event-agnostic. Each event
 * wires its own config fields. Live edits take effect on the next tick.</p>
 */
public final class GroupCohesion {

    private static final double FOLLOW_SPEED = 1.1;
    private static final double FOLLOW_STAND_OFF = 2.0;

    private final ServerLevel level;
    private final List<Mob> members;
    private final BooleanSupplier enabled;
    private final IntSupplier radius;
    private int leaderIndex = 0;
    private boolean finished = false;

    public GroupCohesion(ServerLevel level, List<Mob> members, BooleanSupplier enabled, IntSupplier radius) {
        this.level = level;
        this.members = new ArrayList<>(members);
        this.enabled = enabled;
        this.radius = radius;
    }

    public ServerLevel level() {
        return level;
    }

    public boolean isFinished() {
        return finished;
    }

    public void tick() {
        pruneDead();
        if (members.size() < 2) {
            finished = true;
            return;
        }

        if (!enabled.getAsBoolean()) return;

        Mob leader = members.get(leaderIndex);
        double r = Math.max(1, radius.getAsInt());
        double radiusSqr = r * r;

        Vec3 leaderPos = leader.position();
        for (int i = 0; i < members.size(); i++) {
            if (i == leaderIndex) continue;
            Mob follower = members.get(i);
            if (follower.isNoAi()) continue;
            if (follower.getTarget() != null) continue;
            if (follower.distanceToSqr(leader) <= radiusSqr) continue;

            // Aim a couple of blocks short of the leader so followers form a
            // loose ring around them instead of all stacking on the same tile.
            // The stand-off point sits on the leader→follower axis, preserving
            // the follower's existing bearing so motion stays natural.
            Vec3 dir = follower.position().subtract(leaderPos);
            double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            double tx, tz;
            if (horiz < 1.0e-4) {
                tx = leaderPos.x + FOLLOW_STAND_OFF;
                tz = leaderPos.z;
            } else {
                tx = leaderPos.x + (dir.x / horiz) * FOLLOW_STAND_OFF;
                tz = leaderPos.z + (dir.z / horiz) * FOLLOW_STAND_OFF;
            }
            follower.getNavigation().moveTo(tx, leaderPos.y, tz, FOLLOW_SPEED);
        }
    }

    private void pruneDead() {
        members.removeIf(m -> !m.isAlive() || m.isRemoved());
        if (leaderIndex >= members.size()) leaderIndex = 0;
    }
}
