package com.yoursay.user.usercharacteristic.model.Enums;

/**
 * Ethnic background — broad, globally recognisable groups that people in any country can identify
 * with, rather than one nation's census subcategories. Kept multi-select (see the entity's race
 * collection) because mixed and multiple backgrounds are realistic. The type name stays {@code Race}
 * for continuity with the stored column and vote axis; the product surface calls it "Ethnic background".
 */
public enum Race {
    WHITE_EUROPEAN,
    BLACK_AFRICAN,
    EAST_ASIAN,
    SOUTH_ASIAN,
    SOUTHEAST_ASIAN,
    MIDDLE_EASTERN_NORTH_AFRICAN,
    HISPANIC_LATINO,
    INDIGENOUS,
    PACIFIC_ISLANDER,
    MIXED_MULTIPLE,
    OTHER_ETHNIC_GROUP,
    SELF_DESCRIBE
}
