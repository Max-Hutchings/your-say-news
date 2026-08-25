import {createJSONStorage, persist} from "zustand/middleware";
import {Platform} from "react-native";
import {create} from "zustand";
import {SessionRestoreResult, User, UserState} from "../types";
import * as SecureStore from "expo-secure-store";
import {KeycloakTokens, loginWithKeycloak, refreshTokens, revokeTokens} from "./keycloakService";
import {getOnboardingStatus, getUser, verifySession} from "./UserService";


const isWeb = Platform.OS === "web";

export const useAuthStore = create(
    persist<UserState>(
        (set) => ({
            _stateHydrated: isWeb,
            isLoggedIn: false,
            hasOnboarded: false,
            hasCharacteristics: false,
            id: null,
            email: null,
            firstName: null,
            lastName: null,
            dateOfBirth: null,
            consentedAt: null,
            accountType: "USER",
            publisherStatus: "NONE",
            canPublish: false,
            accessToken: null,
            refreshToken: null,
            accessTokenExpiresAt: null,

            // ✅ Just reference the helpers, don't reimplement them
            getAccessToken,
            setAccessToken,
            getRefreshToken,
            setRefreshToken,
            accessTokenExpired,
            refreshAccessToken,

            login,
            completeLogin,
            restoreSession,
            logout,
            setHasOnboarded,
            setHasCharacteristics,
            setConsentedAt,
        }),
        {
            name: "auth-store",
            storage: isWeb
                ? createJSONStorage(() => localStorage)
                : createJSONStorage(() => ({
                    setItem: (key: string, value: string) => SecureStore.setItemAsync(key, value),
                    getItem: (key: string) => SecureStore.getItemAsync(key),
                    removeItem: (key: string) => SecureStore.deleteItemAsync(key),
                })),
            merge: (persistedState, currentState) => ({
                ...currentState,
                ...(persistedState as Partial<UserState>),
                _stateHydrated: true,
            }),
            onRehydrateStorage: () => {
                return () => {
                    useAuthStore.setState({ _stateHydrated: true });
                };
            },
        }
    )
);

// Your helpers – still with no implementation as you wanted
function getAccessToken(): string | null  {
    const state = useAuthStore.getState();
    return state.accessToken;
}

function setAccessToken(token: string): void {
    useAuthStore.setState({
        accessToken: token,
    });
}

function getRefreshToken(): string | null {
    const state = useAuthStore.getState();
    return state.refreshToken;
}

function setRefreshToken(token: string): void {
    useAuthStore.setState({
        refreshToken: token,
    });
}

async function login(): Promise<boolean> {
    // Ask KeycloakService to run the PKCE flow and give us tokens
    const tokens: KeycloakTokens | null = await loginWithKeycloak();

    if (!tokens) {
        return false; // user cancelled / error
    }

    return completeLogin(tokens);
}

/**
 * Identity fields reset to their signed-out defaults.
 *
 * Used whenever a new identity takes over or a session ends, so no field of the previous user
 * (their consent, their onboarding flags, their account type) can survive into the next session.
 */
const SIGNED_OUT_IDENTITY = {
    id: null,
    email: null,
    firstName: null,
    lastName: null,
    dateOfBirth: null,
    consentedAt: null,
    accountType: "USER",
    publisherStatus: "NONE",
    canPublish: false,
    isLoggedIn: false,
    hasOnboarded: false,
    hasCharacteristics: false,
} as const;

const SIGNED_OUT_CREDENTIALS = {
    accessToken: null,
    refreshToken: null,
    accessTokenExpiresAt: null,
} as const;

async function completeLogin(tokens: {
    accessToken: string;
    refreshToken: string | null;
    expiresIn: number | null;
}): Promise<boolean> {
    // Drop whoever was signed in before touching the new credentials. Signing in is a change of
    // identity, so the previous user's details must never be left to blend with the new one's.
    useAuthStore.setState({
        ...SIGNED_OUT_IDENTITY,
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
        accessTokenExpiresAt: expiresInToTimestamp(tokens.expiresIn),
    })


    // Fetch user details
    const user: User | null = await getUser()

    if (!user){
        useAuthStore.setState({
            ...SIGNED_OUT_IDENTITY,
            ...SIGNED_OUT_CREDENTIALS,
        });
        return false;
    }


    // Ask the server how far along onboarding they are (consent + characteristic profile). If the
    // status request fails, fail closed: consent alone cannot prove the characteristic profile exists.
    const status = await getOnboardingStatus();

    useAuthStore.setState({
        id: user.id,
        email: user.email,
        firstName: user.firstName,
        lastName: user.lastName,
        dateOfBirth: user.dateOfBirth,
        consentedAt: user.consentedAt,
        accountType: user.accountType,
        publisherStatus: user.publisherStatus,
        canPublish: user.canPublish,
        hasCharacteristics: status?.hasCharacteristics ?? false,
        hasOnboarded: status?.onboarded ?? false,
        isLoggedIn: true,

    });

    return true;
}

/**
 * Decide what a session restored from storage is actually worth, on startup.
 *
 * A persisted session must be one of two things and nothing in between: genuinely signed in, or
 * gone. A stored identity whose token the server no longer accepts is wiped here — store, web
 * storage and cookies — so it can never be handed to whoever opens the app next, and so nobody ever
 * has to clear site data by hand to sign in as someone else.
 *
 * The one case we do not wipe is an unreachable server: that tells us nothing about the session, so
 * throwing it away would sign people out every time the backend hiccups. It is reported as
 * "unverified" and the caller keeps the user out of the app without destroying their credentials.
 */
