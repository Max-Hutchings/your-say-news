package com.yoursay.feed;

import com.yoursay.feed.client.FeedUserClient;
import com.yoursay.feed.client.SocialClient;
import com.yoursay.feed.service.FeedCursor;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.Response;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keyset feed pagination against the real reactive Postgres (ADR-042). Fixture posts are dated far
 * in the future so they sit at the head of the feed regardless of what other suites left in the
 * table, and are removed afterwards.
 *
 * <p>These cover the parts only the database can prove: the {@code (created_at, id)} keyset
 * predicate pages without gaps or repeats <em>including across posts sharing one timestamp</em>, and
 * the post-type filter really runs in SQL, so a video feed keeps paging past a run of article posts
 * instead of reporting a false end of feed.
 *
 * <p>Auth is covered by {@code PostControllerAuthTest} (401 anonymous, 403 wrong role on
 * {@code /feed}); this class runs as an authorised reader throughout.
 */
@QuarkusTest
@TestSecurity(user = "reader@yoursay.com", roles = "user")
public class FeedKeysetPaginationTest {

    private static final String VIEWER_EMAIL = "reader@yoursay.com";
    private static final long VIEWER_ID = 501L;
    private static final long AUTHOR_ID = 502L;
    private static final long FOLLOWED_AUTHOR_ID = 503L;
    private static final String MARKER = "keyset-fixture";
    /** Far enough ahead that fixture posts outrank anything another suite persisted. */
    private static final Instant BASE = Instant.parse("2099-01-01T12:00:00Z");
    /** The two posts sharing this instant force the cursor's id tie-break to do real work. */
    private static final Instant SHARED_INSTANT = BASE.minusSeconds(300);

    @InjectMock
    FeedUserClient userClient;

    @InjectMock
    SocialClient socialClient;

    @InjectMock
    S3Presigner presigner;

    @Inject
    AgroalDataSource dataSource;

    /** Fixture post ids in the order the feed must serve them (newest first). */
    private final List<Long> ids = new ArrayList<>();

    @BeforeEach
    public void setup() throws Exception {
        Mockito.reset(userClient, socialClient, presigner);
        Mockito.when(userClient.getUserByEmail(Mockito.eq(VIEWER_EMAIL), Mockito.any()))
                .thenReturn(Uni.createFrom().item(new FeedUserClient.UserRef(VIEWER_ID)));
        follows();

        PresignedGetObjectRequest get = Mockito.mock(PresignedGetObjectRequest.class);
        Mockito.when(get.url()).thenReturn(URI.create("https://s3.local/download?sig=get").toURL());
        Mockito.when(presigner.presignGetObject(Mockito.any(
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                .thenReturn(get);

        // Newest first: video, article, article, followed-author article, video, then two articles
        // sharing one instant. The three articles between the videos are what a windowed in-memory
        // filter used to mistake for the end of a video feed.
        ids.clear();
        ids.add(insertPost(AUTHOR_ID, BASE, true));
        ids.add(insertPost(AUTHOR_ID, BASE.minusSeconds(60), false));
        ids.add(insertPost(AUTHOR_ID, BASE.minusSeconds(120), false));
        ids.add(insertPost(FOLLOWED_AUTHOR_ID, BASE.minusSeconds(180), false));
        ids.add(insertPost(AUTHOR_ID, BASE.minusSeconds(240), true));
        // Inserted oldest-id first; `id desc` means the second of the pair is served first.
        long sharedLowerId = insertPost(AUTHOR_ID, SHARED_INSTANT, false);
        long sharedHigherId = insertPost(AUTHOR_ID, SHARED_INSTANT, false);
        ids.add(sharedHigherId);
        ids.add(sharedLowerId);
    }

    @AfterEach
    public void cleanup() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "delete from post where summary like ?")) {
            statement.setString(1, MARKER + "%");
            statement.executeUpdate();
        }
    }

