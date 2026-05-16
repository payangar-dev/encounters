package com.payangar.encounters.event.portal;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.event.ally.EncounterAllies;
import com.payangar.encounters.event.banner.BannerArmy;
import com.payangar.encounters.event.cinematic.Cinematic;
import com.payangar.encounters.event.cinematic.MobMarkerParticles;
import com.payangar.encounters.event.cohesion.GroupCohesion;
import com.payangar.encounters.event.cohesion.GroupCohesionTicker;
import com.payangar.encounters.event.portal.PortalGeometry.PortalSite;
import com.payangar.encounters.event.portal.internal.InvasionParticleEmitter;
import com.payangar.encounters.event.portal.internal.InvasionRewardEmitter;
import com.payangar.encounters.event.portal.internal.InvasionWaveSpawner;
import com.payangar.encounters.event.portal.internal.MagmaBombCaster;
import com.payangar.encounters.network.EncountersNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Multi-wave portal invasion cinematic.
 *
 * <p>Phases: BUILDUP (portal sparks before the first wave) → for each wave:
 * WAVE_SPAWN (mobs emerge one by one, locked down) then COMBAT (mobs free,
 * waiting for the wave to be wiped) → AFTERMATH (reward stream + calm tail) →
 * FINISHED. Wave size scales linearly: wave {@code n} contains
 * {@code firstWaveSize + (n - 1) * step} mobs.</p>
 *
 * <p>This class is the orchestrator. The actual work is delegated to four
 * collaborators: {@link InvasionWaveSpawner}, {@link InvasionRewardEmitter},
 * {@link InvasionParticleEmitter} and {@link MagmaBombCaster}.</p>
 */
public final class PortalInvasion implements Cinematic {

    enum Phase { BUILDUP, WAVE_SPAWN, COMBAT, AFTERMATH, FINISHED }

    static final int BUILDUP_TICKS = 60;
    static final int SPAWN_INTERVAL_TICKS = 8;
    static final int WAVE_GRACE_TICKS = 30;
    static final int AFTERMATH_TICKS = 40;

    /** Half-extent (X/Z) of the portal-lock box that suppresses teleportation. */
    private static final double PORTAL_LOCK_HALF = 5.0;
    /** Vertical extent of the portal-lock box (covers the full 3-block portal interior plus margin). */
    private static final double PORTAL_LOCK_HEIGHT = 6.0;

    /** A mob beyond this distance from the leash anchor gets pulled back. */
    private static final double LEASH_RADIUS = 20.0;
    /** Pulled-back mobs aim for a point this far from the leash anchor — hysteresis vs LEASH_RADIUS. */
    private static final double LEASH_RETURN_RADIUS = 12.0;
    private static final double LEASH_SPEED = 1.1;
    private static final int LEASH_INTERVAL_TICKS = 20;

    /** Cap multiplier on the wave weight transform — adjusted weight ≤ base × this. */
    private static final double WEIGHT_BOOST_CAP = 4.0;

    /** Cadence of the portal-integrity check — once per second is enough to feel responsive. */
    private static final int PORTAL_INTEGRITY_CHECK_INTERVAL_TICKS = 20;

    private final ServerLevel level;
    private final PortalSite site;
    private final Direction spawnFace;
    private final Vec3 anchor;
    private final BlockPos anchorKey;
    private final Vec3 leashAnchor;
    private final int totalWaves;
    private final String groupName;
    private final List<Mob> currentWaveMobs = new ArrayList<>();

    private final InvasionWaveSpawner spawner;
    private final InvasionParticleEmitter particles;
    private final InvasionRewardEmitter rewards = new InvasionRewardEmitter();
    private final MagmaBombCaster magmaBomb;

    private Phase phase = Phase.BUILDUP;
    private int phaseTicks = 0;
    private int combatGraceTicks = 0;
    private int currentWave = 0;
    private int currentWaveSize = 0;
    private int spawnedThisWave = 0;
    private boolean finished = false;
    /** Calm-tail counter — runs only after the reward queue has been fully drained. */
    private int rewardTailTicks = 0;

    public PortalInvasion(ServerLevel level, PortalSite site, Direction spawnFace) {
        this.level = level;
        this.site = site;
        this.spawnFace = spawnFace;
        this.anchor = site.centerBase();
        this.anchorKey = PortalGeometry.anchorKey(site);
        // Leash anchor sits one block in front of the portal so pulled-back
        // mobs end up on the spawn side rather than potentially behind the frame.
        this.leashAnchor = PortalGeometry.spawnAnchor(site, spawnFace);
        this.groupName = EncounterAllies.newGroupName();
        BannerArmy bannerArmy = BannerArmy.random(NetherPortalInvasionEvent.BANNER_THEMES, level.getRandom());
        EncountersConfig config = EncountersConfig.get();
        int min = Math.max(1, config.netherPortalInvasionMinWaves);
        int max = Math.max(min, config.netherPortalInvasionMaxWaves);
        this.totalWaves = min + level.getRandom().nextInt(max - min + 1);

        this.spawner = new InvasionWaveSpawner(level, site, spawnFace, groupName, bannerArmy, currentWaveMobs);
        this.particles = new InvasionParticleEmitter(level, site, spawnFace);
        this.magmaBomb = new MagmaBombCaster(level, anchor, spawnFace, groupName);

        EncountersNetwork.sendInvasionStart(level, anchorKey, PortalGeometry.portalBlocks(level, site));
    }

