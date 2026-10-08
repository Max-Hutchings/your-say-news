package com.yoursay.platform.mobiletelemetry.service;

import com.yoursay.platform.mobiletelemetry.dto.MobileClientDto;
import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryEventDto;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static com.yoursay.platform.mobiletelemetry.MobileTelemetryFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobileEventSanitizerTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final long NOW_MS = NOW.toEpochMilli();

    private final MobileEventSanitizer sanitizer =
            new MobileEventSanitizer(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void hostedApiCallIsLabelledWithTheSameRouteVocabularyAsTheBackend() {
        MobileEvent event = sanitizer.sanitize(
                apiCall("GET", "/api/posts/42?include=sources", 200, null, NOW_MS, NOW_MS + 184)).orElseThrow();

        MobileEvent.ApiCall call = event.apiCall();
        assertEquals("/posts/{id}", call.route());
        assertEquals("posts", call.domain());
        assertEquals("GET.posts.{id}", call.operation());
        assertEquals("success", call.outcome().outcome());
        assertEquals(Duration.ofMillis(184), event.duration());
        assertEquals(TRACE_ID, event.traceId());
        assertEquals(SCREEN_SPAN_ID, event.parentSpanId());
    }

    @Test
    void memberIdsInTheRequestPathAreNeverKept() {
        MobileEvent.ApiCall profile = sanitizer.sanitize(
                apiCall("GET", "/api/profiles/17", 200, null, NOW_MS, NOW_MS)).orElseThrow().apiCall();
        MobileEvent.ApiCall follow = sanitizer.sanitize(
                apiCall("DELETE", "/social/follows/17", 200, null, NOW_MS, NOW_MS)).orElseThrow().apiCall();

        assertEquals("/profiles/{id}", profile.route());
        assertEquals("user", profile.domain());
        assertEquals("GET.profiles.{id}", profile.operation());
        assertEquals("/social/follows/{id}", follow.route());
        assertEquals("DELETE.social.follows.{id}", follow.operation());
    }

    @Test
    void unknownMethodAndStatusCollapseToBoundedValues() {
        MobileEvent.ApiCall call = sanitizer.sanitize(
                apiCall("TRACE", "/votes", 999, "network", NOW_MS, NOW_MS)).orElseThrow().apiCall();

        assertEquals("OTHER", call.method());
        assertEquals(0, call.status());
        assertEquals("network_error", call.outcome().faultCode());
    }

    @Test
    void unknownScreenActionAndUnsafeTargetNeverBecomeLabels() {
        MobileEvent event = sanitizer.sanitize(
                action("/admin/secret", "vote.cast_for_option_7", "jane@example.com", NOW_MS)).orElseThrow();

        assertEquals("other", event.screen());
        assertEquals("other", event.action());
        assertNull(event.target());
    }

    @Test
    void knownScreenActionAndTargetPassThrough() {
        MobileEvent event = sanitizer.sanitize(action("/posts/[postId]", "vote.cast", "42", NOW_MS)).orElseThrow();

        assertEquals("/posts/[postId]", event.screen());
        assertEquals("vote.cast", event.action());
        assertEquals("42", event.target());
    }

    @Test
    void spanEventsWithoutUsableTraceContextAreDropped() {
        MobileTelemetryEventDto noTrace = new MobileTelemetryEventDto("action", NOW_MS, null, "/", null,
                null, CHILD_SPAN_ID, null, "feed.refresh", null, null, null, null, null, null, null, null, null);
        MobileTelemetryEventDto zeroTrace = new MobileTelemetryEventDto("action", NOW_MS, null, "/", null,
                "0".repeat(32), CHILD_SPAN_ID, null, "feed.refresh", null, null, null, null, null, null, null, null, null);
        MobileTelemetryEventDto shortSpan = new MobileTelemetryEventDto("action", NOW_MS, null, "/", null,
                TRACE_ID, "abc", null, "feed.refresh", null, null, null, null, null, null, null, null, null);

        assertTrue(sanitizer.sanitize(noTrace).isEmpty());
        assertTrue(sanitizer.sanitize(zeroTrace).isEmpty());
        assertTrue(sanitizer.sanitize(shortSpan).isEmpty());
    }

    @Test
    void lifecycleEventsDoNotNeedTraceContext() {
        MobileEvent event = sanitizer.sanitize(appState("background", NOW_MS)).orElseThrow();

        assertEquals(MobileEventType.APP_STATE, event.type());
        assertEquals("background", event.appState());
        assertTrue(sanitizer.sanitize(appStart(NOW_MS)).isPresent());
    }

    @Test
    void eventsOutsideTheClockWindowOrRunningBackwardsAreDropped() {
        long tooOld = NOW.minus(Duration.ofHours(25)).toEpochMilli();
        long tooFarAhead = NOW.plus(Duration.ofMinutes(6)).toEpochMilli();

        assertTrue(sanitizer.sanitize(screenEnter("/", null, tooOld)).isEmpty());
        assertTrue(sanitizer.sanitize(screenEnter("/", null, tooFarAhead)).isEmpty());
        assertTrue(sanitizer.sanitize(screenExit("/", NOW_MS, NOW_MS - 1)).isEmpty());
        long dayAgo = NOW.minus(Duration.ofHours(24)).toEpochMilli();
        assertTrue(sanitizer.sanitize(screenExit("/", dayAgo, NOW_MS + 1)).isEmpty());
        assertTrue(sanitizer.sanitize(screenExit("/", dayAgo, NOW_MS)).isPresent());
        assertTrue(sanitizer.sanitize(screenEnter("/", null, NOW.plus(Duration.ofMinutes(4)).toEpochMilli()))
                .isPresent());
    }

    @Test
    void unknownEventTypeIsDropped() {
        MobileTelemetryEventDto unknown = new MobileTelemetryEventDto("keystroke", NOW_MS, null, "/", null,
                null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertTrue(sanitizer.sanitize(unknown).isEmpty());
    }

    @Test
    void crashMessageIsScrubbedOfEmailsAndLongNumbersAndTruncated() {
        String message = "Cannot load profile for jane.doe+news@example.co.uk with phone 07700900123 " + "retry ".repeat(80);

        MobileEvent.AppError error = sanitizer.sanitize(
                error("TypeError", message, "render", false, NOW_MS)).orElseThrow().error();

        assertTrue(error.message().startsWith("Cannot load profile for [email] with phone [number] retry retry"));
        assertEquals(300, error.message().length());
        assertEquals("TypeError", error.name());
        assertEquals("render", error.source());
    }

    @Test
    void crashMessageIsScrubbedOfOpaqueIdsButKeepsShortNumbers() {
        String message = "No profile for uid Xk3vT9qLmZ2wR8pYc4nB7sD1fGh0 at index 3";

        assertEquals("No profile for uid [id] at index 3", MobileEventSanitizer.scrubbedMessage(message));
    }

    @Test
    void crashWithUnsafeNameAndUnknownSourceFallsBack() {
        MobileEvent.AppError error = sanitizer.sanitize(
                error("<script>", null, "keyboard", true, NOW_MS)).orElseThrow().error();

        assertEquals("Error", error.name());
        assertEquals("global", error.source());
        assertEquals("", error.message());
        assertTrue(error.fatal());
    }

    @Test
    void signInFailureLogKeepsItsNameLevelAndOnlyAllowlistedAttributes() {
        MobileEvent.AppLog log = sanitizer.sanitize(log("warn", "auth.sign_in_failed", null,
                Map.of("code", "10", "stage", "google", "email", "jane@example.com"), NOW_MS)).orElseThrow().log();

        assertEquals(new MobileEvent.AppLog("warn", "auth.sign_in_failed", "",
                Map.of("stage", "google", "code", "10")), log);
    }

    @Test
    void logAttributeValuesThatCouldHoldFreeTextAreDropped() {
        MobileEvent.AppLog log = sanitizer.sanitize(log("warn", "auth.sign_in_failed", null,
                Map.of("stage", "google", "code", "jane doe@example.com"), NOW_MS)).orElseThrow().log();

        assertEquals(Map.of("stage", "google"), log.attributes());
    }

    @Test
    void consoleMessageIsScrubbedAndAnUnknownLogNameCollapsesToOther() {
        MobileEvent.AppLog console = sanitizer.sanitize(log("error", "console",
                "Profile load failed for jane@example.com", null, NOW_MS)).orElseThrow().log();
        MobileEvent.AppLog invented = sanitizer.sanitize(log("warn", "user.jane_doe_logged_in", null, null, NOW_MS))
                .orElseThrow().log();

        assertEquals("console", console.name());
        assertEquals("Profile load failed for [email]", console.message());
        assertEquals("other", invented.name());
        assertEquals(Map.of(), invented.attributes());
    }

    @Test
    void logWithAnUnknownLevelOrNoLogBodyIsDropped() {
        MobileTelemetryEventDto noBody = new MobileTelemetryEventDto("log", NOW_MS, null, "/sign-in", null,
                null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertTrue(sanitizer.sanitize(log("debug", "console", "hello", null, NOW_MS)).isEmpty());
        assertTrue(sanitizer.sanitize(noBody).isEmpty());
        assertTrue(sanitizer.sanitize(log("info", "console", "hello", null, NOW_MS)).isPresent());
    }

    @Test
    void sessionKeepsKnownPlatformAndCollapsesUnsafeVersions() {
        MobileSession known = sanitizer.session(SESSION_ID, new MobileClientDto("android", "15", "1.4.0"));
        MobileSession unknown = sanitizer.session(SESSION_ID, new MobileClientDto("tvos", "15; DROP", null));

        assertEquals(new MobileSession(SESSION_ID, "android", "15", "1.4.0", null), known);
        assertEquals(new MobileSession(SESSION_ID, "other", "unknown", "unknown", null), unknown);
    }
}
