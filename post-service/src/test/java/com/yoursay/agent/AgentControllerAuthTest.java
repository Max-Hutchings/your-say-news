package com.yoursay.agent;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

import java.util.UUID;

@QuarkusTest
class AgentControllerAuthTest {

    @Test
    void anonymousCallerCannotStartJob() {
        given()
                .contentType("application/json")
                .body("{\"request\":\"Cover a current policy dispute.\"}")
                .when().post("/agent/jobs")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void authenticatedCallerWithoutUserRoleCannotStartJob() {
        given()
                .contentType("application/json")
                .body("{\"request\":\"Cover a current policy dispute.\"}")
                .when().post("/agent/jobs")
                .then()
                .statusCode(403);
    }

    @Test
    void anonymousCallerCannotPollJob() {
        given()
                .when().get("/agent/jobs/" + UUID.randomUUID())
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void authenticatedCallerWithoutUserRoleCannotPollJob() {
        given()
                .when().get("/agent/jobs/" + UUID.randomUUID())
                .then()
                .statusCode(403);
    }
}
