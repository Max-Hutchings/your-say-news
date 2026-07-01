/**
 * Auth feature — public face.
 *
 * Routes and other features import auth ONLY from here, never from the
 * internal services/ or hooks/ folders.
 */

export { useAuthStore } from "./services/authContext";
export {
    completeKeycloakWebRedirectFromUrl,
    exchangeKeycloakCodeAsync,
    startKeycloakWebRedirect,
    useKeycloakAuthRequest,
} from "./services/keycloakService";
export { default as YsnHttpClient } from "./services/requests";
export { recordConsent, PRIVACY_POLICY_VERSION } from "./services/ConsentService";
export { PrivacyConsentScreen } from "./components/PrivacyConsentScreen";
export type { User, UserState } from "./types";
