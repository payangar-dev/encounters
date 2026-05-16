package com.payangar.encounters.config;

import com.payangar.encounters.Constants;
import com.payangar.encounters.event.EncounterRegistry;
import com.payangar.encounters.platform.Services;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.DoubleSlider;
import dev.isxander.yacl3.config.v2.api.autogen.IntSlider;
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

    // ===== Nether Portal Invasion =====

    @SerialEntry(comment = "Master toggle for the nether_portal_invasion event")
    @AutoGen(category = CATEGORY_PORTAL_INVASION)
    @TickBox
    public boolean netherPortalInvasionEnabled = true;

    @SerialEntry(comment = "How often (in server ticks) the mod scans loaded chunks for active nether portals")
    @AutoGen(category = CATEGORY_PORTAL_INVASION)
    @IntSlider(min = 20, max = 1200, step = 20)
    public int netherPortalInvasionScanIntervalTicks = 100;

    @SerialEntry(comment = "Maximum number of nether portal invasions that may run simultaneously across the level")
    @AutoGen(category = CATEGORY_PORTAL_INVASION)
    @IntSlider(min = 1, max = 4, step = 1)
    public int netherPortalInvasionMaxConcurrent = 1;

    @SerialEntry(comment = "Minimum distance (in blocks) between two concurrent portal invasions")
    @AutoGen(category = CATEGORY_PORTAL_INVASION)
    @IntSlider(min = 64, max = 1024, step = 32)
    public int netherPortalInvasionMinDistanceBetween = 256;

    @SerialEntry(comment = "Cooldown (in ticks) applied across the level after any invasion ends before another one may trigger")
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
        config.netherPortalInvasionMaxConcurrent = clampInt("netherPortalInvasionMaxConcurrent",
                config.netherPortalInvasionMaxConcurrent, 1, Integer.MAX_VALUE);
        config.netherPortalInvasionMinDistanceBetween = clampInt("netherPortalInvasionMinDistanceBetween",
                config.netherPortalInvasionMinDistanceBetween, 0, Integer.MAX_VALUE);
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

}
