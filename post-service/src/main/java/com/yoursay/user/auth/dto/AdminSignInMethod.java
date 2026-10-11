package com.yoursay.user.auth.dto;

/** How the admin SPA signs in to Firebase: emulator email/password on a laptop, Google when hosted. */
public enum AdminSignInMethod {
    PASSWORD,
    GOOGLE
}
