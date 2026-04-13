package com.payangar.encounters.compat.epicfight;

import com.payangar.encounters.Constants;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

/**
 * Runtime bridge that lets a pseudo mixin multiply an EpicFight
 * {@code OpenMatrix4f} by the vanilla {@code minecraft:generic.scale}
 * attribute without compile-time access to EpicFight classes.
 * Only loaded when the mixin fires, i.e. when EpicFight is present.
 */
public final class EpicFightScaleBridge {

    private static volatile boolean initialized = false;
    private static volatile boolean unavailable = false;
    private static Field originalField;
    private static MethodHandle scaleHandle;

    private EpicFightScaleBridge() {}

    public static Object applyVanillaScale(Object patchInstance, Object matrix) {
        if (unavailable || matrix == null || patchInstance == null) return matrix;
        if (!initialized) init(patchInstance.getClass(), matrix.getClass());
        if (unavailable) return matrix;

        try {
            Object original = originalField.get(patchInstance);
            if (!(original instanceof LivingEntity living)) return matrix;

            AttributeInstance attr = living.getAttribute(Attributes.SCALE);
            if (attr == null) return matrix;

            double scale = attr.getValue();
            if (scale == 1.0D) return matrix;

            float s = (float) scale;
            return scaleHandle.invoke(matrix, s, s, s);
        } catch (Throwable t) {
            Constants.LOG.warn("[encounters/epicfight] Failed to apply scale, disabling compat", t);
            unavailable = true;
            return matrix;
        }
    }

    private static synchronized void init(Class<?> patchClass, Class<?> matrixClass) {
        if (initialized) return;
        try {
            Field field = findInheritedField(patchClass, "original");
            if (field == null) {
                Constants.LOG.warn("[encounters/epicfight] EntityPatch#original field not found, disabling compat");
                unavailable = true;
                initialized = true;
                return;
            }
            field.setAccessible(true);
            originalField = field;

            scaleHandle = MethodHandles.publicLookup().findVirtual(
                matrixClass,
                "scale",
                MethodType.methodType(matrixClass, float.class, float.class, float.class)
            );
            initialized = true;
            Constants.LOG.info("[encounters/epicfight] Scale compat initialized ({} / {})",
                patchClass.getName(), matrixClass.getName());
        } catch (Throwable t) {
            Constants.LOG.warn("[encounters/epicfight] Failed to init scale compat, disabling", t);
            unavailable = true;
            initialized = true;
        }
    }

    private static Field findInheritedField(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
