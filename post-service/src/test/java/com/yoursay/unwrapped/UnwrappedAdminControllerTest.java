package com.yoursay.unwrapped;

import com.yoursay.unwrapped.agent.UnwrappedSystemPrompt;
import com.yoursay.unwrapped.service.UnwrappedReconciliationWorker;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class UnwrappedAdminControllerTest {
    @Inject
    AgroalDataSource dataSource;
    @Inject
    UnwrappedReconciliationWorker reconciliationWorker;
    @Inject
    UnwrappedMilestoneService milestoneService;

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void adminViteOriginCanTriggerGeneration() throws Exception {
        TestPost post = createPost();
        try {
            given()
                    .header("Origin", "http://localhost:8083")
                    .when().post("/api/admin/unwrapped/posts/" + post.id() + "/generate")
                    .then()
                    .statusCode(202)
                    .header("Access-Control-Allow-Origin", "http://localhost:8083")
                    .body("postId", equalTo((int) post.id()))
                    .body("status", equalTo("RECONCILIATION_QUEUED"));
        } finally {
            deletePost(post.id());
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void seededVoteTotalsStayIdleUntilAnAdminExplicitlyQueuesThePost() throws Exception {
        TestPost post = createPost();
        try {
            insertVotes(post, 100);

            assertEquals(0, reconciliationCount(post.id()));
            assertEquals(0, jobCount(post.id()));
            List<Map<String, Object>> statuses = given()
                    .when().get("/api/admin/unwrapped/generation-status")
                    .then().statusCode(200)
                    .extract().jsonPath().getList("statuses");
            assertFalse(statuses.stream().anyMatch(
                    candidate -> ((Number) candidate.get("postId")).longValue() == post.id()));

            given()
                    .when().post("/api/admin/unwrapped/posts/" + post.id() + "/generate")
                    .then().statusCode(202)
                    .body("postId", equalTo((int) post.id()))
                    .body("status", equalTo("RECONCILIATION_QUEUED"));
            reconciliationWorker.reconcileOne();

            assertEquals(0, reconciliationCount(post.id()));
            assertEquals(1, jobCount(post.id()));
            assertEquals("PENDING", jobState(post.id()).status());
        } finally {
            deletePost(post.id());
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void adminPostDeskShowsPostDetailsAndExactOverallVoteSplitWithoutVoterData() throws Exception {
        TestPost post = createPost();
        try {
            insertVotes(post, 3);
            insertVotes(post.id(), post.disagreeOptionId(), 2, 10_000);

            List<Map<String, Object>> posts = given()
                    .queryParam("page", 0)
                    .queryParam("size", 50)
                    .when().get("/api/admin/unwrapped/posts")
                    .then()
                    .statusCode(200)
                    .extract().jsonPath().getList("$");

            Map<String, Object> item = posts.stream()
                    .filter(candidate -> ((Number) candidate.get("postId")).longValue() == post.id())
                    .findFirst()
                    .orElseThrow();
            assertEquals(Set.of("postId", "summary", "question", "caseFor", "caseAgainst",
                    "jurisdiction", "votingType", "createdAt", "canonicalVoteCount", "overall"),
                    item.keySet());
            assertEquals("Forced generation summary", item.get("summary"));
            assertEquals("Should this be generated?", item.get("question"));
            assertEquals("A clear case for the proposal.", item.get("caseFor"));
            assertEquals("A clear case against the proposal.", item.get("caseAgainst"));
            assertEquals("GLOBAL", item.get("jurisdiction"));
            assertEquals("BINARY", item.get("votingType"));
            assertEquals(5, ((Number) item.get("canonicalVoteCount")).intValue());
            assertFalse(item.containsKey("userId"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> overall = (List<Map<String, Object>>) item.get("overall");
            assertEquals(List.of("Agree", "Disagree"),
                    overall.stream().map(option -> (String) option.get("label")).toList());
            assertEquals(List.of(3, 2),
                    overall.stream().map(option -> ((Number) option.get("count")).intValue()).toList());
            assertEquals(List.of(60.0, 40.0),
                    overall.stream().map(option -> ((Number) option.get("percentage")).doubleValue()).toList());
            assertEquals(Set.of("optionId", "label", "ordinal", "semanticKey", "count", "percentage"),
                    overall.getFirst().keySet());
            assertEquals(Set.of("optionId", "label", "ordinal", "semanticKey", "count", "percentage"),
                    overall.getLast().keySet());
            assertEquals(List.of(post.agreeOptionId(), post.disagreeOptionId()),
                    overall.stream().map(option -> ((Number) option.get("optionId")).longValue()).toList());
            assertEquals(List.of(0, 1),
                    overall.stream().map(option -> ((Number) option.get("ordinal")).intValue()).toList());
            assertEquals(List.of("AGREE", "DISAGREE"),
                    overall.stream().map(option -> (String) option.get("semanticKey")).toList());
        } finally {
            deletePost(post.id());
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void postDeskHonoursRecentPostPagination() throws Exception {
        TestPost older = createPost();
        TestPost newer = createPost();
        try {
            setCreatedAt(older.id(), "2034-01-01T12:00:00Z");
            setCreatedAt(newer.id(), "2035-01-01T12:00:00Z");

            List<Integer> firstPage = given()
                    .queryParam("page", 0).queryParam("size", 1)
                    .when().get("/api/admin/unwrapped/posts")
                    .then().statusCode(200)
                    .extract().jsonPath().getList("postId");
            List<Integer> secondPage = given()
                    .queryParam("page", 1).queryParam("size", 1)
                    .when().get("/api/admin/unwrapped/posts")
                    .then().statusCode(200)
                    .extract().jsonPath().getList("postId");
            List<Integer> emptyPage = given()
                    .queryParam("page", 10_000).queryParam("size", 1)
                    .when().get("/api/admin/unwrapped/posts")
                    .then().statusCode(200)
                    .extract().jsonPath().getList("postId");

            assertEquals(List.of((int) newer.id()), firstPage);
            assertEquals(List.of((int) older.id()), secondPage);
            assertEquals(List.of(), emptyPage);
        } finally {
            deletePost(newer.id());
            deletePost(older.id());
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void adminCanTriggerTheNormalIdempotentMilestonePathWithoutAnotherVote() throws Exception {
        TestPost post = createPost();
        try {
            insertVotes(post, 100);
            String path = "/api/admin/unwrapped/posts/" + post.id() + "/generate";

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
    void adminTriggerExplicitlyRequeuesTheFailedCurrentMilestone() throws Exception {
        TestPost post = createPost();
        try {
            insertVotes(post, 100);
            milestoneService.markForReconciliation(post.id());
            reconciliationWorker.reconcileOne();
            failJob(post.id());

            given()
                    .when().post("/api/admin/unwrapped/posts/" + post.id() + "/generate")
                    .then()
                    .statusCode(202)
                    .body("status", equalTo("RECONCILIATION_QUEUED"));

            assertEquals(0, reconciliationCount(post.id()));
            assertEquals(1, jobCount(post.id()));
            assertEquals("PENDING", jobState(post.id()).status());
        } finally {
            deletePost(post.id());
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void manualTriggerQueuesOnlyTheHighestMilestoneAlreadyReached() throws Exception {
        TestPost post = createPost();
        try {
            insertVotes(post, 550);

            given().when().post("/api/admin/unwrapped/posts/" + post.id() + "/generate")
                    .then().statusCode(202);
            reconciliationWorker.reconcileOne();

            assertEquals(1, jobCount(post.id()));
            JobState job = jobState(post.id());
            assertEquals(500, job.milestone());
            assertEquals("PENDING", job.status());
        } finally {
            deletePost(post.id());
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void generationMonitorShowsQueuedWorkAndUnavailableWorkerWithoutExposingConfiguration() throws Exception {
        TestPost post = createPost();
        try {
            insertVotes(post, 100);
            given().when().post("/api/admin/unwrapped/posts/" + post.id() + "/generate")
                    .then().statusCode(202);

            Map<String, Object> monitor = given()
                    .when().get("/api/admin/unwrapped/generation-status")
                    .then().statusCode(200)
                    .extract().jsonPath().getMap("$");

            assertEquals(false, monitor.get("workerAvailable"));
            assertEquals(Set.of("workerAvailable", "refreshedAt", "statuses"), monitor.keySet());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> statuses = (List<Map<String, Object>>) monitor.get("statuses");
            Map<String, Object> status = statuses.stream()
                    .filter(candidate -> ((Number) candidate.get("postId")).longValue() == post.id())
                    .findFirst().orElseThrow();
            assertEquals("QUEUED", status.get("state"));
            assertEquals(1, ((Number) status.get("queuedJobs")).intValue());
            assertEquals(null, status.get("errorMessage"));
            assertFalse(monitor.toString().contains("api-key"));
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
                    .when().post("/api/admin/unwrapped/posts/" + post.id() + "/generate")
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
    void unknownPostDoesNotCreateAJob() throws Exception {
        given()
                .when().post("/api/admin/unwrapped/posts/999999/generate")
                .then()
                .statusCode(404)
                .body("code", equalTo("UNWRAPPED_POST_NOT_FOUND"));
        assertEquals(0, reconciliationCount(999999));
        assertEquals(0, jobCount(999999));
    }

    @Test
    @TestSecurity(user = "reader@yoursay.com", roles = "user")
    void nonAdminCannotTriggerGeneration() {
        given()
                .when().post("/api/admin/unwrapped/posts/1/generate")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "reader@yoursay.com", roles = "user")
    void nonAdminCannotReadThePostAnalysisDesk() {
        given()
                .when().get("/api/admin/unwrapped/posts")
                .then()
                .statusCode(403);
        given()
                .when().get("/api/admin/unwrapped/generation-status")
                .then()
                .statusCode(403);
        given()
                .when().get("/api/admin/unwrapped/benchmark/system-prompt")
                .then()
                .statusCode(403);
        given()
                .contentType("application/json")
                .body(Map.of("systemPrompts", List.of("A", "B", "C")))
                .when().post("/api/admin/unwrapped/posts/1/benchmark")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void benchmarkContractExposesTheDefaultPromptAndAcceptsUpToThreePrompts() {
        given()
                .when().get("/api/admin/unwrapped/benchmark/system-prompt")
                .then()
                .statusCode(200)
                .body("systemPrompt", equalTo(UnwrappedSystemPrompt.DEFAULT));

        given()
                .contentType("application/json")
                .body(Map.of("systemPrompts", List.of()))
                .when().post("/api/admin/unwrapped/posts/1/benchmark")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"));

        given()
                .contentType("application/json")
                .body(Map.of("systemPrompts", List.of("Prompt A", "Prompt B", "Prompt C", "Prompt D")))
                .when().post("/api/admin/unwrapped/posts/1/benchmark")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"));

        given()
                .contentType("application/json")
                .body(Map.of("systemPrompts", List.of("Prompt A", "Prompt B", "Prompt C")))
                .when().post("/api/admin/unwrapped/posts/999999/benchmark")
                .then()
                .statusCode(404)
                .body("code", equalTo("UNWRAPPED_POST_NOT_FOUND"));

        given()
                .contentType("application/json")
                .body("{}")
                .when().post("/api/admin/unwrapped/posts/1/benchmark")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"));

        given()
                .contentType("application/json")
                .body("{\"systemPrompts\":null}")
                .when().post("/api/admin/unwrapped/posts/1/benchmark")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"));

        given()
                .contentType("application/json")
                .when().post("/api/admin/unwrapped/posts/1/benchmark")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"));

        given()
                .contentType("application/json")
                .body(Map.of("systemPrompts", List.of("   ")))
                .when().post("/api/admin/unwrapped/posts/1/benchmark")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"));

        String maximumPrompt = "x".repeat(20_000);
        given()
                .contentType("application/json")
                .body(Map.of("systemPrompts", List.of(maximumPrompt)))
                .when().post("/api/admin/unwrapped/posts/999999/benchmark")
                .then()
                .statusCode(404)
                .body("code", equalTo("UNWRAPPED_POST_NOT_FOUND"));

        given()
                .contentType("application/json")
                .body(Map.of("systemPrompts", List.of(maximumPrompt + "x")))
                .when().post("/api/admin/unwrapped/posts/999999/benchmark")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"));
    }

    private TestPost createPost() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement post = connection.prepareStatement("""
                     insert into post(
                         user_id, summary, support_question, is_unbiased,
                         created_at, updated_at, voting_type, jurisdiction, case_for, case_against
                     ) values (1, 'Forced generation summary', 'Should this be generated?',
                         false, now(), now(), 'BINARY', 'GLOBAL',
                         'A clear case for the proposal.', 'A clear case against the proposal.')
                     returning id
                     """)) {
            long postId;
            try (ResultSet result = post.executeQuery()) {
                result.next();
                postId = result.getLong(1);
            }
            long agreeOptionId;
            long disagreeOptionId;
            try (PreparedStatement options = connection.prepareStatement("""
                    insert into post_vote_option(post_id, label, ordinal, semantic_key)
                    values (?, 'Agree', 0, 'AGREE'), (?, 'Disagree', 1, 'DISAGREE')
                    returning id
                    """)) {
                options.setLong(1, postId);
                options.setLong(2, postId);
                try (ResultSet result = options.executeQuery()) {
                    result.next();
                    agreeOptionId = result.getLong(1);
                    result.next();
                    disagreeOptionId = result.getLong(1);
                }
            }
            return new TestPost(postId, agreeOptionId, disagreeOptionId);
        }
    }

    private void insertVotes(TestPost post, int count) throws Exception {
        insertVotes(post.id(), post.agreeOptionId(), count, 0);
    }

    private void insertVotes(long postId, long optionId, int count, int userOffset) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into votes(post_id, user_id, option_id, characteristic_snapshot)
                     select ?, 900000 + ? + generated.user_number, ?, '{}'::jsonb
                     from generate_series(1, ?) as generated(user_number)
                     """)) {
            statement.setLong(1, postId);
            statement.setInt(2, userOffset);
            statement.setLong(3, optionId);
            statement.setInt(4, count);
            assertEquals(count, statement.executeUpdate());
        }
    }

    private void setCreatedAt(long postId, String instant) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "update post set created_at = ?::timestamptz where id = ?")) {
            statement.setString(1, instant);
            statement.setLong(2, postId);
            assertEquals(1, statement.executeUpdate());
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

    private void failJob(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     update unwrapped_analysis_job
                     set status = 'FAILED', completed_at = now(),
                         error_code = 'UNWRAPPED_PAGE_UNSOURCED',
                         error_message = 'No sourced claims were returned.'
                     where post_id = ?
                     """)) {
            statement.setLong(1, postId);
            assertEquals(1, statement.executeUpdate());
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

    private record TestPost(long id, long agreeOptionId, long disagreeOptionId) {
    }

    private record JobState(UUID id, int milestone, String status) {
    }
}
