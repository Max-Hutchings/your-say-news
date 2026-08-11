package com.yoursay.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class DomainMetrics {

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

    public void recordOperation(String domain, String operation, boolean success) {
        Tags tags = Tags.of(
                "domain", domain,
                "operation", operation,
                "outcome", success ? "success" : "service_error",
                "error_type", success ? "none" : "internal",
                "error_code", success ? "none" : "operation_failed",
                "environment", environment
        );
        registry.counter("yoursay.domain.operations.total", tags).increment();
        registry.counter("yoursay.domain.success.total", tags).increment(success ? 1.0 : 0.0);
        if (!success) {
            registry.counter("yoursay.domain.errors.total", tags).increment();
        }
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
            return "server_error";
        }
        if (status >= 400) {
            return expectedRejection ? "expected_rejection" : "unexpected_client_error";
        }
        return "success";
    }

    private static String requestErrorType(int status) {
        if (status >= 500) {
            return "server";
        }
        return status >= 400 ? "client" : "none";
    }

    private static boolean isFailure(String outcome) {
        return !"success".equals(outcome) && !"expected_rejection".equals(outcome);
    }
}
