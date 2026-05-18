package com.payangar.encounters.event.portal.internal;

import com.payangar.encounters.Constants;
import com.payangar.encounters.config.EncountersConfig;
import com.payangar.encounters.event.ally.EncounterAllies;
import com.payangar.encounters.event.portal.NetherPortalInvasionEvent;
import com.payangar.encounters.platform.Services;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Iron's Spells {@code MagmaBombSpell} caster. Spawns an invisible armor stand
 * at the portal mouth and casts a magma bomb every {@link #INTERVAL_TICKS}
 * with random yaw/pitch jitter. No-op when Iron's Spells is absent or the
 * config toggle is off.
 *
 * <p>The caster is added to the invasion's scoreboard team so Iron's Spells'
 * {@code DamageSources#isFriendlyFireBetween} skips damaging invasion mobs
 * when the bomb lands.</p>
 */
public final class MagmaBombCaster {

    /** Interval (ticks) between two casts. */
    public static final int INTERVAL_TICKS = 80;
    /** Spell level used during wave 1 — each subsequent wave adds +1 up to {@link #MAX_LEVEL}. */
    private static final int START_LEVEL = 2;
    /** Yaw deviation from the spawn face (degrees, both directions). */
    private static final float YAW_JITTER_DEG = 30f;
    /** Lower bound (degrees) of the upward pitch — higher value = closer landings. */
    private static final float PITCH_MIN_DEG = 35f;
    /** Upper bound (degrees) of the upward pitch — steepest, shortest lobs. */
    private static final float PITCH_MAX_DEG = 70f;
    /** Hard cap — Iron's Spells' MagmaBombSpell tops at 8. */
    private static final int MAX_LEVEL = 8;
    /** Vertical offset of the caster relative to the portal anchor (mid-portal). */
    private static final double CASTER_Y_OFFSET = 1.5;

    private final ServerLevel level;
    private final Vec3 anchor;
    private final Direction spawnFace;
    private final String groupName;

    private Entity caster;
    private boolean attempted;

    public MagmaBombCaster(ServerLevel level, Vec3 anchor, Direction spawnFace, String groupName) {
        this.level = level;
        this.anchor = anchor;
        this.spawnFace = spawnFace;
        this.groupName = groupName;
    }

    /**
     * Once per tick. Runs the cast cadence if both the config toggle and
     * Iron's Spells presence allow it. {@code currentWave} drives spell-level
     * scaling.
     */
    public void tick(int currentWave) {
        if (!EncountersConfig.get().portal.magmaBombEnabled) return;
        if (!Services.SPELLS.isAvailable()) return;

        ensureCaster();
        if (!(caster instanceof LivingEntity living)) return;

        if (level.getGameTime() % INTERVAL_TICKS != 0) return;
        cast(living, currentWave);
    }

    /** Discards the caster armor stand, idempotent. */
    public void discard() {
        if (caster != null) {
            caster.discard();
            caster = null;
        }
    }

    private void ensureCaster() {
        if (attempted) return;
        attempted = true;

        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:armor_stand");
        tag.putBoolean("Invisible", true);
        tag.putBoolean("Invulnerable", true);
        tag.putBoolean("Marker", true);
        tag.putBoolean("NoBasePlate", true);
        tag.putBoolean("Silent", true);
        tag.putBoolean("NoGravity", true);

        Vec3 pos = anchor.add(0, CASTER_Y_OFFSET, 0);
        Entity stand = EntityType.loadEntityRecursive(tag, level, e -> {
            e.moveTo(pos.x, pos.y, pos.z, 0f, 0f);
            return e;
        });
        if (stand == null) {
            Constants.LOG.warn("[{}] failed to spawn magma bomb caster", NetherPortalInvasionEvent.ID);
            return;
        }
        stand.addTag("encounters_cinematic_caster");
        level.addFreshEntity(stand);
        this.caster = stand;
        EncounterAllies.addToGroup(level, groupName, stand);
    }

    private void cast(LivingEntity caster, int currentWave) {
        RandomSource rng = level.getRandom();
        float baseYaw = spawnFace.toYRot();
        float yawJitter = (rng.nextFloat() - 0.5f) * 2f * YAW_JITTER_DEG;
        float pitch = -(PITCH_MIN_DEG + rng.nextFloat() * (PITCH_MAX_DEG - PITCH_MIN_DEG));

        caster.setYRot(baseYaw + yawJitter);
        caster.setXRot(pitch);

        int wave = Math.max(1, currentWave);
        int spellLevel = Math.min(MAX_LEVEL, START_LEVEL + (wave - 1));
        Services.SPELLS.castMagmaBomb(level, caster, spellLevel);
    }
}
