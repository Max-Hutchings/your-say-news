package com.yoursay.usercharacteristic.model.Enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Parent / caregiver status — a directly chosen answer (no longer inferred as MUM/DAD from sex at
 * birth). Legacy values are kept at the end so previously stored answers parse.
 */
public enum Parent {
    NOT_PARENT_CAREGIVER,
    PARENT_CAREGIVER_UNDER_18,
    PARENT_CAREGIVER_ADULT_ONLY,
    EXPECTING,
    // --- legacy (no longer offered) ---
    @Deprecated
    MUM,
    @Deprecated
    DAD,
    @Deprecated
    NO;

    @JsonCreator
    public Parent fromValue(String value){
        return Parent.valueOf(value.toUpperCase());
    }
}
