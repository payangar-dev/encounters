package com.payangar.encounters.event;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.WeightedMob;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.entity.EntityType;

import java.util.List;
import java.util.Optional;

public final class MobRoster {

    private final SimpleWeightedRandomList<ResolvedMob> pool;
    private final int size;

    private MobRoster(SimpleWeightedRandomList<ResolvedMob> pool, int size) {
        this.pool = pool;
        this.size = size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    public Optional<ResolvedMob> pick(RandomSource rng) {
        return pool.getRandomValue(rng);
    }

    public static MobRoster resolve(List<WeightedMob> raw, String eventId) {
        SimpleWeightedRandomList.Builder<ResolvedMob> builder = SimpleWeightedRandomList.builder();
        int kept = 0;
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

                ResolvedMob resolved = new ResolvedMob(id, maybeType.get(), nbt, entry.label);
                builder.add(resolved, entry.weight);
                kept++;
            }
        }

        if (kept == 0) {
            Constants.LOG.warn("[{}] no valid mob entries after filtering — event will be disabled (kept=0, filtered={})",
                    eventId, filtered);
        } else if (filtered > 0) {
            Constants.LOG.info("[{}] roster resolved: {} kept, {} filtered (missing mods)", eventId, kept, filtered);
        } else {
            Constants.LOG.info("[{}] roster resolved: {} valid entries", eventId, kept);
        }

        return new MobRoster(builder.build(), kept);
    }
}
