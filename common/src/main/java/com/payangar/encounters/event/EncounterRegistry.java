package com.payangar.encounters.event;

import com.payangar.encounters.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * Process-wide registry of encounter events. Each coordinator registers itself
 * at boot ({@code Encounters#init}) so cross-event operations can iterate the
 * list once instead of duplicating call sites.
 *
 * <p>Currently the only such operation is roster invalidation on config
 * load/save — hence the name. Add more lifecycle hooks here when a future
 * event needs them rather than threading new method calls through every
 * coordinator.</p>
 */
public final class EncounterRegistry {

    public record Entry(String id, Runnable invalidate) {}

    private static final List<Entry> EVENTS = new ArrayList<>();

    private EncounterRegistry() {}

    /** Register an event with its roster-invalidation callback. Idempotent on the {@code id}. */
    public static synchronized void register(String id, Runnable invalidate) {
        for (Entry e : EVENTS) {
            if (e.id.equals(id)) return;
        }
        EVENTS.add(new Entry(id, invalidate));
    }

    /** Calls every registered invalidator. Exceptions are caught and logged so one bad event doesn't break the others. */
    public static void invalidateAll() {
        for (Entry e : EVENTS) {
            try {
                e.invalidate.run();
            } catch (Throwable t) {
                Constants.LOG.warn("[{}] roster invalidation failed", e.id, t);
            }
        }
    }
}
