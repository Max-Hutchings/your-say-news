package com.yoursay.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class DomainMetrics {

    @Inject
    MeterRegistry registry;

    public void recordRequest(String domain, String operation, int status, long durationNanos) {
        String outcome = status >= 500 ? "server_error" : status >= 400 ? "client_error" : "success";
        Tags tags = Tags.of(
                "domain", domain,
                "operation", operation,
                "status", Integer.toString(status),
                "outcome", outcome
        );
        registry.counter("yoursay.domain.requests.total", tags).increment();
        registry.counter("yoursay.domain.throughput.total", tags).increment();
        if (!"success".equals(outcome)) {
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
                "outcome", success ? "success" : "error"
        );
        registry.counter("yoursay.domain.operations.total", tags).increment();
        registry.counter("yoursay.domain.success.total", tags).increment(success ? 1.0 : 0.0);
        if (!success) {
            registry.counter("yoursay.domain.errors.total", tags).increment();
        }
    }

    public void recordError(String domain, String operation, String errorCode, int status) {
        registry.counter("yoursay.domain.errors.by_code.total", Tags.of(
                "domain", domain,
                "operation", operation,
                "error_code", errorCode,
                "status", Integer.toString(status)
        )).increment();
    }
}
