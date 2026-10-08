package com.yoursay.platform.mobiletelemetry.service;

import com.yoursay.platform.mobiletelemetry.dto.MobileClientDto;
import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryBatchDto;
import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryEventDto;
import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryReceiptDto;
import com.yoursay.user.user.YourSayUserService;
import com.yoursay.user.user.dto.UserAccessDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.Mockito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.yoursay.platform.mobiletelemetry.MobileTelemetryFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The relay's routing: which events become which metric, and when spans and logs are exported. */
class MobileTelemetryServiceImplTest {

    private SimpleMeterRegistry registry;
    private static final String CALLER = "riley.reader@example.com";

    private final List<String> exportedSignals = new ArrayList<>();
    private final List<Map<String, Object>> exportedPayloads = new ArrayList<>();
    private final YourSayUserService userService = Mockito.mock(YourSayUserService.class);
    private MobileTelemetryServiceImpl service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        MobileTelemetryMetrics metrics = new MobileTelemetryMetrics();
        metrics.registry = registry;
        metrics.environment = "test";
        OtlpExporter exporter = new OtlpExporter() {
            @Override
            CompletableFuture<Void> export(String signal, Map<String, Object> payload) {
                exportedSignals.add(signal);
                exportedPayloads.add(payload);
                return CompletableFuture.completedFuture(null);
            }
        };
        exporter.endpoint = Optional.of(URI.create("http://collector:4318"));
        service = new MobileTelemetryServiceImpl();
        service.metrics = metrics;
        service.exporter = exporter;
        service.userService = userService;
        Mockito.when(userService.getAccessByEmail(CALLER)).thenReturn(new UserAccessDto(1207L, null, null, false));
        service.environment = "test";
        service.anonymousBatchesPerMinute = 2;
        service.init();
    }

    @Test
    void eachEventTypeFeedsItsOwnMetric() {
        long now = System.currentTimeMillis();

        MobileTelemetryReceiptDto receipt = service.record(batch(List.of(
                appStart(now),
                screenEnter("/posts/[postId]", "42", now),
                screenExit("/posts/[postId]", now, now + 12_300),
                apiCall("GET", "/feed", 200, null, now, now + 80),
                error("TypeError", "boom", "render", false, now),
                appState("background", now))), CALLER);

        assertEquals(new MobileTelemetryReceiptDto(6, 0), receipt);
        assertEquals(1.0, registry.get("yoursay.mobile.sessions.total").tags("platform", "ios").counter().count());
        assertEquals(1.0, registry.get("yoursay.mobile.screen.views.total")
                .tags("screen", "/posts/[postId]").counter().count());
        assertEquals(12_300.0, registry.get("yoursay.mobile.screen.duration")
                .tags("screen", "/posts/[postId]").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
        assertEquals(80.0, registry.get("yoursay.mobile.operation.duration")
                .tags("domain", "feed", "operation", "GET.feed", "outcome", "success").timer()
                .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
        assertEquals(1.0, registry.get("yoursay.mobile.app.faults.total")
                .tags("screen", "/posts/[postId]", "fault_code", "render_error", "fatal", "false").counter().count());
        assertEquals(List.of("traces", "logs"), exportedSignals);
    }

    @Test
    void batchWithOnlyInvalidEventsExportsNothingAndCountsTheDrops() {
        MobileTelemetryEventDto unknown = new MobileTelemetryEventDto("keystroke", System.currentTimeMillis(), null,
                "/", null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        MobileTelemetryReceiptDto receipt = service.record(batch(List.of(unknown, unknown)), CALLER);

        assertEquals(new MobileTelemetryReceiptDto(0, 2), receipt);
        assertEquals(List.of(), exportedSignals);
        assertEquals(2.0, registry.get("yoursay.mobile.events.dropped.total").tags("reason", "invalid").counter().count());
    }

    @Test
    void journeyIsStampedWithTheCallersInternalIdNeverTheirEmail() {
        long now = System.currentTimeMillis();
        service.record(batch(List.of(appStart(now), action("/", "feed.refresh", null, now))), CALLER);

        assertEquals(List.of("traces", "logs"), exportedSignals);
        assertEquals(List.of("1207"), userIdsIn(exportedPayloads.get(0)));
        assertEquals(List.of("1207", "1207"), userIdsIn(exportedPayloads.get(1)));
        exportedPayloads.forEach(payload -> assertFalse(payload.toString().contains(CALLER)));
    }

    @Test
    void lifecycleOnlyBatchSkipsTheEmptyTracesExport() {
        service.record(batch(List.of(appStart(System.currentTimeMillis()))), CALLER);

        assertEquals(List.of("logs"), exportedSignals);
    }

    @Test
    void callerWithoutAnAccountIsRecordedWithoutAUserId() {
        service.record(batch(List.of(appStart(System.currentTimeMillis()))), "new.signup@example.com");

        assertEquals(List.of(), userIdsIn(exportedPayloads.getFirst()));
    }

    @Test
    void appLogIsCountedByLevelAndName() {
        long now = System.currentTimeMillis();

        service.record(batch(List.of(
                log("warn", "auth.sign_in_failed", null, Map.of("stage", "google", "code", "10"), now),
                log("error", "console", "boom", null, now))), CALLER);

        assertEquals(1.0, registry.get("yoursay.mobile.logs.total")
                .tags("platform", "ios", "level", "warn", "log_name", "auth.sign_in_failed").counter().count());
        assertEquals(1.0, registry.get("yoursay.mobile.logs.total")
                .tags("level", "error", "log_name", "console").counter().count());
        assertEquals(List.of("logs"), exportedSignals);
    }

    @Test
    void callerWhoHasNotSignedInIsRecordedWithoutAUserIdOrAnAccountLookup() {
        MobileTelemetryReceiptDto receipt = service.record(batch(List.of(
                log("warn", "auth.sign_in_failed", null, Map.of("stage", "google", "code", "10"),
                        System.currentTimeMillis()))), null);

        assertEquals(new MobileTelemetryReceiptDto(1, 0), receipt);
        assertEquals(List.of(), userIdsIn(exportedPayloads.getFirst()));
        Mockito.verifyNoInteractions(userService);
    }

    @Test
    void anonymousBatchesBeyondTheBudgetAreDroppedButSignedInCallersAreNot() {
        long now = System.currentTimeMillis();
        MobileTelemetryBatchDto twoEvents = batch(List.of(appStart(now), appState("background", now)));

        service.record(twoEvents, CALLER);
        service.record(twoEvents, CALLER);
        MobileTelemetryReceiptDto firstAnonymous = service.record(twoEvents, null);
        MobileTelemetryReceiptDto secondAnonymous = service.record(twoEvents, null);
        MobileTelemetryReceiptDto overBudget = service.record(twoEvents, null);
        MobileTelemetryReceiptDto signedIn = service.record(twoEvents, CALLER);

        assertEquals(new MobileTelemetryReceiptDto(2, 0), firstAnonymous);
        assertEquals(new MobileTelemetryReceiptDto(2, 0), secondAnonymous);
        assertEquals(new MobileTelemetryReceiptDto(0, 2), overBudget);
        assertEquals(new MobileTelemetryReceiptDto(2, 0), signedIn);
        assertEquals(2.0, registry.get("yoursay.mobile.events.dropped.total")
                .tags("reason", "anonymous_rate_limited").counter().count());
        assertEquals(List.of("logs", "logs", "logs", "logs", "logs"), exportedSignals);
    }

    /** Every {@code user.id} attribute value anywhere in an OTLP payload, in document order. */
    @SuppressWarnings("unchecked")
    private static List<String> userIdsIn(Object node) {
        List<String> found = new ArrayList<>();
        if (node instanceof Map<?, ?> map) {
            if ("user.id".equals(map.get("key"))) {
                found.add((String) ((Map<String, Object>) map.get("value")).get("stringValue"));
            }
            map.values().forEach(value -> found.addAll(userIdsIn(value)));
        } else if (node instanceof List<?> list) {
            list.forEach(item -> found.addAll(userIdsIn(item)));
        }
        return found;
    }

    private static MobileTelemetryBatchDto batch(List<MobileTelemetryEventDto> events) {
        return new MobileTelemetryBatchDto(SESSION_ID, new MobileClientDto("ios", "18.2", "1.4.0"), events);
    }
}
