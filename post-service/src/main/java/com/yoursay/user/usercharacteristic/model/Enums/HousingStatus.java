package com.yoursay.user.usercharacteristic.model.Enums;

/**
 * A user's housing tenure — a non-identifying bucket. Temporary / no-fixed-address is treated as
 * sensitive downstream (small buckets suppressed). Legacy values are kept so prior answers parse.
 */
public enum HousingStatus {
    OWN_OUTRIGHT,
    OWN_MORTGAGE,
    SHARED_OWNERSHIP,
    PRIVATE_RENT,
    SOCIAL_RENT,
    LIVE_WITH_FAMILY,
    RENT_FREE,
    STUDENT_ACCOMMODATION,
    TEMPORARY_NO_FIXED,
    OTHER,
    // --- legacy (no longer offered) ---
    @Deprecated
    LIVE_WITH_PARENTS,
    @Deprecated
    RENT,
    @Deprecated
    OWN
}
