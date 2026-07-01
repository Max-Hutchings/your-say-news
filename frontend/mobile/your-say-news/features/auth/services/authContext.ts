import {createJSONStorage, persist} from "zustand/middleware";
import {Platform} from "react-native";
import {create} from "zustand";
import {User, UserState} from "../types";
import * as SecureStore from "expo-secure-store";
import {KeycloakTokens, loginWithKeycloak, refreshTokens, revokeTokens} from "./keycloakService";
import {getUser} from "./UserService";


const isWeb = Platform.OS === "web";

export const useAuthStore = create(
    persist<UserState>(
        (set) => ({
            _stateHydrated: isWeb,
            isLoggedIn: false,
            hasOnboarded: false,
            id: null,
            email: null,
            firstName: null,
            lastName: null,
            dateOfBirth: null,
            consentedAt: null,
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
            logout,
            setHasOnboarded,
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

async function completeLogin(tokens: {
    accessToken: string;
    refreshToken: string | null;
    expiresIn: number | null;
}): Promise<boolean> {
    useAuthStore.setState({
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
        accessTokenExpiresAt: expiresInToTimestamp(tokens.expiresIn),
    })


    // Fetch user details
    const user: User | null = await getUser()

    if (!user){
        useAuthStore.setState({
            accessToken: null,
            refreshToken: null,
            accessTokenExpiresAt: null,
            isLoggedIn: false,
        });
        return false;
    }


    // Once we have tokens, update your Zustand state. A returning user who has already consented to
    // the privacy promise is treated as past onboarding, so they land straight on the feed.
    useAuthStore.setState({
        id: user.id,
        email: user.email,
        firstName: user.firstName,
        lastName: user.lastName,
        dateOfBirth: user.dateOfBirth,
        consentedAt: user.consentedAt,
        hasOnboarded: !!user.consentedAt,
        isLoggedIn: true,

    });

    return true;
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
        await revokeTokens(refreshToken);
    }

    useAuthStore.setState({
        id: null,
        email: null,
        firstName: null,
        lastName: null,
        dateOfBirth: null,
        consentedAt: null,
        accessToken: null,
        refreshToken: null,
        accessTokenExpiresAt: null,
        isLoggedIn: false,
        hasOnboarded: false,
    })
}

function setConsentedAt(at: string | null): void {
    useAuthStore.setState({ consentedAt: at });
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
