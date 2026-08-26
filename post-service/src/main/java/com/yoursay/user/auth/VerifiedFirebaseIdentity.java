package com.yoursay.user.auth;

/** Identity claims accepted only after the configured Firebase project verifies the credential. */
public record VerifiedFirebaseIdentity(
        String subject,
        String email,
        boolean emailVerified,
        String firstName,
        String lastName
) {
}
