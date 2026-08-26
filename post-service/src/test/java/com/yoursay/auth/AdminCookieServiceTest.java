package com.yoursay.user.auth;

import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpServerResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminCookieServiceTest {

    @Test
    void adminSessionCookieIsHttpOnlyStrictAndLimitedToTwelveHours() {
        AdminCookieService service = new AdminCookieService("ysn_admin_session", Duration.ofHours(12));
        HttpServerResponse response = mock(HttpServerResponse.class);

        service.issueSessionCookie(response, "signed-session");

        Cookie cookie = addedCookie(response);
        assertEquals("ysn_admin_session", cookie.getName());
        assertEquals("signed-session", cookie.getValue());
        assertEquals("/", cookie.getPath());
        assertEquals(43_200L, cookie.getMaxAge());
        assertEquals(CookieSameSite.STRICT, cookie.getSameSite());
        assertTrue(cookie.isHttpOnly());
        assertFalse(cookie.isSecure(), "Local HTTP development cannot use a Secure cookie");
    }

    @Test
    void csrfCookieRemainsReadableByTheAdminUi() {
        AdminCookieService service = new AdminCookieService("ysn_admin_session", Duration.ofHours(12));
        HttpServerResponse response = mock(HttpServerResponse.class);

        String csrf = service.issueCsrfCookie(response);

        Cookie cookie = addedCookie(response);
        assertTrue(csrf.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(csrf, cookie.getValue());
        assertEquals(AdminCookieService.CSRF_COOKIE_NAME, cookie.getName());
        assertFalse(cookie.isHttpOnly());
        assertEquals(CookieSameSite.STRICT, cookie.getSameSite());

        HttpServerResponse secondResponse = mock(HttpServerResponse.class);
        assertNotEquals(csrf, service.issueCsrfCookie(secondResponse));
    }

    @Test
    void clearedSessionCookieExpiresImmediatelyAndRemainsHttpOnly() {
        AdminCookieService service = new AdminCookieService("ysn_admin_session", Duration.ofHours(12));
        HttpServerResponse response = mock(HttpServerResponse.class);

        service.clearSessionCookie(response);

        Cookie cookie = addedCookie(response);
        assertEquals("ysn_admin_session", cookie.getName());
        assertEquals("", cookie.getValue());
        assertEquals(0, cookie.getMaxAge());
        assertEquals("/", cookie.getPath());
        assertTrue(cookie.isHttpOnly());
        assertEquals(CookieSameSite.STRICT, cookie.getSameSite());
    }

    private static Cookie addedCookie(HttpServerResponse response) {
        ArgumentCaptor<Cookie> cookie = ArgumentCaptor.forClass(Cookie.class);
        verify(response).addCookie(cookie.capture());
        return cookie.getValue();
    }
}
