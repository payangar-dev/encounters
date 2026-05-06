package com.payangar.encounters.event.ally;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Allied-group bookkeeping for encounter mobs. Each group of mobs spawned
 * by a single event shares one {@code encounters_group_<shortId>} tag,
 * stored in the entity's vanilla tag set (NBT-persisted, no scoreboard
 * footprint). The {@link #areAllies} predicate is consulted by the
 * {@code Mob#setTarget} mixin to suppress any targeting between tagged
 * peers — blocking {@code HurtByTargetGoal} retaliation and any other
 * target goal from locking onto a group mate.
 *
 * <p>Groups stay separated by id on purpose: two concurrent encounters
 * remain hostile to each other, preserving emergent cross-group fights.</p>
 */
public final class EncounterAllies {

    public static final String TAG_PREFIX = "encounters_group_";

    private EncounterAllies() {}

    /** Mints a fresh group tag, ready to be applied to one or more mobs. */
    public static String newGroupTag() {
        return TAG_PREFIX + UUID.randomUUID().toString().substring(0, 8);
    }

    /** True when {@code entity} carries any encounter-group tag — i.e. was spawned by an event. */
    public static boolean isEncounterMob(Entity entity) {
        if (entity == null) return false;
        for (String tag : entity.getTags()) {
            if (tag.startsWith(TAG_PREFIX)) return true;
        }
        return false;
    }

    /**
     * Stamps every member of the collection with a fresh shared group tag.
     * Called once per spawned encounter, after all mobs have been added.
     */
    public static void tagGroup(Collection<? extends Mob> members) {
        if (members.isEmpty()) return;
        String tag = newGroupTag();
        for (Mob m : members) {
            m.addTag(tag);
        }
    }

    /**
     * True when both entities carry the same encounter group tag. Returns
     * false for any non-encounter entity (players, wild mobs) since they
     * carry no such tag.
     */
    public static boolean areAllies(Entity a, Entity b) {
        if (a == null || b == null || a == b) return false;
        Set<String> tagsA = a.getTags();
        if (tagsA.isEmpty()) return false;
        Set<String> tagsB = b.getTags();
        if (tagsB.isEmpty()) return false;
        for (String tag : tagsA) {
            if (tag.startsWith(TAG_PREFIX) && tagsB.contains(tag)) return true;
        }
        return false;
    }
}
