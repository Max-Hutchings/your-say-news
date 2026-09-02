/**
 * Auth feature — public face.
 *
 * Routes and other features import auth ONLY from here, never from the
 * internal services/ or hooks/ folders.
 */

export { useAuthStore } from "./services/authContext";
export { getFirebaseIdToken } from "./services/firebaseService";
export { default as YsnHttpClient } from "./services/requests";
export { getOnboardingStatus } from "./services/UserService";
export { resolveOnboardingDestination } from "./onboardingRoute";
export type { OnboardingDestination, OnboardingProgress } from "./onboardingRoute";
export { recordConsent, PRIVACY_POLICY_VERSION } from "./services/ConsentService";
export { PrivacyConsentScreen } from "./components/PrivacyConsentScreen";
export type { OnboardingStatus, SessionRestoreResult, User, UserState } from "./types";
