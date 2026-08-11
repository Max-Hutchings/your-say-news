package com.yoursay.topics;

import com.yoursay.feed.client.FeedUserClient;
import com.yoursay.feed.client.SocialClient;
import com.yoursay.posts.client.UserServiceClient;
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
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@TestSecurity(user = "creator@yoursay.com", roles = "user")
class TopicTagIntegrationTest {

    private static final long CREATOR_ID = 42L;

    @InjectMock
    UserServiceClient userServiceClient;

    @InjectMock
    FeedUserClient feedUserClient;

    @InjectMock
    SocialClient socialClient;

    @Inject
    AgroalDataSource dataSource;

    @BeforeEach
    void setup() {
        Mockito.reset(userServiceClient, feedUserClient, socialClient);
        Mockito.when(userServiceClient.getCurrentUserAccess(Mockito.any()))
                .thenReturn(Uni.createFrom().item(new UserServiceClient.UserAccess(
                        CREATOR_ID, "OFFICIAL", "ACTIVE", true)));
        Mockito.when(feedUserClient.getUserByEmail(Mockito.any(), Mockito.any()))
                .thenReturn(Uni.createFrom().item(new FeedUserClient.UserRef(CREATOR_ID)));
        Mockito.when(socialClient.getFollowing(Mockito.any()))
                .thenReturn(Uni.createFrom().item(new SocialClient.FollowingRef(Set.of())));
    }

    @Test
    void exposesTheGovernedCatalogueAsTopicTags() {
        given().when().get("/topic-tags")
                .then().statusCode(200)
                .body("[0].id", equalTo("politics"))
                .body("[0].label", equalTo("Politics"));
    }

    @Test
    void creatorSelectionsKeepProvenanceAndReturnAsEffectiveTopicTagChips() throws Exception {
        int postId = given().contentType("application/json")
                .body("""
                        {
                          "summary": "Councils need more homes for local families.",
                          "supportQuestion": "Should councils build more affordable homes?",
                          "media": [],
                          "topicTagIds": ["housing", "politics"]
                        }
                        """)
                .when().post("/posts")
                .then().statusCode(201)
                .body("topicTags.id", contains("politics", "housing"))
                .body("topicTags.label", contains("Politics", "Housing"))
                .extract().path("id");

        assertEquals(List.of(
                "housing|CREATOR|ACCEPTED",
                "politics|CREATOR|ACCEPTED"), assignmentRows(postId));
        assertEquals(List.of(
                "housing|CREATOR",
                "politics|CREATOR"), effectiveRows(postId));
    }

    @Test
    void classifierAssignmentsRequireConfidenceAndVersion() throws Exception {
        int postId = createUntaggedPost();

        assertClassifierRejected(postId, "technology", null, "classifier-v1");
        assertClassifierRejected(postId, "technology", "0.7500", null);
        assertClassifierRejected(postId, "technology", "0.7500", "  ");
        assertClassifierRejected(postId, "technology", "-0.0001", "classifier-low");
        assertClassifierRejected(postId, "technology", "1.0001", "classifier-high");

        insertClassifier(postId, "technology", "0.0000", "classifier-zero", "UNREVIEWED");
        insertClassifier(postId, "health", "1.0000", "classifier-one", "ACCEPTED");
        assertEquals(List.of(
                "health|CLASSIFIER|ACCEPTED",
                "technology|CLASSIFIER|UNREVIEWED"), assignmentRows(postId));
    }

    @Test
    void creatorAssignmentsCannotCarryClassifierMetadata() {
        int postId = createUntaggedPost();

        assertThrows(SQLException.class, () -> insertAssignment(
                postId, "economy", "CREATOR", "0.8000", "manual-v1", "ACCEPTED"));
    }

    @Test
    void rejectedClassifierAssignmentsDoNotEnterTheEffectiveCategoryFeed() throws Exception {
        int postId = createUntaggedPost();
        insertClassifier(postId, "housing", "0.9100", "classifier-v2", "REJECTED");

        List<Integer> ids = given().when().get("/feed?topicTag=housing&size=50")
                .then().statusCode(200)
                .extract().path("posts.id");

        org.junit.jupiter.api.Assertions.assertFalse(ids.contains(postId),
                "raw rejected assignments must not leak into the effective category feed");
    }

