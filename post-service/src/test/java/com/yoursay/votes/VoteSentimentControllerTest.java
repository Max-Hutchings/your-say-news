package com.yoursay.votes;

import com.yoursay.votes.client.UserCharacteristicClient;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for the Stage 4 sentiment endpoints:
 * {@code GET /votes/{postId}/sentiment} and {@code GET /votes/{postId}/sentiment/{axis}}.
 *
 * <p>The essential slice of Workstream T: a <em>known, deterministic</em> vote distribution with
 * characteristic snapshots is seeded straight into the votes table, then the by-characteristic
 * breakdown is asserted to match it <em>exactly</em> (per-bucket yes/no counts and percentages),
 * proving Stage 4 aggregation is correct — not merely non-empty. The gating rules
 * (must-have-voted → 403, unknown post → 404, unknown axis → 400, wrong role → 403) are pinned too.
 */
@QuarkusTest
@TestSecurity(user = "voter@yoursay.com", roles = "user")
public class VoteSentimentControllerTest {

    private static final String VOTER_EMAIL = "voter@yoursay.com";
    private static final long VOTER_ID = 55L;

    @InjectMock
    @RestClient
    UserCharacteristicClient userClient;

    @Inject
    AgroalDataSource dataSource;

    @BeforeEach
    public void setup() {
        Mockito.reset(userClient);
        // user-service lookup: caller email → numeric id. nullable() so it fires with or without a header.
        Response userRef = Response.ok(new UserCharacteristicClient.UserRef(VOTER_ID)).build();
        Mockito.when(userClient.getUserByEmail(Mockito.eq(VOTER_EMAIL), Mockito.nullable(String.class)))
                .thenReturn(userRef);
    }

    // ── overall vs by-characteristic on a known distribution ───────────────────

    @Test
    public void overallSentiment_afterVoting_matchesSeededDistributionExactly() {
        long postId = seedKnownDistribution();

        JsonPath json = given()
                .when().get("/votes/" + postId + "/sentiment")
                .then().statusCode(200)
                .extract().jsonPath();

        assertEquals(postId, json.getLong("postId"));
        assertEquals("OVERALL", json.getString("characteristic"));
        assertEquals(0, json.getInt("suppressedBuckets"));

        List<Map<String, Object>> buckets = json.getList("buckets");
        assertEquals(1, buckets.size());
        // 7 yes / 7 no across all 14 seeded votes.
        assertBucket(buckets.get(0), "OVERALL", 7, 7, 14, 50.0);
    }

    @Test
    public void sentimentByCharacteristic_afterVoting_matchesSeededDistributionExactly() {
        long postId = seedKnownDistribution();

        JsonPath json = given()
                .when().get("/votes/" + postId + "/sentiment/politicalPersuasion")
                .then().statusCode(200)
                .extract().jsonPath();

        assertEquals("politicalPersuasion", json.getString("characteristic"));
        assertEquals(0, json.getInt("suppressedBuckets"));

        List<Map<String, Object>> buckets = json.getList("buckets");
        assertEquals(4, buckets.size());
        // Largest bucket first: RIGHT(5) > LEFT(4) > CENTRE(3) > UNKNOWN(2).
        assertBucket(buckets.get(0), "RIGHT", 1, 4, 5, 20.0);
        assertBucket(buckets.get(1), "LEFT", 3, 1, 4, 75.0);
        assertBucket(buckets.get(2), "CENTRE", 2, 1, 3, 66.667);
        // Votes lacking a political value land in exactly one UNKNOWN bucket, so buckets reconcile
        // to the 14-vote overall total above (5 + 4 + 3 + 2 = 14).
        assertBucket(buckets.get(3), "UNKNOWN", 1, 1, 2, 50.0);
    }

    @Test
    public void sentimentResponse_carriesNoIdentityFields() {
        // The PII boundary: the aggregate result exposes only bucket labels, counts and percentages —
        // never a userId, email or any identifying key that could tie an aggregate back to a person.
        long postId = seedKnownDistribution();

        JsonPath json = given()
                .when().get("/votes/" + postId + "/sentiment/politicalPersuasion")
                .then().statusCode(200)
                .extract().jsonPath();

        assertEquals(Set.of("postId", "characteristic", "buckets", "suppressedBuckets"),
                json.getMap("$").keySet());
        List<Map<String, Object>> buckets = json.getList("buckets");
        for (Map<String, Object> bucket : buckets) {
            assertEquals(Set.of("bucket", "yesCount", "noCount", "total", "yesPct", "noPct"),
                    bucket.keySet());
        }
    }

    // ── gating ─────────────────────────────────────────────────────────────────

    @Test
    public void sentiment_callerHasNotVoted_returns403() {
        // Post exists and has votes, but none from the caller — results stay locked.
        long postId = insertPost();
        insertVote(postId, 900L, true, "LEFT");

        given()
                .when().get("/votes/" + postId + "/sentiment")
                .then()
                .statusCode(403)
                .body("code", org.hamcrest.CoreMatchers.is("VOTE_RESULTS_LOCKED"));

        given()
                .when().get("/votes/" + postId + "/sentiment/politicalPersuasion")
                .then()
                .statusCode(403)
                .body("code", org.hamcrest.CoreMatchers.is("VOTE_RESULTS_LOCKED"));
    }

    @Test
    public void sentimentByCharacteristic_notVotedAndUnknownAxis_gatesFirstWith403() {
        // Gating runs before axis validation, so a non-voter cannot probe which axes are valid
        // (they get 403, never a 400 that would confirm/deny the axis). Locks that ordering.
        long postId = insertPost();
        insertVote(postId, 901L, true, "LEFT"); // a vote exists, but not from the caller

        given()
                .when().get("/votes/" + postId + "/sentiment/favouriteColour")
                .then()
                .statusCode(403)
                .body("code", org.hamcrest.CoreMatchers.is("VOTE_RESULTS_LOCKED"));
    }

