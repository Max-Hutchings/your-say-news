package com.yoursay.usercharacteristic.model.Enums;

/**
 * Current work or study status. Legacy values are kept at the end so previously stored answers parse;
 * onboarding offers the current set.
 */
public enum OccupationStatus {
    STUDENT,
    EMPLOYED_FULL_TIME,
    EMPLOYED_PART_TIME,
    WORKING_AND_STUDYING,
    SELF_EMPLOYED,
    CASUAL_GIG_TEMP,
    UNEMPLOYED_LOOKING,
    NOT_WORKING_NOT_LOOKING,
    CARER_HOMEMAKER,
    UNABLE_TO_WORK_HEALTH,
    RETIRED,
    // --- legacy (no longer offered) ---
    @Deprecated
    EMPLOYED_AND_STUDYING,
    @Deprecated
    UNEMPLOYED
}
