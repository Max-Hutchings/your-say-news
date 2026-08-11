package com.yoursay.user;

import com.yoursay.user.usercharacteristic.model.Enums.AgeRange;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Year;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the characteristic onboarding flow against a real Postgres
 * (Testcontainers via Dev Services) with the seed data loaded.
 *
 * <p>The PII boundary is the headline thing under test: {@code userId} is always resolved from the
 * authenticated subject, never from the request body. Also covers the reformed shape: age as a number
 * (stored as a birth year, read back as a derived band), multi-select nationality/pet/neuro/disability,
 * direct parent-carer status, home type for everyone with a fixed address, and news-habit fields.
 */
@QuarkusTest
public class UserCharacteristicControllerTest {

    static final String BASE = "/user-characteristics";

    // Seed user 5 (nora) exists but has NOT onboarded — used for the create path.
    static final String NORA = "nora.new@example.com";
    // Seed user 1 (john) has a seeded characteristic profile — used for the read-back path.
    static final String JOHN = "john.doe@example.com";
    // A pristine, never-written user (seed user 6) for the "not onboarded" read.
    static final String BLANK = "blank.user@example.com";
    // Login-ready local-development account intentionally kept pristine for manual onboarding.
    static final String CASEY = "casey.morgan@example.com";
    // Login-ready standard reader with a complete seeded profile.
    static final String RILEY = "riley.reader@example.com";

    @Inject
    AgroalDataSource dataSource;

    /** A valid full answer body. {@code id}/{@code userId} are deliberately present to prove they are ignored. */
    static String validBody() {
        return """
            {
              "id": 1,
              "userId": 1,
              "country": "United Kingdom",
              "city": "Leeds",
              "ukCounty": "WEST_YORKSHIRE",
              "urbanRural": "URBAN",
              "age": 22,
              "gender": "WOMAN",
              "sexAtBirth": "FEMALE",
              "sexualOrientation": "STRAIGHT_HETEROSEXUAL",
              "maritalStatus": "SINGLE",
              "race": ["WHITE_EUROPEAN", "SOUTH_ASIAN"],
              "countryOfBirth": "UNITED_KINGDOM",
              "citizenship": ["BRITISH"],
              "religion": "NO_RELIGION",
              "religiosity": "NOT_RELIGIOUS",
              "politicalPersuasion": "CENTRE_LEFT",
              "education": "BACHELORS",
              "occupation": "STUDENT",
              "employmentSector": "NOT_APPLICABLE",
              "universitySubject": "LAW",
              "personalIncomeRange": "BELOW_20K",
              "householdIncomeRange": "BETWEEN_100K_AND_150K",
              "height": "FEET_5_4_TO_5_6",
              "weightRange": "KG_60_69",
              "eyeColor": "BLUE",
              "parent": "NOT_PARENT_CAREGIVER",
              "hasPet": true,
              "petType": ["DOG"],
              "chronotype": "NIGHT_OWL",
              "outlook": "OPTIMIST",
              "neurodivergent": true,
              "neurodivergenceType": ["ADHD"],
              "hasDisability": false,
              "disabilityType": [],
              "housingStatus": "OWN_MORTGAGE",
              "propertyType": "FLAT_APARTMENT",
              "newsFrequency": 6,
              "balancedNewsViewpoint": true,
              "mainstreamNewsPercent": 60,
              "betterWorldWithData": true
            }
            """;
    }

    static String versionedIndiaBody() {
        return validBody()
                .replace("\"country\": \"United Kingdom\"", "\"country\": \"India\"")
                .replace(
                        "\"personalIncomeRange\": \"BELOW_20K\",",
                        """
                        "countryCode": "INDIA",
                        "income": {
                          "answerVersion": 2,
                          "catalogVersion": "2026.1",
                          "profileId": "IN-INR-GROSS-2023-24-v1",
                          "profileVersion": 1,
                          "marketCode": "IN",
                          "currencyCode": "INR",
                          "personalBandId": "PERSONAL_TIER_3",
                          "householdBandId": "HOUSEHOLD_TIER_5"
                        },""")
                .replace("\"householdIncomeRange\": \"BETWEEN_100K_AND_150K\",", "");
    }

    @Test
    @TestSecurity(user = BLANK, roles = {"user"})
    public void notOnboardedReturns204() {
        given().when().get(BASE + "/me").then().statusCode(204);
    }

    @Test
    @TestSecurity(user = CASEY, roles = {"user"})
    public void loginReadyOnboardingFixtureHasNoCharacteristics() {
        given().when().get(BASE + "/me").then().statusCode(204);
    }

