package com.yoursay.user;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * The admin SPA reads its Firebase web settings here before anyone has signed in, so the route must
 * be public. The values come from runtime config because one image is promoted from dev to prod.
 */
@QuarkusTest
class AdminWebConfigControllerTest {

    @Test
    void servesTheEnvironmentsFirebaseWebSettingsWithoutSignIn() {
        given().when().get("/api/auth/admin/config")
                .then().statusCode(200)
                .body("projectId", equalTo("demo-your-say-news"))
                .body("authDomain", equalTo("demo-your-say-news.firebaseapp.com"))
                .body("apiKey", equalTo("test-web-api-key"))
                .body("appId", equalTo("test-web-app-id"))
                .body("signInMethod", equalTo("GOOGLE"))
                .body("authEmulatorUrl", nullValue());
    }
}
