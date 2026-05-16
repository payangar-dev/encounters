package com.payangar.encounters.event.skirmish;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.event.EncounterSpawner;
import com.payangar.encounters.event.MobRoster;
import com.payangar.encounters.event.ResolvedMob;
import com.payangar.encounters.event.ally.EncounterAllies;
import com.payangar.encounters.event.cinematic.Cinematic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Patrol skirmish cinematic — a bilateral encounter between a villager-side
 * patrol and an illager-side patrol anchored at a clearing in the overworld.
 *
 * <p>Spawns once at construction: two pockets of
 * {@link EncountersConfig#patrolSkirmishMobsPerSide} mobs each, separated
 * by {@code 2 × POCKET_OFFSET} blocks along a random horizontal axis. No
 * wave system; combat ends when one faction is eliminated (or has every
 * member outside the out-of-zone radius), or when
 * {@link EncountersConfig#patrolSkirmishTimeoutTicks} elapses.</p>
 *
 * <p>After combat ends, a reward state machine takes over:</p>
 * <pre>
 *   NOT_STARTED ──(combat ends)──┬── playerParticipated=false ──► EXPIRED
 *                                └── playerParticipated=true  ──► PENDING
 *   PENDING ──(eligible player + leader found)──► DELIVERING
 *   PENDING ──(reward wait timeout)──► EXPIRED
 *   DELIVERING ──(leader reaches player)──► DELIVERED
 *   DELIVERING ──(travel watchdog)──► DELIVERED (fallback drop at leader)
 *   DELIVERED / EXPIRED ──► finishAndRelease
 * </pre>
 *
 * <p>"Eligible player" = alive, non-spectator, within
 * {@link #REWARD_SEARCH_RADIUS} blocks of the anchor, NOT currently being
 * targeted by any villager-side mob. The latter encodes "hostile player"
 * without a custom rep tracker — vanilla {@code Mob#getTarget()} already
 * reflects it.</p>
 *
 * <p>Leader cascade priority: Villager (caravan carrier) → Guard
 * (Guard Villagers) → Iron Golem → any surviving villager-side mob.</p>
 */
public final class PatrolSkirmish implements Cinematic {

    /** Half-distance between the two pockets along the chosen axis. */
    private static final double POCKET_OFFSET = 8.0;
    /** Random scatter applied to each mob within its pocket (full range = 2× this). */
    private static final double POCKET_SCATTER = 2.0;
    /** All members of a faction beyond this squared distance from the anchor end the combat. */
    private static final double OUT_OF_ZONE_SQR = 12544.0; // 112² — matches vanilla Raid.updateRaiders
    /** Idle mobs beyond this squared distance from the anchor get nav-ordered back. */
    private static final double LEASH_RADIUS_SQR = 20.0 * 20.0;
    private static final double LEASH_SPEED = 1.0;
    private static final int LEASH_INTERVAL_TICKS = 20;

    /** Search radius for eligible reward recipients (around the anchor). */
    private static final double REWARD_SEARCH_RADIUS = 64.0;
    /** Leader is "close enough" to the player to drop the chest at this squared distance. */
    private static final double DELIVERY_DIST_SQR = 3.0 * 3.0;
    /** Travel speed override used while the leader walks to the player. */
    private static final double LEADER_SPEED = 1.1;
    /** Maximum number of ticks the leader is allowed to travel before fallback-dropping. */
    private static final int LEADER_TRAVEL_TIMEOUT_TICKS = 600;
    /** Cadence at which we re-issue the leader navigation order and emit particles. */
    private static final int LEADER_TICK_INTERVAL = 10;

    /**
     * SNBT tag stamped on roster entries that should consume two slots in
     * the spawn budget. Any entry carrying this tag costs 2 instead of 1
     * when {@link #spawnPocket} draws it, balancing strong combatants
     * (Iron Golem out of the box, Ravager via user config, etc.) against
     * lighter ones.
     */
    public static final String HIGH_COST_TAG = "encounters_high_cost";

    /**
     * Reward delivery state machine. The skirmish transitions {@code
     * NOT_STARTED → (PENDING|EXPIRED) → (DELIVERING|EXPIRED) →
     * (DELIVERED|EXPIRED)}. Terminal states ({@code DELIVERED}, {@code
     * EXPIRED}) trigger {@link #finishAndRelease}.
     */
    public enum RewardState { NOT_STARTED, PENDING, DELIVERING, EXPIRED, DELIVERED }

    private final ServerLevel level;
    private final BlockPos anchorPos;
    private final Vec3 anchor;
    private final String villagerGroup;
    private final String illagerGroup;
    private final List<Mob> villagerMobs = new ArrayList<>();
    private final List<Mob> illagerMobs = new ArrayList<>();
    /**
     * Caravan members (carrier + pack animal) per faction. Stored separately
     * from combat lists so they don't contribute to the end-of-combat
     * evaluation (the carrier may flee, the pack animal stays put) yet remain
     * part of the faction's allies team for friendly-fire protection.
     */
    private final List<Mob> villagerCaravanMobs = new ArrayList<>();
    private final List<Mob> illagerCaravanMobs = new ArrayList<>();

    private int tickCount = 0;
    private boolean finished = false;

    /** Set to true the first tick we observe any mob in the skirmish was struck by a player. */
    private boolean playerParticipated = false;
    private RewardState rewardState = RewardState.NOT_STARTED;
    private int rewardPendingTicks = 0;
    private Mob currentLeader = null;
    private Player targetedPlayer = null;
    private int leaderTravelTicks = 0;

    public PatrolSkirmish(ServerLevel level, BlockPos anchorPos) {
        this.level = level;
        this.anchorPos = anchorPos;
        this.anchor = Vec3.atBottomCenterOf(anchorPos);
        this.villagerGroup = EncounterAllies.newGroupName();
        this.illagerGroup = EncounterAllies.newGroupName();
        spawnInitialPockets();
        applyInitialAggro();
    }

    @Override public ServerLevel level()  { return level; }
    @Override public Vec3 anchor()        { return anchor; }
    @Override public String eventId()     { return PatrolSkirmishEvent.ID; }
    @Override public boolean isFinished() { return finished; }

    @Override
    public void tick() {
        tickCount++;

        // Abort cleanly when both pockets failed to spawn anything — avoids
        // logging "combat ended" with 0/0 counts which is misleading.
        if (tickCount == 1 && villagerMobs.isEmpty() && illagerMobs.isEmpty()) {
            Constants.LOG.warn("[{}] aborted: no mobs spawned at ({}, {}, {})",
                    PatrolSkirmishEvent.ID, anchorPos.getX(), anchorPos.getY(), anchorPos.getZ());
            finishAndRelease();
            return;
        }

        // Poll participation BEFORE pruning dead mobs — vanilla keeps
        // lastHurtByPlayer set after die() until the death animation
        // expires, so we can still read it on a freshly-killed mob.
        // Reading after the removeIf would drop one-shot kills on the
        // last tick of combat.
        pollParticipation();

        // Drop dead/removed mobs after polling them.
        villagerMobs.removeIf(m -> !m.isAlive() || m.isRemoved());
        illagerMobs.removeIf(m -> !m.isAlive() || m.isRemoved());
        villagerCaravanMobs.removeIf(m -> !m.isAlive() || m.isRemoved());
        illagerCaravanMobs.removeIf(m -> !m.isAlive() || m.isRemoved());

        // Global timeout always wins.
        if (tickCount >= EncountersConfig.get().patrolSkirmishTimeoutTicks) {
            Constants.LOG.info("[{}] timed out at ({}, {}, {})",
                    PatrolSkirmishEvent.ID, anchorPos.getX(), anchorPos.getY(), anchorPos.getZ());
            finishAndRelease();
            return;
        }

        if (rewardState == RewardState.NOT_STARTED) {
            if (isCombatOver()) {
                startRewardPhase();
                return;
            }
            if (tickCount % LEASH_INTERVAL_TICKS == 0) {
                enforceLeash(villagerMobs);
                enforceLeash(illagerMobs);
            }
        } else {
            tickRewardPhase();
        }
    }

    @Override
    public void onAbandoned() {
        Constants.LOG.info("[{}] abandoned at ({}, {}, {})",
                PatrolSkirmishEvent.ID, anchorPos.getX(), anchorPos.getY(), anchorPos.getZ());
        finishAndRelease();
    }

    // ---------- Spawn ----------

    private void spawnInitialPockets() {
        EncountersConfig config = EncountersConfig.get();
        int perSide = config.patrolSkirmishMobsPerSide;
        MobRoster villagerRoster = PatrolSkirmishEvent.villagerRoster();
        MobRoster illagerRoster = PatrolSkirmishEvent.illagerRoster();
        if (villagerRoster.isEmpty() || illagerRoster.isEmpty()) return;

        RandomSource rng = level.getRandom();
        double angle = rng.nextDouble() * Math.PI * 2;
        double dirX = Math.cos(angle);
        double dirZ = Math.sin(angle);
        Vec3 villagerCentre = new Vec3(anchor.x - dirX * POCKET_OFFSET, anchor.y, anchor.z - dirZ * POCKET_OFFSET);
        Vec3 illagerCentre  = new Vec3(anchor.x + dirX * POCKET_OFFSET, anchor.y, anchor.z + dirZ * POCKET_OFFSET);
        Vec3 villagerAway = new Vec3(-dirX, 0, -dirZ);
        Vec3 illagerAway  = new Vec3( dirX, 0,  dirZ);

        spawnPocket(villagerRoster, villagerGroup, villagerCentre, perSide, villagerMobs, rng);
        spawnPocket(illagerRoster,  illagerGroup,  illagerCentre,  perSide, illagerMobs,  rng);

        CaravanSpawner.maybeSpawnCaravan(level, CaravanSpawner.Faction.VILLAGER, villagerCentre,
                villagerAway, villagerGroup, villagerCaravanMobs, rng);
        CaravanSpawner.maybeSpawnCaravan(level, CaravanSpawner.Faction.ILLAGER, illagerCentre,
                illagerAway, illagerGroup, illagerCaravanMobs, rng);
    }

    private void spawnPocket(MobRoster roster, String groupName, Vec3 centre, int budget,
                             List<Mob> dest, RandomSource rng) {
        while (budget > 0) {
            Optional<ResolvedMob> picked = roster.pick(rng);
            if (picked.isEmpty()) break;
            ResolvedMob mob = picked.get();
            int cost = costFor(mob);

            double offX = (rng.nextDouble() - 0.5) * 2 * POCKET_SCATTER;
            double offZ = (rng.nextDouble() - 0.5) * 2 * POCKET_SCATTER;
            int x = (int) Math.floor(centre.x + offX);
            int z = (int) Math.floor(centre.z + offZ);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Vec3 spawnPos = new Vec3(x + 0.5, y, z + 0.5);

            Entity spawned = EncounterSpawner.spawn(level, mob, spawnPos, rng, PatrolSkirmishEvent.ID);
            if (spawned == null) continue;
            EncounterAllies.addToGroup(level, groupName, spawned);
            registerMember(dest, spawned, groupName);
            budget -= cost;
        }
    }

    /**
     * Spawn-budget cost of a roster entry. Entries carrying the SNBT tag
     * {@link #HIGH_COST_TAG} consume 2 slots in {@code mobsPerSide} instead
     * of 1, so strong combatants (e.g. Iron Golem, Ravager) don't stack a
     * numerical advantage on top of their power advantage. Tag-driven by
     * design — any user-added entry can be flagged through the config GUI
     * without code changes.
     */
    private static int costFor(ResolvedMob mob) {
        if (!mob.nbt().contains("Tags", Tag.TAG_LIST)) return 1;
        ListTag tags = mob.nbt().getList("Tags", Tag.TAG_STRING);
        for (int i = 0; i < tags.size(); i++) {
            if (HIGH_COST_TAG.equals(tags.getString(i))) return 2;
        }
        return 1;
    }

    /**
     * Recursively walks passengers and appends every mob to {@code dest} while
     * tagging it into the faction team. Mirrors {@code PortalInvasion}'s wave
     * registration so a Hoglin + Piglin Brute combo contributes two entries.
     */
    private void registerMember(List<Mob> dest, Entity entity, String groupName) {
        if (entity instanceof Mob mob) dest.add(mob);
        for (Entity p : entity.getPassengers()) {
            EncounterAllies.addToGroup(level, groupName, p);
            registerMember(dest, p, groupName);
        }
    }

    /**
     * Re-targets every spawned mob onto the nearest enemy-faction member so
     * the bilateral fight starts immediately. {@link EncounterSpawner#spawn}
     * already aggroed each mob on the nearest player (within 64 blocks) — our
     * override here wins because the enemy pocket sits ~10 blocks away while
     * the triggering player is typically 48-100 blocks out.
     */
    private void applyInitialAggro() {
        for (Mob v : villagerMobs) {
            Mob target = nearest(v.position(), illagerMobs);
            if (target != null) EncounterSpawner.applyAggro(v, target);
        }
        for (Mob i : illagerMobs) {
            Mob target = nearest(i.position(), villagerMobs);
            if (target != null) EncounterSpawner.applyAggro(i, target);
        }
    }

    // ---------- Combat lifecycle ----------

    private boolean isCombatOver() {
        if (villagerMobs.isEmpty() || illagerMobs.isEmpty()) return true;
        return allOutOfZone(villagerMobs) || allOutOfZone(illagerMobs);
    }

    private boolean allOutOfZone(List<Mob> mobs) {
        for (Mob m : mobs) {
            if (m.position().distanceToSqr(anchor) < OUT_OF_ZONE_SQR) return false;
        }
        return true;
    }

    private void enforceLeash(List<Mob> mobs) {
        for (Mob m : mobs) {
            if (m.isNoAi()) continue;
            if (m.getTarget() != null) continue;
            if (m.position().distanceToSqr(anchor) <= LEASH_RADIUS_SQR) continue;
            m.getNavigation().moveTo(anchor.x, anchor.y, anchor.z, LEASH_SPEED);
        }
    }

    // ---------- Reward state machine ----------

    private void pollParticipation() {
        if (playerParticipated) return;
        if (checkLastHurtByPlayer(villagerMobs)
                || checkLastHurtByPlayer(illagerMobs)
                || checkLastHurtByPlayer(villagerCaravanMobs)
                || checkLastHurtByPlayer(illagerCaravanMobs)) {
            playerParticipated = true;
            Constants.LOG.debug("[{}] participation flag set at tick {}",
                    PatrolSkirmishEvent.ID, tickCount);
        }
    }

    private static boolean checkLastHurtByPlayer(List<Mob> mobs) {
        for (Mob m : mobs) {
            // getKillCredit() returns the recent attacker, preferring the
            // last player to have hit within vanilla's memory window
            // (~100 ticks) over a later mob attacker.
            if (m.getKillCredit() instanceof Player) return true;
        }
        return false;
    }

    private void startRewardPhase() {
        Constants.LOG.info("[{}] combat ended at ({}, {}, {}) — villagers={}, illagers={}, participated={}",
                PatrolSkirmishEvent.ID, anchorPos.getX(), anchorPos.getY(), anchorPos.getZ(),
                villagerMobs.size(), illagerMobs.size(), playerParticipated);

        if (!playerParticipated || villagerMobs.isEmpty()) {
            rewardState = RewardState.EXPIRED;
            Constants.LOG.info("[{}] reward expired upfront: participated={}, villagerSurvivors={}",
                    PatrolSkirmishEvent.ID, playerParticipated, villagerMobs.size());
            finishAndRelease();
            return;
        }

        rewardState = RewardState.PENDING;
        rewardPendingTicks = 0;
        Constants.LOG.info("[{}] reward pending — waiting for eligible player", PatrolSkirmishEvent.ID);
    }

    private void tickRewardPhase() {
        rewardPendingTicks++;

        // Hard timeout on the reward path — stops the leader cinematic and the
        // tick from running indefinitely while we wait for an eligible player.
        int rewardMax = EncountersConfig.get().patrolSkirmishRewardWaitMaxTicks;
        if (rewardState == RewardState.PENDING && rewardPendingTicks >= rewardMax) {
            rewardState = RewardState.EXPIRED;
            Constants.LOG.info("[{}] reward expired: no eligible player within {} ticks",
                    PatrolSkirmishEvent.ID, rewardMax);
            finishAndRelease();
            return;
        }

        if (rewardState == RewardState.PENDING) {
            tryStartDelivery();
            return;
        }

        if (rewardState == RewardState.DELIVERING) {
            tickDelivering();
            return;
        }

        // Terminal states reached on a previous tick — clean up now.
        if (rewardState == RewardState.DELIVERED || rewardState == RewardState.EXPIRED) {
            finishAndRelease();
        }
    }

    private void tryStartDelivery() {
        Player target = findEligiblePlayer();
        if (target == null) return; // wait until one becomes eligible

        Mob leader = pickLeader();
        if (leader == null) {
            rewardState = RewardState.EXPIRED;
            Constants.LOG.info("[{}] reward expired: no surviving villager-side leader",
                    PatrolSkirmishEvent.ID);
            finishAndRelease();
            return;
        }

        currentLeader = leader;
        targetedPlayer = target;
        leaderTravelTicks = 0;
        rewardState = RewardState.DELIVERING;
        Constants.LOG.info("[{}] reward delivering — leader {} → player {}",
                PatrolSkirmishEvent.ID, leader.getType().getDescriptionId(),
                target.getName().getString());
    }

    private void tickDelivering() {
        leaderTravelTicks++;

        // Validate leader still alive — cascade otherwise.
        if (currentLeader == null || !currentLeader.isAlive() || currentLeader.isRemoved()) {
            currentLeader = pickLeader();
            if (currentLeader == null) {
                rewardState = RewardState.EXPIRED;
                Constants.LOG.info("[{}] reward expired: leader cascade exhausted",
                        PatrolSkirmishEvent.ID);
                finishAndRelease();
                return;
            }
            leaderTravelTicks = 0;
        }

        // Validate target player still eligible — re-acquire otherwise.
        if (targetedPlayer == null || !targetedPlayer.isAlive() || targetedPlayer.isSpectator()
                || isHostileToVillagerSide(targetedPlayer)
                || targetedPlayer.position().distanceToSqr(anchor)
                   > REWARD_SEARCH_RADIUS * REWARD_SEARCH_RADIUS) {
            targetedPlayer = findEligiblePlayer();
            if (targetedPlayer == null) {
                // No eligible player right now — fall back to PENDING and
                // wait for one. The reward-wait timeout still applies.
                rewardState = RewardState.PENDING;
                Constants.LOG.debug("[{}] delivery target lost — back to PENDING",
                        PatrolSkirmishEvent.ID);
                return;
            }
            leaderTravelTicks = 0;
        }

        // Periodic cosmetics + nav order.
        if (leaderTravelTicks % LEADER_TICK_INTERVAL == 0) {
            spawnHappyParticles(currentLeader);
            currentLeader.getNavigation().moveTo(
                    targetedPlayer.getX(), targetedPlayer.getY(), targetedPlayer.getZ(),
                    LEADER_SPEED);
        }

        // Close enough — drop the chest in front of the leader.
        if (currentLeader.distanceToSqr(targetedPlayer) <= DELIVERY_DIST_SQR) {
            placeRewardChest(true);
            return;
        }

        // Watchdog — leader can't get to player, drop in place.
        if (leaderTravelTicks >= LEADER_TRAVEL_TIMEOUT_TICKS) {
            Constants.LOG.info("[{}] delivery watchdog: leader couldn't reach player in {} ticks — fallback drop",
                    PatrolSkirmishEvent.ID, LEADER_TRAVEL_TIMEOUT_TICKS);
            placeRewardChest(false);
        }
    }

    /**
     * True iff at least one alive villager-side mob currently targets {@code
     * player}. Encodes "player is hostile to the village faction" without a
     * custom rep tracker — vanilla {@code DefendVillageTargetGoal} (golems)
     * and {@code DefendVillageGuardGoal} (Guard Villagers) already set the
     * target when reputation drops or a villager is struck.
     */
    private boolean isHostileToVillagerSide(Player player) {
        return targetsMatch(villagerMobs, player) || targetsMatch(villagerCaravanMobs, player);
    }

    private static boolean targetsMatch(List<Mob> mobs, Player player) {
        for (Mob m : mobs) {
            if (!m.isAlive()) continue;
            if (m.getTarget() == player) return true;
        }
        return false;
    }

    private Player findEligiblePlayer() {
        Player closest = null;
        double minDistSqr = Double.MAX_VALUE;
        double maxSqr = REWARD_SEARCH_RADIUS * REWARD_SEARCH_RADIUS;
        for (Player p : level.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            double d = p.position().distanceToSqr(anchor);
            if (d > maxSqr) continue;
            if (isHostileToVillagerSide(p)) continue;
            if (d < minDistSqr) {
                minDistSqr = d;
                closest = p;
            }
        }
        return closest;
    }

    /**
     * Picks the cascade leader: villager (caravan carrier) → Guard → Iron
     * Golem → any surviving villager-side mob. Returns {@code null} only if
     * every villager-side list is empty.
     */
    private Mob pickLeader() {
        for (Mob m : villagerCaravanMobs) {
            if (m.isAlive() && m instanceof Villager) return m;
        }
        for (Mob m : villagerMobs) {
            if (m.isAlive() && isGuard(m)) return m;
        }
        for (Mob m : villagerMobs) {
            if (m.isAlive() && m instanceof IronGolem) return m;
        }
        for (Mob m : villagerMobs) {
            if (m.isAlive()) return m;
        }
        for (Mob m : villagerCaravanMobs) {
            if (m.isAlive()) return m;
        }
        return null;
    }

    private static boolean isGuard(Mob m) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(m.getType());
        return key != null && "guard".equals(key.getPath());
    }

    // ---------- Reward delivery ----------

    private void spawnHappyParticles(Entity entity) {
        double r = Math.max(0.5, entity.getBbWidth() * 0.6);
        double yTop = entity.getY() + entity.getBbHeight() * 0.9;
        for (int i = 0; i < 6; i++) {
            double dx = (level.getRandom().nextDouble() - 0.5) * 2 * r;
            double dz = (level.getRandom().nextDouble() - 0.5) * 2 * r;
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    entity.getX() + dx, yTop, entity.getZ() + dz,
                    1, 0, 0, 0, 0);
        }
    }

    /**
     * Places the reward chest. When {@code inFrontOfLeader} is true the chest
     * lands one block ahead of the leader along the leader→player axis;
     * otherwise it lands at the leader's current ground level (used by the
     * travel watchdog when the player is unreachable).
     */
    private void placeRewardChest(boolean inFrontOfLeader) {
        Vec3 leaderPos = currentLeader.position();
        int targetX;
        int targetZ;
        if (inFrontOfLeader && targetedPlayer != null) {
            Vec3 toPlayer = targetedPlayer.position().subtract(leaderPos);
            double horiz = Math.sqrt(toPlayer.x * toPlayer.x + toPlayer.z * toPlayer.z);
            if (horiz < 1.0e-4) {
                targetX = (int) Math.floor(leaderPos.x);
                targetZ = (int) Math.floor(leaderPos.z);
            } else {
                targetX = (int) Math.floor(leaderPos.x + (toPlayer.x / horiz) * 1.5);
                targetZ = (int) Math.floor(leaderPos.z + (toPlayer.z / horiz) * 1.5);
            }
        } else {
            targetX = (int) Math.floor(leaderPos.x);
            targetZ = (int) Math.floor(leaderPos.z);
        }
        int targetY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);
        BlockPos placePos = new BlockPos(targetX, targetY, targetZ);

        // If the target slot is occupied (unlikely after MOTION_BLOCKING_NO_LEAVES
        // but possible with vegetation), nudge upward once.
        if (!level.getBlockState(placePos).canBeReplaced()) {
            placePos = placePos.above();
        }
        if (!level.getBlockState(placePos).canBeReplaced()) {
            Constants.LOG.warn("[{}] reward chest could not be placed near ({}, {}, {}) — slot occupied",
                    PatrolSkirmishEvent.ID, targetX, targetY, targetZ);
            rewardState = RewardState.EXPIRED;
            finishAndRelease();
            return;
        }

        // Compute chest facing so the opening faces the targeted player when
        // possible. Vanilla ChestBlock.FACING is the direction the chest fronts,
        // so we point it toward the player.
        Direction facing = Direction.NORTH;
        if (targetedPlayer != null) {
            double playerDx = targetedPlayer.getX() - (placePos.getX() + 0.5);
            double playerDz = targetedPlayer.getZ() - (placePos.getZ() + 0.5);
            if (Math.abs(playerDx) > 1.0e-4 || Math.abs(playerDz) > 1.0e-4) {
                Direction nearest = Direction.getNearest(playerDx, 0, playerDz);
                if (nearest.getAxis() != Direction.Axis.Y) facing = nearest;
            }
        }
        BlockState chestState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
        if (!level.setBlock(placePos, chestState, 3)) {
            Constants.LOG.warn("[{}] setBlock returned false at ({}, {}, {}) — reward lost",
                    PatrolSkirmishEvent.ID, placePos.getX(), placePos.getY(), placePos.getZ());
            rewardState = RewardState.EXPIRED;
            finishAndRelease();
            return;
        }
        BlockEntity be = level.getBlockEntity(placePos);
        if (be instanceof RandomizableContainer container) {
            container.setLootTable(PatrolSkirmishEvent.REWARD_LOOT_TABLE);
            container.setLootTableSeed(level.getRandom().nextLong());
        } else {
            Constants.LOG.warn("[{}] reward chest placed but BlockEntity is not a RandomizableContainer ({})",
                    PatrolSkirmishEvent.ID, be);
        }

        level.playSound(null, placePos,
                SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.7f, 1.2f);
        spawnHappyParticles(currentLeader);

        Constants.LOG.info("[{}] reward chest placed at ({}, {}, {}) by leader {}",
                PatrolSkirmishEvent.ID,
                placePos.getX(), placePos.getY(), placePos.getZ(),
                currentLeader.getType().getDescriptionId());

        rewardState = RewardState.DELIVERED;
        finishAndRelease();
    }

    // ---------- Misc helpers ----------

    private Mob nearest(Vec3 from, List<Mob> candidates) {
        Mob closest = null;
        double minDist = Double.MAX_VALUE;
        for (Mob c : candidates) {
            double d = c.position().distanceToSqr(from);
            if (d < minDist) {
                minDist = d;
                closest = c;
            }
        }
        return closest;
    }

    private void finishAndRelease() {
        if (finished) return;
        finished = true;
        EncounterAllies.disbandGroup(level, villagerGroup);
        EncounterAllies.disbandGroup(level, illagerGroup);
        PatrolSkirmishEvent.releaseSkirmish(this);
    }
}
