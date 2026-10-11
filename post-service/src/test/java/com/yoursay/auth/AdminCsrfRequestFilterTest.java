package com.yoursay.user.auth;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JAX-RS reports the request path with a leading slash ({@code /api/admin/users/11}). The filter
 * must still recognise admin routes in that shape, or every cookie-authenticated admin mutation
 * would run without CSRF protection once the admin UI is reachable on a public hostname.
 */
class AdminCsrfRequestFilterTest {

    private static final String DEV_ORIGIN = "https://dev.yoursaynews.com";

    @Test
    void adminMutationWithoutAMatchingCsrfTokenIsRejected() {
        ContainerRequestContext request = request("PUT", "/api/admin/users/11", DEV_ORIGIN, "header-token", "cookie-token");

        WebApplicationException rejection = assertThrows(WebApplicationException.class, () -> filter().filter(request));

        assertEquals(403, rejection.getResponse().getStatus());
    }

    @Test
    void adminMutationMissingTheCsrfCookieTheHeaderOrTheOriginIsRejected() {
        ContainerRequestContext noCookie = request("PUT", "/api/admin/users/11", DEV_ORIGIN, "token", null);
        ContainerRequestContext noHeader = request("PUT", "/api/admin/users/11", DEV_ORIGIN, null, "token");
        ContainerRequestContext noOrigin = request("PUT", "/api/admin/users/11", null, "token", "token");

        assertEquals(403, assertThrows(WebApplicationException.class, () -> filter().filter(noCookie)).getResponse().getStatus());
        assertEquals(403, assertThrows(WebApplicationException.class, () -> filter().filter(noHeader)).getResponse().getStatus());
        assertEquals(403, assertThrows(WebApplicationException.class, () -> filter().filter(noOrigin)).getResponse().getStatus());
    }

    @Test
    void adminMutationFromAnUnlistedOriginIsRejectedEvenWithMatchingTokens() {
        ContainerRequestContext request = request("POST", "/api/admin/auto-post/runs", "https://attacker.example", "token", "token");

        WebApplicationException rejection = assertThrows(WebApplicationException.class, () -> filter().filter(request));

        assertEquals(403, rejection.getResponse().getStatus());
    }

    @Test
    void adminMutationFromTheAllowedOriginWithMatchingTokensPasses() {
        ContainerRequestContext request = request("POST", "/api/admin/auto-post/runs", DEV_ORIGIN, "token", "token");

        assertDoesNotThrow(() -> filter().filter(request));
    }

    @Test
    void readsAndNonAdminMutationsAreNotCsrfChecked() {
        ContainerRequestContext adminRead = request("GET", "/api/admin/users", null, null, null);
        ContainerRequestContext mobileVote = request("POST", "/votes", null, null, null);

        assertDoesNotThrow(() -> filter().filter(adminRead));
        assertDoesNotThrow(() -> filter().filter(mobileVote));
    }

    private static AdminCsrfRequestFilter filter() {
        AdminCsrfRequestFilter filter = new AdminCsrfRequestFilter();
        // HashSet accepts contains(null), as SmallRye's injected set does, so a missing Origin reaches the check.
        filter.allowedOrigins = new HashSet<>(Set.of(DEV_ORIGIN));
        filter.enforced = true;
        return filter;
    }

    private static ContainerRequestContext request(
            String method,
            String path,
            String origin,
            String csrfHeader,
            String csrfCookie
    ) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn(path);
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(request.getMethod()).thenReturn(method);
        when(request.getHeaderString("Origin")).thenReturn(origin);
        when(request.getHeaderString("X-CSRF-Token")).thenReturn(csrfHeader);
        when(request.getCookies()).thenReturn(csrfCookie == null
                ? Map.of()
                : Map.of(AdminCookieService.CSRF_COOKIE_NAME, new Cookie.Builder(AdminCookieService.CSRF_COOKIE_NAME)
                        .value(csrfCookie).build()));
        return request;
    }
}
