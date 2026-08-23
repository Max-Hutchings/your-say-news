package com.yoursay.autopost;

import com.yoursay.autopost.agent.DiscoveredStory;
import com.yoursay.autopost.agent.DiscoveredStorySource;
import com.yoursay.autopost.agent.AutoPostDiscoveryException;
import com.yoursay.autopost.agent.StoryDiscoveryAgent;
import com.yoursay.autopost.agent.StoryDiscoveryResult;
import com.yoursay.autopost.service.AutoPostWorker;
import com.yoursay.observability.DomainMetrics;
import com.yoursay.posts.postagent.AutoPostAgentService;
import com.yoursay.posts.postagent.PepperDraftStatus;
import com.yoursay.posts.postagent.dto.AgentPublicationDto;
import com.yoursay.posts.postagent.dto.AgentSourceDto;
import com.yoursay.posts.postagent.dto.PepperDraftDto;
import com.yoursay.posts.postagent.dto.PepperPostDraftDto;
import com.yoursay.posts.PostService;
import com.yoursay.posts.VotingType;
import com.yoursay.posts.dto.CreatePostRequest;
import com.yoursay.posts.dto.PostCreationProvenance;
import com.yoursay.posts.dto.PostDto;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.quarkus.test.security.TestSecurity;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestSecurity(user = "admin@yoursay.com", roles = "admin")
class AutoPostControllerTest {

    private static final UUID POST_AGENT_JOB_ID = UUID.fromString("495e2d43-08a4-4b20-a219-c2337174ab8f");

    @InjectMock
    StoryDiscoveryAgent discoveryAgent;

    @InjectMock
    AutoPostAgentService postAgentService;

    @InjectMock
    PostService postService;

    @Inject
    AutoPostWorker worker;

    @Inject
    AgroalDataSource dataSource;

    @InjectSpy
    DomainMetrics metrics;

