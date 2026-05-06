package com.payangar.encounters.event.portal;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.event.portal.PortalGeometry.PortalSite;
import com.payangar.encounters.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Periodic scanner that drives natural triggering of the nether portal
 * invasion event.
 *
 * <p>Every {@code netherPortalInvasionScanIntervalTicks} server ticks, for
 * each living overworld player, the scanner sweeps a {@value #SCAN_RADIUS}-
 * block cube of loaded blocks looking for {@link Blocks#NETHER_PORTAL}.
 * Discovered blocks are de-duplicated to portal sites (same frame contributes
 * one site), shuffled, then each site rolls
 * {@code netherPortalInvasionTriggerChance}. The first site whose roll
 * succeeds and whose geometry yields a valid spawn face triggers the
 * invasion via {@link NetherPortalInvasionEvent#forceTrigger}.</p>
 *
 * <p>The global lock and world-wide cooldown are enforced upstream by
 * {@link NetherPortalInvasionEvent#canScannerTrigger}.</p>
 */
public final class PortalScanner {

    private static final int SCAN_RADIUS = 12;

    private static boolean registered = false;

    private PortalScanner() {}

    public static synchronized void initialize() {
        if (registered) return;
        Services.PLATFORM.registerServerLevelTickListener(PortalScanner::onLevelTick);
        registered = true;
    }

    private static void onLevelTick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        EncountersConfig config = EncountersConfig.get();
        if (!config.netherPortalInvasionEnabled) return;
        long interval = Math.max(20, config.netherPortalInvasionScanIntervalTicks);
        if (level.getGameTime() % interval != 0) return;
        if (config.netherPortalInvasionTriggerChance <= 0.0) return;
        if (!NetherPortalInvasionEvent.canScannerTrigger(level)) return;

        scanAndMaybeTrigger(level, config);
    }

    private static void scanAndMaybeTrigger(ServerLevel level, EncountersConfig config) {
        List<BlockPos> portalBlocks = findPortalBlocksNearPlayers(level);
        if (portalBlocks.isEmpty()) return;

        // De-dupe to unique portal sites (the same frame yields several
        // portal blocks; only one chance roll per frame).
        Set<Vec3> seen = new HashSet<>();
        List<PortalSite> sites = new ArrayList<>();
        for (BlockPos b : portalBlocks) {
            Optional<PortalSite> site = PortalGeometry.analyze(level, b);
            if (site.isPresent() && seen.add(site.get().centerBase())) {
                sites.add(site.get());
            }
        }
        if (sites.isEmpty()) return;

        RandomSource rng = level.getRandom();
        shuffle(sites, rng);
        Constants.LOG.debug("[{}] scan: {} candidate portal site(s) near players",
                NetherPortalInvasionEvent.ID, sites.size());

        for (PortalSite site : sites) {
            if (rng.nextDouble() >= config.netherPortalInvasionTriggerChance) continue;
            Optional<Direction> face = PortalGeometry.chooseSpawnFace(level, site, rng);
            if (face.isEmpty()) continue;
            if (NetherPortalInvasionEvent.forceTrigger(level, site, face.get())) {
                return; // success — only one invasion at a time anyway
            }
        }
    }

    private static List<BlockPos> findPortalBlocksNearPlayers(ServerLevel level) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int worldMinY = level.getMinBuildHeight();
        int worldMaxY = level.getMaxBuildHeight() - 1;

        for (ServerPlayer player : level.players()) {
            if (!player.isAlive()) continue;
            BlockPos centre = player.blockPosition();
            int cx = centre.getX();
            int cy = centre.getY();
            int cz = centre.getZ();
            int yMin = Math.max(worldMinY, cy - SCAN_RADIUS);
            int yMax = Math.min(worldMaxY, cy + SCAN_RADIUS);
            for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    int x = cx + dx;
                    int z = cz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue; // never force-load
                    for (int y = yMin; y <= yMax; y++) {
                        cursor.set(x, y, z);
                        if (level.getBlockState(cursor).is(Blocks.NETHER_PORTAL)) {
                            found.add(cursor.immutable());
                        }
                    }
                }
            }
        }
        return found;
    }

    private static <T> void shuffle(List<T> list, RandomSource rng) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            Collections.swap(list, i, j);
        }
    }
}
