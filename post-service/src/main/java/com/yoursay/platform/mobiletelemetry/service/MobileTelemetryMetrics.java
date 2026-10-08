package com.yoursay.platform.mobiletelemetry.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Prometheus counting source for the Mobile dashboard. Labels are drawn only from
 * {@link MobileVocabulary}, the backend route vocabulary and fixed outcome codes, so a session id,
 * post id or target never becomes a label - those live in logs and traces only.
 */
@ApplicationScoped
class MobileTelemetryMetrics {

    static final String DROP_INVALID = "invalid";
    static final String DROP_ANONYMOUS_RATE_LIMITED = "anonymous_rate_limited";

    @Inject
    MeterRegistry registry;

    @ConfigProperty(name = "app.environment")
    String environment;

    void recordSessionStart(MobileSession session) {
        registry.counter("yoursay.mobile.sessions.total", baseTags(session)).increment();
    }

    void recordScreenView(MobileSession session, MobileEvent event) {
        registry.counter("yoursay.mobile.screen.views.total",
                baseTags(session).and("screen", event.screen())).increment();
    }

    /** Time on screen. A long dwell on a results screen is engagement; on a spinner it is a fault. */
    void recordScreenDwell(MobileSession session, MobileEvent event) {
        Timer.builder("yoursay.mobile.screen.duration")
                .tags(baseTags(session).and("screen", event.screen()))
                .register(registry)
                .record(event.duration());
    }

    void recordAction(MobileSession session, MobileEvent event) {
        registry.counter("yoursay.mobile.actions.total",
                baseTags(session).and("screen", event.screen(), "action", event.action())).increment();
    }

    /** Same domain / operation / outcome schema as the backend, timed on the device so it includes the network. */
    void recordApiCall(MobileSession session, MobileEvent event) {
        MobileEvent.ApiCall call = event.apiCall();
        Tags tags = baseTags(session).and(
                "domain", call.domain(),
                "operation", call.operation(),
                "outcome", call.outcome().outcome(),
                "error_code", call.outcome().errorCode(),
                "fault_code", call.outcome().faultCode());
        registry.counter("yoursay.mobile.operations.total", tags).increment();
        Timer.builder("yoursay.mobile.operation.duration")
                .tags(tags)
                .register(registry)
                .record(event.duration());
    }

    void recordAppFault(MobileSession session, MobileEvent event) {
        MobileEvent.AppError error = event.error();
        registry.counter("yoursay.mobile.app.faults.total", baseTags(session).and(
                "screen", event.screen(),
                "fault_code", error.source() + "_error",
                "fatal", Boolean.toString(error.fatal()))).increment();
    }

    /** Log name and level are allowlisted, so this stays bounded; attributes and messages stay in Loki. */
    void recordLog(MobileSession session, MobileEvent event) {
        MobileEvent.AppLog log = event.log();
        registry.counter("yoursay.mobile.logs.total",
                baseTags(session).and("level", log.level(), "log_name", log.name())).increment();
    }

    /** {@code reason} is {@link #DROP_INVALID} or {@link #DROP_ANONYMOUS_RATE_LIMITED}. */
    void recordDroppedEvents(int count, String reason) {
        registry.counter("yoursay.mobile.events.dropped.total",
                Tags.of("environment", environment, "reason", reason)).increment(count);
    }

    /** A broken pipeline must be visible, otherwise an empty dashboard reads as a quiet app. */
    void recordExportFailure(String signal) {
        registry.counter("yoursay.mobile.telemetry.export.failures.total",
                Tags.of("signal", signal, "environment", environment)).increment();
    }

    private Tags baseTags(MobileSession session) {
        return Tags.of("platform", session.platform(), "environment", environment);
    }
}
