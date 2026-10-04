package com.yoursay.platform.mobiletelemetry.service;

import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryEventDto;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.yoursay.platform.mobiletelemetry.MobileTelemetryFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SuppressWarnings("unchecked")
class OtlpPayloadBuilderTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final long NOW_MS = NOW.toEpochMilli();
    private static final MobileSession SESSION = new MobileSession(SESSION_ID, "ios", "18.2", "1.4.0", 1207L);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final MobileEventSanitizer sanitizer = new MobileEventSanitizer(clock);
    private final OtlpPayloadBuilder builder = new OtlpPayloadBuilder("dev", clock);

    @Test
    void apiCallBecomesClientSpanUnderTheScreenSpanWithTheDeviceIds() {
        Map<String, Object> span = onlySpan(builder.traces(SESSION, events(
                apiCall("POST", "/votes", 201, null, NOW_MS, NOW_MS + 184))));

        assertEquals(TRACE_ID, span.get("traceId"));
        assertEquals(CHILD_SPAN_ID, span.get("spanId"));
        assertEquals(SCREEN_SPAN_ID, span.get("parentSpanId"));
        assertEquals("HTTP POST.votes", span.get("name"));
        assertEquals(3, span.get("kind"));
        assertEquals("1791108000000000000", span.get("startTimeUnixNano"));
        assertEquals("1791108000184000000", span.get("endTimeUnixNano"));
        assertFalse(span.containsKey("status"));
        Map<String, String> attributes = attributes(span);
        assertEquals("201", attributes.get("http.response.status_code"));
        assertEquals("votes", attributes.get("app.domain"));
        assertEquals("success", attributes.get("app.outcome"));
        assertEquals(SESSION_ID, attributes.get("session.id"));
    }

    @Test
    void profileCallPayloadsNeverContainTheMemberId() throws Exception {
        List<MobileEvent> events = events(apiCall("POST", "/api/social/follows/918273", 200, null, NOW_MS, NOW_MS + 40));
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        String traces = mapper.writeValueAsString(builder.traces(SESSION, events));
        String logs = mapper.writeValueAsString(builder.logs(SESSION, events));

        assertFalse(traces.contains("918273"));
        assertFalse(logs.contains("918273"));
        assertEquals("/social/follows/{id}", attributes(onlySpan(builder.traces(SESSION, events))).get("url.template"));
    }

    @Test
    void crashBecomesAnErrorSpanUnderTheScreenWithItsFaultCode() {
        Map<String, Object> span = onlySpan(builder.traces(SESSION, events(
                error("TypeError", "x is undefined", "global", true, NOW_MS))));

        assertEquals("crash TypeError", span.get("name"));
        assertEquals(1, span.get("kind"));
        assertEquals(SCREEN_SPAN_ID, span.get("parentSpanId"));
        assertEquals(Map.of("code", 2), span.get("status"));
        Map<String, String> attributes = attributes(span);
        assertEquals("fault", attributes.get("app.outcome"));
        assertEquals("global_error", attributes.get("app.fault_code"));
        assertEquals("true", attributes.get("app.error.fatal"));
        assertEquals("x is undefined", attributes.get("exception.message"));
    }

    @Test
    void tapBecomesAnInstantSpanUnderTheScreen() {
        Map<String, Object> span = onlySpan(builder.traces(SESSION, events(
                action("/posts/[postId]", "vote.cast", "42", NOW_MS))));

        assertEquals("tap vote.cast", span.get("name"));
        assertEquals(1, span.get("kind"));
        assertEquals(span.get("startTimeUnixNano"), span.get("endTimeUnixNano"));
        assertFalse(span.containsKey("status"));
        assertEquals("42", attributes(span).get("app.target"));
    }

    @Test
    void everySpanAndLogCarriesTheInternalUserIdSoSupportCanFindTheJourney() {
        List<MobileEvent> events = events(action("/posts/[postId]", "vote.cast", "42", NOW_MS));

        assertEquals("1207", attributes(onlySpan(builder.traces(SESSION, events))).get("user.id"));
        assertEquals("1207", attributes(logRecords(builder.logs(SESSION, events)).getFirst()).get("user.id"));
    }

    @Test
    void sessionWithoutAnAccountHasNoUserId() {
        List<MobileEvent> events = events(appStart(NOW_MS));

        Map<String, Object> log = logRecords(builder.logs(SESSION.withUserId(null), events)).getFirst();

        assertFalse(attributes(log).containsKey("user.id"));
    }

    @Test
    void faultyApiCallSpanIsMarkedAsError() {
        Map<String, Object> span = onlySpan(builder.traces(SESSION, events(
                apiCall("GET", "/feed", 503, null, NOW_MS, NOW_MS + 30))));

        assertEquals(Map.of("code", 2), span.get("status"));
        assertEquals("http_503", attributes(span).get("app.fault_code"));
    }

    @Test
    void screenExitIsTheRootSpanAndScreenEnterIsLogOnly() {
        Map<String, Object> traces = builder.traces(SESSION, events(
                screenEnter("/posts/[postId]", "42", NOW_MS),
                screenExit("/posts/[postId]", NOW_MS, NOW_MS + 12_300)));

        Map<String, Object> span = onlySpan(traces);
        assertEquals("screen /posts/[postId]", span.get("name"));
        assertEquals(SCREEN_SPAN_ID, span.get("spanId"));
        assertFalse(span.containsKey("parentSpanId"));
        assertEquals("12300", attributes(span).get("duration_ms"));
    }

    @Test
    void everyEventBecomesAReadableJourneyLogLinkedToItsSpan() {
        List<Map<String, Object>> records = logRecords(builder.logs(SESSION, events(
                appStart(NOW_MS),
                screenEnter("/posts/[postId]", "42", NOW_MS + 1),
                action("/posts/[postId]", "vote.cast", "42", NOW_MS + 2),
                apiCall("POST", "/api/votes", 409, null, NOW_MS + 3, NOW_MS + 90),
                apiCall("GET", "/feed", null, "timeout", NOW_MS + 4, NOW_MS + 10_004),
                screenExit("/posts/[postId]", NOW_MS + 1, NOW_MS + 12_301),
                appState("background", NOW_MS + 12_302),
                error("TypeError", "x is undefined", "render", false, NOW_MS + 12_303))));

        assertEquals(List.of(
                "App started on ios 18.2 (app 1.4.0)",
                "Opened /posts/[postId] (42)",
                "Tapped vote.cast on /posts/[postId] (42)",
                "POST.votes -> 409 error http_409 in 87 ms",
                "GET.feed -> no response fault timeout in 10000 ms",
                "Left /posts/[postId] after 12.3 s",
                "App moved to background",
                "Screen crash TypeError on /posts/[postId]: x is undefined"),
                records.stream().map(r -> ((Map<String, String>) r.get("body")).get("stringValue")).toList());
        assertEquals(List.of("INFO", "INFO", "INFO", "WARN", "ERROR", "INFO", "INFO", "ERROR"),
                records.stream().map(r -> r.get("severityText")).toList());
        assertEquals(CHILD_SPAN_ID, records.get(2).get("spanId"));
        assertEquals(TRACE_ID, records.get(2).get("traceId"));
        assertFalse(records.get(0).containsKey("traceId"));
        assertEquals("1791108012301000000", records.get(5).get("timeUnixNano"));
    }

    @Test
    void resourceNamesTheMobileServiceAndEnvironment() {
        Map<String, Object> resourceLogs = ((List<Map<String, Object>>) builder.logs(SESSION, events(appStart(NOW_MS)))
                .get("resourceLogs")).getFirst();
        Map<String, String> resource = attributes((Map<String, Object>) resourceLogs.get("resource"));

        assertEquals("your-say-news-mobile", resource.get("service.name"));
        assertEquals("dev", resource.get("deployment.environment"));
        assertEquals("1.4.0", resource.get("service.version"));
        assertEquals("ios", resource.get("os.name"));
    }

    private List<MobileEvent> events(MobileTelemetryEventDto... dtos) {
        return Stream.of(dtos).map(dto -> sanitizer.sanitize(dto).orElseThrow()).toList();
    }

    private static Map<String, Object> onlySpan(Map<String, Object> traces) {
        Map<String, Object> resourceSpans = ((List<Map<String, Object>>) traces.get("resourceSpans")).getFirst();
        Map<String, Object> scopeSpans = ((List<Map<String, Object>>) resourceSpans.get("scopeSpans")).getFirst();
        List<Map<String, Object>> spans = (List<Map<String, Object>>) scopeSpans.get("spans");
        assertEquals(1, spans.size());
        return spans.getFirst();
    }

    private static List<Map<String, Object>> logRecords(Map<String, Object> logs) {
        Map<String, Object> resourceLogs = ((List<Map<String, Object>>) logs.get("resourceLogs")).getFirst();
        Map<String, Object> scopeLogs = ((List<Map<String, Object>>) resourceLogs.get("scopeLogs")).getFirst();
        return (List<Map<String, Object>>) scopeLogs.get("logRecords");
    }

    private static Map<String, String> attributes(Map<String, Object> owner) {
        return ((List<Map<String, Object>>) owner.get("attributes")).stream()
                .collect(java.util.stream.Collectors.toMap(
                        attribute -> (String) attribute.get("key"),
                        attribute -> String.valueOf(((Map<String, Object>) attribute.get("value")).values().iterator().next())));
    }
}
