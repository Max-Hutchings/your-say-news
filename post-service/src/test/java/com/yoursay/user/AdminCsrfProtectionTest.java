package com.yoursay.user;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs a real admin mutation through the registered CSRF filter under the hosted /api root path
 * (ADR-064), where the admin API lives at /api/api/admin/*. RestAssured prefixes the root path.
 */
@QuarkusTest
@TestProfile(AdminCsrfProtectionTest.HostedCsrfProfile.class)
@TestSecurity(user = "admin@yoursay.com", roles = "admin")
class AdminCsrfProtectionTest {

    private static final String ADMIN_ORIGIN = "https://dev.yoursaynews.com";

    @Test
    void adminMutationWithoutACsrfTokenIsRefusedAndChangesNothing() {
        String topicId = createTopic();

        browser().cookie("ysn_admin_csrf", "csrf-token")
                .body("{ \"active\": false }")
                .when().put("/api/admin/topic-tags/" + topicId + "/active")
                .then().statusCode(403);

        assertEquals(true, topicActive(topicId));
    }

    @Test
    void adminMutationWhoseHeaderDoesNotMatchTheCookieIsRefusedAndChangesNothing() {
        String topicId = createTopic();

        browser().cookie("ysn_admin_csrf", "csrf-token").header("X-CSRF-Token", "forged-token")
                .body("{ \"active\": false }")
                .when().put("/api/admin/topic-tags/" + topicId + "/active")
                .then().statusCode(403);

        assertEquals(true, topicActive(topicId));
    }

    @Test
    void adminMutationWithMatchingTokensFromTheAdminOriginIsApplied() {
        String topicId = createTopic();

        withCsrf().body("{ \"active\": false }")
                .when().put("/api/admin/topic-tags/" + topicId + "/active")
                .then().statusCode(200).body("active", equalTo(false));

        assertEquals(false, topicActive(topicId));
    }

    private static String createTopic() {
        return withCsrf()
                .body("{ \"label\": \"CSRF guard %d\", \"displayGroup\": \"Society\" }".formatted(System.nanoTime()))
                .when().post("/api/admin/topic-tags")
                .then().statusCode(201).extract().path("id");
    }

    private static Boolean topicActive(String topicId) {
        List<Map<String, Object>> topics = given().when().get("/api/admin/topic-tags")
                .then().statusCode(200).extract().path("$");
        return topics.stream()
                .filter(topic -> topicId.equals(topic.get("id")))
                .map(topic -> (Boolean) topic.get("active"))
                .findFirst()
                .orElseThrow();
    }

    private static RequestSpecification browser() {
        return given().contentType("application/json").header("Origin", ADMIN_ORIGIN);
    }

    private static RequestSpecification withCsrf() {
        return browser().cookie("ysn_admin_csrf", "csrf-token").header("X-CSRF-Token", "csrf-token");
    }

    public static final class HostedCsrfProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.http.root-path", "/api",
                    "firebase.auth.csrf-enforced", "true",
                    "firebase.auth.allowed-origins", ADMIN_ORIGIN,
                    "quarkus.http.cors.origins", ADMIN_ORIGIN);
        }
    }
}
