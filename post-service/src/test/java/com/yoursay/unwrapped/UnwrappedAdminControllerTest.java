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
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                .when().get("/api/admin/unwrapped/posts/2007/benchmark/context")
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
    void benchmarkContractExposesTheDefaultPromptAndAcceptsUpToThreePrompts() throws Exception {
        TestPost post = createPost();
        try {
            insertVotesWithCharacteristic(post.id(), post.agreeOptionId(), 60, 0,
                    "{\"gender\":\"MAN\"}");
            insertVotesWithCharacteristic(post.id(), post.disagreeOptionId(), 40, 10_000,
                    "{\"gender\":\"WOMAN\"}");
            String contextJson = given()
                    .when().get("/api/admin/unwrapped/posts/" + post.id() + "/benchmark/context")
                    .then()
                    .statusCode(200)
                    .body("systemPrompt", equalTo(UnwrappedSystemPrompt.DEFAULT))
                    .body("outputInstructions", equalTo(UnwrappedSystemPrompt.outputInstructions(
                            List.of(post.agreeOptionId(), post.disagreeOptionId()))))
                    .body("input.postId", equalTo((int) post.id()))
                    .body("input.summary", equalTo("Forced generation summary"))
                    .body("input.question", equalTo("Should this be generated?"))
                    .body("input.jurisdiction", equalTo("GLOBAL"))
                    .body("input.canonicalVoteCount", equalTo(100))
                    .body("input.aggregateVersion", matchesPattern("sha256:[0-9a-f]{64}"))
                    .body("input.options.size()", equalTo(2))
                    .body("input.options[0].option.id", equalTo((int) post.agreeOptionId()))
                    .body("input.options[0].option.label", equalTo("Agree"))
                    .body("input.options[0].overallVoteCount", equalTo(60))
                    .body("input.options[0].overallVotePercentage", equalTo(60.0F))
                    .body("input.options[1].option.id", equalTo((int) post.disagreeOptionId()))
                    .body("input.options[1].option.label", equalTo("Disagree"))
                    .body("input.options[1].overallVoteCount", equalTo(40))
                    .body("input.options[1].overallVotePercentage", equalTo(40.0F))
                    // ADR-044: eligibility is privacy-only, so both gender groups reach every
                    // option. The anchor is the largest privacy-safe group, the differentiator the
                    // strongest remaining one, which flips their option affinity between options.
                    .body("input.options[0].candidates.size()", equalTo(2))
                    .body("input.options[0].candidates[0].cohortId", equalTo("gender=MAN"))
                    .body("input.options[0].candidates[0].dimensions[0].axis", equalTo("gender"))
                    .body("input.options[0].candidates[0].dimensions[0].bucket", equalTo("MAN"))
                    .body("input.options[0].candidates[0].role", equalTo("CORE_ANCHOR"))
                    .body("input.options[0].candidates[0].displayName", equalTo("Men"))
                    .body("input.options[0].candidates[0].sampleSize", equalTo(60))
                    .body("input.options[0].candidates[0].optionVoteCount", equalTo(60))
                    .body("input.options[0].candidates[0].propensityPercentage", equalTo(100.0F))
                    .body("input.options[0].candidates[1].cohortId", equalTo("gender=WOMAN"))
                    .body("input.options[0].candidates[1].role", equalTo("CORE_DIFFERENTIATOR"))
                    .body("input.options[0].candidates[1].displayName", equalTo("Women"))
                    .body("input.options[0].candidates[1].sampleSize", equalTo(40))
                    .body("input.options[0].candidates[1].optionVoteCount", equalTo(0))
                    .body("input.options[0].candidates[1].propensityPercentage", equalTo(0.0F))
                    .body("input.options[1].candidates.size()", equalTo(2))
                    .body("input.options[1].candidates[0].cohortId", equalTo("gender=MAN"))
                    .body("input.options[1].candidates[0].role", equalTo("CORE_ANCHOR"))
                    .body("input.options[1].candidates[0].displayName", equalTo("Men"))
                    .body("input.options[1].candidates[0].sampleSize", equalTo(60))
                    .body("input.options[1].candidates[0].optionVoteCount", equalTo(0))
                    .body("input.options[1].candidates[0].propensityPercentage", equalTo(0.0F))
                    .body("input.options[1].candidates[1].cohortId", equalTo("gender=WOMAN"))
                    .body("input.options[1].candidates[1].role", equalTo("CORE_DIFFERENTIATOR"))
                    .body("input.options[1].candidates[1].displayName", equalTo("Women"))
                    .body("input.options[1].candidates[1].sampleSize", equalTo(40))
                    .body("input.options[1].candidates[1].optionVoteCount", equalTo(40))
                    .body("input.options[1].candidates[1].propensityPercentage", equalTo(100.0F))
                    .body("input.options[0].narrativeInstructions[0]", equalTo(
                            "Explain why a selected cohort is likely to favour the option using researched context."))
                    .extract().path("input").toString();
            // Scope the PII guard to the aggregate actually sent to the model. The surrounding
            // prompt prose legitimately mentions these words to forbid them ("Do not identify
            // individual voters using personal names, email addresses, ..."), so asserting over
            // the whole response would fail on the instruction that enforces the rule.
            assertFalse(contextJson.contains("userId"), contextJson);
            assertFalse(contextJson.contains("email"), contextJson);
            assertFalse(contextJson.contains("dateOfBirth"), contextJson);
        } finally {
            deletePost(post.id());
        }

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

    /**
     * The identity of a real voter must never reach the model. The aggregate is built from
     * {@code (optionId, characteristicSnapshot)} pairs and structurally has nowhere to put a name,
     * so this guards the boundary staying that way: a genuine account with a realistic name, email
     * and date of birth casts a counted vote, and none of those literal values may appear in the
     * payload sent to the provider. Asserting the values, not the field names, is the point —
     * "jane.whitfield@example.com" contains no substring "email".
     */
    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void modelInputCarriesNoVoterIdentityEvenWhenTheVoterHasFullProfileData() throws Exception {
        TestPost post = createPost();
        long voterId = 0;
        try {
            insertVotesWithCharacteristic(post.id(), post.agreeOptionId(), 60, 0,
                    "{\"gender\":\"MAN\"}");
            insertVotesWithCharacteristic(post.id(), post.disagreeOptionId(), 40, 10_000,
                    "{\"gender\":\"WOMAN\"}");
            voterId = insertIdentifiableUser();
            insertVoteFor(post.id(), post.agreeOptionId(), voterId, "{\"gender\":\"WOMAN\"}");

            Map<String, Object> input = given()
                    .when().get("/api/admin/unwrapped/posts/" + post.id() + "/benchmark/context")
                    .then()
                    .statusCode(200)
                    // The vote counted, so this voter really is inside the aggregate below.
                    .body("input.canonicalVoteCount", equalTo(101))
                    .extract().path("input");
            String modelInput = input.toString();
            // Positive control. Every other assertion here checks for absence, and absence passes
            // just as happily against an empty map or a mistyped extraction path, so first prove
            // this really is the populated payload.
            assertTrue(modelInput.contains("Forced generation summary"),
                    () -> "Scanned the wrong payload: " + modelInput);

            // The handle is public profile data rather than PII, but tying it to how someone voted
            // is the same disclosure, so it is forbidden here for the same reason.
            for (String identity : List.of(
                    "Jane", "Whitfield", "Jane Whitfield", "jane-whitfield",
                    "jane.whitfield@example.com", "1987-04-23")) {
                assertFalse(modelInput.contains(identity),
                        () -> "Voter identity '" + identity + "' reached the model input: "
                                + modelInput);
            }
            // A bare numeric id cannot be searched for as text — "12" occurs inside hashes, ids and
            // counts — so pin the shape instead: no field anywhere in the payload may carry a
            // per-person identifier, which is what would make a vote traceable to an account.
            Set<String> identityKeys = new java.util.TreeSet<>();
            collectKeys(input, identityKeys);
            // Not displayName — that is the cohort's label ("Men"), and a person's display name is
            // already covered by the value check above.
            identityKeys.removeIf(key -> !key.toLowerCase(java.util.Locale.ROOT)
                    .matches(".*(userid|user_id|voterid|voter_id|accountid|account_id"
                            + "|email|handle|firstname|lastname|dateofbirth|birth).*"));
            assertEquals(Set.of(), identityKeys,
                    () -> "Model input exposes per-person fields: " + identityKeys);
        } finally {
            deletePost(post.id());
            if (voterId != 0) {
                deleteUser(voterId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectKeys(Object node, Set<String> into) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                into.add(String.valueOf(key));
                collectKeys(value, into);
            });
        } else if (node instanceof Iterable<?> items) {
            items.forEach(item -> collectKeys(item, into));
        }
    }

    private long insertIdentifiableUser() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into your_say_user(
                         email, first_name, last_name, display_name, handle, date_of_birth,
                         created_date, active)
                     values ('jane.whitfield@example.com', 'Jane', 'Whitfield', 'Jane Whitfield',
                         'jane-whitfield', date '1987-04-23', now(), true)
                     returning id
                     """);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private void insertVoteFor(long postId, long optionId, long userId, String snapshot)
            throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into votes(post_id, user_id, option_id, characteristic_snapshot)
                     values (?, ?, ?, ?::jsonb)
                     """)) {
            statement.setLong(1, postId);
            statement.setLong(2, userId);
            statement.setLong(3, optionId);
            statement.setString(4, snapshot);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void deleteUser(long userId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "delete from your_say_user where id = ?")) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
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

    private void insertVotesWithCharacteristic(long postId, long optionId, int count,
                                               int userOffset, String snapshot) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into votes(post_id, user_id, option_id, characteristic_snapshot)
                     select ?, 900000 + ? + generated.user_number, ?, ?::jsonb
                     from generate_series(1, ?) as generated(user_number)
                     """)) {
            statement.setLong(1, postId);
            statement.setInt(2, userOffset);
            statement.setLong(3, optionId);
            statement.setString(4, snapshot);
            statement.setInt(5, count);
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
