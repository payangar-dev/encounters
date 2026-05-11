package com.payangar.encounters.event.portal.internal;

import com.payangar.encounters.event.portal.PortalGeometry.PortalSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * All visual emissions for a portal invasion: the ambient sparks during every
 * non-finished phase, the wide burst on each wave start, and the per-mob
 * spawn flash. Density and particle types scale with the current wave —
 * BUILDUP and early waves stay calm (FLAME only), wave 3+ adds LAVA droplets,
 * wave 5+ folds in SOUL_FIRE_FLAME and LARGE_SMOKE for the climax.
 */
public final class InvasionParticleEmitter {

    /**
     * Angular speed (radians per server tick) of the time-evolved swirl base
     * used by continuous emissions. ~0.1 rad/tick = a full rotation every
     * ~63 ticks (~3.2 s), slow enough that consecutive particles trace a
     * visible spiral instead of looking randomly scattered.
     */
    private static final double SWIRL_ANGULAR_SPEED = 0.1;

    private final ServerLevel level;
    private final PortalSite site;
    private final Direction spawnFace;

    public InvasionParticleEmitter(ServerLevel level, PortalSite site, Direction spawnFace) {
        this.level = level;
        this.site = site;
        this.spawnFace = spawnFace;
    }

    /**
     * Continuous outward flame jets — particles spawn on the portal surface and
     * spiral outward toward {@code spawnFace}. Throttled to once every 4 ticks.
     */
    public void emitPortalSparks(int phaseTicks, int currentWave) {
        if (phaseTicks % 4 != 0) return;
        int emissions = 1 + currentWave / 3;
        double baseSpeed = 0.15 + Math.min(currentWave, 6) * 0.02;
        RandomSource rng = level.getRandom();
        Vec3 c = site.centerBase();

        double swirlBase = level.getGameTime() * SWIRL_ANGULAR_SPEED;

        for (int i = 0; i < emissions; i++) {
            double axisOffset = (rng.nextDouble() - 0.5) * site.width();
            double yOffset = rng.nextDouble() * 3.0;
            double sx = site.axis() == Direction.Axis.X ? c.x + axisOffset : c.x;
            double sz = site.axis() == Direction.Axis.Z ? c.z + axisOffset : c.z;
            double sy = c.y + yOffset;

            double theta = swirlBase + i * 0.3 + (rng.nextDouble() - 0.5) * 0.4;
            Vec3 v = swirlVelocity(theta, 0.3, 0.7, rng);
            double speed = curvedSpeed(baseSpeed, rng);

            emitDirected(ParticleTypes.FLAME, sx, sy, sz, v.x, v.y, v.z, speed);
            if (currentWave >= 3) {
                emitDirected(ParticleTypes.LAVA, sx, sy, sz, v.x, v.y * 0.5, v.z, speed * 0.6);
            }
            if (currentWave >= 5) {
                emitDirected(ParticleTypes.SOUL_FIRE_FLAME, sx, sy, sz, v.x, v.y, v.z, speed);
                emitDirected(ParticleTypes.LARGE_SMOKE, sx, sy, sz, v.x, v.y + 0.1, v.z, speed * 0.4);
            }
        }
    }

    /**
     * Wide outward burst across the whole portal face when a wave starts.
     * Uses a fully random swirl angle (no time progression) so the burst reads
     * as chaotic rather than spiral.
     */
    public void emitWaveStartBurst(int currentWave) {
        int count = 15 + currentWave * 5;
        double baseSpeed = 0.4;
        RandomSource rng = level.getRandom();
        Vec3 c = site.centerBase();

        for (int i = 0; i < count; i++) {
            double axisOffset = (rng.nextDouble() - 0.5) * site.width();
            double yOffset = rng.nextDouble() * 3.0;
            double sx = site.axis() == Direction.Axis.X ? c.x + axisOffset : c.x;
            double sz = site.axis() == Direction.Axis.Z ? c.z + axisOffset : c.z;
            double sy = c.y + yOffset;

            double theta = rng.nextDouble() * Math.PI * 2;
            Vec3 v = swirlVelocity(theta, 0.4, 1.0, rng);
            double speed = curvedSpeed(baseSpeed, rng);

            emitDirected(ParticleTypes.FLAME, sx, sy, sz, v.x, v.y, v.z, speed);
            if (currentWave >= 3 && (i & 3) == 0) {
                emitDirected(ParticleTypes.LAVA, sx, sy, sz, v.x, v.y * 0.5, v.z, speed * 0.6);
            }
            if (currentWave >= 5 && (i & 1) == 0) {
                emitDirected(ParticleTypes.SOUL_FIRE_FLAME, sx, sy, sz, v.x, v.y, v.z, speed);
            }
        }
    }

    /** Per-mob spawn flash. Same escalation rules as the portal sparks. */
    public void burstAtSpawn(Vec3 pos, int currentWave) {
        int flameCount = 12 + currentWave * 2;
        int smallFlameCount = 8 + currentWave * 2;
        level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.5, pos.z,
                flameCount, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.SMALL_FLAME, pos.x, pos.y + 0.5, pos.z,
                smallFlameCount, 0.3, 0.3, 0.3, 0.04);
        if (currentWave >= 3) {
            level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.3, pos.z,
                    4, 0.2, 0.1, 0.2, 0.0);
        }
        if (currentWave >= 5) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.5, pos.z,
                    8, 0.3, 0.3, 0.3, 0.05);
        }
        float pitch = 0.7f + level.getRandom().nextFloat() * 0.4f;
        level.playSound(null, BlockPos.containing(pos),
                SoundEvents.PORTAL_TRAVEL, SoundSource.HOSTILE, 0.4f, pitch);
    }

    /**
     * Builds a velocity vector = forward(spawnFace) + tangential perturbation
     * around the spawnFace axis at angle {@code theta}. The tangential plane
     * is spanned by the world horizontal "right" axis (perpendicular to
     * spawnFace) and world up, so the swirl is a true rotation around the
     * outward axis.
     */
    private Vec3 swirlVelocity(double theta, double tangentialMin, double tangentialMax, RandomSource rng) {
        double tangentialMag = tangentialMin + rng.nextDouble() * (tangentialMax - tangentialMin);
        // right = forward × up = (-fz, 0, fx)
        double rightX = -spawnFace.getStepZ();
        double rightZ = spawnFace.getStepX();
        double cosT = Math.cos(theta);
        double sinT = Math.sin(theta);
        double vx = spawnFace.getStepX() + cosT * rightX * tangentialMag;
        double vy = sinT * tangentialMag + 0.05;
        double vz = spawnFace.getStepZ() + cosT * rightZ * tangentialMag;
        return new Vec3(vx, vy, vz);
    }

    /**
     * Non-linear speed sample: most particles travel slow (cluster near
     * 0.5×base), a long tail reaches ~1.7×base.
     */
    private static double curvedSpeed(double base, RandomSource rng) {
        double r = rng.nextDouble();
        return base * (0.5 + Math.pow(r, 2.0) * 1.2);
    }

    private void emitDirected(ParticleOptions type,
                              double x, double y, double z,
                              double vx, double vy, double vz, double speed) {
        level.sendParticles(type, x, y, z, 0, vx, vy, vz, speed);
    }
}