    @BeforeEach
    void cleanRuns() throws Exception {
        Mockito.reset(discoveryAgent, postAgentService, postService);
        Mockito.clearInvocations(metrics);
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("delete from auto_post_run")) {
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "delete from pepper_ai_draft_post where id = ?")) {
                statement.setObject(1, POST_AGENT_JOB_ID);
                statement.executeUpdate();
            }
        }
    }

    @Test
    void discoveryPersistsTheExactWindowAndTenRankedCandidatesWithSources() throws Exception {
        Mockito.when(discoveryAgent.discover(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> discoveryResult(invocation.getArgument(0), invocation.getArgument(1)));

        String runId = startRun();
        worker.processNext();

        Map<String, Object> run = given().when().get("/api/admin/auto-post/runs/" + runId)
                .then().statusCode(200)
                .body("status", equalTo("CANDIDATES_READY"))
                .body("candidates", hasSize(10))
                .body("candidates[0].rank", equalTo(1))
                .body("candidates[0].region", equalTo("UK"))
                .body("candidates[0].headline", equalTo("Chancellor sets out revised budget rules"))
                .body("candidates[0].sources[0].publisher", equalTo("BBC News"))
                .extract().jsonPath().getMap("$");

        Instant windowStart = Instant.parse((String) run.get("windowStart"));
        Instant windowEnd = Instant.parse((String) run.get("windowEnd"));
        assertEquals(86_400L, windowEnd.getEpochSecond() - windowStart.getEpochSecond());
        assertEquals(10, count("auto_post_candidate"));
        assertEquals(10, count("auto_post_candidate_source"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) run.get("candidates");
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                candidates.stream().map(candidate -> (Integer) candidate.get("rank")).toList());
        assertFalse(run.containsKey("triggeredByAdminId"));
        assertFalse(run.toString().contains("admin@yoursay.com"));

        given().accept("text/event-stream")
                .when().get("/api/admin/auto-post/runs/" + runId + "/events")
                .then().statusCode(200)
                .contentType(startsWith("text/event-stream"))
                .body(org.hamcrest.Matchers.containsString("CANDIDATES_READY"));
        Mockito.verify(metrics).recordOperation(
                Mockito.eq("autopost"), Mockito.eq("sseEvent"), Mockito.eq("success"),
                Mockito.eq("none"), Mockito.eq("none"), Mockito.anyLong());
    }

    @Test
    void confirmedSelectionHandsOneBoundedBriefToPostAgentAsTheSeededOfficialAccount() throws Exception {
        Mockito.when(discoveryAgent.discover(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> discoveryResult(invocation.getArgument(0), invocation.getArgument(1)));
        Mockito.when(postAgentService.startForPublisher(Mockito.anyLong(), Mockito.anyString()))
                .thenReturn(POST_AGENT_JOB_ID);
        String runId = startRun();
        worker.processNext();
        String candidateId = given().when().get("/api/admin/auto-post/runs/" + runId)
                .then().statusCode(200).extract().path("candidates[0].id");
        long officialUserId = officialAccountId();
        insertPepperDraft(POST_AGENT_JOB_ID, officialUserId);

        given().when().post("/api/admin/auto-post/runs/" + runId + "/candidates/" + candidateId + "/select")
                .then().statusCode(202)
                .body("status", equalTo("DRAFTING"))
                .body("selectedCandidateId", equalTo(candidateId))
                .body("pepperDraftId", equalTo(POST_AGENT_JOB_ID.toString()))
                .body("draft", equalTo(null));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        Mockito.verify(postAgentService).startForPublisher(Mockito.eq(officialUserId), prompt.capture());
        assertTrue(prompt.getValue().contains("Chancellor sets out revised budget rules"));
        assertTrue(prompt.getValue().contains("Primary region: UK"));
        assertTrue(prompt.getValue().contains("A factual summary of the material development in story 1."));
        assertTrue(prompt.getValue().contains("https://www.bbc.com/news/uk-budget"));
        assertTrue(prompt.getValue().length() <= 2_000);

        given().when().post("/api/admin/auto-post/runs/" + runId + "/candidates/" + candidateId + "/select")
                .then().statusCode(202)
                .body("pepperDraftId", equalTo(POST_AGENT_JOB_ID.toString()));
        Mockito.verify(postAgentService, Mockito.times(1))
                .startForPublisher(Mockito.eq(officialUserId), Mockito.anyString());
    }

    @Test
    @TestSecurity(user = "john.doe@example.com", roles = "admin")
    void identityProviderRoleCannotBypassDatabaseAdminAuthority() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        given().when().post("/api/admin/auto-post/runs")
                .then().statusCode(403)
                .body("code", equalTo("ADMIN_ACCESS_REQUIRED"));
        given().when().get("/api/admin/auto-post/runs")
                .then().statusCode(403)
                .body("code", equalTo("ADMIN_ACCESS_REQUIRED"));
        given().when().get("/api/admin/auto-post/runs/" + runId)
                .then().statusCode(403)
                .body("code", equalTo("ADMIN_ACCESS_REQUIRED"));
        given().accept("text/event-stream").when().get("/api/admin/auto-post/runs/" + runId + "/events")
                .then().statusCode(403);
        given().when().post("/api/admin/auto-post/runs/" + runId + "/candidates/" + candidateId + "/select")
                .then().statusCode(403)
                .body("code", equalTo("ADMIN_ACCESS_REQUIRED"));
        given().when().post("/api/admin/auto-post/runs/" + runId + "/approve")
                .then().statusCode(403)
                .body("code", equalTo("ADMIN_ACCESS_REQUIRED"));

        assertEquals(0, count("auto_post_run"));
    }

    @Test
    @TestSecurity(user = "jane.smith@example.com", roles = "user")
    void nonAdminRoleCannotReadAutoPostHistory() {
        given().when().get("/api/admin/auto-post/runs")
                .then().statusCode(403);
    }

    @Test
    void providerOutputWithBlankRequiredFieldFailsTheRunWithoutPersistingCandidates() throws Exception {
        Mockito.when(discoveryAgent.discover(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    StoryDiscoveryResult valid = discoveryResult(invocation.getArgument(0), invocation.getArgument(1));
                    DiscoveredStory first = valid.stories().getFirst();
                    DiscoveredStory invalid = new DiscoveredStory(first.rank(), first.region(), " ",
                            first.summary(), first.deduplicationKey(), first.publishedAt(), first.sources());
                    return new StoryDiscoveryResult(List.of(invalid), valid.model(),
                            valid.providerResponseId(), valid.providerCitations());
                });

        String runId = startRun();
        worker.processNext();

        given().when().get("/api/admin/auto-post/runs/" + runId)
                .then().statusCode(200)
                .body("status", equalTo("FAILED"))
                .body("errorCode", equalTo("AUTO_POST_INVALID_PROVIDER_OUTPUT"))
                .body("candidates", hasSize(0));
        assertEquals(0, count("auto_post_candidate"));
    }

    @Test
    void providerFailurePresentedAsAStoryFailsTheRunWithoutBecomingSelectable() throws Exception {
        Mockito.when(discoveryAgent.discover(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    Instant windowEnd = invocation.getArgument(1);
                    DiscoveredStory failurePresentedAsStory = new DiscoveredStory(
                            1,
                            AutoPostRegion.GLOBAL,
                            "No qualified stories in supplied window",
                            "Live search could not be completed with verifiable primary sources.",
                            "no-qualified-stories",
                            windowEnd,
                            List.of(new DiscoveredStorySource(
                                    "https://www.bbc.com/news", "BBC News", "BBC")));
                    return new StoryDiscoveryResult(
                            List.of(failurePresentedAsStory),
                            "grok-4.5",
                            "provider-failure-response",
                            List.of());
                });

        String runId = startRun();
        worker.processNext();

        given().when().get("/api/admin/auto-post/runs/" + runId)
                .then().statusCode(200)
                .body("status", equalTo("FAILED"))
                .body("errorCode", equalTo("AUTO_POST_INVALID_PROVIDER_OUTPUT"))
                .body("candidates", hasSize(0));
        assertEquals(0, count("auto_post_candidate"));
    }

    @Test
    void retryableProviderFailureStopsAfterTheFirstAttempt() throws Exception {
        Mockito.when(discoveryAgent.discover(Mockito.any(), Mockito.any()))
                .thenThrow(new AutoPostDiscoveryException(
                        "AUTO_POST_PROVIDER_UNAVAILABLE", "Temporary provider outage", true));
        String runId = startRun();

        worker.processNext();
        assertRunState(runId, "FAILED", 1);

        Mockito.verify(discoveryAgent).discover(Mockito.any(), Mockito.any());
    }

    @Test
    void completedPepperDraftReturnsForReviewAndExplicitApprovalPublishesItOnce() throws Exception {
        Mockito.when(discoveryAgent.discover(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> discoveryResult(invocation.getArgument(0), invocation.getArgument(1)));
        Mockito.when(postAgentService.startForPublisher(Mockito.anyLong(), Mockito.anyString()))
                .thenReturn(POST_AGENT_JOB_ID);
        String runId = startRun();
        worker.processNext();
        String candidateId = given().when().get("/api/admin/auto-post/runs/" + runId)
                .then().statusCode(200).extract().path("candidates[0].id");
        long officialUserId = officialAccountId();
        insertPepperDraft(POST_AGENT_JOB_ID, officialUserId);
        given().when().post("/api/admin/auto-post/runs/" + runId + "/candidates/" + candidateId + "/select")
                .then().statusCode(202);

        PepperPostDraftDto content = new PepperPostDraftDto(
                "A balanced publication-ready article.", "Do you support the revised budget rules?",
                "Supporters cite fiscal stability.", "Opponents cite reduced flexibility.",
                VotingType.BINARY, List.of("Agree", "Disagree"),
                List.of(new AgentSourceDto(
                        "https://www.bbc.com/news/uk-budget", "Budget rules", "BBC News")));
        PepperDraftDto draft = new PepperDraftDto(
                POST_AGENT_JOB_ID, "prompt", "local", PepperDraftStatus.FINISHED, true,
                content, null, null, 1);
        Mockito.when(postAgentService.getForPublisher(POST_AGENT_JOB_ID, officialUserId))
                .thenReturn(Optional.of(draft));
        Mockito.when(postAgentService.preparePublicationForPublisher(
                        POST_AGENT_JOB_ID, officialUserId, content.citations()))
                .thenReturn(new AgentPublicationDto(POST_AGENT_JOB_ID, content.citations()));
        long publishedPostId = insertPublishedPost(officialUserId);
        Mockito.when(postService.createForPublisher(
                        Mockito.eq(officialUserId), Mockito.any(), Mockito.any()))
                .thenReturn(Uni.createFrom().item(new PostDto(
                        publishedPostId, officialUserId, content.summary(), content.supportQuestion(),
                        content.caseFor(), content.caseAgainst(), "GB", VotingType.BINARY,
                        List.of(), true, Instant.now(), List.of(), List.of(), List.of())));

        given().when().get("/api/admin/auto-post/runs/" + runId)
                .then().statusCode(200)
                .body("status", equalTo("DRAFT_READY"))
                .body("draft.summary", equalTo(content.summary()))
                .body("draft.supportQuestion", equalTo(content.supportQuestion()))
                .body("draft.voteOptions", equalTo(content.voteOptions()));

        given().when().post("/api/admin/auto-post/runs/" + runId + "/approve")
                .then().statusCode(200)
                .body("status", equalTo("PUBLISHED"))
                .body("publishedPostId", equalTo(Math.toIntExact(publishedPostId)));

        ArgumentCaptor<CreatePostRequest> request = ArgumentCaptor.forClass(CreatePostRequest.class);
        ArgumentCaptor<PostCreationProvenance> provenance =
                ArgumentCaptor.forClass(PostCreationProvenance.class);
        Mockito.verify(postService).createForPublisher(
                Mockito.eq(officialUserId), request.capture(), provenance.capture());
        assertEquals("GB", request.getValue().jurisdiction());
        assertEquals(POST_AGENT_JOB_ID, request.getValue().pepperDraftId());
        assertEquals(List.of("Agree", "Disagree"), request.getValue().voteOptions().stream()
                .map(CreatePostRequest.VoteOption::label).toList());
        assertEquals(POST_AGENT_JOB_ID, provenance.getValue().pepperDraftId());
        Mockito.verify(postAgentService).markPublished(POST_AGENT_JOB_ID, publishedPostId);

        given().when().post("/api/admin/auto-post/runs/" + runId + "/approve")
                .then().statusCode(200)
                .body("publishedPostId", equalTo(Math.toIntExact(publishedPostId)));
        Mockito.verify(postService, Mockito.times(1)).createForPublisher(
                Mockito.eq(officialUserId), Mockito.any(), Mockito.any());
    }

    @Test
    void postPersistenceFailureRestoresTheDraftAndRecordsTheExactStage() throws Exception {
        Mockito.when(discoveryAgent.discover(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> discoveryResult(
                        invocation.getArgument(0), invocation.getArgument(1)));
        Mockito.when(postAgentService.startForPublisher(Mockito.anyLong(), Mockito.anyString()))
                .thenReturn(POST_AGENT_JOB_ID);
        String runId = startRun();
        worker.processNext();
        String candidateId = given().when().get("/api/admin/auto-post/runs/" + runId)
                .then().statusCode(200).extract().path("candidates[0].id");
        long officialUserId = officialAccountId();
        insertPepperDraft(POST_AGENT_JOB_ID, officialUserId);
        given().when().post("/api/admin/auto-post/runs/" + runId
                        + "/candidates/" + candidateId + "/select")
                .then().statusCode(202);

        PepperPostDraftDto content = new PepperPostDraftDto(
                "A balanced publication-ready article.",
                "Do you support the revised budget rules?",
                "Supporters cite fiscal stability.",
                "Opponents cite reduced flexibility.",
                VotingType.BINARY,
                List.of("Agree", "Disagree"),
                List.of(new AgentSourceDto(
                        "https://www.bbc.com/news/uk-budget", "Budget rules", "BBC News")));
        PepperDraftDto draft = new PepperDraftDto(
                POST_AGENT_JOB_ID, "prompt", "local", PepperDraftStatus.FINISHED, true,
                content, null, null, 1);
        Mockito.when(postAgentService.getForPublisher(POST_AGENT_JOB_ID, officialUserId))
                .thenReturn(Optional.of(draft));
        Mockito.when(postAgentService.preparePublicationForPublisher(
                        POST_AGENT_JOB_ID, officialUserId, content.citations()))
                .thenReturn(new AgentPublicationDto(POST_AGENT_JOB_ID, content.citations()));
        Mockito.when(postService.createForPublisher(
                        Mockito.eq(officialUserId), Mockito.any(), Mockito.any()))
                .thenReturn(Uni.createFrom().failure(
                        new IllegalStateException("Representative post persistence failure")));

        given().when().get("/api/admin/auto-post/runs/" + runId)
                .then().statusCode(200)
                .body("status", equalTo("DRAFT_READY"));
        given().when().post("/api/admin/auto-post/runs/" + runId + "/approve")
                .then().statusCode(502)
                .body("code", equalTo("AUTO_POST_PUBLICATION_FAILED"));
        given().when().get("/api/admin/auto-post/runs/" + runId)
                .then().statusCode(200)
                .body("status", equalTo("DRAFT_READY"))
                .body("errorCode", equalTo("AUTO_POST_PUBLICATION_FAILED"));

        Mockito.verify(metrics).recordOperation(
                Mockito.eq("autopost"),
                Mockito.eq("postPersistence"),
                Mockito.eq("fault"),
                Mockito.eq("downstream_domain"),
                Mockito.eq("AUTO_POST_POST_PERSISTENCE_FAILED"),
                Mockito.anyLong());
        Mockito.verify(postAgentService, Mockito.never())
                .markPublished(Mockito.any(), Mockito.anyLong());
    }

    @Test
    void seedContainsDedicatedActiveOfficialYourSayNewsAccount() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select display_name, handle, active, account_type, publisher_status
                     from your_say_user where email = 'official@yoursay.com'
                     """); ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertEquals("Your Say News", result.getString("display_name"));
            assertEquals("yoursay", result.getString("handle"));
            assertTrue(result.getBoolean("active"));
            assertEquals("OFFICIAL", result.getString("account_type"));
            assertEquals("ACTIVE", result.getString("publisher_status"));
            assertFalse(result.next());
        }
    }

    private String startRun() {
        return given().when().post("/api/admin/auto-post/runs")
                .then().statusCode(202)
                .body("status", equalTo("QUEUED"))
                .body("candidates", hasSize(0))
                .extract().path("id");
    }

    private StoryDiscoveryResult discoveryResult(Instant windowStart, Instant windowEnd) {
        List<AutoPostRegion> regions = List.of(
                AutoPostRegion.UK, AutoPostRegion.US, AutoPostRegion.GLOBAL,
                AutoPostRegion.UK, AutoPostRegion.US, AutoPostRegion.GLOBAL,
                AutoPostRegion.UK, AutoPostRegion.US, AutoPostRegion.GLOBAL, AutoPostRegion.GLOBAL);
        List<String> headlines = List.of(
                "Chancellor sets out revised budget rules",
                "Senate advances national housing package",
                "G20 agrees cross-border climate finance plan",
                "NHS publishes elective care recovery figures",
                "Supreme Court hears digital privacy challenge",
                "UN reports expansion of emergency food programme",
                "Rail regulator publishes punctuality review",
                "Federal Reserve keeps rates unchanged",
                "WHO updates international outbreak response",
                "Major economies sign shipping emissions accord");
        List<DiscoveredStory> stories = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(rank -> {
                    String headline = headlines.get(rank - 1);
                    String url = rank == 1
                            ? "https://www.bbc.com/news/uk-budget"
                            : "https://news.example.com/story-" + rank;
                    String publisher = rank == 1 ? "BBC News" : "Representative News";
                    return new DiscoveredStory(rank, regions.get(rank - 1), headline,
                            "A factual summary of the material development in story " + rank + ".",
                            "current-story-" + rank, windowEnd.minusSeconds(rank * 900L),
                            List.of(new DiscoveredStorySource(url, headline + " source", publisher)));
                }).toList();
        return new StoryDiscoveryResult(stories, "grok-4.5", "response-autopost-1",
                stories.stream().flatMap(story -> story.sources().stream())
                        .map(DiscoveredStorySource::url).toList());
    }

    private long officialAccountId() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select id from your_say_user where handle = 'yoursay'");
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private long insertPublishedPost(long userId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into post (user_id, summary, support_question, is_ai_generated,
                                       jurisdiction, voting_type, created_at, updated_at)
                     values (?, 'Published auto-post test fixture', 'Should this be supported?',
                             true, 'GB', 'BINARY', now(), now())
                     returning id
                     """)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private void insertPepperDraft(UUID jobId, long userId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into pepper_ai_draft_post
                         (id, user_id, prompt, replica_id, status, version, created_at, updated_at)
                     values (?, ?, 'Auto-post controller test', 'local', 'RECEIVED', 0, now(), now())
                     """)) {
            statement.setObject(1, jobId);
            statement.setLong(2, userId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private long count(String table) throws Exception {
        if (!List.of("auto_post_run", "auto_post_candidate", "auto_post_candidate_source").contains(table)) {
            throw new IllegalArgumentException("Unexpected table");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("select count(*) from " + table);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private void assertRunState(String runId, String status, int attemptCount) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select status, attempt_count from auto_post_run where id = ?")) {
            statement.setObject(1, UUID.fromString(runId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(status, result.getString("status"));
                assertEquals(attemptCount, result.getInt("attempt_count"));
            }
        }
    }

}
