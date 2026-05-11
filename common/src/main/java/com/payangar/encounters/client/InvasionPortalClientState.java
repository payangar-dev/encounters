package com.payangar.encounters.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side tracker of active portal invasions, indexed by their
 * unique anchor (the portal's bottom-row centre block). Holds the list
 * of nether-portal block positions for each invasion plus a smooth
 * 0..1 fade value used by the overlay renderer.
 *
 * <p>The fade is purely cosmetic: it ramps up at invasion start and
 * back down at invasion end over {@value #FADE_TICKS} ticks
 * (i.e. {@code FADE_TICKS / 20} seconds). It does not encode wave
 * progress.</p>
 *
 * <p>The {@link #hiddenPositions} set is read off-thread by chunk
 * meshing (via the {@code BlockRenderDispatcher} mixin) so it uses a
 * concurrent collection. Every other field is touched only on the
 * client thread — network handlers dispatch via {@code Minecraft.execute}
 * and the tick advance runs on the client tick callback.</p>
 *
 * <p>While a position is in {@link #hiddenPositions}, the vanilla
 * nether portal block does not contribute vertices to the chunk mesh.
 * The overlay renderer then draws the violet (vanilla sprite) and red
 * (encounters sprite) layers itself with complementary alphas — a true
 * cross-fade rather than an additive overlay.</p>
 */
public final class InvasionPortalClientState {

    /** Number of client ticks the fade ramp takes to traverse 0 → 1 (or 1 → 0). */
    private static final int FADE_TICKS = 20;
    private static final float FADE_PER_TICK = 1.0f / FADE_TICKS;

    private static final Map<BlockPos, Entry> entries = new HashMap<>();

    /** Positions whose vanilla portal rendering must be skipped. Read off-thread by chunk meshing. */
    private static final Set<BlockPos> hiddenPositions = ConcurrentHashMap.newKeySet();

    private InvasionPortalClientState() {}

    public static void onInvasionStart(BlockPos anchor, List<BlockPos> portalBlocks) {
        Entry e = entries.computeIfAbsent(anchor, k -> new Entry());
        e.portalBlocks = List.copyOf(portalBlocks);
        e.fadeTarget = 1f;
        Set<BlockPos> newlyHidden = new HashSet<>();
        for (BlockPos p : e.portalBlocks) {
            if (hiddenPositions.add(p)) newlyHidden.add(p);
        }
        if (!newlyHidden.isEmpty()) markSectionsDirty(newlyHidden);
    }

    public static void onInvasionEnd(BlockPos anchor) {
        Entry e = entries.get(anchor);
        if (e != null) e.fadeTarget = 0f;
    }

    /** Wipe all state — called on disconnect / client level swap. */
    public static void clear() {
        entries.clear();
        if (!hiddenPositions.isEmpty()) {
            Set<BlockPos> snapshot = new HashSet<>(hiddenPositions);
            hiddenPositions.clear();
            markSectionsDirty(snapshot);
        }
    }

    /**
     * Advances the fade for every active entry by one tick. When an
     * entry decays to zero after an end packet, its positions are
     * dropped from {@link #hiddenPositions} and the affected sections
     * are queued for re-mesh so the vanilla portal pops back in cleanly.
     */
    public static void tickFade() {
        Iterator<Map.Entry<BlockPos, Entry>> it = entries.entrySet().iterator();
        Set<BlockPos> toUnhide = null;
        while (it.hasNext()) {
            Entry e = it.next().getValue();
            e.prevFade = e.fade;
            if (e.fade < e.fadeTarget) {
                e.fade = Math.min(e.fadeTarget, e.fade + FADE_PER_TICK);
            } else if (e.fade > e.fadeTarget) {
                e.fade = Math.max(e.fadeTarget, e.fade - FADE_PER_TICK);
            }
            if (e.fade <= 0f && e.fadeTarget <= 0f) {
                if (toUnhide == null) toUnhide = new HashSet<>();
                for (BlockPos p : e.portalBlocks) {
                    if (hiddenPositions.remove(p)) toUnhide.add(p);
                }
                it.remove();
            }
        }
        if (toUnhide != null && !toUnhide.isEmpty()) markSectionsDirty(toUnhide);
    }

    public static Iterable<Entry> activeEntries() {
        return entries.values();
    }

    /** Off-thread safe lookup used by the {@code BlockRenderDispatcher} mixin. */
    public static boolean shouldHide(BlockPos pos) {
        return hiddenPositions.contains(pos);
    }

    private static void markSectionsDirty(Set<BlockPos> positions) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        LevelRenderer renderer = mc.levelRenderer;
        Set<Long> seen = new HashSet<>();
        for (BlockPos pos : positions) {
            int sx = SectionPos.blockToSectionCoord(pos.getX());
            int sy = SectionPos.blockToSectionCoord(pos.getY());
            int sz = SectionPos.blockToSectionCoord(pos.getZ());
            if (seen.add(SectionPos.asLong(sx, sy, sz))) {
                renderer.setSectionDirty(sx, sy, sz);
            }
        }
    }

    public static final class Entry {
        public List<BlockPos> portalBlocks = List.of();
        public float fadeTarget = 0f;
        public float fade = 0f;
        public float prevFade = 0f;

        public float renderFade(float partialTick) {
            return Mth.lerp(partialTick, prevFade, fade);
        }
    }
}
