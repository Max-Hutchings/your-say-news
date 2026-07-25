import { renderHook } from "@testing-library/react-native";
import * as AuthSession from "expo-auth-session";
import {
  completeKeycloakWebRedirectFromUrl,
  exchangeKeycloakCodeAsync,
  loginWithKeycloak,
  refreshTokens,
  revokeTokens,
  startKeycloakWebRedirect,
  useKeycloakAuthRequest,
} from "./keycloakService";

jest.mock("expo-constants", () => ({
  __esModule: true,
  default: {
    expoConfig: {
      extra: {
        KEYCLOAK_BASE_URL: "https://identity.example",
        KEYCLOAK_REALM: "readers",
        KEYCLOAK_CLIENT_ID: "your-say-mobile",
      },
    },
  },
}));

jest.mock("expo-auth-session", () => ({
  AuthRequest: jest.fn(),
  Prompt: { Login: "login" },
  ResponseType: { Code: "code" },
  TokenTypeHint: { RefreshToken: "refresh_token" },
  exchangeCodeAsync: jest.fn(),
  fetchDiscoveryAsync: jest.fn(),
  makeRedirectUri: jest.fn(() => "yoursay://auth"),
  refreshAsync: jest.fn(),
  revokeAsync: jest.fn(),
  useAutoDiscovery: jest.fn(),
  useAuthRequest: jest.fn(),
}));

const discovery = {
  authorizationEndpoint: "https://identity.example/auth",
  tokenEndpoint: "https://identity.example/token",
};
const request = {
  codeVerifier: "pkce-verifier",
  promptAsync: jest.fn(),
  state: "expected-state",
  url: "https://identity.example/auth?client_id=your-say-mobile",
};

beforeEach(() => {
  jest.clearAllMocks();
  sessionStorage.clear();
  (AuthSession.fetchDiscoveryAsync as jest.Mock).mockResolvedValue(discovery);
});

test("the auth hook exposes a PKCE request and only becomes ready after discovery", () => {
  const promptAsync = jest.fn();
  (AuthSession.useAutoDiscovery as jest.Mock).mockReturnValue(discovery);
  (AuthSession.useAuthRequest as jest.Mock).mockReturnValue([
    request,
    { type: "success", params: { code: "code-1" } },
    promptAsync,
  ]);

  const { result } = renderHook(() => useKeycloakAuthRequest());

  expect(AuthSession.makeRedirectUri).toHaveBeenCalledWith({});
  expect(AuthSession.useAutoDiscovery).toHaveBeenCalledWith(
    "https://identity.example/realms/readers",
  );
  expect(AuthSession.useAuthRequest).toHaveBeenCalledWith(
    {
      clientId: "your-say-mobile",
      prompt: "login",
      redirectUri: "yoursay://auth",
      responseType: "code",
      scopes: ["openid", "profile", "email", "offline_access"],
      usePKCE: true,
    },
    discovery,
  );
  expect(result.current).toMatchObject({
    discovery,
    promptAsync,
    redirectUri: "yoursay://auth",
    request,
    ready: true,
  });
});

test("the auth hook remains unready when either discovery or request is absent", () => {
  (AuthSession.useAutoDiscovery as jest.Mock).mockReturnValueOnce(null);
  (AuthSession.useAuthRequest as jest.Mock).mockReturnValueOnce([
    request,
    null,
    jest.fn(),
  ]);
  const withoutDiscovery = renderHook(() => useKeycloakAuthRequest());
  expect(withoutDiscovery.result.current.ready).toBe(false);
  withoutDiscovery.unmount();

  (AuthSession.useAutoDiscovery as jest.Mock).mockReturnValueOnce(discovery);
  (AuthSession.useAuthRequest as jest.Mock).mockReturnValueOnce([
    null,
    null,
    jest.fn(),
  ]);
  const withoutRequest = renderHook(() => useKeycloakAuthRequest());
  expect(withoutRequest.result.current.ready).toBe(false);
});

test("code exchange ignores cancelled and code-less authorization results", async () => {
  await expect(
    exchangeKeycloakCodeAsync({ type: "cancel" } as never, request as never, discovery, "yoursay://auth"),
  ).resolves.toBeNull();
  await expect(
    exchangeKeycloakCodeAsync(
      { type: "success", params: {} } as never,
      request as never,
      discovery,
      "yoursay://auth",
    ),
  ).resolves.toBeNull();
  expect(AuthSession.exchangeCodeAsync).not.toHaveBeenCalled();
});

test("code exchange sends the verifier and normalizes optional token fields", async () => {
  (AuthSession.exchangeCodeAsync as jest.Mock).mockResolvedValue({
    accessToken: "access-1",
  });

  await expect(
    exchangeKeycloakCodeAsync(
      { type: "success", params: { code: "authorization-code" } } as never,
      request as never,
      discovery,
      "yoursay://auth",
    ),
  ).resolves.toEqual({
    accessToken: "access-1",
    refreshToken: null,
    idToken: null,
    expiresIn: null,
  });
  expect(AuthSession.exchangeCodeAsync).toHaveBeenCalledWith(
    {
      clientId: "your-say-mobile",
      code: "authorization-code",
      redirectUri: "yoursay://auth",
      extraParams: { code_verifier: "pkce-verifier" },
    },
    discovery,
  );
});

