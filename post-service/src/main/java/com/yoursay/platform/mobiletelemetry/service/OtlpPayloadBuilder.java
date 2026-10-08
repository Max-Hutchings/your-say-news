package com.yoursay.platform.mobiletelemetry.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds OTLP/JSON trace and log payloads for the "your-say-news-mobile" service.
 *
 * <p>The OpenTelemetry Java API cannot create a span with an id chosen elsewhere, but the app
 * already sent its trace and span ids to post-service in the {@code traceparent} header. Writing the
 * OTLP payload directly keeps those ids, so Tempo shows one tree: screen, then the API call made on
 * it, then the post-service span that answered it.
 */
class OtlpPayloadBuilder {

    static final String SERVICE_NAME = "your-say-news-mobile";

    private static final int SPAN_KIND_INTERNAL = 1;
    private static final int SPAN_KIND_CLIENT = 3;
    private static final int STATUS_ERROR = 2;
    private static final int SEVERITY_INFO = 9;
    private static final int SEVERITY_WARN = 13;
    private static final int SEVERITY_ERROR = 17;

    private final String environment;
    private final Clock clock;

    OtlpPayloadBuilder(String environment, Clock clock) {
        this.environment = environment;
        this.clock = clock;
    }

    /** Lifecycle-only batches (app start, background) have nothing for Tempo. */
    static boolean hasSpans(List<MobileEvent> events) {
        return events.stream().anyMatch(OtlpPayloadBuilder::becomesSpan);
    }

    Map<String, Object> traces(MobileSession session, List<MobileEvent> events) {
        List<Map<String, Object>> spans = events.stream()
                .filter(OtlpPayloadBuilder::becomesSpan)
                .map(event -> span(session, event))
                .toList();
        return Map.of("resourceSpans", List.of(Map.of(
                "resource", resource(session),
                "scopeSpans", List.of(Map.of("scope", scope(), "spans", spans)))));
    }

    Map<String, Object> logs(MobileSession session, List<MobileEvent> events) {
        String observed = nanos(clock.instant());
        List<Map<String, Object>> records = events.stream()
                .map(event -> logRecord(session, event, observed))
                .toList();
        return Map.of("resourceLogs", List.of(Map.of(
                "resource", resource(session),
                "scopeLogs", List.of(Map.of("scope", scope(), "logRecords", records)))));
    }

    private static boolean becomesSpan(MobileEvent event) {
        return event.hasSpan() && event.traceId() != null && isSpanEvent(event);
    }

    private static boolean isSpanEvent(MobileEvent event) {
        return switch (event.type()) {
            case SCREEN_EXIT, ACTION, API_CALL, ERROR -> true;
            default -> false;
        };
    }

    private Map<String, Object> span(MobileSession session, MobileEvent event) {
        Map<String, Object> span = new LinkedHashMap<>();
        span.put("traceId", event.traceId());
        span.put("spanId", event.spanId());
        if (event.parentSpanId() != null) {
            span.put("parentSpanId", event.parentSpanId());
        }
        span.put("name", spanName(event));
        span.put("kind", event.type() == MobileEventType.API_CALL ? SPAN_KIND_CLIENT : SPAN_KIND_INTERNAL);
        span.put("startTimeUnixNano", nanos(event.start()));
        span.put("endTimeUnixNano", nanos(event.end()));
        span.put("attributes", eventAttributes(session, event).build());
        if (isFailure(event)) {
            span.put("status", Map.of("code", STATUS_ERROR));
        }
        return span;
    }

    private static String spanName(MobileEvent event) {
        return switch (event.type()) {
            case SCREEN_EXIT -> "screen " + event.screen();
            case ACTION -> "tap " + event.action();
            case API_CALL -> "HTTP " + event.apiCall().operation();
            case ERROR -> "crash " + event.error().name();
            default -> event.type().wireName();
        };
    }

    private Map<String, Object> logRecord(MobileSession session, MobileEvent event, String observed) {
        Map<String, Object> record = new LinkedHashMap<>();
        Instant at = event.type() == MobileEventType.SCREEN_EXIT ? event.end() : event.start();
        record.put("timeUnixNano", nanos(at));
        record.put("observedTimeUnixNano", observed);
        int severity = severity(event);
        record.put("severityNumber", severity);
        record.put("severityText", severity == SEVERITY_ERROR ? "ERROR" : severity == SEVERITY_WARN ? "WARN" : "INFO");
        record.put("body", Map.of("stringValue", JourneyMessage.describe(session, event)));
        record.put("attributes", eventAttributes(session, event).build());
        if (event.traceId() != null && event.spanId() != null) {
            record.put("traceId", event.traceId());
            record.put("spanId", event.spanId());
        }
        return record;
    }

