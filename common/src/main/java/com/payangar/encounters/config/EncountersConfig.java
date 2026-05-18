package com.payangar.encounters.config;

import com.payangar.encounters.Constants;
import com.payangar.encounters.LogLevelControl;
import com.payangar.encounters.event.EncounterRegistry;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;

/**
 * Top-level Cloth Config entry point. Each sub-{@code Settings} class becomes
 * its own GUI tab and on-disk file under {@code <configDir>/encounters/},
 * thanks to {@link PartitioningSerializer.GlobalData}.
 *
 * <p>Capability blocks ({@link TrackerSettings}, {@link ScannerSettings},
 * {@link CohesionSettings}) are composed inside per-event Settings via
 * {@code @ConfigEntry.Gui.CollapsibleObject} — they appear as collapsible
 * sub-sections in the GUI without being top-level partitioned modules.</p>
 *
 * <p>This class must declare <strong>no static fields</strong>:
 * {@link PartitioningSerializer.GlobalData} reflects over every declared field
 * looking for a {@code @Config}-annotated sub-module and throws
 * {@code "Invalid module: ..."} on anything else (static or not). State that
 * was previously stored in a static {@code HOLDER} is now accessed on demand
 * via {@link AutoConfig#getConfigHolder(Class)}.</p>
 */
@Config(name = Constants.MOD_ID)
public class EncountersConfig extends PartitioningSerializer.GlobalData {

    @ConfigEntry.Category("general")
    @ConfigEntry.Gui.TransitiveObject
    public GeneralSettings general = new GeneralSettings();

    @ConfigEntry.Category("lightning")
    @ConfigEntry.Gui.TransitiveObject
    public LightningSettings lightning = new LightningSettings();

    @ConfigEntry.Category("portal")
    @ConfigEntry.Gui.TransitiveObject
    public PortalSettings portal = new PortalSettings();

    @ConfigEntry.Category("skirmish")
    @ConfigEntry.Gui.TransitiveObject
    public SkirmishSettings skirmish = new SkirmishSettings();

    public static EncountersConfig get() {
        return holder().getConfig();
    }

    public static ConfigHolder<EncountersConfig> holder() {
        return AutoConfig.getConfigHolder(EncountersConfig.class);
    }

    /**
     * Registers the config with AutoConfig. Must run exactly once during
     * loader bootstrap, before any event reads a config field.
     */
    public static void load() {
        LegacyConfigMigrator.run();
        ConfigHolder<EncountersConfig> h = AutoConfig.register(EncountersConfig.class,
                PartitioningSerializer.wrap(JanksonConfigSerializer::new));
        // Invalidate per-event roster caches AND re-apply the log level
        // whenever the config is saved via the GUI. Datapack reloads invalidate
        // rosters via EncounterPoolsManager independently — this hook covers
        // GUI-driven changes only.
        h.registerSaveListener((holder, config) -> {
            EncounterRegistry.invalidateAll();
            LogLevelControl.apply(config.general.debug);
            return net.minecraft.world.InteractionResult.PASS;
        });
        EncounterRegistry.invalidateAll();
        LogLevelControl.apply(h.getConfig().general.debug);
        Constants.LOG.info("Loaded {} config under {}/encounters/", Constants.MOD_NAME, Constants.MOD_ID);
    }

    /** Convenience save — used by callers that mutated the config programmatically. */
    public static void save() {
        ConfigHolder<EncountersConfig> h = AutoConfig.getConfigHolder(EncountersConfig.class);
        if (h != null) h.save();
    }
}
