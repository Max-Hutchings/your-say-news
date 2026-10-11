package com.yoursay.user;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class AdminUserControllerTest {

    private static final String BASE_URL = "/api/admin/users";

    @Inject
    AgroalDataSource dataSource;

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "user")
    void activeDatabaseAdminCanListEveryAccountWithoutSensitiveProfileData() throws Exception {
        List<Map<String, Object>> users = given()
                .when().get(BASE_URL)
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("$");

        // "Every account" is pinned against the table itself rather than a hard-coded 11, so an
        // account another test legitimately creates cannot turn this into a false failure while a
        // genuinely omitted account still does.
        org.junit.jupiter.api.Assertions.assertEquals(countAccounts(), users.size());
        Set<String> exactKeys = Set.of(
                "id", "email", "firstName", "lastName", "displayName",
                "createdDate", "active", "accountType", "publishingEnabled"
        );
        users.forEach(user -> org.junit.jupiter.api.Assertions.assertEquals(exactKeys, user.keySet()));
        // The repository promises newest first (createdDate desc, id desc). Asserting the ordering
        // itself keeps that contract pinned without depending on which row happens to sit at an
        // index — a newly created account sorts to the front.
        assertOrderedNewestFirst(users);
        org.junit.jupiter.api.Assertions.assertEquals(Map.of(
                "id", 11,
                "email", "admin@yoursay.com",
                "firstName", "YourSay",
                "lastName", "Admin",
                "displayName", "YourSay Admin",
                "createdDate", "2024-06-07",
                "active", true,
                "accountType", "ADMIN",
                "publishingEnabled", false
        ), account(users, "admin@yoursay.com"));
        org.junit.jupiter.api.Assertions.assertEquals("OFFICIAL",
                account(users, "john.doe@example.com").get("accountType"));
        org.junit.jupiter.api.Assertions.assertEquals(true,
                account(users, "john.doe@example.com").get("publishingEnabled"));
        org.junit.jupiter.api.Assertions.assertEquals(false,
                account(users, "bob.johnson@example.com").get("active"));
    }

    private static Map<String, Object> account(List<Map<String, Object>> users, String email) {
        return users.stream()
                .filter(user -> email.equals(user.get("email")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No account listed for " + email));
    }

    private static void assertOrderedNewestFirst(List<Map<String, Object>> users) {
        List<String> sortKeys = users.stream()
                .map(user -> user.get("createdDate") + "|" + String.format("%010d",
                        ((Number) user.get("id")).longValue()))
                .toList();
        org.junit.jupiter.api.Assertions.assertEquals(
                sortKeys.stream().sorted(java.util.Comparator.reverseOrder()).toList(),
                sortKeys,
                "Accounts must be listed newest first by createdDate then id");
    }

    private long countAccounts() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement("select count(*) from your_say_user");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    @Test
    @TestSecurity(user = "jane.smith@example.com", roles = "user")
    void officialAccountCannotReadOrUpdateThroughTheAdminApi() throws Exception {
        try {
            given()
                    .when().get(BASE_URL)
                    .then()
                    .statusCode(403)
                    .body("code", equalTo("USER_ADMIN_ACCESS_REQUIRED"));

            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {"accountType":"ADMIN","active":false}
                            """)
                    .when().put(BASE_URL + "/9")
                    .then()
                    .statusCode(403)
                    .body("code", equalTo("USER_ADMIN_ACCESS_REQUIRED"));

            assertAccountState(9, "USER", false, true);
        } finally {
            setAccountState(9, "USER", false, true);
        }
    }

    @Test
    @TestSecurity(user = "jane.smith@example.com", roles = "admin")
    void legacyKeycloakAdminRoleDoesNotOverrideDatabaseAccountType() {
        given()
                .when().get(BASE_URL)
                .then()
                .statusCode(403)
                .body("code", equalTo("USER_ADMIN_ACCESS_REQUIRED"));
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com")
    void databaseAdminStillRequiresAnAuthenticatedApplicationRole() {
        given()
                .when().get(BASE_URL)
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "user")
    void adminCanPromoteAUserToAdmin() throws Exception {
        try {
            Map<String, Object> updated = given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {"accountType":"ADMIN","active":true}
                            """)
                    .when().put(BASE_URL + "/10")
                    .then()
                    .statusCode(200)
                    .extract().jsonPath().getMap("$");

            org.junit.jupiter.api.Assertions.assertEquals(Map.of(
                    "id", 10,
                    "email", "riley.reader@example.com",
                    "firstName", "Riley",
                    "lastName", "Reader",
                    "displayName", "Riley Reader",
                    "createdDate", "2024-06-06",
                    "active", true,
                    "accountType", "ADMIN",
                    "publishingEnabled", false
            ), updated);

            assertAccountState(10, "ADMIN", false, true);
        } finally {
            setAccountState(10, "USER", false, true);
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "user")
    void adminCanGrantAndRevokePublishingForAnAdmin() throws Exception {
        setAccountState(10, "ADMIN", false, true);
        try {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {"accountType":"ADMIN","active":true,"publishingEnabled":true}
                            """)
                    .when().put(BASE_URL + "/10")
                    .then()
                    .statusCode(200)
                    .body("accountType", equalTo("ADMIN"))
                    .body("publishingEnabled", equalTo(true));
            assertAccountState(10, "ADMIN", true, true);

            // Leaving publishingEnabled out keeps the current setting when the type is unchanged.
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {"accountType":"ADMIN","active":true}
                            """)
                    .when().put(BASE_URL + "/10")
                    .then()
                    .statusCode(200)
                    .body("publishingEnabled", equalTo(true));
            assertAccountState(10, "ADMIN", true, true);

            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {"accountType":"ADMIN","active":true,"publishingEnabled":false}
                            """)
                    .when().put(BASE_URL + "/10")
                    .then()
                    .statusCode(200)
                    .body("publishingEnabled", equalTo(false));
            assertAccountState(10, "ADMIN", false, true);
        } finally {
            setAccountState(10, "USER", false, true);
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "user")
    void changingAccountTypeResetsPublishingToTheNewTypesDefault() throws Exception {
        setAccountState(10, "ADMIN", true, true);
        try {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {"accountType":"OFFICIAL","active":true}
                            """)
                    .when().put(BASE_URL + "/10")
                    .then()
                    .statusCode(200)
                    .body("publishingEnabled", equalTo(true));
            assertAccountState(10, "OFFICIAL", true, true);

            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {"accountType":"ADMIN","active":true}
                            """)
                    .when().put(BASE_URL + "/10")
                    .then()
                    .statusCode(200)
                    .body("publishingEnabled", equalTo(false));
            assertAccountState(10, "ADMIN", false, true);

            // An explicit value in the same request wins over the new type's default.
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {"accountType":"OFFICIAL","active":true,"publishingEnabled":false}
                            """)
                    .when().put(BASE_URL + "/10")
                    .then()
                    .statusCode(200)
                    .body("publishingEnabled", equalTo(false));
            assertAccountState(10, "OFFICIAL", false, true);
        } finally {
            setAccountState(10, "USER", false, true);
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "user")
    void publishingCannotBeEnabledForAUserAccount() throws Exception {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"accountType":"USER","active":true,"publishingEnabled":true}
                        """)
                .when().put(BASE_URL + "/9")
                .then()
                .statusCode(400)
                .body("code", equalTo("USER_ACCOUNT_CANNOT_PUBLISH"));

        assertAccountState(9, "USER", false, true);
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "user")
    void demotingAPublisherToUserWhileKeepingPublishingIsRefusedAndChangesNothing() throws Exception {
        setAccountState(9, "OFFICIAL", true, true);
        try {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {"accountType":"USER","active":true,"publishingEnabled":true}
                            """)
                    .when().put(BASE_URL + "/9")
                    .then()
                    .statusCode(400)
                    .body("code", equalTo("USER_ACCOUNT_CANNOT_PUBLISH"));

            assertAccountState(9, "OFFICIAL", true, true);
        } finally {
            setAccountState(9, "USER", false, true);
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "user")
    void demotingAPublishingAdminToUserSwitchesPublishingOff() throws Exception {
        setAccountState(10, "ADMIN", true, true);
        try {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {"accountType":"USER","active":true}
                            """)
                    .when().put(BASE_URL + "/10")
                    .then()
                    .statusCode(200)
                    .body("accountType", equalTo("USER"))
                    .body("publishingEnabled", equalTo(false));

            assertAccountState(10, "USER", false, true);
        } finally {
            setAccountState(10, "USER", false, true);
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "user")
    void promotingAUserToOfficialMakesThemAnActivePoster() throws Exception {
        try {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {"accountType":"OFFICIAL","active":true}
                            """)
                    .when().put(BASE_URL + "/9")
                    .then()
                    .statusCode(200)
                    .body("accountType", equalTo("OFFICIAL"))
                    .body("active", equalTo(true));

            assertAccountState(9, "OFFICIAL", true, true);
        } finally {
            setAccountState(9, "USER", false, true);
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "user")
    void adminCanDeactivateAndReactivateAnAccount() throws Exception {
        try {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {"accountType":"USER","active":false}
                            """)
                    .when().put(BASE_URL + "/9")
                    .then()
                    .statusCode(200)
                    .body("accountType", equalTo("USER"))
                    .body("active", equalTo(false));

            assertAccountState(9, "USER", false, false);

            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {"accountType":"USER","active":true}
                            """)
                    .when().put(BASE_URL + "/9")
                    .then()
                    .statusCode(200)
                    .body("accountType", equalTo("USER"))
                    .body("active", equalTo(true));

            assertAccountState(9, "USER", false, true);
        } finally {
            setAccountState(9, "USER", false, true);
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "user")
    void updateValidatesTheCompleteAccountStateAndUnknownUser() throws Exception {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"accountType":"USER"}
                        """)
                .when().put(BASE_URL + "/9")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"accountType":"MODERATOR","active":true}
                        """)
                .when().put(BASE_URL + "/9")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_REQUEST_VALUE"))
                .body("message", equalTo("Invalid request."));

        assertAccountState(9, "USER", false, true);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"accountType":"USER","active":true}
                        """)
                .when().put(BASE_URL + "/99999")
                .then()
                .statusCode(404)
                .body("code", equalTo("USER_NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "riley.reader@example.com", roles = "user")
    void inactiveAccountIsRejectedAcrossAuthenticatedFunctionality() throws Exception {
        setAccountState(10, "USER", false, false);
        try {
            given()
                    .when().get("/your-say-user/onboarding")
                    .then()
                    .statusCode(403)
                    .body("code", equalTo("USER_ACCOUNT_INACTIVE"))
                    .body("message", equalTo("This account is inactive."));
        } finally {
            setAccountState(10, "USER", false, true);
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "user")
    void inactiveAdminCannotUseTheAdminApi() throws Exception {
        setAccountState(11, "ADMIN", false, false);
        try {
            given()
                    .when().get(BASE_URL)
                    .then()
                    .statusCode(403)
                    .body("code", equalTo("USER_ACCOUNT_INACTIVE"));
        } finally {
            setAccountState(11, "ADMIN", false, true);
        }
    }

    @Test
    void unauthenticatedCallerCannotReadAccounts() {
        given()
                .when().get(BASE_URL)
                .then()
                .statusCode(401);
    }

    private void assertAccountState(long userId, String type, boolean publishingEnabled, boolean active)
            throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select account_type, publishing_enabled, active
                     from your_say_user
                     where id = ?
                     """)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                org.junit.jupiter.api.Assertions.assertEquals(type, result.getString("account_type"));
                org.junit.jupiter.api.Assertions.assertEquals(publishingEnabled, result.getBoolean("publishing_enabled"));
                org.junit.jupiter.api.Assertions.assertEquals(active, result.getBoolean("active"));
                org.junit.jupiter.api.Assertions.assertFalse(result.next());
            }
        }
    }

    private void setAccountState(long userId, String type, boolean publishingEnabled, boolean active)
            throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     update your_say_user
                     set account_type = ?, publishing_enabled = ?, active = ?
                     where id = ?
                     """)) {
            statement.setString(1, type);
            statement.setBoolean(2, publishingEnabled);
            statement.setBoolean(3, active);
            statement.setLong(4, userId);
            statement.executeUpdate();
        }
    }
}
