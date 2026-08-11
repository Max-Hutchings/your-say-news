package com.yoursay.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.TimeUnit;

/**
 * Custom domain-level telemetry. Every tag here must stay low cardinality: domain, operation and
 * outcome are fixed vocabularies, and no identifier, email or raw error message is ever a tag.
 */
@ApplicationScoped
public class DomainMetrics {

    private static final String SUCCESS = "success";
    private static final String EXPECTED_REJECTION = "expected_rejection";
    private static final String UNEXPECTED_CLIENT_ERROR = "unexpected_client_error";
    private static final String SERVER_ERROR = "server_error";
    private static final String SERVICE_ERROR = "service_error";
    private static final String JOB_ERROR = "job_error";

    @Inject
    MeterRegistry registry;

    @ConfigProperty(name = "quarkus.profile", defaultValue = "prod")
    String environment;

    public void recordRequest(String domain, String operation, int status, long durationNanos) {
        recordRequest(domain, operation, status, false, durationNanos);
    }

    public void recordRequest(
            String domain,
            String operation,
            int status,
            boolean expectedRejection,
            long durationNanos
    ) {
        String outcome = requestOutcome(status, expectedRejection);
        Tags tags = Tags.of(
                "domain", domain,
                "operation", operation,
                "status", Integer.toString(status),
                "outcome", outcome,
                "error_type", requestErrorType(status),
                "error_code", status < 400 ? "none" : "http_" + status,
                "environment", environment
        );
        registry.counter("yoursay.domain.requests.total", tags).increment();
        registry.counter("yoursay.domain.throughput.total", tags).increment();
        if (isFailure(outcome)) {
            registry.counter("yoursay.domain.errors.total", tags).increment();
        }
        Timer.builder("yoursay.domain.request.duration")
                .tags(tags)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /** Traffic and failures of an internal domain operation that has no public API of its own. */
    public void recordOperation(String domain, String operation, boolean success) {
        countOutcome(domain, operation, success, SERVICE_ERROR);
    }

    /**
     * A background job run. Its duration is recorded as well as its outcome, because a job that
     * silently slows down is invisible in a counter alone.
     */
    public void recordJob(String domain, String operation, boolean success, long durationNanos) {
        Tags tags = countOutcome(domain, operation, success, JOB_ERROR);
        Timer.builder("yoursay.domain.job.duration")
                .tags(tags)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private Tags countOutcome(String domain, String operation, boolean success, String errorOutcome) {
        Tags tags = Tags.of(
                "domain", domain,
                "operation", operation,
                "outcome", success ? SUCCESS : errorOutcome,
                "error_type", success ? "none" : "internal",
                "error_code", success ? "none" : errorCodeFor(errorOutcome),
                "environment", environment
        );
        registry.counter("yoursay.domain.operations.total", tags).increment();
        registry.counter("yoursay.domain.success.total", tags).increment(success ? 1.0 : 0.0);
        if (!success) {
            registry.counter("yoursay.domain.errors.total", tags).increment();
        }
        return tags;
    }

    public void recordOperation(
            String domain,
            String operation,
            String outcome,
            String errorType,
            String errorCode,
            long durationNanos
    ) {
        Tags tags = Tags.of(
                "domain", domain,
                "operation", operation,
                "outcome", outcome,
                "error_type", errorType,
                "error_code", errorCode,
                "environment", environment
        );
        registry.counter("yoursay.domain.operations.total", tags).increment();
        registry.counter("yoursay.domain.success.total", tags)
                .increment("success".equals(outcome) ? 1.0 : 0.0);
        if (isFailure(outcome)) {
            registry.counter("yoursay.domain.errors.total", tags).increment();
        }
        Timer.builder("yoursay.domain.operation.duration")
                .tags(tags)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /** Top-10 error panels group by this stable code, never by a raw message or stack trace. */
    public void recordError(String domain, String operation, String errorCode, int status) {
        registry.counter("yoursay.domain.errors.by_code.total", Tags.of(
                "domain", domain,
                "operation", operation,
                "error_code", errorCode,
                "status", Integer.toString(status),
                "outcome", requestOutcome(status, false),
                "error_type", requestErrorType(status),
                "environment", environment
        )).increment();
    }

    private static String requestOutcome(int status, boolean expectedRejection) {
        if (status >= 500) {
            return SERVER_ERROR;
        }
        if (status >= 400) {
            return expectedRejection ? EXPECTED_REJECTION : UNEXPECTED_CLIENT_ERROR;
        }
        return SUCCESS;
    }

    private static String requestErrorType(int status) {
        if (status >= 500) {
            return "server";
        }
        return status >= 400 ? "client" : "none";
    }

    private static boolean isFailure(String outcome) {
        return !SUCCESS.equals(outcome) && !EXPECTED_REJECTION.equals(outcome);
    }

    private static String errorCodeFor(String errorOutcome) {
        return JOB_ERROR.equals(errorOutcome) ? "job_failed" : "operation_failed";
    }
}
