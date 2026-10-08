package com.yoursay.platform.mobiletelemetry.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * One thing that happened in the app. A flat record rather than a type hierarchy: each {@code type}
 * reads the fields it needs and the server ignores the rest.
 *
 * <ul>
 *   <li>{@code app_start}, {@code app_state} - lifecycle; {@code appState} is active or background.</li>
 *   <li>{@code screen_enter}, {@code screen_exit} - a route opened or closed; exit carries the span.</li>
 *   <li>{@code action} - a named tap such as {@code vote.cast}.</li>
 *   <li>{@code api_call} - one request to post-service, timed on the device.</li>
 *   <li>{@code error} - a render crash or an unhandled JavaScript error.</li>
 *   <li>{@code log} - a diagnostic log record (sign-in failure, console warning or error); see {@link MobileLogDto}.</li>
 * </ul>
 *
 * <p>No field may carry a user id, email, vote choice or characteristic answer.
 */
public record MobileTelemetryEventDto(
        @Size(max = 32) String type,
        long timestampMs,
        Long endTimestampMs,
        @Size(max = 128) String screen,
        @Size(max = 64) String target,
        @Size(max = 32) String traceId,
        @Size(max = 16) String spanId,
        @Size(max = 16) String parentSpanId,
        @Size(max = 64) String action,
        @Size(max = 16) String method,
        @Size(max = 512) String path,
        Integer status,
        @Size(max = 32) String errorKind,
        @Size(max = 128) String errorName,
        @Size(max = 2000) String errorMessage,
        Boolean fatal,
        @Size(max = 16) String appState,
        @Valid MobileLogDto log
) {
}
