package com.payangar.encounters.event.pool;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * One entry of a {@link SpawnPool}: a weighted reference to an entity type,
 * optionally enriched with custom NBT (applied via
 * {@code EntityType#loadEntityRecursive}) and a display label.
 *
 * <p>Loaded from {@code data/<ns>/encounters/spawn_pools/<pool>.json} where
 * each entry looks like:</p>
 *
 * <pre>{@code
 * { "id": "minecraft:skeleton", "weight": 10,
 *   "label": "Stormcaller",
 *   "nbt": { "Health": 28.0, "HandItems": [ { "id": "minecraft:bow", "count": 1 }, {} ] } }
 * }</pre>
 *
 * <p>The {@code nbt} block is plain JSON, decoded into a {@link CompoundTag}
 * by {@link CompoundTag#CODEC}. No SNBT string is ever stored, which avoids
 * the {@code quilt-parsers 0.2.x} escape bug that would otherwise corrupt
 * the file at every save.</p>
 */
public record SpawnEntry(
        ResourceLocation id,
        int weight,
        Optional<CompoundTag> nbt,
        Optional<String> label
) {

    public static final Codec<SpawnEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(SpawnEntry::id),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(SpawnEntry::weight),
            CompoundTag.CODEC.optionalFieldOf("nbt").forGetter(SpawnEntry::nbt),
            Codec.STRING.optionalFieldOf("label").forGetter(SpawnEntry::label)
    ).apply(instance, SpawnEntry::new));

    /**
     * Returns the entry's NBT, or a fresh empty {@link CompoundTag} when none
     * was provided. Allocates on each call rather than sharing a sentinel —
     * cheaper than copy-on-read and avoids ever leaking a mutable instance
     * shared between entries.
     */
    public CompoundTag nbtOrEmpty() {
        return nbt.orElseGet(CompoundTag::new);
    }
}
