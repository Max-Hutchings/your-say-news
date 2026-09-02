package com.yoursay.user.auth;

public interface FirebaseTokenVerifier {

    VerifiedFirebaseIdentity verifyIdToken(String token);

    VerifiedFirebaseIdentity verifySessionCookie(String cookie);

    String createSessionCookie(String idToken, long expiresInMillis);
}
