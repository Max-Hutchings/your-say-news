package com.yoursay.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DomainMetricsTest {

    private SimpleMeterRegistry registry;
    private DomainMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new DomainMetrics();
        metrics.registry = registry;
        metrics.environment = "test";
    }

    @Test
    void recordsSuccessfulOperationCountAndDuration() {
        metrics.recordOperation(
                "usercharacteristic", "activate_profile",
                "success", "none", "none", 25_000_000L);

        assertEquals(1.0, operationCounter("success", "none").count());
        assertEquals("none", operationCounter("success", "none").getId().getTag("error_code"));
        assertEquals("test", operationCounter("success", "none").getId().getTag("environment"));
        assertEquals(1.0, successCounter("success", "none").count());
        Timer timer = registry.find("yoursay.domain.operation.duration")
                .tags("domain", "usercharacteristic", "operation", "activate_profile",
                        "outcome", "success", "error_type", "none")
                .timer();
        assertEquals(1, timer.count());
        assertEquals(25.0, timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test
    void recordsExpectedRejectionWithoutCountingItAsAnError() {
        metrics.recordOperation(
                "usercharacteristic", "resolve_income_display",
                "expected_rejection", "not_found", "income_range_not_found", 5_000_000L);

        assertEquals(1.0, registry.find("yoursay.domain.operations.total")
                .tags("domain", "usercharacteristic", "operation", "resolve_income_display",
                        "outcome", "expected_rejection", "error_type", "not_found",
                        "error_code", "income_range_not_found")
                .counter().count());
        assertEquals(0.0, registry.find("yoursay.domain.success.total")
                .tags("domain", "usercharacteristic", "operation", "resolve_income_display",
                        "outcome", "expected_rejection", "error_type", "not_found",
                        "error_code", "income_range_not_found")
                .counter().count());
        assertNull(registry.find("yoursay.domain.errors.total")
                .tags("domain", "usercharacteristic", "operation", "resolve_income_display",
                        "outcome", "expected_rejection", "error_type", "not_found")
                .counter());
    }

    @Test
    void recordsDependencyFailureWithItsNormalisedErrorType() {
        metrics.recordOperation(
                "usercharacteristic", "activate_profile",
                "dependency_error", "database", "income_profile_database_failure", 10_000_000L);

        assertEquals(1.0, operationCounter("dependency_error", "database").count());
        assertEquals(0.0, successCounter("dependency_error", "database").count());
        Counter errors = registry.find("yoursay.domain.errors.total")
                .tags("domain", "usercharacteristic", "operation", "activate_profile",
                        "outcome", "dependency_error", "error_type", "database",
                        "error_code", "income_profile_database_failure")
                .counter();
        assertEquals(1.0, errors.count());
    }

    @Test
    void classifiesRequestsUsingStatusAndTheEndpointContract() {
        metrics.recordRequest("usercharacteristic", "GET.catalog", 200, 2_000_000L);
        metrics.recordRequest("usercharacteristic", "POST.answers", 400, 3_000_000L);
        metrics.recordRequest(
                "usercharacteristic", "GET.profile", 404, true, 4_000_000L);
        metrics.recordRequest("usercharacteristic", "GET.catalog", 503, 5_000_000L);

        assertEquals(1.0, requestCounter("GET.catalog", 200, "success").count());
        assertEquals(1.0, requestCounter("POST.answers", 400, "unexpected_client_error").count());
        assertEquals(1.0, requestCounter("GET.profile", 404, "expected_rejection").count());
        assertEquals(1.0, requestCounter("GET.catalog", 503, "server_error").count());
        assertEquals(2.0, registry.find("yoursay.domain.errors.total")
                .tag("domain", "usercharacteristic")
                .counters().stream().mapToDouble(Counter::count).sum());
    }

    @Test
    void tagsRequestsWithABoundedErrorCodeAndRecordsTheirDuration() {
        metrics.recordRequest("votes", "GET.votes.{id}.count", 200, 7_000_000L);
        metrics.recordRequest("votes", "POST.votes", 409, true, 3_000_000L);

        assertEquals("none", requestCounter("votes", "GET.votes.{id}.count", 200, "success")
                .getId().getTag("error_code"));
        assertEquals("http_409", requestCounter("votes", "POST.votes", 409, "expected_rejection")
                .getId().getTag("error_code"));
        assertEquals("client", requestCounter("votes", "POST.votes", 409, "expected_rejection")
                .getId().getTag("error_type"));

        // Throughput mirrors requests, and is what the traffic panels read.
        assertEquals(1.0, registry.find("yoursay.domain.throughput.total")
                .tags("domain", "votes", "operation", "GET.votes.{id}.count", "outcome", "success")
                .counter().count());

        Timer timer = registry.find("yoursay.domain.request.duration")
                .tags("domain", "votes", "operation", "GET.votes.{id}.count", "outcome", "success")
                .timer();
        assertEquals(1, timer.count());
        assertEquals(7.0, timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    private Counter requestCounter(String domain, String operation, int status, String outcome) {
        return registry.find("yoursay.domain.requests.total")
                .tags("domain", domain, "operation", operation,
                        "status", Integer.toString(status), "outcome", outcome)
                .counter();
    }

    @Test
    void recordsLegacyBooleanOperationsWithoutLosingFailureClassification() {
        metrics.recordOperation("user", "save_profile", true);
        metrics.recordOperation("user", "save_profile", false);

        assertEquals(1.0, registry.find("yoursay.domain.operations.total")
                .tags("domain", "user", "operation", "save_profile", "outcome", "success")
                .counter().count());
        assertEquals(1.0, registry.find("yoursay.domain.operations.total")
                .tags("domain", "user", "operation", "save_profile", "outcome", "service_error")
                .counter().count());
        assertEquals(1.0, registry.find("yoursay.domain.errors.total")
                .tags("domain", "user", "operation", "save_profile", "outcome", "service_error")
                .counter().count());
    }

    @Test
    void recordsJobDurationAndCountsAFailedJobAsAnError() {
        metrics.recordJob("unwrapped", "generation", true, 40_000_000L);
        metrics.recordJob("unwrapped", "generation", false, 12_000_000L);

        Timer succeeded = registry.find("yoursay.domain.job.duration")
                .tags("domain", "unwrapped", "operation", "generation", "outcome", "success")
                .timer();
        assertEquals(1, succeeded.count());
        assertEquals(40.0, succeeded.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));

        Timer failed = registry.find("yoursay.domain.job.duration")
                .tags("domain", "unwrapped", "operation", "generation", "outcome", "job_error")
                .timer();
        assertEquals(1, failed.count());
        assertEquals(12.0, failed.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));

        assertEquals(1.0, jobCounter("yoursay.domain.operations.total", "success", "none", "none").count());
        assertEquals(1.0, jobCounter("yoursay.domain.operations.total", "job_error", "internal", "job_failed").count());

        // The success counter drives every success-rate panel, so both branches are pinned.
        assertEquals(1.0, jobCounter("yoursay.domain.success.total", "success", "none", "none").count());
        assertEquals(0.0, jobCounter("yoursay.domain.success.total", "job_error", "internal", "job_failed").count());

        // A job never returns an HTTP status, so its failure only counts as an error here.
        assertEquals(1.0, jobCounter("yoursay.domain.errors.total", "job_error", "internal", "job_failed").count());
        assertNull(registry.find("yoursay.domain.errors.total")
                .tags("domain", "unwrapped", "operation", "generation", "outcome", "success")
                .counter());
    }

    @Test
    void marksAFailedInternalOperationWithADifferentErrorCodeFromAFailedJob() {
        metrics.recordOperation("feed", "rank", false);
        metrics.recordJob("feed", "rank", false, 1_000_000L);

        assertEquals(1.0, registry.find("yoursay.domain.errors.total")
                .tags("domain", "feed", "operation", "rank",
                        "outcome", "service_error", "error_code", "operation_failed")
                .counter().count());
        assertEquals(1.0, registry.find("yoursay.domain.errors.total")
                .tags("domain", "feed", "operation", "rank",
                        "outcome", "job_error", "error_code", "job_failed")
                .counter().count());
    }

    private Counter jobCounter(String name, String outcome, String errorType, String errorCode) {
        return registry.find(name)
                .tags("domain", "unwrapped", "operation", "generation", "outcome", outcome,
                        "error_type", errorType, "error_code", errorCode, "environment", "test")
                .counter();
    }

    @Test
    void recordsStableApiErrorCodeDimensions() {
        metrics.recordError("usercharacteristic", "api", "INCOME_PROFILE_UNKNOWN", 400);

        Counter counter = registry.find("yoursay.domain.errors.by_code.total")
                .tags("domain", "usercharacteristic", "operation", "api",
                        "error_code", "INCOME_PROFILE_UNKNOWN", "status", "400",
                        "outcome", "unexpected_client_error", "error_type", "client",
                        "environment", "test")
                .counter();
        assertEquals(1.0, counter.count());
    }

    private Counter operationCounter(String outcome, String errorType) {
        return registry.find("yoursay.domain.operations.total")
                .tags("domain", "usercharacteristic", "operation", "activate_profile",
                        "outcome", outcome, "error_type", errorType)
                .counter();
    }

    private Counter successCounter(String outcome, String errorType) {
        return registry.find("yoursay.domain.success.total")
                .tags("domain", "usercharacteristic", "operation", "activate_profile",
                        "outcome", outcome, "error_type", errorType)
                .counter();
    }

    private Counter requestCounter(String operation, int status, String outcome) {
        return registry.find("yoursay.domain.requests.total")
                .tags("domain", "usercharacteristic", "operation", operation,
                        "status", Integer.toString(status), "outcome", outcome)
                .counter();
    }
}
