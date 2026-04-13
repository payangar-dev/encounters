package com.payangar.encounters.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public final class NeoForgeLightningListener {

    private NeoForgeLightningListener() {}

    public static void register(IEventBus gameEventBus) {
        gameEventBus.addListener(NeoForgeLightningListener::onEntityJoin);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LightningBolt bolt)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (LightningOverchargeEvent.onLightningSpawn(serverLevel, bolt)) {
            event.setCanceled(true);
        }
    }
}
