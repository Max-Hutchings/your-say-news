package com.yoursay.unwrapped;

import com.yoursay.unwrapped.UnwrappedTestData.TestPost;
import com.yoursay.unwrapped.service.UnwrappedReconciliationWorker;
import com.yoursay.votes.client.UserCharacteristicClient;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A reader tapping Unwrap queues generation only once the post has 500 votes, and only for a
 * reader who has voted. The admin trigger is covered by {@link UnwrappedAdminGenerationIntegrationTest}.
 */
@QuarkusTest
@TestSecurity(user = UnwrappedReaderGenerationTest.READER_EMAIL, roles = "user")
class UnwrappedReaderGenerationTest {
    static final String READER_EMAIL = "reader@yoursay.com";
    private static final long READER_ID = 4401L;

    @InjectMock
    UserCharacteristicClient userClient;
    @Inject
    AgroalDataSource dataSource;
    @Inject
    UnwrappedReconciliationWorker reconciliationWorker;

    private UnwrappedTestData data;

    @BeforeEach
    void setUp() {
        data = new UnwrappedTestData(dataSource);
        Mockito.when(userClient.getUserByEmail(Mockito.eq(READER_EMAIL), Mockito.nullable(String.class)))
                .thenReturn(Response.ok(new UserCharacteristicClient.UserRef(READER_ID)).build());
    }

    @Test
    void featuresReportBothFlagsOnByDefault() {
        given().when().get("/unwrapped/features")
                .then()
                .statusCode(200)
                .body("enabled", equalTo(true))
                .body("unwrapButton", equalTo(true));
    }

    @Test
    @TestSecurity
    void featuresRequireASignedInCaller() {
        given().when().get("/unwrapped/features").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void featuresRefuseACallerWithoutTheUserRole() {
        given().when().get("/unwrapped/features").then().statusCode(403);
    }

    @Test
    void unwrapAtFiveHundredVotesQueuesTheFiveHundredMilestone() throws Exception {
        TestPost post = data.createPost(READER_ID);
        try {
            data.insertVotes(post, READER_ID, 499);

            given().when().post("/posts/" + post.id() + "/unwrapped/generate")
                    .then()
                    .statusCode(202)
                    .body("postId", equalTo((int) post.id()))
                    .body("queued", equalTo(true));

            assertEquals(1, data.count("unwrapped_reconciliation", post.id()));
            reconcileUntilProcessed(post.id());
            assertEquals(500, data.jobMilestone(post.id()));
        } finally {
            data.deletePost(post.id());
        }
    }

    /**
     * The worker claims the oldest queued post first, so a post another test left queued would be
     * processed before ours. Drain until ours is gone rather than assuming the queue was empty.
     */
    private void reconcileUntilProcessed(long postId) throws Exception {
        for (int attempt = 0; attempt < 50 && data.count("unwrapped_reconciliation", postId) > 0; attempt++) {
            reconciliationWorker.reconcileOne();
        }
        assertEquals(0, data.count("unwrapped_reconciliation", postId));
    }

    @Test
    void unwrapBelowFiveHundredVotesQueuesNothing() throws Exception {
        TestPost post = data.createPost(READER_ID);
        try {
            data.insertVotes(post, READER_ID, 498);

            given().when().post("/posts/" + post.id() + "/unwrapped/generate")
                    .then()
                    .statusCode(202)
                    .body("queued", equalTo(false));

            assertEquals(0, data.count("unwrapped_reconciliation", post.id()));
        } finally {
            data.deletePost(post.id());
        }
    }

    @Test
    void readerWhoHasNotVotedCannotQueueGeneration() throws Exception {
        TestPost post = data.createPost(READER_ID);
        try {
            data.insertVotes(post, 4402L, 600);

            given().when().post("/posts/" + post.id() + "/unwrapped/generate")
                    .then()
                    .statusCode(403);

            assertEquals(0, data.count("unwrapped_reconciliation", post.id()));
        } finally {
            data.deletePost(post.id());
        }
    }
}
