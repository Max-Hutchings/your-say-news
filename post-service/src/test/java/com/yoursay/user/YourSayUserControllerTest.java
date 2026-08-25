package com.yoursay.user;


import com.yoursay.user.user.YourSayUserService;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.SecurityAttribute;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;


@QuarkusTest
public class YourSayUserControllerTest {

    final String BASE_URL = "/your-say-user";

    @Inject
    AgroalDataSource dataSource;

    @Inject
    YourSayUserService userService;


    @Test
    @TestSecurity(user="max@gmail.com", roles={"user"}, attributes = {@SecurityAttribute(key = "given_name", value="max"), @SecurityAttribute(key="family_name", value="rax")})
    public void saveUser() throws Exception {
        String body = """
                {
                  "birthDate": "2001-03-17"
                }
                """;

        try {
            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .post(BASE_URL + "/save")
                    .then()
                    .statusCode(201)
                    .body("email", equalTo("max@gmail.com"))
                    .body("firstName", equalTo("max"))
                    .body("lastName", equalTo("rax"))
                    .body("dateOfBirth", equalTo("2001-03-17"))
                    .body("active", equalTo(true))
                    .body("accountType", equalTo("USER"))
                    .body("publisherStatus", equalTo("NONE"))
                    .body("canPublish", equalTo(false));
        } finally {
            // your_say_user is shared by every test in the JVM, so the account this test creates
            // has to go back out again — otherwise whichever test runs next sees an extra row.
            deleteUserByEmail("max@gmail.com");
        }
    }

    @Test
    @TestSecurity(user="nora.new@example.com", roles={"user"})
    public void recordConsentStampsTimeAndVersion() throws Exception {
        Instant before = Instant.now();
        String consentedAt = given()
                .contentType(ContentType.JSON)
                .body("{ \"privacyPolicyVersion\": \"2026-06-01\" }")
                .when()
                .post(BASE_URL + "/consent")
                .then()
                .statusCode(200)
                .body("email", equalTo("nora.new@example.com"))
                .body("privacyPolicyVersion", equalTo("2026-06-01"))
                .body("consentedAt", notNullValue())
                .extract().path("consentedAt");
        Instant after = Instant.now();

        Instant responseTime = Instant.parse(consentedAt);
        org.junit.jupiter.api.Assertions.assertFalse(responseTime.isBefore(before));
        org.junit.jupiter.api.Assertions.assertFalse(responseTime.isAfter(after));
        assertPersistedConsent("nora.new@example.com", responseTime, "2026-06-01");
    }

    @Test
    @TestSecurity(user="casey.morgan@example.com", roles={"user"})
    public void recordConsentRejectsMissingBlankAndUnknownPolicyVersions() throws Exception {
        for (String body : java.util.List.of(
                "{}",
                "{ \"privacyPolicyVersion\": \"\" }",
                "{ \"privacyPolicyVersion\": \"2025-01-01\" }")) {
            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .post(BASE_URL + "/consent")
                    .then()
                    .statusCode(400)
                    .body("code", equalTo("VALIDATION_FAILED"));
        }

        assertPersistedConsent("casey.morgan@example.com", null, null);
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
                .body("$", aMapWithSize(1))
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
    @TestSecurity(user="jane.smith@example.com", roles={"user"})
    public void testGetUserByEmailReturnsIdOnlyNeverPii() {
        // A subject can resolve only their own internal id, and the response contains exactly that id.
        given()
                .contentType(ContentType.JSON)
                .when()
                .get(BASE_URL + "/email/jane.smith@example.com")
                .then()
                .statusCode(200)
                .body("id", equalTo(2))
                .body("$", aMapWithSize(1))
                .body("email", nullValue())
                .body("firstName", nullValue())
                .body("lastName", nullValue())
                .body("dateOfBirth", nullValue());
    }

    @Test
    @TestSecurity(user="nonexistent@example.com", roles={"user"})
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
    public void testGetUserByEmailRejectsAnotherUsersIdentityLookup() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get(BASE_URL + "/email/jane.smith@example.com")
                .then()
                .statusCode(403)
                .body("code", equalTo("USER_SUBJECT_LOOKUP_FORBIDDEN"));
    }

    @Test
    @TestSecurity(user="jane.smith@example.com", roles={"user"})
    public void currentAccessIdentifiesAnActiveOfficialPublisher() {
        given()
                .when()
                .get(BASE_URL + "/me/access")
                .then()
                .statusCode(200)
                .body("userId", equalTo(2))
                .body("accountType", equalTo("OFFICIAL"))
                .body("publisherStatus", equalTo("ACTIVE"))
                .body("canPublish", equalTo(true))
                .body("email", nullValue());
    }

