package com.yoursay.observability;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.List;
import java.util.regex.Pattern;

@Provider
@Priority(Priorities.USER)
public class DomainRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String START_NANOS = "yoursay.startNanos";

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

    private static final Pattern NUMERIC_SEGMENT = Pattern.compile("/\\d+");
    private static final Pattern UUID_SEGMENT = Pattern.compile(
            "/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern EMAIL_SEGMENT = Pattern.compile("/[^/]+@[^/]+");
    /** Topic ids are caller-supplied slugs, so the segment after {@code topic-tags} is collapsed too. */
    private static final Pattern TOPIC_SLUG_SEGMENT = Pattern.compile("(topic-tags)/[^/]+");

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
                responseContext.getStatus(), elapsedNanos(requestContext));
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
     * Build a stable, low-cardinality operation name. Every identifier-shaped segment collapses to
     * a placeholder so a metric tag never carries a post id, story id or email address.
     */
    static String operationFrom(String method, String path) {
        if (path == null || path.isBlank()) {
            return method + ".root";
        }
        String template = UUID_SEGMENT.matcher(path).replaceAll("/{id}");
        template = NUMERIC_SEGMENT.matcher(template).replaceAll("/{id}");
        template = EMAIL_SEGMENT.matcher(template).replaceAll("/{email}");
        template = TOPIC_SLUG_SEGMENT.matcher(template).replaceAll("$1/{id}");
        return method + "." + template.replace('/', '.');
    }

    private record DomainRoute(String prefix, String domain) {
    }
}
