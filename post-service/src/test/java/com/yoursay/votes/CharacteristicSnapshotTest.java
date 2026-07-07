package com.yoursay.votes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            "50K_75K",        // personalIncomeRange
            "100K_150K",      // householdIncomeRange
            null,             // height (not captured)
            null,             // weightRange
            null,             // eyeColor
            "NO",             // parent
            "4",              // newsFrequency
            "true",           // hasPet
            "DOG",            // petType
            "NIGHT_OWL",      // chronotype
            "OPTIMIST",       // outlook
            "true",           // neurodivergent
            "ADHD",           // neurodivergenceType
            "true",           // hasDisability
            "HEARING",        // disabilityType
            "OWN",            // housingStatus
            "FLAT"            // propertyType
    );

    @Test
    void resolvesStringAxesToTheirValue() {
        assertEquals("LEFT", SAMPLE.bucketFor("politicalPersuasion"));
        assertEquals("50K_75K", SAMPLE.bucketFor("personalIncomeRange"));
        assertEquals("100K_150K", SAMPLE.bucketFor("householdIncomeRange"));
        assertEquals("SURREY", SAMPLE.bucketFor("ukCounty"));
        assertEquals("25_34", SAMPLE.bucketFor("ageRange"));
        assertEquals("URBAN", SAMPLE.bucketFor("urbanRural"));
        assertEquals("4", SAMPLE.bucketFor("newsFrequency"));
        assertEquals("true", SAMPLE.bucketFor("hasPet"));
        assertEquals("DOG", SAMPLE.bucketFor("petType"));
        assertEquals("NIGHT_OWL", SAMPLE.bucketFor("chronotype"));
        assertEquals("OPTIMIST", SAMPLE.bucketFor("outlook"));
        assertEquals("true", SAMPLE.bucketFor("neurodivergent"));
        assertEquals("ADHD", SAMPLE.bucketFor("neurodivergenceType"));
        assertEquals("true", SAMPLE.bucketFor("hasDisability"));
        assertEquals("HEARING", SAMPLE.bucketFor("disabilityType"));
        assertEquals("OWN", SAMPLE.bucketFor("housingStatus"));
        assertEquals("FLAT", SAMPLE.bucketFor("propertyType"));
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
    void isAxisRecognisesEveryKnownAxisAndRejectsUnknowns() {
        // The valid-axis set is the endpoint's 400 guard and must match bucketFor exactly.
        assertTrue(CharacteristicSnapshot.isAxis("politicalPersuasion"));
        assertTrue(CharacteristicSnapshot.isAxis("householdIncomeRange"));
        assertTrue(CharacteristicSnapshot.isAxis("propertyType"));
        assertFalse(CharacteristicSnapshot.isAxis("favouriteColour"));
        assertFalse(CharacteristicSnapshot.isAxis("OVERALL"));
        assertFalse(CharacteristicSnapshot.isAxis(""));
        // 36 axes captured on the snapshot — the full breakdown surface.
        assertEquals(36, CharacteristicSnapshot.AXES.size());
    }

    @Test
    void everyAxisResolvesToANonNullBucketOnAFullSnapshot() {
        // Guards the AXES set against drifting from bucketFor: each declared axis must actually
        // resolve (never fall through to the default UNKNOWN because a case was missed).
        for (String axis : CharacteristicSnapshot.AXES) {
            if (SAMPLE.bucketFor(axis).equals(CharacteristicSnapshot.UNKNOWN)) {
                // Only the three deliberately-null fields on SAMPLE may be UNKNOWN.
                assertTrue(axis.equals("height") || axis.equals("weightRange") || axis.equals("eyeColor"),
                        "axis '" + axis + "' is in AXES but bucketFor did not resolve it");
            }
        }
    }

    @Test
    void emptySnapshotIsUnknownOnEveryAxis() {
        CharacteristicSnapshot empty = CharacteristicSnapshot.empty();
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("politicalPersuasion"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("race"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("ageRange"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("personalIncomeRange"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("householdIncomeRange"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("hasPet"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("petType"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("chronotype"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("outlook"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("neurodivergent"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("disabilityType"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("housingStatus"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, empty.bucketFor("propertyType"));
    }
}
