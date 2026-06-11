// KeycloakService.ts
import * as AuthSession from "expo-auth-session";
import * as WebBrowser from "expo-web-browser";
import {DiscoveryDocument} from "expo-auth-session";

// Make sure the browser popup gets closed correctly on web
WebBrowser.maybeCompleteAuthSession();

// --- Keycloak config (adjust if you move off localhost) ---
const KEYCLOAK_ISSUER = "http://localhost:8080/realms/your-say-news"; // OIDC issuer
const KEYCLOAK_CLIENT_ID = "frontend-client";

// Scopes: `offline_access` is what gives you a refresh token in Keycloak
const KEYCLOAK_SCOPES = ["openid", "profile", "email", "offline_access"];

// What we’ll return to the caller
export type KeycloakTokens = {
    accessToken: string;
    refreshToken: string | null;
    idToken: string | null;
    expiresIn: number | null;
};

// Main function you’ll call from your login flow / Zustand store
export async function loginWithKeycloak(): Promise<KeycloakTokens | null> {
    // 1) Discover OIDC endpoints (auth, token, revocation...) from Keycloak
    //    This uses the standard .well-known/openid-configuration under the hood
    const discovery: DiscoveryDocument = await AuthSession.fetchDiscoveryAsync(KEYCLOAK_ISSUER);

    // 2) Build redirect URI back into your app
    //    IMPORTANT: whatever this resolves to must be added as a "Valid Redirect URI" in Keycloak.
    //    For dev builds you can just start with `makeRedirectUri()` and then copy the value into Keycloak.
    const redirectUri: string = AuthSession.makeRedirectUri({
        // You can add a path if you want (also put that in Keycloak):
        // path: "auth/callback",
    });

    console.log("Redict uri: " + redirectUri);

    // 3) Build an authorization request that uses PKCE (usePKCE: true)
    const authRequestConfig: AuthSession.AuthRequestConfig = {
        clientId: KEYCLOAK_CLIENT_ID,
        redirectUri,
        responseType: AuthSession.ResponseType.Code, // "code" for Authorization Code flow
        scopes: KEYCLOAK_SCOPES,
        usePKCE: true, // ✅ This tells AuthSession to generate code_verifier / code_challenge (S256)
        prompt: AuthSession.Prompt.Login, // Always show login UI; adjust if you want "select_account" etc.
        // You can also set `state` here for CSRF protection if you want
    };

    console.log("Auth Request config: " + authRequestConfig);

    // Create the low-level AuthRequest instance so we can later read `codeVerifier`
    const request = new AuthSession.AuthRequest(authRequestConfig);

    console.log("Request: "  + request.clientId);

    // 4) Open the browser and let the user log in to Keycloak
    //    This will redirect to Keycloak, then back to your app via `redirectUri`.
    const authResult = await request.promptAsync(discovery);

    console.log("Auth Result: " + authResult.type + " . " + authResult.params.code);

    // If the user cancelled or something went wrong, bail out cleanly
    if (authResult.type !== "success" || !authResult.params.code) {
        return null;
    }

    const authorizationCode = authResult.params.code as string;

    // 5) Exchange the authorization code for tokens at the token endpoint
    //    PKCE: we pass `code_verifier` that matches the `code_challenge` AuthSession generated.
    const tokenResponse = await AuthSession.exchangeCodeAsync(
        {
            clientId: KEYCLOAK_CLIENT_ID,
            code: authorizationCode,
            redirectUri,
            // `request.codeVerifier` was generated automatically when `usePKCE: true` was set
            extraParams: {
                code_verifier: request.codeVerifier ?? "",
            },
        },
        discovery, // contains the `tokenEndpoint` from Keycloak
    );

    console.log("Access token response: " + tokenResponse.accessToken + "   " + tokenResponse.refreshToken);

    // 6) Normalise into our own shape
    return {
        accessToken: tokenResponse.accessToken ?? "",
        refreshToken: tokenResponse.refreshToken ?? null,
        idToken: tokenResponse.idToken ?? null,
        expiresIn: tokenResponse.expiresIn ?? null,
    };
}
