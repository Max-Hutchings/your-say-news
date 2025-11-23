package com.yoursay.usercharacteristic.model.Enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PoliticalPersuasion {
    LEFT,
    RIGHT;


    @JsonCreator
    public PoliticalPersuasion fromValue(String value){
        return PoliticalPersuasion.valueOf(value.toUpperCase());
    }
}
