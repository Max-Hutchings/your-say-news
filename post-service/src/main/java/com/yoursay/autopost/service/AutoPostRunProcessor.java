package com.yoursay.autopost.service;

import com.yoursay.autopost.agent.AutoPostDiscoveryException;
import com.yoursay.autopost.agent.DiscoveredStory;
import com.yoursay.autopost.agent.DiscoveredStorySource;
import com.yoursay.autopost.agent.StoryDiscoveryResult;
import com.yoursay.autopost.model.AutoPostCandidate;
import com.yoursay.autopost.model.AutoPostCandidateRepository;
import com.yoursay.autopost.model.AutoPostCandidateSource;
import com.yoursay.autopost.model.AutoPostCandidateSourceRepository;
import com.yoursay.autopost.model.AutoPostRun;
import com.yoursay.autopost.model.AutoPostRunRepository;
import com.yoursay.autopost.observability.AutoPostLog;
import com.yoursay.autopost.validation.AutoPostCandidateValidator;
import com.yoursay.autopost.validation.AutoPostValidationException;
import com.yoursay.platform.observability.DomainMetrics;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AutoPostRunProcessor {

    @Inject
    AutoPostRunRepository runs;

    @Inject
    AutoPostCandidateRepository candidates;

    @Inject
    AutoPostCandidateSourceRepository sources;

    @Inject
    AutoPostCandidateValidator validator;

    @Inject
    DomainMetrics metrics;

    @Transactional
    public Optional<DiscoveryWork> claimNextDiscovery() {
        return runs.claimable().map(run -> {
            run.markDiscovering();
            runs.flush();
            return new DiscoveryWork(run.getId(), run.getWindowStart(), run.getWindowEnd());
        });
    }

    @Transactional
    public void completeDiscovery(UUID runId, StoryDiscoveryResult result) {
        AutoPostRun run = runs.findForUpdate(runId)
                .orElseThrow(() -> new IllegalStateException("Auto-post run disappeared"));
        validateCandidates(result);
        persistCandidates(run, result);
    }

    private void validateCandidates(StoryDiscoveryResult result) {
        long validationStarted = System.nanoTime();
        AutoPostLog.started("candidateValidation", "candidate_validation");
        try {
            validator.validateRequiredFields(result);
            metrics.recordOperation("autopost", "candidateValidation", "success", "none", "none",
                    System.nanoTime() - validationStarted);
            AutoPostLog.succeeded("candidateValidation", "candidate_validation");
        } catch (AutoPostValidationException error) {
            metrics.recordOperation("autopost", "candidateValidation", "fault",
                    "provider_contract", error.code(), System.nanoTime() - validationStarted);
            AutoPostLog.failed("candidateValidation", "candidate_validation", "provider_contract",
                    error.code(), error);
            throw error;
        }
    }

    private void persistCandidates(AutoPostRun run, StoryDiscoveryResult result) {
        long persistenceStarted = System.nanoTime();
        AutoPostLog.started("candidatePersistence", "candidate_persistence");
        try {
            persistStories(run.getId(), result.stories());
            run.markCandidatesReady(result.model(), result.providerResponseId());
            runs.flush();
            metrics.recordOperation("autopost", "candidatePersistence", "success", "none", "none",
                    System.nanoTime() - persistenceStarted);
            AutoPostLog.succeeded("candidatePersistence", "candidate_persistence");
        } catch (RuntimeException error) {
            metrics.recordOperation("autopost", "candidatePersistence", "fault", "database",
                    "AUTO_POST_CANDIDATE_PERSISTENCE_FAILED",
                    System.nanoTime() - persistenceStarted);
            AutoPostLog.failed("candidatePersistence", "candidate_persistence", "database",
                    "AUTO_POST_CANDIDATE_PERSISTENCE_FAILED", error);
            throw new AutoPostDiscoveryException(
                    "AUTO_POST_CANDIDATE_PERSISTENCE_FAILED",
                    "database",
                    "candidate_persistence",
                    "Discovered stories could not be persisted",
                    false,
                    error);
        }
    }

    private void persistStories(UUID runId, List<DiscoveredStory> stories) {
        for (DiscoveredStory story : stories) {
            AutoPostCandidate candidate = new AutoPostCandidate(runId, story.rank(), story.region(),
                    story.headline(), story.summary(), story.deduplicationKey(), story.publishedAt());
            candidates.persist(candidate);
            persistSources(candidate.getId(), story.sources());
        }
    }

    private void persistSources(UUID candidateId, List<DiscoveredStorySource> storySources) {
        for (int ordinal = 0; ordinal < storySources.size(); ordinal++) {
            DiscoveredStorySource source = storySources.get(ordinal);
            sources.persist(new AutoPostCandidateSource(candidateId, ordinal,
                    source.url(), source.title(), source.publisher()));
        }
    }

    @Transactional
    public void failDiscovery(UUID runId, AutoPostDiscoveryException error) {
        AutoPostRun run = runs.findForUpdate(runId)
                .orElseThrow(() -> new IllegalStateException("Auto-post run disappeared"));
        run.markFailed(error.code(), publicMessage(error.code()));
    }

    private static String publicMessage(String code) {
        return switch (code) {
            case "AUTO_POST_PROVIDER_NOT_CONFIGURED" -> "Story discovery is not configured.";
            case "AUTO_POST_WEB_SEARCH_MISSING", "AUTO_POST_WEB_SEARCH_FAILED",
                    "AUTO_POST_PROVIDER_EVIDENCE_MISSING" ->
                    "The story provider could not complete live research.";
            case "AUTO_POST_PROVIDER_RESPONSE_INVALID", "AUTO_POST_INVALID_PROVIDER_OUTPUT" ->
                    "The discovered story list did not pass validation.";
            default -> "Story discovery failed. Try again.";
        };
    }

    public record DiscoveryWork(UUID id, Instant windowStart, Instant windowEnd) {
    }
}
