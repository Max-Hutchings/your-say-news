package com.yoursay.usercharacteristic.model.Enums;

/**
 * Highest level of education — internationally recognisable levels (no country-specific
 * GCSE/A-level/T-level wording). Higher-education levels gate the optional university-subject
 * question. Legacy values are kept so previously stored answers parse.
 */
public enum EducationLevel {
    NO_FORMAL_QUALIFICATIONS,
    PRIMARY_SCHOOLING,
    SECONDARY_SCHOOL,
    VOCATIONAL_TECHNICAL,
    HIGHER_EDUCATION_BELOW_DEGREE,
    BACHELORS,
    MASTERS,
    DOCTORATE,
    OTHER,
    NOT_SURE,
    // --- legacy (no longer offered) ---
    @Deprecated
    NO_FORMAL_EDUCATION,
    @Deprecated
    HIGH_SCHOOL
}
