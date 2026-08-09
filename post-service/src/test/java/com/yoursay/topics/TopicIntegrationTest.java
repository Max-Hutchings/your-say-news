package com.yoursay.topics;

import com.yoursay.feed.client.FeedUserClient;
import com.yoursay.feed.client.SocialClient;
import com.yoursay.posts.client.UserServiceClient;
import com.yoursay.topics.dto.TopicDto;
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
import java.util.List;
import java.util.Set;
import java.util.Arrays;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The topics slice end to end over the real Postgres: the catalogue shipped by migration 0015,
 * assignment at post creation, and the category feed (ADR-043).
 *
 * <p>The paging test is the one that matters most. A category feed is only useful if it pages the
 * same way the main feed does, and the failure mode — a topic filter applied after the keyset scan
 * rather than inside it — produces short pages and skipped posts that a single-page test cannot see.
 */
@QuarkusTest
@TestSecurity(user = "author@yoursay.com", roles = "user")
class TopicIntegrationTest {

    private static final long AUTHOR_ID = 42L;
    private static final List<TopicDto> STARTING_TOPICS = List.of(
            new TopicDto("politics", "Politics", "Politics & government", 1, true),
            new TopicDto("economy", "Economy", "Money & business", 2, true),
            new TopicDto("health", "Health", "Society", 3, true),
            new TopicDto("technology", "Technology", "Science & technology", 4, true),
            new TopicDto("cost-of-living", "Cost of living", "Money & business", 5, true),
            new TopicDto("housing", "Housing", "Society", 6, true),
            new TopicDto("climate-change", "Climate", "Climate & environment", 7, true),
            new TopicDto("immigration", "Immigration", "Politics & government", 8, true),
            new TopicDto("elections", "Elections", "Politics & government", 9, true),
            new TopicDto("international", "World", "World affairs", 10, true),
            new TopicDto("war-conflict", "War & conflict", "World affairs", 11, true),
            new TopicDto("business", "Business", "Money & business", 12, true),
            new TopicDto("jobs-work", "Jobs & work", "Money & business", 13, true),
            new TopicDto("education", "Education", "Society", 14, true),
            new TopicDto("crime", "Crime", "Society", 15, true),
            new TopicDto("artificial-intelligence", "AI", "Science & technology", 16, true),
            new TopicDto("energy", "Energy", "Climate & environment", 17, true),
            new TopicDto("transport", "Transport", "Transport & places", 18, true),
            new TopicDto("arts-culture", "Culture", "Culture & life", 19, true),
            new TopicDto("sport", "Sport", "Sport", 20, true));

    @InjectMock
    UserServiceClient userServiceClient;

