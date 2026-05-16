package com.payangar.encounters.event;

import com.payangar.encounters.platform.Services;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Walks an NBT tree looking for {@code "modid:path"} references whose
 * declaring mod is not loaded. Used to silently filter out spawn pool
 * entries that depend on optional mods.
 */
public final class NbtModFilter {

    private NbtModFilter() {}

    /**
     * @return the first {@code "modid:path"} string encountered whose mod is
     *         not present (as a debug hint for logs), or {@code null} if the
     *         entire tree only references loaded mods and vanilla resources.
     */
    public static String findMissingModRef(Tag tag) {
        if (tag == null) return null;
        return walk(tag);
    }

    private static String walk(Tag tag) {
        if (tag instanceof CompoundTag ct) {
            for (String key : ct.getAllKeys()) {
                String miss = checkString(key);
                if (miss != null) return miss;
                miss = walk(ct.get(key));
                if (miss != null) return miss;
            }
        } else if (tag instanceof ListTag lt) {
            for (Tag child : lt) {
                String miss = walk(child);
                if (miss != null) return miss;
            }
        } else if (tag instanceof StringTag st) {
            return checkString(st.getAsString());
        }
        return null;
    }

    private static String checkString(String value) {
        if (value == null || value.indexOf(':') < 0) return null;
        ResourceLocation rl = ResourceLocation.tryParse(value);
        if (rl == null) return null;
        String namespace = rl.getNamespace();
        if ("minecraft".equals(namespace)) return null;
        if (Services.PLATFORM.isModLoaded(namespace)) return null;
        return value;
    }
}
