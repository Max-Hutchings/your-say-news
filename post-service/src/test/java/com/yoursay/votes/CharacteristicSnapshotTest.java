package com.yoursay.votes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit-tests the axis resolution that every by-characteristic aggregate depends on. Pure logic, so it
 * pins the exact bucket label for each kind of axis and the fallbacks that keep totals reconciling.
 */
class CharacteristicSnapshotTest {

    private static final CharacteristicSnapshot SAMPLE = new CharacteristicSnapshot(
            "LEFT",          // politicalPersuasion
            "BAND_100K_PLUS",// incomeRange
            "ASIAN",         // race
            "FEMALE",        // sexAtBirth
            "UNITED_KINGDOM",// countryOfBirth
            "GREATER_LONDON",// ukCounty
            null,            // height (not captured)
            null,            // eyeColor
            null,            // weightRange
            null,            // parent
            null,            // universitySubject
            true,            // universityEducated
            false            // propertyOwner
    );

    @Test
    void resolvesStringAxesToTheirValue() {
        assertEquals("LEFT", SAMPLE.bucketFor("politicalPersuasion"));
        assertEquals("BAND_100K_PLUS", SAMPLE.bucketFor("incomeRange"));
        assertEquals("GREATER_LONDON", SAMPLE.bucketFor("ukCounty"));
    }

    @Test
    void resolvesBooleanAxesToTrueOrFalse() {
        assertEquals("true", SAMPLE.bucketFor("universityEducated"));
        assertEquals("false", SAMPLE.bucketFor("propertyOwner"));
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
        // Boolean axes default to false on an empty snapshot, which is a real bucket label.
        assertEquals("false", empty.bucketFor("universityEducated"));
    }
}