    private static int severity(MobileEvent event) {
        if (event.type() == MobileEventType.ERROR) {
            return SEVERITY_ERROR;
        }
        if (event.type() == MobileEventType.LOG) {
            return logSeverity(event.log().level());
        }
        if (event.type() == MobileEventType.API_CALL && !event.apiCall().outcome().isSuccess()) {
            return event.apiCall().outcome().isFault() ? SEVERITY_ERROR : SEVERITY_WARN;
        }
        return SEVERITY_INFO;
    }

    private static int logSeverity(String level) {
        return switch (level) {
            case "error" -> SEVERITY_ERROR;
            case "warn" -> SEVERITY_WARN;
            default -> SEVERITY_INFO;
        };
    }

    private static boolean isFailure(MobileEvent event) {
        return event.type() == MobileEventType.ERROR
                || event.type() == MobileEventType.API_CALL && !event.apiCall().outcome().isSuccess();
    }

    /** Shared by spans and logs, so a log line and its span can be filtered on the same fields. */
    private static OtlpAttributes eventAttributes(MobileSession session, MobileEvent event) {
        OtlpAttributes attributes = new OtlpAttributes()
                .string("session.id", session.sessionId())
                .string("user.id", session.userId() == null ? null : session.userId().toString())
                .string("event.name", event.type().wireName())
                .string("app.screen", event.screen())
                .string("app.target", event.target())
                .string("app.action", event.action())
                .string("app.state", event.appState());
        if (event.type() == MobileEventType.SCREEN_EXIT || event.type() == MobileEventType.API_CALL) {
            attributes.integer("duration_ms", event.duration().toMillis());
        }
        if (event.apiCall() != null) {
            MobileEvent.ApiCall call = event.apiCall();
            attributes.string("http.request.method", call.method())
                    .string("url.template", call.route())
                    .integer("http.response.status_code", call.status())
                    .string("app.domain", call.domain())
                    .string("app.operation", call.operation())
                    .string("app.outcome", call.outcome().outcome())
                    .string("app.error_code", call.outcome().errorCode())
                    .string("app.fault_code", call.outcome().faultCode());
        }
        if (event.error() != null) {
            MobileEvent.AppError error = event.error();
            attributes.string("exception.type", error.name())
                    .string("exception.message", error.message())
                    .string("app.outcome", "fault")
                    .string("app.fault_code", error.source() + "_error")
                    .bool("app.error.fatal", error.fatal());
        }
        if (event.log() != null) {
            attributes.string("app.log.name", event.log().name());
            event.log().attributes().forEach((key, value) -> attributes.string("app.log." + key, value));
        }
        return attributes;
    }

    private Map<String, Object> resource(MobileSession session) {
        return Map.of("attributes", new OtlpAttributes()
                .string("service.name", SERVICE_NAME)
                .string("service.namespace", "your-say-news")
                .string("service.version", session.appVersion())
                .string("deployment.environment", environment)
                .string("deployment.environment.name", environment)
                .string("os.name", session.platform())
                .string("os.version", session.osVersion())
                .build());
    }

    private static Map<String, Object> scope() {
        return Map.of("name", "com.yoursay.platform.mobiletelemetry");
    }

    /** OTLP/JSON encodes 64-bit integers as strings. */
    private static String nanos(Instant instant) {
        return Long.toString(instant.getEpochSecond() * 1_000_000_000L + instant.getNano());
    }

    /** OTLP key/value list that skips absent values instead of writing empty strings. */
    static final class OtlpAttributes {
        private final List<Map<String, Object>> values = new ArrayList<>();

        OtlpAttributes string(String key, String value) {
            if (value != null) {
                values.add(Map.of("key", key, "value", Map.of("stringValue", value)));
            }
            return this;
        }

        OtlpAttributes integer(String key, long value) {
            values.add(Map.of("key", key, "value", Map.of("intValue", Long.toString(value))));
            return this;
        }

        OtlpAttributes bool(String key, boolean value) {
            values.add(Map.of("key", key, "value", Map.of("boolValue", value)));
            return this;
        }

        List<Map<String, Object>> build() {
            return List.copyOf(values);
        }
    }
}
