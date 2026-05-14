package com.payangar.encounters.event.portal;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.config.WeightedMob;
import com.payangar.encounters.event.ActiveEncounterTracker;
import com.payangar.encounters.event.MobRoster;
import com.payangar.encounters.event.banner.BannerArmy;
import com.payangar.encounters.event.cinematic.CinematicTicker;
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
 * Coordinates the nether portal invasion event.
 *
 * <p>Concurrency and spatial spacing are enforced via
 * {@link ActiveEncounterTracker}: at most
 * {@link EncountersConfig#netherPortalInvasionMaxConcurrent} invasions may run
 * on a level at any time, and the scanner skips candidate portal sites within
 * {@link EncountersConfig#netherPortalInvasionMinDistanceBetween} blocks of an
 * already-active invasion. After any invasion ends,
 * {@link #lastInvasionEndTick} stores the server tick at which it released;
 * {@link #canScannerTrigger} adds a world-wide post-end cooldown on top.</p>
 *
 * <p>The debug command bypasses the cooldown (it still respects the concurrent
 * cap) so testing isn't blocked by a previous run. The minimum-distance gate
 * lives only in the scanner — the command may force two invasions adjacent to
 * each other for testing.</p>
 */
public final class NetherPortalInvasionEvent {

    public static final String ID = "nether_portal_invasion";

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

    /**
     * Server tick at which the last invasion on any level ended. Combined
     * with {@link EncountersConfig#netherPortalInvasionPortalCooldownTicks} to
     * enforce a world-wide cooldown between invasions. Stays at
     * {@link Long#MIN_VALUE} until the first invasion ends, so the cooldown
     * is trivially satisfied at server start.
     */
    private static volatile long lastInvasionEndTick = Long.MIN_VALUE;

    private static MobRoster cachedRoster;
    private static List<WeightedMob> cachedSource;

    private NetherPortalInvasionEvent() {}

    /**
     * Whether the periodic scanner is allowed to start an invasion on
     * {@code level} right now. Gates on the per-level concurrency cap and
     * the world-wide post-invasion cooldown.
     */
    public static boolean canScannerTrigger(ServerLevel level) {
        EncountersConfig config = EncountersConfig.get();
        if (ActiveEncounterTracker.activeCount(level, ID) >= config.netherPortalInvasionMaxConcurrent) return false;
        long now = level.getGameTime();
        long cooldown = config.netherPortalInvasionPortalCooldownTicks;
        return (now - lastInvasionEndTick) >= cooldown;
    }

    /**
     * Starts an invasion at the given portal site, spawning toward the given
     * face. Bypasses the cooldown but respects the concurrent cap and the
     * dimension/roster sanity checks. Used by the debug command and by the
     * scanner once it has picked a candidate.
     *
     * <p>The minimum-distance gate is enforced upstream by {@link PortalScanner}
     * so the debug command may force two adjacent invasions for testing.</p>
     *
     * @return true if the invasion was started, false if blocked.
     */
    public static synchronized boolean forceTrigger(ServerLevel level, PortalSite site, Direction face) {
        if (level.dimension() != Level.OVERWORLD) {
            Constants.LOG.warn("[{}] refused: not in overworld (dim={})", ID, level.dimension().location());
            return false;
        }
        EncountersConfig config = EncountersConfig.get();
        if (roster(config).isEmpty()) {
            Constants.LOG.warn("[{}] refused: roster is empty", ID);
            return false;
        }
        int activeCount = ActiveEncounterTracker.activeCount(level, ID);
        if (activeCount >= config.netherPortalInvasionMaxConcurrent) {
            Constants.LOG.info("[{}] refused: max concurrent reached ({}/{})",
                    ID, activeCount, config.netherPortalInvasionMaxConcurrent);
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

    /**
     * Called by {@link PortalInvasion} when it finishes or is abandoned.
     * Drops the tracker entry and records the cooldown timestamp so the next
     * scanner trigger waits {@link EncountersConfig#netherPortalInvasionPortalCooldownTicks}.
     */
    static synchronized void releaseLock(PortalInvasion invasion) {
        ActiveEncounterTracker.unregister(invasion);
        lastInvasionEndTick = invasion.level().getGameTime();
        Constants.LOG.info("[{}] lock released at tick {}", ID, lastInvasionEndTick);
    }

    /** Drops the cooldown timestamp. Used at server stop. */
    public static void releaseAll() {
        lastInvasionEndTick = Long.MIN_VALUE;
    }

    public static MobRoster roster(EncountersConfig config) {
        if (cachedRoster == null || cachedSource != config.netherPortalInvasionMobs) {
            cachedRoster = MobRoster.resolve(config.netherPortalInvasionMobs, ID);
            cachedSource = config.netherPortalInvasionMobs;
        }
        return cachedRoster;
    }

    public static void invalidateRoster() {
        cachedRoster = null;
        cachedSource = null;
    }
}
