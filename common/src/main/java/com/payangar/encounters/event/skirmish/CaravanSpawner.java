package com.payangar.encounters.event.skirmish;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.event.ally.EncounterAllies;
import com.payangar.encounters.mixin.AbstractHorseAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Spawns the optional caravan (carrier + chest-bearing pack animal) for a
 * patrol skirmish faction. The carrier is a vanilla villager (villager side)
 * or pillager (illager side); the pack animal is randomly a llama or a
 * donkey, with its chest pre-filled from the faction's loot table.
 *
 * <p>The chest is pre-populated at spawn rather than via a runtime
 * {@code LootTable} reference, because {@link AbstractChestedHorse} does
 * not honour entity-borne loot tables. Vanilla drops the chest contents
 * on death, so a player killing the animal automatically picks up the
 * items; opening the chest of a living animal also exposes them.</p>
 *
 * <p>Lootr does not hook entity-borne chests (it targets block containers
 * and minecarts), so the caravan inventory stays first-come-first-served
 * between players. This is intentional — the per-player reward instance
 * lives at the cinematic-reward chest placed by the leader, not on the
 * caravan.</p>
 */
public final class CaravanSpawner {

    /**
     * Distance behind the pocket centre at which the caravan spawns.
     *
     * <p>Combined with {@code PatrolSkirmish.POCKET_OFFSET=8} and
     * {@link #CARAVAN_SCATTER}, the caravan lands up to ~15.5 blocks from the
     * anchor — beyond the scanner's {@code CLEARING_RADIUS=10} verification.
     * The caravan therefore may spawn on slightly uneven terrain (a step up,
     * a small dip). Accepted tradeoff: enforcing strict clearing over a
     * 32×32 area would make valid sites very rare. {@code level.getHeight}
     * + {@code setPersistenceRequired} keep the spawn at ground level and
     * stable; vanilla AI handles the rest.</p>
     */
    private static final double REAR_GUARD_OFFSET = 6.0;
    /** Random scatter applied to the caravan position. */
    private static final double CARAVAN_SCATTER = 1.5;
    /**
     * First slot of the pack-animal inventory that we treat as "chest contents".
     * Vanilla {@link AbstractChestedHorse} layout: slot 0 = saddle, slot 1 =
     * body armor, slots 2+ = chest items.
     */
    private static final int CHEST_FIRST_SLOT = 2;

