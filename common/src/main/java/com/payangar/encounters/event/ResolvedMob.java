package com.payangar.encounters.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public final class ResolvedMob {

    private final ResourceLocation id;
    private final EntityType<?> type;
    private final CompoundTag nbt;
    private final String label;

    public ResolvedMob(ResourceLocation id, EntityType<?> type, CompoundTag nbt, String label) {
        this.id = id;
        this.type = type;
        this.nbt = nbt;
        this.label = label;
    }

    public ResourceLocation id() {
        return id;
    }

    public EntityType<?> type() {
        return type;
    }

    public CompoundTag nbtCopy() {
        return nbt.copy();
    }

    public String displayName() {
        return label != null && !label.isBlank() ? label : id.toString();
    }
}
