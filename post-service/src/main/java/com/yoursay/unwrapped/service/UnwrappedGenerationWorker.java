package com.yoursay.unwrapped.service;

import com.yoursay.platform.ai.AiConfig;
import com.yoursay.unwrapped.agent.UnwrappedResearchGenerator;
import com.yoursay.unwrapped.agent.UnwrappedResearchRequest;
import com.yoursay.unwrapped.agent.UnwrappedResearchResult;
import com.yoursay.platform.observability.DomainMetrics;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
public class UnwrappedGenerationWorker {
    @Inject
    UnwrappedJobProcessor processor;
    @Inject
    UnwrappedResearchPreparation preparation;
    @Inject
    UnwrappedResearchGenerator generator;
    @Inject
    DomainMetrics metrics;
    @Inject
    AiConfig aiConfig;

    @Scheduled(identity = "unwrapped-generation-worker",
            every = "${unwrapped.jobs.poll-interval:2s}", concurrentExecution = SKIP)
    @RunOnVirtualThread
    public void processNext() {
        if (!aiConfig.unwrapped().configured()) {
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
            metrics.recordJob("unwrapped", "generation", true, System.nanoTime() - started);
            Log.infof("Unwrapped draft ready: jobId=%s postId=%d milestone=%d attempt=%d model=%s durationMs=%d",
                    job.id(), job.postId(), job.milestone(), job.attempt(), result.model(),
                    elapsedMillis(started));
        } catch (RuntimeException failure) {
            UnwrappedJobProcessor.FailureResult result = processor.fail(job.id(), failure);
            metrics.recordJob("unwrapped", "generation", false, System.nanoTime() - started);
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
        UnwrappedResearchPreparation.PreparedResearch prepared = preparation.prepare(job.postId());
        var aggregate = prepared.aggregate();
        processor.attachAggregate(job.id(), aggregate.canonicalVoteCount(),
                aggregate.aggregateVersion(), aggregate);
        return prepared.request();
    }
}
