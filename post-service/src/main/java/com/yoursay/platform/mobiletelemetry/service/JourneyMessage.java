package com.yoursay.platform.mobiletelemetry.service;

import java.util.Locale;

/**
 * One readable line per app event, so a session's Loki stream reads as a click journey:
 * "Opened /posts/[postId] (42)", "Tapped vote.cast", "POST.votes -> 201 success in 184 ms".
 */
final class JourneyMessage {

    private JourneyMessage() {
    }

    static String describe(MobileSession session, MobileEvent event) {
        return switch (event.type()) {
            case APP_START -> "App started on %s %s (app %s)".formatted(
                    session.platform(), session.osVersion(), session.appVersion());
            case APP_STATE -> "background".equals(event.appState())
                    ? "App moved to background"
                    : "App returned to foreground";
            case SCREEN_ENTER -> "Opened " + event.screen() + targetSuffix(event);
            case SCREEN_EXIT -> "Left %s%s after %s".formatted(
                    event.screen(), targetSuffix(event), seconds(event));
            case ACTION -> "Tapped %s on %s%s".formatted(event.action(), event.screen(), targetSuffix(event));
            case API_CALL -> apiCall(event);
            case ERROR -> crash(event);
            case LOG -> log(event);
        };
    }

    private static String apiCall(MobileEvent event) {
        MobileEvent.ApiCall call = event.apiCall();
        String result = call.outcome().isSuccess()
                ? "success"
                : call.outcome().outcome() + " " + call.outcome().failureCode();
        String status = call.status() == 0 ? "no response" : Integer.toString(call.status());
        return "%s -> %s %s in %d ms".formatted(call.operation(), status, result, event.duration().toMillis());
    }

    private static String crash(MobileEvent event) {
        MobileEvent.AppError error = event.error();
        String kind = error.fatal() ? "Fatal crash" : "render".equals(error.source()) ? "Screen crash" : "Unhandled error";
        String message = error.message().isEmpty() ? "" : ": " + error.message();
        return "%s %s on %s%s".formatted(kind, error.name(), event.screen(), message);
    }

    /** "auth.sign_in_failed on /sign-in stage=google code=10" or "console on /: Profile load failed". */
    private static String log(MobileEvent event) {
        MobileEvent.AppLog log = event.log();
        StringBuilder line = new StringBuilder(log.name()).append(" on ").append(event.screen());
        log.attributes().forEach((key, value) -> line.append(' ').append(key).append('=').append(value));
        if (!log.message().isEmpty()) {
            line.append(": ").append(log.message());
        }
        return line.toString();
    }

    private static String targetSuffix(MobileEvent event) {
        return event.target() == null ? "" : " (" + event.target() + ")";
    }

    private static String seconds(MobileEvent event) {
        return String.format(Locale.ROOT, "%.1f s", event.duration().toMillis() / 1000.0);
    }
}
