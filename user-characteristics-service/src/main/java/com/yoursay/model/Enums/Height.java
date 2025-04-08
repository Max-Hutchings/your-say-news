package com.yoursay.model.Enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Height {
    FEET_4_0_TO_4_4,
    FEET_4_5_TO_4_9,
    FEET_4_10_TO_5_0,
    FEET_5_1_TO_5_3,
    FEET_5_4_TO_5_6,
    FEET_5_7_TO_5_9,
    FEET_5_10_TO_6_0,
    FEET_6_1_TO_6_3,
    FEET_6_4_TO_6_6,
    FEET_6_7_TO_6_9,
    FEET_6_10_TO_7_0,
    FEET_7_1_PLUS;

    @JsonCreator
    public Height fromValue(String value){
        return Height.valueOf(value.toUpperCase());
    }
}