    @Test
    public void pagesTheWholeFeedByCursorWithoutGapsOrRepeats() {
        List<Long> collected = new ArrayList<>();
        String cursor = null;

        for (int request = 0; request < 4; request++) {
            Response response = feed(cursor, 2, null);
            collected.addAll(response.jsonPath().getList("posts.id", Long.class));
            cursor = response.jsonPath().getString("nextCursor");
        }

        assertEquals(ids, collected.subList(0, ids.size()));
        assertEquals(collected.size(), collected.stream().distinct().count(),
                "cursor paging served a post twice");
    }

    @Test
    public void pagesBetweenTwoPostsSharingOneTimestampWithoutSkippingEither() {
        // The page boundary is placed between the pair, so resuming needs the cursor's id
        // tie-break. Without `or (created_at = :cursor and id < :cursorId)` the next page would
        // ask for strictly older posts and the second of the pair would be lost for good.
        Response first = feed(null, 6, null);
        String cursor = first.jsonPath().getString("nextCursor");

        List<Long> resumed = feed(cursor, 1, null).jsonPath().getList("posts.id", Long.class);

        assertEquals(ids.subList(0, 6), first.jsonPath().getList("posts.id", Long.class));
        assertEquals(FeedCursor.encode(SHARED_INSTANT, ids.get(5)), cursor);
        assertEquals(List.of(ids.get(6)), resumed);
    }

    @Test
    public void boostsAFollowedAuthorWithinThePageWhileTheCursorTracksTheScanTail() {
        // ADR-042's deliberate trade: the boost reorders the page only. The followed author's post
        // is the page's oldest, so it leads the ranked output while remaining the scan tail — the
        // cursor must name it, not the last ranked post.
        follows(FOLLOWED_AUTHOR_ID);

        Response page = feed(null, 4, null);

        assertEquals(List.of(ids.get(3), ids.get(0), ids.get(1), ids.get(2)),
                page.jsonPath().getList("posts.id", Long.class));
        assertEquals(FeedCursor.encode(BASE.minusSeconds(180), ids.get(3)),
                page.jsonPath().getString("nextCursor"));
    }

    @Test
    public void aVideoFeedPagesPastTheArticlesBetweenTheTwoVideos() {
        Response first = feed(null, 1, "VIDEO");
        List<Long> firstPage = first.jsonPath().getList("posts.id", Long.class);
        String cursor = first.jsonPath().getString("nextCursor");
        assertNotNull(cursor, "a second video exists, so the feed must not end here");

        List<Long> secondPage = feed(cursor, 1, "VIDEO").jsonPath().getList("posts.id", Long.class);

        assertEquals(List.of(ids.get(0)), firstPage);
        assertEquals(List.of(ids.get(4)), secondPage);
    }

    @Test
    public void anArticleFeedExcludesEveryPostCarryingAVideo() {
        List<Long> articles = feed(null, 5, "ARTICLE").jsonPath().getList("posts.id", Long.class);

        assertEquals(List.of(ids.get(1), ids.get(2), ids.get(3), ids.get(5), ids.get(6)), articles);
    }

    @Test
    public void pagingToTheBottomOfTheFeedEndsWithANullCursor() {
        // Deterministic regardless of what other suites left behind: page with the maximum size
        // until the service reports the end, then check the fixture run came back whole and once.
        List<Long> collected = new ArrayList<>();
        String cursor = null;
        int requests = 0;

        do {
            Response response = feed(cursor, 50, null);
            collected.addAll(response.jsonPath().getList("posts.id", Long.class));
            cursor = response.jsonPath().getString("nextCursor");
            requests++;
        } while (cursor != null && requests < 20);

        assertNull(cursor, "paging never reached the end of the feed");
        assertEquals(ids, collected.subList(0, ids.size()));
        assertEquals(collected.size(), collected.stream().distinct().count());
    }

    @Test
    public void eachPostCarriesItsOwnVoteOptionsWhenTheyAreFetchedInOneQuery() {
        // The batched options fetch groups one result set by post id; mis-grouping would hand every
        // post the same options, so the labels are made post-specific on purpose.
        Response page = feed(null, 2, null);

        assertEquals(List.of("Yes " + ids.get(0), "No " + ids.get(0)),
                page.jsonPath().getList("posts[0].voteOptions.label"));
        assertEquals(List.of("Yes " + ids.get(1), "No " + ids.get(1)),
                page.jsonPath().getList("posts[1].voteOptions.label"));
    }

