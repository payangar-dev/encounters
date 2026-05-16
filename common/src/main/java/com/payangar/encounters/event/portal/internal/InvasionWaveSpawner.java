package com.payangar.encounters.event.portal.internal;

import com.payangar.encounters.event.EncounterSpawner;
import com.payangar.encounters.event.MobRoster;
import com.payangar.encounters.event.ResolvedMob;
import com.payangar.encounters.event.ally.EncounterAllies;
import com.payangar.encounters.event.banner.BannerArmy;
import com.payangar.encounters.event.portal.NetherPortalInvasionEvent;
import com.payangar.encounters.event.portal.PortalGeometry;
import com.payangar.encounters.event.portal.PortalGeometry.PortalSite;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

/**
 * Spawn pipeline for one invasion wave. Holds the per-wave membership list,
 * runs the per-mob lockdown / team / banner stamping, and exposes the periodic
 * re-aggro pass and wave lockdown release.
 */
public final class InvasionWaveSpawner {

    /** Search radius for periodic re-aggro on wave members that lost their target. */
    private static final double REAGGRO_RADIUS = 64.0;
    /** Re-aggro pass cadence (3 seconds). */
    private static final int REAGGRO_INTERVAL_TICKS = 60;
    /** Mobs whose hitbox exceeds this in width or height get spawned further from the portal frame. */
    private static final double LARGE_MOB_THRESHOLD = 3.0;

    private final ServerLevel level;
    private final PortalSite site;
    private final Direction spawnFace;
    private final String groupName;
    private final BannerArmy bannerArmy;
    private final List<Mob> currentWaveMobs;

    public InvasionWaveSpawner(ServerLevel level, PortalSite site, Direction spawnFace,
                               String groupName, BannerArmy bannerArmy, List<Mob> currentWaveMobs) {
        this.level = level;
        this.site = site;
        this.spawnFace = spawnFace;
        this.groupName = groupName;
        this.bannerArmy = bannerArmy;
        this.currentWaveMobs = currentWaveMobs;
    }

    /**
     * Picks one entry from the roster (possibly with a wave-bias transform),
     * spawns it in front of the portal, locks it down, registers it as a wave
     * member, and applies the army banner. Returns the spawned position so the
     * caller can flash particles there.
     */
    public Vec3 spawnOneMob(IntUnaryOperator weightTransform) {
        MobRoster roster = NetherPortalInvasionEvent.roster();
        Optional<ResolvedMob> pick = roster.pick(level.getRandom(), weightTransform);
        if (pick.isEmpty()) return null;

        Vec3 spawnPos = pickSpawnPos(pick.get());
        Entity entity = EncounterSpawner.spawn(level, pick.get(), spawnPos, level.getRandom(),
                NetherPortalInvasionEvent.ID);
        if (entity == null) return null;

        registerWaveMember(entity);
        bannerArmy.applyTo(entity, level);
        return spawnPos;
    }

    /** Releases AI lockdown on every wave member that's still alive. */
    public void releaseWaveLockdown() {
        for (Mob m : currentWaveMobs) {
            if (m.isAlive() && !m.isRemoved()) {
                m.setInvulnerable(false);
                m.setNoAi(false);
                // While noAi is on, LivingEntity#travel early-returns (isControlledByLocalInstance
                // falls through to isEffectiveAi == false), so push impulses from overlapping
                // neighbours accumulate in deltaMovement instead of being consumed by move(SELF).
                // Without this reset, the very next aiStep applies the whole accumulated vector
                // at once and ejects the mob — proportional to how many mobs shared its column.
                m.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    /**
     * Periodic re-aggro pass — every {@value #REAGGRO_INTERVAL_TICKS} ticks,
     * any wave member that lost its target is re-pointed at the nearest live
     * player. Combined with the spawn-time aggro in {@link EncounterSpawner},
     * this keeps invasion mobs continuously hostile regardless of vanilla
     * pacification rules.
     */
    public void enforceAggroOnWave() {
        if (level.getGameTime() % REAGGRO_INTERVAL_TICKS != 0) return;
        for (Mob m : currentWaveMobs) {
            if (!m.isAlive() || m.isRemoved()) continue;
            if (m.isNoAi()) continue;
            if (m.getTarget() != null && m.getTarget().isAlive()) continue;
            Player nearest = level.getNearestPlayer(m.getX(), m.getY(), m.getZ(), REAGGRO_RADIUS, true);
            if (nearest != null && nearest.isAlive()) {
                EncounterSpawner.applyAggro(m, nearest);
            }
        }
    }

    /**
     * Recursively applies lockdown, persistence, group team membership and
     * wave tracking to an entity and every passenger nested inside it.
     */
    private void registerWaveMember(Entity entity) {
        entity.setInvulnerable(true);
        if (entity instanceof Mob m) {
            m.setNoAi(true);
            m.setPersistenceRequired();
            currentWaveMobs.add(m);
        }
        EncounterAllies.addToGroup(level, groupName, entity);
        for (Entity p : entity.getPassengers()) {
            registerWaveMember(p);
        }
    }

    /**
     * Picks a spawn position in front of the portal. Large mobs (hitbox larger
     * than {@value #LARGE_MOB_THRESHOLD} on width or height — notably the Ghast
     * at 4×4×4) get a wider face offset so their bounding box clears the
     * obsidian frame instead of clipping into it.
     */
    private Vec3 pickSpawnPos(ResolvedMob mob) {
        RandomSource rng = level.getRandom();
        Vec3 base = PortalGeometry.spawnAnchor(site, spawnFace);

        EntityDimensions dim = mob.type().getDimensions();
        boolean large = dim.width() > LARGE_MOB_THRESHOLD || dim.height() > LARGE_MOB_THRESHOLD;
        double faceOffset = large
                ? 3.5 + rng.nextDouble() * 1.5  // 3.5–5 blocks out for ghasts and friends
                : rng.nextDouble() * 0.5;       // 0–0.5 blocks for regular mobs

        double axisOffset = (rng.nextDouble() - 0.5) * site.width();
        double dx, dz;
        if (site.axis() == Direction.Axis.X) {
            dx = axisOffset;
            dz = faceOffset * spawnFace.getStepZ();
        } else {
            dz = axisOffset;
            dx = faceOffset * spawnFace.getStepX();
        }
        return new Vec3(base.x + dx, base.y, base.z + dz);
    }
}
