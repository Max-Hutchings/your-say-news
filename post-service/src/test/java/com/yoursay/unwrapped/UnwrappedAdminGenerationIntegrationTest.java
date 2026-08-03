package com.yoursay.unwrapped;

import com.yoursay.unwrapped.service.UnwrappedGenerationWorker;
import com.yoursay.unwrapped.service.UnwrappedReconciliationWorker;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(UnwrappedAdminGenerationIntegrationTest.ProviderProfile.class)
class UnwrappedAdminGenerationIntegrationTest {
    @Inject
    AgroalDataSource dataSource;
    @Inject
    UnwrappedReconciliationWorker reconciliationWorker;
    @Inject
    UnwrappedGenerationWorker generationWorker;

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void adminGenerateEndpointRunsThroughToAReviewableSourcedDraft() throws Exception {
        TestPost post = createPost();
        try {
            insertVotes(post, 100);

            given()
                    .when().post("/api/admin/unwrapped/posts/" + post.id() + "/generate")
                    .then()
                    .statusCode(202)
                    .body("status", equalTo("RECONCILIATION_QUEUED"));

            reconciliationWorker.reconcileOne();
            generationWorker.processNext();

            JobResult job = jobResult(post.id());
            assertEquals("DRAFT_READY", job.status());
            if (ProviderProfile.stubbed()) {
                assertEquals("stubbed-unwrapped", job.model());
                assertEquals("stub-post-" + post.id(), job.providerResponseId());
                assertEquals("https://www.ons.gov.uk/", sourceUrl(post.id()));
            }
            assertEquals(1, storyCount(post.id()));
        } finally {
            deletePost(post.id());
        }
    }

    private TestPost createPost() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement post = connection.prepareStatement("""
                     insert into post(
                         user_id, summary, support_question, is_unbiased,
                         created_at, updated_at, voting_type, jurisdiction, case_for, case_against
                     ) values (1, 'Integration generation summary', 'Should this be generated?',
                         false, now(), now(), 'BINARY', 'GLOBAL',
                         'A clear case for the proposal.', 'A clear case against the proposal.')
                     returning id
                     """)) {
            long postId;
            try (ResultSet result = post.executeQuery()) {
                result.next();
                postId = result.getLong(1);
            }
            try (PreparedStatement options = connection.prepareStatement("""
                    insert into post_vote_option(post_id, label, ordinal, semantic_key)
                    values (?, 'Agree', 0, 'AGREE'), (?, 'Disagree', 1, 'DISAGREE')
                    returning id
                    """)) {
                options.setLong(1, postId);
                options.setLong(2, postId);
                try (ResultSet result = options.executeQuery()) {
                    result.next();
                    return new TestPost(postId, result.getLong(1));
                }
            }
        }
    }

    private void insertVotes(TestPost post, int amount) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into votes(post_id, user_id, option_id, characteristic_snapshot)
                     select ?, 950000 + generated.user_number, ?, '{}'::jsonb
                     from generate_series(1, ?) as generated(user_number)
                     """)) {
            statement.setLong(1, post.id());
            statement.setLong(2, post.optionId());
            statement.setInt(3, amount);
            assertEquals(amount, statement.executeUpdate());
        }
    }

    private JobResult jobResult(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     select status, model, provider_response_id
                     from unwrapped_analysis_job where post_id = ?
                     """)) {
            statement.setLong(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return new JobResult(result.getString("status"), result.getString("model"),
                        result.getString("provider_response_id"));
            }
        }
    }

    private int storyCount(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from unwrapped_story where post_id = ?")) {
            statement.setLong(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private String sourceUrl(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select source.url from unwrapped_source source
                     join unwrapped_story story on story.id = source.story_id
                     where story.post_id = ?
                     """)) {
            statement.setLong(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
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

    /**
     * Defaults to a deterministic provider. Set UNWRAPPED_INTEGRATION_STUBBED=false and
     * UNWRAPPED_INTEGRATION_API_KEY to run this exact flow through xAI deliberately.
     */
    public static final class ProviderProfile implements QuarkusTestProfile {
        static boolean stubbed() {
            return Boolean.parseBoolean(
                    System.getenv().getOrDefault("UNWRAPPED_INTEGRATION_STUBBED", "true"));
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "unwrapped.agent.stubbed",
                    Boolean.toString(stubbed()),
                    "unwrapped.agent.api-key",
                    System.getenv().getOrDefault(
                            "UNWRAPPED_INTEGRATION_API_KEY", "test-key-not-used"));
        }
    }

    private record TestPost(long id, long optionId) {
    }

    private record JobResult(String status, String model, String providerResponseId) {
    }
}
