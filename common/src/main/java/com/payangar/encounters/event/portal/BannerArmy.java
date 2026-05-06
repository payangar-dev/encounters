package com.payangar.encounters.event.portal;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.List;

/**
 * Generic banner-bearer system shared across encounter events.
 *
 * <p>Eligibility is opt-in per roster entry via the entity-tag
 * {@value #BEARER_ELIGIBLE_TAG} declared in the entry's SNBT
 * ({@code Tags:["encounters_banner_eligible"]}). The tag travels with the
 * entity in vanilla NBT, so any user-customised entry can flip a single
 * mob in or out of the banner pool without touching code.</p>
 *
 * <p>Themes are <em>not</em> user-customisable — each event ships its own
 * curated list (e.g. {@link NetherPortalInvasionEvent#BANNER_THEMES} for
 * the nether faction). Per-invasion, the event picks one theme at random
 * and constructs a {@code BannerArmy} from it; every eligible mob of that
 * invasion then has a {@link #BEARER_CHANCE} probability of becoming a
 * bearer (banner in head slot + matching pattern stamped on any vanilla
 * shield in their offhand). This produces a coherent army identity while
 * leaving most soldiers unmarked, so the bearer reads as a standout.</p>
 */
public final class BannerArmy {

    /** Entity tag opting a mob into the banner-bearer pool for the current event. */
    public static final String BEARER_ELIGIBLE_TAG = "encounters_banner_eligible";

    /** Per-spawn probability a tagged mob becomes a bearer. */
    private static final float BEARER_CHANCE = 0.3f;

    /** A single pattern layer keyed by ResourceLocation (resolved against the registry at apply time). */
    public record Layer(ResourceLocation patternId, DyeColor color) {}

    /** A complete banner design: base color + ordered list of pattern layers. */
    public record Theme(DyeColor base, List<Layer> layers) {}

    private final Theme theme;

    public BannerArmy(Theme theme) {
        this.theme = theme;
    }

    /** Picks a theme uniformly from the provided list and returns the resulting army. */
    public static BannerArmy random(List<Theme> themes, RandomSource rng) {
        if (themes == null || themes.isEmpty()) {
            throw new IllegalArgumentException("BannerArmy.random requires a non-empty theme list");
        }
        return new BannerArmy(themes.get(rng.nextInt(themes.size())));
    }

    /**
     * Recursively walks {@code entity} and every passenger. Each Mob carrying
     * the {@link #BEARER_ELIGIBLE_TAG} is independently rolled against
     * {@link #BEARER_CHANCE} — winners become bearers (banner in head slot,
     * patterned shield if they had a vanilla shield in the offhand).
     * Untagged mobs and non-winners are untouched.
     */
    public void applyTo(Entity entity, ServerLevel level) {
        if (entity instanceof Mob mob
                && mob.getTags().contains(BEARER_ELIGIBLE_TAG)
                && level.getRandom().nextFloat() < BEARER_CHANCE) {
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
        ItemStack stack = new ItemStack(bannerItemForColor(theme.base()));
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

    private static Item bannerItemForColor(DyeColor color) {
        return switch (color) {
            case WHITE -> Items.WHITE_BANNER;
            case ORANGE -> Items.ORANGE_BANNER;
            case MAGENTA -> Items.MAGENTA_BANNER;
            case LIGHT_BLUE -> Items.LIGHT_BLUE_BANNER;
            case YELLOW -> Items.YELLOW_BANNER;
            case LIME -> Items.LIME_BANNER;
            case PINK -> Items.PINK_BANNER;
            case GRAY -> Items.GRAY_BANNER;
            case LIGHT_GRAY -> Items.LIGHT_GRAY_BANNER;
            case CYAN -> Items.CYAN_BANNER;
            case PURPLE -> Items.PURPLE_BANNER;
            case BLUE -> Items.BLUE_BANNER;
            case BROWN -> Items.BROWN_BANNER;
            case GREEN -> Items.GREEN_BANNER;
            case RED -> Items.RED_BANNER;
            case BLACK -> Items.BLACK_BANNER;
        };
    }
}