    @Test
    public void theFeedExposesNoPersonallyIdentifyingAuthorDetail() {
        // Aggregate-not-identity is the product rule: a feed post names its author only by id.
        Response page = feed(null, 1, null);

        assertEquals(AUTHOR_ID, page.jsonPath().getLong("posts[0].userId"));
        assertTrue(page.jsonPath().getMap("posts[0]").keySet().stream()
                        .noneMatch(field -> Set.of("email", "name", "dateOfBirth", "authorEmail")
                                .contains(field)),
                "feed post leaked an identifying author field");
    }

    @Test
    public void aMalformedCursorIsRejectedRatherThanRestartingTheFeed() {
        given().queryParam("cursor", "!!! not a cursor !!!")
                .when().get("/feed")
                .then().statusCode(400)
                .body("code", org.hamcrest.Matchers.is("FEED_CURSOR_INVALID"));
    }

    @Test
    public void anAbsentSizeFallsBackToTheDefaultPageSize() {
        List<Long> page = feed(null, null, null).jsonPath().getList("posts.id", Long.class);

        assertEquals(ids.subList(0, 5), page);
    }

    /** Point the mocked social client at the given followed author ids for this test. */
    private void follows(Long... authorIds) {
        Mockito.when(socialClient.getFollowing(Mockito.any()))
                .thenReturn(Uni.createFrom().item(new SocialClient.FollowingRef(Set.of(authorIds))));
    }

    private static Response feed(String cursor, Integer size, String type) {
        var request = given();
        if (cursor != null) {
            request = request.queryParam("cursor", cursor);
        }
        if (size != null) {
            request = request.queryParam("size", size);
        }
        if (type != null) {
            request = request.queryParam("type", type);
        }
        return request.when().get("/feed").then().statusCode(200).extract().response();
    }

    private long insertPost(long authorId, Instant createdAt, boolean withVideo) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            long postId;
            try (PreparedStatement statement = connection.prepareStatement("""
                    insert into post (user_id, summary, support_question, is_unbiased, jurisdiction,
                                      voting_type, created_at, updated_at)
                    values (?, ?, ?, false, 'GLOBAL', 'BINARY', ?, ?) returning id
                    """)) {
                statement.setLong(1, authorId);
                statement.setString(2, MARKER + " " + createdAt + " " + (withVideo ? "video" : "article"));
                statement.setString(3, "Should this fixture post be supported?");
                statement.setTimestamp(4, Timestamp.from(createdAt));
                statement.setTimestamp(5, Timestamp.from(createdAt));
                try (ResultSet keys = statement.executeQuery()) {
                    keys.next();
                    postId = keys.getLong(1);
                }
            }
            insertVoteOption(connection, postId, "Yes " + postId, 0, "AGREE");
            insertVoteOption(connection, postId, "No " + postId, 1, "DISAGREE");
            if (withVideo) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        insert into post_media (post_id, media_type, orientation, s3_key, content_type,
                                                ordinal, created_at)
                        values (?, 'VIDEO', 'PORTRAIT', ?, 'video/mp4', 0, ?)
                        """)) {
                    statement.setLong(1, postId);
                    statement.setString(2, "posts/" + postId + "/video.mp4");
                    statement.setTimestamp(3, Timestamp.from(createdAt));
                    statement.executeUpdate();
                }
            }
            return postId;
        }
    }

    private static void insertVoteOption(Connection connection, long postId, String label,
                                         int ordinal, String semanticKey) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into post_vote_option (post_id, label, ordinal, semantic_key)
                values (?, ?, ?, ?)
                """)) {
            statement.setLong(1, postId);
            statement.setString(2, label);
            statement.setInt(3, ordinal);
            statement.setString(4, semanticKey);
            statement.executeUpdate();
        }
    }
}
