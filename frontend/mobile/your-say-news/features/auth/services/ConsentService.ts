import Constants from "expo-constants";
import YsnHttpClient from "./requests";

/**
 * Records the user's explicit consent to the privacy promise.
 *
 * The body carries only the policy version; the identity is the bearer token. Consent is stored on
 * the user record (user-service), never beside the characteristic data it governs.
 */
const extra = Constants.expoConfig?.extra ?? {};
const CONSENT_URL = `${extra.USER_SERVICE_HOST}${extra.USER_SERVICE_PORT}/your-say-user/consent`;

/** The privacy-policy version the user is consenting to. Bump when the promise materially changes. */
export const PRIVACY_POLICY_VERSION = "2026-06-01";

/** Records consent and returns the server-stamped consent timestamp (ISO string), or null on failure. */
export async function recordConsent(): Promise<string | null> {
    const res = await YsnHttpClient.getSecure().post(CONSENT_URL, {
        privacyPolicyVersion: PRIVACY_POLICY_VERSION,
    });
    return res.data?.consentedAt ?? null;
}
