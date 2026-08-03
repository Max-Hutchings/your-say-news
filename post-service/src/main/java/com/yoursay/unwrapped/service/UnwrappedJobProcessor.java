package com.yoursay.unwrapped.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.agent.UnwrappedResearchResult;
import com.yoursay.unwrapped.model.UnwrappedAnalysisJob;
import com.yoursay.unwrapped.model.UnwrappedAnalysisJobRepository;
import com.yoursay.unwrapped.model.UnwrappedJobStatus;
import com.yoursay.unwrapped.model.UnwrappedStory;
import com.yoursay.unwrapped.model.UnwrappedStoryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UnwrappedJobProcessor {
    @Inject
    UnwrappedAnalysisJobRepository jobs;
    @Inject
    UnwrappedStoryRepository stories;
    @Inject
    ObjectMapper objectMapper;
    @Inject
    EntityManager entityManager;
    @ConfigProperty(name = "unwrapped.jobs.retry-enabled", defaultValue = "false")
    boolean retryEnabled;

    @Transactional
    public Optional<JobWork> claimNext() {
        Optional<UnwrappedAnalysisJob> next = jobs.nextForUpdate();
        next.ifPresent(UnwrappedAnalysisJob::claim);
        return next.map(job -> new JobWork(job.getId(), job.getPostId(),
                job.getMilestone(), job.getAnalysisVersion(), job.getAttemptCount()));
    }

    @Transactional
    public void attachAggregate(UUID id, long count, String version, Object aggregate) {
        UnwrappedAnalysisJob job = jobs.findById(id);
        job.attachAggregate(count, version, objectMapper.valueToTree(aggregate));
    }

    @Transactional
    public void complete(UUID id, UnwrappedResearchResult result) {
        UnwrappedAnalysisJob job = jobs.findById(id);
        UnwrappedResearchDraftV1 draft = result.draft();
        UnwrappedStory story =
                new UnwrappedStory(job, objectMapper.valueToTree(draft), result.model());
        stories.persist(story);
        draft.sources().forEach(source -> entityManager.createNativeQuery("""
                insert into unwrapped_source(
                    story_id, citation_id, url, publisher, title, classification, accessed_at
                ) values (?1, ?2, ?3, ?4, ?5, ?6, now())
                """)
                .setParameter(1, story.getId())
                .setParameter(2, source.id())
                .setParameter(3, source.url())
                .setParameter(4, source.publisher())
                .setParameter(5, source.title())
                .setParameter(6, source.classification().name())
                .executeUpdate());
        job.complete(result.model(), result.providerResponseId());
    }

    @Transactional
    public FailureResult fail(UUID id, RuntimeException failure) {
        UnwrappedAnalysisJob job = jobs.findById(id);
        String code = errorCode(failure);
        String message = "UNWRAPPED_INSUFFICIENT_DEMOGRAPHIC_EVIDENCE".equals(code)
                ? "No statistically reliable demographic pattern is available for every option."
                : "Pepper could not build this story.";
        job.fail(code, message, retryEnabled
                && !"UNWRAPPED_INSUFFICIENT_DEMOGRAPHIC_EVIDENCE".equals(code));
        return new FailureResult(code, job.getStatus().name(),
                job.getStatus() == UnwrappedJobStatus.PENDING);
    }

    private static String errorCode(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || !message.startsWith("UNWRAPPED_")) {
            return "UNWRAPPED_GENERATION_FAILED";
        }
        int separator = message.indexOf(':');
        return separator < 0 ? message : message.substring(0, separator);
    }

    public record FailureResult(String code, String status, boolean retryScheduled) {
    }

    public record JobWork(
            UUID id,
            Long postId,
            Integer milestone,
            String analysisVersion,
            int attempt
    ) {
    }
}
