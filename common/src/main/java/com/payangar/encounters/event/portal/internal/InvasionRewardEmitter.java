package com.payangar.encounters.event.portal.internal;

import com.payangar.encounters.Constants;
import com.payangar.encounters.event.portal.NetherPortalInvasionEvent;
import com.payangar.encounters.event.portal.PortalRewards;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Reward stacks accumulator for the portal invasion. Rolled once on the
 * COMBAT→AFTERMATH transition and drained one stack at a time during
 * {@link #tickEmission(int, ServerLevel, Vec3, Direction)} until empty.
 *
 * <p>Decoupled from {@code PortalInvasion} so the wanted-count formula and
 * the staggered emission cadence can be tested or tuned in isolation.</p>
 */
public final class InvasionRewardEmitter {

    /** One stack every N server ticks (~1.6 stacks/sec at 12). */
    public static final int EMIT_INTERVAL_TICKS = 12;
    /** Pause between the last kill and the first reward beat. */
    public static final int INITIAL_DELAY_TICKS = 40;
    /** Hard cap on the number of reward stacks emitted at the end of any invasion. */
    public static final int MAX_STACKS = 12;
    /** Floor — every successful invasion yields at least this many stacks. */
    public static final int MIN_STACKS = 2;
    /** Approximate stacks yielded by a single roll of the reward loot table. */
    private static final int APPROX_STACKS_PER_ROLL = 5;
    /** Safety cap on extra refill rolls when the initial pool is short of {@code wanted}. */
    private static final int REFILL_ROLLS_CAP = 5;

    private final Deque<ItemStack> pending = new ArrayDeque<>();

    /**
     * Resolves the wanted-count from the wave count then rolls the loot table
     * enough times to satisfy it. Sampling from the rolled pool flattens the
     * native distribution slightly (rare items end up over-represented in the
     * sampled subset) but keeps the queue size predictable.
     */
    public void roll(ServerLevel level, Vec3 origin, int totalWaves) {
        RandomSource rng = level.getRandom();
        int wanted = computeWanted(totalWaves, rng);

        int rolls = Math.max(1, (wanted + APPROX_STACKS_PER_ROLL - 1) / APPROX_STACKS_PER_ROLL);
        List<ItemStack> pool = new ArrayList<>(PortalRewards.roll(level, origin, rolls));
        int refills = 0;
        while (pool.size() < wanted && refills++ < REFILL_ROLLS_CAP) {
            List<ItemStack> extra = PortalRewards.roll(level, origin, 1);
            if (extra.isEmpty()) break;
            pool.addAll(extra);
        }

        while (!pool.isEmpty() && pending.size() < wanted) {
            pending.addLast(pool.remove(rng.nextInt(pool.size())));
        }

        Constants.LOG.info("[{}] {} reward stacks queued (target {} for {} waves)",
                NetherPortalInvasionEvent.ID, pending.size(), wanted, totalWaves);
    }

    /**
     * Linear ramp from {@link #MIN_STACKS} to {@link #MAX_STACKS} based on the
     * wave count, with a small random spread inside the range. Tuned so:
     * 4 waves → [4, 6], 5 → [5, 8], 6 → [6, 10], 7 → [7, 12], 8+ → [waves, 12].
     */
    static int computeWanted(int totalWaves, RandomSource rng) {
        int waves = Math.max(2, totalWaves);
        int min = Math.min(MAX_STACKS, Math.max(MIN_STACKS, waves));
        int max = Math.min(MAX_STACKS, Math.max(min, 2 * (waves - 1)));
        return min + (max > min ? rng.nextInt(max - min + 1) : 0);
    }

    /** True iff a stack is still queued for emission. */
    public boolean hasPending() {
        return !pending.isEmpty();
    }

    /**
     * Emits one stack if {@code phaseTicks} has crossed the initial delay and
     * is on a beat. Returns {@code true} if the queue is empty after this call —
     * the caller uses the transition to start the calm-tail timer.
     */
    public boolean tickEmission(int phaseTicks, ServerLevel level, Vec3 origin, Direction face) {
        if (pending.isEmpty()) return true;
        int sinceDelay = phaseTicks - INITIAL_DELAY_TICKS;
        if (sinceDelay >= 0 && sinceDelay % EMIT_INTERVAL_TICKS == 0) {
            ItemStack stack = pending.poll();
            if (stack != null && !stack.isEmpty()) {
                PortalRewards.eject(level, origin, face, stack);
            }
        }
        return pending.isEmpty();
    }
}
