package com.yoursay.user.auth;

import com.yoursay.platform.observability.DomainMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminSessionServiceTest {

    private StubVerifier verifier;
    private StubRoleResolver roles;
    private AdminSessionService service;

    @BeforeEach
    void setUp() {
        verifier = new StubVerifier();
        roles = new StubRoleResolver();
        DomainMetrics metrics = AuthTestMetrics.create(new SimpleMeterRegistry());
        service = new AdminSessionService(verifier, roles, metrics, 43_200_000L);
    }

    @Test
    void createsATwelveHourFirebaseCookieOnlyForAnActiveDatabaseAdministrator() {
        verifier.identity = new VerifiedFirebaseIdentity(
                "firebase-yoursay-admin", "admin@yoursay.com", true, "YourSay", "Admin");
        roles.admin = true;

        CreatedAdminSession session = service.create("fresh-firebase-id-token");

        assertEquals("fresh-firebase-id-token", verifier.lastIdToken);
        assertEquals("admin@yoursay.com", roles.lastEmail);
        assertEquals(43_200_000L, verifier.lastExpiryMillis);
        assertEquals("firebase-session-cookie", session.cookieValue());
        assertEquals("admin@yoursay.com", session.identity().email());
        assertEquals("YourSay Admin", session.identity().name());
    }

    @Test
    void refusesAValidFirebaseIdentityWithoutDatabaseAdminPermission() {
        verifier.identity = new VerifiedFirebaseIdentity(
                "firebase-riley-reader", "riley.reader@example.com", true, "Riley", "Reader");

        assertThrows(ForbiddenException.class, () -> service.create("reader-token"));
        assertEquals(0, verifier.createSessionCookieCalls);
    }

    @Test
    void refusesAnUnverifiedAdministratorBeforeCreatingTheSessionCookie() {
        verifier.identity = new VerifiedFirebaseIdentity(
                "firebase-yoursay-admin", "admin@yoursay.com", false, "YourSay", "Admin");
        roles.admin = true;

        assertThrows(ForbiddenException.class, () -> service.create("unverified-admin-token"));
        assertEquals(0, roles.lookupCalls);
        assertEquals(0, verifier.createSessionCookieCalls);
    }

    private static final class StubVerifier implements FirebaseTokenVerifier {
        private VerifiedFirebaseIdentity identity;
        private String lastIdToken;
        private long lastExpiryMillis;
        private int createSessionCookieCalls;

        @Override
        public VerifiedFirebaseIdentity verifyIdToken(String token) {
            lastIdToken = token;
            return identity;
        }

        @Override
        public VerifiedFirebaseIdentity verifySessionCookie(String cookie) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String createSessionCookie(String idToken, long expiresInMillis) {
            createSessionCookieCalls++;
            lastExpiryMillis = expiresInMillis;
            return "firebase-session-cookie";
        }
    }

    private static final class StubRoleResolver implements FirebaseRoleResolver {
        private boolean admin;
        private String lastEmail;
        private int lookupCalls;

        @Override
        public boolean hasActiveAdminAccess(String email) {
            lastEmail = email;
            lookupCalls++;
            return admin;
        }
    }
}
