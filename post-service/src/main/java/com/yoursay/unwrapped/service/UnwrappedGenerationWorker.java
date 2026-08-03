package com.yoursay.unwrapped.service;

import com.yoursay.unwrapped.agent.UnwrappedResearchGenerator;
import com.yoursay.unwrapped.agent.UnwrappedResearchRequest;
import com.yoursay.unwrapped.agent.UnwrappedResearchResult;
import com.yoursay.unwrapped.selection.InsightSelectionService;
import com.yoursay.unwrapped.selection.UnwrappedAnalysisBriefV1;
import com.yoursay.observability.DomainMetrics;
import com.yoursay.votes.PostAnalysisAggregateService;
import com.yoursay.votes.dto.PostAnalysisAggregateV1;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
public class UnwrappedGenerationWorker {
    private static final String NOT_CONFIGURED = "__not_configured__";

    @Inject
    UnwrappedJobProcessor processor;
    @Inject
    PostAnalysisAggregateService aggregates;
    @Inject
    InsightSelectionService selector;
    @Inject
    UnwrappedResearchGenerator generator;
    @Inject
    DomainMetrics metrics;
    @ConfigProperty(name = "unwrapped.agent.api-key", defaultValue = NOT_CONFIGURED)
    String apiKey;

    @Scheduled(identity = "unwrapped-generation-worker",
            every = "${unwrapped.jobs.poll-interval:2s}", concurrentExecution = SKIP)
    @RunOnVirtualThread
    public void processNext() {
        if (apiKey == null || apiKey.isBlank() || NOT_CONFIGURED.equals(apiKey)) {
            return;
        }
        Optional<UnwrappedJobProcessor.JobWork> claimed = processor.claimNext();
        if (claimed.isEmpty()) return;
        UnwrappedJobProcessor.JobWork job = claimed.get();
        long started = System.nanoTime();
        try {
            UnwrappedResearchRequest request = request(job);
            UnwrappedResearchResult result = generator.generate(request);
            processor.complete(job.id(), result);
            metrics.recordOperation("unwrapped", "generation", true);
            Log.infof("Unwrapped draft ready: jobId=%s postId=%d milestone=%d attempt=%d model=%s durationMs=%d",
                    job.id(), job.postId(), job.milestone(), job.attempt(), result.model(),
                    elapsedMillis(started));
        } catch (RuntimeException failure) {
            UnwrappedJobProcessor.FailureResult result = processor.fail(job.id(), failure);
            metrics.recordOperation("unwrapped", "generation", false);
            metrics.recordError("unwrapped", "generation", result.code(), 500);
            Log.warnf(failure,
                    "Unwrapped generation failed: jobId=%s postId=%d milestone=%d attempt=%d code=%s status=%s retryScheduled=%s causeType=%s causeMessage=%s durationMs=%d",
                    job.id(), job.postId(), job.milestone(), job.attempt(), result.code(),
                    result.status(), result.retryScheduled(), failure.getClass().getSimpleName(),
                    failure.getMessage(), elapsedMillis(started));
        }
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private UnwrappedResearchRequest request(UnwrappedJobProcessor.JobWork job) {
        PostAnalysisAggregateV1 aggregate = aggregates.capture(job.postId());
        processor.attachAggregate(job.id(), aggregate.canonicalVoteCount(),
                aggregate.aggregateVersion(), aggregate);
        UnwrappedAnalysisBriefV1 brief = selector.select(aggregate);
        return new UnwrappedResearchRequest(brief.postId(), brief.summary(), brief.question(),
                brief.jurisdiction(), brief.canonicalVoteCount(), brief.aggregateVersion(),
                brief.options());
    }
}
