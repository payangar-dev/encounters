package com.payangar.encounters;

import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.event.ActiveEncounterTracker;
import com.payangar.encounters.event.EncounterRegistry;
import com.payangar.encounters.event.LightningOverchargeEvent;
import com.payangar.encounters.event.cinematic.CinematicTicker;
import com.payangar.encounters.event.cohesion.GroupCohesionTicker;
import com.payangar.encounters.event.portal.NetherPortalInvasionEvent;
import com.payangar.encounters.event.portal.PortalScanner;
import com.payangar.encounters.event.skirmish.PatrolSkirmishEvent;
import com.payangar.encounters.event.skirmish.ReputationHook;
import com.payangar.encounters.event.skirmish.SkirmishScanner;
import com.payangar.encounters.platform.Services;

public final class Encounters {

    public static void init() {
        Constants.LOG.info("Initializing {} common bootstrap", Constants.MOD_NAME);
        registerEvents();
        EncountersConfig.load();
        CinematicTicker.initialize();
        GroupCohesionTicker.initialize();
        PortalScanner.initialize();
        SkirmishScanner.initialize();
        ReputationHook.install();
        registerLifecycleHooks();
    }

    private static void registerEvents() {
        EncounterRegistry.register(LightningOverchargeEvent.ID, LightningOverchargeEvent::invalidateRoster);
        EncounterRegistry.register(NetherPortalInvasionEvent.ID, NetherPortalInvasionEvent::invalidateRoster);
        EncounterRegistry.register(PatrolSkirmishEvent.ID, PatrolSkirmishEvent::invalidateRoster);
    }

    private static void registerLifecycleHooks() {
        Services.PLATFORM.registerServerStoppingListener(server -> {
            CinematicTicker.clear();
            GroupCohesionTicker.clear();
            ActiveEncounterTracker.clear();
            NetherPortalInvasionEvent.releaseAll();
            PatrolSkirmishEvent.releaseAll();
            SkirmishScanner.clear();
        });
        Services.PLATFORM.registerServerLevelUnloadListener(level -> {
            CinematicTicker.clearLevel(level);
            GroupCohesionTicker.clearLevel(level);
            ActiveEncounterTracker.clearLevel(level);
            NetherPortalInvasionEvent.releaseLevel(level);
            SkirmishScanner.clearLevel(level);
        });
    }

    private Encounters() {}
}
