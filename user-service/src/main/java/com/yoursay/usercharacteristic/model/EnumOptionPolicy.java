package com.yoursay.usercharacteristic.model;

/** Distinguishes current onboarding choices from deprecated constants retained for historical rows. */
public final class EnumOptionPolicy {

    private EnumOptionPolicy() {
    }

    public static boolean isOffered(Enum<?> value) {
        try {
            return !value.getDeclaringClass()
                    .getField(value.name())
                    .isAnnotationPresent(Deprecated.class);
        } catch (NoSuchFieldException impossible) {
            throw new IllegalStateException("Enum constant field is missing: " + value, impossible);
        }
    }
}
