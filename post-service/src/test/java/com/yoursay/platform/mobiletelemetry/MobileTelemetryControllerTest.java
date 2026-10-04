package com.yoursay.platform.mobiletelemetry;

import com.yoursay.platform.mobiletelemetry.dto.MobileClientDto;
import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryBatchDto;
import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryEventDto;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.yoursay.platform.mobiletelemetry.MobileTelemetryFixtures.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The relay end to end: auth, validation and the Prometheus counters the Mobile dashboard reads.
 * Each test uses its own platform/screen labels' delta, because the registry is shared by the suite.
 */
@QuarkusTest
class MobileTelemetryControllerTest {

    @Inject
    MeterRegistry registry;

    @Test
    @TestSecurity(user = "reader@yoursay.com", roles = "user")
    void recordsAJourneyAndDropsTheInvalidEvent() {
        long now = System.currentTimeMillis();
        double screenViewsBefore = count(registry.find("yoursay.mobile.screen.views.total")
                .tags("platform", "android", "screen", "/posts/[postId]"));
        double votesBefore = count(registry.find("yoursay.mobile.actions.total")
                .tags("platform", "android", "action", "vote.cast"));
        double conflictsBefore = count(registry.find("yoursay.mobile.operations.total")
                .tags("platform", "android", "domain", "votes", "operation", "POST.votes",
                        "outcome", "error", "error_code", "http_409"));
        double droppedBefore = count(registry.find("yoursay.mobile.events.dropped.total"));

        given().contentType("application/json")
                .body(batch("android", List.of(
                        screenEnter("/posts/[postId]", "42", now),
                        action("/posts/[postId]", "vote.cast", "42", now + 5),
                        apiCall("POST", "/votes", 409, null, now + 6, now + 90),
                        new MobileTelemetryEventDto("keystroke", now, null, "/", null, null, null, null,
                                null, null, null, null, null, null, null, null, null))))
                .when().post("/telemetry/mobile")
                .then()
                .statusCode(202)
                .body("accepted", is(3))
                .body("dropped", is(1));

        assertEquals(screenViewsBefore + 1, count(registry.find("yoursay.mobile.screen.views.total")
                .tags("platform", "android", "screen", "/posts/[postId]")));
        assertEquals(votesBefore + 1, count(registry.find("yoursay.mobile.actions.total")
                .tags("platform", "android", "action", "vote.cast")));
        assertEquals(conflictsBefore + 1, count(registry.find("yoursay.mobile.operations.total")
                .tags("platform", "android", "domain", "votes", "operation", "POST.votes",
                        "outcome", "error", "error_code", "http_409")));
        assertEquals(droppedBefore + 1, count(registry.find("yoursay.mobile.events.dropped.total")));
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

    @Test
    void rejectsAnAnonymousCaller() {
        given().contentType("application/json")
                .body(batch("ios", List.of(appStart(System.currentTimeMillis()))))
                .when().post("/telemetry/mobile")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void rejectsACallerWithoutTheUserRole() {
        given().contentType("application/json")
                .body(batch("ios", List.of(appStart(System.currentTimeMillis()))))
                .when().post("/telemetry/mobile")
                .then()
                .statusCode(403);
    }

    private static MobileTelemetryBatchDto batch(String platform, List<MobileTelemetryEventDto> events) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        return new MobileTelemetryBatchDto(sessionId, new MobileClientDto(platform, "15", "1.4.0"), events);
    }

    private static double count(Search search) {
        return search.counters().stream().mapToDouble(counter -> counter.count()).sum();
    }
}
