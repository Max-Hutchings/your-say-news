package com.yoursay.usercharacteristic.model.Enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum IncomeRange {
    BELOW_20K,
    BETWEEN_20K_AND_50K,
    BETWEEN_50K_AND_100K,
    BETWEEN_100K_AND_200K,
    BETWEEN_200k_and_500k,
    BETWEEN_500K_and_100000,
    ABOVE_100000;

    @JsonCreator
    public IncomeRange fromValue(String value){
        return IncomeRange.valueOf(value.toUpperCase());
    }
}
