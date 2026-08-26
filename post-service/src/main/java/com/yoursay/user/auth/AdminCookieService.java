package com.yoursay.user.auth;

import io.quarkus.arc.profile.IfBuildProfile;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpServerResponse;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@ApplicationScoped
@IfBuildProfile("dev")
public class AdminCookieService {

    static final String CSRF_COOKIE_NAME = "ysn_admin_csrf";

    private final SecureRandom random = new SecureRandom();
    private final String sessionCookieName;
    private final long maxAgeSeconds;

    AdminCookieService(
            @ConfigProperty(name = "firebase.auth.session-cookie-name") String sessionCookieName,
            @ConfigProperty(name = "firebase.auth.session-duration") Duration sessionDuration
    ) {
        this.sessionCookieName = sessionCookieName;
        this.maxAgeSeconds = sessionDuration.toSeconds();
    }

    public String issueCsrfCookie(HttpServerResponse response) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        response.addCookie(cookie(CSRF_COOKIE_NAME, token, false, maxAgeSeconds));
        return token;
    }

    public void issueSessionCookie(HttpServerResponse response, String value) {
        response.addCookie(cookie(sessionCookieName, value, true, maxAgeSeconds));
    }

    public void clearSessionCookie(HttpServerResponse response) {
        response.addCookie(cookie(sessionCookieName, "", true, 0));
    }

    private static Cookie cookie(String name, String value, boolean httpOnly, long maxAgeSeconds) {
        return Cookie.cookie(name, value)
                .setPath("/")
                .setHttpOnly(httpOnly)
                .setSecure(false)
                .setSameSite(CookieSameSite.STRICT)
                .setMaxAge(maxAgeSeconds);
    }
}
