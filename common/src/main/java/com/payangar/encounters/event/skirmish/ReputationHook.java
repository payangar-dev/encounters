package com.payangar.encounters.event.skirmish;

import com.payangar.encounters.Constants;
import com.payangar.encounters.platform.Services;
import com.payangar.encounters.platform.services.EntityInteractListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.Team;

/**
 * Reputation gossip emitter for caravan interactions. When a player mounts
 * or opens the chest of a caravan pack animal flagged on the villager side,
 * applies a vanilla gossip penalty on the nearest live caravan carrier
 * ({@link Villager}). Vanilla's {@code DefendVillageTargetGoal} and Guard
 * Villagers' {@code DefendVillageGuardGoal} pick up the resulting reputation
 * drop automatically — golems and guards turn hostile if it crosses -100.
 *
 * <p>No penalty applies if the carrier is already dead — the kill itself
 * propagated {@code MAJOR_NEGATIVE} gossip through the standard villager
 * death path, so the player has already paid for that one.</p>
 *
 * <p>Illager-side caravans have no equivalent system; illagers don't
 * maintain reputation, so the carrier is just killable without remorse.
 * Asymmetry is intentional — voler les illagers est moins risqué
 * socialement, c'est aussi moins gratifiant moralement.</p>
 *
 * <p>The hook is installed once at boot via {@link #install()}, which
 * registers a single listener through {@link Services#PLATFORM}.</p>
 */
public final class ReputationHook implements EntityInteractListener {

    /** NBT tag stamped on pack animals belonging to a villager caravan. */
    public static final String CARAVAN_TAG_VILLAGER = "encounters_caravan_villager";
    /** NBT tag stamped on pack animals belonging to an illager caravan. */
    public static final String CARAVAN_TAG_ILLAGER = "encounters_caravan_illager";

    /** Gossip points added on mount (MINOR_NEGATIVE × 5 = rep impact -5). */
    private static final int MOUNT_PENALTY = 5;
    /** Gossip points added on chest open (MAJOR_NEGATIVE × 10 = rep impact -50). */
    private static final int OPEN_CHEST_PENALTY = 10;
    /** Search radius for the nearest carrier villager when applying gossip. */
    private static final double CARRIER_SEARCH_RADIUS = 32.0;

    private static boolean installed = false;

    private ReputationHook() {}

    public static synchronized void install() {
        if (installed) return;
        Services.PLATFORM.registerEntityInteractListener(new ReputationHook());
        installed = true;
    }

    @Override
    public InteractionResult onInteract(Player player, Entity entity, InteractionHand hand) {
        // Vanilla fires the event for both hands — only react once to avoid
        // double-applying the gossip penalty.
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player.level().isClientSide()) return InteractionResult.PASS;
        if (!(entity instanceof AbstractChestedHorse animal)) return InteractionResult.PASS;
        if (!animal.getTags().contains(CARAVAN_TAG_VILLAGER)) return InteractionResult.PASS;

        boolean opening = player.isSecondaryUseActive();
        int gossipPoints = opening ? OPEN_CHEST_PENALTY : MOUNT_PENALTY;
        GossipType gossipType = opening ? GossipType.MAJOR_NEGATIVE : GossipType.MINOR_NEGATIVE;

        Villager carrier = findNearestCarrier((ServerLevel) player.level(), animal);
        if (carrier == null) return InteractionResult.PASS;

        carrier.getGossips().add(player.getUUID(), gossipType, gossipPoints);
        Constants.LOG.debug("[{}] reputation: player {} {} caravan animal — +{} {} on carrier {}",
                PatrolSkirmishEvent.ID, player.getName().getString(),
                opening ? "opened chest of" : "mounted",
                gossipPoints, gossipType, carrier.getUUID());

        return InteractionResult.PASS;
    }

    /**
     * Finds the nearest live {@link Villager} on the same allies team as
     * {@code caravanAnimal}, within {@link #CARRIER_SEARCH_RADIUS} blocks.
     * Returns {@code null} if the carrier is already dead or no villager is
     * within range.
     */
    private static Villager findNearestCarrier(ServerLevel level, Entity caravanAnimal) {
        Team team = caravanAnimal.getTeam();
        if (team == null) return null;
        AABB box = caravanAnimal.getBoundingBox().inflate(CARRIER_SEARCH_RADIUS);
        Villager closest = null;
        double minDistSqr = Double.MAX_VALUE;
        for (Villager v : level.getEntitiesOfClass(Villager.class, box)) {
            if (!v.isAlive()) continue;
            if (v.getTeam() != team) continue;
            double d = v.position().distanceToSqr(caravanAnimal.position());
            if (d < minDistSqr) {
                minDistSqr = d;
                closest = v;
            }
        }
        return closest;
    }
}
