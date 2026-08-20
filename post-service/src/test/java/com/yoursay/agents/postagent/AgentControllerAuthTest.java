package com.yoursay.agents.postagent;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

import java.util.UUID;

@QuarkusTest
class AgentControllerAuthTest {

    @Test
    void anonymousCallerCannotUseDraftEndpoints() {
        UUID id = UUID.randomUUID();
        given()
                .contentType("application/json")
                .body("{\"request\":\"Cover a current policy dispute.\"}")
                .when().post("/agent/drafts")
                .then()
                .statusCode(401);
        given().when().get("/agent/drafts/latest").then().statusCode(401);
        given().header("X-Pepper-Replica", "replica-a")
                .when().get("/agent/drafts/" + id + "/events").then().statusCode(401);
        given().contentType("application/json")
                .body("{\"version\":1,\"content\":null}")
                .when().put("/agent/drafts/" + id).then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void authenticatedCallerWithoutUserRoleCannotUseDraftEndpoints() {
        UUID id = UUID.randomUUID();
        given().contentType("application/json")
                .body("{\"request\":\"Cover a current policy dispute.\"}")
                .when().post("/agent/drafts").then().statusCode(403);
        given().when().get("/agent/drafts/latest").then().statusCode(403);
        given().header("X-Pepper-Replica", "replica-a")
                .when().get("/agent/drafts/" + id + "/events").then().statusCode(403);
        given().contentType("application/json")
                .body("{\"version\":1,\"content\":null}")
                .when().put("/agent/drafts/" + id).then().statusCode(403);
    }
}
