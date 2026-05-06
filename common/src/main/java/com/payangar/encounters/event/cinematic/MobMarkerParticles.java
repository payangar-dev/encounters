package com.payangar.encounters.event.cinematic;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Emits one identifier particle at a random point inside each live mob's
 * bounding box. Lets players see which mobs belong to a running encounter at
 * a glance. The caller controls the cadence — call this once per N ticks
 * from the cinematic's tick method.
 */
public final class MobMarkerParticles {

    private MobMarkerParticles() {}

    public static void emit(ServerLevel level, Iterable<? extends Entity> mobs, ParticleOptions particle) {
        RandomSource rng = level.getRandom();
        for (Entity e : mobs) {
            if (!e.isAlive() || e.isRemoved()) continue;
            AABB bb = e.getBoundingBox();
            double x = bb.minX + rng.nextDouble() * (bb.maxX - bb.minX);
            double y = bb.minY + rng.nextDouble() * (bb.maxY - bb.minY);
            double z = bb.minZ + rng.nextDouble() * (bb.maxZ - bb.minZ);
            level.sendParticles(particle, x, y, z, 1, 0.0, 0.02, 0.0, 0.01);
        }
    }
}
