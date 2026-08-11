package com.yoursay.posts.postagent.service;

import com.yoursay.observability.DomainMetrics;
import com.yoursay.posts.postagent.generator.GenerationException;
import com.yoursay.posts.postagent.generator.GenerationResult;
import com.yoursay.posts.postagent.generator.UnbiasedPostGenerator;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
public class AgentJobWorker {

    @Inject
    AgentJobProcessor processor;

    @Inject
    UnbiasedPostGenerator generator;

    @Inject
    DomainMetrics metrics;

    @Scheduled(identity = "unbiased-post-agent-worker",
            every = "${agent.jobs.poll-interval:2s}", concurrentExecution = SKIP)
    @RunOnVirtualThread
    void processNext() {
        Optional<AgentJobProcessor.JobWork> next = processor.claimNext();
        if (next.isEmpty()) {
            return;
        }
        AgentJobProcessor.JobWork job = next.get();
        long started = System.nanoTime();
        try {
            GenerationResult result = generator.generate(job.request());
            processor.complete(job.id(), result);
            recordSuccess(started);
            Log.infof("Agent job completed: jobId=%s model=%s durationMs=%d",
                    job.id(), result.model(), elapsedMillis(started));
        } catch (GenerationException e) {
            processor.fail(job.id(), job.attempt(), e);
            recordFailure(e.code(), started);
            Log.warnf("Agent job failed: jobId=%s code=%s retryable=%s durationMs=%d",
                    job.id(), e.code(), e.retryable(), elapsedMillis(started));
        } catch (RuntimeException e) {
            GenerationException wrapped = new GenerationException(
                    "AGENT_UNEXPECTED_FAILURE", "Unexpected generation failure", true, e);
            processor.fail(job.id(), job.attempt(), wrapped);
            recordFailure(wrapped.code(), started);
            Log.errorf(e, "Unexpected agent job failure: jobId=%s durationMs=%d",
                    job.id(), elapsedMillis(started));
        }
    }

    private void recordSuccess(long started) {
        if (metrics != null) {
            metrics.recordJob("postagent", "generation", true, System.nanoTime() - started);
        }
    }

    /** A failed generation is an error even though no HTTP request ever sees it. */
    private void recordFailure(String errorCode, long started) {
        if (metrics != null) {
            metrics.recordJob("postagent", "generation", false, System.nanoTime() - started);
            metrics.recordError("postagent", "generation", errorCode, 500);
        }
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
