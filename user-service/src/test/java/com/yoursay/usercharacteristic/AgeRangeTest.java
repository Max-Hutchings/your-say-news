package com.yoursay.usercharacteristic;

import com.yoursay.usercharacteristic.model.Enums.AgeRange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgeRangeTest {

    @ParameterizedTest
    @CsvSource({
            "16, AGE_16_17",
            "17, AGE_16_17",
            "18, AGE_18_19",
            "19, AGE_18_19",
            "20, AGE_20_24",
            "24, AGE_20_24",
            "25, AGE_25_34",
            "34, AGE_25_34",
            "35, AGE_35_44",
            "44, AGE_35_44",
            "45, AGE_45_54",
            "54, AGE_45_54",
            "55, AGE_55_64",
            "64, AGE_55_64",
            "65, AGE_65_74",
            "74, AGE_65_74",
            "75, AGE_75_84",
            "84, AGE_75_84",
            "85, AGE_85_PLUS"
    })
    void mapsEveryReportingBoundary(int age, AgeRange expected) {
        assertEquals(expected, AgeRange.fromAge(age));
    }
}
