package com.yoursay.votes.service;

import com.yoursay.votes.CharacteristicSnapshot;
import com.yoursay.votes.client.UserCharacteristicView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit-tests the freeze from a live user-characteristic response into the anonymised vote-time
 * snapshot. Pure logic, so it pins the exact frozen value for the categorical axes (including the
 * quirky chronotype/outlook and pet axes), the stringification of numeric/boolean axes, the
 * multi-race join, and the null-view fallback.
 */
class CharacteristicSnapshotMapperTest {

    private static UserCharacteristicView fullView() {
        return new UserCharacteristicView(
                42L,              // userId — must NOT reach the snapshot
                "LEFT",           // politicalPersuasion
                "AGE_25_34",      // ageRange
                "WOMAN",          // gender
                "FEMALE",         // sexAtBirth
                "HETEROSEXUAL",   // sexualOrientation
                "SINGLE",         // maritalStatus
                List.of("WHITE", "ASIAN"), // race (multi)
                "United Kingdom", // country
                "South East",     // region
                "URBAN",          // urbanRural
                "SURREY",         // ukCounty
                "UNITED_KINGDOM", // countryOfBirth
                "UNITED_KINGDOM", // citizenship
                "NO_RELIGION",    // religion
                "NOT_RELIGIOUS",  // religiosity
                "BACHELORS",      // education
                "EMPLOYED_FULL_TIME", // occupation
                "IT_TECHNOLOGY",  // employmentSector
                "COMPUTER_SCIENCE", // universitySubject
                "BETWEEN_50K_AND_100K",  // personalIncomeRange
                "BETWEEN_100K_AND_150K", // householdIncomeRange
                "FEET_5_4_TO_5_6", // height
                "KG_60_69",       // weightRange
                "GREEN",          // eyeColor
                "MUM",            // parent
                7,                // newsFrequency
                true,             // hasPet
                "DOG",            // petType
                "NIGHT_OWL",      // chronotype
                "OPTIMIST",       // outlook
                true,             // neurodivergent
                "ADHD",           // neurodivergenceType
                true,             // hasDisability
                "HEARING",        // disabilityType
                "OWN",            // housingStatus
                "FLAT"            // propertyType
        );
    }

    @Test
    void freezesEveryAxisOntoTheSnapshot() {
        CharacteristicSnapshot s = CharacteristicSnapshotMapper.from(fullView());

        assertEquals("LEFT", s.bucketFor("politicalPersuasion"));
        assertEquals("AGE_25_34", s.bucketFor("ageRange"));
        assertEquals("WOMAN", s.bucketFor("gender"));
        assertEquals("SURREY", s.bucketFor("ukCounty"));
        assertEquals("BETWEEN_50K_AND_100K", s.bucketFor("personalIncomeRange"));
        assertEquals("MUM", s.bucketFor("parent"));
        // Numeric/boolean axes are stringified.
        assertEquals("7", s.bucketFor("newsFrequency"));
        assertEquals("true", s.bucketFor("hasPet"));
        assertEquals("DOG", s.bucketFor("petType"));
        // Quirky axes.
        assertEquals("NIGHT_OWL", s.bucketFor("chronotype"));
        assertEquals("OPTIMIST", s.bucketFor("outlook"));
        // Neurodiversity & disability axes (booleans stringified).
        assertEquals("true", s.bucketFor("neurodivergent"));
        assertEquals("ADHD", s.bucketFor("neurodivergenceType"));
        assertEquals("true", s.bucketFor("hasDisability"));
        assertEquals("HEARING", s.bucketFor("disabilityType"));
        // Property axes.
        assertEquals("OWN", s.bucketFor("housingStatus"));
        assertEquals("FLAT", s.bucketFor("propertyType"));
    }

    @Test
    void joinsMultipleRacesInSortedOrder() {
        // Order in must not affect the bucket label, so aggregates of the same combo reconcile.
        CharacteristicSnapshot s = CharacteristicSnapshotMapper.from(fullView());
        assertEquals("ASIAN+WHITE", s.bucketFor("race"));
    }

    @Test
    void emptyOrNullRaceFreezesToNull() {
        assertNull(CharacteristicSnapshotMapper.from(viewWithRace(List.of())).race());
        assertNull(CharacteristicSnapshotMapper.from(viewWithRace(null)).race());
    }

    @Test
    void nullNumericAndBooleanAxesStayNull() {
        UserCharacteristicView view = new UserCharacteristicView(
                1L, "LEFT", "AGE_25_34", "WOMAN", "FEMALE", "HETEROSEXUAL", "SINGLE",
                List.of("WHITE"), "United Kingdom", null, "URBAN", null, null, null,
                null, null, null, null, null, null, "BELOW_20K", "BELOW_20K", null, null,
                null, null, null /* newsFrequency */, null /* hasPet */, null, null, null,
                null /* neurodivergent */, null, null /* hasDisability */, null, null, null);

        CharacteristicSnapshot s = CharacteristicSnapshotMapper.from(view);
        assertNull(s.newsFrequency());
        assertNull(s.hasPet());
        assertNull(s.chronotype());
        assertNull(s.outlook());
        assertNull(s.neurodivergent());
        assertNull(s.hasDisability());
        assertNull(s.housingStatus());
    }

    @Test
    void nullViewFreezesToAllUnknown() {
        CharacteristicSnapshot s = CharacteristicSnapshotMapper.from(null);
        assertEquals(CharacteristicSnapshot.UNKNOWN, s.bucketFor("politicalPersuasion"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, s.bucketFor("chronotype"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, s.bucketFor("outlook"));
        assertEquals(CharacteristicSnapshot.UNKNOWN, s.bucketFor("petType"));
    }

    private static UserCharacteristicView viewWithRace(List<String> race) {
        return new UserCharacteristicView(
                1L, "LEFT", "AGE_25_34", "WOMAN", "FEMALE", "HETEROSEXUAL", "SINGLE",
                race, "United Kingdom", null, "URBAN", null, null, null, null, null, null,
                null, null, null, "BELOW_20K", "BELOW_20K", null, null, null, null, 3, true,
                "DOG", "NIGHT_OWL", "OPTIMIST", true, "ADHD", false, null, "OWN", "FLAT");
    }
}
