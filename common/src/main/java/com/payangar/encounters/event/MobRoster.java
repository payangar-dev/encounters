package com.payangar.encounters.event;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.WeightedMob;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

public final class MobRoster {

    public record Entry(ResolvedMob mob, int weight) {}

    private final List<Entry> entries;
    private final int totalBaseWeight;
    private final int maxBaseWeight;

    private MobRoster(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        int total = 0;
        int max = 0;
        for (Entry e : entries) {
            total += e.weight;
            if (e.weight > max) max = e.weight;
        }
        this.totalBaseWeight = total;
        this.maxBaseWeight = max;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /** Highest base weight in the pool — useful to build per-pick weight transforms. */
    public int maxBaseWeight() {
        return maxBaseWeight;
    }

    public Optional<ResolvedMob> pick(RandomSource rng) {
        if (totalBaseWeight <= 0) return Optional.empty();
        int roll = rng.nextInt(totalBaseWeight);
        int acc = 0;
        for (Entry e : entries) {
            acc += e.weight;
            if (roll < acc) return Optional.of(e.mob());
        }
        return Optional.empty();
    }

    /**
     * Picks a mob with weights remapped per-pick by {@code weightTransform}.
     * Negative outputs are clamped to 0; if every transformed weight is zero
     * the result is empty. Use this for callers that want to bias the
     * distribution dynamically (e.g. wave-based rarity scaling).
     */
    public Optional<ResolvedMob> pick(RandomSource rng, IntUnaryOperator weightTransform) {
        if (entries.isEmpty()) return Optional.empty();
        int[] adjusted = new int[entries.size()];
        int total = 0;
        for (int i = 0; i < entries.size(); i++) {
            int w = Math.max(0, weightTransform.applyAsInt(entries.get(i).weight()));
            adjusted[i] = w;
            total += w;
        }
        if (total <= 0) return Optional.empty();
        int roll = rng.nextInt(total);
        int acc = 0;
        for (int i = 0; i < adjusted.length; i++) {
            acc += adjusted[i];
            if (roll < acc) return Optional.of(entries.get(i).mob());
        }
        return Optional.empty();
    }

    public static MobRoster resolve(List<WeightedMob> raw, String eventId) {
        List<Entry> resolved = new ArrayList<>();
        int filtered = 0;

        if (raw != null) {
            for (int i = 0; i < raw.size(); i++) {
                WeightedMob entry = raw.get(i);
                if (entry == null) continue;

                if (entry.weight <= 0) {
                    Constants.LOG.debug("[{}] entry #{} '{}' skipped: weight={} (must be > 0)",
                            eventId, i, entry.id, entry.weight);
                    continue;
                }

                ResourceLocation id = ResourceLocation.tryParse(entry.id);
                if (id == null) {
                    Constants.LOG.warn("[{}] entry #{} skipped: invalid id '{}'",
                            eventId, i, entry.id);
                    continue;
                }

                Optional<EntityType<?>> maybeType = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
                if (maybeType.isEmpty()) {
                    Constants.LOG.debug("[{}] entry #{} skipped: entity type '{}' not registered (mod missing?)",
                            eventId, i, id);
                    filtered++;
                    continue;
                }

                CompoundTag nbt;
                if (entry.nbt != null && !entry.nbt.isBlank()) {
                    try {
                        nbt = TagParser.parseTag(entry.nbt);
                    } catch (Exception e) {
                        Constants.LOG.warn("[{}] entry #{} '{}' skipped: malformed SNBT ({})",
                                eventId, i, id, e.getMessage());
                        continue;
                    }
                } else {
                    nbt = new CompoundTag();
                }

                String missingRef = NbtModFilter.findMissingModRef(nbt);
                if (missingRef != null) {
                    Constants.LOG.debug("[{}] entry #{} '{}' skipped: NBT references unknown mod resource '{}'",
                            eventId, i, id, missingRef);
                    filtered++;
                    continue;
                }

                ResolvedMob resolvedMob = new ResolvedMob(id, maybeType.get(), nbt, entry.label);
                resolved.add(new Entry(resolvedMob, entry.weight));
            }
        }

        int kept = resolved.size();
        if (kept == 0) {
            Constants.LOG.warn("[{}] no valid mob entries after filtering — event will be disabled (kept=0, filtered={})",
                    eventId, filtered);
        } else if (filtered > 0) {
            Constants.LOG.info("[{}] roster resolved: {} kept, {} filtered (missing mods)", eventId, kept, filtered);
        } else {
            Constants.LOG.info("[{}] roster resolved: {} valid entries", eventId, kept);
        }

        return new MobRoster(resolved);
    }
}
