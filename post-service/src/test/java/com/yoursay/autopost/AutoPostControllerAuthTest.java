package com.yoursay.autopost;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;

@QuarkusTest
class AutoPostControllerAuthTest {

    @Test
    void anonymousCallerCannotUseAnyAutoPostEndpoint() {
        UUID runId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        given().when().post("/api/admin/auto-post/runs").then().statusCode(401);
        given().when().get("/api/admin/auto-post/runs").then().statusCode(401);
        given().when().get("/api/admin/auto-post/runs/" + runId).then().statusCode(401);
        given().accept("text/event-stream")
                .when().get("/api/admin/auto-post/runs/" + runId + "/events").then().statusCode(401);
        given().when().post("/api/admin/auto-post/runs/" + runId + "/candidates/" + candidateId + "/select")
                .then().statusCode(401);
        given().when().post("/api/admin/auto-post/runs/" + runId + "/approve").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "jane.smith@example.com", roles = "user")
    void nonAdminRoleCannotUseAnyAutoPostEndpoint() {
        UUID runId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        given().when().post("/api/admin/auto-post/runs").then().statusCode(403);
        given().when().get("/api/admin/auto-post/runs").then().statusCode(403);
        given().when().get("/api/admin/auto-post/runs/" + runId).then().statusCode(403);
        given().accept("text/event-stream")
                .when().get("/api/admin/auto-post/runs/" + runId + "/events").then().statusCode(403);
        given().when().post("/api/admin/auto-post/runs/" + runId + "/candidates/" + candidateId + "/select")
                .then().statusCode(403);
        given().when().post("/api/admin/auto-post/runs/" + runId + "/approve").then().statusCode(403);
    }
}
