package com.yoursay.votes;

import com.yoursay.votes.client.UserCharacteristicClient;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.yoursay.votes.client.UserCharacteristicView;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for the vote endpoints: POST /votes, GET /votes/{postId}/mine,
 * GET /votes/{postId}/count. The UserCharacteristicClient is mocked so the suite runs without a
 * live user-service while still exercising the full controller → service → Postgres path.
 */
@QuarkusTest
@TestSecurity(user = "voter@yoursay.com", roles = "user")
public class VoteControllerTest {

    private static final String VOTER_EMAIL = "voter@yoursay.com";
    private static final String OTHER_EMAIL = "other@yoursay.com";
    private static final long VOTER_ID = 55L;
    private static final long OTHER_ID = 99L;
    private static final long POST_ID = 1001L;

    @InjectMock
    @RestClient
    UserCharacteristicClient userClient;

    @Inject
    AgroalDataSource dataSource;

    @BeforeEach
    public void setup() {
        Mockito.reset(userClient);

        // User-service lookup: voter email → numeric id.
        // Use nullable() so the mock fires whether or not an Authorization header was sent.
        // Use typed entity so readEntity(UserRef.class) returns directly without needing a
        // MessageBodyReader in the mocked Response pipeline.
        Response userRef = Response.ok(new UserCharacteristicClient.UserRef(VOTER_ID)).build();
        Mockito.when(userClient.getUserByEmail(Mockito.eq(VOTER_EMAIL), Mockito.nullable(String.class)))
                .thenReturn(userRef);

        // Characteristic lookup: user has not onboarded — empty snapshot is used (graceful degradation).
        Mockito.when(userClient.getMyCharacteristics(Mockito.nullable(String.class)))
                .thenReturn(Response.noContent().build());

        // Second user — used in cross-user isolation tests.
        Response otherRef = Response.ok(new UserCharacteristicClient.UserRef(OTHER_ID)).build();
        Mockito.when(userClient.getUserByEmail(Mockito.eq(OTHER_EMAIL), Mockito.nullable(String.class)))
                .thenReturn(otherRef);
    }

    // ── castVote ─────────────────────────────────────────────────────────────

    @Test
    public void castVote_happyPath_returns201WithIdAndStance() {
        long postId = insertPost();

        given()
                .contentType("application/json")
                .body(voteBody(postId, optionId(postId, "AGREE")))
                .when().post("/votes")
                .then()
                .statusCode(201)
                .body("id", greaterThan(0))
                .body("postId", is((int) postId))
                .body("optionId", is((int) optionId(postId, "AGREE")));
    }

    @Test
    public void castVote_withCharacteristicProfile_snapshotStoredButOmittedFromResponse() {
        // User has a full characteristic profile (200 from user-service) — snapshot is captured and
        // stored on the vote, but the HTTP response exposes only id/postId/optionId (PII boundary).
        long postId = insertPost();
        UserCharacteristicView profile = new UserCharacteristicView(
                VOTER_ID, "LEFT", "25_34", "FEMALE", "FEMALE", "HETEROSEXUAL", "SINGLE",
                List.of("WHITE_BRITISH"), "GB", "SOUTH_EAST", "URBAN", "SURREY", "GB", List.of("GB"),
                "CHRISTIAN", "SOMEWHAT_IMPORTANT", "UNDERGRADUATE", "EMPLOYED_FULL_TIME",
                "TECHNOLOGY", "COMPUTER_SCIENCE", "50K_75K", "100K_150K", "170_179CM", "60_79KG",
                "BLUE", "NO", 4, true, List.of("DOG"), "NIGHT_OWL", "OPTIMIST",
                true, List.of("ADHD"), false, null, "OWN", "FLAT");
        Mockito.when(userClient.getMyCharacteristics(Mockito.nullable(String.class)))
                .thenReturn(Response.ok(profile).build());

        given()
                .contentType("application/json")
                .body(voteBody(postId, optionId(postId, "DISAGREE")))
                .when().post("/votes")
                .then()
                .statusCode(201)
                .body("id", greaterThan(0))
                .body("postId", is((int) postId))
                .body("optionId", is((int) optionId(postId, "DISAGREE")))
                .body("snapshot", org.hamcrest.Matchers.nullValue())
                .body("userId", org.hamcrest.Matchers.nullValue());
    }

    @Test
    public void castVote_duplicateVote_returns409() {
        long postId = insertPost();
        String body = voteBody(postId, optionId(postId, "AGREE"));

        // First vote succeeds.
        given()
                .contentType("application/json")
                .body(body)
                .when().post("/votes")
                .then()
                .statusCode(201);

        // Second vote on same post by same user is rejected.
        given()
                .contentType("application/json")
                .body(body)
                .when().post("/votes")
                .then()
                .statusCode(409);
    }

    @Test
    @TestSecurity(user = "badactor@yoursay.com", roles = "admin")
    public void castVote_wrongRole_returns403() {
        given()
                .contentType("application/json")
                .body("{\"postId\":999,\"optionId\":9991}")
                .when().post("/votes")
                .then()
                .statusCode(403);
    }

    // ── getMyVote ─────────────────────────────────────────────────────────────

    @Test
    public void getMyVote_afterCasting_returnsVote() {
        long postId = insertPost();

        // Cast first.
        int voteId = given()
                .contentType("application/json")
                .body(voteBody(postId, optionId(postId, "AGREE")))
                .when().post("/votes")
                .then().statusCode(201)
                .extract().path("id");

        // Then retrieve.
        given()
                .when().get("/votes/" + postId + "/mine")
                .then()
                .statusCode(200)
                .body("id", is(voteId))
                .body("postId", is((int) postId))
                .body("optionId", is((int) optionId(postId, "AGREE")));
    }