    @Test
    @TestSecurity(user = RILEY, roles = {"user"})
    public void standardReaderHasACompleteSeededCharacteristicProfile() {
        int expectedAge = Year.now().getValue() - 1993;

        given()
                .when().get(BASE + "/me")
                .then()
                .statusCode(200)
                .body("userId", equalTo(10))
                .body("country", equalTo("United Kingdom"))
                .body("city", equalTo("Leeds"))
                .body("ukCounty", equalTo("WEST_YORKSHIRE"))
                .body("urbanRural", equalTo("URBAN"))
                .body("age", equalTo(expectedAge))
                .body("ageRange", equalTo(AgeRange.fromAge(expectedAge).name()))
                .body("gender", equalTo("NON_BINARY"))
                .body("sexAtBirth", equalTo("FEMALE"))
                .body("sexualOrientation", equalTo("PANSEXUAL"))
                .body("maritalStatus", equalTo("SINGLE"))
                .body("race", contains("MIXED_MULTIPLE"))
                .body("countryOfBirth", equalTo("UNITED_KINGDOM"))
                .body("citizenship", contains("BRITISH"))
                .body("religion", equalTo("NO_RELIGION"))
                .body("religiosity", equalTo("NOT_RELIGIOUS"))
                .body("politicalPersuasion", equalTo("NOT_POLITICAL"))
                .body("education", equalTo("BACHELORS"))
                .body("occupation", equalTo("EMPLOYED_PART_TIME"))
                .body("employmentSector", equalTo("MEDIA_COMMUNICATIONS"))
                .body("universitySubject", equalTo("JOURNALISM"))
                .body("personalIncomeRange", equalTo("BETWEEN_30K_AND_40K"))
                .body("householdIncomeRange", equalTo("BETWEEN_50K_AND_75K"))
                .body("height", equalTo("FEET_5_7_TO_5_9"))
                .body("weightRange", equalTo("KG_70_79"))
                .body("eyeColor", equalTo("HAZEL"))
                .body("parent", equalTo("NOT_PARENT_CAREGIVER"))
                .body("hasPet", equalTo(true))
                .body("petType", contains("CAT"))
                .body("chronotype", equalTo("IN_BETWEEN"))
                .body("outlook", equalTo("OPTIMIST"))
                .body("neurodivergent", equalTo(false))
                .body("neurodivergenceType", empty())
                .body("hasDisability", equalTo(false))
                .body("disabilityType", empty())
                .body("housingStatus", equalTo("PRIVATE_RENT"))
                .body("propertyType", equalTo("FLAT_APARTMENT"))
                .body("newsFrequency", equalTo(7))
                .body("balancedNewsViewpoint", equalTo(true))
                .body("mainstreamNewsPercent", equalTo(45))
                .body("betterWorldWithData", equalTo(true));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void saveThenReadBackPinsValues() {
        // Create
        given()
                .contentType(ContentType.JSON)
                .body(validBody())
                .when().post(BASE)
                .then()
                .statusCode(201)
                // userId comes from the token (nora == seed user 5), NOT the 9999 in the body.
                .body("userId", equalTo(5))
                // Age is stored as a birth year and read back as a derived band (22 -> 20-24).
                .body("age", equalTo(22))
                .body("ageRange", equalTo("AGE_20_24"))
                .body("politicalPersuasion", equalTo("CENTRE_LEFT"))
                .body("personalIncomeRange", equalTo("BELOW_20K"))
                .body("householdIncomeRange", equalTo("BETWEEN_100K_AND_150K"))
                .body("race", hasItems("WHITE_EUROPEAN", "SOUTH_ASIAN"))
                .body("race.size()", is(2))
                .body("citizenship", contains("BRITISH"))
                .body("hasPet", equalTo(true))
                .body("petType", contains("DOG"));

        // Read back
        given()
                .when().get(BASE + "/me")
                .then()
                .statusCode(200)
                .body("userId", equalTo(5))
                .body("country", equalTo("United Kingdom"))
                .body("city", equalTo("Leeds"))
                .body("ukCounty", equalTo("WEST_YORKSHIRE"))
                .body("urbanRural", equalTo("URBAN"))
                .body("gender", equalTo("WOMAN"))
                .body("sexAtBirth", equalTo("FEMALE"))
                .body("sexualOrientation", equalTo("STRAIGHT_HETEROSEXUAL"))
                .body("maritalStatus", equalTo("SINGLE"))
                .body("countryOfBirth", equalTo("UNITED_KINGDOM"))
                .body("religion", equalTo("NO_RELIGION"))
                .body("religiosity", equalTo("NOT_RELIGIOUS"))
                .body("education", equalTo("BACHELORS"))
                .body("occupation", equalTo("STUDENT"))
                .body("employmentSector", equalTo("NOT_APPLICABLE"))
                .body("universitySubject", equalTo("LAW"))
                .body("height", equalTo("FEET_5_4_TO_5_6"))
                .body("weightRange", equalTo("KG_60_69"))
                .body("eyeColor", equalTo("BLUE"))
                .body("parent", equalTo("NOT_PARENT_CAREGIVER"))
                .body("newsFrequency", equalTo(6))
                .body("balancedNewsViewpoint", equalTo(true))
                .body("mainstreamNewsPercent", equalTo(60))
                .body("betterWorldWithData", equalTo(true))
                .body("hasPet", equalTo(true))
                .body("petType", contains("DOG"))
                .body("chronotype", equalTo("NIGHT_OWL"))
                .body("outlook", equalTo("OPTIMIST"))
                .body("neurodivergent", equalTo(true))
                .body("neurodivergenceType", contains("ADHD"))
                .body("hasDisability", equalTo(false))
                .body("disabilityType", empty())
                .body("housingStatus", equalTo("OWN_MORTGAGE"))
                .body("propertyType", equalTo("FLAT_APARTMENT"))
                .body("race", hasItems("WHITE_EUROPEAN", "SOUTH_ASIAN"))
                .body("$", not(hasKey("name")))
                .body("$", not(hasKey("email")))
                .body("$", not(hasKey("dateOfBirth")));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void versionedIncomeStoresProfileProvenanceAndServerDerivedTiers() {
        given()
                .contentType(ContentType.JSON)
                .body(versionedIndiaBody())
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("country", equalTo("India"))
                .body("countryCode", equalTo("INDIA"))
                .body("personalIncomeRange", nullValue())
                .body("householdIncomeRange", nullValue())
                .body("income.answerVersion", equalTo(2))
                .body("income.catalogVersion", equalTo("2026.1"))
                .body("income.profileId", equalTo("IN-INR-GROSS-2023-24-v1"))
                .body("income.profileVersion", equalTo(1))
                .body("income.marketCode", equalTo("IN"))
                .body("income.currencyCode", equalTo("INR"))
                .body("income.personalBandId", equalTo("PERSONAL_TIER_3"))
                .body("income.householdBandId", equalTo("HOUSEHOLD_TIER_5"))
                .body("personalIncomeTier", equalTo("TIER_3"))
                .body("householdIncomeTier", equalTo("TIER_5"));

        given()
                .when().get(BASE + "/me")
                .then()
                .statusCode(200)
                .body("countryCode", equalTo("INDIA"))
                .body("income.answerVersion", equalTo(2))
                .body("income.catalogVersion", equalTo("2026.1"))
                .body("income.profileId", equalTo("IN-INR-GROSS-2023-24-v1"))
                .body("income.profileVersion", equalTo(1))
                .body("income.marketCode", equalTo("IN"))
                .body("income.currencyCode", equalTo("INR"))
                .body("income.personalBandId", equalTo("PERSONAL_TIER_3"))
                .body("income.householdBandId", equalTo("HOUSEHOLD_TIER_5"))
                .body("personalIncomeTier", equalTo("TIER_3"))
                .body("householdIncomeTier", equalTo("TIER_5"));

        assertIncomeDatabaseUpdateRejected("personal_income_range = 'BELOW_20K'");
        assertIncomeDatabaseUpdateRejected("income_answer_version = NULL");
        assertIncomeDatabaseUpdateRejected("income_currency_code = NULL");
        assertVersionedIncomeReferences(
                "IN-INR-GROSS-2023-24-v1", "PERSONAL_TIER_3", "HOUSEHOLD_TIER_5");
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void versionedIncomeRejectsWrongResidenceProfileAndUnknownBands() {
        String wrongResidenceProfile = versionedIndiaBody()
                .replace("\"profileId\": \"IN-INR-GROSS-2023-24-v1\"", "\"profileId\": \"GB-GBP-GROSS-2025-v1\"")
                .replace("\"marketCode\": \"IN\"", "\"marketCode\": \"GB\"")
                .replace("\"currencyCode\": \"INR\"", "\"currencyCode\": \"GBP\"");
        String unknownBand = versionedIndiaBody()
                .replace("\"personalBandId\": \"PERSONAL_TIER_3\"", "\"personalBandId\": \"PERSONAL_TIER_99\"");
        String mixedVersion = versionedIndiaBody()
                .replace(
                        "\"countryCode\": \"INDIA\",",
                        "\"countryCode\": \"INDIA\",\n"
                                + "              \"personalIncomeRange\": \"BELOW_20K\",");

        for (String invalid : new String[]{wrongResidenceProfile, unknownBand, mixedVersion}) {
            given()
                    .contentType(ContentType.JSON)
                    .body(invalid)
                    .when().post(BASE)
                    .then()
                    .statusCode(400)
                    .body("code", equalTo("USER_CHARACTERISTIC_INVALID_FIELD"));
        }
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void ageBelowMinimumIsRejected() {
        String tooYoung = validBody().replace("\"age\": 22", "\"age\": 15");
        given()
                .contentType(ContentType.JSON)
                .body(tooYoung)
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_CHARACTERISTIC_INVALID_FIELD"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void minimumAgeAndNewsScaleBoundariesAreAccepted() {
        String boundaryBody = validBody()
                .replace("\"age\": 22", "\"age\": 16")
                .replace("\"newsFrequency\": 6", "\"newsFrequency\": 0")
                .replace("\"mainstreamNewsPercent\": 60", "\"mainstreamNewsPercent\": 100");

        given()
                .contentType(ContentType.JSON)
                .body(boundaryBody)
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("age", equalTo(16))
                .body("ageRange", equalTo("AGE_16_17"))
                .body("newsFrequency", equalTo(0))
                .body("mainstreamNewsPercent", equalTo(100));

        String oppositeBoundaries = validBody()
                .replace("\"newsFrequency\": 6", "\"newsFrequency\": 10")
                .replace("\"mainstreamNewsPercent\": 60", "\"mainstreamNewsPercent\": 0");
        given()
                .contentType(ContentType.JSON)
                .body(oppositeBoundaries)
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("newsFrequency", equalTo(10))
                .body("mainstreamNewsPercent", equalTo(0));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void newsScaleValuesOutsideTheirDomainsAreRejected() {
        for (String invalid : new String[]{
                validBody().replace("\"newsFrequency\": 6", "\"newsFrequency\": -1"),
                validBody().replace("\"newsFrequency\": 6", "\"newsFrequency\": 11"),
                validBody().replace("\"mainstreamNewsPercent\": 60", "\"mainstreamNewsPercent\": -1"),
                validBody().replace("\"mainstreamNewsPercent\": 60", "\"mainstreamNewsPercent\": 101")}) {
            given()
                    .contentType(ContentType.JSON)
                    .body(invalid)
                    .when().post(BASE)
                    .then()
                    .statusCode(400)
                    .body("code", equalTo("USER_CHARACTERISTIC_INVALID_FIELD"));
        }
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void petTypeIsClearedWhenUserHasNoPet() {
        // A non-owner who still sends pet types must have them dropped server-side.
        String noPet = validBody()
                .replace("\"hasPet\": true", "\"hasPet\": false");
        given()
                .contentType(ContentType.JSON)
                .body(noPet)
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("hasPet", equalTo(false))
                .body("petType", empty());
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void petOwnerWithoutPetTypeIsRejected() {
        // hasPet true but no pet types is invalid — at least one type is required for owners.
        String missingType = validBody().replace("\"petType\": [\"DOG\"]", "\"petType\": []");
        given()
                .contentType(ContentType.JSON)
                .body(missingType)
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_CHARACTERISTIC_EMPTY_MULTI_SELECT"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void missingHasPetIsRejected() {
        String missing = validBody().replace("\"hasPet\": true,", "");
        given()
                .contentType(ContentType.JSON)
                .body(missing)
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_CHARACTERISTIC_REQUIRED_FIELD"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void neurodivergenceTypeIsClearedWhenNotNeurodivergent() {
        // First store a type as a neurodivergent user...
        given()
                .contentType(ContentType.JSON)
                .body(validBody())
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("neurodivergent", equalTo(true))
                .body("neurodivergenceType", contains("ADHD"));
        // ...then re-save as NOT neurodivergent with stray types still in the body: the previously
        // stored types must be cleared (proves the force-empty else-branch on the update path).
        String notNd = validBody().replace("\"neurodivergent\": true", "\"neurodivergent\": false");
        given()
                .contentType(ContentType.JSON)
                .body(notNd)
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("neurodivergent", equalTo(false))
                .body("neurodivergenceType", empty());
        given().when().get(BASE + "/me").then()
                .statusCode(200)
                .body("neurodivergent", equalTo(false))
                .body("neurodivergenceType", empty());
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void neurodivergentWithoutTypeIsRejected() {
        // neurodivergent true but no types is invalid — at least one is required when the flag is set.
        String body = validBody().replace("\"neurodivergenceType\": [\"ADHD\"]", "\"neurodivergenceType\": []");
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_CHARACTERISTIC_EMPTY_MULTI_SELECT"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void disabilityTypeIsRequiredWhenHasDisability() {
        // Flip to having a disability but omit the types — must be rejected.
        String body = validBody()
                .replace("\"hasDisability\": false", "\"hasDisability\": true");
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_CHARACTERISTIC_EMPTY_MULTI_SELECT"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void disabilityTypeIsStoredWhenHasDisability() {
        String body = validBody()
                .replace("\"hasDisability\": false", "\"hasDisability\": true")
                .replace("\"disabilityType\": []", "\"disabilityType\": [\"HEARING\"]");
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("hasDisability", equalTo(true))
                .body("disabilityType", contains("HEARING"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void disabilityTypeIsClearedWhenNoDisability() {
        // First store a disability + type...
        String withDisability = validBody()
                .replace("\"hasDisability\": false", "\"hasDisability\": true")
                .replace("\"disabilityType\": []", "\"disabilityType\": [\"HEARING\"]");
        given()
                .contentType(ContentType.JSON)
                .body(withDisability)
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("hasDisability", equalTo(true))
                .body("disabilityType", contains("HEARING"));
        // ...then re-save with no disability but stray types in the body: they must be cleared.
        String noDisability = withDisability.replace("\"hasDisability\": true", "\"hasDisability\": false");
        given()
                .contentType(ContentType.JSON)
                .body(noDisability)
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("hasDisability", equalTo(false))
                .body("disabilityType", empty());
        given().when().get(BASE + "/me").then()
                .statusCode(200)
                .body("hasDisability", equalTo(false))
                .body("disabilityType", empty());
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void missingHasDisabilityIsRejected() {
        String missing = validBody().replace("\"hasDisability\": false,", "");
        given()
                .contentType(ContentType.JSON)
                .body(missing)
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_CHARACTERISTIC_REQUIRED_FIELD"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void missingNeurodivergentIsRejected() {
        String missing = validBody().replace("\"neurodivergent\": true,", "");
        given()
                .contentType(ContentType.JSON)
                .body(missing)
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_CHARACTERISTIC_REQUIRED_FIELD"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void homeTypeIsClearedForNoFixedAddress() {
        // First store a home type with a fixed home...
        given()
                .contentType(ContentType.JSON)
                .body(validBody())
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("housingStatus", equalTo("OWN_MORTGAGE"))
                .body("propertyType", equalTo("FLAT_APARTMENT"));
        // ...then re-save as no-fixed-address with a stray home type still in the body: it must be nulled.
        String noFixed = validBody().replace("\"housingStatus\": \"OWN_MORTGAGE\"", "\"housingStatus\": \"TEMPORARY_NO_FIXED\"");
        given()
                .contentType(ContentType.JSON)
                .body(noFixed)
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("housingStatus", equalTo("TEMPORARY_NO_FIXED"))
                .body("propertyType", nullValue());
        given().when().get(BASE + "/me").then()
                .statusCode(200)
                .body("housingStatus", equalTo("TEMPORARY_NO_FIXED"))
                .body("propertyType", nullValue());
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void fixedHomeWithoutHomeTypeIsRejected() {
        // A fixed housing status but no home type is invalid — the type is required for everyone with a home.
        String body = validBody().replace("\"propertyType\": \"FLAT_APARTMENT\"", "\"propertyType\": null");
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_CHARACTERISTIC_REQUIRED_FIELD"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void missingHousingStatusIsRejected() {
        String missing = validBody().replace("\"housingStatus\": \"OWN_MORTGAGE\",", "");
        given()
                .contentType(ContentType.JSON)
                .body(missing)
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_CHARACTERISTIC_REQUIRED_FIELD"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void selfDescribeGenderRequiresText() {
        // gender SELF_DESCRIBE with a blank description must be rejected...
        String blank = validBody().replace("\"gender\": \"WOMAN\"", "\"gender\": \"SELF_DESCRIBE\"");
        given()
                .contentType(ContentType.JSON)
                .body(blank)
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_CHARACTERISTIC_REQUIRED_FIELD"));
        // ...but is stored when a description is supplied.
        String described = validBody()
                .replace("\"gender\": \"WOMAN\"", "\"gender\": \"SELF_DESCRIBE\", \"genderSelfDescribe\": \"Agender\"");
        given()
                .contentType(ContentType.JSON)
                .body(described)
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("gender", equalTo("SELF_DESCRIBE"))
                .body("genderSelfDescribe", equalTo("Agender"));

        given()
                .contentType(ContentType.JSON)
                .body(validBody().replace(
                        "\"gender\": \"WOMAN\"",
                        "\"gender\": \"WOMAN\", \"genderSelfDescribe\": \"stale\""))
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("gender", equalTo("WOMAN"))
                .body("genderSelfDescribe", nullValue());
        given().when().get(BASE + "/me").then()
                .statusCode(200)
                .body("genderSelfDescribe", nullValue());
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void bodyIdentityIsNeverTrusted() {
        // The body targets John's real user/profile ids, but the saved row still belongs to Nora (5).
        given()
                .contentType(ContentType.JSON)
                .body(validBody())
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("userId", not(equalTo(1)))
                .body("id", not(equalTo(1)))
                .body("userId", equalTo(5));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void nonHigherEducationClearsAContradictoryUniversitySubject() {
        String body = validBody().replace("\"education\": \"BACHELORS\"", "\"education\": \"SECONDARY_SCHOOL\"");
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("education", equalTo("SECONDARY_SCHOOL"))
                .body("universitySubject", nullValue());
        given().when().get(BASE + "/me").then()
                .statusCode(200)
                .body("education", equalTo("SECONDARY_SCHOOL"))
                .body("universitySubject", nullValue());
    }

    @Test
    @TestSecurity(user = JOHN, roles = {"user"})
    public void readsSeededProfile() {
        given()
                .when().get(BASE + "/me")
                .then()
                .statusCode(200)
                .body("userId", equalTo(1))
                .body("city", equalTo("Bristol"))
                .body("politicalPersuasion", equalTo("CENTRE_LEFT"))
                .body("religion", equalTo("NO_RELIGION"))
                .body("newsFrequency", equalTo(8))
                .body("citizenship", contains("BRITISH"))
                .body("race", contains("WHITE_EUROPEAN"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void invalidEnumValueIsRejected() {
        String bad = validBody().replace("\"CENTRE_LEFT\"", "\"CENTRE_LEFTISH\"");
        given()
                .contentType(ContentType.JSON)
                .body(bad)
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_CHARACTERISTIC_INVALID_ENUM"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void everyLegacyEnumTypeIsRejectedForNewAnswers() {
        String[][] replacements = {
                {"\"education\": \"BACHELORS\"", "\"education\": \"HIGH_SCHOOL\""},
                {"\"occupation\": \"STUDENT\"", "\"occupation\": \"UNEMPLOYED\""},
                {"\"sexualOrientation\": \"STRAIGHT_HETEROSEXUAL\"", "\"sexualOrientation\": \"HETEROSEXUAL\""},
                {"\"maritalStatus\": \"SINGLE\"", "\"maritalStatus\": \"DIVORCED\""},
                {"\"politicalPersuasion\": \"CENTRE_LEFT\"", "\"politicalPersuasion\": \"APOLITICAL\""},
                {"\"weightRange\": \"KG_60_69\"", "\"weightRange\": \"KG_30_39\""},
                {"\"personalIncomeRange\": \"BELOW_20K\"", "\"personalIncomeRange\": \"BETWEEN_20K_AND_50K\""},
                {"\"parent\": \"NOT_PARENT_CAREGIVER\"", "\"parent\": \"MUM\""},
                {"\"housingStatus\": \"OWN_MORTGAGE\"", "\"housingStatus\": \"OWN\""},
                {"\"propertyType\": \"FLAT_APARTMENT\"", "\"propertyType\": \"HOUSE\""},
                {"\"universitySubject\": \"LAW\"", "\"universitySubject\": \"NA\""},
                {"\"citizenship\": [\"BRITISH\"]", "\"citizenship\": [\"UNITED_KINGDOM\"]"},
        };

        for (String[] replacement : replacements) {
            given()
                    .contentType(ContentType.JSON)
                    .body(validBody().replace(replacement[0], replacement[1]))
                    .when().post(BASE)
                    .then()
                    .statusCode(400)
                    .body("code", equalTo("USER_CHARACTERISTIC_INVALID_ENUM"));
        }
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void deprecatedConstantsRemainReadableOnHistoricalRows() throws Exception {
        given().contentType(ContentType.JSON).body(validBody()).when().post(BASE).then().statusCode(201);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE user_characteristic
                    SET education = 'HIGH_SCHOOL', parent = 'MUM', property_type = 'HOUSE'
                    WHERE user_id = 5
                    """);
        }

        given()
                .when().get(BASE + "/me")
                .then()
                .statusCode(200)
                .body("education", equalTo("HIGH_SCHOOL"))
                .body("parent", equalTo("MUM"))
                .body("propertyType", equalTo("HOUSE"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void missingRequiredFieldIsRejected() {
        // Drop the required personalIncomeRange.
        String missing = validBody().replace("\"personalIncomeRange\": \"BELOW_20K\",", "");
        given()
                .contentType(ContentType.JSON)
                .body(missing)
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_CHARACTERISTIC_REQUIRED_FIELD"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void nullRequestBodyIsRejectedWithItsStructuredError() {
        given()
                .contentType(ContentType.JSON)
                .body("null")
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_CHARACTERISTIC_REQUEST_BODY_REQUIRED"));
    }

    @Test
    @TestSecurity(user = "not.in.local.database@example.com", roles = {"user"})
    public void authenticatedSubjectWithoutLocalUserIsRejected() {
        given()
                .when().get(BASE + "/me")
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_CHARACTERISTIC_USER_MISSING"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void emptyRaceIsRejected() {
        String emptyRace = validBody().replace("[\"WHITE_EUROPEAN\", \"SOUTH_ASIAN\"]", "[]");
        given()
                .contentType(ContentType.JSON)
                .body(emptyRace)
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_CHARACTERISTIC_EMPTY_RACE"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void emptyCitizenshipIsRejected() {
        String emptyCitizenship = validBody().replace("\"citizenship\": [\"BRITISH\"]", "\"citizenship\": []");
        given()
                .contentType(ContentType.JSON)
                .body(emptyCitizenship)
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_CHARACTERISTIC_EMPTY_MULTI_SELECT"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void optionsExposeCuratedCurrentValuesWithoutLegacyConstants() {
        given()
                .when().get(BASE + "/options")
                .then()
                .statusCode(200)
                .body("schemaVersion", equalTo(1))
                .body("minimumAge", equalTo(16))
                .body("fields.keySet()", containsInAnyOrder(
                        "urbanRural", "gender", "sexAtBirth", "race", "sexualOrientation", "maritalStatus",
                        "countryOfBirth", "citizenship", "ukCounty", "religion", "religiosity",
                        "politicalPersuasion", "education", "occupation", "employmentSector",
                        "universitySubject", "height", "weightRange", "incomeRange", "eyeColor", "parent",
                        "petType", "chronotype", "outlook", "neurodivergenceType", "disabilityType",
                        "housingStatus", "propertyType"))
                .body("fields.gender.value", contains("SELF_DESCRIBE", "MAN", "NON_BINARY", "WOMAN"))
                .body("fields.gender.label", contains("Another gender identity", "Man", "Non-binary", "Woman"))
                .body("fields.petType.value", contains(
                        "AMPHIBIAN", "BIRD", "CAT", "DOG", "FISH", "HORSE_PONY",
                        "INVERTEBRATE", "OTHER", "RABBIT", "REPTILE", "SMALL_MAMMAL"))
                .body("fields.education.value", contains(
                        "NO_FORMAL_QUALIFICATIONS", "PRIMARY_SCHOOLING", "SECONDARY_SCHOOL",
                        "VOCATIONAL_TECHNICAL", "HIGHER_EDUCATION_BELOW_DEGREE", "BACHELORS",
                        "MASTERS", "DOCTORATE", "OTHER", "NOT_SURE"))
                .body("fields.education.label", contains(
                        "No formal qualifications", "Primary / basic schooling", "Secondary school",
                        "Vocational / technical qualification", "Higher education below degree",
                        "Bachelor's degree", "Master's degree", "Doctorate", "Other", "Not sure"))
                .body("fields.sexualOrientation.value", contains(
                        "SELF_DESCRIBE", "ASEXUAL", "BISEXUAL", "GAY_LESBIAN", "PANSEXUAL", "QUEER",
                        "QUESTIONING", "STRAIGHT_HETEROSEXUAL"))
                .body("fields.sexualOrientation.label", contains(
                        "Another orientation", "Asexual", "Bisexual", "Gay or lesbian", "Pansexual",
                        "Queer", "Questioning / unsure", "Straight / heterosexual"))
                .body("fields.universitySubject.value", hasItems(
                        "ACCOUNTING_FINANCE", "ALLIED_HEALTH", "CRIMINOLOGY", "DATA_SCIENCE",
                        "DENTISTRY", "DESIGN", "PHARMACY", "PUBLIC_HEALTH", "SOCIAL_WORK",
                        "VETERINARY_SCIENCE"))
                .body("fields.universitySubject.label", hasItems(
                        "Accounting & finance", "Allied health", "Data science", "Public health",
                        "Social work", "Veterinary science"))
                .body("fields.incomeRange.value", contains(
                        "BELOW_20K", "BETWEEN_20K_AND_30K", "BETWEEN_30K_AND_40K", "BETWEEN_40K_AND_50K",
                        "BETWEEN_50K_AND_75K", "BETWEEN_75K_AND_100K", "BETWEEN_100K_AND_150K",
                        "BETWEEN_150K_AND_200K", "BETWEEN_200K_AND_500K", "BETWEEN_500K_AND_1000K",
                        "ABOVE_1000000"))
                .body("fields.housingStatus.value", contains(
                        "RENT_FREE", "LIVE_WITH_FAMILY", "OTHER", "OWN_OUTRIGHT", "OWN_MORTGAGE",
                        "PRIVATE_RENT", "SHARED_OWNERSHIP", "SOCIAL_RENT", "STUDENT_ACCOMMODATION",
                        "TEMPORARY_NO_FIXED"))
                .body("fields.race.value", contains(
                        "BLACK_AFRICAN", "EAST_ASIAN", "HISPANIC_LATINO", "INDIGENOUS",
                        "MIDDLE_EASTERN_NORTH_AFRICAN", "MIXED_MULTIPLE", "OTHER_ETHNIC_GROUP",
                        "PACIFIC_ISLANDER", "SELF_DESCRIBE", "SOUTH_ASIAN", "SOUTHEAST_ASIAN",
                        "WHITE_EUROPEAN"))
                .body("fields.disabilityType.value", contains(
                        "CHRONIC_ILLNESS", "COGNITIVE_LEARNING", "HEARING", "MENTAL_HEALTH", "OTHER",
                        "PHYSICAL_MOBILITY", "VISUAL"))
                .body("fields.citizenship.value", hasItems("BRITISH", "NORTHERN_IRISH", "IRELAND"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void incomeOptionsExposeMarketSpecificGbpAndInrBands() {
        given()
                .queryParam("marketCode", "GB")
                .queryParam("currencyCode", "GBP")
                .when().get(BASE + "/income-options")
                .then()
                .statusCode(200)
                .body("catalogVersion", equalTo("2026.1"))
                .body("profileId", equalTo("GB-GBP-GROSS-2025-v1"))
                .body("profileVersion", equalTo(1))
                .body("marketCode", equalTo("GB"))
                .body("marketLabel", equalTo("United Kingdom"))
                .body("currencyCode", equalTo("GBP"))
                .body("residenceCountryCodes", contains("UNITED_KINGDOM"))
                .body("sourceYear", equalTo("2025"))
                .body("sourceUrl", containsString("ons.gov.uk"))
                .body("derivation", containsString("ONS earnings distribution"))
                .body("confidence", equalTo("HIGH"))
                .body("personalBands.id", contains(
                        "PERSONAL_TIER_1", "PERSONAL_TIER_2", "PERSONAL_TIER_3",
                        "PERSONAL_TIER_4", "PERSONAL_TIER_5", "PERSONAL_TIER_6",
                        "PERSONAL_TIER_7"))
                .body("personalBands.tier", contains(
                        "TIER_1", "TIER_2", "TIER_3", "TIER_4", "TIER_5", "TIER_6", "TIER_7"))
                .body("personalBands[0].lowerInclusive", nullValue())
                .body("personalBands[0].upperExclusive", equalTo(15000))
                .body("personalBands[6].lowerInclusive", equalTo(140000))
                .body("personalBands[6].upperExclusive", nullValue())
                .body("personalBands.label", contains(
                        "Under GBP 15k", "GBP 15k to GBP 25k", "GBP 25k to GBP 40k",
                        "GBP 40k to GBP 60k", "GBP 60k to GBP 90k",
                        "GBP 90k to GBP 140k", "GBP 140k or more"))
                .body("householdBands.label", contains(
                        "Under GBP 20k", "GBP 20k to GBP 35k", "GBP 35k to GBP 55k",
                        "GBP 55k to GBP 85k", "GBP 85k to GBP 130k",
                        "GBP 130k to GBP 200k", "GBP 200k or more"));

        given()
                .queryParam("marketCode", "IN")
                .queryParam("currencyCode", "INR")
                .when().get(BASE + "/income-options")
                .then()
                .statusCode(200)
                .body("catalogVersion", equalTo("2026.1"))
                .body("profileId", equalTo("IN-INR-GROSS-2023-24-v1"))
                .body("profileVersion", equalTo(1))
                .body("marketCode", equalTo("IN"))
                .body("marketLabel", equalTo("India"))
                .body("currencyCode", equalTo("INR"))
                .body("residenceCountryCodes", contains("INDIA"))
                .body("sourceYear", equalTo("2023-24"))
                .body("sourceUrl", containsString("mospi.gov.in"))
                .body("derivation", containsString("PLFS earnings evidence"))
                .body("confidence", equalTo("MEDIUM"))
                .body("personalBands[0].upperExclusive", equalTo(200000))
                .body("personalBands[6].lowerInclusive", equalTo(3500000))
                .body("householdBands[0].upperExclusive", equalTo(300000))
                .body("householdBands[6].lowerInclusive", equalTo(5000000))
                .body("personalBands.label", contains(
                        "Under INR 2 lakh", "INR 2 lakh to INR 4 lakh",
                        "INR 4 lakh to INR 7 lakh", "INR 7 lakh to INR 12 lakh",
                        "INR 12 lakh to INR 20 lakh", "INR 20 lakh to INR 35 lakh",
                        "INR 35 lakh or more"))
                .body("householdBands.label", contains(
                        "Under INR 3 lakh", "INR 3 lakh to INR 6 lakh",
                        "INR 6 lakh to INR 10 lakh", "INR 10 lakh to INR 18 lakh",
                        "INR 18 lakh to INR 30 lakh", "INR 30 lakh to INR 50 lakh",
                        "INR 50 lakh or more"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void unsupportedIncomeMarketCurrencyPairReturns404() {
        given()
                .queryParam("marketCode", "GB")
                .queryParam("currencyCode", "INR")
                .when().get(BASE + "/income-options")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void usersCannotReadCharacteristicProfilesById() {
        given().when().get(BASE + "/1").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin@yoursay.example", roles = {"admin"})
    public void wrongRoleCannotUseCharacteristicEndpoints() {
        given().when().get(BASE + "/me").then().statusCode(403);
        given().when().get(BASE + "/options").then().statusCode(403);
        given()
                .queryParam("marketCode", "GB")
                .queryParam("currencyCode", "GBP")
                .when().get(BASE + "/income-options")
                .then().statusCode(403);
        given().contentType(ContentType.JSON).body(validBody()).when().post(BASE).then().statusCode(403);
    }

    @Test
    public void requiresAuthentication() {
        given().when().get(BASE + "/me").then().statusCode(401);
        given().when().get(BASE + "/options").then().statusCode(401);
        given()
                .queryParam("marketCode", "GB")
                .queryParam("currencyCode", "GBP")
                .when().get(BASE + "/income-options")
                .then().statusCode(401);
        given().contentType(ContentType.JSON).body(validBody()).when().post(BASE).then().statusCode(401);
    }

    private void assertIncomeDatabaseUpdateRejected(String assignment) {
        assertThrows(SQLException.class, () -> {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "UPDATE user_characteristic SET " + assignment + " WHERE user_id = 5");
            }
        });
    }

    private void assertVersionedIncomeReferences(
            String expectedProfile, String expectedPersonalBand, String expectedHouseholdBand) {
        String sql = """
                SELECT p.public_id,
                       personal.band_code, personal.measure, personal_profile.public_id,
                       household.band_code, household.measure, household_profile.public_id
                FROM user_characteristic c
                JOIN income_range_profile p ON p.id = c.income_range_profile_ref_id
                JOIN income_range_band personal ON personal.id = c.personal_income_band_ref_id
                JOIN income_range_profile personal_profile
                  ON personal_profile.id = personal.income_range_profile_id
                JOIN income_range_band household ON household.id = c.household_income_band_ref_id
                JOIN income_range_profile household_profile
                  ON household_profile.id = household.income_range_profile_id
                WHERE c.user_id = 5
                """;
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                java.sql.ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            assertEquals(expectedProfile, result.getString(1));
            assertEquals(expectedPersonalBand, result.getString(2));
            assertEquals("PERSONAL", result.getString(3));
            assertEquals(expectedProfile, result.getString(4));
            assertEquals(expectedHouseholdBand, result.getString(5));
            assertEquals("HOUSEHOLD", result.getString(6));
            assertEquals(expectedProfile, result.getString(7));
            assertFalse(result.next());
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to verify versioned income references", e);
        }
    }
}
