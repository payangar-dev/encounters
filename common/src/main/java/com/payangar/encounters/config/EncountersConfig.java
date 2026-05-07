package com.payangar.encounters.config;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.gui.WeightedMobListFactory;
import com.payangar.encounters.event.LightningOverchargeEvent;
import com.payangar.encounters.event.portal.NetherPortalInvasionEvent;
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
        LightningOverchargeEvent.invalidateRoster();
        NetherPortalInvasionEvent.invalidateRoster();
        Constants.LOG.info("Loaded {} config from {}.json5", Constants.MOD_NAME, Constants.MOD_ID);
    }

    public static void save() {
        sanitize(HANDLER.instance());
        HANDLER.save();
        LightningOverchargeEvent.invalidateRoster();
        NetherPortalInvasionEvent.invalidateRoster();
    }

    private static void sanitize(EncountersConfig config) {
        if (config.lightningOverchargeChance < 0.0 || config.lightningOverchargeChance > 1.0) {
            Constants.LOG.warn("lightningOverchargeChance was {}, clamped to [0, 1]", config.lightningOverchargeChance);
            config.lightningOverchargeChance = Math.max(0.0, Math.min(1.0, config.lightningOverchargeChance));
        }
        if (config.lightningOverchargeGroupMin < 1) config.lightningOverchargeGroupMin = 1;
        if (config.lightningOverchargeGroupMax < config.lightningOverchargeGroupMin) {
            config.lightningOverchargeGroupMax = config.lightningOverchargeGroupMin;
        }
        if (config.lightningOverchargeGroupCohesionRadius < 1) {
            config.lightningOverchargeGroupCohesionRadius = 1;
        }
        if (config.netherPortalInvasionTriggerChance < 0.0 || config.netherPortalInvasionTriggerChance > 1.0) {
            Constants.LOG.warn("netherPortalInvasionTriggerChance was {}, clamped to [0, 1]",
                    config.netherPortalInvasionTriggerChance);
            config.netherPortalInvasionTriggerChance = Math.max(0.0, Math.min(1.0, config.netherPortalInvasionTriggerChance));
        }
        if (config.netherPortalInvasionMinWaves < 1) config.netherPortalInvasionMinWaves = 1;
        if (config.netherPortalInvasionMaxWaves < config.netherPortalInvasionMinWaves) {
            config.netherPortalInvasionMaxWaves = config.netherPortalInvasionMinWaves;
        }
        if (config.netherPortalInvasionFirstWaveSize < 1) config.netherPortalInvasionFirstWaveSize = 1;
        if (config.netherPortalInvasionWaveSizeStep < 0) config.netherPortalInvasionWaveSizeStep = 0;
        if (config.netherPortalInvasionScanIntervalTicks < 20) config.netherPortalInvasionScanIntervalTicks = 20;
        if (config.netherPortalInvasionPortalCooldownTicks < 1200) config.netherPortalInvasionPortalCooldownTicks = 1200;
        if (config.netherPortalInvasionGroupCohesionRadius < 1) {
            config.netherPortalInvasionGroupCohesionRadius = 1;
        }
    }

    private static List<WeightedMob> defaultPortalInvasionMobs() {
        List<WeightedMob> list = new ArrayList<>();
        // Piglin variants dominate the early waves; nether elites and the
        // mounted hoglin combos take over as the rarity bias kicks in. Hoglin
        // appears only as a mount (its Piglin Brute rider doesn't antagonize
        // it because PiglinBruteAi has no StartHuntingHoglin task — and the
        // regular Piglin's hunting task is suspended in RIDE activity).
        // Magma cube and standalone hoglin/zoglin remain excluded for the
        // reasons documented in CLAUDE.md.
        list.add(new WeightedMob("minecraft:piglin", 40, SNBT_PIGLIN_SOLDIER, "Piglin Soldier"));
        list.add(new WeightedMob("minecraft:piglin", 25, SNBT_PIGLIN_MARKSMAN, "Piglin Marksman"));
        list.add(new WeightedMob("minecraft:piglin", 15, SNBT_PIGLIN_SHIELDBEARER, "Piglin Shieldbearer"));
        list.add(new WeightedMob("minecraft:wither_skeleton", 12, SNBT_ASHEN_MARAUDER, "Ashen Marauder"));
        list.add(new WeightedMob("minecraft:blaze", 8, SNBT_EMBERCALLER, "Embercaller"));
        list.add(new WeightedMob("minecraft:piglin_brute", 5, SNBT_IRONHIDE_BRUTE, "Ironhide Brute"));
        list.add(new WeightedMob("minecraft:hoglin", 3, SNBT_TUSKED_VANGUARD, "Tusked Vanguard"));
        list.add(new WeightedMob("minecraft:hoglin", 2, SNBT_CROSSBOW_OUTRIDER, "Crossbow Outrider"));
        list.add(new WeightedMob("minecraft:ghast", 1, SNBT_INFERNO_SOVEREIGN, "Inferno Sovereign"));
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
            {Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.max_health",base:35.0d},{id:"minecraft:generic.knockback_resistance",base:0.5d}],Health:35.0f,IsImmuneToZombification:1b,HandItems:[{id:"minecraft:iron_sword",count:1,components:{"minecraft:enchantments":{sharpness:2}}},{id:"minecraft:shield",count:1}]}\
            """;

    private static final String SNBT_ASHEN_MARAUDER = """
            {attributes:[{id:"minecraft:generic.max_health",base:25.0d}],Health:25.0f,HandItems:[{id:"minecraft:stone_sword",count:1},{}]}\
            """;

    private static final String SNBT_EMBERCALLER = """
            {attributes:[{id:"minecraft:generic.max_health",base:25.0d}],Health:25.0f}\
            """;

    private static final String SNBT_IRONHIDE_BRUTE = """
            {Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.scale",base:1.1d},{id:"minecraft:generic.max_health",base:60.0d},{id:"minecraft:generic.knockback_resistance",base:0.4d}],Health:60.0f,IsImmuneToZombification:1b,HandItems:[{id:"minecraft:iron_axe",count:1,components:{"minecraft:enchantments":{sharpness:3}}},{}]}\
            """;

    // Mounted: hoglin (mount) + piglin brute rider with iron axe + KB1.
    // PiglinBruteAi has no StartHuntingHoglin task, so no antagonism loop.
    // Banner tag applies to the rider only — the hoglin mount carries no flag.
    private static final String SNBT_TUSKED_VANGUARD = """
            {attributes:[{id:"minecraft:generic.max_health",base:50.0d},{id:"minecraft:generic.knockback_resistance",base:0.6d}],Health:50.0f,IsImmuneToZombification:1b,Passengers:[{id:"minecraft:piglin_brute",Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.scale",base:1.1d},{id:"minecraft:generic.max_health",base:50.0d}],Health:50.0f,IsImmuneToZombification:1b,HandItems:[{id:"minecraft:iron_axe",count:1,components:{"minecraft:enchantments":{sharpness:2,knockback:1}}},{}]}]}\
            """;

    // Mounted: hoglin (mount) + piglin crossbow rider. Regular Piglin's
    // StartHuntingHoglin task is suspended in RIDE activity, so no loop.
    private static final String SNBT_CROSSBOW_OUTRIDER = """
            {attributes:[{id:"minecraft:generic.max_health",base:45.0d},{id:"minecraft:generic.knockback_resistance",base:0.5d}],Health:45.0f,IsImmuneToZombification:1b,Passengers:[{id:"minecraft:piglin",Tags:["encounters_banner_eligible"],attributes:[{id:"minecraft:generic.max_health",base:25.0d}],Health:25.0f,IsImmuneToZombification:1b,HandItems:[{id:"minecraft:crossbow",count:1,components:{"minecraft:enchantments":{quick_charge:2,multishot:1}}},{}]}]}\
            """;

    private static final String SNBT_INFERNO_SOVEREIGN = """
            {attributes:[{id:"minecraft:generic.scale",base:1.1d},{id:"minecraft:generic.max_health",base:30.0d}],Health:30.0f}\
            """;
}
