package com.payangar.encounters.event.portal;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.event.EncounterSpawner;
import com.payangar.encounters.event.MobRoster;
import com.payangar.encounters.event.ResolvedMob;
import com.payangar.encounters.event.ally.EncounterAllies;
import com.payangar.encounters.event.cinematic.Cinematic;
import com.payangar.encounters.event.cinematic.MobMarkerParticles;
import com.payangar.encounters.event.cohesion.GroupCohesion;
import com.payangar.encounters.event.cohesion.GroupCohesionTicker;
import com.payangar.encounters.event.portal.PortalGeometry.PortalSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

/**
 * Multi-wave portal invasion cinematic.
 *
 * <p>Phases: BUILDUP (portal sparks before the first wave) → for each wave:
 * WAVE_SPAWN (mobs emerge one by one, locked down) then COMBAT (mobs free,
 * waiting for the wave to be wiped) → AFTERMATH (brief tail) → FINISHED.</p>
 *
 * <p>Wave transitions are event-driven: the next wave starts when the last
 * mob of the current wave is dead, plus a short grace period to absorb
 * AoE wipes (a single explosion killing the whole wave should not chain
 * the next one within the same tick). Wave size scales linearly: wave
 * {@code n} contains {@code firstWaveSize + (n - 1) * step} mobs.</p>
 *
 * <p>All mobs of the invasion share one allies tag so they never target one
 * another. Group cohesion is intentionally not used here — invasion mobs are
 * meant to spread out and converge on the player rather than reform a
 * cluster around a leader.</p>
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

    /** Search radius for periodic re-aggro on wave members that lost their target. */
    private static final double REAGGRO_RADIUS = 64.0;
    /** Re-aggro pass cadence (3 seconds). */
    private static final int REAGGRO_INTERVAL_TICKS = 60;

    /** Cap multiplier on the wave weight transform — adjusted weight ≤ base × this. */
    private static final double WEIGHT_BOOST_CAP = 4.0;

    /** Mobs whose hitbox exceeds this in width or height get spawned further from the portal frame. */
    private static final double LARGE_MOB_THRESHOLD = 3.0;

    private final ServerLevel level;
    private final PortalSite site;
    private final Direction spawnFace;
    private final Vec3 anchor;
    private final Vec3 leashAnchor;
    private final int totalWaves;
    private final String groupTag;
    private final BannerArmy bannerArmy;
    private final List<Mob> currentWaveMobs = new ArrayList<>();

    private Phase phase = Phase.BUILDUP;
    private int phaseTicks = 0;
    private int combatGraceTicks = 0;
    private int currentWave = 0;
    private int currentWaveSize = 0;
    private int spawnedThisWave = 0;
    private boolean finished = false;

    public PortalInvasion(ServerLevel level, PortalSite site, Direction spawnFace) {
        this.level = level;
        this.site = site;
        this.spawnFace = spawnFace;
        this.anchor = site.centerBase();
        // Leash anchor sits one block in front of the portal so pulled-back
        // mobs end up on the spawn side rather than potentially behind the frame.
        this.leashAnchor = PortalGeometry.spawnAnchor(site, spawnFace);
        this.groupTag = EncounterAllies.newGroupTag();
        // Roll the army's banner once per invasion — every banner-eligible
        // mob spawned by this instance shares the same theme, two separate
        // invasions look like two different armies. Themes come from the
        // event's curated list, not from user config.
        this.bannerArmy = BannerArmy.random(NetherPortalInvasionEvent.BANNER_THEMES, level.getRandom());
        EncountersConfig config = EncountersConfig.get();
        int min = Math.max(1, config.netherPortalInvasionMinWaves);
        int max = Math.max(min, config.netherPortalInvasionMaxWaves);
        this.totalWaves = min + level.getRandom().nextInt(max - min + 1);
    }

    public int totalWaves() {
        return totalWaves;
    }

    @Override
    public ServerLevel level() {
        return level;
    }

    @Override
    public Vec3 anchor() {
        return anchor;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void tick() {
        suppressPortalTeleportation();
        enforcePortalLeash();
        switch (phase) {
            case BUILDUP -> tickBuildup();
            case WAVE_SPAWN -> tickWaveSpawn();
            case COMBAT -> tickCombat();
            case AFTERMATH -> tickAftermath();
            default -> {}
        }
        phaseTicks++;
    }

    /**
     * Bumps the portal cooldown of every entity inside the lock box on every
     * tick. Refreshes the vanilla {@code Entity#portalCooldown} (300 ticks)
     * back to its max so neither players nor invasion mobs can ever
     * accumulate the {@code isInsidePortal} state long enough to teleport.
     * After the cinematic finishes the cooldown drains naturally over ~15 s,
     * giving a brief grace period before the portal works again.
     */
    private void suppressPortalTeleportation() {
        AABB box = AABB.ofSize(anchor.add(0, 2, 0),
                PORTAL_LOCK_HALF * 2, PORTAL_LOCK_HEIGHT, PORTAL_LOCK_HALF * 2);
        for (Entity e : level.getEntities((Entity) null, box)) {
            e.setPortalCooldown();
        }
    }

    /**
     * Pulls back any wave member that strayed beyond {@link #LEASH_RADIUS} of
     * the spawn anchor. Mobs in active combat ({@code getTarget() != null})
     * are left alone so a chase isn't yanked mid-flight; mobs in lockdown
     * (no AI) are skipped naturally. Throttled to once per second.
     *
     * <p>The pull target is {@link #LEASH_RETURN_RADIUS} blocks short of the
     * leash anchor on the mob→anchor line, providing hysteresis so a mob
     * just past the radius doesn't get re-issued the same nav order every
     * second.</p>
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

    @Override
    public void onAbandoned() {
        // Release any in-flight lockdown so abandoned mobs don't stay frozen.
        releaseWaveLockdown();
        Constants.LOG.info("[{}] invasion abandoned at wave {}/{} (anchor {}, {}, {})",
                NetherPortalInvasionEvent.ID, currentWave, totalWaves,
                (int) anchor.x, (int) anchor.y, (int) anchor.z);
        finishAndRelease();
    }

    // ---------- Phases ----------

    private void tickBuildup() {
        emitPortalSparks();
        if (phaseTicks >= BUILDUP_TICKS) {
            startNextWave();
        }
    }

    private void tickWaveSpawn() {
        emitPortalSparks();
        if (spawnedThisWave < currentWaveSize && (phaseTicks % SPAWN_INTERVAL_TICKS) == 0) {
            spawnOneMob();
        }
        if (spawnedThisWave >= currentWaveSize) {
            releaseWaveLockdown();
            startWaveCohesion();
            phase = Phase.COMBAT;
            phaseTicks = 0;
            combatGraceTicks = 0;
        }
    }

    /**
     * Registers a fresh {@link GroupCohesion} group for the current wave —
     * the first surviving member is the leader; other members regroup
     * toward it whenever they stray beyond the configured radius. Mounted
     * passengers are excluded since they can't navigate independently from
     * their mount. Each wave gets its own group, so the leader is always
     * a member of the wave currently in combat.
     */
    private void startWaveCohesion() {
        List<Mob> ground = currentWaveMobs.stream()
                .filter(m -> !m.isPassenger())
                .toList();
        if (ground.size() < 2) return;
        GroupCohesionTicker.start(new GroupCohesion(level, ground,
                () -> EncountersConfig.get().netherPortalInvasionGroupCohesionEnabled,
                () -> EncountersConfig.get().netherPortalInvasionGroupCohesionRadius));
    }

    private void tickCombat() {
        emitPortalSparks();
        if (phaseTicks % 3 == 0) {
            MobMarkerParticles.emit(level, currentWaveMobs, ParticleTypes.FLAME);
            if (currentWave >= 5) {
                MobMarkerParticles.emit(level, currentWaveMobs, ParticleTypes.SOUL_FIRE_FLAME);
            }
        }
        enforceAggroOnWave();
        currentWaveMobs.removeIf(m -> !m.isAlive() || m.isRemoved());

        if (currentWaveMobs.isEmpty()) {
            // Grace period absorbs simultaneous deaths (e.g., a single AoE wiping
            // the wave) so we don't chain to the next wave within the same tick.
            combatGraceTicks++;
            if (combatGraceTicks >= WAVE_GRACE_TICKS) {
                if (currentWave >= totalWaves) {
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
        emitPortalSparks();
        if (phaseTicks >= AFTERMATH_TICKS) {
            finishAndRelease();
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
        emitWaveStartBurst();
        Constants.LOG.info("[{}] starting wave {}/{} ({} mobs)",
                NetherPortalInvasionEvent.ID, currentWave, totalWaves, currentWaveSize);
    }

    private void releaseWaveLockdown() {
        for (Mob m : currentWaveMobs) {
            if (m.isAlive() && !m.isRemoved()) {
                m.setInvulnerable(false);
                m.setNoAi(false);
            }
        }
    }

    // ---------- Spawning ----------

    private void spawnOneMob() {
        EncountersConfig config = EncountersConfig.get();
        MobRoster roster = NetherPortalInvasionEvent.roster(config);
        Optional<ResolvedMob> pick = roster.pick(level.getRandom(), waveWeightTransform(roster));
        if (pick.isEmpty()) {
            spawnedThisWave++;
            return;
        }
        Vec3 spawnPos = pickSpawnPos(pick.get());
        Entity entity = EncounterSpawner.spawn(level, pick.get(), spawnPos, level.getRandom(),
                NetherPortalInvasionEvent.ID);
        if (entity == null) {
            spawnedThisWave++;
            return;
        }

        // Lockdown + tagging + tracking applied recursively so passengers
        // (e.g. a Piglin Brute riding a Hoglin) inherit the same treatment.
        // Without this, the rider would not carry the allies tag → other
        // invasion mobs could attack it; and its death wouldn't count
        // toward wave completion since only the mount is in currentWaveMobs.
        registerWaveMember(entity);

        // Stamp the army banner on every piglin in the entity tree — head
        // slot for the visible flag, plus their offhand shield (if any)
        // gets the same banner pattern. Run after registerWaveMember so
        // the items inherit the drop-chance override that the spawner
        // applied via setItemSlot's vanilla equipment update.
        bannerArmy.applyTo(entity, level);

        burstAtSpawn(spawnPos);
        spawnedThisWave++;
    }

    /**
     * Recursively applies lockdown, persistence, the group tag and wave
     * tracking to an entity and every passenger nested inside it.
     */
    private void registerWaveMember(Entity entity) {
        entity.setInvulnerable(true);
        if (entity instanceof Mob m) {
            m.setNoAi(true);
            m.setPersistenceRequired();
            m.addTag(groupTag);
            currentWaveMobs.add(m);
        }
        for (Entity p : entity.getPassengers()) {
            registerWaveMember(p);
        }
    }

    /**
     * Periodic re-aggro pass — every {@value #REAGGRO_INTERVAL_TICKS} ticks,
     * any wave member that lost its target (player died, ran out of sight,
     * brain dropped target, etc.) is re-pointed at the nearest live player.
     * Combined with the spawn-time aggro in {@link EncounterSpawner}, this
     * keeps invasion mobs continuously hostile regardless of vanilla
     * pacification rules.
     */
    private void enforceAggroOnWave() {
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
     * Linear-inversion weight transform with a per-entry cap so very rare
     * mobs (low base weight) never become dominant in late waves.
     *
     * <p>Base formula: {@code adjusted = base + progress * (max - 2*base + 1)}.
     * Without cap this fully inverts the distribution at the final wave —
     * a base-1 entry becomes the most common pick, which contradicts the
     * intent of "very rare". The cap clamps any boost to
     * {@code base × WEIGHT_BOOST_CAP}, so a base-1 mob can climb to
     * {@value #WEIGHT_BOOST_CAP} at most while a base-30 mob (already
     * decreasing) is unaffected.</p>
     *
     * <p>Examples with {@code max=30}, {@code WEIGHT_BOOST_CAP=4}:</p>
     * <ul>
     *   <li>base 30 → wave 1: 30, last: 1 (cap doesn't apply, formula decreases)</li>
     *   <li>base 7 → wave 1: 7, last: 24 (formula gives 24, cap = 28, no clip)</li>
     *   <li>base 2 → wave 1: 2, last: 8 (formula would give 27, capped to 8)</li>
     *   <li>base 1 → wave 1: 1, last: 4 (formula would give 30, capped to 4)</li>
     * </ul>
     *
     * <p>If the invasion has only one wave, progress is forced to 0.
     * Adjusted weights are floored to 1 so an entry never disappears.</p>
     */
    private IntUnaryOperator waveWeightTransform(MobRoster roster) {
        double progress = totalWaves > 1 ? (currentWave - 1.0) / (totalWaves - 1) : 0.0;
        int maxW = roster.maxBaseWeight();
        return base -> {
            double inverted = base + progress * (maxW - 2.0 * base + 1.0);
            double capped = Math.min(inverted, base * WEIGHT_BOOST_CAP);
            return (int) Math.max(1, Math.round(capped));
        };
    }

    /**
     * Picks a spawn position in front of the portal. Large mobs (hitbox
     * larger than {@value #LARGE_MOB_THRESHOLD} on width or height —
     * notably the Ghast at 4×4×4) get a wider face offset so their bounding
     * box clears the obsidian frame instead of clipping into it.
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

    // ---------- Particles & sound ----------

    /**
     * Angular speed (radians per server tick) of the time-evolved swirl
     * base used by continuous emissions. ~0.1 rad/tick = a full rotation
     * every ~63 ticks (~3.2 s), slow enough that consecutive particles
     * trace a visible spiral instead of looking randomly scattered.
     */
    private static final double SWIRL_ANGULAR_SPEED = 0.1;

    /**
     * Continuous outward flame jets — particles spawn on the portal surface
     * and spiral outward toward {@link #spawnFace}, as if the portal were
     * radiating heat. Uses the {@code count=0} form of
     * {@link ServerLevel#sendParticles}, which turns the offset triplet
     * into a velocity vector multiplied by {@code speed}.
     *
     * <p>Each particle gets a velocity = forward (toward spawnFace) plus a
     * tangential component rotating around the forward axis (the "swirl").
     * The swirl angle evolves over time so consecutive emissions trace a
     * helix; per-particle jitter on angle, tangential magnitude and speed
     * keeps the cloud from looking lockstep.</p>
     *
     * <p>Density and particle types scale with the current wave: BUILDUP
     * and early waves stay calm (FLAME only); wave 3+ adds LAVA droplets;
     * wave 5+ folds in SOUL_FIRE_FLAME and LARGE_SMOKE for the climax.</p>
     */
    private void emitPortalSparks() {
        if (phaseTicks % 4 != 0) return;
        int wave = currentWave;
        int emissions = 1 + wave / 3; // 1 in BUILDUP & waves 1-2, 2 at wave 3-5, 3 at wave 6+
        double baseSpeed = 0.15 + Math.min(wave, 6) * 0.02;
        RandomSource rng = level.getRandom();
        Vec3 c = site.centerBase();

        double swirlBase = level.getGameTime() * SWIRL_ANGULAR_SPEED;

        for (int i = 0; i < emissions; i++) {
            double axisOffset = (rng.nextDouble() - 0.5) * site.width();
            double yOffset = rng.nextDouble() * 3.0;
            double sx = site.axis() == Direction.Axis.X ? c.x + axisOffset : c.x;
            double sz = site.axis() == Direction.Axis.Z ? c.z + axisOffset : c.z;
            double sy = c.y + yOffset;

            // Within a tick, particles are spaced ~17° apart on the swirl;
            // the base angle drifts each tick. Small random jitter breaks lockstep.
            double theta = swirlBase + i * 0.3 + (rng.nextDouble() - 0.5) * 0.4;
            Vec3 v = swirlVelocity(theta, 0.3, 0.7, rng);
            double speed = curvedSpeed(baseSpeed, rng);

            emitDirected(ParticleTypes.FLAME, sx, sy, sz, v.x, v.y, v.z, speed);
            if (wave >= 3) {
                emitDirected(ParticleTypes.LAVA, sx, sy, sz, v.x, v.y * 0.5, v.z, speed * 0.6);
            }
            if (wave >= 5) {
                emitDirected(ParticleTypes.SOUL_FIRE_FLAME, sx, sy, sz, v.x, v.y, v.z, speed);
                emitDirected(ParticleTypes.LARGE_SMOKE, sx, sy, sz, v.x, v.y + 0.1, v.z, speed * 0.4);
            }
        }
    }

    /**
     * Wide outward burst across the whole portal face when a wave starts.
     * Each particle gets a fully random swirl angle (no time progression)
     * so the burst reads as chaotic rather than spiral, then the same
     * non-linear speed curve as the continuous sparks.
     */
    private void emitWaveStartBurst() {
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

    /**
     * Builds a velocity vector = forward(spawnFace) + tangential perturbation
     * around the spawnFace axis at angle {@code theta}. The tangential plane
     * is spanned by the world horizontal "right" axis (perpendicular to
     * spawnFace) and world up, so the swirl is a true rotation around the
     * outward axis. {@code tangentialMin/Max} bracket the per-particle
     * tangential magnitude — wider range = more visual spread.
     */
    private Vec3 swirlVelocity(double theta, double tangentialMin, double tangentialMax, RandomSource rng) {
        double tangentialMag = tangentialMin + rng.nextDouble() * (tangentialMax - tangentialMin);
        // right = forward × up = (-fz, 0, fx)
        double rightX = -spawnFace.getStepZ();
        double rightZ = spawnFace.getStepX();
        double cosT = Math.cos(theta);
        double sinT = Math.sin(theta);
        double vx = spawnFace.getStepX() + cosT * rightX * tangentialMag;
        double vy = sinT * tangentialMag + 0.05; // slight upward flame bias
        double vz = spawnFace.getStepZ() + cosT * rightZ * tangentialMag;
        return new Vec3(vx, vy, vz);
    }

    /**
     * Non-linear speed sample: most particles travel slow (cluster near
     * 0.5×base), a long tail reaches ~1.7×base. {@code rng^2} biases
     * the random sample toward 0; remapping to [0.5, 1.7] keeps the slow
     * cluster from being too anaemic while letting the occasional fast
     * particle punch through.
     */
    private static double curvedSpeed(double base, RandomSource rng) {
        double r = rng.nextDouble();
        return base * (0.5 + Math.pow(r, 2.0) * 1.2);
    }

    /**
     * Sends a single particle with explicit velocity. Uses the {@code count=0}
     * form of {@link ServerLevel#sendParticles}: with a count of zero, the
     * offset triplet is interpreted on the client as the particle's velocity
     * (multiplied by {@code speed}) instead of a random distribution box.
     */
    private void emitDirected(net.minecraft.core.particles.ParticleOptions type,
                              double x, double y, double z,
                              double vx, double vy, double vz, double speed) {
        level.sendParticles(type, x, y, z, 0, vx, vy, vz, speed);
    }

    /**
     * Per-mob spawn flash. Same escalation rules as the portal sparks so the
     * spawn beat stays visually coherent with the surrounding crackle.
     */
    private void burstAtSpawn(Vec3 pos) {
        int wave = currentWave;
        int flameCount = 12 + wave * 2;
        int smallFlameCount = 8 + wave * 2;
        level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.5, pos.z,
                flameCount, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.SMALL_FLAME, pos.x, pos.y + 0.5, pos.z,
                smallFlameCount, 0.3, 0.3, 0.3, 0.04);
        if (wave >= 3) {
            level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.3, pos.z,
                    4, 0.2, 0.1, 0.2, 0.0);
        }
        if (wave >= 5) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.5, pos.z,
                    8, 0.3, 0.3, 0.3, 0.05);
        }
        float pitch = 0.7f + level.getRandom().nextFloat() * 0.4f;
        level.playSound(null, BlockPos.containing(pos),
                SoundEvents.PORTAL_TRAVEL, SoundSource.HOSTILE, 0.4f, pitch);
    }

    private void finishAndRelease() {
        if (finished) return;
        finished = true;
        phase = Phase.FINISHED;
        NetherPortalInvasionEvent.releaseLock(this);
    }
}
