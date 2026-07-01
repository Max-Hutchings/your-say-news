// KeycloakService.ts
import * as AuthSession from "expo-auth-session";
import {DiscoveryDocument} from "expo-auth-session";
import Constants from "expo-constants";
import { useMemo } from "react";

// --- Keycloak config (driven by app.config.<env>.js via expoConfig.extra) ---
const extra = Constants.expoConfig?.extra ?? {};
const KEYCLOAK_BASE_URL: string = extra.KEYCLOAK_BASE_URL;
const KEYCLOAK_REALM: string = extra.KEYCLOAK_REALM;
const KEYCLOAK_ISSUER = `${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}`; // OIDC issuer
const KEYCLOAK_CLIENT_ID: string = extra.KEYCLOAK_CLIENT_ID;

// Scopes: `offline_access` is what gives you a refresh token in Keycloak
const KEYCLOAK_SCOPES = ["openid", "profile", "email", "offline_access"];
const WEB_AUTH_SESSION_KEY = "ysn-keycloak-auth-session";

// What we’ll return to the caller
export type KeycloakTokens = {
    accessToken: string;
    refreshToken: string | null;
    idToken: string | null;
    expiresIn: number | null;
};

type StoredWebAuthSession = {
    codeVerifier: string;
    redirectUri: string;
    state: string;
};

function makeKeycloakRedirectUri(): string {
    return AuthSession.makeRedirectUri({
        // You can add a path if you want (also put that in Keycloak):
        // path: "auth/callback",
    });
}

function makeKeycloakAuthRequestConfig(redirectUri: string): AuthSession.AuthRequestConfig {
    return {
        clientId: KEYCLOAK_CLIENT_ID,
        redirectUri,
        responseType: AuthSession.ResponseType.Code,
        scopes: KEYCLOAK_SCOPES,
        usePKCE: true,
        prompt: AuthSession.Prompt.Login,
    };
}

export function useKeycloakAuthRequest() {
    const redirectUri = useMemo(() => makeKeycloakRedirectUri(), []);
    const discovery = AuthSession.useAutoDiscovery(KEYCLOAK_ISSUER);
    const authRequestConfig = useMemo(
        () => makeKeycloakAuthRequestConfig(redirectUri),
        [redirectUri],
    );
    const [request, result, promptAsync] = AuthSession.useAuthRequest(
        authRequestConfig,
        discovery,
    );

    return {
        discovery,
        promptAsync,
        redirectUri,
        request,
        result,
        ready: !!discovery && !!request,
    };
}

export async function exchangeKeycloakCodeAsync(
    authResult: AuthSession.AuthSessionResult,
    request: AuthSession.AuthRequest,
    discovery: DiscoveryDocument,
    redirectUri: string,
): Promise<KeycloakTokens | null> {
    if (authResult.type !== "success" || !authResult.params.code) {
        return null;
    }

    const tokenResponse = await AuthSession.exchangeCodeAsync(
        {
            clientId: KEYCLOAK_CLIENT_ID,
            code: authResult.params.code as string,
            redirectUri,
            extraParams: {
                code_verifier: request.codeVerifier ?? "",
            },
        },
        discovery,
    );

    return {
        accessToken: tokenResponse.accessToken ?? "",
        refreshToken: tokenResponse.refreshToken ?? null,
        idToken: tokenResponse.idToken ?? null,
        expiresIn: tokenResponse.expiresIn ?? null,
    };
}

export function startKeycloakWebRedirect(
    request: AuthSession.AuthRequest,
    redirectUri: string,
): boolean {
    if (typeof window === "undefined" || !request.url || !request.codeVerifier) {
        return false;
    }

    const session: StoredWebAuthSession = {
        codeVerifier: request.codeVerifier,
        redirectUri,
        state: request.state,
    };

    window.sessionStorage.setItem(WEB_AUTH_SESSION_KEY, JSON.stringify(session));
    window.location.assign(request.url);
    return true;
}

