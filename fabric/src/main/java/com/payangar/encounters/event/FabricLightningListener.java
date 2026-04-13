package com.payangar.encounters.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.LightningBolt;

public final class FabricLightningListener {

    private FabricLightningListener() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof LightningBolt bolt) {
                if (LightningOverchargeEvent.onLightningSpawn(level, bolt)) {
                    // ENTITY_LOAD is not cancellable on Fabric — remove the bolt
                    // instead. Our replacement bolt is scheduled for next tick.
                    bolt.discard();
                }
            }
        });
    }
}
