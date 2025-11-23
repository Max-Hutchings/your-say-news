package com.yoursay.usercharacteristic.model.Enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Parent {
    MUM,
    DAD,
    NO;

    @JsonCreator
    public Parent fromValue(String value){
        return Parent.valueOf(value.toUpperCase());
    }
}
