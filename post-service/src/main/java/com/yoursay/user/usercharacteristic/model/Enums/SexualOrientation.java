package com.yoursay.user.usercharacteristic.model.Enums;

/**
 * Sexual orientation. {@code GAY_LESBIAN} replaces the dated {@code HOMOSEXUAL} enum name (see the
 * identity-demographics reform). Legacy values are kept at the end so previously stored answers still
 * parse; onboarding only offers the current set.
 */
public enum SexualOrientation {
    STRAIGHT_HETEROSEXUAL,
    GAY_LESBIAN,
    BISEXUAL,
    PANSEXUAL,
    ASEXUAL,
    QUEER,
    QUESTIONING,
    SELF_DESCRIBE,
    // --- legacy (no longer offered) ---
    @Deprecated
    HETEROSEXUAL,
    @Deprecated
    HOMOSEXUAL,
    @Deprecated
    OTHER
}
