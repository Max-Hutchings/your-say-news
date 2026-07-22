package com.yoursay.user.usercharacteristic.model.Enums;

/**
 * Age reporting band — a derived, non-identifying bucket computed from the user's age, which itself
 * comes from the stored {@code birthYear} (see ADR-017). We never store a band or an exact DOB; the
 * band is recomputed on read so it stays correct every year. Used only as an aggregate reporting axis.
 */
public enum AgeRange {
    AGE_16_17,
    AGE_18_19,
    AGE_20_24,
    AGE_25_34,
    AGE_35_44,
    AGE_45_54,
    AGE_55_64,
    AGE_65_74,
    AGE_75_84,
    AGE_85_PLUS;

    /** Maps a computed age (years) to its reporting band. Ages below 16 are rejected upstream. */
    public static AgeRange fromAge(int age) {
        if (age < 18) return AGE_16_17;
        if (age < 20) return AGE_18_19;
        if (age < 25) return AGE_20_24;
        if (age < 35) return AGE_25_34;
        if (age < 45) return AGE_35_44;
        if (age < 55) return AGE_45_54;
        if (age < 65) return AGE_55_64;
        if (age < 75) return AGE_65_74;
        if (age < 85) return AGE_75_84;
        return AGE_85_PLUS;
    }
}
