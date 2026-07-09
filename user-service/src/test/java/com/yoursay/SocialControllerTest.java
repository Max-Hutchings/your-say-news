package com.yoursay;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class SocialControllerTest {

    @Test
    @TestSecurity(user = "john.doe@example.com", roles = "user")
    public void followUnfollowUpdatesStatusAndPublicProfileCounts() {
        given()
                .when().get("/profiles/2")
                .then()
                .statusCode(200)
                .body("id", equalTo(2))
                .body("displayName", equalTo("Jane Smith"))
                .body("handle", equalTo("jane.smith"))
                .body("email", nullValue())
                .body("dateOfBirth", nullValue())
                .body("followerCount", equalTo(0))
                .body("followedByViewer", equalTo(false));

        given()
                .when().get("/social/follows/2")
                .then()
                .statusCode(200)
                .body("userId", equalTo(2))
                .body("following", equalTo(false))
                .body("followerCount", equalTo(0))
                .body("followingCount", equalTo(0));

        given()
                .contentType("application/json")
                .when().post("/social/follows/2")
                .then()
                .statusCode(200)
                .body("userId", equalTo(2))
                .body("following", equalTo(true))
                .body("followerCount", equalTo(1))
                .body("followingCount", equalTo(0));

        given()
                .when().get("/social/following")
                .then()
                .statusCode(200)
                .body("userIds", contains(2));

        given()
                .when().get("/social/follows/2")
                .then()
                .statusCode(200)
                .body("userId", equalTo(2))
                .body("following", equalTo(true))
                .body("followerCount", equalTo(1))
                .body("followingCount", equalTo(0));

        given()
                .when().get("/profiles/2")
                .then()
                .statusCode(200)
                .body("followerCount", equalTo(1))
                .body("followedByViewer", equalTo(true));

        given()
                .contentType("application/json")
                .when().delete("/social/follows/2")
                .then()
                .statusCode(200)
                .body("userId", equalTo(2))
                .body("following", equalTo(false))
                .body("followerCount", equalTo(0));

        given()
                .when().get("/social/follows/2")
                .then()
                .statusCode(200)
                .body("userId", equalTo(2))
                .body("following", equalTo(false))
                .body("followerCount", equalTo(0))
                .body("followingCount", equalTo(0));
    }

    @Test
    @TestSecurity(user = "john.doe@example.com", roles = "user")
    public void usersCannotFollowThemselves() {
        given()
                .contentType("application/json")
                .when().post("/social/follows/1")
                .then()
                .statusCode(400);

        given()
                .when().get("/social/following")
                .then()
                .statusCode(200)
                .body("userIds", not(hasItem(1)));

        given()
                .when().get("/profiles/me")
                .then()
                .statusCode(200)
                .body("followerCount", equalTo(0))
                .body("followingCount", equalTo(0))
                .body("followedByViewer", equalTo(false));
    }

    @Test
    @TestSecurity(user = "john.doe@example.com", roles = "user")
    public void meReturnsAuthenticatedPublicProfileWithoutPii() {
        given()
                .when().get("/profiles/me")
                .then()
                .statusCode(200)
                .body("id", equalTo(1))
                .body("displayName", equalTo("John Doe"))
                .body("handle", equalTo("john.doe"))
                .body("email", nullValue())
                .body("dateOfBirth", nullValue())
                .body("followerCount", equalTo(0))
                .body("followingCount", equalTo(0))
                .body("followedByViewer", equalTo(false));
    }

    @Test
    @TestSecurity(user = "john.doe@example.com", roles = "user")
    public void missingProfileReturnsNoContent() {
        given()
                .when().get("/profiles/999999")
                .then()
                .statusCode(204);
    }

    @Test
    public void socialEndpointsRejectAnonymousCaller() {
        given()
                .when().get("/social/following")
                .then()
                .statusCode(401);

        given()
                .contentType("application/json")
                .when().post("/social/follows/2")
                .then()
                .statusCode(401);

        given()
                .contentType("application/json")
                .when().delete("/social/follows/2")
                .then()
                .statusCode(401);
    }

    @Test
    public void profileEndpointsRejectAnonymousCaller() {
        given()
                .when().get("/profiles/1")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "carol@yoursay.example", roles = "admin")
    public void socialEndpointsRejectAuthenticatedCallerWithoutUserRole() {
        given()
                .when().get("/social/following")
                .then()
                .statusCode(403);

        given()
                .contentType("application/json")
                .when().post("/social/follows/2")
                .then()
                .statusCode(403);

        given()
                .contentType("application/json")
                .when().delete("/social/follows/2")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "carol@yoursay.example", roles = "admin")
    public void profileEndpointsRejectAuthenticatedCallerWithoutUserRole() {
        given()
                .when().get("/profiles/1")
                .then()
                .statusCode(403);
    }
}
