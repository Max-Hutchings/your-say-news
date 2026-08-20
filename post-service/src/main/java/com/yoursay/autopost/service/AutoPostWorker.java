package com.yoursay.autopost.service;

import com.yoursay.autopost.agent.AutoPostDiscoveryException;
import com.yoursay.autopost.agent.StoryDiscoveryAgent;
import com.yoursay.autopost.agent.StoryDiscoveryResult;
import com.yoursay.autopost.validation.AutoPostValidationException;
import com.yoursay.observability.DomainMetrics;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
public class AutoPostWorker {

    @Inject
    AutoPostRunProcessor processor;

    @Inject
    StoryDiscoveryAgent discoveryAgent;

    @Inject
    DomainMetrics metrics;

    @Scheduled(identity = "auto-post-discovery-worker",
            every = "${autopost.jobs.poll-interval:2s}", concurrentExecution = SKIP)
    @RunOnVirtualThread
    public void processNext() {
        Optional<AutoPostRunProcessor.RunWork> next = processor.claimNext();
        if (next.isEmpty()) {
            return;
        }
        AutoPostRunProcessor.RunWork work = next.get();
        long started = System.nanoTime();
        try {
            StoryDiscoveryResult result = discoveryAgent.discover(work.windowStart(), work.windowEnd());
            processor.complete(work.id(), result);
            record("success", "none", "none", started);
            Log.info("Auto-post story discovery completed: domain=autopost operation=discovery outcome=success");
        } catch (AutoPostValidationException error) {
            AutoPostDiscoveryException wrapped = new AutoPostDiscoveryException(
                    "AUTO_POST_INVALID_PROVIDER_OUTPUT", error.getMessage(), false, error);
            processor.fail(work.id(), work.attempt(), wrapped);
            record("fault", "provider_contract", error.code(), started);
            Log.warnf("Auto-post story discovery failed: domain=autopost operation=discovery "
                    + "outcome=fault faultCode=%s", error.code());
        } catch (AutoPostDiscoveryException error) {
            processor.fail(work.id(), work.attempt(), error);
            record("fault", "dependency", error.code(), started);
            Log.warnf("Auto-post story discovery failed: domain=autopost operation=discovery "
                    + "outcome=fault faultCode=%s retryable=%s", error.code(), error.retryable());
        } catch (RuntimeException error) {
            AutoPostDiscoveryException wrapped = new AutoPostDiscoveryException(
                    "AUTO_POST_UNEXPECTED_FAILURE", "Unexpected discovery failure", true, error);
            processor.fail(work.id(), work.attempt(), wrapped);
            record("fault", "application", wrapped.code(), started);
            Log.error("Auto-post story discovery failed unexpectedly: domain=autopost "
                    + "operation=discovery outcome=fault faultCode=AUTO_POST_UNEXPECTED_FAILURE", error);
        }
    }

    private void record(String outcome, String type, String code, long started) {
        metrics.recordOperation("autopost", "discovery", outcome, type, code,
                System.nanoTime() - started);
    }
}
