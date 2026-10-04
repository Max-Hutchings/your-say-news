package com.yoursay.platform.mobiletelemetry.service;

import com.yoursay.platform.mobiletelemetry.dto.MobileClientDto;
import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryEventDto;
import com.yoursay.platform.observability.DomainRequestFilter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns untrusted device input into a {@link MobileEvent}. The app is a public client, so every
 * value is either matched against an allowlist, collapsed to a bounded fallback, or the event is
 * dropped. Nothing unbounded reaches a metric label, and error text is scrubbed before it is logged.
 */
class MobileEventSanitizer {

    /** Device clocks drift; anything older or further ahead than this is treated as garbage. */
    private static final Duration MAX_AGE = Duration.ofHours(24);
    private static final Duration MAX_CLOCK_AHEAD = Duration.ofMinutes(5);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 300;

    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern SPAN_ID = Pattern.compile("[0-9a-f]{16}");
    private static final Pattern TARGET = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9_.+-]{1,32}");
    private static final Pattern ERROR_NAME = Pattern.compile("[A-Za-z0-9_.$]{1,64}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\d{5,}");
    /** Firebase UIDs, tokens and similar opaque ids: 20+ characters with no spaces. */
    private static final Pattern OPAQUE_ID = Pattern.compile("\\b[A-Za-z0-9_-]{20,}\\b");
    private static final Pattern HOSTED_ROOT_PATH = Pattern.compile("^/api(?=/)");

    private static final Set<String> PLATFORMS = Set.of("ios", "android", "web");
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> APP_STATES = Set.of("active", "background");
    private static final Set<String> ERROR_SOURCES = Set.of("render", "global");

    private final Clock clock;

    MobileEventSanitizer(Clock clock) {
        this.clock = clock;
    }

    MobileSession session(String sessionId, MobileClientDto client) {
        return new MobileSession(
                sessionId,
                PLATFORMS.contains(client.platform()) ? client.platform() : MobileVocabulary.OTHER,
                matchesOrUnknown(VERSION, client.osVersion()),
                matchesOrUnknown(VERSION, client.appVersion()),
                null);
    }

    Optional<MobileEvent> sanitize(MobileTelemetryEventDto dto) {
        Optional<MobileEventType> type = MobileEventType.fromWireName(dto.type());
        if (type.isEmpty() || !hasValidTiming(dto) || !hasValidTraceContext(type.get(), dto)) {
            return Optional.empty();
        }
        return Optional.of(toEvent(type.get(), dto));
    }

    private MobileEvent toEvent(MobileEventType type, MobileTelemetryEventDto dto) {
        Instant start = Instant.ofEpochMilli(dto.timestampMs());
        Instant end = dto.endTimestampMs() == null ? start : Instant.ofEpochMilli(dto.endTimestampMs());
        return new MobileEvent(
                type,
                start,
                end,
                MobileVocabulary.screen(dto.screen()),
                matchesOrNull(TARGET, dto.target()),
                matchesOrNull(TRACE_ID, dto.traceId()),
                matchesOrNull(SPAN_ID, dto.spanId()),
                matchesOrNull(SPAN_ID, dto.parentSpanId()),
                type == MobileEventType.ACTION ? MobileVocabulary.action(dto.action()) : null,
                type == MobileEventType.API_CALL ? apiCall(dto) : null,
                type == MobileEventType.ERROR ? appError(dto) : null,
                type == MobileEventType.APP_STATE && APP_STATES.contains(dto.appState()) ? dto.appState() : null);
    }

    private boolean hasValidTiming(MobileTelemetryEventDto dto) {
        Instant now = clock.instant();
        Instant start = Instant.ofEpochMilli(dto.timestampMs());
        if (start.isBefore(now.minus(MAX_AGE)) || start.isAfter(now.plus(MAX_CLOCK_AHEAD))) {
            return false;
        }
        return dto.endTimestampMs() == null || dto.endTimestampMs() >= dto.timestampMs()
                && Duration.ofMillis(dto.endTimestampMs() - dto.timestampMs()).compareTo(MAX_AGE) <= 0;
    }

    /** Events that become spans are useless without ids that Tempo can join on, so they are dropped. */
    private static boolean hasValidTraceContext(MobileEventType type, MobileTelemetryEventDto dto) {
        boolean needsSpan = type == MobileEventType.SCREEN_EXIT || type == MobileEventType.ACTION
                || type == MobileEventType.API_CALL;
        if (!needsSpan) {
            return true;
        }
        return isTraceId(dto.traceId()) && dto.spanId() != null && SPAN_ID.matcher(dto.spanId()).matches();
    }

    private static boolean isTraceId(String value) {
        return value != null && TRACE_ID.matcher(value).matches() && !value.chars().allMatch(c -> c == '0');
    }

    private static MobileEvent.ApiCall apiCall(MobileTelemetryEventDto dto) {
        String method = METHODS.contains(dto.method()) ? dto.method() : "OTHER";
        String path = backendPath(dto.path());
        int status = dto.status() == null || dto.status() < 0 || dto.status() > 599 ? 0 : dto.status();
        return new MobileEvent.ApiCall(
                method,
                DomainRequestFilter.routeTemplateOf(path),
                DomainRequestFilter.domainFromPath(path),
                DomainRequestFilter.operationFrom(method, path),
                status,
                ApiOutcome.classify(status, dto.errorKind() == null ? "" : dto.errorKind()));
    }

    /**
     * The path as post-service routes it: no query string, and no hosted {@code /api} root path, so
     * the same call gets the same operation label on a laptop and on the dev server.
     */
    private static String backendPath(String rawPath) {
        if (rawPath == null || !rawPath.startsWith("/")) {
            return "/";
        }
        int query = rawPath.indexOf('?');
        String path = query >= 0 ? rawPath.substring(0, query) : rawPath;
        return HOSTED_ROOT_PATH.matcher(path).replaceFirst("");
    }

    private static MobileEvent.AppError appError(MobileTelemetryEventDto dto) {
        String name = dto.errorName() != null && ERROR_NAME.matcher(dto.errorName()).matches()
                ? dto.errorName() : "Error";
        String source = ERROR_SOURCES.contains(dto.errorKind()) ? dto.errorKind() : "global";
        return new MobileEvent.AppError(name, scrubbedMessage(dto.errorMessage()),
                Boolean.TRUE.equals(dto.fatal()), source);
    }

    /** Crash text is for debugging only, but an interpolated email or id must still never be stored. */
    static String scrubbedMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String withoutEmails = EMAIL.matcher(message).replaceAll("[email]");
        String withoutIds = OPAQUE_ID.matcher(withoutEmails).replaceAll("[id]");
        String scrubbed = LONG_NUMBER.matcher(withoutIds).replaceAll("[number]");
        return scrubbed.length() > MAX_ERROR_MESSAGE_LENGTH
                ? scrubbed.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                : scrubbed;
    }

    private static String matchesOrNull(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches() ? value : null;
    }

    private static String matchesOrUnknown(Pattern pattern, String value) {
        String matched = matchesOrNull(pattern, value);
        return matched == null ? "unknown" : matched;
    }
}
