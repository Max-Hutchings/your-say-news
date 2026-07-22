package com.yoursay.user.user;

/**
 * Whether the authenticated user has finished onboarding — i.e. both agreed to the privacy promise
 * and filled out their characteristic profile. The client routes on {@link #onboarded()}; the two
 * parts are exposed so it can send a partially-onboarded user to the right step.
 */
public record OnboardingStatusDto(
        boolean consented,
        boolean hasCharacteristics,
        boolean onboarded
) {
}
