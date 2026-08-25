package com.yoursay.posts.postagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.posts.VotingType;
import com.yoursay.posts.postagent.client.AgentUserClient;
import com.yoursay.posts.postagent.dto.AgentDraftDto;
import com.yoursay.posts.postagent.dto.AgentSourceDto;
import com.yoursay.posts.postagent.dto.AgentVoteOptionDto;
import com.yoursay.posts.postagent.dto.SourcedClaimDto;
import com.yoursay.posts.postagent.agent.GenerationException;
import com.yoursay.posts.postagent.agent.GenerationResult;
import com.yoursay.posts.postagent.agent.PepperPostGenerator;
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
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
@TestSecurity(user = "editor@yoursay.com", roles = "user")
class AgentControllerTest {

    private static final long EDITOR_ID = 701L;
    private static final long OTHER_ID = 702L;
    private static final String EDITOR_AUTH = "Bearer editor-pepper-token";
    private static final String OTHER_AUTH = "Bearer other-pepper-token";
    private static final String SOURCE_URL = "https://www.ons.gov.uk/employmentandlabourmarket";

    @InjectMock
    AgentUserClient userClient;

    @InjectMock
    PepperPostGenerator generator;

    @Inject
    AgroalDataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        Mockito.reset(userClient, generator);
        Mockito.when(userClient.getCurrentUserAccess(Mockito.eq(EDITOR_AUTH)))
                .thenReturn(new AgentUserClient.UserAccess(
                        EDITOR_ID, "OFFICIAL", "ACTIVE", true));
        Mockito.when(userClient.getCurrentUserAccess(Mockito.eq(OTHER_AUTH)))
                .thenReturn(new AgentUserClient.UserAccess(
                        OTHER_ID, "OFFICIAL", "ACTIVE", true));
        Mockito.when(generator.generate(Mockito.anyString())).thenReturn(generatedResult());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "delete from pepper_ai_draft_post where user_id in (701, 702)")) {
            statement.executeUpdate();
        }
    }

    @Test
    void directGenerationStreamsEveryStatusAndPersistsThePromptAndCompleteDraft() throws Exception {
        String prompt = "Compare the strongest evidence around four-day working weeks.";

        String stream = given()
                .header("Authorization", EDITOR_AUTH)
                .accept("text/event-stream")
                .contentType("application/json")
                .body("{\"request\":\"" + prompt + "\"}")
                .when().post("/agent/drafts")
                .then()
                .statusCode(200)
                .contentType(containsString("text/event-stream"))
                .extract().asString();

        int received = stream.indexOf("\"status\":\"RECEIVED\"");
        int generating = stream.indexOf("\"status\":\"GENERATING\"");
        int finished = stream.indexOf("\"status\":\"FINISHED\"");
        org.junit.jupiter.api.Assertions.assertTrue(received >= 0, stream);
        org.junit.jupiter.api.Assertions.assertTrue(generating > received, stream);
        org.junit.jupiter.api.Assertions.assertTrue(finished > generating, stream);
        org.junit.jupiter.api.Assertions.assertTrue(stream.contains(
                "\"supportQuestion\":\"Should more employers trial a four-day working week?\""), stream);
        org.junit.jupiter.api.Assertions.assertTrue(stream.contains(
                "\"summary\":\"Trials generally maintained productivity.\""), stream);
        org.junit.jupiter.api.Assertions.assertTrue(stream.contains(
                "\"caseFor\":\"Retention may improve.\""), stream);
        org.junit.jupiter.api.Assertions.assertTrue(stream.contains(
                "\"caseAgainst\":\"Coverage may cost more.\""), stream);
        org.junit.jupiter.api.Assertions.assertTrue(stream.contains(
                "\"voteOptions\":[\"Agree\",\"Disagree\"]"), stream);
        org.junit.jupiter.api.Assertions.assertTrue(stream.contains(SOURCE_URL), stream);
        Mockito.verify(generator).generate(prompt);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select prompt, status, success, generated_content, content, version
                     from pepper_ai_draft_post where user_id = ?
                     """)) {
            statement.setLong(1, EDITOR_ID);
            try (ResultSet result = statement.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                org.junit.jupiter.api.Assertions.assertEquals(prompt, result.getString("prompt"));
                org.junit.jupiter.api.Assertions.assertEquals("FINISHED", result.getString("status"));
                org.junit.jupiter.api.Assertions.assertTrue(result.getBoolean("success"));
                org.junit.jupiter.api.Assertions.assertTrue(result.getString("generated_content")
                        .contains("Should more employers trial a four-day working week?"));
                org.junit.jupiter.api.Assertions.assertEquals(
                        result.getString("generated_content"), result.getString("content"));
                org.junit.jupiter.api.Assertions.assertEquals(1, result.getInt("version"));
                org.junit.jupiter.api.Assertions.assertFalse(result.next());
            }
        }
    }

    @Test
    void providerFaultIsPersistedAndOnlyTheSafePepperMessageIsStreamed() throws Exception {
        Mockito.when(generator.generate(Mockito.anyString())).thenThrow(new GenerationException(
                "AGENT_PROVIDER_UNAVAILABLE", "xAI token and raw provider details", true));

        String stream = given()
                .header("Authorization", EDITOR_AUTH)
                .accept("text/event-stream")
                .contentType("application/json")
                .body("{\"request\":\"Research a current transport dispute.\"}")
                .when().post("/agent/drafts")
                .then().statusCode(200)
                .extract().asString();

        JsonNode failed = event(stream, "FAILED");
        org.junit.jupiter.api.Assertions.assertEquals("FAILED", failed.path("status").asText());
        org.junit.jupiter.api.Assertions.assertFalse(failed.path("draftId").asText().isBlank());
        org.junit.jupiter.api.Assertions.assertFalse(failed.path("replicaId").asText().isBlank());
        org.junit.jupiter.api.Assertions.assertEquals(
                "Pepper AI is having trouble, please try again later.",
                failed.path("errorMessage").asText());
        org.junit.jupiter.api.Assertions.assertFalse(failed.hasNonNull("result"));
        org.junit.jupiter.api.Assertions.assertFalse(stream.contains("xAI token"), stream);
        org.junit.jupiter.api.Assertions.assertFalse(stream.contains("raw provider details"), stream);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select prompt, status, success, content, error_code, error_message
                     from pepper_ai_draft_post where user_id = ?
                     """)) {
            statement.setLong(1, EDITOR_ID);
            try (ResultSet result = statement.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                org.junit.jupiter.api.Assertions.assertEquals(
                        "Research a current transport dispute.", result.getString("prompt"));
                org.junit.jupiter.api.Assertions.assertEquals("FAILED", result.getString("status"));
                org.junit.jupiter.api.Assertions.assertFalse(result.getBoolean("success"));
                org.junit.jupiter.api.Assertions.assertNull(result.getString("content"));
                org.junit.jupiter.api.Assertions.assertEquals(
                        "AGENT_PROVIDER_UNAVAILABLE", result.getString("error_code"));
                org.junit.jupiter.api.Assertions.assertEquals(
                        "Pepper AI is having trouble, please try again later.",
                        result.getString("error_message"));
            }
        }
    }

    @Test
    void latestReturnsTheNewestUnpublishedDraftAndReconnectReplaysItsFinishedResult() {
        String stream = generateSuccessfully("Research employment evidence.");
        String draftId = extractJsonString(stream, "draftId");
        String replicaId = extractJsonString(stream, "replicaId");

        given()
                .header("Authorization", EDITOR_AUTH)
                .when().get("/agent/drafts/latest")
                .then()
                .statusCode(200)
                .body("id", is(draftId))
                .body("prompt", is("Research employment evidence."))
                .body("status", is("FINISHED"))
                .body("success", is(true))
                .body("content.supportQuestion",
                        is("Should more employers trial a four-day working week?"));

        given()
                .header("Authorization", EDITOR_AUTH)
                .header("X-Pepper-Replica", replicaId)
                .accept("text/event-stream")
                .when().get("/agent/drafts/" + draftId + "/events")
                .then()
                .statusCode(200)
                .body(containsString("\"status\":\"FINISHED\""))
                .body(containsString("\"result\""));
    }

    @Test
    void autosaveAllowsCitationRemovalButRejectsAnInventedCitationAndStaleVersion() {
        String stream = generateSuccessfully("Research working-time evidence.");
        String draftId = extractJsonString(stream, "draftId");

        String withoutCitations = """
                {
                  "version": 1,
                  "content": {
                    "summary": "Edited overview.",
                    "supportQuestion": "Should more employers trial a shorter week?",
                    "caseFor": "Retention may improve.",
                    "caseAgainst": "Coverage may cost more.",
                    "votingType": "BINARY",
                    "voteOptions": ["Agree", "Disagree"],
                    "citations": []
                  }
                }
                """;
        given().header("Authorization", EDITOR_AUTH).contentType("application/json")
                .body(withoutCitations)
                .when().put("/agent/drafts/" + draftId)
                .then().statusCode(200)
                .body("version", is(2))
                .body("content.summary", is("Edited overview."))
                .body("content.citations.size()", is(0));

        given().header("Authorization", EDITOR_AUTH).contentType("application/json")
                .body(withoutCitations)
                .when().put("/agent/drafts/" + draftId)
                .then().statusCode(409)
                .body("code", is("AGENT_DRAFT_VERSION_CONFLICT"));

        String inventedCitation = withoutCitations
                .replace("\"version\": 1", "\"version\": 2")
                .replace("\"citations\": []", """
                        "citations": [{
                          "url":"https://invented.example/story",
                          "title":"Invented source",
                          "publisher":"Unknown"
                        }]
                        """);
        given().header("Authorization", EDITOR_AUTH).contentType("application/json")
                .body(inventedCitation)
                .when().put("/agent/drafts/" + draftId)
                .then().statusCode(400)
                .body("code", is("AGENT_CITATION_INVALID"));

        assertStoredDraft(draftId, 2, "Edited overview.", 0);
    }

    @Test
    @TestSecurity(user = "other@yoursay.com", roles = "user")
    void anotherUserCannotRestoreReconnectToOrEditTheEditorsDraft() {
        String stream = generateSuccessfully("Research ownership boundaries.");
        String draftId = extractJsonString(stream, "draftId");
        String replicaId = extractJsonString(stream, "replicaId");

        given().header("Authorization", OTHER_AUTH)
                .when().get("/agent/drafts/latest")
                .then().statusCode(204);
        given().header("Authorization", OTHER_AUTH)
                .header("X-Pepper-Replica", replicaId)
                .when().get("/agent/drafts/" + draftId + "/events")
                .then().statusCode(404)
                .body(containsString("AGENT_DRAFT_NOT_FOUND"));
        given().header("Authorization", OTHER_AUTH).contentType("application/json")
                .body("""
                        {
                          "version":1,
                          "content":{
                            "summary":"Overwritten by another user.",
                            "supportQuestion":"Should this overwrite work?",
                            "caseFor":null,
                            "caseAgainst":null,
                            "votingType":"BINARY",
                            "voteOptions":["Agree","Disagree"],
                            "citations":[]
                          }
                        }
                        """)
                .when().put("/agent/drafts/" + draftId)
                .then().statusCode(404)
                .body("code", is("AGENT_DRAFT_NOT_FOUND"));

        assertStoredDraft(draftId, 1, "Trials generally maintained productivity.", 2);
    }

    @Test
    void standardAccountCannotGenerate() throws Exception {
        Mockito.when(userClient.getCurrentUserAccess(Mockito.eq(EDITOR_AUTH)))
                .thenReturn(new AgentUserClient.UserAccess(EDITOR_ID, "USER", "NONE", false));

        given().header("Authorization", EDITOR_AUTH).accept("text/event-stream")
                .contentType("application/json")
                .body("{\"request\":\"Research a current issue.\"}")
                .when().post("/agent/drafts")
                .then().statusCode(403)
                .body(containsString("AGENT_PUBLISHING_FORBIDDEN"));

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from pepper_ai_draft_post where user_id = ?")) {
            statement.setLong(1, EDITOR_ID);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                org.junit.jupiter.api.Assertions.assertEquals(0, result.getInt(1));
            }
        }
    }

    @Test
    void blankPromptAndContradictoryPublisherDataAreRejectedWithoutPersistingDrafts() throws Exception {
        given().header("Authorization", EDITOR_AUTH).accept("text/event-stream")
                .contentType("application/json").body("{\"request\":\"   \"}")
                .when().post("/agent/drafts").then().statusCode(400)
                .body(containsString("VALIDATION_FAILED"));

        Mockito.when(userClient.getCurrentUserAccess(Mockito.eq(EDITOR_AUTH)))
                .thenReturn(new AgentUserClient.UserAccess(EDITOR_ID, "USER", "NONE", true));
        given().header("Authorization", EDITOR_AUTH).accept("text/event-stream")
                .contentType("application/json").body("{\"request\":\"Research this.\"}")
                .when().post("/agent/drafts").then().statusCode(403)
                .body(containsString("AGENT_PUBLISHING_FORBIDDEN"));

        Mockito.when(userClient.getCurrentUserAccess(Mockito.eq(EDITOR_AUTH)))
                .thenReturn(new AgentUserClient.UserAccess(EDITOR_ID, "OFFICIAL", "ACTIVE", false));
        given().header("Authorization", EDITOR_AUTH).accept("text/event-stream")
                .contentType("application/json").body("{\"request\":\"Research this too.\"}")
                .when().post("/agent/drafts").then().statusCode(403)
                .body(containsString("AGENT_PUBLISHING_FORBIDDEN"));

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from pepper_ai_draft_post where user_id = ?")) {
            statement.setLong(1, EDITOR_ID);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                org.junit.jupiter.api.Assertions.assertEquals(0, result.getInt(1));
            }
        }
    }

    private String generateSuccessfully(String prompt) {
        return given().header("Authorization", EDITOR_AUTH).accept("text/event-stream")
                .contentType("application/json")
                .body("{\"request\":\"" + prompt + "\"}")
                .when().post("/agent/drafts")
                .then().statusCode(200)
                .extract().asString();
    }

    private static GenerationResult generatedResult() {
        AgentSourceDto source = new AgentSourceDto(SOURCE_URL, "Employment evidence", "ONS");
        return new GenerationResult(new AgentDraftDto(
                List.of(new SourcedClaimDto(
                        "Trials generally maintained productivity.", List.of(SOURCE_URL))),
                List.of(new SourcedClaimDto("Retention may improve.", List.of(SOURCE_URL))),
                List.of(new SourcedClaimDto("Coverage may cost more.", List.of(SOURCE_URL))),
                "Should more employers trial a four-day working week?",
                VotingType.BINARY,
                List.of(new AgentVoteOptionDto("Agree"), new AgentVoteOptionDto("Disagree")),
                List.of(source, new AgentSourceDto(
                        "https://www.acas.org.uk/working-hours", "Working hours", "Acas")),
                "A workplace changing shifts.",
                "four day week workplace"
        ), "grok-4.5", "response-41");
    }

    private static String extractJsonString(String stream, String field) {
        String marker = "\"" + field + "\":\"";
        int start = stream.indexOf(marker);
        if (start < 0) {
            throw new AssertionError("Missing " + field + " in stream: " + stream);
        }
        int valueStart = start + marker.length();
        return stream.substring(valueStart, stream.indexOf('"', valueStart));
    }

    private JsonNode event(String stream, String status) throws Exception {
        for (String line : stream.split("\\R")) {
            if (line.contains("data:") && line.contains("\"status\":\"" + status + "\"")) {
                return objectMapper.readTree(line.substring(line.indexOf("data:") + 5).trim());
            }
        }
        throw new AssertionError("Missing " + status + " event: " + stream);
    }

    private void assertStoredDraft(String draftId, int version, String summary, int citationCount) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select version, content ->> 'summary' as summary,
                            jsonb_array_length(content -> 'citations') as citation_count
                     from pepper_ai_draft_post where id = cast(? as uuid)
                     """)) {
            statement.setString(1, draftId);
            try (ResultSet result = statement.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                org.junit.jupiter.api.Assertions.assertEquals(version, result.getInt("version"));
                org.junit.jupiter.api.Assertions.assertEquals(summary, result.getString("summary"));
                org.junit.jupiter.api.Assertions.assertEquals(citationCount, result.getInt("citation_count"));
            }
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
