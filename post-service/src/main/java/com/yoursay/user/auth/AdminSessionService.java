package com.yoursay.user.auth;

import com.yoursay.platform.observability.DomainMetrics;
import com.yoursay.user.auth.dto.AdminIdentityDto;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

@ApplicationScoped
@IfBuildProfile("dev")
public class AdminSessionService {

    private final FirebaseTokenVerifier verifier;
    private final FirebaseRoleResolver roleResolver;
    private final DomainMetrics metrics;
    private final long sessionDurationMillis;

    @Inject
    AdminSessionService(
            FirebaseTokenVerifier verifier,
            FirebaseRoleResolver roleResolver,
            DomainMetrics metrics,
            @ConfigProperty(name = "firebase.auth.session-duration") Duration sessionDuration
    ) {
        this(verifier, roleResolver, metrics, sessionDuration.toMillis());
    }

    AdminSessionService(
            FirebaseTokenVerifier verifier,
            FirebaseRoleResolver roleResolver,
            DomainMetrics metrics,
            long sessionDurationMillis
    ) {
        this.verifier = verifier;
        this.roleResolver = roleResolver;
        this.metrics = metrics;
        this.sessionDurationMillis = sessionDurationMillis;
    }

    public CreatedAdminSession create(String idToken) {
        long started = System.nanoTime();
        try {
            VerifiedFirebaseIdentity identity = verifier.verifyIdToken(idToken);
            if (!identity.emailVerified() || !roleResolver.hasActiveAdminAccess(identity.email())) {
                metrics.recordOperation("user", "create_admin_session", "error", "authorization",
                        "admin_access_required", System.nanoTime() - started);
                throw new ForbiddenException("Active administrator access is required.");
            }
            String cookie = verifier.createSessionCookie(idToken, sessionDurationMillis);
            AdminIdentityDto adminIdentity = new AdminIdentityDto(
                    identity.email(), displayName(identity));
            metrics.recordOperation("user", "create_admin_session", "success", "none", "none",
                    System.nanoTime() - started);
            return new CreatedAdminSession(cookie, adminIdentity);
        } catch (ForbiddenException exception) {
            throw exception;
        } catch (FirebaseIdentityRejectedException exception) {
            metrics.recordOperation("user", "create_admin_session", "error", "authentication",
                    exception.errorCode(), System.nanoTime() - started);
            throw new ForbiddenException("Firebase identity was rejected.");
        } catch (FirebaseDependencyException exception) {
            metrics.recordOperation("user", "create_admin_session", "fault", "firebase",
                    exception.faultCode(), System.nanoTime() - started);
            throw exception;
        }
    }

    private static String displayName(VerifiedFirebaseIdentity identity) {
        String firstName = identity.firstName() == null ? "" : identity.firstName().trim();
        String lastName = identity.lastName() == null ? "" : identity.lastName().trim();
        String name = (firstName + " " + lastName).trim();
        return name.isBlank() ? identity.email() : name;
    }
}
