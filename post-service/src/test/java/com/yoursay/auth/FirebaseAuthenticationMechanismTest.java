package com.yoursay.user.auth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirebaseAuthenticationMechanismTest {

    private StubFirebaseTokenVerifier verifier;
    private StubFirebaseRoleResolver roleResolver;
    private SimpleMeterRegistry registry;
    private FirebaseAuthenticationMechanism mechanism;

    @BeforeEach
    void setUp() {
        verifier = new StubFirebaseTokenVerifier();
        roleResolver = new StubFirebaseRoleResolver();
        registry = new SimpleMeterRegistry();
        mechanism = new FirebaseAuthenticationMechanism(
                verifier,
                roleResolver,
                AuthTestMetrics.create(registry));
    }

    @Test
    void bearerTokenCreatesAUserIdentityFromVerifiedFirebaseClaims() {
        verifier.identity = new VerifiedFirebaseIdentity(
                "firebase-riley-reader",
                "riley.reader@example.com",
                true,
                "Riley",
                "Reader");

        SecurityIdentity identity = mechanism.authenticateCredential(
                new FirebaseCredential(FirebaseCredential.Type.BEARER, "signed-id-token"));

        assertEquals("signed-id-token", verifier.lastIdToken);
        assertEquals("riley.reader@example.com", identity.getPrincipal().getName());
        assertEquals("firebase-riley-reader", identity.getAttribute("firebase_uid"));
        assertEquals("Riley", identity.getAttribute("given_name"));
        assertEquals("Reader", identity.getAttribute("family_name"));
        assertEquals(Set.of("user"), identity.getRoles());
        assertEquals(1.0, operationCount("authenticate_bearer", "success"));
    }

    @Test
    void sessionCookieUsesTheCookieVerifierAndDatabaseAdminRole() {
        verifier.identity = new VerifiedFirebaseIdentity(
                "firebase-yoursay-admin",
                "admin@yoursay.com",
                true,
                "YourSay",
                "Admin");
        roleResolver.admin = true;

        SecurityIdentity identity = mechanism.authenticateCredential(
                new FirebaseCredential(FirebaseCredential.Type.SESSION_COOKIE, "signed-session-cookie"));

        assertEquals("signed-session-cookie", verifier.lastSessionCookie);
        assertEquals(Set.of("user", "admin"), identity.getRoles());
        assertEquals("admin@yoursay.com", roleResolver.lastEmail);
        assertEquals(1.0, operationCount("authenticate_session", "success"));
    }

    @Test
    void unverifiedEmailIsRejectedWithoutLeakingTheIdentityIntoMetrics() {
        verifier.identity = new VerifiedFirebaseIdentity(
                "firebase-unverified",
                "unverified@example.com",
                false,
                "Unverified",
                "Person");

        assertThrows(AuthenticationFailedException.class, () -> mechanism.authenticateCredential(
                new FirebaseCredential(FirebaseCredential.Type.BEARER, "unverified-token")));

        assertEquals(1.0, operationCount("authenticate_bearer", "error"));
        assertFalse(registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .anyMatch(tag -> tag.getValue().contains("unverified@example.com")));
    }

    @Test
    void rejectedAndUnavailableFirebaseCallsHaveDifferentOutcomes() {
        verifier.failure = new FirebaseIdentityRejectedException("expired_token");
        assertThrows(AuthenticationFailedException.class, () -> mechanism.authenticateCredential(
                new FirebaseCredential(FirebaseCredential.Type.BEARER, "expired-token")));
        assertEquals(1.0, operationCount("authenticate_bearer", "error"));

        verifier.failure = new FirebaseDependencyException("firebase_unavailable");
        assertThrows(AuthenticationFailedException.class, () -> mechanism.authenticateCredential(
                new FirebaseCredential(FirebaseCredential.Type.BEARER, "unavailable-token")));
        assertEquals(1.0, operationCount("authenticate_bearer", "fault"));
    }

    private double operationCount(String operation, String outcome) {
        Counter counter = registry.find("yoursay.domain.operations.total")
                .tags("domain", "user", "operation", operation, "outcome", outcome)
                .counter();
        assertTrue(counter != null, () -> "Missing operation metric for " + operation + "/" + outcome);
        return counter.count();
    }

    private static final class StubFirebaseTokenVerifier implements FirebaseTokenVerifier {
        private VerifiedFirebaseIdentity identity;
        private RuntimeException failure;
        private String lastIdToken;
        private String lastSessionCookie;

        @Override
        public VerifiedFirebaseIdentity verifyIdToken(String token) {
            lastIdToken = token;
            if (failure != null) {
                throw failure;
            }
            return identity;
        }

        @Override
        public VerifiedFirebaseIdentity verifySessionCookie(String cookie) {
            lastSessionCookie = cookie;
            if (failure != null) {
                throw failure;
            }
            return identity;
        }

        @Override
        public String createSessionCookie(String idToken, long expiresInMillis) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubFirebaseRoleResolver implements FirebaseRoleResolver {
        private boolean admin;
        private String lastEmail;

        @Override
        public boolean hasActiveAdminAccess(String email) {
            lastEmail = email;
            return admin;
        }
    }
}
