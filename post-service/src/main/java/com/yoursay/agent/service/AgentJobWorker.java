package com.yoursay.agent.service;

import com.yoursay.agent.generator.GenerationException;
import com.yoursay.agent.generator.GenerationResult;
import com.yoursay.agent.generator.UnbiasedPostGenerator;
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
            Log.infof("Agent job completed: jobId=%s model=%s durationMs=%d",
                    job.id(), result.model(), elapsedMillis(started));
        } catch (GenerationException e) {
            processor.fail(job.id(), job.attempt(), e);
            Log.warnf("Agent job failed: jobId=%s code=%s retryable=%s durationMs=%d",
                    job.id(), e.code(), e.retryable(), elapsedMillis(started));
        } catch (RuntimeException e) {
            GenerationException wrapped = new GenerationException(
                    "AGENT_UNEXPECTED_FAILURE", "Unexpected generation failure", true, e);
            processor.fail(job.id(), job.attempt(), wrapped);
            Log.errorf(e, "Unexpected agent job failure: jobId=%s durationMs=%d",
                    job.id(), elapsedMillis(started));
        }
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
