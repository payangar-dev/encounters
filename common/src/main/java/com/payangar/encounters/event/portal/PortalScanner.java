package com.payangar.encounters.event.portal;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.event.ActiveEncounterTracker;
import com.payangar.encounters.event.EncounterScanner;
import com.payangar.encounters.event.portal.PortalGeometry.PortalSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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
 * invasion event. Inherits the common check chain from
 * {@link EncounterScanner}; this class only carries the event-specific
 * gates, scan logic and commit strategy.
 *
 * <p>Every {@code portal.scanner.scanIntervalTicks} ticks (per overworld
 * level), the scanner sweeps a {@value #SCAN_RADIUS}-block cube of loaded
 * blocks around each living player looking for {@link Blocks#NETHER_PORTAL}.
 * Discovered blocks are de-duplicated to portal sites (same frame contributes
 * one site), shuffled, then each site rolls
 * {@code portal.scanner.triggerChance}. The first site whose roll succeeds
 * and whose geometry yields a valid spawn face triggers the invasion via
 * {@link NetherPortalInvasionEvent#forceTrigger}.</p>
 */
public final class PortalScanner extends EncounterScanner {

    public static final PortalScanner INSTANCE = new PortalScanner();

    private static final int SCAN_RADIUS = 12;

    private PortalScanner() {
        super(NetherPortalInvasionEvent.ID);
    }

    @Override
    protected boolean enabled(EncountersConfig config) {
        return config.portal.enabled;
    }

    @Override
    protected int scanIntervalTicks(EncountersConfig config) {
        return config.portal.scanner.scanIntervalTicks;
    }

    @Override
    protected double triggerChance(EncountersConfig config) {
        return config.portal.scanner.triggerChance;
    }

    @Override
    protected boolean canTrigger(ServerLevel level, EncountersConfig config) {
        int active = ActiveEncounterTracker.activeCount(level, eventId);
        if (active >= config.portal.concurrency.maxConcurrent) {
            Constants.LOG.debug("[{}] scanner skipped: max concurrent reached ({}/{})",
                    eventId, active, config.portal.concurrency.maxConcurrent);
            return false;
        }
        if (!ActiveEncounterTracker.cooldownElapsed(level, eventId, config.portal.cooldown.cooldownTicks)) {
            Constants.LOG.debug("[{}] scanner skipped: post-event cooldown still active", eventId);
            return false;
        }
        return true;
    }

    @Override
    protected void scan(ServerLevel level, EncountersConfig config) {
        List<BlockPos> portalBlocks = findPortalBlocksNearPlayers(level);
        if (portalBlocks.isEmpty()) {
            Constants.LOG.debug("[{}] scanner skipped: no portal candidates near players", eventId);
            return;
        }

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
        if (sites.isEmpty()) {
            Constants.LOG.debug("[{}] scanner skipped: no portal sites resolved from candidate blocks", eventId);
            return;
        }

        RandomSource rng = level.getRandom();
        shuffle(sites, rng);
        Constants.LOG.debug("[{}] scan: {} candidate portal site(s) near players", eventId, sites.size());

        for (PortalSite site : sites) {
            BlockPos sitePos = BlockPos.containing(site.centerBase());
            if (ActiveEncounterTracker.nearestActiveDistance(level, eventId, site.centerBase())
                    < config.portal.concurrency.minDistanceBetween) {
                Constants.LOG.debug("[{}] scanner skipped at ({}, {}, {}): too close to another active invasion",
                        eventId, sitePos.getX(), sitePos.getY(), sitePos.getZ());
                continue;
            }
            if (rng.nextDouble() >= config.portal.scanner.triggerChance) {
                Constants.LOG.debug("[{}] scanner skipped at ({}, {}, {}): chance roll missed",
                        eventId, sitePos.getX(), sitePos.getY(), sitePos.getZ());
                continue;
            }
            Optional<Direction> face = PortalGeometry.chooseSpawnFace(level, site, rng);
            if (face.isEmpty()) {
                Constants.LOG.debug("[{}] scanner skipped at ({}, {}, {}): no valid spawn face",
                        eventId, sitePos.getX(), sitePos.getY(), sitePos.getZ());
                continue;
            }
            if (NetherPortalInvasionEvent.forceTrigger(level, site, face.get())) {
                return; // one trigger per scan tick is enough; the next interval will pick another candidate
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