async function restoreSession(): Promise<SessionRestoreResult> {
    const { accessToken, refreshToken, isLoggedIn } = useAuthStore.getState();

    // Any trace of a session has to be checked. Leftover tokens without an `isLoggedIn` flag (or the
    // reverse) are exactly the half-written state this is meant to clean up, so they must not be
    // waved through as "nothing stored".
    if (!accessToken && !refreshToken && !isLoggedIn) {
        return "signed-out";
    }

    const check = await verifySession();

    if (check.state === "unauthenticated") {
        await logout();
        return "signed-out";
    }

    if (check.state === "unreachable") {
        // Credentials are kept — they may still be good — but an unvouched-for session must not
        // count as being signed in, or the route guards would admit it anyway.
        useAuthStore.setState({ isLoggedIn: false });
        return "unverified";
    }

    const status = await getOnboardingStatus();

    useAuthStore.setState({
        id: check.user.id,
        email: check.user.email,
        firstName: check.user.firstName,
        lastName: check.user.lastName,
        dateOfBirth: check.user.dateOfBirth,
        consentedAt: check.user.consentedAt,
        accountType: check.user.accountType,
        publisherStatus: check.user.publisherStatus,
        canPublish: check.user.canPublish,
        hasCharacteristics: status?.hasCharacteristics ?? false,
        hasOnboarded: status?.onboarded ?? false,
        isLoggedIn: true,
    });

    return "signed-in";
}

/** Convert Keycloak's `expires_in` (seconds from now) to an absolute epoch-ms timestamp. */
function expiresInToTimestamp(expiresIn: number | null): number | null {
    if (expiresIn == null) {
        return null;
    }
    return Date.now() + expiresIn * 1000;
}




async function logout(): Promise<void> {
    // Best-effort server-side revocation before we drop the token locally.
    const { refreshToken } = useAuthStore.getState();
    if (refreshToken) {
        try {
            await revokeTokens(refreshToken);
        } catch {
            // Revocation is best-effort. Local credentials must still be removed.
        }
    }

    useAuthStore.setState({
        id: null,
        email: null,
        firstName: null,
        lastName: null,
        dateOfBirth: null,
        consentedAt: null,
        accountType: "USER",
        publisherStatus: "NONE",
        canPublish: false,
        accessToken: null,
        refreshToken: null,
        accessTokenExpiresAt: null,
        isLoggedIn: false,
        hasOnboarded: false,
        hasCharacteristics: false,
    })

    await wipeSession();
}

/**
 * Wipe every trace of the session from the device/browser after logout. Drops the persisted
 * auth store (localStorage on web, SecureStore on native) and, on web, clears local/session
 * storage and any JS-readable cookies for this origin so no identity survives the sign-out.
 */
async function wipeSession(): Promise<void> {
    try {
        await useAuthStore.persist.clearStorage();
    } catch {
        // Ignore — a missing storage entry is fine, we only care that nothing remains.
    }

    if (isWeb && typeof window !== "undefined") {
        try {
            window.localStorage.clear();
            window.sessionStorage.clear();
            document.cookie.split(";").forEach((cookie) => {
                const name = cookie.split("=")[0]?.trim();
                if (name) {
                    document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/`;
                }
            });
        } catch {
            // Storage/cookies may be unavailable (e.g. privacy mode); best effort only.
        }
    }
}

function setConsentedAt(at: string | null): void {
    useAuthStore.setState({ consentedAt: at });
}

function setHasCharacteristics(has: boolean): void {
    useAuthStore.setState({ hasCharacteristics: has });
}

// Dedupe concurrent refreshes so a burst of requests triggers a single token exchange.
let refreshInFlight: Promise<string | null> | null = null;

/**
 * Exchange the stored refresh token for a fresh access token, updating the store.
 * Returns the new access token, or null (and logs out) if the refresh fails.
 */
async function refreshAccessToken(): Promise<string | null> {
    if (refreshInFlight) {
        return refreshInFlight;
    }

    refreshInFlight = (async () => {
        const { refreshToken } = useAuthStore.getState();
        if (!refreshToken) {
            await logout();
            return null;
        }

        const tokens = await refreshTokens(refreshToken);
        if (!tokens || !tokens.accessToken) {
            await logout();
            return null;
        }

        useAuthStore.setState({
            accessToken: tokens.accessToken,
            refreshToken: tokens.refreshToken,
            accessTokenExpiresAt: expiresInToTimestamp(tokens.expiresIn),
        });
        return tokens.accessToken;
    })();

    try {
        return await refreshInFlight;
    } finally {
        refreshInFlight = null;
    }
}

/**
 * True when there is no access token, no known expiry, or the expiry has passed.
 * A 30s skew guards against treating a token that is about to expire as still valid.
 */
function accessTokenExpired(): boolean {
    const { accessToken, accessTokenExpiresAt } = useAuthStore.getState();
    if (!accessToken || accessTokenExpiresAt == null) {
        return true;
    }
    const skewMs = 30_000;
    return Date.now() >= accessTokenExpiresAt - skewMs;
}


function setHasOnboarded(onboarded: boolean): void{
     useAuthStore.setState({
        hasOnboarded: onboarded
    })
}
