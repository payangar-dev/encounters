package com.payangar.encounters.event.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Analyses nether portal frames and the surface around them.
 *
 * <p>From any portal block, {@link #analyze} walks down to the bottom row and
 * along the portal's axis to find the full base width. The resulting
 * {@link PortalSite} captures the bottom-row centre (the spot a mob would
 * stand in front of), the axis the frame extends along, and how many blocks
 * wide the base is (typically 2 for a vanilla portal).</p>
 *
 * <p>{@link #chooseSpawnFace} then picks one of the two perpendicular faces
 * to spawn from. A face is valid iff every column of the base width has, one
 * step out: a replaceable block at foot height, a replaceable block at head
 * height, and a sturdy floor below. If both faces are valid the choice is
 * 50/50; if neither is, the portal cannot host an invasion.</p>
 */
public final class PortalGeometry {

    /** Defensive cap on the number of blocks to walk in any direction when probing the frame. */
    private static final int MAX_PORTAL_DIM = 32;

    private PortalGeometry() {}

    public record PortalSite(Vec3 centerBase, Direction.Axis axis, int width) {}

    /**
     * Searches a cube of half-side {@code radius} around {@code from} for the
     * closest nether portal block. Returns empty if none found within range.
     */
    public static Optional<BlockPos> findNearbyPortalBlock(ServerLevel level, Vec3 from, int radius) {
        BlockPos origin = BlockPos.containing(from);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.getBlockState(cursor).is(Blocks.NETHER_PORTAL)) continue;
                    double d = cursor.distSqr(origin);
                    if (d < bestDistSqr) {
                        bestDistSqr = d;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Walks down to the bottom row and along the axis to determine the
     * portal's full base. Returns empty if {@code anyPortalBlock} is not
     * actually a portal block (e.g. it was broken since detection).
     */
    public static Optional<PortalSite> analyze(ServerLevel level, BlockPos anyPortalBlock) {
        BlockState state = level.getBlockState(anyPortalBlock);
        if (!state.is(Blocks.NETHER_PORTAL)) return Optional.empty();
        Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);

        // Walk down to the bottom row.
        BlockPos bottom = anyPortalBlock;
        for (int i = 0; i < MAX_PORTAL_DIM; i++) {
            BlockPos below = bottom.below();
            if (!level.getBlockState(below).is(Blocks.NETHER_PORTAL)) break;
            bottom = below;
        }

        Direction positive = directionAlong(axis, true);
        Direction negative = directionAlong(axis, false);

        BlockPos minBlock = bottom;
        BlockPos maxBlock = bottom;
        for (int i = 1; i < MAX_PORTAL_DIM; i++) {
            BlockPos test = bottom.relative(positive, i);
            if (!level.getBlockState(test).is(Blocks.NETHER_PORTAL)) break;
            maxBlock = test;
        }
        for (int i = 1; i < MAX_PORTAL_DIM; i++) {
            BlockPos test = bottom.relative(negative, i);
            if (!level.getBlockState(test).is(Blocks.NETHER_PORTAL)) break;
            minBlock = test;
        }

        int minCoord = axisCoord(minBlock, axis);
        int maxCoord = axisCoord(maxBlock, axis);
        int width = maxCoord - minCoord + 1;

        // Centre of the bottom row in world coordinates: spans from minCoord
        // to maxCoord+1 (block edges), midpoint is (min + max + 1) / 2.
        double centerCoord = (minCoord + maxCoord + 1) / 2.0;
        double cx = axis == Direction.Axis.X ? centerCoord : bottom.getX() + 0.5;
        double cz = axis == Direction.Axis.Z ? centerCoord : bottom.getZ() + 0.5;
        Vec3 centerBase = new Vec3(cx, bottom.getY(), cz);
        return Optional.of(new PortalSite(centerBase, axis, width));
    }

    /**
     * Picks one of the two perpendicular faces of the portal. Both valid →
     * 50/50; only one valid → that one; neither → empty.
     */
    public static Optional<Direction> chooseSpawnFace(ServerLevel level, PortalSite site, RandomSource rng) {
        Direction[] faces = perpendicularFaces(site.axis());
        boolean a = isFaceValid(level, site, faces[0]);
        boolean b = isFaceValid(level, site, faces[1]);
        if (a && b) return Optional.of(rng.nextBoolean() ? faces[0] : faces[1]);
        if (a) return Optional.of(faces[0]);
        if (b) return Optional.of(faces[1]);
        return Optional.empty();
    }

    /**
     * For each column of the portal base width, the cell one step out in
     * {@code face} must have: walkable foot, walkable head, sturdy floor.
     */
    private static boolean isFaceValid(ServerLevel level, PortalSite site, Direction face) {
        int baseY = (int) Math.floor(site.centerBase().y);
        Direction.Axis axis = site.axis();
        double centerCoord = axis == Direction.Axis.X ? site.centerBase().x : site.centerBase().z;
        int min = (int) Math.floor(centerCoord - site.width() / 2.0);
        int otherCoord = axis == Direction.Axis.X
                ? (int) Math.floor(site.centerBase().z)
                : (int) Math.floor(site.centerBase().x);

        for (int i = 0; i < site.width(); i++) {
            int axisCoord = min + i;
            int x = axis == Direction.Axis.X ? axisCoord : otherCoord;
            int z = axis == Direction.Axis.Z ? axisCoord : otherCoord;
            BlockPos basePortalBlock = new BlockPos(x, baseY, z);
            BlockPos facePos = basePortalBlock.relative(face);
            BlockState feet = level.getBlockState(facePos);
            BlockState head = level.getBlockState(facePos.above());
            BlockPos floorPos = facePos.below();
            BlockState floor = level.getBlockState(floorPos);

            if (!isWalkable(feet)) return false;
            if (!isWalkable(head)) return false;
            if (!floor.isFaceSturdy(level, floorPos, Direction.UP)) return false;
        }
        return true;
    }

    /**
     * Centre point of the spawn surface, one block in front of the portal
     * base, on the floor — useful as the cinematic anchor and the first
     * spawn position.
     */
    public static Vec3 spawnAnchor(PortalSite site, Direction face) {
        Vec3 c = site.centerBase();
        return new Vec3(c.x + face.getStepX(), c.y, c.z + face.getStepZ());
    }

    private static boolean isWalkable(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    private static Direction directionAlong(Direction.Axis axis, boolean positive) {
        if (axis == Direction.Axis.X) return positive ? Direction.EAST : Direction.WEST;
        return positive ? Direction.SOUTH : Direction.NORTH;
    }

    private static Direction[] perpendicularFaces(Direction.Axis axis) {
        return axis == Direction.Axis.X
                ? new Direction[]{Direction.NORTH, Direction.SOUTH}
                : new Direction[]{Direction.EAST, Direction.WEST};
    }

    private static int axisCoord(BlockPos pos, Direction.Axis axis) {
        return axis == Direction.Axis.X ? pos.getX() : pos.getZ();
    }
}
