package com.yoursay.user.auth;

import java.util.Locale;

/** Identity claims accepted only after the configured Firebase project verifies the credential. */
public record VerifiedFirebaseIdentity(
        String subject,
        String email,
        boolean emailVerified,
        String firstName,
        String lastName
) {

    /**
     * Emails are stored lowercase (migration 0018), so every lookup, deactivation check and new
     * account uses the same form regardless of how the provider capitalised it.
     */
    public VerifiedFirebaseIdentity {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
