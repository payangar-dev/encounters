package com.payangar.encounters.event.cinematic;

import com.payangar.encounters.Constants;
import com.payangar.encounters.event.ally.EncounterAllies;
import com.payangar.encounters.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates a single lightning overcharge event after its bolt strikes.
 *
 * <p>Phase 1 — impact (0–40 ticks): invisible armor-stand caster fires
 * lightning spells, sculk ground spreads outward in concentric rings, mobs
 * stay invulnerable and AI-locked. Ends by releasing the mobs and removing
 * the caster.</p>
 *
 * <p>Phase 2 — aftermath (until every spawned mob is dead): sculk veins grow
 * organically from the impact point, pausing when no player is near and
 * resuming when one returns. Soul-fire-flame particles keep rising from the
 * scorched ground until the last mob falls.</p>
 */
public final class LightningCinematic implements Cinematic {

    enum Phase { IMPACT, AFTERMATH, FINISHED }

    static final int IMPACT_DURATION = 40;
    static final double GROUND_RADIUS = 3.0;
    static final double PLAYER_DETECTION_RADIUS = 32.0;
    static final int VEIN_CAP = 300;
    static final int VEIN_MAX_DISTANCE = 12;
    static final int VEIN_GROWTH_INTERVAL = 10;
    static final int VEIN_GROWTHS_PER_TICK = 1;
    static final int VEIN_TIPS_MAX = 48;
    private static final int VEIN_SEED_COUNT = 8;
    private static final int SPELL_TICK_SHOCKWAVE = 5;
    private static final int SHOCKWAVE_LEVEL = 8;
    private static final int SURFACE_SCAN_UP = 3;
    private static final int SURFACE_SCAN_DOWN = 3;

    private static final Set<Block> NATURAL_SURFACE = Set.of(
            // dirt family
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT,
            Blocks.PODZOL, Blocks.MYCELIUM, Blocks.ROOTED_DIRT,
            Blocks.DIRT_PATH, Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS,
            // stone family
            Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE,
            Blocks.DEEPSLATE, Blocks.TUFF, Blocks.CALCITE, Blocks.DRIPSTONE_BLOCK,
            // sediment
            Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.CLAY,
            Blocks.SANDSTONE, Blocks.RED_SANDSTONE,
            // terracotta
            Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA,
            Blocks.YELLOW_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.BROWN_TERRACOTTA,
            Blocks.LIGHT_GRAY_TERRACOTTA,
            // cold biomes
            Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.BLUE_ICE,
            // nether surface (portal event later)
            Blocks.NETHERRACK, Blocks.SOUL_SAND, Blocks.SOUL_SOIL,
            Blocks.BASALT, Blocks.BLACKSTONE,
            // end
            Blocks.END_STONE
    );

    private final ServerLevel level;
    private final Vec3 center;
    private final BlockPos centerBlock;
    private final List<Entity> spawnedMobs = new ArrayList<>();
    private final List<GroundSlot> groundSlots = new ArrayList<>();
    private final List<BlockPos> veinTips = new ArrayList<>();
    private final Set<BlockPos> veinedPositions = new HashSet<>();
    private int veinCount = 0;

    private Entity caster;
    private Phase phase = Phase.IMPACT;
    private int ticks = 0;
    private int groundIdx = 0;
    private int aftermathTicks = 0;
    /** Scoreboard team name shared by every spawned mob. Set by the event before the cinematic is started. */
    private String groupName;

    private record GroundSlot(BlockPos pos, double distance) {}

    public LightningCinematic(ServerLevel level, Vec3 center) {
        this.level = level;
        this.center = center;
        this.centerBlock = BlockPos.containing(center);
    }

    /** Stores the team name so it can be disbanded at teardown. */
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    @Override
    public ServerLevel level() {
        return level;
    }

    @Override
    public Vec3 anchor() {
        return center;
    }

    public void registerSpawnedMob(Entity mob) {
        spawnedMobs.add(mob);
    }

    @Override
    public boolean isFinished() {
        return phase == Phase.FINISHED;
    }

    @Override
    public void onAbandoned() {
        disbandGroup();
    }

    private void disbandGroup() {
        if (groupName != null) {
            EncounterAllies.disbandGroup(level, groupName);
            groupName = null;
        }
    }

