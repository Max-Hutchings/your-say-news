package com.yoursay;


import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.SecurityAttribute;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;


import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;


@QuarkusTest
public class YourSayUserControllerTest {

    final String BASE_URL = "/your-say-user";


    @Test
    @TestSecurity(user="max@gmail.com", roles={"user"}, attributes = {@SecurityAttribute(key = "given_name", value="max"), @SecurityAttribute(key="family_name", value="rax")})
    public void saveUser() {
        String body = """
                {
                  "birthDate": "2001-03-17"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(BASE_URL + "/save")
                .then()
                .statusCode(201);
    }

    @Test
    @TestSecurity(user="nora.new@example.com", roles={"user"})
    public void recordConsentStampsTimeAndVersion() {
        given()
                .contentType(ContentType.JSON)
                .body("{ \"privacyPolicyVersion\": \"2026-06-01\" }")
                .when()
                .post(BASE_URL + "/consent")
                .then()
                .statusCode(200)
                .body("email", equalTo("nora.new@example.com"))
                .body("privacyPolicyVersion", equalTo("2026-06-01"))
                .body("consentedAt", notNullValue());
    }

    @Test
    @TestSecurity(user="test@example.com", roles={"user"})
    public void testGetUserById() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get(BASE_URL + "/id/1")
                .then()
                .statusCode(200)
                .body("id", equalTo(1))
                .body("email", equalTo("john.doe@example.com"))
                .body("firstName", equalTo("John"))
                .body("lastName", equalTo("Doe"))
                .body("dateOfBirth", equalTo("1990-05-15"))
                .body("active", equalTo(true));
    }

    @Test
    @TestSecurity(user="test@example.com", roles={"user"})
    public void testGetUserByIdNotFound() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get(BASE_URL + "/id/999")
                .then()
                .statusCode(204); // Expecting no content when user not found
    }

    @Test
    @TestSecurity(user="test@example.com", roles={"user"})
    public void testGetUserByEmail() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get(BASE_URL + "/email/jane.smith@example.com")
                .then()
                .statusCode(200)
                .body("email", equalTo("jane.smith@example.com"))
                .body("firstName", equalTo("Jane"))
                .body("lastName", equalTo("Smith"))
                .body("dateOfBirth", equalTo("1985-08-22"))
                .body("active", equalTo(true));
    }

    @Test
    @TestSecurity(user="test@example.com", roles={"user"})
    public void testGetUserByEmailNotFound() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get(BASE_URL + "/email/nonexistent@example.com")
                .then()
                .statusCode(204); // Expecting no content when user not found
    }

    @Test
    @TestSecurity(user="test@example.com", roles={"user"})
    public void testGetInactiveUser() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get(BASE_URL + "/id/3")
                .then()
                .statusCode(200)
                .body("email", equalTo("bob.johnson@example.com"))
                .body("active", equalTo(false));
    }

    @Test
    @TestSecurity(user="blank.user@example.com", roles={"user"})
    public void onboardingIsFalseWithNeitherConsentNorCharacteristics() {
        given()
                .when()
                .get(BASE_URL + "/onboarding")
                .then()
                .statusCode(200)
                .body("consented", equalTo(false))
                .body("hasCharacteristics", equalTo(false))
                .body("onboarded", equalTo(false));
    }

    @Test
    @TestSecurity(user="john.doe@example.com", roles={"user"})
    public void onboardingRequiresConsentToo_johnHasCharacteristicsButHasNotConsented() {
        // John has a seeded characteristic profile but has never consented — so NOT onboarded.
        given()
                .when()
                .get(BASE_URL + "/onboarding")
                .then()
                .statusCode(200)
                .body("hasCharacteristics", equalTo(true))
                .body("consented", equalTo(false))
                .body("onboarded", equalTo(false));
    }

    @Test
    @TestSecurity(user="jane.smith@example.com", roles={"user"})
    public void onboardingBecomesTrueOnceAConsentedUserHasCharacteristics() {
        // Jane has a seeded characteristic profile; once she records consent she is fully onboarded.
        given()
                .contentType(ContentType.JSON)
                .body("{ \"privacyPolicyVersion\": \"2026-06-01\" }")
                .when()
                .post(BASE_URL + "/consent")
                .then()
                .statusCode(200);

        given()
                .when()
                .get(BASE_URL + "/onboarding")
                .then()
                .statusCode(200)
                .body("consented", equalTo(true))
                .body("hasCharacteristics", equalTo(true))
                .body("onboarded", equalTo(true));
    }

    @Test
    @TestSecurity(user="intruder@example.com", roles={"guest"})
    public void onboardingRejectsCallersWithoutTheUserRole() {
        given()
                .when()
                .get(BASE_URL + "/onboarding")
                .then()
                .statusCode(anyOf(is(401), is(403)));
    }

}

