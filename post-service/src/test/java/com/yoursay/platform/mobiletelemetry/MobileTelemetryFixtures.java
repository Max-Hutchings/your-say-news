package com.yoursay.platform.mobiletelemetry;

import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryEventDto;

/** Events shaped as the Expo app sends them. */
public final class MobileTelemetryFixtures {

    public static final String SESSION_ID = "0123456789abcdef0123456789abcdef";
    public static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    public static final String SCREEN_SPAN_ID = "00f067aa0ba902b7";
    public static final String CHILD_SPAN_ID = "b7ad6b7169203331";

    private MobileTelemetryFixtures() {
    }

    public static MobileTelemetryEventDto appStart(long at) {
        return event("app_start", at, null, "/", null, null, null, null);
    }

    public static MobileTelemetryEventDto screenEnter(String screen, String target, long at) {
        return event("screen_enter", at, null, screen, target, TRACE_ID, SCREEN_SPAN_ID, null);
    }

    public static MobileTelemetryEventDto screenExit(String screen, long start, long end) {
        return event("screen_exit", start, end, screen, null, TRACE_ID, SCREEN_SPAN_ID, null);
    }

    public static MobileTelemetryEventDto action(String screen, String action, String target, long at) {
        return new MobileTelemetryEventDto("action", at, null, screen, target, TRACE_ID, CHILD_SPAN_ID,
                SCREEN_SPAN_ID, action, null, null, null, null, null, null, null, null);
    }

    public static MobileTelemetryEventDto apiCall(String method, String path, Integer status, String errorKind,
                                                  long start, long end) {
        return new MobileTelemetryEventDto("api_call", start, end, "/posts/[postId]", null, TRACE_ID,
                CHILD_SPAN_ID, SCREEN_SPAN_ID, null, method, path, status, errorKind, null, null, null, null);
    }

    public static MobileTelemetryEventDto error(String name, String message, String source, boolean fatal, long at) {
        return new MobileTelemetryEventDto("error", at, null, "/posts/[postId]", null, TRACE_ID, CHILD_SPAN_ID,
                SCREEN_SPAN_ID, null, null, null, null, source, name, message, fatal, null);
    }

    public static MobileTelemetryEventDto appState(String state, long at) {
        return new MobileTelemetryEventDto("app_state", at, null, "/", null, null, null, null,
                null, null, null, null, null, null, null, null, state);
    }

    private static MobileTelemetryEventDto event(String type, long start, Long end, String screen, String target,
                                                 String traceId, String spanId, String parentSpanId) {
        return new MobileTelemetryEventDto(type, start, end, screen, target, traceId, spanId, parentSpanId,
                null, null, null, null, null, null, null, null, null);
    }
}
