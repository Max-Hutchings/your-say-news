package com.yoursay.usercharacteristic.model.Enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum WeightRange {
    KG_30_39,
    KG_40_49,
    KG_50_59,
    KG_60_69,
    KG_70_79,
    KG_80_89,
    KG_90_99,
    KG_100_109,
    KG_110_119,
    KG_120_129,
    KG_130_139,
    KG_140_149,
    KG_150_PLUS;

    @JsonCreator
    public WeightRange fromValue(String value){
        return WeightRange.valueOf(value.toUpperCase());
    }
}
