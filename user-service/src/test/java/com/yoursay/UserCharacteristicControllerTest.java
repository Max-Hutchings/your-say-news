package com.yoursay;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for the characteristic onboarding flow against a real Postgres
 * (Testcontainers via Dev Services) with the seed data loaded.
 *
 * <p>The PII boundary is the headline thing under test: {@code userId} is always resolved from the
 * authenticated subject, never from the request body.
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

    /** A valid full answer body. {@code id}/{@code userId} are deliberately present to prove they are ignored. */
    static String validBody() {
        return """
            {
              "id": 8888,
              "userId": 9999,
              "country": "United Kingdom",
              "city": "Leeds",
              "ukCounty": "WEST_YORKSHIRE",
              "urbanRural": "URBAN",
              "ageRange": "AGE_18_24",
              "gender": "WOMAN",
              "sexAtBirth": "FEMALE",
              "sexualOrientation": "HETEROSEXUAL",
              "maritalStatus": "SINGLE",
              "race": ["WHITE", "ASIAN"],
              "countryOfBirth": "UNITED_KINGDOM",
              "citizenship": "UNITED_KINGDOM",
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
              "parent": "NO",
              "newsFrequency": 6,
              "hasPet": true,
              "petType": "DOG",
              "chronotype": "NIGHT_OWL",
              "outlook": "OPTIMIST",
              "neurodivergent": true,
              "neurodivergenceType": "ADHD",
              "hasDisability": false,
              "disabilityType": null,
              "housingStatus": "OWN",
              "propertyType": "FLAT"
            }
            """;
    }

    @Test
    @TestSecurity(user = BLANK, roles = {"user"})
    public void notOnboardedReturns204() {
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
                .body("ageRange", equalTo("AGE_18_24"))
                .body("politicalPersuasion", equalTo("CENTRE_LEFT"))
                .body("personalIncomeRange", equalTo("BELOW_20K"))
                .body("householdIncomeRange", equalTo("BETWEEN_100K_AND_150K"))
                .body("race", hasItems("WHITE", "ASIAN"))
                .body("race.size()", is(2))
                .body("hasPet", equalTo(true))
                .body("petType", equalTo("DOG"));

        // Read back
        given()
                .when().get(BASE + "/me")
                .then()
                .statusCode(200)
                .body("userId", equalTo(5))
                .body("country", equalTo("United Kingdom"))
                .body("city", equalTo("Leeds"))
                .body("urbanRural", equalTo("URBAN"))
                .body("maritalStatus", equalTo("SINGLE"))
                .body("religion", equalTo("NO_RELIGION"))
                .body("newsFrequency", equalTo(6))
                .body("hasPet", equalTo(true))
                .body("petType", equalTo("DOG"))
                .body("chronotype", equalTo("NIGHT_OWL"))
                .body("outlook", equalTo("OPTIMIST"))
                .body("neurodivergent", equalTo(true))
                .body("neurodivergenceType", equalTo("ADHD"))
                .body("hasDisability", equalTo(false))
                .body("disabilityType", nullValue())
                .body("housingStatus", equalTo("OWN"))
                .body("propertyType", equalTo("FLAT"))
                .body("race", hasItems("WHITE", "ASIAN"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void petTypeIsClearedWhenUserHasNoPet() {
        // A non-owner who still sends a petType must have it dropped server-side.
        String noPet = validBody()
                .replace("\"hasPet\": true", "\"hasPet\": false");
        given()
                .contentType(ContentType.JSON)
                .body(noPet)
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("hasPet", equalTo(false))
                .body("petType", nullValue());
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void petOwnerWithoutPetTypeIsRejected() {
        // hasPet true but no petType is invalid — the type is required for owners.
        String missingType = validBody().replace("\"petType\": \"DOG\"", "\"petType\": null");
        given()
                .contentType(ContentType.JSON)
                .body(missingType)
                .when().post(BASE)
                .then()
                .statusCode(400);
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
                .statusCode(400);
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
                .body("neurodivergenceType", equalTo("ADHD"));
        // ...then re-save as NOT neurodivergent with a stray type still in the body: the previously
        // stored type must be nulled (proves the force-null else-branch on the update path).
        String notNd = validBody().replace("\"neurodivergent\": true", "\"neurodivergent\": false");
        given()
                .contentType(ContentType.JSON)
                .body(notNd)
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("neurodivergent", equalTo(false))
                .body("neurodivergenceType", nullValue());
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void neurodivergentWithoutTypeIsRejected() {
        // neurodivergent true but no type is invalid — the type is required when the flag is set.
        String body = validBody().replace("\"neurodivergenceType\": \"ADHD\"", "\"neurodivergenceType\": null");
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post(BASE)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void disabilityTypeIsRequiredWhenHasDisability() {
        // Flip to having a disability but omit the type — must be rejected.
        String body = validBody()
                .replace("\"hasDisability\": false", "\"hasDisability\": true");
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post(BASE)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void disabilityTypeIsStoredWhenHasDisability() {
        String body = validBody()
                .replace("\"hasDisability\": false", "\"hasDisability\": true")
                .replace("\"disabilityType\": null", "\"disabilityType\": \"HEARING\"");
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("hasDisability", equalTo(true))
                .body("disabilityType", equalTo("HEARING"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void disabilityTypeIsClearedWhenNoDisability() {
        // First store a disability + type...
        String withDisability = validBody()
                .replace("\"hasDisability\": false", "\"hasDisability\": true")
                .replace("\"disabilityType\": null", "\"disabilityType\": \"HEARING\"");
        given()
                .contentType(ContentType.JSON)
                .body(withDisability)
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("hasDisability", equalTo(true))
                .body("disabilityType", equalTo("HEARING"));
        // ...then re-save with no disability but a stray type in the body: it must be nulled.
        String noDisability = withDisability.replace("\"hasDisability\": true", "\"hasDisability\": false");
        given()
                .contentType(ContentType.JSON)
                .body(noDisability)
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("hasDisability", equalTo(false))
                .body("disabilityType", nullValue());
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
                .statusCode(400);
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
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void propertyTypeIsClearedWhenNotAnOwner() {
        // First store a property type as an owner...
        given()
                .contentType(ContentType.JSON)
                .body(validBody())
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("housingStatus", equalTo("OWN"))
                .body("propertyType", equalTo("FLAT"));
        // ...then re-save as a renter with a stray property type still in the body: the previously
        // stored type must be nulled (proves the force-null else-branch on the update path).
        String renter = validBody().replace("\"housingStatus\": \"OWN\"", "\"housingStatus\": \"RENT\"");
        given()
                .contentType(ContentType.JSON)
                .body(renter)
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("housingStatus", equalTo("RENT"))
                .body("propertyType", nullValue());
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void ownerWithoutPropertyTypeIsRejected() {
        // housingStatus OWN but no propertyType is invalid — the type is required for owners.
        String body = validBody().replace("\"propertyType\": \"FLAT\"", "\"propertyType\": null");
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post(BASE)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void missingHousingStatusIsRejected() {
        String missing = validBody().replace("\"housingStatus\": \"OWN\",", "");
        given()
                .contentType(ContentType.JSON)
                .body(missing)
                .when().post(BASE)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void bodyIdentityIsNeverTrusted() {
        // Even though the body says userId 9999 / id 8888, the saved row belongs to nora (5).
        given()
                .contentType(ContentType.JSON)
                .body(validBody())
                .when().post(BASE)
                .then()
                .statusCode(201)
                .body("userId", not(equalTo(9999)))
                .body("id", not(equalTo(8888)))
                .body("userId", equalTo(5));
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
                .body("race", contains("WHITE"));
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void invalidEnumValueIsRejected() {
        String bad = validBody().replace("\"AGE_18_24\"", "\"AGE_999\"");
        given()
                .contentType(ContentType.JSON)
                .body(bad)
                .when().post(BASE)
                .then()
                .statusCode(400);
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
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = NORA, roles = {"user"})
    public void emptyRaceIsRejected() {
        String emptyRace = validBody().replace("[\"WHITE\", \"ASIAN\"]", "[]");
        given()
                .contentType(ContentType.JSON)
                .body(emptyRace)
                .when().post(BASE)
                .then()
                .statusCode(400);
    }

    @Test
    public void requiresAuthentication() {
        given().when().get(BASE + "/me").then().statusCode(401);
    }
}