    @Test
    public void sentiment_unknownPost_returns404() {
        given()
                .when().get("/votes/9999999999/sentiment")
                .then()
                .statusCode(404)
                .body("code", org.hamcrest.CoreMatchers.is("VOTE_POST_MISSING"));
    }

    @Test
    public void sentimentByCharacteristic_unknownAxis_returns400() {
        long postId = insertPost();
        insertVote(postId, VOTER_ID, true, "LEFT"); // caller has voted, so gating passes

        given()
                .when().get("/votes/" + postId + "/sentiment/favouriteColour")
                .then()
                .statusCode(400)
                .body("code", org.hamcrest.CoreMatchers.is("VOTE_UNKNOWN_AXIS"));
    }

    @Test
    public void sentiment_singleCallerVote_isTheMinimalNonEmptyResult() {
        // The gate makes a truly empty result unreachable over HTTP (a voted caller means ≥1 vote);
        // the minimal case is the caller's own single vote — assert it aggregates correctly.
        long postId = insertPost();
        insertVote(postId, VOTER_ID, true, "CENTRE");

        JsonPath json = given()
                .when().get("/votes/" + postId + "/sentiment")
                .then().statusCode(200)
                .extract().jsonPath();
        List<Map<String, Object>> buckets = json.getList("buckets");
        assertEquals(1, buckets.size());
        assertBucket(buckets.get(0), "OVERALL", 1, 0, 1, 100.0);
    }

    @Test
    @TestSecurity(user = "badactor@yoursay.com", roles = "admin")
    public void sentiment_wrongRole_returns403() {
        given()
                .when().get("/votes/1/sentiment")
                .then()
                .statusCode(403);

        given()
                .when().get("/votes/1/sentiment/politicalPersuasion")
                .then()
                .statusCode(403);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    /**
     * Seed a fixed 14-vote distribution on a fresh post, including the caller's own vote so the
     * results gate opens. Distribution by {@code politicalPersuasion} / stance:
     * <pre>
     *   RIGHT : 1 yes, 4 no   (5)
     *   LEFT  : 3 yes, 1 no   (4)   ← includes the caller (VOTER_ID), a LEFT yes
     *   CENTRE: 2 yes, 1 no   (3)
     *   (none): 1 yes, 1 no   (2)   → UNKNOWN bucket
     *   overall            7 yes / 7 no (14)
     * </pre>
     */
    private long seedKnownDistribution() {
        long postId = insertPost();
        long uid = 200L;

        // RIGHT: 1 yes, 4 no
        insertVote(postId, uid++, true, "RIGHT");
        insertVote(postId, uid++, false, "RIGHT");
        insertVote(postId, uid++, false, "RIGHT");
        insertVote(postId, uid++, false, "RIGHT");
        insertVote(postId, uid++, false, "RIGHT");

        // LEFT: 3 yes (one is the caller), 1 no
        insertVote(postId, VOTER_ID, true, "LEFT");
        insertVote(postId, uid++, true, "LEFT");
        insertVote(postId, uid++, true, "LEFT");
        insertVote(postId, uid++, false, "LEFT");

        // CENTRE: 2 yes, 1 no
        insertVote(postId, uid++, true, "CENTRE");
        insertVote(postId, uid++, true, "CENTRE");
        insertVote(postId, uid++, false, "CENTRE");

        // No political value → UNKNOWN: 1 yes, 1 no
        insertVote(postId, uid++, true, null);
        insertVote(postId, uid, false, null);

        return postId;
    }

    private static void assertBucket(Map<String, Object> bucket, String label,
                                     long yes, long no, long total, double yesPct) {
        assertEquals(label, bucket.get("bucket"));
        assertEquals(yes, ((Number) bucket.get("yesCount")).longValue());
        assertEquals(no, ((Number) bucket.get("noCount")).longValue());
        assertEquals(total, ((Number) bucket.get("total")).longValue());
        assertEquals(yesPct, ((Number) bucket.get("yesPct")).doubleValue(), 0.01);
        assertEquals(100.0 - yesPct, ((Number) bucket.get("noPct")).doubleValue(), 0.01);
    }

    /** Insert a bare post and return its id (only the three NOT-NULL columns without a default). */
    private long insertPost() {
        String sql = "INSERT INTO post (user_id, summary, support_question) "
                + "VALUES (?, ?, ?) RETURNING id";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, VOTER_ID);
            ps.setString(2, "A seeded post to aggregate votes over.");
            ps.setString(3, "Do you agree?");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to insert test post", e);
        }
    }

    /**
     * Insert one vote carrying a characteristic snapshot. {@code politicalPersuasion} null seeds an
     * empty snapshot ({@code {}}) so the vote resolves to the UNKNOWN bucket on that axis.
     */
    private void insertVote(long postId, long userId, boolean voteFor, String politicalPersuasion) {
        String snapshot = politicalPersuasion == null
                ? "{}"
                : "{\"politicalPersuasion\":\"" + politicalPersuasion + "\"}";
        String sql = "INSERT INTO votes (post_id, user_id, vote_for, characteristic_snapshot) "
                + "VALUES (?, ?, ?, ?::jsonb)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, postId);
            ps.setLong(2, userId);
            ps.setBoolean(3, voteFor);
            ps.setString(4, snapshot);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to insert test vote", e);
        }
    }
}