test("web redirect stores the verifier and sends the browser to Keycloak", () => {
  const assign = jest.fn();
  Object.defineProperty(window, "location", {
    configurable: true,
    value: { assign },
  });

  expect(startKeycloakWebRedirect(request as never, "https://app.example/callback")).toBe(true);
  expect(JSON.parse(sessionStorage.getItem("ysn-keycloak-auth-session")!)).toEqual({
    codeVerifier: "pkce-verifier",
    redirectUri: "https://app.example/callback",
    state: "expected-state",
  });
  expect(assign).toHaveBeenCalledWith(request.url);
});

test("web redirect refuses an incomplete authorization request", () => {
  expect(
    startKeycloakWebRedirect({ ...request, codeVerifier: undefined } as never, "https://app.example"),
  ).toBe(false);
  expect(sessionStorage.getItem("ysn-keycloak-auth-session")).toBeNull();
});

test("web callback rejects missing or mismatched state and clears a bad session", async () => {
  sessionStorage.setItem(
    "ysn-keycloak-auth-session",
    JSON.stringify({
      codeVerifier: "pkce-verifier",
      redirectUri: "https://app.example/callback",
      state: "expected-state",
    }),
  );

  await expect(
    completeKeycloakWebRedirectFromUrl("https://app.example/callback?code=auth-code"),
  ).resolves.toBeNull();
  await expect(
    completeKeycloakWebRedirectFromUrl(
      "https://app.example/callback?code=auth-code&state=attacker-state",
    ),
  ).resolves.toBeNull();
  expect(sessionStorage.getItem("ysn-keycloak-auth-session")).toBeNull();
  expect(AuthSession.exchangeCodeAsync).not.toHaveBeenCalled();
});

test("web callback exchanges a valid stored session and consumes it", async () => {
  sessionStorage.setItem(
    "ysn-keycloak-auth-session",
    JSON.stringify({
      codeVerifier: "stored-verifier",
      redirectUri: "https://app.example/callback",
      state: "expected-state",
    }),
  );
  (AuthSession.exchangeCodeAsync as jest.Mock).mockResolvedValue({
    accessToken: "web-access",
    refreshToken: "web-refresh",
    idToken: "web-id",
    expiresIn: 300,
  });

  await expect(
    completeKeycloakWebRedirectFromUrl(
      "https://app.example/callback?code=web-code&state=expected-state",
    ),
  ).resolves.toEqual({
    accessToken: "web-access",
    refreshToken: "web-refresh",
    idToken: "web-id",
    expiresIn: 300,
  });
  expect(AuthSession.exchangeCodeAsync).toHaveBeenCalledWith(
    {
      clientId: "your-say-mobile",
      code: "web-code",
      redirectUri: "https://app.example/callback",
      extraParams: { code_verifier: "stored-verifier" },
    },
    discovery,
  );
  expect(sessionStorage.getItem("ysn-keycloak-auth-session")).toBeNull();
});

test("native login builds a PKCE request and exchanges the returned code", async () => {
  request.promptAsync.mockResolvedValue({
    type: "success",
    params: { code: "native-code" },
  });
  (AuthSession.AuthRequest as unknown as jest.Mock).mockImplementation(() => request);
  (AuthSession.exchangeCodeAsync as jest.Mock).mockResolvedValue({
    accessToken: "native-access",
    refreshToken: "native-refresh",
  });

  await expect(loginWithKeycloak()).resolves.toEqual({
    accessToken: "native-access",
    refreshToken: "native-refresh",
    idToken: null,
    expiresIn: null,
  });
  expect(AuthSession.AuthRequest).toHaveBeenCalledWith({
    clientId: "your-say-mobile",
    prompt: "login",
    redirectUri: "yoursay://auth",
    responseType: "code",
    scopes: ["openid", "profile", "email", "offline_access"],
    usePKCE: true,
  });
  expect(request.promptAsync).toHaveBeenCalledWith(discovery);
});

test("refresh preserves a rotated token and returns null when Keycloak rejects it", async () => {
  (AuthSession.refreshAsync as jest.Mock).mockResolvedValueOnce({
    accessToken: "fresh-access",
    refreshToken: "rotated-refresh",
    expiresIn: 900,
  });

  await expect(refreshTokens("existing-refresh")).resolves.toEqual({
    accessToken: "fresh-access",
    refreshToken: "rotated-refresh",
    idToken: null,
    expiresIn: 900,
  });
  expect(AuthSession.refreshAsync).toHaveBeenCalledWith(
    { clientId: "your-say-mobile", refreshToken: "existing-refresh" },
    discovery,
  );

  (AuthSession.refreshAsync as jest.Mock).mockRejectedValueOnce(new Error("revoked"));
  await expect(refreshTokens("revoked-refresh")).resolves.toBeNull();
});

test("revocation sends the refresh-token hint and remains best effort", async () => {
  await revokeTokens("refresh-to-revoke");
  expect(AuthSession.revokeAsync).toHaveBeenCalledWith(
    {
      clientId: "your-say-mobile",
      token: "refresh-to-revoke",
      tokenTypeHint: "refresh_token",
    },
    discovery,
  );

  (AuthSession.fetchDiscoveryAsync as jest.Mock).mockRejectedValueOnce(new Error("offline"));
  await expect(revokeTokens("refresh-while-offline")).resolves.toBeUndefined();
});
