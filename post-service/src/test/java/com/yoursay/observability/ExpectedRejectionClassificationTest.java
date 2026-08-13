package com.yoursay.observability;

import com.yoursay.votes.error.VoteApiException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The mapper decides whether a refusal is the contract working, and the response filter records the
 * metric afterwards. The verdict has to survive the hand-off between them, so this exercises both
 * together rather than either alone - that hand-off is the part that can silently break.
 */
class ExpectedRejectionClassificationTest {

    private SimpleMeterRegistry registry;
    private DomainMetrics metrics;
    private ApiExceptionMapper mapper;
    private DomainRequestFilter filter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new DomainMetrics();
        metrics.registry = registry;
        metrics.environment = "test";
        mapper = new ApiExceptionMapper();
        mapper.metrics = metrics;
        filter = new DomainRequestFilter();
        filter.metrics = metrics;
    }

    @Test
    void keepsLockedResultsOutOfTheErrorCountEvenThoughItIsA403() {
        ContainerRequestContext request = requestContext("GET", "votes/2007/sentiment");

        Response response = handle(request, VoteApiException.resultsLocked(2007L));

        assertEquals(403, response.getStatus());
        assertEquals(1.0, requestCounter("votes", "GET.votes.{id}.sentiment", 403,
                "expected_rejection").count());
        assertNull(registry.find("yoursay.domain.errors.total").tag("domain", "votes").counter(),
                "a contract-defined refusal must not appear in the error rate");
        assertNull(registry.find("yoursay.domain.errors.by_code.total").counter(),
                "an expected rejection is not a top-10 error");
    }

    @Test
    void keepsADuplicateVoteOutOfTheErrorCountEvenThoughItIsA409() {
        ContainerRequestContext request = requestContext("POST", "votes");

        Response response = handle(request, VoteApiException.duplicateVote(2007L, 42L));

        assertEquals(409, response.getStatus());
        assertEquals(1.0, requestCounter("votes", "POST.votes", 409, "expected_rejection").count());
        assertNull(registry.find("yoursay.domain.errors.total").tag("domain", "votes").counter());
    }

    @Test
    void countsAGenuineFailureAsAnErrorAndRecordsItsStableCode() {
        ContainerRequestContext request = requestContext("POST", "votes");

        Response response = handle(request, VoteApiException.postMissing(2007L));

        assertEquals(404, response.getStatus());
        assertEquals(1.0, requestCounter("votes", "POST.votes", 404, "unexpected_client_error").count());
        assertEquals(1.0, registry.find("yoursay.domain.errors.total")
                .tags("domain", "votes", "operation", "POST.votes", "outcome", "unexpected_client_error")
                .counter().count());
        assertEquals(1.0, registry.find("yoursay.domain.errors.by_code.total")
                .tags("domain", "votes", "operation", "api", "error_code", "VOTE_POST_MISSING",
                        "status", "404")
                .counter().count());
    }

    @Test
    void countsAServerFaultAsAnErrorWithoutLeakingItsMessageToTheCaller() {
        ContainerRequestContext request = requestContext("GET", "feed");

        Response response = handle(request, new IllegalStateException("connection to app_db refused"));

        assertEquals(500, response.getStatus());
        assertEquals(1.0, requestCounter("feed", "GET.feed", 500, "server_error").count());
        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertEquals("INTERNAL_ERROR", body.code());
        assertEquals("The request could not be processed.", body.message());
    }

    /** Runs the real mapper, then the real response filter, exactly as a request would. */
    private Response handle(ContainerRequestContext request, Throwable failure) {
        mapper.requestContext = request;
        Response response = mapper.toResponse(failure);
        filter.filter(request, responseContext(response.getStatus()));
        return response;
    }

    private static ContainerRequestContext requestContext(String method, String path) {
        Map<String, Object> properties = new HashMap<>();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn(path);
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getMethod()).thenReturn(method);
        when(context.getUriInfo()).thenReturn(uriInfo);
        when(context.getProperty(anyString())).thenAnswer(call -> properties.get(call.getArgument(0)));
        doAnswer(call -> properties.put(call.getArgument(0), call.getArgument(1)))
                .when(context).setProperty(anyString(), any());
        return context;
    }

    private static ContainerResponseContext responseContext(int status) {
        ContainerResponseContext context = mock(ContainerResponseContext.class);
        when(context.getStatus()).thenReturn(status);
        return context;
    }

    private Counter requestCounter(String domain, String operation, int status, String outcome) {
        return registry.find("yoursay.domain.requests.total")
                .tags("domain", domain, "operation", operation,
                        "status", Integer.toString(status), "outcome", outcome)
                .counter();
    }
}
