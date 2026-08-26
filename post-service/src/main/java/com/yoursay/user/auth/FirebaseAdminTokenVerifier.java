package com.yoursay.user.auth;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.SessionCookieOptions;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

@ApplicationScoped
@IfBuildProfile("dev")
class FirebaseAdminTokenVerifier implements FirebaseTokenVerifier {

    private final FirebaseApp app;
    private final FirebaseAuth auth;

    FirebaseAdminTokenVerifier(@ConfigProperty(name = "firebase.auth.project-id") String projectId) {
        GoogleCredentials emulatorCredentials = GoogleCredentials.create(
                new AccessToken("local-emulator-owner", Date.from(Instant.now().plusSeconds(86_400))));
        FirebaseOptions options = FirebaseOptions.builder()
                .setProjectId(projectId)
                .setCredentials(emulatorCredentials)
                .build();
        app = FirebaseApp.initializeApp(options, "your-say-news-local-auth");
        auth = FirebaseAuth.getInstance(app);
    }

    @Override
    public VerifiedFirebaseIdentity verifyIdToken(String token) {
        try {
            return toIdentity(auth.verifyIdToken(token));
        } catch (FirebaseAuthException exception) {
            throw rejected(exception);
        } catch (RuntimeException exception) {
            throw new FirebaseDependencyException("firebase_verification_failed", exception);
        }
    }

    @Override
    public VerifiedFirebaseIdentity verifySessionCookie(String cookie) {
        try {
            return toIdentity(auth.verifySessionCookie(cookie, true));
        } catch (FirebaseAuthException exception) {
            throw rejected(exception);
        } catch (RuntimeException exception) {
            throw new FirebaseDependencyException("firebase_verification_failed", exception);
        }
    }

    @Override
    public String createSessionCookie(String idToken, long expiresInMillis) {
        try {
            SessionCookieOptions options = SessionCookieOptions.builder()
                    .setExpiresIn(expiresInMillis)
                    .build();
            return auth.createSessionCookie(idToken, options);
        } catch (FirebaseAuthException exception) {
            throw rejected(exception);
        } catch (RuntimeException exception) {
            throw new FirebaseDependencyException("firebase_session_creation_failed", exception);
        }
    }

    private static VerifiedFirebaseIdentity toIdentity(FirebaseToken token) {
        Map<String, Object> claims = token.getClaims();
        String firstName = textClaim(claims, "given_name");
        String lastName = textClaim(claims, "family_name");
        if (firstName == null || lastName == null) {
            String[] displayName = splitDisplayName(textClaim(claims, "name"));
            firstName = firstName == null ? displayName[0] : firstName;
            lastName = lastName == null ? displayName[1] : lastName;
        }
        return new VerifiedFirebaseIdentity(
                token.getUid(),
                token.getEmail(),
                token.isEmailVerified(),
                firstName,
                lastName);
    }

    private static String[] splitDisplayName(String displayName) {
        if (displayName == null) {
            return new String[]{null, null};
        }
        String[] names = displayName.trim().split("\\s+", 2);
        return names.length == 2
                ? names
                : new String[]{names[0], names[0]};
    }

    private static String textClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static FirebaseIdentityRejectedException rejected(FirebaseAuthException exception) {
        String code = exception.getAuthErrorCode() == null
                ? "invalid_firebase_credential"
                : exception.getAuthErrorCode().name().toLowerCase();
        return new FirebaseIdentityRejectedException(code);
    }

    @PreDestroy
    void close() {
        app.delete();
    }
}
