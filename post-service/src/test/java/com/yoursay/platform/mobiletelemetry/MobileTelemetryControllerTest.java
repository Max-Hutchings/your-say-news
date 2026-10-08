package com.yoursay.platform.mobiletelemetry;

import com.yoursay.platform.mobiletelemetry.dto.MobileClientDto;
import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryBatchDto;
import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryEventDto;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.yoursay.platform.mobiletelemetry.MobileTelemetryFixtures.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The relay end to end: auth, validation and the Prometheus counters the Mobile dashboard reads.
 * Each test uses its own platform/screen labels' delta, because the registry is shared by the suite.
 *
 * <p>The test profile disables OpenTelemetry, so Quarkus registers no child under Micrometer's global
 * composite registry and every counter it hands out is a no-op that always reads 0. An in-memory
 * registry is attached for this class so the counters the relay increments can be read back.
 */
@QuarkusTest
class MobileTelemetryControllerTest {

    private static final SimpleMeterRegistry METERS = new SimpleMeterRegistry();

    @BeforeAll
    static void recordMetersInMemory() {
        Metrics.globalRegistry.add(METERS);
    }

    @AfterAll
    static void detachInMemoryMeters() {
        Metrics.globalRegistry.remove(METERS);
    }

    @Test
    @TestSecurity(user = "reader@yoursay.com", roles = "user")
    void recordsAJourneyAndDropsTheInvalidEvent() {
        long now = System.currentTimeMillis();
        double screenViewsBefore = count(METERS.find("yoursay.mobile.screen.views.total")
                .tags("platform", "android", "screen", "/posts/[postId]"));
        double votesBefore = count(METERS.find("yoursay.mobile.actions.total")
                .tags("platform", "android", "action", "vote.cast"));
        double conflictsBefore = count(METERS.find("yoursay.mobile.operations.total")
                .tags("platform", "android", "domain", "votes", "operation", "POST.votes",
                        "outcome", "error", "error_code", "http_409"));
        double droppedBefore = count(METERS.find("yoursay.mobile.events.dropped.total").tags("reason", "invalid"));

        given().contentType("application/json")
                .body(batch("android", List.of(
                        screenEnter("/posts/[postId]", "42", now),
                        action("/posts/[postId]", "vote.cast", "42", now + 5),
                        apiCall("POST", "/votes", 409, null, now + 6, now + 90),
                        new MobileTelemetryEventDto("keystroke", now, null, "/", null, null, null, null,
                                null, null, null, null, null, null, null, null, null, null))))
                .when().post("/telemetry/mobile")
                .then()
                .statusCode(202)
                .body("accepted", is(3))
                .body("dropped", is(1));

        assertEquals(screenViewsBefore + 1, count(METERS.find("yoursay.mobile.screen.views.total")
                .tags("platform", "android", "screen", "/posts/[postId]")));
        assertEquals(votesBefore + 1, count(METERS.find("yoursay.mobile.actions.total")
                .tags("platform", "android", "action", "vote.cast")));
        assertEquals(conflictsBefore + 1, count(METERS.find("yoursay.mobile.operations.total")
                .tags("platform", "android", "domain", "votes", "operation", "POST.votes",
                        "outcome", "error", "error_code", "http_409")));
        assertEquals(droppedBefore + 1, count(METERS.find("yoursay.mobile.events.dropped.total").tags("reason", "invalid")));
    }

    @Test
    @TestSecurity(user = "reader@yoursay.com", roles = "user")
    void rejectsABatchWithAMalformedSessionId() {
        given().contentType("application/json")
                .body(new MobileTelemetryBatchDto("not-a-session", new MobileClientDto("ios", "18", "1.0.0"),
                        List.of()))
                .when().post("/telemetry/mobile")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "reader@yoursay.com", roles = "user")
    void acceptsABatchAtTheSizeLimit() {
        List<MobileTelemetryEventDto> events = Collections.nCopies(MobileTelemetryBatchDto.MAX_EVENTS,
                appStart(System.currentTimeMillis()));

        given().contentType("application/json")
                .body(batch("ios", events))
                .when().post("/telemetry/mobile")
                .then()
                .statusCode(202)
                .body("accepted", is(MobileTelemetryBatchDto.MAX_EVENTS));
    }

    @Test
    @TestSecurity(user = "reader@yoursay.com", roles = "user")
    void rejectsAnOversizedBatch() {
        List<MobileTelemetryEventDto> events = Collections.nCopies(MobileTelemetryBatchDto.MAX_EVENTS + 1,
                appStart(System.currentTimeMillis()));

        given().contentType("application/json")
                .body(batch("ios", events))
                .when().post("/telemetry/mobile")
                .then()
                .statusCode(400);
    }

    /** A Google sign-in that fails on the device never gets a Firebase token, so its diagnostics arrive anonymously. */
    @Test
    void acceptsTheSignInFailureOfACallerWhoHasNotSignedIn() {
        long now = System.currentTimeMillis();
        double signInFailuresBefore = count(METERS.find("yoursay.mobile.logs.total")
                .tags("platform", "android", "level", "warn", "log_name", "auth.sign_in_failed"));

        given().contentType("application/json")
                .body(batch("android", List.of(appStart(now),
                        log("warn", "auth.sign_in_failed", null, Map.of("stage", "google", "code", "10"), now + 1))))
                .when().post("/telemetry/mobile")
                .then()
                .statusCode(202)
                .body("accepted", is(2))
                .body("dropped", is(0));

        assertEquals(signInFailuresBefore + 1, count(METERS.find("yoursay.mobile.logs.total")
                .tags("platform", "android", "level", "warn", "log_name", "auth.sign_in_failed")));
    }

    private static MobileTelemetryBatchDto batch(String platform, List<MobileTelemetryEventDto> events) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        return new MobileTelemetryBatchDto(sessionId, new MobileClientDto(platform, "15", "1.4.0"), events);
    }

    private static double count(Search search) {
        return search.counters().stream().mapToDouble(counter -> counter.count()).sum();
    }
}
