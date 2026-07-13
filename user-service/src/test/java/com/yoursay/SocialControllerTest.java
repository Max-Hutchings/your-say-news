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
    public void followingListPagesNewestFirstWithViewerFollowState() {
        // John (user 1) follows Jane (2), then Bob (3), then Alice (4).
        for (long id : new long[]{2, 3, 4}) {
            given().contentType("application/json").post("/social/follows/" + id).then().statusCode(200);
        }
        try {
            // Full following list: newest follow first (4, 3, 2); the viewer follows all three.
            given()
                    .when().get("/social/1/following")
                    .then()
                    .statusCode(200)
                    .body("items.id", contains(4, 3, 2))
                    .body("items.handle", contains("alice.williams", "bob.johnson", "jane.smith"))
                    .body("items.followedByViewer", contains(true, true, true))
                    .body("hasMore", equalTo(false));

            // Paged: size 2 yields the two newest and flags a further page...
            given()
                    .when().get("/social/1/following?size=2&page=0")
                    .then()
                    .statusCode(200)
                    .body("items.id", contains(4, 3))
                    .body("hasMore", equalTo(true));
            // ...and the second page returns the remainder with no more after it.
            given()
                    .when().get("/social/1/following?size=2&page=1")
                    .then()
                    .statusCode(200)
                    .body("items.id", contains(2))
                    .body("hasMore", equalTo(false));

            // Followers of Jane (2) = just John (1); John does not follow himself, so false.
            given()
                    .when().get("/social/2/followers")
                    .then()
                    .statusCode(200)
                    .body("items.id", contains(1))
                    .body("items.displayName", contains("John Doe"))
                    .body("items.followedByViewer", contains(false))
                    .body("hasMore", equalTo(false));

            // Nobody follows John (1): empty page, no more.
            given()
                    .when().get("/social/1/followers")
                    .then()
                    .statusCode(200)
                    .body("items", hasSize(0))
                    .body("hasMore", equalTo(false));
        } finally {
            // Leave the shared DB as we found it — other tests assume zero follows.
            for (long id : new long[]{2, 3, 4}) {
                given().contentType("application/json").delete("/social/follows/" + id).then().statusCode(200);
            }
        }
    }

    @Test
    public void connectionListsRejectAnonymousCaller() {
        given().when().get("/social/1/followers").then().statusCode(401);
        given().when().get("/social/1/following").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "carol@yoursay.example", roles = "admin")
    public void connectionListsRejectAuthenticatedCallerWithoutUserRole() {
        given().when().get("/social/1/followers").then().statusCode(403);
        given().when().get("/social/1/following").then().statusCode(403);
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
