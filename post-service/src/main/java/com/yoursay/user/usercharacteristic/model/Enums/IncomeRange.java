package com.yoursay.user.usercharacteristic.model.Enums;

/**
 * Annual income band. Values mirror the frontend {@code INCOME_OPTIONS} exactly. The 20k–50k and
 * 50k–100k bands are split for finer resolution; legacy values are kept at the end so previously
 * stored answers parse (an old {@code BETWEEN_20K_AND_50K} cannot be accurately re-split).
 */
public enum IncomeRange {
    BELOW_20K,
    BETWEEN_20K_AND_30K,
    BETWEEN_30K_AND_40K,
    BETWEEN_40K_AND_50K,
    BETWEEN_50K_AND_75K,
    BETWEEN_75K_AND_100K,
    BETWEEN_100K_AND_150K,
    BETWEEN_150K_AND_200K,
    BETWEEN_200K_AND_500K,
    BETWEEN_500K_AND_1000K,
    ABOVE_1000000,
    // --- legacy (no longer offered) ---
    @Deprecated
    BETWEEN_20K_AND_50K,
    @Deprecated
    BETWEEN_151K_AND_200K
}
