package com.yoursay.autopost.service;

import com.yoursay.autopost.agent.AutoPostDiscoveryException;
import com.yoursay.autopost.agent.StoryDiscoveryAgent;
import com.yoursay.autopost.agent.StoryDiscoveryResult;
import com.yoursay.autopost.observability.AutoPostLog;
import com.yoursay.autopost.validation.AutoPostValidationException;
import com.yoursay.observability.DomainMetrics;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Executes one claimed discovery run inside its own trace. */
@ApplicationScoped
public class AutoPostDiscoveryJob {

    @Inject
    AutoPostRunProcessor processor;

    @Inject
    StoryDiscoveryAgent discoveryAgent;

    @Inject
    DomainMetrics metrics;

    @WithSpan("autopost.discovery")
    public void executeDiscovery(AutoPostRunProcessor.DiscoveryWork work) {
        long started = System.nanoTime();
        AutoPostLog.started("discovery", "provider_research");
        try {
            StoryDiscoveryResult result =
                    discoveryAgent.discover(work.windowStart(), work.windowEnd());
            processor.completeDiscovery(work.id(), result);
            record("success", "none", "none", started);
            AutoPostLog.succeeded("discovery", "candidate_persistence");
        } catch (AutoPostValidationException error) {
            failValidation(work, error, started);
        } catch (AutoPostDiscoveryException error) {
            failDiscovery(work, error, started);
        } catch (RuntimeException error) {
            failUnexpectedly(work, error, started);
        }
    }

    private void failValidation(
            AutoPostRunProcessor.DiscoveryWork work,
            AutoPostValidationException error,
            long started
    ) {
        AutoPostDiscoveryException failure = new AutoPostDiscoveryException(
                "AUTO_POST_INVALID_PROVIDER_OUTPUT",
                "provider_contract",
                "candidate_validation",
                error.getMessage(),
                false,
                error);
        recordTraceFailure(failure.code(), error);
        processor.failDiscovery(work.id(), failure);
        record("fault", failure.faultType(), error.code(), started);
        AutoPostLog.failed("discovery", failure.stage(), failure.faultType(), error.code(), error);
    }

    private void failDiscovery(
            AutoPostRunProcessor.DiscoveryWork work,
            AutoPostDiscoveryException error,
            long started
    ) {
        recordTraceFailure(error.code(), error);
        processor.failDiscovery(work.id(), error);
        record("fault", error.faultType(), error.code(), started);
        AutoPostLog.failed("discovery", error.stage(), error.faultType(), error.code(), error);
    }

    private void failUnexpectedly(
            AutoPostRunProcessor.DiscoveryWork work,
            RuntimeException error,
            long started
    ) {
        AutoPostDiscoveryException failure = new AutoPostDiscoveryException(
                "AUTO_POST_UNEXPECTED_FAILURE",
                "application",
                "discovery_workflow",
                "Unexpected discovery failure",
                false,
                error);
        recordTraceFailure(failure.code(), error);
        processor.failDiscovery(work.id(), failure);
        record("fault", failure.faultType(), failure.code(), started);
        AutoPostLog.failed("discovery", failure.stage(), failure.faultType(), failure.code(), error);
    }

    private void record(String outcome, String type, String code, long started) {
        metrics.recordOperation("autopost", "discovery", outcome, type, code,
                System.nanoTime() - started);
    }

    private static void recordTraceFailure(String code, RuntimeException error) {
        Span span = Span.current();
        span.recordException(error);
        span.setStatus(StatusCode.ERROR, code);
    }
}