    @Test
    void retiringATopicTagKeepsHistoricalChipsAndItsCategoryFeed() throws Exception {
        String topicTagId = "retired-history-test";
        upsertTopicTag(topicTagId, true);
        int postId = given().contentType("application/json")
                .body("""
                        {
                          "summary": "A historical category should remain readable.",
                          "supportQuestion": "Should old category links keep working?",
                          "media": [],
                          "topicTagIds": ["retired-history-test"]
                        }
                        """)
                .when().post("/posts")
                .then().statusCode(201)
                .extract().path("id");

        try {
            setTopicTagActive(topicTagId, false);

            given().when().get("/posts/" + postId)
                    .then().statusCode(200)
                    .body("topicTags.id", contains(topicTagId));
            List<Integer> ids = given().when().get("/feed?topicTag=" + topicTagId + "&size=50")
                    .then().statusCode(200)
                    .extract().path("posts.id");
            org.junit.jupiter.api.Assertions.assertTrue(ids.contains(postId));
        } finally {
            setTopicTagActive(topicTagId, true);
        }
    }

    private int createUntaggedPost() {
        return given().contentType("application/json")
                .body("""
                        {
                          "summary": "A practical technology policy question.",
                          "supportQuestion": "Should automated decisions require an appeal?",
                          "media": []
                        }
                        """)
                .when().post("/posts")
                .then().statusCode(201)
                .extract().path("id");
    }

    private void assertClassifierRejected(long postId, String topicTagId,
                                           String confidence, String version) throws Exception {
        assertThrows(SQLException.class,
                () -> insertClassifier(postId, topicTagId, confidence, version, "UNREVIEWED"));
    }

    private void insertClassifier(long postId, String topicTagId, String confidence,
                                  String version, String reviewState) throws Exception {
        insertAssignment(postId, topicTagId, "CLASSIFIER", confidence, version, reviewState);
    }

    private void insertAssignment(long postId, String topicTagId, String source, String confidence,
                                  String version, String reviewState) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into post_topic_tag_assignment
                         (post_id, topic_tag_id, source, confidence, classifier_version,
                          review_state, created_at)
                     values (?, ?, ?, ?::numeric, ?, ?, now())
                     """)) {
            statement.setLong(1, postId);
            statement.setString(2, topicTagId);
            statement.setString(3, source);
            statement.setString(4, confidence);
            statement.setString(5, version);
            statement.setString(6, reviewState);
            statement.executeUpdate();
        }
    }

    private void upsertTopicTag(String topicTagId, boolean active) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into topic_tag
                         (id, label, display_group, display_order, active, created_at)
                     values (?, 'Retired history test', 'Test', 9999, ?, now())
                     on conflict (id) do update set active = excluded.active
                     """)) {
            statement.setString(1, topicTagId);
            statement.setBoolean(2, active);
            statement.executeUpdate();
        }
    }

    private void setTopicTagActive(String topicTagId, boolean active) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "update topic_tag set active = ? where id = ?")) {
            statement.setBoolean(1, active);
            statement.setString(2, topicTagId);
            statement.executeUpdate();
        }
    }

    private List<String> assignmentRows(long postId) throws Exception {
        return rows("""
                select topic_tag_id || '|' || source || '|' || review_state
                from post_topic_tag_assignment
                where post_id = ?
                order by topic_tag_id
                """, postId);
    }

    private List<String> effectiveRows(long postId) throws Exception {
        return rows("""
                select topic_tag_id || '|' || effective_source
                from effective_post_topic_tag
                where post_id = ?
                order by topic_tag_id
                """, postId);
    }

    private List<String> rows(String sql, long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                List<String> rows = new java.util.ArrayList<>();
                while (result.next()) {
                    rows.add(result.getString(1));
                }
                return List.copyOf(rows);
            }
        }
    }
}
