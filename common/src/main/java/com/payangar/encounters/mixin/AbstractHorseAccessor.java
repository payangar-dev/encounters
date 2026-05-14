package com.payangar.encounters.mixin;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor exposing {@link AbstractHorse}'s protected {@code inventory}
 * field. Vanilla offers no public getter for the chest container of a
 * {@code AbstractChestedHorse}, so the caravan spawner needs this hook to
 * pre-populate the pack animal's chest at spawn time.
 */
@Mixin(AbstractHorse.class)
public interface AbstractHorseAccessor {

    @Accessor("inventory")
    SimpleContainer encounters$getInventory();
}
