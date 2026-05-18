package com.payangar.encounters.event.pool;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * A datapack-loaded list of {@link SpawnEntry} values, identified by a
 * {@link net.minecraft.resources.ResourceLocation} that mirrors its file
 * path (e.g. {@code encounters:lightning_overcharge} loads from
 * {@code data/encounters/spawn_pools/lightning_overcharge.json}).
 *
 * <p>Each event class owns one or more pool ids and resolves them through
 * {@code EncounterPoolsManager} at runtime; resolution is delegated to
 * {@link com.payangar.encounters.event.MobRoster} which keeps the weight /
 * mod-presence filtering logic in one place.</p>
 *
 * <p>Pools live as datapack JSON instead of inline SNBT-string inside the
 * config: this yields hot-reload via {@code /reload}, third-party overrides
 * via datapacks, and a JSON layout free of any write-time escape bugs in
 * config-file parsers.</p>
 */
public record SpawnPool(List<SpawnEntry> entries) {

    public static final SpawnPool EMPTY = new SpawnPool(List.of());

    public static final Codec<SpawnPool> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SpawnEntry.CODEC.listOf().fieldOf("entries").forGetter(SpawnPool::entries)
    ).apply(instance, SpawnPool::new));

    public SpawnPool {
        // Defensive copy so a caller can't mutate the pool after construction.
        // List.copyOf preserves order and rejects nulls, exactly what we want.
        entries = List.copyOf(entries);
    }
}