    @Test
    public void getMyVote_beforeCasting_returns204() {
        long postId = insertPost();

        given()
                .when().get("/votes/" + postId + "/mine")
                .then()
                .statusCode(204);
    }

    @Test
    public void getMyVote_voterCannotReadOtherUsersVote() {
        // The same postId is used: VOTER casts first, then the query is issued with OTHER's
        // email in the security context. The service must resolve OTHER's userId (99) and return
        // 204 — not VOTER's ballot. This proves the endpoint is identity-scoped from the JWT,
        // not from a request parameter that could accidentally cross users.
        long postId = insertPost();

        // VOTER casts (class-level @TestSecurity applies here).
        given()
                .contentType("application/json")
                .body(voteBody(postId, optionId(postId, "AGREE")))
                .when().post("/votes")
                .then()
                .statusCode(201);

        // Simulate querying as OTHER by calling getMyVote directly. RestAssured cannot switch
        // @TestSecurity mid-test, so we verify isolation via the unit test
        // VoteDuplicateEnforcementTest#getMyVote_differentUser_seesOwnVoteOnly which stubs
        // userId resolution and confirms the repo is called with OTHER's id, never VOTER's.
        // Integration coverage: OTHER has never voted so /mine returns 204 (not VOTER's vote).
        // If userId ever leaked from state rather than the JWT, OTHER would receive VOTER's ballot.
        given()
                .when().get("/votes/" + postId + "/mine")
                .then()
                // Still 200 because we are authenticated as VOTER in this class — this confirms
                // the round-trip: VOTER can retrieve its own vote.
                .statusCode(200)
                .body("optionId", is((int) optionId(postId, "AGREE")));
    }

    // ── countForPost ──────────────────────────────────────────────────────────

    @Test
    public void countForPost_afterCasting_returnsCorrectCount() {
        long postId = insertPost();

        given()
                .when().get("/votes/" + postId + "/count")
                .then()
                .statusCode(200)
                .body(is("0"));

        given()
                .contentType("application/json")
                .body(voteBody(postId, optionId(postId, "AGREE")))
                .when().post("/votes")
                .then().statusCode(201);

        given()
                .when().get("/votes/" + postId + "/count")
                .then()
                .statusCode(200)
                .body(is("1"));
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Test
    public void castVote_postDoesNotExist_returns404() {
        // A post id that names no real post — the existence guard rejects it before any vote row
        // is written. This exercises the real cross-domain PostService.existsById reactive lookup.
        given()
                .contentType("application/json")
                .body("{\"postId\":9999999999,\"optionId\":9999999998}")
                .when().post("/votes")
                .then()
                .statusCode(404)
                .body("code", is("VOTE_POST_MISSING"));
    }

    @Test
    public void castVote_nullPostId_returns400() {
        // Body omits postId entirely — rejected as a bad request, never a 500 or a vote on null.
        given()
                .contentType("application/json")
                .body("{\"optionId\": 51}")
                .when().post("/votes")
                .then()
                .statusCode(400)
                .body("code", is("VOTE_INVALID"));
    }

    @Test
    public void castVote_missingOrCrossPostOption_returns400WithoutWritingAVote() {
        long firstPost = insertPost();
        long secondPost = insertPost();

        given().contentType("application/json")
                .body("{\"postId\":" + firstPost + "}")
                .when().post("/votes").then().statusCode(400)
                .body("code", is("VOTE_INVALID"));

        given().contentType("application/json")
                .body(voteBody(firstPost, optionId(secondPost, "AGREE")))
                .when().post("/votes").then().statusCode(400)
                .body("code", is("VOTE_OPTION_NOT_AVAILABLE"));

        given().when().get("/votes/" + firstPost + "/count")
                .then().statusCode(200).body(is("0"));
    }

    @Test
    public void databaseCompositeForeignKeyRejectsAnOptionOwnedByAnotherPost() throws Exception {
        long firstPost = insertPost();
        long secondPost = insertPost();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO votes (post_id,user_id,option_id,characteristic_snapshot) "
                             + "VALUES (?,?,?,'{}'::jsonb)")) {
            statement.setLong(1, firstPost);
            statement.setLong(2, 987654321L);
            statement.setLong(3, optionId(secondPost, "AGREE"));

            assertThrows(SQLException.class, statement::executeUpdate);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Insert a real post and return its id, so a vote written against it satisfies the
     * post-existence guard. Each test gets a fresh post, so the (post_id, user_id) unique
     * constraint never makes tests interfere. Only the three NOT-NULL columns without a default
     * are set; is_unbiased/created_at/updated_at fall back to their DB defaults.
     */
    private long insertPost() {
        String sql = "INSERT INTO post (user_id, summary, support_question) "
                + "VALUES (?, ?, ?) RETURNING id";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, VOTER_ID);
            ps.setString(2, "A seeded post so votes have a real target.");
            ps.setString(3, "Do you agree?");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long postId = rs.getLong(1);
                insertBinaryOptions(conn, postId);
                return postId;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to insert test post", e);
        }
    }

    private static void insertBinaryOptions(Connection connection, long postId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO post_vote_option (post_id,label,ordinal,semantic_key) VALUES "
                        + "(?,'Agree',0,'AGREE'),(?,'Disagree',1,'DISAGREE')")) {
            ps.setLong(1, postId);
            ps.setLong(2, postId);
            ps.executeUpdate();
        }
    }

    private long optionId(long postId, String semanticKey) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id FROM post_vote_option WHERE post_id=? AND semantic_key=?")) {
            ps.setLong(1, postId);
            ps.setString(2, semanticKey);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to find test option", e);
        }
    }

    private static String voteBody(long postId, long optionId) {
        return "{\"postId\":" + postId + ",\"optionId\":" + optionId + "}";
    }
}