    @Override
    public void tick() {
        if (ticks == 0) {
            onStart();
        }
        switch (phase) {
            case IMPACT -> tickImpact();
            case AFTERMATH -> tickAftermath();
            default -> {}
        }
        ticks++;
    }

    // ---------- Start ----------

    private void onStart() {
        spawnCaster();
        precomputeGroundSlots();
    }

    private void spawnCaster() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:armor_stand");
        tag.putBoolean("Invisible", true);
        tag.putBoolean("Invulnerable", true);
        tag.putBoolean("Marker", true);
        tag.putBoolean("NoBasePlate", true);
        tag.putBoolean("Silent", true);
        tag.putBoolean("NoGravity", true);
        Entity stand = EntityType.loadEntityRecursive(tag, level, e -> {
            e.moveTo(center.x, center.y, center.z, 0f, 0f);
            return e;
        });
        if (stand == null) {
            Constants.LOG.warn("[lightning_overcharge] cinematic: failed to spawn invisible caster");
            return;
        }
        stand.addTag("encounters_cinematic_caster");
        level.addFreshEntity(stand);
        this.caster = stand;
    }

    /**
     * Precompute every ground block inside the disk, paired with its distance
     * from the centre. The list is sorted ascending so the impact wave can
     * consume it linearly as the ring expands.
     */
    private void precomputeGroundSlots() {
        int r = (int) Math.ceil(GROUND_RADIUS);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > GROUND_RADIUS) continue;
                BlockPos surface = findDirtSurface((int) Math.floor(center.x) + dx,
                        (int) Math.floor(center.y),
                        (int) Math.floor(center.z) + dz);
                if (surface != null) {
                    groundSlots.add(new GroundSlot(surface, dist));
                }
            }
        }
        groundSlots.sort(Comparator.comparingDouble(GroundSlot::distance));
    }

    /**
     * Finds the first dirt-family block in a short vertical scan around the
     * impact height. Returns null if there is no dirt block in the column —
     * meaning nothing gets replaced in this (x,z) stripe.
     */
    private BlockPos findDirtSurface(int x, int y, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = SURFACE_SCAN_UP; dy >= -SURFACE_SCAN_DOWN; dy--) {
            cursor.set(x, y + dy, z);
            BlockState state = level.getBlockState(cursor);
            if (NATURAL_SURFACE.contains(state.getBlock())) {
                return cursor.immutable();
            }
        }
        return null;
    }

    // ---------- Phase 1 ----------

    private void tickImpact() {
        double ringRadius = Math.min(GROUND_RADIUS,
                (ticks + 1) * GROUND_RADIUS / IMPACT_DURATION);
        advanceGroundRing(ringRadius);
        spawnAmbientParticles();
        spawnMobMarkerParticles();

        if (ticks == SPELL_TICK_SHOCKWAVE) {
            castImpactShockwave();
        }

        if (ticks >= IMPACT_DURATION - 1) {
            endImpactPhase();
        }
    }

    private void advanceGroundRing(double currentRadius) {
        while (groundIdx < groundSlots.size() && groundSlots.get(groundIdx).distance <= currentRadius) {
            GroundSlot slot = groundSlots.get(groundIdx++);
            BlockState here = level.getBlockState(slot.pos);
            if (!NATURAL_SURFACE.contains(here.getBlock())) continue; // someone changed it meanwhile
            level.setBlockAndUpdate(slot.pos, Blocks.SCULK.defaultBlockState());
            burstParticlesAt(slot.pos);
        }
    }

    private void burstParticlesAt(BlockPos pos) {
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                4, 0.2, 0.3, 0.2, 0.02);
    }

    private void spawnAmbientParticles() {
        if (ticks % 2 != 0) return;
        RandomSource rng = level.getRandom();
        double currentRadius = Math.min(GROUND_RADIUS,
                (ticks + 1) * GROUND_RADIUS / IMPACT_DURATION);
        double angle = rng.nextDouble() * Math.PI * 2.0;
        double r = rng.nextDouble() * currentRadius;
        double px = center.x + Math.cos(angle) * r;
        double pz = center.z + Math.sin(angle) * r;
        double py = center.y + 0.1;
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                px, py, pz, 1, 0, 0.25, 0, 0.015);
    }

    /**
     * Per-mob identifier particles. Players need a clear signal of which mobs
     * belong to the encounter (i.e. who they must kill to stop the sculk
     * spread) — wispy soul particles emitted around each live spawned mob's
     * bounding box every 3 ticks.
     */
    private void spawnMobMarkerParticles() {
        if (ticks % 3 != 0) return;
        MobMarkerParticles.emit(level, spawnedMobs, ParticleTypes.SOUL);
    }

    private void castImpactShockwave() {
        if (caster instanceof LivingEntity living) {
            Services.SPELLS.castShockwave(level, living, SHOCKWAVE_LEVEL);
        }
    }

    private void endImpactPhase() {
        if (caster != null) {
            caster.discard();
            caster = null;
        }
        for (Entity e : spawnedMobs) {
            if (!e.isAlive()) continue;
            e.setInvulnerable(false);
            if (e instanceof net.minecraft.world.entity.Mob m) {
                m.setNoAi(false);
                // While noAi is on, LivingEntity#travel early-returns (isControlledByLocalInstance
                // falls through to isEffectiveAi == false), so push impulses from overlapping
                // neighbours accumulate in deltaMovement instead of being consumed by move(SELF).
                // Without this reset, the very next aiStep applies the whole accumulated vector
                // at once and ejects the mob — proportional to how many mobs shared its column.
                m.setDeltaMovement(Vec3.ZERO);
            }
        }
        seedVeinTips();
        phase = Phase.AFTERMATH;
    }

    // ---------- Phase 2 ----------

    private void tickAftermath() {
        spawnedMobs.removeIf(e -> !e.isAlive() || e.isRemoved());
        if (spawnedMobs.isEmpty()) {
            disbandGroup();
            phase = Phase.FINISHED;
            return;
        }

        spawnAmbientParticles();
        spawnMobMarkerParticles();

        if (!isPlayerNearby()) {
            return; // Pause growth, resume when a player comes back.
        }

        if (veinCount >= VEIN_CAP) {
            return;
        }

        if (aftermathTicks % VEIN_GROWTH_INTERVAL == 0) {
            for (int i = 0; i < VEIN_GROWTHS_PER_TICK; i++) {
                if (veinCount >= VEIN_CAP) break;
                growOneVein();
            }
        }
        aftermathTicks++;
    }

    private boolean isPlayerNearby() {
        AABB box = new AABB(centerBlock).inflate(PLAYER_DETECTION_RADIUS);
        return !level.getPlayers(p -> p.isAlive() && box.contains(p.position())).isEmpty();
    }

    /**
     * Seeds the growth frontier with positions distributed around the edge of
     * the sculk disk at equal angles. This is what lets the veins branch out
     * on every side instead of piling up on a single heading.
     *
     * <p>For each of {@value #VEIN_SEED_COUNT} compass directions we compute
     * a target (dx,dz) on the ring at GROUND_RADIUS, then pick the closest
     * existing sculk floor position to that point. Its {@code .above()} cell
     * becomes the starting tip — the first growth step will try to place a
     * vein on top of the sculk block there.</p>
     */
    private void seedVeinTips() {
        if (groundSlots.isEmpty()) return;
        for (int i = 0; i < VEIN_SEED_COUNT; i++) {
            double angle = 2.0 * Math.PI * i / VEIN_SEED_COUNT;
            double tx = center.x + Math.cos(angle) * GROUND_RADIUS;
            double tz = center.z + Math.sin(angle) * GROUND_RADIUS;
            GroundSlot best = null;
            double bestDistSqr = Double.MAX_VALUE;
            for (GroundSlot slot : groundSlots) {
                double ddx = slot.pos.getX() + 0.5 - tx;
                double ddz = slot.pos.getZ() + 0.5 - tz;
                double d = ddx * ddx + ddz * ddz;
                if (d < bestDistSqr) {
                    bestDistSqr = d;
                    best = slot;
                }
            }
            if (best != null) {
                veinTips.add(best.pos.above());
            }
        }
    }

    /**
     * Vertical offsets probed whenever a horizontal step is attempted, in
     * order of preference. Letting the walk climb or drop up to 2 blocks lets
     * the veins follow uneven terrain (stairs, cliffs, overhangs) instead of
     * dead-ending against the first step change.
     */
    private static final int[] VEIN_Y_SCAN = {0, 1, -1, 2, -2};

    private void growOneVein() {
        if (veinTips.isEmpty()) return;
        RandomSource rng = level.getRandom();

        BlockPos tip = veinTips.remove(rng.nextInt(veinTips.size()));

        // Each attempt picks a random step direction. Horizontal steps scan
        // vertically around the tip height for the first valid anchor, so a
        // single grown branch can walk up/down terrain cleanly.
        for (int attempt = 0; attempt < 6; attempt++) {
            Direction step = Direction.values()[rng.nextInt(6)];
            BlockPos candidate = pickCandidate(tip, step);
            if (candidate == null) continue;

            BlockState current = level.getBlockState(candidate);
            Direction hostDir = findSolidNeighbor(candidate, rng);
            if (hostDir == null) continue;

            if (placeVein(candidate, current, hostDir)) {
                veinedPositions.add(candidate);
                if (veinTips.size() < VEIN_TIPS_MAX) {
                    veinTips.add(candidate);
                }
                // Always keep the original tip alive so branches can fork from it.
                if (veinTips.size() < VEIN_TIPS_MAX) {
                    veinTips.add(tip);
                }
                veinCount++;
                burstVeinParticlesAt(candidate);
                return;
            }
        }

        // Couldn't grow from this tip this time — put it back at the end of the
        // queue (unless we're already saturated) so another attempt can reuse it.
        if (veinTips.size() < VEIN_TIPS_MAX) {
            veinTips.add(tip);
        }
    }

    /**
     * Resolves a growth direction into a concrete placement cell. For pure
     * vertical steps the result is the direct neighbor. For horizontal steps
     * we scan a small vertical window around the tip and return the first
     * cell that is free (air or existing vein), unvisited, and in range.
     */
    private BlockPos pickCandidate(BlockPos tip, Direction step) {
        int baseX = tip.getX() + step.getStepX();
        int baseZ = tip.getZ() + step.getStepZ();

        if (step.getAxis().isVertical()) {
            BlockPos candidate = new BlockPos(baseX, tip.getY() + step.getStepY(), baseZ);
            return isPlaceable(candidate) ? candidate : null;
        }

        for (int dy : VEIN_Y_SCAN) {
            BlockPos candidate = new BlockPos(baseX, tip.getY() + dy, baseZ);
            if (isPlaceable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isPlaceable(BlockPos candidate) {
        if (veinedPositions.contains(candidate)) return false;
        if (candidate.distSqr(centerBlock) > (long) VEIN_MAX_DISTANCE * VEIN_MAX_DISTANCE) return false;
        BlockState current = level.getBlockState(candidate);
        if (current.isAir()) return true;
        if (current.is(Blocks.SCULK_VEIN)) return true;
        // Replaceable plants (tall_grass, short_grass, ferns, snow layers,
        // seagrass…) are overwritten so veins can climb up through grassy
        // terrain instead of dead-ending on the first tuft.
        return current.canBeReplaced();
    }

    /**
     * Looks at the 6 neighbors of {@code pos} and returns a random direction
     * whose neighbor is a valid attach host for sculk_vein. Returns null if
     * none of the neighbors qualify.
     */
    private Direction findSolidNeighbor(BlockPos pos, RandomSource rng) {
        Direction[] shuffled = Direction.values().clone();
        for (int i = shuffled.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            Direction t = shuffled[i];
            shuffled[i] = shuffled[j];
            shuffled[j] = t;
        }
        for (Direction d : shuffled) {
            BlockState neighbor = level.getBlockState(pos.relative(d));
            if (MultifaceBlock.canAttachTo(level, d, pos.relative(d), neighbor)) {
                return d;
            }
        }
        return null;
    }

    /**
     * Places (or augments) a sculk_vein block at {@code pos}, pinning it to
     * the host in direction {@code hostDir}.
     */
    private boolean placeVein(BlockPos pos, BlockState current, Direction hostDir) {
        BooleanProperty prop = MultifaceBlock.getFaceProperty(hostDir);
        BlockState base = current.is(Blocks.SCULK_VEIN) ? current : Blocks.SCULK_VEIN.defaultBlockState();
        BlockState next = base.setValue(prop, true);
        return level.setBlockAndUpdate(pos, next);
    }

    private void burstVeinParticlesAt(BlockPos pos) {
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                3, 0.25, 0.25, 0.25, 0.01);
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                2, 0.2, 0.2, 0.2, 0.0);
    }
}
