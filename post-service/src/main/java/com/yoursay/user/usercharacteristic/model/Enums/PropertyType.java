package com.yoursay.user.usercharacteristic.model.Enums;

/**
 * Type of home a user lives in — asked of everyone with a current home, not only owners. Legacy
 * values are kept at the end so previously stored answers parse.
 */
public enum PropertyType {
    DETACHED,
    SEMI_DETACHED,
    TERRACED,
    FLAT_APARTMENT,
    ROOM_SHARED_HOUSE,
    STUDENT_HALLS,
    MOBILE_TEMPORARY,
    OTHER_UNKNOWN,
    // --- legacy (no longer offered) ---
    @Deprecated
    HOUSE,
    @Deprecated
    FLAT
}
