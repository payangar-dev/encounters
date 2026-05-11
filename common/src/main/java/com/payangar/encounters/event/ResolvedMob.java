package com.payangar.encounters.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public record ResolvedMob(ResourceLocation id, EntityType<?> type, CompoundTag nbt, String label) {

    public CompoundTag nbtCopy() {
        return nbt.copy();
    }

    public String displayName() {
        return label != null && !label.isBlank() ? label : id.toString();
    }
}
