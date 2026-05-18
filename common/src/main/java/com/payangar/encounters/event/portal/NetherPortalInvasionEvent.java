package com.payangar.encounters.event.portal;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.event.ActiveEncounterTracker;
import com.payangar.encounters.event.MobRoster;
import com.payangar.encounters.event.banner.BannerArmy;
import com.payangar.encounters.event.cinematic.CinematicTicker;
import com.payangar.encounters.event.pool.EncounterPoolsManager;
import com.payangar.encounters.event.pool.SpawnPool;
import com.payangar.encounters.event.portal.PortalGeometry.PortalSite;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

/**
 * Identity, roster cache, banner themes and force-trigger entry point for the
 * nether portal invasion event. All scanner-driven gating (enabled, interval,
 * trigger chance, concurrency cap, post-event cooldown, min distance) lives
 * in {@link PortalScanner}; the post-event cooldown timestamp is owned by
 * {@link ActiveEncounterTracker}. This class only holds what is intrinsically
 * portal-invasion-specific.
 */
public final class NetherPortalInvasionEvent {

    public static final String ID = "nether_portal_invasion";
    /** Datapack pool location: {@code data/encounters/spawn_pools/nether_portal_invasion.json}. */
    public static final ResourceLocation POOL_ID =
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, ID);

    /**
     * Loot table rolled when an invasion is fully completed (all waves
     * cleared, AFTERMATH phase reached). The bundled default at
     * {@code data/encounters/loot_table/portal_invasion/reward.json} delegates
     * to vanilla nether loot tables ({@code bastion_*}, {@code nether_bridge}),
     * so any mod that injects entries into those tables (NeoForge GLM,
     * Fabric loot table events, datapack overrides) automatically appears
     * in the portal invasion rewards without configuration. Surcharger ce
     * chemin via datapack remplace entièrement la table.
     */
    public static final ResourceKey<LootTable> REWARD_LOOT_TABLE = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "portal_invasion/reward")
    );

    /**
     * Curated banner themes for the nether faction. One is rolled per
     * invasion in {@link PortalInvasion}; every banner-eligible mob of
     * that invasion (those carrying the {@code encounters_banner_eligible}
     * tag) shares the chosen design. Adding a new theme is a one-line
     * append; themes are intentionally not user-configurable.
     */
    public static final List<BannerArmy.Theme> BANNER_THEMES = List.of(
            // Bloodflag — red base, single black piglin emblem.
            new BannerArmy.Theme(DyeColor.RED, List.of(
                    new BannerArmy.Layer(ResourceLocation.parse("minecraft:piglin"), DyeColor.BLACK)
            )),
            // Skullbearer — black base, red wither skull.
            new BannerArmy.Theme(DyeColor.BLACK, List.of(
                    new BannerArmy.Layer(ResourceLocation.parse("minecraft:skull"), DyeColor.RED)
            )),
            // Brimstone — red base, yellow vertical stripe behind a black creeper face.
            new BannerArmy.Theme(DyeColor.RED, List.of(
                    new BannerArmy.Layer(ResourceLocation.parse("minecraft:stripe_center"), DyeColor.YELLOW),
                    new BannerArmy.Layer(ResourceLocation.parse("minecraft:creeper"), DyeColor.BLACK)
            )),
            // Iron Phalanx — black base with a bold white cross and a yellow piglin emblem.
            new BannerArmy.Theme(DyeColor.BLACK, List.of(
                    new BannerArmy.Layer(ResourceLocation.parse("minecraft:cross"), DyeColor.WHITE),
                    new BannerArmy.Layer(ResourceLocation.parse("minecraft:piglin"), DyeColor.YELLOW)
            )),
            // Cinder Banner — red base with a dark brick texture.
            new BannerArmy.Theme(DyeColor.RED, List.of(
                    new BannerArmy.Layer(ResourceLocation.parse("minecraft:bricks"), DyeColor.BLACK)
            )),
            // Hellscar — black with red bottom triangle and yellow border.
            new BannerArmy.Theme(DyeColor.BLACK, List.of(
                    new BannerArmy.Layer(ResourceLocation.parse("minecraft:triangle_bottom"), DyeColor.RED),
                    new BannerArmy.Layer(ResourceLocation.parse("minecraft:border"), DyeColor.YELLOW)
            ))
    );

    private static MobRoster cachedRoster;
    private static SpawnPool cachedPool;

    private NetherPortalInvasionEvent() {}

    /**
     * Starts an invasion at the given portal site, spawning toward the given
     * face. Used by the debug command (bypasses every scanner gate) and by
     * {@link PortalScanner} once it has validated a candidate (the scanner
     * applies the gates upstream).
     *
     * <p>Sanity checks kept here for resilience to both call paths: overworld
     * dimension, non-empty roster, concurrency cap. The minimum-distance gate
     * is intentionally <em>not</em> rechecked — the debug command may force
     * two adjacent invasions for testing.</p>
     *
     * @return true if the invasion was started, false if blocked.
     */
    public static synchronized boolean forceTrigger(ServerLevel level, PortalSite site, Direction face) {
        if (level.dimension() != Level.OVERWORLD) {
            Constants.LOG.warn("[{}] refused: not in overworld (dim={})", ID, level.dimension().location());
            return false;
        }
        EncountersConfig config = EncountersConfig.get();
        if (roster().isEmpty()) {
            Constants.LOG.warn("[{}] refused: roster is empty", ID);
            return false;
        }
        int activeCount = ActiveEncounterTracker.activeCount(level, ID);
        if (activeCount >= config.portal.concurrency.maxConcurrent) {
            Constants.LOG.info("[{}] refused: max concurrent reached ({}/{})",
                    ID, activeCount, config.portal.concurrency.maxConcurrent);
            return false;
        }
        PortalInvasion invasion = new PortalInvasion(level, site, face);
        ActiveEncounterTracker.register(invasion);
        CinematicTicker.start(invasion);
        Constants.LOG.info("[{}] invasion started at portal centre ({}, {}, {}), facing {}, {} waves",
                ID, (int) site.centerBase().x, (int) site.centerBase().y, (int) site.centerBase().z,
                face, invasion.totalWaves());
        return true;
    }

    public static MobRoster roster() {
        SpawnPool pool = EncounterPoolsManager.getInstance().get(POOL_ID);
        if (cachedRoster == null || cachedPool != pool) {
            cachedRoster = MobRoster.resolve(pool, ID);
            cachedPool = pool;
        }
        return cachedRoster;
    }

    public static void invalidateRoster() {
        cachedRoster = null;
        cachedPool = null;
    }
}
