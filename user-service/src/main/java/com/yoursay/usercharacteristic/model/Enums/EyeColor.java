package com.yoursay.usercharacteristic.model.Enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EyeColor {
    BROWN,
    BLUE,
    GREEN,
    HAZEL,
    GRAY;

    @JsonCreator
    public EyeColor fromValue(String value){
        return EyeColor.valueOf(value.toUpperCase());
    }
}
