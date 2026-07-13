package com.yoursay;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

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
                .body("fields.gender.value", contains("WOMAN", "MAN", "NON_BINARY", "SELF_DESCRIBE"))
                .body("fields.gender.label", contains("Woman", "Man", "Non-binary", "Another gender identity"))
                .body("fields.petType.value", contains(
                        "DOG", "CAT", "FISH", "BIRD", "REPTILE", "RABBIT",
                        "SMALL_MAMMAL", "HORSE_PONY", "AMPHIBIAN", "INVERTEBRATE", "OTHER"))
                .body("fields.education.value", not(hasItems("NO_FORMAL_EDUCATION", "HIGH_SCHOOL")))
                .body("fields.sexualOrientation.value", not(hasItems("HETEROSEXUAL", "HOMOSEXUAL")))
                .body("fields.incomeRange.value", contains(
                        "BELOW_20K", "BETWEEN_20K_AND_30K", "BETWEEN_30K_AND_40K", "BETWEEN_40K_AND_50K",
                        "BETWEEN_50K_AND_75K", "BETWEEN_75K_AND_100K", "BETWEEN_100K_AND_150K",
                        "BETWEEN_150K_AND_200K", "BETWEEN_200K_AND_500K", "BETWEEN_500K_AND_1000K",
                        "ABOVE_1000000"))
                .body("fields.housingStatus.value", contains(
                        "OWN_OUTRIGHT", "OWN_MORTGAGE", "SHARED_OWNERSHIP", "PRIVATE_RENT", "SOCIAL_RENT",
                        "LIVE_WITH_FAMILY", "RENT_FREE", "STUDENT_ACCOMMODATION", "TEMPORARY_NO_FIXED", "OTHER"))
                .body("fields.race.value", contains(
                        "WHITE_EUROPEAN", "BLACK_AFRICAN", "EAST_ASIAN", "SOUTH_ASIAN", "SOUTHEAST_ASIAN",
                        "MIDDLE_EASTERN_NORTH_AFRICAN", "HISPANIC_LATINO", "INDIGENOUS", "PACIFIC_ISLANDER",
                        "MIXED_MULTIPLE", "OTHER_ETHNIC_GROUP", "SELF_DESCRIBE"))
                .body("fields.disabilityType.value", contains(
                        "PHYSICAL_MOBILITY", "VISUAL", "HEARING", "COGNITIVE_LEARNING", "CHRONIC_ILLNESS",
                        "MENTAL_HEALTH", "OTHER"))
                .body("fields.citizenship.value", hasItems("BRITISH", "NORTHERN_IRISH", "IRELAND"));
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
        given().contentType(ContentType.JSON).body(validBody()).when().post(BASE).then().statusCode(403);
    }

    @Test
    public void requiresAuthentication() {
        given().when().get(BASE + "/me").then().statusCode(401);
        given().when().get(BASE + "/options").then().statusCode(401);
        given().contentType(ContentType.JSON).body(validBody()).when().post(BASE).then().statusCode(401);
    }
}