    /** The feed resolves the viewer and their follow set from the local user domain; neither is
     *  what these tests are about, so both are stubbed to a known reader who follows nobody. */
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
                        AUTHOR_ID, "OFFICIAL", "ACTIVE", true)));
        Mockito.when(feedUserClient.getUserByEmail(Mockito.any(), Mockito.any()))
                .thenReturn(Uni.createFrom().item(new FeedUserClient.UserRef(AUTHOR_ID)));
        Mockito.when(socialClient.getFollowing(Mockito.any()))
                .thenReturn(Uni.createFrom().item(new SocialClient.FollowingRef(Set.of())));
    }

    @Test
    void theCatalogueMigrationShipsTheTwentyStartingTopicsInTabStripOrder() {
        // Reference data in migrations/, not seeding/ — so this passes in every environment, which
        // is the whole point of ADR-043's placement decision.
        List<TopicDto> catalogue = Arrays.asList(given().when().get("/topics")
                .then().statusCode(200).extract().as(TopicDto[].class));

        assertTrue(catalogue.size() >= STARTING_TOPICS.size());
        assertEquals(STARTING_TOPICS, catalogue.subList(0, STARTING_TOPICS.size()));
    }

    @Test
    void aPostKeepsTheTopicsItsAuthorSelectedAndReturnsThemAsChips() {
        int postId = createPost("Should councils build more homes?", List.of("housing", "politics"));

        given().when().get("/posts/" + postId)
                .then().statusCode(200)
                // Chips come back in catalogue order (politics is 1, housing is 6), not request order.
                .body("topics.id", contains("politics", "housing"))
                .body("topics.label", contains("Politics", "Housing"));
    }

    @Test
    void aPostWithNoTopicsIsValidAndCarriesAnEmptyChipList() {
        int postId = createPost("Should the bin collection change?", null);

        given().when().get("/posts/" + postId)
                .then().statusCode(200)
                .body("topics", hasSize(0));
    }

    @Test
    void rejectsAnUnknownTopicRatherThanPublishingWithoutIt() throws Exception {
        long before = countPosts();

        given().contentType("application/json")
                .body(createBody("Does this file under a made-up subject?",
                        List.of("housing", "not-a-real-topic")))
                .when().post("/posts")
                .then().statusCode(400);

        // The whole create fails: a post must never exist minus a topic its author chose.
        assertEquals(before, countPosts());
    }

    @Test
    void rejectsAFourthTopicAndADuplicateSelection() throws Exception {
        long before = countPosts();
        given().contentType("application/json")
                .body(createBody("Four topics?", List.of("housing", "health", "crime", "politics")))
                .when().post("/posts")
                .then().statusCode(400);
        assertEquals(before, countPosts(), "four-topic validation must not insert a post");

        given().contentType("application/json")
                .body(createBody("The same topic twice?", List.of("housing", "housing")))
                .when().post("/posts")
                .then().statusCode(400);
        assertEquals(before, countPosts(), "duplicate topic failure must roll back the post insert");
    }

    @Test
    void theCategoryFeedReturnsOnlyPostsCarryingTheSelectedTopic() {
        int housingPost = createPost("Category filter: housing story", List.of("housing"));
        createPost("Category filter: health story", List.of("health"));

        List<Integer> ids = given().when().get("/feed?topic=housing&type=ARTICLE&size=50")
                .then().statusCode(200)
                .extract().path("posts.id");

        assertTrue(ids.contains(housingPost), "housing post missing from its own category feed");
        // Every returned post genuinely carries the topic, rather than the filter being cosmetic.
        List<List<String>> topicIds = given().when().get("/feed?topic=housing&type=ARTICLE&size=50")
                .then().statusCode(200)
                .extract().path("posts.topics.id");
        assertTrue(topicIds.stream().allMatch(topics -> topics.contains("housing")),
                "category feed returned a post without the topic: " + topicIds);
    }

    @Test
    void pagingACategoryFeedByCursorVisitsEveryPostExactlyOnce() {
        // Seven posts in one topic, paged two at a time. If the topic filter ran after the keyset
        // scan instead of inside it, pages would arrive short and posts would be skipped entirely.
        List<Integer> created = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            created.add(createPost("Paging probe " + i, List.of("energy")));
        }

        List<Integer> seen = new java.util.ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 10; page++) {
            String url = "/feed?topic=energy&size=2" + (cursor == null ? "" : "&cursor=" + cursor);
            io.restassured.path.json.JsonPath body = given().when().get(url)
                    .then().statusCode(200).extract().jsonPath();
            seen.addAll(body.getList("posts.id", Integer.class));
            cursor = body.getString("nextCursor");
            if (cursor == null) {
                break;
            }
        }

        assertEquals(created.size(), seen.stream().distinct().count(),
                "category paging did not return every post exactly once: " + seen);
        assertEquals(created.size(), seen.size(), "category paging returned a duplicate: " + seen);
        assertTrue(seen.containsAll(created), "category paging skipped posts: " + seen);
    }

    @Test
    void anUnknownFeedTopicIsRejectedRatherThanReturnedAsADeadCategory() {
        given().when().get("/feed?topic=not-a-real-topic")
                .then().statusCode(400);
    }

    @Test
    void anAbsentTopicLeavesTheMainFeedUnfiltered() {
        createPost("Unfiltered feed probe", List.of("sport"));

        // A blank topic must not reach the query as a filter matching nothing.
        given().when().get("/feed?topic=&size=5")
                .then().statusCode(200)
                .body("posts.size()", org.hamcrest.Matchers.greaterThan(0));
    }

    private static String createBody(String question, List<String> topicIds) {
        String topics = topicIds == null
                ? ""
                : ", \"topicIds\": [%s]".formatted(topicIds.stream()
                        .map("\"%s\""::formatted).reduce((a, b) -> a + ", " + b).orElse(""));
        return """
                { "summary": "Article context for the vote.", "supportQuestion": "%s",
                  "media": []%s }
                """.formatted(question, topics);
    }

    private static int createPost(String question, List<String> topicIds) {
        return given().contentType("application/json")
                .body(createBody(question, topicIds))
                .when().post("/posts")
                .then().statusCode(201)
                .extract().path("id");
    }

    private long countPosts() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("select count(*) from post");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }
}
