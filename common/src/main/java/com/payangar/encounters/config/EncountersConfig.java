package com.payangar.encounters.config;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.gui.WeightedMobListFactory;
import com.payangar.encounters.event.EncounterRegistry;
import com.payangar.encounters.platform.Services;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.DoubleSlider;
import dev.isxander.yacl3.config.v2.api.autogen.IntSlider;
import dev.isxander.yacl3.config.v2.api.autogen.ListGroup;
import dev.isxander.yacl3.config.v2.api.autogen.TickBox;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class EncountersConfig {

    public static final String CATEGORY_GENERAL = "general";
    public static final String CATEGORY_LIGHTNING = "lightning_overcharge";
    public static final String CATEGORY_PORTAL_INVASION = "nether_portal_invasion";
    public static final String CATEGORY_PATROL_SKIRMISH = "patrol_skirmish";

    private static final ConfigClassHandler<EncountersConfig> HANDLER = ConfigClassHandler
            .createBuilder(EncountersConfig.class)
            .id(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(Services.PLATFORM.getConfigDir().resolve(Constants.MOD_ID + ".json5"))
                    .setJson5(true)
                    .build())
            .build();

    // ===== General =====

    @SerialEntry(comment = "Whether mobs spawned by any encounter event can drop their equipment when killed.")
    @AutoGen(category = CATEGORY_GENERAL)
    @TickBox
    public boolean mobsDropEquipment = false;

    // ===== Lightning Overcharge =====

    @SerialEntry(comment = "Master toggle for the lightning_overcharge event")
    @AutoGen(category = CATEGORY_LIGHTNING)
    @TickBox
    public boolean lightningOverchargeEnabled = true;

    @SerialEntry(comment = "Probability that a natural lightning bolt during a storm triggers the event")
    @AutoGen(category = CATEGORY_LIGHTNING)
    @DoubleSlider(min = 0.0, max = 1.0, step = 0.01)
    public double lightningOverchargeChance = 0.05;

    @SerialEntry(comment = "Minimum number of mobs spawned by the event (inclusive)")
    @AutoGen(category = CATEGORY_LIGHTNING)
    @IntSlider(min = 1, max = 20, step = 1)
    public int lightningOverchargeGroupMin = 2;

    @SerialEntry(comment = "Maximum number of mobs spawned by the event (inclusive)")
    @AutoGen(category = CATEGORY_LIGHTNING)
    @IntSlider(min = 1, max = 20, step = 1)
    public int lightningOverchargeGroupMax = 5;

    @SerialEntry(comment = "When enabled, the first mob of a group acts as leader and the others " +
            "regroup around it whenever they stray too far. Followers in active combat are left alone.")
    @AutoGen(category = CATEGORY_LIGHTNING)
    @TickBox
    public boolean lightningOverchargeGroupCohesionEnabled = true;

    @SerialEntry(comment = "Distance (in blocks) beyond which a follower is pulled back toward its leader")
    @AutoGen(category = CATEGORY_LIGHTNING)
    @IntSlider(min = 4, max = 32, step = 1)
    public int lightningOverchargeGroupCohesionRadius = 12;

    @SerialEntry(comment = "Weighted mob pool. Each entry: { id, weight, nbt?, label? }. " +
            "'nbt' is SNBT identical to the /summon command. Editable from the in-game config GUI.")
    @AutoGen(category = CATEGORY_LIGHTNING)
    @ListGroup(
            valueFactory = WeightedMobListFactory.class,
            controllerFactory = WeightedMobListFactory.class,
            addEntriesToBottom = true
    )
    public List<WeightedMob> lightningOverchargeMobs = defaultLightningMobs();

    // ===== Nether Portal Invasion =====

    @SerialEntry(comment = "Master toggle for the nether_portal_invasion event")
    @AutoGen(category = CATEGORY_PORTAL_INVASION)
    @TickBox
    public boolean netherPortalInvasionEnabled = true;

    @SerialEntry(comment = "How often (in server ticks) the mod scans loaded chunks for active nether portals")
    @AutoGen(category = CATEGORY_PORTAL_INVASION)
    @IntSlider(min = 20, max = 1200, step = 20)
    public int netherPortalInvasionScanIntervalTicks = 100;

    @SerialEntry(comment = "Cooldown (in ticks) after an invasion ends before the same portal can trigger again")
    @AutoGen(category = CATEGORY_PORTAL_INVASION)
    @IntSlider(min = 1200, max = 72000, step = 1200)
    public int netherPortalInvasionPortalCooldownTicks = 12000;

    @SerialEntry(comment = "Probability (per scan) that an eligible portal triggers an invasion")
    @AutoGen(category = CATEGORY_PORTAL_INVASION)
    @DoubleSlider(min = 0.0, max = 1.0, step = 0.01)
    public double netherPortalInvasionTriggerChance = 0.05;

    @SerialEntry(comment = "Minimum number of waves per invasion (inclusive)")
    @AutoGen(category = CATEGORY_PORTAL_INVASION)
    @IntSlider(min = 1, max = 12, step = 1)
    public int netherPortalInvasionMinWaves = 4;

    @SerialEntry(comment = "Maximum number of waves per invasion (inclusive)")
    @AutoGen(category = CATEGORY_PORTAL_INVASION)
    @IntSlider(min = 1, max = 12, step = 1)
    public int netherPortalInvasionMaxWaves = 6;

    @SerialEntry(comment = "Number of mobs in the first wave")
    @AutoGen(category = CATEGORY_PORTAL_INVASION)
    @IntSlider(min = 1, max = 10, step = 1)
    public int netherPortalInvasionFirstWaveSize = 2;

    @SerialEntry(comment = "Additional mobs added per wave (linear scaling: wave_n = first + (n-1) * step)")
    @AutoGen(category = CATEGORY_PORTAL_INVASION)
    @IntSlider(min = 0, max = 5, step = 1)
    public int netherPortalInvasionWaveSizeStep = 1;

    @SerialEntry(comment = "When enabled, the first mob of each wave acts as leader. " +
            "Other wave members regroup around it whenever they stray too far. " +
            "Followers in active combat are left alone, and mounted mobs' passengers are skipped.")
    @AutoGen(category = CATEGORY_PORTAL_INVASION)
    @TickBox
    public boolean netherPortalInvasionGroupCohesionEnabled = true;

    @SerialEntry(comment = "Distance (in blocks) beyond which a wave member is pulled back toward its leader")
    @AutoGen(category = CATEGORY_PORTAL_INVASION)
    @IntSlider(min = 4, max = 32, step = 1)
    public int netherPortalInvasionGroupCohesionRadius = 12;

    @SerialEntry(comment = "Weighted mob pool for the nether portal invasion. " +
            "Same { id, weight, nbt?, label? } format as the lightning event.")
    @AutoGen(category = CATEGORY_PORTAL_INVASION)
    @ListGroup(
            valueFactory = WeightedMobListFactory.class,
            controllerFactory = WeightedMobListFactory.class,
            addEntriesToBottom = true
    )
    public List<WeightedMob> netherPortalInvasionMobs = defaultPortalInvasionMobs();

    @SerialEntry(comment = "When enabled (and Iron's Spells 'n Spellbooks is loaded), the portal " +
            "periodically lobs magma bombs out of the gateway during the invasion. " +
            "Spell level scales +1 per wave, capped at 8. No effect if the mod is absent.")
    @AutoGen(category = CATEGORY_PORTAL_INVASION)
    @TickBox
    public boolean netherPortalInvasionMagmaBombEnabled = true;

    // ===== Patrol Skirmish =====

    @SerialEntry(comment = "Master toggle for the patrol_skirmish event")
    @AutoGen(category = CATEGORY_PATROL_SKIRMISH)
    @TickBox
    public boolean patrolSkirmishEnabled = true;

    @SerialEntry(comment = "How often (in server ticks) the scanner attempts to spawn a new patrol skirmish")
    @AutoGen(category = CATEGORY_PATROL_SKIRMISH)
    @IntSlider(min = 600, max = 12000, step = 200)
    public int patrolSkirmishScanIntervalTicks = 1200;

    @SerialEntry(comment = "Probability (per scan) that the scanner spawns a skirmish at an eligible site")
    @AutoGen(category = CATEGORY_PATROL_SKIRMISH)
    @DoubleSlider(min = 0.0, max = 1.0, step = 0.01)
    public double patrolSkirmishTriggerChance = 0.15;

    @SerialEntry(comment = "Maximum number of skirmishes that may run simultaneously across the level")
    @AutoGen(category = CATEGORY_PATROL_SKIRMISH)
    @IntSlider(min = 1, max = 8, step = 1)
    public int patrolSkirmishMaxConcurrent = 2;

    @SerialEntry(comment = "Minimum distance (in blocks) between two concurrent skirmishes")
    @AutoGen(category = CATEGORY_PATROL_SKIRMISH)
    @IntSlider(min = 64, max = 1024, step = 32)
    public int patrolSkirmishMinDistanceBetween = 256;

    @SerialEntry(comment = "Cooldown (in ticks) after any skirmish ends before a new one can spawn anywhere")
    @AutoGen(category = CATEGORY_PATROL_SKIRMISH)
    @IntSlider(min = 0, max = 72000, step = 600)
    public int patrolSkirmishCooldownTicks = 6000;

    @SerialEntry(comment = "Only spawn skirmishes during daytime")
    @AutoGen(category = CATEGORY_PATROL_SKIRMISH)
    @TickBox
    public boolean patrolSkirmishDayOnly = true;

    @SerialEntry(comment = "Maximum lifetime (in ticks) of a skirmish before forced cleanup")
    @AutoGen(category = CATEGORY_PATROL_SKIRMISH)
    @IntSlider(min = 6000, max = 72000, step = 1200)
    public int patrolSkirmishTimeoutTicks = 24000;

    @SerialEntry(comment = "Maximum wait (in ticks) before the reward cinematic gives up if no eligible player is reachable")
    @AutoGen(category = CATEGORY_PATROL_SKIRMISH)
    @IntSlider(min = 600, max = 12000, step = 200)
    public int patrolSkirmishRewardWaitMaxTicks = 2400;

    @SerialEntry(comment = "Number of mobs spawned per faction at skirmish start")
    @AutoGen(category = CATEGORY_PATROL_SKIRMISH)
    @IntSlider(min = 2, max = 12, step = 1)
    public int patrolSkirmishMobsPerSide = 5;

    @SerialEntry(comment = "Probability that a caravan (carrier + chest-bearing pack animal) spawns per faction")
    @AutoGen(category = CATEGORY_PATROL_SKIRMISH)
    @DoubleSlider(min = 0.0, max = 1.0, step = 0.05)
    public double patrolSkirmishCaravanChancePerSide = 0.65;

    @SerialEntry(comment = "Biomes where a skirmish may spawn. Add namespaced biome ids, e.g. minecraft:plains. " +
            "Editable only via the JSON5 file — no in-game GUI widget for this list.")
    public List<String> patrolSkirmishBiomes = defaultPatrolSkirmishBiomes();

    @SerialEntry(comment = "Weighted mob pool for the villager faction. Same { id, weight, nbt?, label? } format as other events.")
    @AutoGen(category = CATEGORY_PATROL_SKIRMISH)
    @ListGroup(
            valueFactory = WeightedMobListFactory.class,
            controllerFactory = WeightedMobListFactory.class,
            addEntriesToBottom = true
    )
    public List<WeightedMob> patrolSkirmishVillagerMobs = defaultPatrolSkirmishVillagerMobs();

    @SerialEntry(comment = "Weighted mob pool for the illager faction. Same { id, weight, nbt?, label? } format as other events.")
    @AutoGen(category = CATEGORY_PATROL_SKIRMISH)
    @ListGroup(
            valueFactory = WeightedMobListFactory.class,
            controllerFactory = WeightedMobListFactory.class,
            addEntriesToBottom = true
    )
    public List<WeightedMob> patrolSkirmishIllagerMobs = defaultPatrolSkirmishIllagerMobs();

    // ===== Access =====

    public static EncountersConfig get() {
        return HANDLER.instance();
    }

    public static ConfigClassHandler<EncountersConfig> handler() {
        return HANDLER;
    }

    public static void load() {
        HANDLER.load();
        sanitize(HANDLER.instance());
        EncounterRegistry.invalidateAll();
        Constants.LOG.info("Loaded {} config from {}.json5", Constants.MOD_NAME, Constants.MOD_ID);
    }

    public static void save() {
        sanitize(HANDLER.instance());
        HANDLER.save();
        EncounterRegistry.invalidateAll();
    }

    /**
     * Clamps every config field to a safe range on load. Direct edits to the
     * JSON5 file can sneak past YACL's GUI bounds — this is the last line of
     * defence before gameplay code reads the value. Each correction is logged
     * at WARN so admins can spot stale config files.
     */
    private static void sanitize(EncountersConfig config) {
        config.lightningOverchargeChance = clampDouble("lightningOverchargeChance",
                config.lightningOverchargeChance, 0.0, 1.0);
        config.lightningOverchargeGroupMin = clampInt("lightningOverchargeGroupMin",
                config.lightningOverchargeGroupMin, 1, Integer.MAX_VALUE);
        config.lightningOverchargeGroupMax = clampInt("lightningOverchargeGroupMax",
                config.lightningOverchargeGroupMax, config.lightningOverchargeGroupMin, Integer.MAX_VALUE);
        config.lightningOverchargeGroupCohesionRadius = clampInt("lightningOverchargeGroupCohesionRadius",
                config.lightningOverchargeGroupCohesionRadius, 1, Integer.MAX_VALUE);

        config.netherPortalInvasionTriggerChance = clampDouble("netherPortalInvasionTriggerChance",
                config.netherPortalInvasionTriggerChance, 0.0, 1.0);
        config.netherPortalInvasionMinWaves = clampInt("netherPortalInvasionMinWaves",
                config.netherPortalInvasionMinWaves, 1, Integer.MAX_VALUE);
        config.netherPortalInvasionMaxWaves = clampInt("netherPortalInvasionMaxWaves",
                config.netherPortalInvasionMaxWaves, config.netherPortalInvasionMinWaves, Integer.MAX_VALUE);
        config.netherPortalInvasionFirstWaveSize = clampInt("netherPortalInvasionFirstWaveSize",
                config.netherPortalInvasionFirstWaveSize, 1, Integer.MAX_VALUE);
        config.netherPortalInvasionWaveSizeStep = clampInt("netherPortalInvasionWaveSizeStep",
                config.netherPortalInvasionWaveSizeStep, 0, Integer.MAX_VALUE);
        config.netherPortalInvasionScanIntervalTicks = clampInt("netherPortalInvasionScanIntervalTicks",
                config.netherPortalInvasionScanIntervalTicks, 20, Integer.MAX_VALUE);
        config.netherPortalInvasionPortalCooldownTicks = clampInt("netherPortalInvasionPortalCooldownTicks",
                config.netherPortalInvasionPortalCooldownTicks, 1200, Integer.MAX_VALUE);
        config.netherPortalInvasionGroupCohesionRadius = clampInt("netherPortalInvasionGroupCohesionRadius",
                config.netherPortalInvasionGroupCohesionRadius, 1, Integer.MAX_VALUE);

        config.patrolSkirmishTriggerChance = clampDouble("patrolSkirmishTriggerChance",
                config.patrolSkirmishTriggerChance, 0.0, 1.0);
        config.patrolSkirmishCaravanChancePerSide = clampDouble("patrolSkirmishCaravanChancePerSide",
                config.patrolSkirmishCaravanChancePerSide, 0.0, 1.0);
        config.patrolSkirmishScanIntervalTicks = clampInt("patrolSkirmishScanIntervalTicks",
                config.patrolSkirmishScanIntervalTicks, 20, Integer.MAX_VALUE);
        config.patrolSkirmishMaxConcurrent = clampInt("patrolSkirmishMaxConcurrent",
                config.patrolSkirmishMaxConcurrent, 1, Integer.MAX_VALUE);
        config.patrolSkirmishMinDistanceBetween = clampInt("patrolSkirmishMinDistanceBetween",
                config.patrolSkirmishMinDistanceBetween, 0, Integer.MAX_VALUE);
        config.patrolSkirmishCooldownTicks = clampInt("patrolSkirmishCooldownTicks",
                config.patrolSkirmishCooldownTicks, 0, Integer.MAX_VALUE);
        config.patrolSkirmishTimeoutTicks = clampInt("patrolSkirmishTimeoutTicks",
                config.patrolSkirmishTimeoutTicks, 1200, Integer.MAX_VALUE);
        config.patrolSkirmishRewardWaitMaxTicks = clampInt("patrolSkirmishRewardWaitMaxTicks",
                config.patrolSkirmishRewardWaitMaxTicks, 200, Integer.MAX_VALUE);
        config.patrolSkirmishMobsPerSide = clampInt("patrolSkirmishMobsPerSide",
                config.patrolSkirmishMobsPerSide, 1, Integer.MAX_VALUE);
    }

    private static int clampInt(String name, int value, int min, int max) {
        int clamped = Math.max(min, Math.min(max, value));
        if (clamped != value) {
            Constants.LOG.warn("config field {} was {}, clamped to [{}, {}]", name, value, min, max);
        }
        return clamped;
    }

    private static double clampDouble(String name, double value, double min, double max) {
        double clamped = Math.max(min, Math.min(max, value));
        if (clamped != value) {
            Constants.LOG.warn("config field {} was {}, clamped to [{}, {}]", name, value, min, max);
        }
        return clamped;
    }

    private static List<String> defaultPatrolSkirmishBiomes() {
        List<String> list = new ArrayList<>();
        list.add("minecraft:plains");
        list.add("minecraft:sunflower_plains");
        list.add("minecraft:savanna");
        list.add("minecraft:savanna_plateau");
        list.add("minecraft:windswept_savanna");
        list.add("minecraft:taiga");
        list.add("minecraft:snowy_taiga");
        list.add("minecraft:forest");
        list.add("minecraft:flower_forest");
        list.add("minecraft:dark_forest");
        list.add("minecraft:birch_forest");
        list.add("minecraft:old_growth_birch_forest");
        list.add("minecraft:meadow");
        return list;
    }

    private static List<WeightedMob> defaultPatrolSkirmishVillagerMobs() {
        List<WeightedMob> list = new ArrayList<>();
        // Vanilla baseline — always available. Iron Golem already targets every
        // Raider subclass natively, so no extra goal injection is required.
        // The "encounters_high_cost" tag makes the golem consume two slots in
        // mobsPerSide (see PatrolSkirmish.HIGH_COST_TAG) — keeps the bilateral
        // combat balanced against the lighter Guard.
        list.add(new WeightedMob("minecraft:iron_golem", 30, SNBT_PATROL_IRON_GOLEM, "Iron Golem"));
        // Guard Villagers — filtered automatically by MobRoster.resolve when the
        // mod is absent. With it installed, guards carry the bulk of the villager
        // faction's combat output (sword + crossbow + shield, plus a defend-
        // village goal that aggroes on Raiders out of the box).
        list.add(new WeightedMob("guardvillagers:guard", 30, null, "Village Guard"));
        return list;
    }

    private static final String SNBT_PATROL_IRON_GOLEM = """
            {Tags:["encounters_high_cost"]}\
            """;

    private static List<WeightedMob> defaultPatrolSkirmishIllagerMobs() {
        List<WeightedMob> list = new ArrayList<>();
        // Vanilla illager faction. Ravager kept at a deliberately low weight —
        // it disrupts cohesion and pushes mob bounding boxes around, so a
        // single appearance per skirmish is rare on purpose.
        list.add(new WeightedMob("minecraft:pillager", 30, null, "Pillager"));
        list.add(new WeightedMob("minecraft:vindicator", 20, null, "Vindicator"));
        list.add(new WeightedMob("minecraft:witch", 8, null, "Witch"));
        list.add(new WeightedMob("minecraft:evoker", 5, null, "Evoker"));
        list.add(new WeightedMob("minecraft:ravager", 2, null, "Ravager"));
        return list;
    }

    private static List<WeightedMob> defaultPortalInvasionMobs() {
        List<WeightedMob> list = new ArrayList<>();
        // Vanilla nether faction. Crimson / charcoal / dark-gold dyed leather
        // plus golden, iron and chainmail accents define the army's silhouette,
        // while a handful of entries stay intentionally bare (Soldier, Marksman,
        // Berserker, Embercaller, Ashen Marauder, Sovereign) so an invasion is
        // not a uniform armoured wall. Wave rarity bias (see PortalInvasion
        // WEIGHT_BOOST_CAP) gradually surfaces the rarer elites and mounted
        // combos as waves progress. Hoglin appears only as a mount (CLAUDE.md
        // §15); standalone hoglin/zoglin/magma_cube remain excluded.
        list.add(new WeightedMob("minecraft:piglin", 40, SNBT_PIGLIN_SOLDIER, "Piglin Soldier"));
        list.add(new WeightedMob("minecraft:piglin", 25, SNBT_PIGLIN_MARKSMAN, "Piglin Marksman"));
        list.add(new WeightedMob("minecraft:piglin", 15, SNBT_PIGLIN_SHIELDBEARER, "Piglin Shieldbearer"));
        list.add(new WeightedMob("minecraft:wither_skeleton", 12, SNBT_ASHEN_MARAUDER, "Ashen Marauder"));
        list.add(new WeightedMob("minecraft:piglin", 10, SNBT_PIGLIN_PYROMANCER, "Piglin Pyromancer"));
        list.add(new WeightedMob("minecraft:blaze", 8, SNBT_EMBERCALLER, "Embercaller"));
        list.add(new WeightedMob("minecraft:piglin", 8, SNBT_PIGLIN_BERSERKER, "Piglin Berserker"));
        list.add(new WeightedMob("minecraft:piglin", 6, SNBT_PIGLIN_HEAVY_GUARD, "Piglin Heavy Guard"));
        list.add(new WeightedMob("minecraft:piglin_brute", 5, SNBT_IRONHIDE_BRUTE, "Ironhide Brute"));
        list.add(new WeightedMob("minecraft:wither_skeleton", 5, SNBT_CINDER_KNIGHT, "Cinder Knight"));
        list.add(new WeightedMob("minecraft:wither_skeleton", 4, SNBT_ASH_SENTINEL, "Ash Sentinel"));
        list.add(new WeightedMob("minecraft:zombified_piglin", 4, SNBT_ZOMBIFIED_PIGLIN_WANDERER, "Zombified Piglin Wanderer"));
        list.add(new WeightedMob("minecraft:piglin_brute", 3, SNBT_GOLDFORGED_BRUTE, "Goldforged Brute"));
        list.add(new WeightedMob("minecraft:hoglin", 3, SNBT_TUSKED_VANGUARD, "Tusked Vanguard"));
        list.add(new WeightedMob("minecraft:hoglin", 2, SNBT_CROSSBOW_OUTRIDER, "Crossbow Outrider"));
        list.add(new WeightedMob("minecraft:hoglin", 2, SNBT_IRON_CHARGER, "Iron Charger"));
        list.add(new WeightedMob("minecraft:ghast", 1, SNBT_INFERNO_SOVEREIGN, "Inferno Sovereign"));
        // Modded additions — auto-skipped per install by NbtModFilter when their
        // mod is absent (referenced item/effect namespace) or by MobRoster.resolve
        // when the entity type itself is missing from the registry (eternalnether,
        // alexsmobs entries). A single config covers any modpack permutation.
        // Modded weights are pitched against the vanilla scale (Soldier=40,
        // Marksman=25, Shieldbearer=15) so they remain visible when their mod is
        // loaded. Without any mod the entries are filtered and the vanilla
        // distribution stays unchanged.
        // EpicFight — exotic weapon roster: greatsword, spear, dual daggers.
        list.add(new WeightedMob("minecraft:piglin", 12, SNBT_PIGLIN_GREATSWORD_BEARER, "Piglin Greatsword Bearer"));
        list.add(new WeightedMob("minecraft:piglin", 10, SNBT_PIGLIN_SPEARBEARER, "Piglin Spearbearer"));
        list.add(new WeightedMob("minecraft:piglin", 8, SNBT_PIGLIN_DUAL_DAGGER, "Piglin Dual-Dagger"));
        list.add(new WeightedMob("minecraft:wither_skeleton", 6, SNBT_CINDER_REAVER, "Cinder Reaver"));
        // Iron's Spells — fire magic carriers.
        list.add(new WeightedMob("minecraft:piglin", 6, SNBT_MAGMAHEART_PIGLIN, "Magmaheart Piglin"));
        list.add(new WeightedMob("minecraft:wither_skeleton", 5, SNBT_DASHING_INCINERATOR, "Dashing Incinerator"));
        // Simply Swords — fire effect carriers.
        list.add(new WeightedMob("minecraft:piglin", 6, SNBT_WILDFIRE_PIGLIN, "Wildfire Piglin"));
        list.add(new WeightedMob("minecraft:wither_skeleton", 5, SNBT_SMOULDERING_MARAUDER, "Smouldering Marauder"));
        // Eternal Nether — native nether entities with their own AI/equipment.
        // NBT left null so each ships with its vanilla-style finalizeMobSpawn loadout.
        list.add(new WeightedMob("eternalnether:piglin_hunter", 15, null, "Piglin Hunter"));
        list.add(new WeightedMob("eternalnether:piglin_prisoner", 10, null, "Piglin Prisoner"));
        list.add(new WeightedMob("eternalnether:wither_skeleton_knight", 10, null, "Wither Skeleton Knight"));
        list.add(new WeightedMob("eternalnether:corpor", 6, null, "Corpor"));
        list.add(new WeightedMob("eternalnether:wraither", 5, null, "Wraither"));
        // Alex's Mobs — small/medium fire-immune flying threats.
        list.add(new WeightedMob("alexsmobs:soul_vulture", 5, null, "Soul Vulture"));
        list.add(new WeightedMob("alexsmobs:crimson_mosquito", 4, null, "Crimson Mosquito"));
        return list;
    }

    private static List<WeightedMob> defaultLightningMobs() {
        List<WeightedMob> list = new ArrayList<>();
        // Vanilla baseline — always active
        list.add(new WeightedMob("minecraft:skeleton", 10, SNBT_STORMCALLER, "Stormcaller"));
        list.add(new WeightedMob("minecraft:zombie", 8, SNBT_SOUL_DROWNED, "Soul-Drowned"));
        list.add(new WeightedMob("minecraft:stray", 5, SNBT_FROSTBOUND_ECHO, "Frostbound Echo"));
        list.add(new WeightedMob("minecraft:drowned", 4, SNBT_TEMPEST_WRAITH, "Tempest Wraith"));
        list.add(new WeightedMob("minecraft:wither_skeleton", 2, SNBT_SOUL_LIT_VANGUARD, "Soul-lit Vanguard"));
        // Modded additions — filtered at roster resolve when their mod is absent (see NbtModFilter)
        list.add(new WeightedMob("minecraft:wither_skeleton", 2, SNBT_STORMCROWN_VANGUARD, "Stormcrown Vanguard"));
        list.add(new WeightedMob("minecraft:skeleton", 2, SNBT_ECHO_FENCER, "Echo Fencer"));
        list.add(new WeightedMob("minecraft:drowned", 2, SNBT_SPEARBORN_DROWNED, "Spearborn Drowned"));
        list.add(new WeightedMob("minecraft:zombie", 2, SNBT_VOLTAIC_REVENANT, "Voltaic Revenant"));
        list.add(new WeightedMob("minecraft:stray", 2, SNBT_SCULK_ECHO_STALKER, "Sculk-Echo Stalker"));
        list.add(new WeightedMob("minecraft:skeleton", 2, SNBT_STORMBORN_MARKSMAN, "Stormborn Marksman"));
        return list;
    }

    // dyed_color 1912627 = #1D2D33, dark teal — sculk-aligned palette shared across the roster.

    private static final String SNBT_STORMCALLER = """
            {attributes:[{id:"minecraft:generic.scale",base:1.1d},{id:"minecraft:generic.max_health",base:28.0d}],Health:28.0f,HandItems:[{id:"minecraft:bow",count:1,components:{"minecraft:enchantments":{power:3,flame:1,punch:1}}},{}],ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1,components:{"minecraft:dyed_color":1912627,"minecraft:enchantments":{protection:2}}}],HandDropChances:[0f,0f],ArmorDropChances:[0f,0f,0f,0f]}\
            """;

    private static final String SNBT_SOUL_DROWNED = """
            {attributes:[{id:"minecraft:generic.scale",base:1.15d},{id:"minecraft:generic.max_health",base:40.0d},{id:"minecraft:generic.knockback_resistance",base:0.5d}],Health:40.0f,active_effects:[{id:"minecraft:strength",amplifier:0b,duration:-1,show_particles:0b}],HandItems:[{id:"minecraft:iron_sword",count:1,components:{"minecraft:enchantments":{sharpness:2,knockback:1}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":1912627,"minecraft:enchantments":{protection:2}}},{}],HandDropChances:[0f,0f],ArmorDropChances:[0f,0f,0f,0f]}\
            """;

    private static final String SNBT_FROSTBOUND_ECHO = """
            {attributes:[{id:"minecraft:generic.scale",base:1.1d},{id:"minecraft:generic.max_health",base:26.0d}],Health:26.0f,HandItems:[{id:"minecraft:bow",count:1,components:{"minecraft:enchantments":{power:2,punch:2}}},{}],HandDropChances:[0f,0f]}\
            """;

    private static final String SNBT_TEMPEST_WRAITH = """
            {attributes:[{id:"minecraft:generic.scale",base:1.1d},{id:"minecraft:generic.max_health",base:30.0d}],Health:30.0f,active_effects:[{id:"minecraft:conduit_power",amplifier:0b,duration:-1,show_particles:1b}],HandItems:[{id:"minecraft:trident",count:1,components:{"minecraft:enchantments":{impaling:4,loyalty:3}}},{}],HandDropChances:[0f,0f]}\
            """;

    private static final String SNBT_SOUL_LIT_VANGUARD = """
            {attributes:[{id:"minecraft:generic.scale",base:1.2d},{id:"minecraft:generic.max_health",base:45.0d}],Health:45.0f,active_effects:[{id:"minecraft:strength",amplifier:0b,duration:-1,show_particles:0b}],HandItems:[{id:"minecraft:stone_sword",count:1,components:{"minecraft:enchantments":{sharpness:3,fire_aspect:2}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":0,"minecraft:enchantments":{protection:3}}},{}],HandDropChances:[0f,0f],ArmorDropChances:[0f,0f,0f,0f]}\
            """;

    // ===== Modded additions =====
    // Filtered automatically by NbtModFilter when their referenced mod is absent.
    // No HandDropChances/ArmorDropChances boilerplate — the global mobsDropEquipment toggle handles it.

    // Requires: epicfight
    private static final String SNBT_STORMCROWN_VANGUARD = """
            {attributes:[{id:"minecraft:generic.scale",base:1.25d},{id:"minecraft:generic.max_health",base:50.0d}],Health:50.0f,active_effects:[{id:"minecraft:strength",amplifier:1b,duration:-1,show_particles:0b}],HandItems:[{id:"epicfight:netherite_greatsword",count:1,components:{"minecraft:enchantments":{sharpness:3}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":1912627,"minecraft:enchantments":{protection:3}}},{id:"minecraft:leather_helmet",count:1,components:{"minecraft:dyed_color":1912627,"minecraft:enchantments":{protection:2}}}]}\
            """;

    // Requires: epicfight
    private static final String SNBT_ECHO_FENCER = """
            {attributes:[{id:"minecraft:generic.movement_speed",base:0.28d},{id:"minecraft:generic.max_health",base:26.0d}],Health:26.0f,active_effects:[{id:"minecraft:speed",amplifier:1b,duration:-1,show_particles:0b}],HandItems:[{id:"epicfight:iron_dagger",count:1,components:{"minecraft:enchantments":{sharpness:2}}},{id:"epicfight:iron_dagger",count:1,components:{"minecraft:enchantments":{sharpness:1}}}],ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1,components:{"minecraft:dyed_color":1912627}}]}\
            """;

    // Requires: epicfight
    private static final String SNBT_SPEARBORN_DROWNED = """
            {attributes:[{id:"minecraft:generic.scale",base:1.1d},{id:"minecraft:generic.movement_speed",base:0.26d},{id:"minecraft:generic.max_health",base:30.0d}],Health:30.0f,active_effects:[{id:"minecraft:conduit_power",amplifier:0b,duration:-1,show_particles:1b},{id:"minecraft:speed",amplifier:0b,duration:-1,show_particles:0b}],HandItems:[{id:"epicfight:iron_spear",count:1,components:{"minecraft:enchantments":{sharpness:2}}},{}]}\
            """;

    // Requires: irons_spellbooks
    private static final String SNBT_VOLTAIC_REVENANT = """
            {attributes:[{id:"minecraft:generic.scale",base:1.1d},{id:"minecraft:generic.max_health",base:38.0d}],Health:38.0f,active_effects:[{id:"minecraft:strength",amplifier:0b,duration:-1,show_particles:0b},{id:"irons_spellbooks:charged",amplifier:1b,duration:-1,show_particles:1b},{id:"irons_spellbooks:volt_strike",amplifier:0b,duration:-1,show_particles:0b}],HandItems:[{id:"minecraft:iron_sword",count:1,components:{"minecraft:enchantments":{sharpness:2,knockback:1}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":1912627,"minecraft:enchantments":{protection:2}}},{}]}\
            """;

    // Requires: irons_spellbooks
    private static final String SNBT_SCULK_ECHO_STALKER = """
            {attributes:[{id:"minecraft:generic.scale",base:1.1d},{id:"minecraft:generic.max_health",base:28.0d}],Health:28.0f,active_effects:[{id:"minecraft:speed",amplifier:0b,duration:-1,show_particles:0b},{id:"irons_spellbooks:echoing_strikes",amplifier:1b,duration:-1,show_particles:0b},{id:"irons_spellbooks:hastened",amplifier:0b,duration:-1,show_particles:0b}],HandItems:[{id:"minecraft:bow",count:1,components:{"minecraft:enchantments":{power:2,punch:1}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":1912627,"minecraft:enchantments":{protection:2}}},{}]}\
            """;

    // Requires: simplyswords
    private static final String SNBT_STORMBORN_MARKSMAN = """
            {attributes:[{id:"minecraft:generic.scale",base:1.15d},{id:"minecraft:generic.max_health",base:30.0d}],Health:30.0f,active_effects:[{id:"minecraft:speed",amplifier:0b,duration:-1,show_particles:0b},{id:"simplyswords:storm",amplifier:1b,duration:-1,show_particles:1b}],HandItems:[{id:"minecraft:bow",count:1,components:{"minecraft:enchantments":{power:3,punch:2}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":1912627,"minecraft:enchantments":{protection:2}}},{}]}\
            """;

    // ===== Nether Portal Invasion SNBT =====
    // Piglin variants — IsImmuneToZombification stays true so they don't
    // morph mid-fight in the overworld (also enforced by EncounterSpawner
    // on the outer entity, but passenger NBT needs it explicitly).

    // Piglin entries opt into the banner-bearer pool via Tags:["encounters_banner_eligible"].
    // Removing the tag from a user-customised entry takes that mob out of the banner pool
    // without touching code.

    private static final String SNBT_PIGLIN_SOLDIER = """
            {Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.max_health",base:25.0d}],Health:25.0f,IsImmuneToZombification:1b,HandItems:[{id:"minecraft:golden_sword",count:1,components:{"minecraft:enchantments":{sharpness:2}}},{}]}\
            """;

    private static final String SNBT_PIGLIN_MARKSMAN = """
            {Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.max_health",base:25.0d}],Health:25.0f,IsImmuneToZombification:1b,HandItems:[{id:"minecraft:crossbow",count:1,components:{"minecraft:enchantments":{quick_charge:2}}},{}]}\
            """;

    private static final String SNBT_PIGLIN_SHIELDBEARER = """
            {Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.max_health",base:35.0d},{id:"minecraft:generic.knockback_resistance",base:0.5d}],Health:35.0f,IsImmuneToZombification:1b,HandItems:[{id:"minecraft:iron_sword",count:1,components:{"minecraft:enchantments":{sharpness:2}}},{id:"minecraft:shield",count:1}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":10497574,"minecraft:enchantments":{protection:1}}},{}]}\
            """;

    private static final String SNBT_ASHEN_MARAUDER = """
            {attributes:[{id:"minecraft:generic.max_health",base:25.0d}],Health:25.0f,HandItems:[{id:"minecraft:stone_sword",count:1},{}]}\
            """;

    private static final String SNBT_EMBERCALLER = """
            {attributes:[{id:"minecraft:generic.max_health",base:25.0d}],Health:25.0f}\
            """;

    private static final String SNBT_IRONHIDE_BRUTE = """
            {Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.scale",base:1.1d},{id:"minecraft:generic.max_health",base:60.0d},{id:"minecraft:generic.knockback_resistance",base:0.4d}],Health:60.0f,IsImmuneToZombification:1b,HandItems:[{id:"minecraft:iron_axe",count:1,components:{"minecraft:enchantments":{sharpness:3}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":1315860,"minecraft:enchantments":{protection:2}}},{id:"minecraft:iron_helmet",count:1,components:{"minecraft:enchantments":{protection:1}}}]}\
            """;

    // Mounted: hoglin (mount) + piglin brute rider with iron axe + KB1.
    // PiglinBruteAi has no StartHuntingHoglin task, so no antagonism loop.
    // Banner tag applies to the rider only — the hoglin mount carries no flag.
    private static final String SNBT_TUSKED_VANGUARD = """
            {attributes:[{id:"minecraft:generic.max_health",base:50.0d},{id:"minecraft:generic.knockback_resistance",base:0.6d}],Health:50.0f,IsImmuneToZombification:1b,Passengers:[{id:"minecraft:piglin_brute",Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.scale",base:1.1d},{id:"minecraft:generic.max_health",base:50.0d}],Health:50.0f,IsImmuneToZombification:1b,HandItems:[{id:"minecraft:iron_axe",count:1,components:{"minecraft:enchantments":{sharpness:2,knockback:1}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":10497574,"minecraft:enchantments":{protection:2}}},{}]}]}\
            """;

    // Mounted: hoglin (mount) + piglin crossbow rider. Regular Piglin's
    // StartHuntingHoglin task is suspended in RIDE activity, so no loop.
    private static final String SNBT_CROSSBOW_OUTRIDER = """
            {attributes:[{id:"minecraft:generic.max_health",base:45.0d},{id:"minecraft:generic.knockback_resistance",base:0.5d}],Health:45.0f,IsImmuneToZombification:1b,Passengers:[{id:"minecraft:piglin",Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.max_health",base:25.0d}],Health:25.0f,IsImmuneToZombification:1b,HandItems:[{id:"minecraft:crossbow",count:1,components:{"minecraft:enchantments":{quick_charge:2,multishot:1}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":10497574,"minecraft:enchantments":{protection:1}}},{}]}]}\
            """;

    private static final String SNBT_INFERNO_SOVEREIGN = """
            {attributes:[{id:"minecraft:generic.scale",base:1.1d},{id:"minecraft:generic.max_health",base:30.0d}],Health:30.0f}\
            """;

    // dyed_color palette (decimal): 10497574 = #A02E26 crimson red,
    // 1315860 = #141414 charcoal black, 7227919 = #6E4A0F dark gold.

    // Fire-imbued striker. Permanent fire_resistance shrugs off its own
    // fire_aspect splashback and incidental lava ticks during pursuit.
    private static final String SNBT_PIGLIN_PYROMANCER = """
            {Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.max_health",base:25.0d}],Health:25.0f,IsImmuneToZombification:1b,active_effects:[{id:"minecraft:fire_resistance",amplifier:0b,duration:-1,show_particles:0b}],HandItems:[{id:"minecraft:golden_sword",count:1,components:{"minecraft:enchantments":{sharpness:1,fire_aspect:2}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":7227919}},{}]}\
            """;

    // Glass-cannon: no armour, but strength + speed and an enchanted iron axe.
    // Banner-eligible so it can carry the army colours despite being unarmoured.
    private static final String SNBT_PIGLIN_BERSERKER = """
            {Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.max_health",base:25.0d}],Health:25.0f,IsImmuneToZombification:1b,active_effects:[{id:"minecraft:strength",amplifier:0b,duration:-1,show_particles:0b},{id:"minecraft:speed",amplifier:0b,duration:-1,show_particles:0b}],HandItems:[{id:"minecraft:iron_axe",count:1,components:{"minecraft:enchantments":{sharpness:3}}},{}]}\
            """;

    // Tank piglin: iron helmet + chestplate, KB resistance. Intentionally
    // NOT banner-eligible so the iron helmet stays as its visual signature
    // (banner-bearer roll would replace the head slot 30% of the time).
    private static final String SNBT_PIGLIN_HEAVY_GUARD = """
            {attributes:[{id:"minecraft:generic.max_health",base:40.0d},{id:"minecraft:generic.knockback_resistance",base:0.5d}],Health:40.0f,IsImmuneToZombification:1b,HandItems:[{id:"minecraft:iron_sword",count:1,components:{"minecraft:enchantments":{sharpness:2}}},{}],ArmorItems:[{},{},{id:"minecraft:iron_chestplate",count:1,components:{"minecraft:enchantments":{protection:2}}},{id:"minecraft:iron_helmet",count:1,components:{"minecraft:enchantments":{protection:1}}}]}\
            """;

    // Elite brute: full enchanted golden armour and a fire_aspect golden axe.
    // Slow-burn tank that pairs naturally with the Pyromancer thematically.
    private static final String SNBT_GOLDFORGED_BRUTE = """
            {Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.scale",base:1.1d},{id:"minecraft:generic.max_health",base:60.0d},{id:"minecraft:generic.knockback_resistance",base:0.5d}],Health:60.0f,IsImmuneToZombification:1b,HandItems:[{id:"minecraft:golden_axe",count:1,components:{"minecraft:enchantments":{sharpness:3,fire_aspect:1}}},{}],ArmorItems:[{id:"minecraft:golden_boots",count:1,components:{"minecraft:enchantments":{protection:2}}},{id:"minecraft:golden_leggings",count:1,components:{"minecraft:enchantments":{protection:2}}},{id:"minecraft:golden_chestplate",count:1,components:{"minecraft:enchantments":{protection:3}}},{id:"minecraft:golden_helmet",count:1,components:{"minecraft:enchantments":{protection:2}}}]}\
            """;

    // Heavy wither_skeleton with full chainmail and an iron sword.
    // Not banner-eligible — banners stay visually a piglin-faction thing.
    private static final String SNBT_CINDER_KNIGHT = """
            {attributes:[{id:"minecraft:generic.max_health",base:30.0d}],Health:30.0f,HandItems:[{id:"minecraft:iron_sword",count:1,components:{"minecraft:enchantments":{sharpness:2}}},{}],ArmorItems:[{id:"minecraft:chainmail_boots",count:1},{id:"minecraft:chainmail_leggings",count:1},{id:"minecraft:chainmail_chestplate",count:1,components:{"minecraft:enchantments":{protection:1}}},{id:"minecraft:chainmail_helmet",count:1}]}\
            """;

    // Skirmisher wither_skeleton: speed I, stone axe, light leather torso.
    private static final String SNBT_ASH_SENTINEL = """
            {attributes:[{id:"minecraft:generic.max_health",base:25.0d}],Health:25.0f,active_effects:[{id:"minecraft:speed",amplifier:0b,duration:-1,show_particles:0b}],HandItems:[{id:"minecraft:stone_axe",count:1,components:{"minecraft:enchantments":{sharpness:1}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":1315860}},{}]}\
            """;

    // Zombified piglin variant — universal force-aggro (CLAUDE.md §16) bypasses
    // its vanilla neutrality. Banner-eligible to fold it into the army identity.
    private static final String SNBT_ZOMBIFIED_PIGLIN_WANDERER = """
            {Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.max_health",base:25.0d}],Health:25.0f,HandItems:[{id:"minecraft:golden_sword",count:1,components:{"minecraft:enchantments":{sharpness:1}}},{}],ArmorItems:[{},{},{},{id:"minecraft:golden_helmet",count:1}]}\
            """;

    // Mounted: hoglin (mount) + wither_skeleton rider with iron sword and
    // chainmail. Wither-skel AI lacks any anti-hoglin task so the pair
    // doesn't loop. Rider untagged — not part of the banner pool.
    private static final String SNBT_IRON_CHARGER = """
            {attributes:[{id:"minecraft:generic.max_health",base:50.0d},{id:"minecraft:generic.knockback_resistance",base:0.5d}],Health:50.0f,IsImmuneToZombification:1b,Passengers:[{id:"minecraft:wither_skeleton",attributes:[{id:"minecraft:generic.max_health",base:30.0d}],Health:30.0f,HandItems:[{id:"minecraft:iron_sword",count:1,components:{"minecraft:enchantments":{sharpness:2}}},{}],ArmorItems:[{},{},{id:"minecraft:chainmail_chestplate",count:1,components:{"minecraft:enchantments":{protection:1}}},{}]}]}\
            """;

    // ===== Portal Invasion — Modded variants =====
    // Skipped automatically when their referenced mod is missing (see NbtModFilter).
    // Equipment drop chances are intentionally omitted — global mobsDropEquipment
    // toggle handles it. Wither-skeleton variants carry no banner tag by design
    // (banners stay a piglin-faction signature).

    // Requires: epicfight — heavy two-hander piglin. Leather chest dyed crimson
    // leaves the head slot free for the banner roll.
    private static final String SNBT_PIGLIN_GREATSWORD_BEARER = """
            {Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.scale",base:1.1d},{id:"minecraft:generic.max_health",base:35.0d},{id:"minecraft:generic.knockback_resistance",base:0.3d}],Health:35.0f,IsImmuneToZombification:1b,HandItems:[{id:"epicfight:netherite_greatsword",count:1,components:{"minecraft:enchantments":{sharpness:3}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":10497574,"minecraft:enchantments":{protection:2}}},{}]}\
            """;

    // Requires: epicfight — reach-weapon piglin, faster than baseline so the
    // spear's poke window stays oppressive.
    private static final String SNBT_PIGLIN_SPEARBEARER = """
            {Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.movement_speed",base:0.27d},{id:"minecraft:generic.max_health",base:28.0d}],Health:28.0f,IsImmuneToZombification:1b,HandItems:[{id:"epicfight:iron_spear",count:1,components:{"minecraft:enchantments":{sharpness:2}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":7227919}},{}]}\
            """;

    // Requires: epicfight — fast hit-and-run skirmisher with dual daggers and
    // permanent speed I. Glass cannon: no chest armour.
    private static final String SNBT_PIGLIN_DUAL_DAGGER = """
            {Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.movement_speed",base:0.3d},{id:"minecraft:generic.max_health",base:22.0d}],Health:22.0f,IsImmuneToZombification:1b,active_effects:[{id:"minecraft:speed",amplifier:0b,duration:-1,show_particles:0b}],HandItems:[{id:"epicfight:iron_dagger",count:1,components:{"minecraft:enchantments":{sharpness:2}}},{id:"epicfight:iron_dagger",count:1,components:{"minecraft:enchantments":{sharpness:1}}}]}\
            """;

    // Requires: epicfight — wither_skeleton heavyweight with netherite great-
    // sword, fire_aspect and chainmail. Strength I stacks on the wither-skel's
    // already lethal output.
    private static final String SNBT_CINDER_REAVER = """
            {attributes:[{id:"minecraft:generic.scale",base:1.15d},{id:"minecraft:generic.max_health",base:40.0d}],Health:40.0f,active_effects:[{id:"minecraft:strength",amplifier:0b,duration:-1,show_particles:0b}],HandItems:[{id:"epicfight:netherite_greatsword",count:1,components:{"minecraft:enchantments":{sharpness:3,fire_aspect:2}}},{}],ArmorItems:[{},{id:"minecraft:chainmail_leggings",count:1},{id:"minecraft:chainmail_chestplate",count:1,components:{"minecraft:enchantments":{protection:2}}},{}]}\
            """;

    // Requires: irons_spellbooks — fire-DoT aura piglin. fire_resistance keeps
    // it alive through its own immolate ticks and the inevitable splashback.
    private static final String SNBT_MAGMAHEART_PIGLIN = """
            {Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.max_health",base:30.0d}],Health:30.0f,IsImmuneToZombification:1b,active_effects:[{id:"minecraft:fire_resistance",amplifier:0b,duration:-1,show_particles:0b},{id:"irons_spellbooks:immolate",amplifier:0b,duration:-1,show_particles:1b}],HandItems:[{id:"minecraft:iron_sword",count:1,components:{"minecraft:enchantments":{sharpness:2,fire_aspect:1}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":7227919,"minecraft:enchantments":{protection:2}}},{}]}\
            """;

    // Requires: irons_spellbooks — wither_skel carrying burning_dash + strength.
    // Stone sword with fire_aspect II for synergistic ignition.
    private static final String SNBT_DASHING_INCINERATOR = """
            {attributes:[{id:"minecraft:generic.max_health",base:35.0d}],Health:35.0f,active_effects:[{id:"minecraft:strength",amplifier:0b,duration:-1,show_particles:0b},{id:"irons_spellbooks:burning_dash",amplifier:1b,duration:-1,show_particles:1b}],HandItems:[{id:"minecraft:stone_sword",count:1,components:{"minecraft:enchantments":{sharpness:2,fire_aspect:2}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":1315860,"minecraft:enchantments":{protection:2}}},{}]}\
            """;

    // Requires: simplyswords — piglin with wildfire vortex effect; iron axe
    // for melee. fire_resistance prevents self-damage from the vortex bursts.
    private static final String SNBT_WILDFIRE_PIGLIN = """
            {Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.max_health",base:30.0d}],Health:30.0f,IsImmuneToZombification:1b,active_effects:[{id:"minecraft:fire_resistance",amplifier:0b,duration:-1,show_particles:0b},{id:"simplyswords:wildfire",amplifier:0b,duration:-1,show_particles:1b}],HandItems:[{id:"minecraft:iron_axe",count:1,components:{"minecraft:enchantments":{sharpness:2}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":10497574,"minecraft:enchantments":{protection:2}}},{}]}\
            """;

    // Requires: simplyswords — wither_skel with smouldering DoT particles. Speed I
    // closes the gap before the slow burn ticks pay off.
    private static final String SNBT_SMOULDERING_MARAUDER = """
            {attributes:[{id:"minecraft:generic.max_health",base:30.0d}],Health:30.0f,active_effects:[{id:"minecraft:speed",amplifier:0b,duration:-1,show_particles:0b},{id:"simplyswords:smouldering",amplifier:0b,duration:-1,show_particles:1b}],HandItems:[{id:"minecraft:iron_sword",count:1,components:{"minecraft:enchantments":{sharpness:2,fire_aspect:1}}},{}],ArmorItems:[{},{},{id:"minecraft:chainmail_chestplate",count:1,components:{"minecraft:enchantments":{protection:1}}},{}]}\
            """;
}
