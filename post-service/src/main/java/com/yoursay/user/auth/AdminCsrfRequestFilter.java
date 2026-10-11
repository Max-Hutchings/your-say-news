package com.yoursay.user.auth;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Set;

/** Registered with JAX-RS through @Provider; without it the filter is never invoked. */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHORIZATION + 10)
public class AdminCsrfRequestFilter implements ContainerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @ConfigProperty(name = "firebase.auth.allowed-origins")
    Set<String> allowedOrigins;

    /** Always true outside %test, where @TestSecurity admin tests send no browser CSRF tokens. */
    @ConfigProperty(name = "firebase.auth.csrf-enforced")
    boolean enforced;

    @Override
    public void filter(ContainerRequestContext request) {
        if (!enforced || !isAdminApiPath(request.getUriInfo().getPath()) || SAFE_METHODS.contains(request.getMethod())) {
            return;
        }

        String origin = request.getHeaderString("Origin");
        String csrfHeader = request.getHeaderString("X-CSRF-Token");
        Cookie csrfCookie = request.getCookies().get(AdminCookieService.CSRF_COOKIE_NAME);
        if (!allowedOrigins.contains(origin)
                || csrfHeader == null
                || csrfCookie == null
                || !csrfHeader.equals(csrfCookie.getValue())) {
            throw new WebApplicationException("CSRF validation failed.", 403);
        }
    }

    /** JAX-RS reports the path with a leading slash, e.g. {@code /api/admin/users/11}. */
    private static boolean isAdminApiPath(String path) {
        return path.replaceFirst("^/+", "").startsWith("api/admin/");
    }
}