    public int totalWaves() {
        return totalWaves;
    }

    @Override public ServerLevel level() { return level; }
    @Override public Vec3 anchor()       { return anchor; }
    @Override public String eventId()    { return NetherPortalInvasionEvent.ID; }
    @Override public boolean isFinished() { return finished; }

    @Override
    public void tick() {
        if (level.getGameTime() % PORTAL_INTEGRITY_CHECK_INTERVAL_TICKS == 0
                && !isPortalIntact()) {
            onPortalBroken();
            return;
        }
        suppressPortalTeleportation();
        enforcePortalLeash();
        switch (phase) {
            case BUILDUP -> tickBuildup();
            case WAVE_SPAWN -> tickWaveSpawn();
            case COMBAT -> tickCombat();
            case AFTERMATH -> tickAftermath();
            default -> {}
        }
        magmaBomb.tick(currentWave);
        phaseTicks++;
    }

    @Override
    public void onAbandoned() {
        spawner.releaseWaveLockdown();
        Constants.LOG.info("[{}] invasion abandoned at wave {}/{} (anchor {}, {}, {})",
                NetherPortalInvasionEvent.ID, currentWave, totalWaves,
                (int) anchor.x, (int) anchor.y, (int) anchor.z);
        finishAndRelease();
    }

    /**
     * Bumps the portal cooldown of every {@link LivingEntity} inside the lock
     * box on every tick. Refreshes vanilla {@code Entity#portalCooldown} (300
     * ticks) so neither players nor invasion mobs can ever accumulate the
     * {@code isInsidePortal} state long enough to teleport.
     */
    private void suppressPortalTeleportation() {
        AABB box = AABB.ofSize(anchor.add(0, 2, 0),
                PORTAL_LOCK_HALF * 2, PORTAL_LOCK_HEIGHT, PORTAL_LOCK_HALF * 2);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            e.setPortalCooldown();
        }
    }

    /**
     * Pulls back any wave member that strayed beyond {@link #LEASH_RADIUS} of
     * the spawn anchor. Mobs in active combat ({@code getTarget() != null})
     * are left alone so a chase isn't yanked mid-flight.
     */
    private void enforcePortalLeash() {
        if (level.getGameTime() % LEASH_INTERVAL_TICKS != 0) return;
        double leashSqr = LEASH_RADIUS * LEASH_RADIUS;
        for (Mob m : currentWaveMobs) {
            if (!m.isAlive() || m.isRemoved()) continue;
            if (m.isNoAi()) continue;
            if (m.getTarget() != null) continue;
            if (m.distanceToSqr(leashAnchor) <= leashSqr) continue;

            Vec3 away = m.position().subtract(leashAnchor);
            double horiz = Math.sqrt(away.x * away.x + away.z * away.z);
            double tx, tz;
            if (horiz < 1.0e-4) {
                tx = leashAnchor.x;
                tz = leashAnchor.z;
            } else {
                tx = leashAnchor.x + (away.x / horiz) * LEASH_RETURN_RADIUS;
                tz = leashAnchor.z + (away.z / horiz) * LEASH_RETURN_RADIUS;
            }
            m.getNavigation().moveTo(tx, leashAnchor.y, tz, LEASH_SPEED);
        }
    }

    private boolean isPortalIntact() {
        return level.getBlockState(anchorKey).is(Blocks.NETHER_PORTAL);
    }

    /**
     * Stops the invasion early because the portal frame was broken. Mobs
     * already spawned remain alive (consistent with {@link #onAbandoned()}).
     */
    private void onPortalBroken() {
        if (finished) return;
        spawner.releaseWaveLockdown();
        Constants.LOG.info("[{}] invasion stopped at wave {}/{} — portal broken (anchor {}, {}, {})",
                NetherPortalInvasionEvent.ID, currentWave, totalWaves,
                anchorKey.getX(), anchorKey.getY(), anchorKey.getZ());
        finishAndRelease();
    }

    // ---------- Phases ----------

    private void tickBuildup() {
        particles.emitPortalSparks(phaseTicks, currentWave);
        if (phaseTicks >= BUILDUP_TICKS) {
            startNextWave();
        }
    }

    private void tickWaveSpawn() {
        particles.emitPortalSparks(phaseTicks, currentWave);
        if (spawnedThisWave < currentWaveSize && (phaseTicks % SPAWN_INTERVAL_TICKS) == 0) {
            Vec3 pos = spawner.spawnOneMob(waveWeightTransform());
            if (pos != null) {
                particles.burstAtSpawn(pos, currentWave);
            }
            spawnedThisWave++;
        }
        if (spawnedThisWave >= currentWaveSize) {
            spawner.releaseWaveLockdown();
            startWaveCohesion();
            phase = Phase.COMBAT;
            phaseTicks = 0;
            combatGraceTicks = 0;
        }
    }

    /**
     * Registers a fresh {@link GroupCohesion} group for the current wave —
     * the first surviving member is the leader; others regroup toward it
     * whenever they stray. Mounted passengers are excluded since they can't
     * navigate independently from their mount.
     */
    private void startWaveCohesion() {
        List<Mob> ground = currentWaveMobs.stream().filter(m -> !m.isPassenger()).toList();
        if (ground.size() < 2) return;
        GroupCohesionTicker.start(new GroupCohesion(level, ground,
                () -> EncountersConfig.get().netherPortalInvasionGroupCohesionEnabled,
                () -> EncountersConfig.get().netherPortalInvasionGroupCohesionRadius));
    }

    private void tickCombat() {
        particles.emitPortalSparks(phaseTicks, currentWave);
        if (phaseTicks % 3 == 0) {
            MobMarkerParticles.emit(level, currentWaveMobs, ParticleTypes.FLAME);
            if (currentWave >= 5) {
                MobMarkerParticles.emit(level, currentWaveMobs, ParticleTypes.SOUL_FIRE_FLAME);
            }
        }
        spawner.enforceAggroOnWave();
        currentWaveMobs.removeIf(m -> !m.isAlive() || m.isRemoved());

        if (currentWaveMobs.isEmpty()) {
            // Grace period absorbs simultaneous deaths so a single AoE wiping
            // the wave doesn't chain to the next one within the same tick.
            combatGraceTicks++;
            if (combatGraceTicks >= WAVE_GRACE_TICKS) {
                if (currentWave >= totalWaves) {
                    rewards.roll(level, anchor.add(0, 1.5, 0), totalWaves);
                    phase = Phase.AFTERMATH;
                    phaseTicks = 0;
                } else {
                    startNextWave();
                }
            }
        } else {
            combatGraceTicks = 0;
        }
    }

    private void tickAftermath() {
        particles.emitPortalSparks(phaseTicks, currentWave);
        if (rewards.hasPending()) {
            rewards.tickEmission(phaseTicks, level, anchor.add(0, 1.5, 0), spawnFace);
            rewardTailTicks = 0;
        } else {
            rewardTailTicks++;
            if (rewardTailTicks >= AFTERMATH_TICKS) {
                finishAndRelease();
            }
        }
    }

    // ---------- Wave control ----------

    private void startNextWave() {
        currentWave++;
        EncountersConfig config = EncountersConfig.get();
        currentWaveSize = config.netherPortalInvasionFirstWaveSize
                + (currentWave - 1) * config.netherPortalInvasionWaveSizeStep;
        spawnedThisWave = 0;
        currentWaveMobs.clear();
        phase = Phase.WAVE_SPAWN;
        phaseTicks = 0;
        particles.emitWaveStartBurst(currentWave);
        Constants.LOG.info("[{}] starting wave {}/{} ({} mobs)",
                NetherPortalInvasionEvent.ID, currentWave, totalWaves, currentWaveSize);
    }

    /**
     * Linear-inversion weight transform with a per-entry cap so very rare
     * mobs (low base weight) never become dominant in late waves.
     *
     * <p>Base formula: {@code adjusted = base + progress * (max - 2*base + 1)}.
     * Without cap this fully inverts the distribution at the final wave —
     * a base-1 entry becomes the most common pick, contradicting "very rare".
     * The cap clamps any boost to {@code base × WEIGHT_BOOST_CAP}, so a
     * base-1 mob can climb to {@value #WEIGHT_BOOST_CAP} at most while a
     * base-30 mob (already decreasing) is unaffected.</p>
     */
    private IntUnaryOperator waveWeightTransform() {
        int maxW = NetherPortalInvasionEvent.roster().maxBaseWeight();
        double progress = totalWaves > 1 ? (currentWave - 1.0) / (totalWaves - 1) : 0.0;
        return base -> {
            double inverted = base + progress * (maxW - 2.0 * base + 1.0);
            double capped = Math.min(inverted, base * WEIGHT_BOOST_CAP);
            return (int) Math.max(1, Math.round(capped));
        };
    }

    private void finishAndRelease() {
        if (finished) return;
        finished = true;
        phase = Phase.FINISHED;
        magmaBomb.discard();
        EncounterAllies.disbandGroup(level, groupName);
        EncountersNetwork.sendInvasionEnd(level, anchorKey);
        NetherPortalInvasionEvent.releaseLock(this);
    }
}
