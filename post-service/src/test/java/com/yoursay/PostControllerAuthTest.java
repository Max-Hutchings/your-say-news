package com.yoursay;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Posts endpoints are auth-gated. Most tests here send no identity, so the global authenticated
 * permission must reject them (401); one sends an authenticated caller lacking the required "user"
 * role, which the class-level @RolesAllowed("user") must reject (403).
 */
@QuarkusTest
public class PostControllerAuthTest {

    @Test
    public void recentFeedRejectsAnonymousCaller() {
        given()
                .when().get("/posts")
                .then()
                .statusCode(401);
    }

    @Test
    public void socialFeedRejectsAnonymousCaller() {
        given()
                .when().get("/feed")
                .then()
                .statusCode(401);
    }

    @Test
    public void getByIdRejectsAnonymousCaller() {
        given()
                .when().get("/posts/1")
                .then()
                .statusCode(401);
    }

    @Test
    public void listByUserRejectsAnonymousCaller() {
        given()
                .when().get("/posts/user/1")
                .then()
                .statusCode(401);
    }

    @Test
    public void presignRejectsAnonymousCaller() {
        given()
                .contentType("application/json")
                .body("{ \"mediaType\": \"IMAGE\", \"contentType\": \"image/jpeg\" }")
                .when().post("/posts/media/presign")
                .then()
                .statusCode(401);
    }

    @Test
    public void createRejectsAnonymousCaller() {
        given()
                .contentType("application/json")
                .body("{ \"title\": \"x\", \"summary\": \"y\", \"supportQuestion\": \"z\", \"media\": [] }")
                .when().post("/posts")
                .then()
                .statusCode(401);
    }

    // Authenticated, but with a role other than "user": the @RolesAllowed("user") guard must forbid
    // it (403) before any handler runs — proves the guard pins the *role*, not merely "any identity".
    @Test
    @TestSecurity(user = "carol@yoursay.example", roles = "admin")
    public void createRejectsAuthenticatedCallerWithoutUserRole() {
        given()
                .contentType("application/json")
                .body("{ \"title\": \"x\", \"summary\": \"y\", \"supportQuestion\": \"z\", \"media\": [] }")
                .when().post("/posts")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "carol@yoursay.example", roles = "admin")
    public void recentFeedRejectsAuthenticatedCallerWithoutUserRole() {
        given()
                .when().get("/posts")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "carol@yoursay.example", roles = "admin")
    public void socialFeedRejectsAuthenticatedCallerWithoutUserRole() {
        given()
                .when().get("/feed")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "carol@yoursay.example", roles = "admin")
    public void getByIdRejectsAuthenticatedCallerWithoutUserRole() {
        given()
                .when().get("/posts/1")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "carol@yoursay.example", roles = "admin")
    public void listByUserRejectsAuthenticatedCallerWithoutUserRole() {
        given()
                .when().get("/posts/user/1")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "carol@yoursay.example", roles = "admin")
    public void presignRejectsAuthenticatedCallerWithoutUserRole() {
        given()
                .contentType("application/json")
                .body("{ \"mediaType\": \"IMAGE\", \"contentType\": \"image/jpeg\" }")
                .when().post("/posts/media/presign")
                .then()
                .statusCode(403);
    }
}
