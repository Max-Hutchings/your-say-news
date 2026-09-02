package com.yoursay.user.auth;

import com.yoursay.platform.observability.DomainMetrics;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.Principal;

@ApplicationScoped
@IfBuildProfile("dev")
public class FirebaseAuthenticationMechanism implements HttpAuthenticationMechanism {

    private static final String BEARER_PREFIX = "Bearer ";

    private final FirebaseTokenVerifier verifier;
    private final FirebaseRoleResolver roleResolver;
    private final DomainMetrics metrics;
    private final String sessionCookieName;

    @Inject
    FirebaseAuthenticationMechanism(
            FirebaseTokenVerifier verifier,
            FirebaseRoleResolver roleResolver,
            DomainMetrics metrics,
            @ConfigProperty(name = "firebase.auth.session-cookie-name") String sessionCookieName
    ) {
        this.verifier = verifier;
        this.roleResolver = roleResolver;
        this.metrics = metrics;
        this.sessionCookieName = sessionCookieName;
    }

    FirebaseAuthenticationMechanism(
            FirebaseTokenVerifier verifier,
            FirebaseRoleResolver roleResolver,
            DomainMetrics metrics
    ) {
        this(verifier, roleResolver, metrics, "ysn_admin_session");
    }

    @Override
    public Uni<SecurityIdentity> authenticate(
            RoutingContext context,
            IdentityProviderManager identityProviderManager
    ) {
        FirebaseCredential credential = credentialFrom(context);
        if (credential == null) {
            return Uni.createFrom().nullItem();
        }
        return Uni.createFrom().item(() -> authenticateCredential(credential))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    SecurityIdentity authenticateCredential(FirebaseCredential credential) {
        String operation = credential.type() == FirebaseCredential.Type.BEARER
                ? "authenticate_bearer"
                : "authenticate_session";
        long started = System.nanoTime();
        try {
            VerifiedFirebaseIdentity verified = verify(credential);
            requireVerifiedEmail(verified);
            SecurityIdentity identity = createIdentity(verified);
            metrics.recordOperation("user", operation, "success", "none", "none",
                    System.nanoTime() - started);
            return identity;
        } catch (FirebaseIdentityRejectedException | AuthenticationFailedException rejection) {
            String code = rejection instanceof FirebaseIdentityRejectedException firebaseRejection
                    ? firebaseRejection.errorCode()
                    : "firebase_email_not_verified";
            metrics.recordOperation("user", operation, "error", "authentication", code,
                    System.nanoTime() - started);
            Log.warnf("Firebase authentication rejected: domain=user operation=%s outcome=error errorCode=%s",
                    operation, code);
            throw new AuthenticationFailedException();
        } catch (FirebaseDependencyException fault) {
            metrics.recordOperation("user", operation, "fault", "firebase", fault.faultCode(),
                    System.nanoTime() - started);
            Log.errorf(fault, "Firebase authentication fault: domain=user operation=%s outcome=fault faultCode=%s",
                    operation, fault.faultCode());
            throw new AuthenticationFailedException();
        }
    }

    private VerifiedFirebaseIdentity verify(FirebaseCredential credential) {
        return credential.type() == FirebaseCredential.Type.BEARER
                ? verifier.verifyIdToken(credential.value())
                : verifier.verifySessionCookie(credential.value());
    }

    private static void requireVerifiedEmail(VerifiedFirebaseIdentity verified) {
        if (!verified.emailVerified() || verified.email() == null || verified.email().isBlank()) {
            throw new AuthenticationFailedException();
        }
    }

    private SecurityIdentity createIdentity(VerifiedFirebaseIdentity verified) {
        QuarkusSecurityIdentity.Builder identity = QuarkusSecurityIdentity.builder()
                .setPrincipal((Principal) verified::email)
                .addRole("user")
                .addAttribute("firebase_uid", verified.subject())
                .addAttribute("email", verified.email());
        addAttributeIfPresent(identity, "given_name", verified.firstName());
        addAttributeIfPresent(identity, "family_name", verified.lastName());
        if (roleResolver.hasActiveAdminAccess(verified.email())) {
            identity.addRole("admin");
        }
        return identity.build();
    }

    private static void addAttributeIfPresent(
            QuarkusSecurityIdentity.Builder identity,
            String name,
            String value
    ) {
        if (value != null && !value.isBlank()) {
            identity.addAttribute(name, value);
        }
    }

    private FirebaseCredential credentialFrom(RoutingContext context) {
        String authorization = context.request().getHeader("Authorization");
        Cookie cookie = context.request().getCookie(sessionCookieName);
        boolean hasBearer = authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
        boolean hasCookie = cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank();
        if (hasBearer && hasCookie) {
            throw new AuthenticationFailedException();
        }
        if (hasBearer) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            if (token.isBlank()) {
                throw new AuthenticationFailedException();
            }
            return new FirebaseCredential(FirebaseCredential.Type.BEARER, token);
        }
        return hasCookie
                ? new FirebaseCredential(FirebaseCredential.Type.SESSION_COOKIE, cookie.getValue())
                : null;
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(
                401,
                "WWW-Authenticate",
                "Bearer realm=\"your-say-news\""));
    }

    @Override
    public int getPriority() {
        return 2001;
    }
}
