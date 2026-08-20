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
import com.yoursay.autopost.validation.AutoPostCandidateValidator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.yoursay.observability.DomainMetrics;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    @ConfigProperty(name = "autopost.jobs.max-attempts", defaultValue = "3")
    int maxAttempts;

    @Transactional
    public Optional<RunWork> claimNext() {
        return runs.claimable(Instant.now()).map(run -> {
            run.markDiscovering();
            runs.flush();
            return new RunWork(run.getId(), run.getWindowStart(), run.getWindowEnd(), run.getAttemptCount());
        });
    }

    @Transactional
    public void complete(UUID runId, StoryDiscoveryResult result) {
        AutoPostRun run = runs.findForUpdate(runId)
                .orElseThrow(() -> new IllegalStateException("Auto-post run disappeared"));
        long validationStarted = System.nanoTime();
        try {
            validator.validate(result, run.getWindowStart(), run.getWindowEnd());
            metrics.recordOperation("autopost", "candidateValidation", "success", "none", "none",
                    System.nanoTime() - validationStarted);
        } catch (RuntimeException error) {
            String code = error instanceof com.yoursay.autopost.validation.AutoPostValidationException validation
                    ? validation.code() : "AUTO_POST_VALIDATION_FAILURE";
            metrics.recordOperation("autopost", "candidateValidation", "fault",
                    "provider_contract", code, System.nanoTime() - validationStarted);
            throw error;
        }
        for (DiscoveredStory story : result.stories()) {
            AutoPostCandidate candidate = new AutoPostCandidate(runId, story.rank(), story.region(),
                    story.headline(), story.summary(), story.deduplicationKey(), story.publishedAt());
            candidates.persist(candidate);
            int ordinal = 0;
            for (DiscoveredStorySource source : story.sources()) {
                sources.persist(new AutoPostCandidateSource(candidate.getId(), ordinal++,
                        source.url(), source.title(), source.publisher()));
            }
        }
        run.markCandidatesReady(result.model(), result.providerResponseId());
        runs.flush();
    }

    @Transactional
    public void fail(UUID runId, int attempt, AutoPostDiscoveryException error) {
        AutoPostRun run = runs.findForUpdate(runId)
                .orElseThrow(() -> new IllegalStateException("Auto-post run disappeared"));
        if (error.retryable() && attempt < maxAttempts) {
            long delayMinutes = 1L << Math.max(0, attempt - 1);
            run.markRetry(error.code(), Instant.now().plus(delayMinutes, ChronoUnit.MINUTES));
            return;
        }
        run.markFailed(error.code(), publicMessage(error.code()));
    }

    private static String publicMessage(String code) {
        return switch (code) {
            case "AUTO_POST_PROVIDER_NOT_CONFIGURED" -> "Story discovery is not configured.";
            case "AUTO_POST_PROVIDER_RESPONSE_INVALID", "AUTO_POST_INVALID_PROVIDER_OUTPUT" ->
                    "The discovered story list did not pass validation.";
            default -> "Story discovery failed. Try again.";
        };
    }

    public record RunWork(UUID id, Instant windowStart, Instant windowEnd, int attempt) {
    }
}
