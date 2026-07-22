package com.yoursay.votes;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Auth guard tests for the vote endpoints. No class-level @TestSecurity so every request that
 * does not carry a method-level override is unauthenticated — this is the only reliable way to
 * test 401 responses in Quarkus (a class-level @TestSecurity injects an identity for every
 * request and cannot be removed per-method, so .auth().none() in RestAssured does not help).
 */
@QuarkusTest
public class VoteControllerAuthTest {

    // ── unauthenticated ───────────────────────────────────────────────────────

    @Test
    public void castVote_unauthenticated_returns401() {
        given()
                .contentType("application/json")
                .body("{\"postId\":999,\"optionId\":9991}")
                .when().post("/votes")
                .then()
                .statusCode(401);
    }

    @Test
    public void getMyVote_unauthenticated_returns401() {
        given()
                .when().get("/votes/999/mine")
                .then()
                .statusCode(401);
    }

    @Test
    public void countForPost_unauthenticated_returns401() {
        given()
                .when().get("/votes/999/count")
                .then()
                .statusCode(401);
    }

    @Test
    public void overallSentiment_unauthenticated_returns401() {
        given()
                .when().get("/votes/999/sentiment")
                .then()
                .statusCode(401);
    }

    @Test
    public void sentimentByCharacteristic_unauthenticated_returns401() {
        given()
                .when().get("/votes/999/sentiment/politicalPersuasion")
                .then()
                .statusCode(401);
    }

    // ── wrong role ────────────────────────────────────────────────────────────

    @Test
    @TestSecurity(user = "badactor@yoursay.com", roles = "admin")
    public void castVote_wrongRole_returns403() {
        given()
                .contentType("application/json")
                .body("{\"postId\":999,\"optionId\":9991}")
                .when().post("/votes")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "badactor@yoursay.com", roles = "admin")
    public void getMyVote_wrongRole_returns403() {
        given()
                .when().get("/votes/999/mine")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "badactor@yoursay.com", roles = "admin")
    public void countForPost_wrongRole_returns403() {
        given()
                .when().get("/votes/999/count")
                .then()
                .statusCode(403);
    }
}
