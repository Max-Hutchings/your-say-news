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
    void activeDatabaseAdminCanListEveryAccountWithoutSensitiveProfileData() {
        List<Map<String, Object>> users = given()
                .when().get(BASE_URL)
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("$");

        org.junit.jupiter.api.Assertions.assertEquals(11, users.size());
        Set<String> exactKeys = Set.of(
                "id", "email", "firstName", "lastName", "displayName",
                "createdDate", "active", "accountType"
        );
        users.forEach(user -> org.junit.jupiter.api.Assertions.assertEquals(exactKeys, user.keySet()));
        org.junit.jupiter.api.Assertions.assertEquals(Map.of(
                "id", 11,
                "email", "admin@yoursay.com",
                "firstName", "YourSay",
                "lastName", "Admin",
                "displayName", "YourSay Admin",
                "createdDate", "2024-06-07",
                "active", true,
                "accountType", "ADMIN"
        ), users.getFirst());
        org.junit.jupiter.api.Assertions.assertEquals("john.doe@example.com", users.getLast().get("email"));
        org.junit.jupiter.api.Assertions.assertEquals("OFFICIAL", users.getLast().get("accountType"));
        org.junit.jupiter.api.Assertions.assertEquals(false,
                users.stream()
                        .filter(user -> user.get("email").equals("bob.johnson@example.com"))
                        .findFirst()
                        .orElseThrow()
                        .get("active"));
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

            assertAccountState(9, "USER", "NONE", true);
        } finally {
            setAccountState(9, "USER", "NONE", true);
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
                    "accountType", "ADMIN"
            ), updated);

            assertAccountState(10, "ADMIN", "NONE", true);
        } finally {
            setAccountState(10, "USER", "NONE", true);
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

            assertAccountState(9, "OFFICIAL", "ACTIVE", true);
        } finally {
            setAccountState(9, "USER", "NONE", true);
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

            assertAccountState(9, "USER", "NONE", false);

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

            assertAccountState(9, "USER", "NONE", true);
        } finally {
            setAccountState(9, "USER", "NONE", true);
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

        assertAccountState(9, "USER", "NONE", true);

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
        setAccountState(10, "USER", "NONE", false);
        try {
            given()
                    .when().get("/your-say-user/onboarding")
                    .then()
                    .statusCode(403)
                    .body("code", equalTo("USER_ACCOUNT_INACTIVE"))
                    .body("message", equalTo("This account is inactive."));
        } finally {
            setAccountState(10, "USER", "NONE", true);
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "user")
    void inactiveAdminCannotUseTheAdminApi() throws Exception {
        setAccountState(11, "ADMIN", "NONE", false);
        try {
            given()
                    .when().get(BASE_URL)
                    .then()
                    .statusCode(403)
                    .body("code", equalTo("USER_ACCOUNT_INACTIVE"));
        } finally {
            setAccountState(11, "ADMIN", "NONE", true);
        }
    }

    @Test
    void unauthenticatedCallerCannotReadAccounts() {
        given()
                .when().get(BASE_URL)
                .then()
                .statusCode(401);
    }

    private void assertAccountState(long userId, String type, String publisherStatus, boolean active)
            throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select account_type, publisher_status, active
                     from your_say_user
                     where id = ?
                     """)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                org.junit.jupiter.api.Assertions.assertEquals(type, result.getString("account_type"));
                org.junit.jupiter.api.Assertions.assertEquals(publisherStatus, result.getString("publisher_status"));
                org.junit.jupiter.api.Assertions.assertEquals(active, result.getBoolean("active"));
                org.junit.jupiter.api.Assertions.assertFalse(result.next());
            }
        }
    }

    private void setAccountState(long userId, String type, String publisherStatus, boolean active)
            throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     update your_say_user
                     set account_type = ?, publisher_status = ?, active = ?
                     where id = ?
                     """)) {
            statement.setString(1, type);
            statement.setString(2, publisherStatus);
            statement.setBoolean(3, active);
            statement.setLong(4, userId);
            statement.executeUpdate();
        }
    }
}
