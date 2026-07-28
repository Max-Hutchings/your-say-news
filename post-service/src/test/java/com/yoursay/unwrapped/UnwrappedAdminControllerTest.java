package com.yoursay.unwrapped;

import com.yoursay.unwrapped.service.UnwrappedReconciliationWorker;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class UnwrappedAdminControllerTest {
    @Inject
    AgroalDataSource dataSource;
    @Inject
    UnwrappedReconciliationWorker reconciliationWorker;

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void adminCanTriggerTheNormalIdempotentMilestonePathWithoutAnotherVote() throws Exception {
        TestPost post = createPost();
        try {
            insertVotes(post, 100);
            String path = "/admin/unwrapped/posts/" + post.id() + "/generate";

            given()
                    .when().post(path)
                    .then()
                    .statusCode(202)
                    .body("postId", equalTo((int) post.id()))
                    .body("status", equalTo("RECONCILIATION_QUEUED"));

            assertEquals(1, reconciliationCount(post.id()));
            assertEquals(0, jobCount(post.id()));

            given()
                    .when().post(path)
                    .then()
                    .statusCode(202)
                    .body("postId", equalTo((int) post.id()))
                    .body("status", equalTo("RECONCILIATION_QUEUED"));

            assertEquals(1, reconciliationCount(post.id()));
            assertEquals(0, jobCount(post.id()));

            reconciliationWorker.reconcileOne();

            assertEquals(0, reconciliationCount(post.id()));
            JobState firstJob = jobState(post.id());
            assertEquals(100, firstJob.milestone());
            assertEquals("PENDING", firstJob.status());

            given()
                    .when().post(path)
                    .then()
                    .statusCode(202);
            reconciliationWorker.reconcileOne();
            assertEquals(1, jobCount(post.id()));
            assertEquals(firstJob, jobState(post.id()));
        } finally {
            deletePost(post.id());
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void manualTriggerDoesNotBypassTheNormalVoteMilestone() throws Exception {
        TestPost post = createPost();
        try {
            insertVotes(post, 99);

            given()
                    .when().post("/admin/unwrapped/posts/" + post.id() + "/generate")
                    .then()
                    .statusCode(202)
                    .body("postId", equalTo((int) post.id()))
                    .body("status", equalTo("RECONCILIATION_QUEUED"));

            reconciliationWorker.reconcileOne();

            assertEquals(0, reconciliationCount(post.id()));
            assertEquals(0, jobCount(post.id()));
        } finally {
            deletePost(post.id());
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void unknownPostDoesNotCreateAJob() {
        given()
                .when().post("/admin/unwrapped/posts/999999/generate")
                .then()
                .statusCode(404)
                .body("code", equalTo("UNWRAPPED_POST_NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "reader@yoursay.com", roles = "user")
    void nonAdminCannotTriggerGeneration() {
        given()
                .when().post("/admin/unwrapped/posts/1/generate")
                .then()
                .statusCode(403);
    }

    private TestPost createPost() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement post = connection.prepareStatement("""
                     insert into post(
                         user_id, summary, support_question, is_unbiased,
                         created_at, updated_at, voting_type, jurisdiction
                     ) values (1, 'Forced generation summary', 'Should this be generated?',
                         false, now(), now(), 'BINARY', 'GLOBAL')
                     returning id
                     """)) {
            long postId;
            try (ResultSet result = post.executeQuery()) {
                result.next();
                postId = result.getLong(1);
            }
            long optionId;
            try (PreparedStatement options = connection.prepareStatement("""
                    insert into post_vote_option(post_id, label, ordinal, semantic_key)
                    values (?, 'Agree', 0, 'AGREE'), (?, 'Disagree', 1, 'DISAGREE')
                    returning id
                    """)) {
                options.setLong(1, postId);
                options.setLong(2, postId);
                try (ResultSet result = options.executeQuery()) {
                    result.next();
                    optionId = result.getLong(1);
                }
            }
            return new TestPost(postId, optionId);
        }
    }

    private void insertVotes(TestPost post, int count) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into votes(post_id, user_id, option_id, characteristic_snapshot)
                     select ?, 900000 + generated.user_number, ?, '{}'::jsonb
                     from generate_series(1, ?) as generated(user_number)
                     """)) {
            statement.setLong(1, post.id());
            statement.setLong(2, post.optionId());
            statement.setInt(3, count);
            assertEquals(count, statement.executeUpdate());
        }
    }

    private int jobCount(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from unwrapped_analysis_job where post_id = ?")) {
            statement.setLong(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private JobState jobState(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select id, milestone, status from unwrapped_analysis_job where post_id = ?")) {
            statement.setLong(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return new JobState(result.getObject("id", UUID.class),
                        result.getInt("milestone"), result.getString("status"));
            }
        }
    }

    private int reconciliationCount(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from unwrapped_reconciliation where post_id = ?")) {
            statement.setLong(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private void deletePost(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "delete from post where id = ?")) {
            statement.setLong(1, postId);
            statement.executeUpdate();
        }
    }

    private record TestPost(long id, long optionId) {
    }

    private record JobState(UUID id, int milestone, String status) {
    }
}
