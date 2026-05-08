package com.payangar.encounters.event.portal;

import com.payangar.encounters.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Reward emission helpers for the portal invasion event. Split into two
 * stages so the cinematic can stagger the drops over time:
 *
 * <ol>
 *   <li>{@link #roll(ServerLevel, Vec3, int)} — looks up the reward loot
 *       table, rolls it {@code rolls} times and returns the flattened
 *       list of non-empty {@link ItemStack}s. Pure computation.</li>
 *   <li>{@link #eject(ServerLevel, Vec3, Direction, ItemStack)} — spawns a
 *       single {@link ItemEntity} from {@code origin} with a random
 *       velocity inside a forward cone around {@code face}, plays the
 *       trial-chamber-vault eject sound, and sets the portal cooldown so
 *       the item cannot be re-teleported back through the gateway.</li>
 * </ol>
 *
 * <p>The default loot table at
 * {@code data/encounters/loot_table/portal_invasion/reward.json} delegates
 * to vanilla nether tables (bastion variants, nether bridge), so any mod
 * injecting into those (NeoForge GLMs, Fabric loot events, datapack
 * overrides) automatically appears in the rewards without configuration.</p>
 */
public final class PortalRewards {

    /** Half-width of the forward cone (degrees) — items spread ±this around the spawn face. */
    private static final double YAW_SPREAD_DEG = 50.0;
    /** Horizontal velocity range — fast enough to clear the portal frame. */
    private static final double HORIZ_MIN = 0.30;
    private static final double HORIZ_MAX = 0.55;
    /** Vertical (upward) velocity range — gives items a clear lob arc. */
    private static final double VERT_MIN = 0.20;
    private static final double VERT_MAX = 0.55;

    private PortalRewards() {}

    /**
     * Rolls the portal invasion reward loot table {@code rolls} times and
     * returns the flattened list of non-empty stacks. Returns an empty list
     * (with INFO log) if the table resolves to {@link LootTable#EMPTY}.
     */
    public static List<ItemStack> roll(ServerLevel level, Vec3 origin, int rolls) {
        if (rolls <= 0) return List.of();
        LootTable table = level.getServer().reloadableRegistries()
                .getLootTable(NetherPortalInvasionEvent.REWARD_LOOT_TABLE);
        if (table == LootTable.EMPTY) {
            Constants.LOG.info("[{}] reward loot table {} resolved to EMPTY — no rewards rolled",
                    NetherPortalInvasionEvent.ID,
                    NetherPortalInvasionEvent.REWARD_LOOT_TABLE.location());
            return List.of();
        }

        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, origin)
                .create(LootContextParamSets.CHEST);

        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < rolls; i++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                if (!stack.isEmpty()) result.add(stack);
            }
        }
        return result;
    }

    /**
     * Spawns a single reward {@link ItemEntity} from {@code origin}. Velocity
     * is sampled inside a forward cone around {@code face} (yaw spread
     * ±{@value #YAW_SPREAD_DEG}°, randomised horizontal and vertical
     * components) so consecutive items scatter visibly instead of stacking.
     *
     * <p>Each item gets {@code setPortalCooldown()} so the vanilla
     * {@code Entity#handleInsidePortal} accumulator is suppressed for the
     * 300-tick (~15 s) cooldown window — far longer than the time needed
     * for the outward velocity to carry the item out of the portal AABB.
     * Without this, items spawned at the portal mouth would teleport
     * straight to the nether and be lost.</p>
     */
    public static void eject(ServerLevel level, Vec3 origin, Direction face, ItemStack stack) {
        if (stack.isEmpty()) return;
        RandomSource rng = level.getRandom();

        ItemEntity entity = new ItemEntity(level, origin.x, origin.y, origin.z, stack.copy());

        double yawDev = (rng.nextDouble() - 0.5) * 2.0 * Math.toRadians(YAW_SPREAD_DEG);
        double horizMag = HORIZ_MIN + rng.nextDouble() * (HORIZ_MAX - HORIZ_MIN);
        double vertMag = VERT_MIN + rng.nextDouble() * (VERT_MAX - VERT_MIN);
        double fx = face.getStepX();
        double fz = face.getStepZ();
        double cos = Math.cos(yawDev);
        double sin = Math.sin(yawDev);
        double vx = (fx * cos - fz * sin) * horizMag;
        double vz = (fx * sin + fz * cos) * horizMag;

        entity.setDeltaMovement(vx, vertMag, vz);
        entity.setDefaultPickUpDelay();
        entity.setPortalCooldown();
        level.addFreshEntity(entity);

        float pitch = 0.85f + rng.nextFloat() * 0.30f;
        level.playSound(null, BlockPos.containing(origin),
                SoundEvents.VAULT_EJECT_ITEM, SoundSource.BLOCKS, 0.7f, pitch);
    }
}
