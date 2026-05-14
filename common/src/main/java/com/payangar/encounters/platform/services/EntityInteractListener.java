package com.payangar.encounters.platform.services;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Listener invoked when a player right-clicks an entity. Implemented in
 * common code; wired to {@code UseEntityCallback} on Fabric and to
 * {@code PlayerInteractEvent.EntityInteract} on NeoForge by
 * {@link IPlatformHelper#registerEntityInteractListener}.
 *
 * <p>Return {@link InteractionResult#PASS} to let vanilla handle the
 * interaction normally; any other result cancels vanilla and is propagated
 * back to the platform event as the overall outcome.</p>
 */
@FunctionalInterface
public interface EntityInteractListener {
    InteractionResult onInteract(Player player, Entity entity, InteractionHand hand);
}
