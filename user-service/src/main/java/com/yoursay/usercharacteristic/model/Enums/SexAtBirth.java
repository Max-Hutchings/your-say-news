package com.yoursay.usercharacteristic.model.Enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SexAtBirth {
    MALE,
    FEMALE;

    @JsonCreator
    public SexAtBirth fromValue(String value){
        return SexAtBirth.valueOf(value.toUpperCase());
    }
}
