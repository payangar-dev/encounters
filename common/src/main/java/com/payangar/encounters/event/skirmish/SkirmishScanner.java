package com.payangar.encounters.event.skirmish;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.event.ActiveEncounterTracker;
import com.payangar.encounters.event.EncounterScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Periodic scanner that drives natural triggering of patrol skirmishes.
 * Inherits the common check chain from {@link EncounterScanner}; this class
 * carries the event-specific gates (day-only, biome whitelist, clearing
 * footprint), the candidate picking around a player, and the audio-tease
 * commit strategy.
 *
 * <p>Once a candidate is validated, the spawn isn't immediate — a
 * {@link PendingTease} is queued for {@link #TEASE_DURATION_TICKS} ticks
 * during which periodic combat sounds are emitted at the site. The actual
 * skirmish spawn fires via {@link PatrolSkirmishEvent#forceTrigger} only
 * when that window elapses. While a tease is in flight on a given level,
 * the scanner does not queue another (see {@link #preTick}).</p>
 */
public final class SkirmishScanner extends EncounterScanner {

    public static final SkirmishScanner INSTANCE = new SkirmishScanner();

    private static final int CANDIDATE_MIN_DIST = 48;
    private static final int CANDIDATE_MAX_DIST = 100;

    /** Total duration of the audio tease before the actual spawn fires. */
    private static final int TEASE_DURATION_TICKS = 200; // 10 s @ 20 tps
    /** Minimum delay between two consecutive tease sounds. */
    private static final int TEASE_SOUND_MIN_INTERVAL = 25; // 1.25 s
    /** Maximum delay between two consecutive tease sounds. */
    private static final int TEASE_SOUND_MAX_INTERVAL = 80; // 4 s
    /**
     * Volume multiplier passed to {@code Level#playSound}. Vanilla audible
     * range scales linearly with this — value 3.0 carries the sound roughly
     * {@code 16 × 3 = 48} blocks, matching the intended "combat heard from
     * afar" feel.
     */
    private static final float TEASE_VOLUME = 3.0f;
    private static final float TEASE_PITCH_MIN = 0.7f;
    private static final float TEASE_PITCH_MAX = 1.1f;
    /** Sound pool — randomly drawn each emission. */
    private static final SoundEvent[] TEASE_SOUNDS = new SoundEvent[] {
            SoundEvents.IRON_GOLEM_ATTACK,
            SoundEvents.IRON_GOLEM_HURT,
            SoundEvents.PILLAGER_CELEBRATE,
            SoundEvents.VINDICATOR_AMBIENT
    };
    /**
     * Half-side of the clearing footprint check (10 = checks a 20×20 area).
     * Matches the maximum spawn distance from the anchor in
     * {@link PatrolSkirmish} ({@code POCKET_OFFSET=8 + POCKET_SCATTER=2}), so
     * every spawned mob lands inside the verified-flat zone.
     */
    private static final int CLEARING_RADIUS = 10;
    /** Maximum vertical delta tolerated between surrounding heights and the anchor. */
    private static final int CLEARING_MAX_DELTA = 2;
    /** Blocks of vertical air required above the anchor (large mobs like Ravager need clearance). */
    private static final int CLEARING_HEADROOM = 4;

    /** IDs we already warned about (invalid biome entries in config). Avoids log spam. */
    private static final Set<String> warnedInvalidBiomes = ConcurrentHashMap.newKeySet();

    /**
     * Pending tease entries. A site queued by the scanner first sits in this
     * list, emitting periodic combat sounds, then turns into a real spawn
     * when the {@code spawnAtTick} timestamp is reached.
     */
    private final List<PendingTease> pendingTeases = new CopyOnWriteArrayList<>();

    private static final class PendingTease {
        final ServerLevel level;
        final BlockPos pos;
        final long spawnAtTick;
        long nextSoundAtTick;

        PendingTease(ServerLevel level, BlockPos pos, long now, RandomSource rng) {
            this.level = level;
            this.pos = pos;
            this.spawnAtTick = now + TEASE_DURATION_TICKS;
            this.nextSoundAtTick = now + TEASE_SOUND_MIN_INTERVAL
                    + rng.nextInt(TEASE_SOUND_MAX_INTERVAL - TEASE_SOUND_MIN_INTERVAL);
        }
    }

    private SkirmishScanner() {
        super(PatrolSkirmishEvent.ID);
    }

    @Override
    protected boolean preTick(ServerLevel level) {
        processPendingTeases(level);
        // Don't queue another while one is in flight on this level.
        return !hasPendingTease(level);
    }

    @Override
    protected boolean enabled(EncountersConfig config) {
        return config.skirmish.enabled;
    }

    @Override
    protected int scanIntervalTicks(EncountersConfig config) {
        return config.skirmish.scanner.scanIntervalTicks;
    }

    @Override
    protected double triggerChance(EncountersConfig config) {
        return config.skirmish.scanner.triggerChance;
    }

    @Override
    protected boolean canTrigger(ServerLevel level, EncountersConfig config) {
        int active = ActiveEncounterTracker.activeCount(level, eventId);
        if (active >= config.skirmish.concurrency.maxConcurrent) {
            Constants.LOG.debug("[{}] scanner skipped: max concurrent reached ({}/{})",
                    eventId, active, config.skirmish.concurrency.maxConcurrent);
            return false;
        }
        if (!ActiveEncounterTracker.cooldownElapsed(level, eventId, config.skirmish.cooldown.cooldownTicks)) {
            Constants.LOG.debug("[{}] scanner skipped: post-event cooldown still active", eventId);
            return false;
        }
        if (config.skirmish.dayOnly && !level.isDay()) {
            Constants.LOG.debug("[{}] scanner skipped: dayOnly active but it is night", eventId);
            return false;
        }
        return true;
    }

    @Override
    protected void scan(ServerLevel level, EncountersConfig config) {
        List<? extends ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            Constants.LOG.debug("[{}] scanner skipped: no players in level", eventId);
            return;
        }

        RandomSource rng = level.getRandom();
        // One candidate per scan, attached to a random player. Keeps the cost
        // bounded regardless of player count; rate of skirmishes scales with
        // the configured scan interval and trigger chance, not population.
        ServerPlayer player = players.get(rng.nextInt(players.size()));
        if (!player.isAlive() || player.isSpectator()) {
            Constants.LOG.debug("[{}] scanner skipped: picked player not alive or spectating", eventId);
            return;
        }

        BlockPos candidate = pickCandidateSite(level, player, rng, config);
        if (candidate == null) return; // pickCandidateSite logs its own reason

        if (rng.nextDouble() >= config.skirmish.scanner.triggerChance) {
            Constants.LOG.debug("[{}] scanner skipped at ({}, {}, {}): chance roll missed",
                    eventId, candidate.getX(), candidate.getY(), candidate.getZ());
            return;
        }

        enqueueTease(level, candidate, rng);
    }

    @Override
    public void clear() {
        pendingTeases.clear();
    }

    @Override
    public void clearLevel(ServerLevel level) {
        pendingTeases.removeIf(t -> t.level == level);
    }

    // ---------- Audio tease ----------

    private void enqueueTease(ServerLevel level, BlockPos pos, RandomSource rng) {
        pendingTeases.add(new PendingTease(level, pos, level.getGameTime(), rng));
        Constants.LOG.info("[{}] tease scheduled at ({}, {}, {}) — spawn in {} ticks",
                eventId, pos.getX(), pos.getY(), pos.getZ(), TEASE_DURATION_TICKS);
    }

    private boolean hasPendingTease(ServerLevel level) {
        for (PendingTease t : pendingTeases) {
            if (t.level == level) return true;
        }
        return false;
    }

    private void processPendingTeases(ServerLevel level) {
        if (pendingTeases.isEmpty()) return;
        long now = level.getGameTime();
        Iterator<PendingTease> it = pendingTeases.iterator();
        while (it.hasNext()) {
            PendingTease t = it.next();
            if (t.level != level) continue;
            if (now >= t.spawnAtTick) {
                pendingTeases.remove(t);
                boolean started = PatrolSkirmishEvent.forceTrigger(level, Vec3.atBottomCenterOf(t.pos));
                if (!started) {
                    Constants.LOG.warn("[{}] tease completed at ({}, {}, {}) but spawn was refused",
                            eventId, t.pos.getX(), t.pos.getY(), t.pos.getZ());
                }
                continue;
            }
            if (now >= t.nextSoundAtTick) {
                playTeaseSound(level, t.pos);
                RandomSource rng = level.getRandom();
                int gap = TEASE_SOUND_MIN_INTERVAL
                        + rng.nextInt(TEASE_SOUND_MAX_INTERVAL - TEASE_SOUND_MIN_INTERVAL);
                t.nextSoundAtTick = now + gap;
            }
        }
    }

    private static void playTeaseSound(ServerLevel level, BlockPos pos) {
        RandomSource rng = level.getRandom();
        SoundEvent sound = TEASE_SOUNDS[rng.nextInt(TEASE_SOUNDS.length)];
        float pitch = TEASE_PITCH_MIN + rng.nextFloat() * (TEASE_PITCH_MAX - TEASE_PITCH_MIN);
        level.playSound(null, pos, sound, SoundSource.HOSTILE, TEASE_VOLUME, pitch);
    }

    // ---------- Candidate validation ----------

    private BlockPos pickCandidateSite(ServerLevel level, ServerPlayer player,
                                       RandomSource rng, EncountersConfig config) {
        double angle = rng.nextDouble() * Math.PI * 2;
        int distRange = CANDIDATE_MAX_DIST - CANDIDATE_MIN_DIST + 1;
        double dist = CANDIDATE_MIN_DIST + rng.nextInt(distRange);
        BlockPos playerPos = player.blockPosition();
        int x = playerPos.getX() + (int) Math.round(Math.cos(angle) * dist);
        int z = playerPos.getZ() + (int) Math.round(Math.sin(angle) * dist);

        if (!level.hasChunk(x >> 4, z >> 4)) {
            Constants.LOG.debug("[{}] scanner skipped at ({}, ?, {}): candidate chunk not loaded",
                    eventId, x, z);
            return null;
        }

        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos candidate = new BlockPos(x, y, z);
        Vec3 candidateVec = Vec3.atCenterOf(candidate);

        if (ActiveEncounterTracker.nearestActiveDistance(level, eventId, candidateVec)
                < config.skirmish.concurrency.minDistanceBetween) {
            Constants.LOG.debug("[{}] scanner skipped at ({}, {}, {}): too close to another active skirmish",
                    eventId, x, y, z);
            return null;
        }
        if (!isAllowedBiome(level, candidate, config)) {
            Constants.LOG.debug("[{}] scanner skipped at ({}, {}, {}): biome not in allowed list",
                    eventId, x, y, z);
            return null;
        }
        if (!isClearing(level, x, y, z)) {
            Constants.LOG.debug("[{}] scanner skipped at ({}, {}, {}): clearing footprint invalid",
                    eventId, x, y, z);
            return null;
        }
        return candidate;
    }

    private boolean isAllowedBiome(ServerLevel level, BlockPos pos, EncountersConfig config) {
        List<String> allowed = config.skirmish.biomes;
        if (allowed.isEmpty()) return false;
        Holder<Biome> holder = level.getBiome(pos);
        Optional<ResourceLocation> biomeId = holder.unwrapKey().map(k -> k.location());
        if (biomeId.isEmpty()) return false;
        String idStr = biomeId.get().toString();
        for (String entry : allowed) {
            if (entry.equals(idStr)) return true;
            // Validate entry once per unique invalid string — small footprint,
            // useful diagnostic when a typo (or removed mod's biome) breaks the
            // scan silently.
            if (ResourceLocation.tryParse(entry) == null) {
                warnOnceInvalidBiome(entry);
            }
        }
        return false;
    }

    private boolean isClearing(ServerLevel level, int cx, int cy, int cz) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -CLEARING_RADIUS; dx < CLEARING_RADIUS; dx++) {
            for (int dz = -CLEARING_RADIUS; dz < CLEARING_RADIUS; dz++) {
                int xx = cx + dx;
                int zz = cz + dz;
                if (!level.hasChunk(xx >> 4, zz >> 4)) return false;
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, xx, zz);
                if (Math.abs(h - cy) > CLEARING_MAX_DELTA) return false;
            }
        }
        // Headroom check above the anchor — large mobs need vertical space.
        for (int y = cy; y < cy + CLEARING_HEADROOM; y++) {
            cursor.set(cx, y, cz);
            if (level.getBlockState(cursor).blocksMotion()) return false;
        }
        return true;
    }

    private void warnOnceInvalidBiome(String id) {
        if (warnedInvalidBiomes.add(id)) {
            Constants.LOG.warn("[{}] invalid biome id in config: '{}' — entry ignored",
                    eventId, id);
        }
    }
}
