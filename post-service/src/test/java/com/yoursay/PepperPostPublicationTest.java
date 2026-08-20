package com.yoursay;

import com.yoursay.posts.client.UserServiceClient;
import com.yoursay.agents.postagent.client.AgentUserClient;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestSecurity(user = "pepper.editor@yoursay.com", roles = "user")
class PepperPostPublicationTest {

    private static final long AUTHOR_ID = 741L;
    private static final String AUTHORIZATION = "Bearer pepper-publish-token";
    private static final String SOURCE_URL = "https://www.ons.gov.uk/work";

    @InjectMock
    UserServiceClient postUserClient;

    @InjectMock
    AgentUserClient agentUserClient;

    @Inject
    AgroalDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        Mockito.reset(postUserClient, agentUserClient);
        Mockito.when(postUserClient.getCurrentUserAccess(Mockito.eq(AUTHORIZATION)))
                .thenReturn(Uni.createFrom().item(new UserServiceClient.UserAccess(
                        AUTHOR_ID, "OFFICIAL", "ACTIVE", true)));
        Mockito.when(agentUserClient.getCurrentUserAccess(Mockito.eq(AUTHORIZATION)))
                .thenReturn(new AgentUserClient.UserAccess(
                        AUTHOR_ID, "OFFICIAL", "ACTIVE", true));
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement posts = connection.prepareStatement(
                    "delete from post where ai_draft_id in "
                            + "(select id from pepper_ai_draft_post where user_id = ?)")) {
                posts.setLong(1, AUTHOR_ID);
                posts.executeUpdate();
            }
            try (PreparedStatement drafts = connection.prepareStatement(
                    "delete from pepper_ai_draft_post where user_id = ?")) {
                drafts.setLong(1, AUTHOR_ID);
                drafts.executeUpdate();
            }
        }
    }

    @Test
    void normalPostCreationCannotSpoofAiGeneration() {
        given().header("Authorization", AUTHORIZATION).contentType("application/json")
                .body("""
                        {
                          "summary":"A manually written article.",
                          "supportQuestion":"Should this manual proposal proceed?",
                          "isAiGenerated":true,
                          "citations":[{
                            "url":"https://invented.example/source",
                            "title":"Invented",
                            "publisher":"Unknown"
                          }],
                          "media":[]
                        }
                        """)
                .when().post("/posts")
                .then().statusCode(201)
                .body("isAiGenerated", is(false))
                .body("sources.size()", is(0));
    }

    @Test
    void standardPostEndpointPublishesAnOwnedPepperDraftWithAiFlagAndSelectedSources() throws Exception {
        UUID draftId = insertFinishedDraft(AUTHOR_ID);
        String publicationBody = publicationBody(draftId, SOURCE_URL);

        int postId = given().header("Authorization", AUTHORIZATION).contentType("application/json")
                .body(publicationBody)
                .when().post("/posts")
                .then().statusCode(201)
                .body("isAiGenerated", is(true))
                .body("sources.size()", is(1))
                .body("sources[0].url", is(SOURCE_URL))
                .body("sources[0].title", is("Working patterns"))
                .body("sources[0].publisher", is("ONS"))
                .extract().path("id");

        int repeatedId = given().header("Authorization", AUTHORIZATION).contentType("application/json")
                .body(publicationBody)
                .when().post("/posts")
                .then().statusCode(201)
                .body("isAiGenerated", is(true))
                .body("sources.size()", is(1))
                .extract().path("id");
        org.junit.jupiter.api.Assertions.assertEquals(postId, repeatedId);

        given().when().get("/posts/" + postId).then().statusCode(200)
                .body("isAiGenerated", is(true))
                .body("sources[0].url", is(SOURCE_URL));

        try (Connection connection = dataSource.getConnection();
             PreparedStatement post = connection.prepareStatement("""
                     select is_ai_generated, ai_draft_id from post where id = ?
                     """);
             PreparedStatement source = connection.prepareStatement("""
                     select url, title, publisher, ordinal from post_source where post_id = ?
                     """);
             PreparedStatement draft = connection.prepareStatement("""
                     select published_post_id from pepper_ai_draft_post where id = ?
                     """)) {
            post.setInt(1, postId);
            try (ResultSet result = post.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                org.junit.jupiter.api.Assertions.assertTrue(result.getBoolean("is_ai_generated"));
                org.junit.jupiter.api.Assertions.assertEquals(draftId,
                        result.getObject("ai_draft_id", UUID.class));
            }
            source.setInt(1, postId);
            try (ResultSet result = source.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                org.junit.jupiter.api.Assertions.assertEquals(SOURCE_URL, result.getString("url"));
                org.junit.jupiter.api.Assertions.assertEquals("Working patterns", result.getString("title"));
                org.junit.jupiter.api.Assertions.assertEquals("ONS", result.getString("publisher"));
                org.junit.jupiter.api.Assertions.assertEquals(0, result.getInt("ordinal"));
                org.junit.jupiter.api.Assertions.assertFalse(result.next());
            }
            draft.setObject(1, draftId);
            try (ResultSet result = draft.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                org.junit.jupiter.api.Assertions.assertEquals(postId, result.getInt("published_post_id"));
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(
                1, countPostsWithDraft(draftId), "publishing twice must remain idempotent");
    }

    @Test
    void inventedCitationCannotBePublishedAsAiGeneratedContent() throws Exception {
        UUID draftId = insertFinishedDraft(AUTHOR_ID);
        long before = countPostsWithDraft(draftId);

        given().header("Authorization", AUTHORIZATION).contentType("application/json")
                .body(publicationBody(draftId, "https://invented.example/source"))
                .when().post("/posts")
                .then().statusCode(400)
                .body("code", is("AGENT_CITATION_INVALID"));

        org.junit.jupiter.api.Assertions.assertEquals(before, countPostsWithDraft(draftId));
        assertDraftUnpublished(draftId);
        org.junit.jupiter.api.Assertions.assertEquals(0, countSourcesForDraft(draftId));
    }

    @Test
    void publisherCannotPublishAnotherUsersPepperDraft() throws Exception {
        UUID draftId = insertFinishedDraft(999L);
        long before = countPostsWithDraft(draftId);

        given().header("Authorization", AUTHORIZATION).contentType("application/json")
                .body("""
                        {
                          "summary":"Stolen draft.",
                          "supportQuestion":"Should this be rejected?",
                          "pepperDraftId":"%s",
                          "citations":[],
                          "media":[]
                        }
                        """.formatted(draftId))
                .when().post("/posts")
                .then().statusCode(404)
                .body("code", is("AGENT_DRAFT_NOT_FOUND"));

        org.junit.jupiter.api.Assertions.assertEquals(before, countPostsWithDraft(draftId));
        assertDraftUnpublished(draftId);
        org.junit.jupiter.api.Assertions.assertEquals(0, countSourcesForDraft(draftId));
    }

    private UUID insertFinishedDraft(long ownerId) throws Exception {
        UUID id = UUID.randomUUID();
        String content = """
                {
                  "summary":"Generated account of working-week trials.",
                  "supportQuestion":"Should employers trial a four-day working week?",
                  "caseFor":"Retention may improve.",
                  "caseAgainst":"Coverage may cost more.",
                  "votingType":"BINARY",
                  "voteOptions":["Agree","Disagree"],
                  "citations":[
                    {
                      "url":"https://www.ons.gov.uk/work",
                      "title":"Working patterns",
                      "publisher":"ONS"
                    },
                    {
                      "url":"https://www.acas.org.uk/hours",
                      "title":"Working hours",
                      "publisher":"Acas"
                    }
                  ]
                }
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into pepper_ai_draft_post
                       (id, user_id, prompt, replica_id, status, success, generated_content,
                        content, version, created_at, updated_at, completed_at)
                     values (?, ?, ?, 'replica-test', 'FINISHED', true, cast(? as jsonb),
                             cast(? as jsonb), 1, now(), now(), now())
                     """)) {
            statement.setObject(1, id);
            statement.setLong(2, ownerId);
            statement.setString(3, "Research four-day working weeks.");
            statement.setString(4, content);
            statement.setString(5, content);
            statement.executeUpdate();
        }
        return id;
    }

    private static String publicationBody(UUID draftId, String sourceUrl) {
        return """
                {
                  "summary":"An editor-adjusted account of working-week trials.",
                  "supportQuestion":"Should employers trial a four-day working week?",
                  "caseFor":"Retention may improve.",
                  "caseAgainst":"Coverage may cost more.",
                  "votingType":"BINARY",
                  "voteOptions":[],
                  "pepperDraftId":"%s",
                  "citations":[{
                    "url":"%s",
                    "title":"Working patterns",
                    "publisher":"ONS"
                  }],
                  "media":[]
                }
                """.formatted(draftId, sourceUrl);
    }

    private long countPostsWithDraft(UUID draftId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from post where ai_draft_id = ?")) {
            statement.setObject(1, draftId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private long countSourcesForDraft(UUID draftId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select count(*) from post_source s
                     join post p on p.id = s.post_id
                     where p.ai_draft_id = ?
                     """)) {
            statement.setObject(1, draftId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private void assertDraftUnpublished(UUID draftId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select published_post_id from pepper_ai_draft_post where id = ?")) {
            statement.setObject(1, draftId);
            try (ResultSet result = statement.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                org.junit.jupiter.api.Assertions.assertNull(result.getObject("published_post_id"));
            }
        }
    }
}