export async function completeKeycloakWebRedirectFromUrl(
    currentUrl: string,
): Promise<KeycloakTokens | null> {
    if (typeof window === "undefined") {
        return null;
    }

    const url = new URL(currentUrl);
    const code = url.searchParams.get("code");
    const state = url.searchParams.get("state");

    if (!code || !state) {
        return null;
    }

    const rawSession = window.sessionStorage.getItem(WEB_AUTH_SESSION_KEY);
    if (!rawSession) {
        return null;
    }

    const session = JSON.parse(rawSession) as StoredWebAuthSession;
    if (session.state !== state) {
        window.sessionStorage.removeItem(WEB_AUTH_SESSION_KEY);
        return null;
    }

    const discovery: DiscoveryDocument = await AuthSession.fetchDiscoveryAsync(KEYCLOAK_ISSUER);
    const tokenResponse = await AuthSession.exchangeCodeAsync(
        {
            clientId: KEYCLOAK_CLIENT_ID,
            code,
            redirectUri: session.redirectUri,
            extraParams: {
                code_verifier: session.codeVerifier,
            },
        },
        discovery,
    );

    window.sessionStorage.removeItem(WEB_AUTH_SESSION_KEY);

    return {
        accessToken: tokenResponse.accessToken ?? "",
        refreshToken: tokenResponse.refreshToken ?? null,
        idToken: tokenResponse.idToken ?? null,
        expiresIn: tokenResponse.expiresIn ?? null,
    };
}

// Main function you’ll call from your login flow / Zustand store
export async function loginWithKeycloak(): Promise<KeycloakTokens | null> {
    // 1) Discover OIDC endpoints (auth, token, revocation...) from Keycloak
    //    This uses the standard .well-known/openid-configuration under the hood
    const discovery: DiscoveryDocument = await AuthSession.fetchDiscoveryAsync(KEYCLOAK_ISSUER);

    // 2) Build redirect URI back into your app
    //    IMPORTANT: whatever this resolves to must be added as a "Valid Redirect URI" in Keycloak.
    //    For dev builds you can just start with `makeRedirectUri()` and then copy the value into Keycloak.
    const redirectUri: string = makeKeycloakRedirectUri();

    // 3) Build an authorization request that uses PKCE (usePKCE: true)
    const authRequestConfig = makeKeycloakAuthRequestConfig(redirectUri);

    // Create the low-level AuthRequest instance so we can later read `codeVerifier`
    const request = new AuthSession.AuthRequest(authRequestConfig);

    // 4) Open the browser and let the user log in to Keycloak
    //    This will redirect to Keycloak, then back to your app via `redirectUri`.
    const authResult = await request.promptAsync(discovery);
    return exchangeKeycloakCodeAsync(authResult, request, discovery, redirectUri);
}

/**
 * Exchange a refresh token for a fresh access token at Keycloak's token endpoint.
 * Returns null if the refresh token is rejected (e.g. expired / revoked).
 */
export async function refreshTokens(
    refreshToken: string,
): Promise<KeycloakTokens | null> {
    try {
        const discovery: DiscoveryDocument = await AuthSession.fetchDiscoveryAsync(KEYCLOAK_ISSUER);

        const tokenResponse = await AuthSession.refreshAsync(
            {
                clientId: KEYCLOAK_CLIENT_ID,
                refreshToken,
            },
            discovery,
        );

        return {
            accessToken: tokenResponse.accessToken ?? "",
            // Keycloak rotates refresh tokens; fall back to the existing one if absent.
            refreshToken: tokenResponse.refreshToken ?? refreshToken,
            idToken: tokenResponse.idToken ?? null,
            expiresIn: tokenResponse.expiresIn ?? null,
        };
    } catch {
        return null;
    }
}

/**
 * Best-effort revocation of the refresh token at Keycloak, ending the server-side
 * session on logout. Failures are swallowed — local state is cleared regardless.
 */
export async function revokeTokens(refreshToken: string): Promise<void> {
    try {
        const discovery: DiscoveryDocument = await AuthSession.fetchDiscoveryAsync(KEYCLOAK_ISSUER);

        await AuthSession.revokeAsync(
            {
                clientId: KEYCLOAK_CLIENT_ID,
                token: refreshToken,
                tokenTypeHint: AuthSession.TokenTypeHint.RefreshToken,
            },
            discovery,
        );
    } catch {
        // best-effort; ignore
    }
}
