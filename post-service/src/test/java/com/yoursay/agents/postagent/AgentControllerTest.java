package com.yoursay.agents.postagent;

import com.yoursay.agents.postagent.client.AgentUserClient;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@TestSecurity(user = "editor@yoursay.com", roles = "user")
class AgentControllerTest {

    private static final long EDITOR_ID = 701L;
    private static final long OTHER_USER_ID = 702L;
    private static final String EDITOR_AUTH = "Bearer editor-stage7-token";
    private static final String OTHER_AUTH = "Bearer other-stage7-token";

    @InjectMock
    AgentUserClient userClient;

    @Inject
    AgroalDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        Mockito.reset(userClient);
        Mockito.when(userClient.getCurrentUserAccess(Mockito.eq(EDITOR_AUTH)))
                .thenReturn(new AgentUserClient.UserAccess(
                        EDITOR_ID, "OFFICIAL", "ACTIVE", true));
        Mockito.when(userClient.getCurrentUserAccess(Mockito.eq(OTHER_AUTH)))
                .thenReturn(new AgentUserClient.UserAccess(
                        OTHER_USER_ID, "STANDARD", "NONE", false));
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("delete from agent_generation_job")) {
            statement.executeUpdate();
        }
    }

    @Test
    void startReturnsPendingJobAndPersistsAuthenticatedOwnerAndExactRequest() throws Exception {
        String request = "Cover the proposed UK voting-age change and the strongest evidence on each side.";

        String jobId = given()
                .header("Authorization", EDITOR_AUTH)
                .contentType("application/json")
                .body("{\"request\":\"" + request + "\"}")
                .when().post("/agent/jobs")
                .then()
                .statusCode(202)
                .body("status", is("PENDING"))
                .body("attemptCount", is(0))
                .body("model", nullValue())
                .body("draft", nullValue())
                .body("errorCode", nullValue())
                .extract().path("id");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select user_id, request, status, attempt_count
                     from agent_generation_job
                     where id = cast(? as uuid)
                     """)) {
            statement.setString(1, jobId);
            try (ResultSet result = statement.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                org.junit.jupiter.api.Assertions.assertEquals(EDITOR_ID, result.getLong("user_id"));
                org.junit.jupiter.api.Assertions.assertEquals(request, result.getString("request"));
                org.junit.jupiter.api.Assertions.assertEquals("PENDING", result.getString("status"));
                org.junit.jupiter.api.Assertions.assertEquals(0, result.getInt("attempt_count"));
                org.junit.jupiter.api.Assertions.assertFalse(result.next());
            }
        }
    }

    @Test
    void ownerCanPollTheirPendingJob() {
        String jobId = createJob();

        given()
                .header("Authorization", EDITOR_AUTH)
                .when().get("/agent/jobs/" + jobId)
                .then()
                .statusCode(200)
                .body("id", is(jobId))
                .body("status", is("PENDING"))
                .body("attemptCount", is(0))
                .body("draft", nullValue());
    }

    @Test
    @TestSecurity(user = "other@yoursay.com", roles = "user")
    void anotherUserCannotReadJob() {
        String jobId = insertJobOwnedBy(EDITOR_ID);

        given()
                .header("Authorization", OTHER_AUTH)
                .when().get("/agent/jobs/" + jobId)
                .then()
                .statusCode(404);
    }

    @Test
    void blankRequestIsRejectedBeforePersistence() throws Exception {
        given()
                .header("Authorization", EDITOR_AUTH)
                .contentType("application/json")
                .body("{\"request\":\"   \"}")
                .when().post("/agent/jobs")
                .then()
                .statusCode(400)
                .body("code", is("VALIDATION_FAILED"))
                .body("message", is("Invalid request."));

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from agent_generation_job");
             ResultSet result = statement.executeQuery()) {
            result.next();
            org.junit.jupiter.api.Assertions.assertEquals(0, result.getInt(1));
        }
    }

    @Test
    void officialWithoutActivePublisherStatusCannotStartJob() throws Exception {
        Mockito.when(userClient.getCurrentUserAccess(Mockito.eq(EDITOR_AUTH)))
                .thenReturn(new AgentUserClient.UserAccess(
                        EDITOR_ID, "OFFICIAL", "NONE", false));

        given()
                .header("Authorization", EDITOR_AUTH)
                .contentType("application/json")
                .body("{\"request\":\"Cover a current policy dispute.\"}")
                .when().post("/agent/jobs")
                .then()
                .statusCode(403)
                .body("code", is("AGENT_PUBLISHING_FORBIDDEN"));

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from agent_generation_job");
             ResultSet result = statement.executeQuery()) {
            result.next();
            org.junit.jupiter.api.Assertions.assertEquals(0, result.getInt(1));
        }
    }

    @Test
    void contradictoryAccessDataCannotBypassAgentPublishingRule() throws Exception {
        Mockito.when(userClient.getCurrentUserAccess(Mockito.eq(EDITOR_AUTH)))
                .thenReturn(new AgentUserClient.UserAccess(
                        EDITOR_ID, "STANDARD", "NONE", true));

        given()
                .header("Authorization", EDITOR_AUTH)
                .contentType("application/json")
                .body("{\"request\":\"Research a current transport dispute.\"}")
                .when().post("/agent/jobs")
                .then()
                .statusCode(403)
                .body("code", is("AGENT_PUBLISHING_FORBIDDEN"));

        Mockito.when(userClient.getCurrentUserAccess(Mockito.eq(EDITOR_AUTH)))
                .thenReturn(new AgentUserClient.UserAccess(
                        EDITOR_ID, "OFFICIAL", "ACTIVE", false));

        given()
                .header("Authorization", EDITOR_AUTH)
                .contentType("application/json")
                .body("{\"request\":\"Research a current housing dispute.\"}")
                .when().post("/agent/jobs")
                .then()
                .statusCode(403)
                .body("code", is("AGENT_PUBLISHING_FORBIDDEN"));

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from agent_generation_job");
             ResultSet result = statement.executeQuery()) {
            result.next();
            org.junit.jupiter.api.Assertions.assertEquals(0, result.getInt(1));
        }
    }

    private static String createJob() {
        return given()
                .header("Authorization", EDITOR_AUTH)
                .contentType("application/json")
                .body("""
                        {"request":"Compare the evidence and arguments around a current transport policy."}
                        """)
                .when().post("/agent/jobs")
                .then()
                .statusCode(202)
                .extract().path("id");
    }

    private String insertJobOwnedBy(long userId) {
        String id = java.util.UUID.randomUUID().toString();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into agent_generation_job
                       (id, user_id, request, status, attempt_count, next_attempt_at, created_at, updated_at)
                     values (cast(? as uuid), ?, ?, 'PENDING', 0, now(), now(), now())
                     """)) {
            statement.setString(1, id);
            statement.setLong(2, userId);
            statement.setString(3, "A persisted job owned by another user.");
            statement.executeUpdate();
            return id;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
