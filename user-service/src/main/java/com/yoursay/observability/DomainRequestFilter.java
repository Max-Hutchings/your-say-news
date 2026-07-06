package com.yoursay.observability;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.USER)
public class DomainRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String START_NANOS = "yoursay.startNanos";

    @Inject
    DomainMetrics metrics;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        requestContext.setProperty(START_NANOS, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object start = requestContext.getProperty(START_NANOS);
        long duration = start instanceof Long started ? System.nanoTime() - started : 0L;
        String path = requestContext.getUriInfo().getPath();
        metrics.recordRequest(domainFromPath(path), operationFrom(requestContext.getMethod(), path),
                responseContext.getStatus(), duration);
    }

    static String domainFromPath(String path) {
        if (path == null) {
            return "unknown";
        }
        if (path.startsWith("your-say-user")) {
            return "user";
        }
        if (path.startsWith("user-characteristics")) {
            return "usercharacteristic";
        }
        if (path.startsWith("live") || path.startsWith("q/")) {
            return "platform";
        }
        return "unknown";
    }

    static String operationFrom(String method, String path) {
        String normalized = path == null || path.isBlank() ? "root" : path
                .replaceAll("/\\d+", "/{id}")
                .replaceAll("/[^/]+@[^/]+", "/{email}")
                .replace('/', '.');
        return method + "." + normalized;
    }
}
