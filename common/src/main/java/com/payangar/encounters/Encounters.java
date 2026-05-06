package com.payangar.encounters;

import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.event.cinematic.CinematicTicker;
import com.payangar.encounters.event.cohesion.GroupCohesionTicker;
import com.payangar.encounters.event.portal.PortalScanner;

public final class Encounters {

    public static void init() {
        Constants.LOG.info("Initializing {} common bootstrap", Constants.MOD_NAME);
        EncountersConfig.load();
        CinematicTicker.initialize();
        GroupCohesionTicker.initialize();
        PortalScanner.initialize();
    }

    private Encounters() {}
}
