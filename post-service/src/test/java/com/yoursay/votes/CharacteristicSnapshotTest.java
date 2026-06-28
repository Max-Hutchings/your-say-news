package com.yoursay.votes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit-tests the axis resolution that every by-characteristic aggregate depends on. Pure logic, so it
 * pins the exact bucket label for each kind of axis and the fallbacks that keep totals reconciling.
 */
class CharacteristicSnapshotTest {

    private static final CharacteristicSnapshot SAMPLE = new CharacteristicSnapshot(
            "LEFT",           // politicalPersuasion
            "25_34",          // ageRange
            "FEMALE",         // gender
            "FEMALE",         // sexAtBirth
            "HETEROSEXUAL",   // sexualOrientation
            "SINGLE",         // maritalStatus
            "WHITE_BRITISH",  // race
            "GB",             // country
            "SOUTH_EAST",     // region
            "URBAN",          // urbanRural
            "SURREY",         // ukCounty
            "GB",             // countryOfBirth
            "GB",             // citizenship
            "CHRISTIAN",      // religion
            "SOMEWHAT_IMPORTANT", // religiosity
            "UNDERGRADUATE",  // education
            "EMPLOYED_FULL_TIME", // occupation
            "TECHNOLOGY",     // employmentSector
            "COMPUTER_SCIENCE", // universitySubject
            "50K_75K",        // incomeRange
            null,             // height (not captured)
            null,             // weightRange
            null,             // eyeColor
            "NO",             // parent
            "4"               // newsFrequency
    );

    @Test
    void resolvesStringAxesToTheirValue() {
        assertEquals("LEFT", SAMPLE.bucketFor("politicalPersuasion"));
        assertEquals("50K_75K", SAMPLE.bucketFor("incomeRange"));
        assertEquals("SURREY", SAMPLE.bucketFor("ukCounty"));
        assertEquals("25_34", SAMPLE.bucketFor("ageRange"));
        assertEquals("URBAN", SAMPLE.bucketFor("urbanRural"));
        assertEquals("4", SAMPLE.bucketFor("newsFrequency"));
    }

    @Test
    void unknownAxisFallsBackToUnknownBucket() {
        // A field that isn't a known axis must not throw — it groups under UNKNOWN.
        assertEquals(CharacteristicSnapshot.UNKNOWN, SAMPLE.bucketFor("favouriteColour"));
    }

    @Test
    void notCapturedValueFallsBackToUnknownBucket() {
        assertEquals(CharacteristicSnapshot.UNKNOWN, SAMPLE.bucketFor("height"));
    }

    @Test
    void emptySnapshotIsUnknownOnEveryAxis() {
        CharacteristicSnapshot empty = CharacteristicSnapshot.empty();
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("politicalPersuasion"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("race"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("ageRange"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("incomeRange"));
    }
}
