package com.yoursay.user.usercharacteristic.model.Enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EyeColor {
    BROWN,
    BLUE,
    GREEN,
    HAZEL,
    AMBER,
    GRAY,
    BLACK_DARK_BROWN,
    OTHER_UNSURE;

    @JsonCreator
    public EyeColor fromValue(String value){
        return EyeColor.valueOf(value.toUpperCase());
    }
}
