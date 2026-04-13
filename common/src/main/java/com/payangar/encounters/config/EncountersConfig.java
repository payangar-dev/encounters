package com.payangar.encounters.config;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.gui.WeightedMobListFactory;
import com.payangar.encounters.event.LightningOverchargeEvent;
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

    public static final String CATEGORY_LIGHTNING = "lightning_overcharge";

    private static final ConfigClassHandler<EncountersConfig> HANDLER = ConfigClassHandler
            .createBuilder(EncountersConfig.class)
            .id(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(Services.PLATFORM.getConfigDir().resolve(Constants.MOD_ID + ".json5"))
                    .setJson5(true)
                    .build())
            .build();

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

    @SerialEntry(comment = "Weighted mob pool. Each entry: { id, weight, nbt?, label? }. " +
            "'nbt' is SNBT identical to the /summon command. Editable from the in-game config GUI.")
    @AutoGen(category = CATEGORY_LIGHTNING)
    @ListGroup(
            valueFactory = WeightedMobListFactory.class,
            controllerFactory = WeightedMobListFactory.class,
            addEntriesToBottom = true
    )
    public List<WeightedMob> lightningOverchargeMobs = defaultLightningMobs();

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
        Constants.LOG.info("Loaded {} config from {}.json5", Constants.MOD_NAME, Constants.MOD_ID);
    }

    public static void save() {
        sanitize(HANDLER.instance());
        HANDLER.save();
        LightningOverchargeEvent.invalidateRoster();
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
    }

    private static List<WeightedMob> defaultLightningMobs() {
        List<WeightedMob> list = new ArrayList<>();
        list.add(new WeightedMob("minecraft:skeleton", 10, SNBT_STORMCALLER, "Stormcaller"));
        list.add(new WeightedMob("minecraft:zombie", 8, SNBT_SOUL_DROWNED, "Soul-Drowned"));
        list.add(new WeightedMob("minecraft:stray", 5, SNBT_FROSTBOUND_ECHO, "Frostbound Echo"));
        list.add(new WeightedMob("minecraft:drowned", 4, SNBT_TEMPEST_WRAITH, "Tempest Wraith"));
        list.add(new WeightedMob("minecraft:wither_skeleton", 2, SNBT_SOUL_LIT_VANGUARD, "Soul-lit Vanguard"));
        return list;
    }

    // dyed_color 1912627 = #1D2D33, dark teal — sculk-aligned palette shared across the roster.

    private static final String SNBT_STORMCALLER = """
            {attributes:[{id:"minecraft:generic.scale",base:1.1d}],HandItems:[{id:"minecraft:bow",count:1,components:{"minecraft:enchantments":{power:3,flame:1,punch:1}}},{}],ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1,components:{"minecraft:dyed_color":1912627,"minecraft:enchantments":{protection:2}}}],HandDropChances:[0f,0f],ArmorDropChances:[0f,0f,0f,0f]}\
            """;

    private static final String SNBT_SOUL_DROWNED = """
            {attributes:[{id:"minecraft:generic.scale",base:1.15d},{id:"minecraft:generic.max_health",base:30.0d},{id:"minecraft:generic.knockback_resistance",base:0.5d}],Health:30.0f,active_effects:[{id:"minecraft:strength",amplifier:0b,duration:-1,show_particles:0b}],HandItems:[{id:"minecraft:iron_sword",count:1,components:{"minecraft:enchantments":{sharpness:2,knockback:1}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":1912627,"minecraft:enchantments":{protection:2}}},{}],HandDropChances:[0f,0f],ArmorDropChances:[0f,0f,0f,0f]}\
            """;

    private static final String SNBT_FROSTBOUND_ECHO = """
            {attributes:[{id:"minecraft:generic.scale",base:1.1d}],HandItems:[{id:"minecraft:bow",count:1,components:{"minecraft:enchantments":{power:2,punch:2}}},{}],HandDropChances:[0f,0f]}\
            """;

    private static final String SNBT_TEMPEST_WRAITH = """
            {attributes:[{id:"minecraft:generic.scale",base:1.1d}],active_effects:[{id:"minecraft:conduit_power",amplifier:0b,duration:-1,show_particles:1b}],HandItems:[{id:"minecraft:trident",count:1,components:{"minecraft:enchantments":{impaling:4,loyalty:3}}},{}],HandDropChances:[0f,0f]}\
            """;

    private static final String SNBT_SOUL_LIT_VANGUARD = """
            {attributes:[{id:"minecraft:generic.scale",base:1.2d},{id:"minecraft:generic.max_health",base:30.0d}],Health:30.0f,active_effects:[{id:"minecraft:strength",amplifier:0b,duration:-1,show_particles:0b}],HandItems:[{id:"minecraft:stone_sword",count:1,components:{"minecraft:enchantments":{sharpness:3,fire_aspect:2}}},{}],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":0,"minecraft:enchantments":{protection:3}}},{}],HandDropChances:[0f,0f],ArmorDropChances:[0f,0f,0f,0f]}\
            """;
}