    @Test
    @TestSecurity(user="jane.smith@example.com", roles={"user"})
    public void currentAccessDeniesPublishingWhenAnOfficialAccountIsInactive() throws Exception {
        setUserActive(2, false);
        try {
            given()
                    .when()
                    .get(BASE_URL + "/me/access")
                    .then()
                    .statusCode(403)
                    .body("code", equalTo("USER_ACCOUNT_INACTIVE"))
                    .body("message", equalTo("This account is inactive."));
        } finally {
            setUserActive(2, true);
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
                .body("accountType", equalTo("USER"))
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
                .body("accountType", equalTo("USER"))
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

    /**
     * Postgres timestamptz resolves to microseconds. Linux clocks give {@code Instant.now()}
     * nanosecond precision, so the surplus digits cannot survive the write; macOS clocks only offer
     * microseconds and the value round-trips untouched — which is why comparing the two for exact
     * equality passes on a developer machine and fails on CI. Which way the surplus goes is not
     * fixed either: sent as text the server rounds, sent as binary the driver truncates, and pgjdbc
     * only switches to binary once a statement has been server-prepared. So assert the two things
     * that are actually guaranteed — the stored stamp holds microsecond precision, and it agrees
     * with the returned stamp to within the one microsecond Postgres can represent.
     */
    private static void assertStampMatchesToPostgresResolution(Instant persisted, Instant returned) {
        org.junit.jupiter.api.Assertions.assertNotNull(persisted, "consent must be stamped");
        org.junit.jupiter.api.Assertions.assertEquals(0, persisted.getNano() % 1_000,
                () -> "Stored stamp must be microsecond precision, was " + persisted);
        org.junit.jupiter.api.Assertions.assertTrue(
                Duration.between(persisted, returned).abs()
                        .compareTo(Duration.ofNanos(1_000)) < 0,
                () -> "Stored stamp " + persisted + " must match the returned " + returned
                        + " to the microsecond Postgres can hold");
    }

    @Test
    public void usernamesByIdsReturnsPublicHandlesForTheIdsAskedAndNothingElse() throws Exception {
        // Other domains label content with the author's handle through this lookup, so it must
        // return the handle for every id it knows and simply omit the ones it does not.
        long amina = insertUserWithHandle("amina.lookup@example.com", "Amina", "Khan", "amina.k.lookup");
        long sam = insertUserWithHandle("sam.lookup@example.com", "Sam", "Okafor", "sam.o.lookup");
        long closed = insertUserWithHandle("theo.lookup@example.com", "Theo", "Adeyemi", "theo.a.lookup");
        setUserActive(closed, false);
        try {
            Map<Long, String> usernames = userService.usernamesByIds(List.of(amina, sam, closed, 987654L));

            // A deactivated account keeps its handle, so its existing stories stay attributed
            // rather than silently losing their author.
            assertEquals(
                    Map.of(amina, "amina.k.lookup", sam, "sam.o.lookup", closed, "theo.a.lookup"),
                    usernames);
        } finally {
            deleteUserByEmail("amina.lookup@example.com");
            deleteUserByEmail("sam.lookup@example.com");
            deleteUserByEmail("theo.lookup@example.com");
        }
    }

    @Test
    public void usernamesByIdsReturnsNothingWhenAskedForNoIds() {
        assertEquals(Map.of(), userService.usernamesByIds(List.of()));
        assertEquals(Map.of(), userService.usernamesByIds(null));
    }

    private long insertUserWithHandle(String email, String firstName, String lastName, String handle)
            throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into your_say_user(
                         email, first_name, last_name, display_name, handle, created_date, active)
                     values (?, ?, ?, ?, ?, now(), true)
                     returning id
                     """)) {
            statement.setString(1, email);
            statement.setString(2, firstName);
            statement.setString(3, lastName);
            statement.setString(4, firstName + " " + lastName);
            statement.setString(5, handle);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private void deleteUserByEmail(String email) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "delete from your_say_user where email = ?")) {
            statement.setString(1, email);
            statement.executeUpdate();
        }
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

    private void assertPersistedConsent(
            String email,
            Instant expectedTime,
            String expectedVersion
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select consented_at, privacy_policy_version
                     from your_say_user
                     where email = ?
                     """)) {
            statement.setString(1, email);
            try (ResultSet result = statement.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                OffsetDateTime stored = result.getObject("consented_at", OffsetDateTime.class);
                Instant persisted = stored == null ? null : stored.toInstant();
                if (expectedTime == null) {
                    // Rejected requests must leave the account unstamped.
                    org.junit.jupiter.api.Assertions.assertNull(persisted);
                } else {
                    assertStampMatchesToPostgresResolution(persisted, expectedTime);
                }
                org.junit.jupiter.api.Assertions.assertEquals(
                        expectedVersion,
                        result.getString("privacy_policy_version")
                );
                org.junit.jupiter.api.Assertions.assertFalse(result.next());
            }
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
