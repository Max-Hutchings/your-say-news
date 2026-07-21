package com.yoursay;


import io.agroal.api.AgroalDataSource;
import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.SecurityAttribute;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;


@QuarkusTest
public class YourSayUserControllerTest {

    final String BASE_URL = "/your-say-user";

    @Inject
    AgroalDataSource dataSource;


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
    public void testGetUserByIdReturnsIdOnlyNeverPii() {
        // Lookup endpoints must expose only the anonymised id — never PII — so an authenticated
        // caller cannot harvest the user base by iterating ids.
        given()
                .contentType(ContentType.JSON)
                .when()
                .get(BASE_URL + "/id/1")
                .then()
                .statusCode(200)
                .body("id", equalTo(1))
                .body("email", nullValue())
                .body("firstName", nullValue())
                .body("lastName", nullValue())
                .body("dateOfBirth", nullValue());
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
    public void testGetUserByEmailReturnsIdOnlyNeverPii() {
        // Resolves the email to the internal id for cross-service callers, exposing no PII.
        given()
                .contentType(ContentType.JSON)
                .when()
                .get(BASE_URL + "/email/jane.smith@example.com")
                .then()
                .statusCode(200)
                .body("id", equalTo(2))
                .body("email", nullValue())
                .body("firstName", nullValue())
                .body("lastName", nullValue())
                .body("dateOfBirth", nullValue());
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
    @TestSecurity(user="john.doe@example.com", roles={"user"})
    public void currentAccessIdentifiesAnActiveOfficialPublisher() {
        given()
                .when()
                .get(BASE_URL + "/me/access")
                .then()
                .statusCode(200)
                .body("userId", equalTo(1))
                .body("accountType", equalTo("OFFICIAL"))
                .body("publisherStatus", equalTo("ACTIVE"))
                .body("canPublish", equalTo(true))
                .body("email", nullValue());
    }

    @Test
    @TestSecurity(user="john.doe@example.com", roles={"user"})
    public void currentAccessDeniesPublishingWhenAnOfficialAccountIsInactive() throws Exception {
        setUserActive(1, false);
        try {
            given()
                    .when()
                    .get(BASE_URL + "/me/access")
                    .then()
                    .statusCode(200)
                    .body("userId", equalTo(1))
                    .body("accountType", equalTo("OFFICIAL"))
                    .body("publisherStatus", equalTo("ACTIVE"))
                    .body("canPublish", equalTo(false));
        } finally {
            setUserActive(1, true);
        }
    }

    @Test
    @TestSecurity(user="nora.new@example.com", roles={"user"})
    public void currentAccessKeepsANonAuthorStandardAndUnableToPublish() {
        given()
                .when()
                .get(BASE_URL + "/me/access")
                .then()
                .statusCode(200)
                .body("userId", equalTo(5))
                .body("accountType", equalTo("STANDARD"))
                .body("publisherStatus", equalTo("NONE"))
                .body("canPublish", equalTo(false))
                .body("email", nullValue());
    }

    @Test
    @TestSecurity(user="riley.reader@example.com", roles={"user"})
    public void profiledReaderIsOnboardedButCannotPublish() {
        given()
                .when()
                .get(BASE_URL + "/me/access")
                .then()
                .statusCode(200)
                .body("userId", equalTo(10))
                .body("accountType", equalTo("STANDARD"))
                .body("publisherStatus", equalTo("NONE"))
                .body("canPublish", equalTo(false))
                .body("$", aMapWithSize(4))
                .body("$", not(hasKey("email")))
                .body("$", not(hasKey("firstName")))
                .body("$", not(hasKey("lastName")))
                .body("$", not(hasKey("dateOfBirth")));

        given()
                .when()
                .get(BASE_URL + "/onboarding")
                .then()
                .statusCode(200)
                .body("consented", equalTo(true))
                .body("hasCharacteristics", equalTo(true))
                .body("onboarded", equalTo(true));
    }

    private void setUserActive(long userId, boolean active) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "update your_say_user set active = ? where id = ?")) {
            statement.setBoolean(1, active);
            statement.setLong(2, userId);
            statement.executeUpdate();
        }
    }

    @Test
    @TestSecurity(user="test@example.com", roles={"user"})
    public void testGetInactiveUserStillResolvesToIdOnly() {
        // An inactive user is still resolvable to its id, and still leaks no PII.
        given()
                .contentType(ContentType.JSON)
                .when()
                .get(BASE_URL + "/id/3")
                .then()
                .statusCode(200)
                .body("id", equalTo(3))
                .body("email", nullValue())
                .body("active", nullValue());
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
