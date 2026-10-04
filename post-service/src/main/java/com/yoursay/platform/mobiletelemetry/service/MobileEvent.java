package com.yoursay.platform.mobiletelemetry.service;

import java.time.Duration;
import java.time.Instant;

/**
 * A validated app event. Only {@link MobileEventSanitizer} creates these, so every string here has
 * already been checked against an allowlist or a strict pattern.
 */
record MobileEvent(
        MobileEventType type,
        Instant start,
        Instant end,
        String screen,
        String target,
        String traceId,
        String spanId,
        String parentSpanId,
        String action,
        ApiCall apiCall,
        AppError error,
        String appState
) {

    Duration duration() {
        return Duration.between(start, end);
    }

    boolean hasSpan() {
        return spanId != null;
    }

    /**
     * One request to post-service. {@code route} is the templated path ({@code /profiles/{id}}): the
     * raw path can hold a member id and is never kept. {@code domain} and {@code operation} match the
     * backend's own labels.
     */
    record ApiCall(String method, String route, String domain, String operation, int status, ApiOutcome outcome) {
    }

    /** A crash. {@code source} is render (an error boundary caught it) or global (nothing did). */
    record AppError(String name, String message, boolean fatal, String source) {
    }
}