    public static final ResourceKey<LootTable> VILLAGE_SUPPLIES = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "caravan/village_supplies"));
    public static final ResourceKey<LootTable> ILLAGER_SUPPLIES = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "caravan/illager_supplies"));

    enum Faction {
        VILLAGER("minecraft:villager", VILLAGE_SUPPLIES),
        ILLAGER("minecraft:pillager", ILLAGER_SUPPLIES);

        final ResourceLocation carrierId;
        final ResourceKey<LootTable> lootTable;

        Faction(String carrierId, ResourceKey<LootTable> lootTable) {
            this.carrierId = ResourceLocation.parse(carrierId);
            this.lootTable = lootTable;
        }
    }

    private CaravanSpawner() {}

    /**
     * Rolls {@link EncountersConfig#patrolSkirmishCaravanChancePerSide} and,
     * on success, spawns a carrier + chest pack animal at the rear of the
     * faction's pocket. All spawned mobs are tagged into {@code groupName}
     * and appended to {@code dest}.
     *
     * @param awayDirection unit vector pointing from the anchor toward the
     *                      faction (i.e. away from the front line)
     * @return number of mobs spawned (0 if the roll failed or both spawns failed)
     */
    public static int maybeSpawnCaravan(ServerLevel level, Faction faction, Vec3 pocketCentre,
                                        Vec3 awayDirection, String groupName, List<Mob> dest,
                                        RandomSource rng) {
        EncountersConfig config = EncountersConfig.get();
        if (rng.nextDouble() >= config.patrolSkirmishCaravanChancePerSide) return 0;

        Vec3 caravanCentre = pocketCentre.add(
                awayDirection.x * REAR_GUARD_OFFSET, 0, awayDirection.z * REAR_GUARD_OFFSET);
        double offX = (rng.nextDouble() - 0.5) * 2 * CARAVAN_SCATTER;
        double offZ = (rng.nextDouble() - 0.5) * 2 * CARAVAN_SCATTER;
        int x = (int) Math.floor(caravanCentre.x + offX);
        int z = (int) Math.floor(caravanCentre.z + offZ);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        Vec3 spawnPos = new Vec3(x + 0.5, y, z + 0.5);

        int spawned = 0;
        Mob carrier = spawnCarrier(level, faction, spawnPos, rng);
        if (carrier != null) {
            EncounterAllies.addToGroup(level, groupName, carrier);
            dest.add(carrier);
            spawned++;
        }

        Mob animal = spawnPackAnimal(level, faction, spawnPos.add(1.0, 0, 0), rng);
        if (animal != null) {
            // Tag the pack animal so ReputationHook can detect interactions.
            animal.addTag(faction == Faction.VILLAGER
                    ? ReputationHook.CARAVAN_TAG_VILLAGER
                    : ReputationHook.CARAVAN_TAG_ILLAGER);
            EncounterAllies.addToGroup(level, groupName, animal);
            dest.add(animal);
            spawned++;
            if (carrier != null) {
                animal.setLeashedTo(carrier, true);
            }
        }

        if (spawned > 0) {
            Constants.LOG.info("[{}] caravan ({}) spawned at ({}, {}, {}) — {} mob(s)",
                    PatrolSkirmishEvent.ID, faction.name().toLowerCase(), x, y, z, spawned);
        }
        return spawned;
    }

    private static Mob spawnCarrier(ServerLevel level, Faction faction, Vec3 pos, RandomSource rng) {
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(faction.carrierId);
        if (type.isEmpty()) {
            Constants.LOG.warn("[{}] caravan: carrier entity '{}' not registered",
                    PatrolSkirmishEvent.ID, faction.carrierId);
            return null;
        }
        Entity entity = type.get().create(level);
        if (!(entity instanceof Mob mob)) return null;
        mob.moveTo(pos.x, pos.y, pos.z, rng.nextFloat() * 360f, 0f);
        mob.setPersistenceRequired();
        if (!level.tryAddFreshEntityWithPassengers(mob)) return null;
        return mob;
    }

    private static Mob spawnPackAnimal(ServerLevel level, Faction faction, Vec3 pos, RandomSource rng) {
        ResourceLocation animalId = rng.nextBoolean()
                ? ResourceLocation.parse("minecraft:llama")
                : ResourceLocation.parse("minecraft:donkey");
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(animalId);
        if (type.isEmpty()) return null;
        Entity entity = type.get().create(level);
        if (!(entity instanceof AbstractChestedHorse chested)) return null;
        chested.setChest(true);
        // Tame with no owner — required for vanilla mount + chest-open
        // interactions to be allowed. With persistence already set, the
        // animal stays in place after the skirmish ends.
        chested.setTamed(true);
        chested.moveTo(pos.x, pos.y, pos.z, rng.nextFloat() * 360f, 0f);
        chested.setPersistenceRequired();
        if (!level.tryAddFreshEntityWithPassengers(chested)) return null;
        // Populate inventory AFTER spawn — getInventory() is only valid once the
        // synced-data chest flag has propagated and the container is created.
        fillChest(level, chested, faction.lootTable, pos);
        return chested;
    }

    /**
     * Generates loot items from the faction's loot table and inserts them into
     * the pack animal's chest portion of its inventory (skipping the saddle
     * and body-armor slots). Vanilla {@link AbstractChestedHorse} drops every
     * chest item on death, so killing the animal yields the same loot a player
     * would see by opening the chest alive.
     */
    private static void fillChest(ServerLevel level, AbstractChestedHorse animal,
                                  ResourceKey<LootTable> tableKey, Vec3 origin) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(tableKey);
        if (table == LootTable.EMPTY) {
            Constants.LOG.info("[{}] caravan loot table {} resolved to EMPTY — chest left empty",
                    PatrolSkirmishEvent.ID, tableKey.location());
            return;
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, origin)
                .create(LootContextParamSets.CHEST);

        Container inventory = ((AbstractHorseAccessor) animal).encounters$getInventory();
        int size = inventory.getContainerSize();
        int slot = CHEST_FIRST_SLOT;
        for (ItemStack stack : table.getRandomItems(params)) {
            if (slot >= size) break;
            if (stack.isEmpty()) continue;
            inventory.setItem(slot++, stack);
        }
    }
}
