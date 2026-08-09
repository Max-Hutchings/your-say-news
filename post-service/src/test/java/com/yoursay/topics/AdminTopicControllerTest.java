package com.yoursay.topics;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catalogue administration (ADR-043). Admins extending the taxonomy at runtime is the part of this
 * change that reverses ADR-020, so the authorisation boundary around it carries real weight: the
 * catalogue is still controlled, and this is now where the control lives.
 */
@QuarkusTest
class AdminTopicControllerTest {

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void addsATopicAtTheEndOfTheCatalogueWithAnIdDerivedFromItsLabel() {
        String label = "Rail and buses " + System.nanoTime();
        String expectedId = label.toLowerCase().replace(" ", "-");
        List<Integer> existingOrders = given().when().get("/api/admin/topics")
                .then().statusCode(200).extract().path("displayOrder");
        int expectedOrder = existingOrders.stream().mapToInt(Integer::intValue).max().orElse(0) + 1;

        given().contentType("application/json")
                .body("""
                        { "label": "%s", "displayGroup": "Transport & places" }
                        """.formatted(label))
                .when().post("/api/admin/topics")
                .then().statusCode(201)
                .body("id", equalTo(expectedId))
                .body("label", equalTo(label))
                .body("displayGroup", equalTo("Transport & places"))
                .body("active", equalTo(true))
                .body("displayOrder", equalTo(expectedOrder));

        // It is immediately offered to readers, without a deploy — the point of the ADR.
        List<String> readerIds = given().when().get("/topics")
                .then().statusCode(200).extract().path("id");
        assertTrue(readerIds.contains(expectedId), "new topic missing from the reader catalogue");
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void refusesADuplicateWithAConflictRatherThanAConstraintViolation() {
        given().contentType("application/json")
                .body("""
                        { "label": "Housing", "displayGroup": "Society" }
                        """)
                .when().post("/api/admin/topics")
                .then().statusCode(409);
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void refusesALabelThatCannotBecomeACanonicalId() {
        // Punctuation alone would slugify to "", which the ck_topic_id constraint would reject as a
        // 500 rather than a validation error.
        given().contentType("application/json")
                .body("""
                        { "label": "!!!", "displayGroup": "Society" }
                        """)
                .when().post("/api/admin/topics")
                .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void retiringATopicHidesItFromReadersButKeepsItInTheAdminLedger() {
        String label = "Temporary subject " + System.nanoTime();
        String id = given().contentType("application/json")
                .body("""
                        { "label": "%s", "displayGroup": "Society" }
                        """.formatted(label))
                .when().post("/api/admin/topics")
                .then().statusCode(201).extract().path("id");

        given().contentType("application/json")
                .body("{ \"active\": false }")
                .when().put("/api/admin/topics/" + id + "/active")
                .then().statusCode(200)
                .body("active", equalTo(false));

        List<String> readerIds = given().when().get("/topics")
                .then().statusCode(200).extract().path("id");
        assertTrue(!readerIds.contains(id), "a retired topic must not be offered to readers");

        // Retire, never delete: the row survives so posts already filed under it stay intelligible.
        List<String> adminIds = given().when().get("/api/admin/topics")
                .then().statusCode(200).extract().path("id");
        assertTrue(adminIds.contains(id), "a retired topic must remain in the admin ledger");

        given().contentType("application/json")
                .body("{ \"active\": true }")
                .when().put("/api/admin/topics/" + id + "/active")
                .then().statusCode(200).body("active", equalTo(true));
        List<String> restoredReaderIds = given().when().get("/topics")
                .then().statusCode(200).extract().path("id");
        assertTrue(restoredReaderIds.contains(id), "a restored topic must return to the reader catalogue");
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void reportsAMissingTopicRatherThanCreatingOneOnRetire() {
        given().contentType("application/json")
                .body("{ \"active\": false }")
                .when().put("/api/admin/topics/no-such-topic/active")
                .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "reader@yoursay.com", roles = "user")
    void aPlainUserCannotReadOrExtendTheCatalogue() {
        // The catalogue is controlled: a signed-in reader must not be able to add to it.
        given().when().get("/api/admin/topics").then().statusCode(403);

        given().contentType("application/json")
                .body("""
                        { "label": "Reader invented topic", "displayGroup": "Society" }
                        """)
                .when().post("/api/admin/topics")
                .then().statusCode(403);

        given().contentType("application/json")
                .body("{ \"active\": false }")
                .when().put("/api/admin/topics/housing/active")
                .then().statusCode(403);
    }

    @Test
    void anAnonymousCallerIsRejectedBeforeAnyRoleCheck() {
        given().when().get("/api/admin/topics").then().statusCode(401);
        given().when().get("/topics").then().statusCode(401);
        given().contentType("application/json")
                .body("""
                        { "label": "Anonymous topic", "displayGroup": "Society" }
                        """)
                .when().post("/api/admin/topics").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "publisher@yoursay.com", roles = "official")
    void aCallerWithoutAReaderOrAdminRoleCannotReadTheCatalogue() {
        given().when().get("/topics").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void theAdminLedgerShowsTheShippedCatalogueInTabStripOrder() {
        List<Integer> orders = given().when().get("/api/admin/topics")
                .then().statusCode(200).extract().path("displayOrder");

        assertTrue(orders.size() >= 20, "expected at least the 20 shipped topics, got " + orders.size());
        List<Integer> sorted = orders.stream().sorted().toList();
        assertEquals(sorted, orders, "the ledger must be ordered by displayOrder");
    }
}
