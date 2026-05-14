package com.payangar.encounters.event.skirmish;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.event.ActiveEncounterTracker;
import com.payangar.encounters.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
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
 *
 * <p>Every {@code patrolSkirmishScanIntervalTicks} ticks (per overworld
 * level), the scanner picks a random alive player, rolls a candidate site
 * in a 48-100 block ring around them, validates the site (biome whitelist
 * + clearing footprint + min distance to other active skirmishes), and
 * fires the trigger chance roll. Successful candidates are queued as a
 * {@link PendingTease} for {@link #TEASE_DURATION_TICKS} ticks during
 * which periodic combat sounds are emitted at the site — only when that
 * window elapses is the actual skirmish spawned via
 * {@link PatrolSkirmishEvent#forceTrigger}.</p>
 *
 * <p>The concurrency cap and post-skirmish cooldown are enforced upstream
 * by {@link PatrolSkirmishEvent#canScannerTrigger}. While a tease is in
 * flight on a given level, the scanner won't queue another to avoid
 * stacked spawns.</p>
 */
public final class SkirmishScanner {

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

    private static boolean registered = false;

    /** IDs we already warned about (invalid biome entries in config). Avoids log spam. */
    private static final Set<String> warnedInvalidBiomes = ConcurrentHashMap.newKeySet();

    /**
     * Pending tease entries. A site queued by the scanner first sits in this
     * list, emitting periodic combat sounds, then turns into a real spawn
     * when the {@code spawnAtTick} timestamp is reached.
     */
    private static final List<PendingTease> pendingTeases = new CopyOnWriteArrayList<>();

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

    private SkirmishScanner() {}

    public static synchronized void initialize() {
        if (registered) return;
        Services.PLATFORM.registerServerLevelTickListener(SkirmishScanner::onLevelTick);
        registered = true;
    }

    /** Drops every pending tease. Used at server stop. */
    public static void clear() {
        pendingTeases.clear();
    }

    /** Drops every pending tease bound to {@code level}. Used at level unload. */
    public static void clearLevel(ServerLevel level) {
        pendingTeases.removeIf(t -> t.level == level);
    }

    private static void onLevelTick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        processPendingTeases(level);
        if (hasPendingTease(level)) return; // don't queue another while one is in flight
        EncountersConfig config = EncountersConfig.get();
        if (!config.patrolSkirmishEnabled) return;
        long interval = Math.max(20, config.patrolSkirmishScanIntervalTicks);
        if (level.getGameTime() % interval != 0) return;
        if (config.patrolSkirmishTriggerChance <= 0.0) return;
        // Early-return on concurrency cap + cooldown before doing any per-player work.
        if (!PatrolSkirmishEvent.canScannerTrigger(level, config)) return;
        if (config.patrolSkirmishDayOnly && !level.isDay()) return;

        List<? extends ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        RandomSource rng = level.getRandom();

        // One candidate per scan, attached to a random player. Keeps the cost
        // bounded regardless of player count; rate of skirmishes scales with
        // the configured scan interval and trigger chance, not population.
        ServerPlayer player = players.get(rng.nextInt(players.size()));
        if (!player.isAlive() || player.isSpectator()) return;

        BlockPos candidate = pickCandidateSite(level, player, rng, config);
        if (candidate == null) return;

        if (rng.nextDouble() >= config.patrolSkirmishTriggerChance) return;

        enqueueTease(level, candidate, rng);
    }

    // ---------- Audio tease ----------

    private static void enqueueTease(ServerLevel level, BlockPos pos, RandomSource rng) {
        pendingTeases.add(new PendingTease(level, pos, level.getGameTime(), rng));
        Constants.LOG.info("[{}] tease scheduled at ({}, {}, {}) — spawn in {} ticks",
                PatrolSkirmishEvent.ID, pos.getX(), pos.getY(), pos.getZ(), TEASE_DURATION_TICKS);
    }

    private static boolean hasPendingTease(ServerLevel level) {
        for (PendingTease t : pendingTeases) {
            if (t.level == level) return true;
        }
        return false;
    }

    private static void processPendingTeases(ServerLevel level) {
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
                            PatrolSkirmishEvent.ID, t.pos.getX(), t.pos.getY(), t.pos.getZ());
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

    private static BlockPos pickCandidateSite(ServerLevel level, ServerPlayer player,
                                              RandomSource rng, EncountersConfig config) {
        double angle = rng.nextDouble() * Math.PI * 2;
        int distRange = CANDIDATE_MAX_DIST - CANDIDATE_MIN_DIST + 1;
        double dist = CANDIDATE_MIN_DIST + rng.nextInt(distRange);
        BlockPos playerPos = player.blockPosition();
        int x = playerPos.getX() + (int) Math.round(Math.cos(angle) * dist);
        int z = playerPos.getZ() + (int) Math.round(Math.sin(angle) * dist);

        if (!level.hasChunk(x >> 4, z >> 4)) return null;

        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos candidate = new BlockPos(x, y, z);
        Vec3 candidateVec = Vec3.atCenterOf(candidate);

        if (ActiveEncounterTracker.nearestActiveDistance(level, candidateVec)
                < config.patrolSkirmishMinDistanceBetween) return null;
        if (!isAllowedBiome(level, candidate, config)) return null;
        if (!isClearing(level, x, y, z)) return null;
        return candidate;
    }

    private static boolean isAllowedBiome(ServerLevel level, BlockPos pos, EncountersConfig config) {
        List<String> allowed = config.patrolSkirmishBiomes;
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

    private static boolean isClearing(ServerLevel level, int cx, int cy, int cz) {
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

    private static void warnOnceInvalidBiome(String id) {
        if (warnedInvalidBiomes.add(id)) {
            Constants.LOG.warn("[{}] invalid biome id in config: '{}' — entry ignored",
                    PatrolSkirmishEvent.ID, id);
        }
    }
}
