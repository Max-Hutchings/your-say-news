package com.yoursay.user.auth;

import com.yoursay.user.auth.dto.AdminIdentityDto;
import com.yoursay.user.auth.dto.AdminSessionRequest;
import io.vertx.core.http.HttpServerResponse;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Login and logout are public routes, so their Origin and CSRF check is the only guard against a
 * cross-site page signing an admin in to an attacker's session or out of their own (login CSRF).
 */
class AdminSessionControllerTest {

    private static final String DEV_ORIGIN = "https://dev.yoursaynews.com";
    private static final AdminSessionRequest SIGN_IN = new AdminSessionRequest("firebase-id-token");

    private AdminSessionService sessionService;
    private AdminCookieService cookieService;
    private AdminSessionController controller;

    @BeforeEach
    void setUp() {
        sessionService = mock(AdminSessionService.class);
        cookieService = mock(AdminCookieService.class);
        controller = new AdminSessionController();
        controller.sessionService = sessionService;
        controller.cookieService = cookieService;
        controller.response = mock(HttpServerResponse.class);
        // HashSet accepts contains(null), as SmallRye's injected set does.
        controller.allowedOrigins = new HashSet<>(Set.of(DEV_ORIGIN));
    }

    @Test
    void signInFromTheAllowedOriginWithMatchingTokensIssuesTheSessionCookie() {
        AdminIdentityDto admin = new AdminIdentityDto("m.admin@example.com", "Morgan Admin");
        when(sessionService.create("firebase-id-token")).thenReturn(new CreatedAdminSession("signed-session", admin));

        AdminIdentityDto identity = controller.createSession(SIGN_IN, DEV_ORIGIN, "csrf", "csrf");

        assertEquals(admin, identity);
        verify(cookieService).issueSessionCookie(controller.response, "signed-session");
    }

    @Test
    void signInFromAnotherOriginWithMismatchedOrMissingTokensIssuesNoSession() {
        assertRejected(() -> controller.createSession(SIGN_IN, "https://attacker.example", "csrf", "csrf"));
        assertRejected(() -> controller.createSession(SIGN_IN, null, "csrf", "csrf"));
        assertRejected(() -> controller.createSession(SIGN_IN, DEV_ORIGIN, "csrf", "other"));
        assertRejected(() -> controller.createSession(SIGN_IN, DEV_ORIGIN, null, null));
        assertRejected(() -> controller.createSession(SIGN_IN, DEV_ORIGIN, "csrf", null));

        verify(sessionService, never()).create(anyString());
        verify(cookieService, never()).issueSessionCookie(any(), anyString());
    }

    @Test
    void logoutFromAnotherOriginLeavesTheSessionCookieInPlace() {
        assertRejected(() -> controller.logout("https://attacker.example", "csrf", "csrf"));
        assertRejected(() -> controller.logout(DEV_ORIGIN, "csrf", "other"));

        verify(cookieService, never()).clearSessionCookie(any());

        controller.logout(DEV_ORIGIN, "csrf", "csrf");
        verify(cookieService).clearSessionCookie(controller.response);
    }

    private static void assertRejected(Runnable call) {
        WebApplicationException rejection = assertThrows(WebApplicationException.class, call::run);
        assertEquals(403, rejection.getResponse().getStatus());
    }
}
