package com.payangar.encounters.event.banner;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Reusable banner-bearer system. Any encounter event can build a {@code BannerArmy}
 * from its own list of curated themes and apply it to spawned entities — eligible
 * mobs (those carrying the {@value #BEARER_ELIGIBLE_TAG} tag in their SNBT) are
 * independently rolled to become bearers, who get a banner in their head slot
 * plus a matching pattern stamped on any vanilla shield in their offhand.
 *
 * <p>Themes are deliberately not user-customisable: each event ships its own
 * curated list (e.g. {@code NetherPortalInvasionEvent.BANNER_THEMES} for the
 * nether faction, but a sky-themed event could provide a different list).</p>
 *
 * <p>The bearer chance is configurable per army instance — events that want a
 * different density (e.g. an "elite squad" event where every mob is a bearer)
 * pass their own probability to {@link #random(List, RandomSource, float)} or
 * to the constructor.</p>
 */
public final class BannerArmy {

    /** Entity tag opting a mob into the banner-bearer pool. Set in the entry's SNBT. */
    public static final String BEARER_ELIGIBLE_TAG = "encounters_banner_eligible";

    /** Default per-spawn probability that a tagged mob becomes a bearer. */
    public static final float DEFAULT_BEARER_CHANCE = 0.3f;

    /** A single pattern layer keyed by ResourceLocation (resolved against the registry at apply time). */
    public record Layer(ResourceLocation patternId, DyeColor color) {}

    /** A complete banner design: base color + ordered list of pattern layers. */
    public record Theme(DyeColor base, List<Layer> layers) {}

    private static final Map<DyeColor, Item> BANNER_BY_COLOR = buildBannerColorMap();

    private final Theme theme;
    private final float bearerChance;

    public BannerArmy(Theme theme) {
        this(theme, DEFAULT_BEARER_CHANCE);
    }

    public BannerArmy(Theme theme, float bearerChance) {
        this.theme = theme;
        this.bearerChance = bearerChance;
    }

    /** Picks a theme uniformly from the provided list and returns the resulting army with the default bearer chance. */
    public static BannerArmy random(List<Theme> themes, net.minecraft.util.RandomSource rng) {
        return random(themes, rng, DEFAULT_BEARER_CHANCE);
    }

    /** Picks a theme uniformly from the provided list and returns the resulting army with the given bearer chance. */
    public static BannerArmy random(List<Theme> themes, net.minecraft.util.RandomSource rng, float bearerChance) {
        if (themes == null || themes.isEmpty()) {
            throw new IllegalArgumentException("BannerArmy.random requires a non-empty theme list");
        }
        return new BannerArmy(themes.get(rng.nextInt(themes.size())), bearerChance);
    }

    /**
     * Recursively walks {@code entity} and every passenger. Each Mob carrying
     * the {@link #BEARER_ELIGIBLE_TAG} is independently rolled against the
     * army's bearer chance — winners become bearers (banner in head slot,
     * patterned shield if they had a vanilla shield in the offhand). Untagged
     * mobs and non-winners are untouched.
     */
    public void applyTo(Entity entity, ServerLevel level) {
        if (entity instanceof Mob mob
                && mob.getTags().contains(BEARER_ELIGIBLE_TAG)
                && level.getRandom().nextFloat() < bearerChance) {
            mob.setItemSlot(EquipmentSlot.HEAD, buildBanner(level));
            ItemStack offhand = mob.getItemBySlot(EquipmentSlot.OFFHAND);
            if (offhand.is(Items.SHIELD)) {
                mob.setItemSlot(EquipmentSlot.OFFHAND, buildShield(level));
            }
        }
        for (Entity p : entity.getPassengers()) {
            applyTo(p, level);
        }
    }

    private ItemStack buildBanner(ServerLevel level) {
        ItemStack stack = new ItemStack(BANNER_BY_COLOR.get(theme.base()));
        stack.set(DataComponents.BANNER_PATTERNS, resolveLayers(level));
        return stack;
    }

    private ItemStack buildShield(ServerLevel level) {
        ItemStack stack = new ItemStack(Items.SHIELD);
        stack.set(DataComponents.BASE_COLOR, theme.base());
        stack.set(DataComponents.BANNER_PATTERNS, resolveLayers(level));
        return stack;
    }

    /**
     * Resolves each layer's ResourceLocation into a Holder via the level's
     * banner-pattern registry. Done at apply time (not at theme declaration)
     * because Holders need the dynamic registry, which only exists at runtime.
     */
    private BannerPatternLayers resolveLayers(ServerLevel level) {
        Registry<BannerPattern> registry = level.registryAccess().registryOrThrow(Registries.BANNER_PATTERN);
        List<BannerPatternLayers.Layer> resolved = theme.layers().stream()
                .map(l -> new BannerPatternLayers.Layer(
                        registry.getHolderOrThrow(ResourceKey.create(Registries.BANNER_PATTERN, l.patternId())),
                        l.color()))
                .toList();
        return new BannerPatternLayers(resolved);
    }

    private static Map<DyeColor, Item> buildBannerColorMap() {
        Map<DyeColor, Item> map = new EnumMap<>(DyeColor.class);
        map.put(DyeColor.WHITE,      Items.WHITE_BANNER);
        map.put(DyeColor.ORANGE,     Items.ORANGE_BANNER);
        map.put(DyeColor.MAGENTA,    Items.MAGENTA_BANNER);
        map.put(DyeColor.LIGHT_BLUE, Items.LIGHT_BLUE_BANNER);
        map.put(DyeColor.YELLOW,     Items.YELLOW_BANNER);
        map.put(DyeColor.LIME,       Items.LIME_BANNER);
        map.put(DyeColor.PINK,       Items.PINK_BANNER);
        map.put(DyeColor.GRAY,       Items.GRAY_BANNER);
        map.put(DyeColor.LIGHT_GRAY, Items.LIGHT_GRAY_BANNER);
        map.put(DyeColor.CYAN,       Items.CYAN_BANNER);
        map.put(DyeColor.PURPLE,     Items.PURPLE_BANNER);
        map.put(DyeColor.BLUE,       Items.BLUE_BANNER);
        map.put(DyeColor.BROWN,      Items.BROWN_BANNER);
        map.put(DyeColor.GREEN,      Items.GREEN_BANNER);
        map.put(DyeColor.RED,        Items.RED_BANNER);
        map.put(DyeColor.BLACK,      Items.BLACK_BANNER);
        return map;
    }
}
