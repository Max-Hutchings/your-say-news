package com.yoursay.usercharacteristic.model.Enums;

/**
 * Relationship status — the social-context axis for opinion breakdowns (distinct from a purely legal
 * marital classification). Legacy values are kept at the end so previously stored answers parse.
 */
public enum MaritalStatus {
    SINGLE,
    IN_RELATIONSHIP,
    COHABITING,
    MARRIED,
    CIVIL_PARTNERSHIP,
    SEPARATED,
    DIVORCED_OR_DISSOLVED,
    WIDOWED_OR_SURVIVING_PARTNER,
    // --- legacy (no longer offered) ---
    @Deprecated
    DIVORCED,
    @Deprecated
    WIDOWED
}
