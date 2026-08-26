package com.yoursay.user.auth;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Set;

@ApplicationScoped
@Priority(Priorities.AUTHORIZATION + 10)
@IfBuildProfile("dev")
public class AdminCsrfRequestFilter implements ContainerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @ConfigProperty(name = "firebase.auth.allowed-origins")
    Set<String> allowedOrigins;

    @Override
    public void filter(ContainerRequestContext request) {
        String path = request.getUriInfo().getPath();
        if (!path.startsWith("api/admin/") || SAFE_METHODS.contains(request.getMethod())) {
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
}
