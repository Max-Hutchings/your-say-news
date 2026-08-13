package com.yoursay.observability;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Provider
@Priority(Priorities.USER)
public class DomainRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String START_NANOS = "yoursay.startNanos";

    /** Set by {@link ApiExceptionMapper} when the endpoint contract defines the refusal as normal. */
    static final String EXPECTED_REJECTION = "yoursay.expectedRejection";

    /**
     * Longest path prefix wins, so a nested domain route such as {@code posts/{id}/unwrapped} is
     * attributed to the domain that owns it rather than to the domain that owns its URL root.
     */
    private static final List<DomainRoute> DOMAIN_ROUTES = List.of(
            new DomainRoute("api/admin/unwrapped", "unwrapped"),
            new DomainRoute("api/admin/topic-tags", "topics"),
            new DomainRoute("api/admin/users", "user"),
            new DomainRoute("topic-tags", "topics"),
            new DomainRoute("posts", "posts"),
            new DomainRoute("votes", "votes"),
            new DomainRoute("feed", "feed"),
            new DomainRoute("your-say-user", "user"),
            new DomainRoute("profiles", "user"),
            new DomainRoute("user-characteristics", "usercharacteristic"),
            new DomainRoute("social", "social"),
            new DomainRoute("agent", "postagent"),
            new DomainRoute("live", "platform"),
            new DomainRoute("q/", "platform"));

    /** Post Unwrapped hangs off a post's URL but is its own domain, so it is matched separately. */
    private static final Pattern UNWRAPPED_POST_ROUTE = Pattern.compile("^posts/[^/]+/unwrapped(/.*)?$");

    /**
     * Every literal segment that appears in a route of this service. A segment outside this set is
     * caller-supplied and becomes a placeholder.
     *
     * <p>This is an allowlist on purpose. Recognising identifiers by shape instead would leave the
     * tag unbounded: {@code /votes/{postId}/sentiment/{axis}} takes a free-form axis, and the
     * whitelist check runs inside the controller, after this filter has already read the raw URI.
     * Anything unrecognised - an invalid axis, a probe for {@code /wp-admin/setup-config.php} - would
     * become a new tag value. Collapsing by default means a forgotten literal degrades to
     * {@code {id}} rather than to an unbounded metric.
     */
    private static final Set<String> ROUTE_LITERALS = Set.of(
            "access", "active", "admin", "agent", "api", "approve", "benchmark", "consent",
            "context", "count", "data", "email", "feed", "follow-up", "followers", "following",
            "follows", "generate", "generation-status", "id", "income-options", "jobs", "live",
            "me", "media", "mine", "onboarding", "options", "posts", "presign", "profiles", "q",
            "reject", "review", "save", "sentiment", "social", "topic-tags", "unwrapped", "user",
            "user-characteristics", "users", "votes", "your-say-user");

    private static final String PLACEHOLDER = "{id}";

    @Inject
    DomainMetrics metrics;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        requestContext.setProperty(START_NANOS, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        String path = requestContext.getUriInfo().getPath();
        metrics.recordRequest(domainFromPath(path), operationFrom(requestContext.getMethod(), path),
                responseContext.getStatus(), isExpectedRejection(requestContext),
                elapsedNanos(requestContext));
    }

    private static boolean isExpectedRejection(ContainerRequestContext requestContext) {
        return Boolean.TRUE.equals(requestContext.getProperty(EXPECTED_REJECTION));
    }

    private static long elapsedNanos(ContainerRequestContext requestContext) {
        Object start = requestContext.getProperty(START_NANOS);
        return start instanceof Long started ? System.nanoTime() - started : 0L;
    }

    static String domainFromPath(String path) {
        if (path == null) {
            return "unknown";
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        if (UNWRAPPED_POST_ROUTE.matcher(normalized).matches()) {
            return "unwrapped";
        }
        return DOMAIN_ROUTES.stream()
                .filter(route -> normalized.startsWith(route.prefix()))
                .map(DomainRoute::domain)
                .findFirst()
                .orElse("unknown");
    }

    /**
     * Build a stable, low-cardinality operation name. Only known route literals survive; every other
     * segment is caller-supplied and collapses to a placeholder, so a metric tag can never carry a
     * post id, story id, email address, characteristic axis or scanner probe path.
     */
    static String operationFrom(String method, String path) {
        // A leading slash would otherwise become an empty segment, producing names like "GET..feed".
        String normalized = path == null ? "" : path.replaceAll("^/+", "");
        if (normalized.isBlank()) {
            return method + ".root";
        }
        return method + "." + templateOf(normalized);
    }

    private static String templateOf(String normalizedPath) {
        return Arrays.stream(normalizedPath.split("/"))
                .filter(segment -> !segment.isBlank())
                .map(segment -> ROUTE_LITERALS.contains(segment) ? segment : PLACEHOLDER)
                .collect(Collectors.joining("."));
    }

    private record DomainRoute(String prefix, String domain) {
    }
}
