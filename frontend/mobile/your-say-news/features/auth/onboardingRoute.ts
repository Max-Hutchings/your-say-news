/**
 * Where an authenticated user belongs in the onboarding journey.
 *
 * Pure decision logic, kept out of the route files so it can be tested directly and so the
 * `(protected)` index and the characteristics wizard can never drift apart on the rules.
 *
 * The ordering rule that matters: consent is asked for on its own. A user who already has a
 * characteristic profile but has not consented goes to the consent page and then straight to the
 * feed — we already hold their answers, so the wizard must never run again. Seeded accounts
 * created before consent existed are exactly this case.
 */
export type OnboardingDestination = "consent" | "characteristics" | "feed" | "checking";

export type OnboardingProgress = {
    /** Server timestamp of the privacy-promise agreement; null until they consent. */
    consentedAt: string | null;
    /** Whether a characteristic profile exists. Fails closed to false, so it alone cannot send
     *  someone to the wizard — see `serverConfirmed`. */
    hasCharacteristics: boolean;
    /** Whether `hasCharacteristics` reflects a completed status call rather than a stale or
     *  failed-closed default. */
    serverConfirmed: boolean;
};

export function resolveOnboardingDestination({
    consentedAt,
    hasCharacteristics,
    serverConfirmed,
}: OnboardingProgress): OnboardingDestination {
    if (!consentedAt) {
        return "consent";
    }

    if (hasCharacteristics) {
        return "feed";
    }

    // Only send someone through the wizard once the server has actually confirmed there is no
    // profile. Re-asking a user for answers we already hold is worse than a moment of waiting.
    return serverConfirmed ? "characteristics" : "checking";
}
