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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
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
            insertStronglySplitVotes(post, 100);

            given()
                    .when().post("/api/admin/unwrapped/posts/" + post.id() + "/generate")
                    .then()
                    .statusCode(202)
                    .body("status", equalTo("RECONCILIATION_QUEUED"));

            reconciliationWorker.reconcileOne();
            generationWorker.processNext();

            JobResult job = jobResult(post.id());
            assertEquals("DRAFT_READY", job.status());
            assertEquals(null, job.errorCode());
            if (ProviderProfile.stubbed()) {
                assertEquals("stubbed-unwrapped", job.model());
                assertEquals("stub-post-" + post.id(), job.providerResponseId());
                assertEquals("https://www.ons.gov.uk/", sourceUrl(post.id()));
            }
            assertEquals(1, storyCount(post.id()));
            assertEquals("unwrapped-story-v2", storySchemaVersion(post.id()));
            assertEquals("unwrapped-cohort-causal-v2", promptVersion(post.id()));
            assertEquals(2, articleParagraphCount(post.id()));
            // Both gender groups are privacy-safe here (ADR-044), so every option gets an anchor
            // and a differentiator. The two groups are the same size, so each option anchors on
            // the group that actually chose it and the pair is ordered differently per page.
            assertEquals(List.of("gender=MAN", "gender=WOMAN"), selectedCohortIds(post.id(), 0));
            assertEquals(List.of("gender=WOMAN", "gender=MAN"), selectedCohortIds(post.id(), 1));
        } finally {
            deletePost(post.id());
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void normalGenerationStillCreatesEveryOptionWhenNoDemographicCohortQualifies() throws Exception {
        TestPost post = createPost();
        try {
            insertVotesWithoutCharacteristics(post, 100);
            given().when().post("/api/admin/unwrapped/posts/" + post.id() + "/generate")
                    .then().statusCode(202);

            reconciliationWorker.reconcileOne();
            generationWorker.processNext();

            JobResult job = jobResult(post.id());
            assertEquals("DRAFT_READY", job.status());
            assertEquals(null, job.errorCode());
            assertEquals(1, storyCount(post.id()));
            assertEquals(0, selectedCohortCount(post.id()));
        } finally {
            deletePost(post.id());
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void benchmarkGeneratesThreeDirectDraftsWithoutCreatingPublicationWork() throws Exception {
        TestPost post = createPost();
        try {
            insertStronglySplitVotes(post, 100);

            given()
                    .contentType("application/json")
                    .body(Map.of("systemPrompts", List.of(
                            "Write concise analysis.",
                            "Write detailed analysis.",
                            "Write conversational analysis.")))
                    .when().post("/api/admin/unwrapped/posts/" + post.id() + "/benchmark")
                    .then()
                    .statusCode(200)
                    .body("postId", equalTo((int) post.id()))
                    .body("variants", hasSize(3))
                    .body("variants.position", equalTo(List.of(1, 2, 3)))
                    .body("variants.status", everyItem(equalTo("SUCCEEDED")))
                    .body("variants.argumentPages", everyItem(hasSize(2)));

            assertEquals(0, lifecycleCount("unwrapped_reconciliation", post.id()));
            assertEquals(0, lifecycleCount("unwrapped_analysis_job", post.id()));
            assertEquals(0, storyCount(post.id()));
        } finally {
            deletePost(post.id());
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void benchmarkCallsTheAgentDirectlyForEveryOptionWithoutAQualifiedCohort() throws Exception {
        TestPost post = createPost();
        try {
            insertVotesWithoutCharacteristics(post, 100);

            given()
                    .contentType("application/json")
                    .body(Map.of("systemPrompts", List.of("Write a concise balanced analysis.")))
                    .when().post("/api/admin/unwrapped/posts/" + post.id() + "/benchmark")
                    .then()
                    .statusCode(200)
                    .body("postId", equalTo((int) post.id()))
                    .body("variants", hasSize(1))
                    .body("variants[0].status", equalTo("SUCCEEDED"))
                    .body("variants[0].systemPrompt", equalTo("Write a concise balanced analysis."))
                    .body("variants[0].argumentPages", hasSize(2))
                    .body("variants[0].argumentPages.selectedCohortIds",
                            equalTo(List.of(List.of(), List.of())));

            assertEquals(0, lifecycleCount("unwrapped_reconciliation", post.id()));
            assertEquals(0, lifecycleCount("unwrapped_analysis_job", post.id()));
            assertEquals(0, storyCount(post.id()));
        } finally {
            deletePost(post.id());
        }
    }

    private TestPost createPost() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement post = connection.prepareStatement("""
                     insert into post(
                         user_id, summary, support_question, is_ai_generated,
                         created_at, updated_at, voting_type, jurisdiction, case_for, case_against
                     ) values (1,
                         'A council is considering permits that cap street-performance hours and rotate busy pitches after residents complained about amplified evening music.',
                         'Should street performers need a licence to play in busy public spaces?',
                         false, now(), now(), 'BINARY', 'GLOBAL',
                         'Licensing can limit noise, protect residents and keep popular pitches fairly shared.',
                         'Permits and auditions risk removing the spontaneity that makes street performance valuable.')
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
                    long agree = result.getLong(1);
                    result.next();
                    return new TestPost(postId, agree, result.getLong(1));
                }
            }
        }
    }

    private void insertStronglySplitVotes(TestPost post, int amountPerOption) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into votes(post_id, user_id, option_id, characteristic_snapshot)
                     select ?, 950000 + generated.user_number, ?, '{"gender":"MAN"}'::jsonb
                     from generate_series(1, ?) as generated(user_number)
                     union all
                     select ?, 960000 + generated.user_number, ?, '{"gender":"WOMAN"}'::jsonb
                     from generate_series(1, ?) as generated(user_number)
                     """)) {
            statement.setLong(1, post.id());
            statement.setLong(2, post.agreeOptionId());
            statement.setInt(3, amountPerOption);
            statement.setLong(4, post.id());
            statement.setLong(5, post.disagreeOptionId());
            statement.setInt(6, amountPerOption);
            assertEquals(amountPerOption * 2, statement.executeUpdate());
        }
    }

    private void insertVotesWithoutCharacteristics(TestPost post, int amount) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into votes(post_id, user_id, option_id, characteristic_snapshot)
                     select ?, 970000 + generated.user_number, ?, '{}'::jsonb
                     from generate_series(1, ?) as generated(user_number)
                     """)) {
            statement.setLong(1, post.id());
            statement.setLong(2, post.agreeOptionId());
            statement.setInt(3, amount);
            assertEquals(amount, statement.executeUpdate());
        }
    }

    private JobResult jobResult(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     select status, model, provider_response_id, error_code
                     from unwrapped_analysis_job where post_id = ?
                     """)) {
            statement.setLong(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return new JobResult(result.getString("status"), result.getString("model"),
                        result.getString("provider_response_id"), result.getString("error_code"));
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

    private int lifecycleCount(String table, long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from " + table + " where post_id = ?")) {
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

    private String storySchemaVersion(long postId) throws Exception {
        return storyValue(postId, "story_schema_version");
    }

    private String promptVersion(long postId) throws Exception {
        return storyValue(postId, "prompt_version");
    }

    private String storyValue(long postId, String column) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select " + column + " from unwrapped_story where post_id = ?")) {
            statement.setLong(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private int articleParagraphCount(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select jsonb_array_length(story_json->'pages'->0->'paragraphs')
                     from unwrapped_story where post_id = ?
                     """)) {
            statement.setLong(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    /**
     * The cohort ids the stored story names for one page, in order. Pinning the ids rather than
     * only their count is what makes this track the selector: the stub caps at two candidates, so a
     * count alone stays 2 however many the selector actually shortlisted.
     */
    private List<String> selectedCohortIds(long postId, int pageIndex) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select cohort.id
                     from unwrapped_story,
                          jsonb_array_elements_text(
                                  story_json->'pages'->?->'selectedCohortIds')
                                  with ordinality as cohort(id, position)
                     where post_id = ?
                     order by cohort.position
                     """)) {
            statement.setInt(1, pageIndex);
            statement.setLong(2, postId);
            List<String> ids = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ids.add(result.getString(1));
                }
            }
            return ids;
        }
    }

    private int selectedCohortCount(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select jsonb_array_length(story_json->'pages'->0->'selectedCohortIds')
                     from unwrapped_story where post_id = ?
                     """)) {
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

    private record TestPost(long id, long agreeOptionId, long disagreeOptionId) {
    }

    private record JobResult(String status, String model, String providerResponseId,
                             String errorCode) {
    }
}
