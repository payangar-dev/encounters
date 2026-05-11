package com.payangar.encounters.platform;

import com.payangar.encounters.Constants;
import com.payangar.encounters.platform.services.INetworkBridge;
import com.payangar.encounters.platform.services.IPlatformHelper;
import com.payangar.encounters.platform.services.ISpellCompat;

import java.util.ServiceLoader;

public final class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    public static final ISpellCompat SPELLS = load(ISpellCompat.class);
    public static final INetworkBridge NETWORK = load(INetworkBridge.class);

    private static <T> T load(Class<T> clazz) {
        T service = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Constants.LOG.debug("Loaded {} for service {}", service, clazz);
        return service;
    }

    private Services() {}
}
