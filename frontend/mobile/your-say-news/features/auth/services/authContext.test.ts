jest.mock("expo-secure-store", () => ({
    getItemAsync: jest.fn(async () => null),
    setItemAsync: jest.fn(async () => undefined),
    deleteItemAsync: jest.fn(async () => undefined),
}));

jest.mock("./keycloakService", () => ({
    loginWithKeycloak: jest.fn(),
    refreshTokens: jest.fn(),
    revokeTokens: jest.fn(async () => undefined),
}));

jest.mock("./UserService", () => ({
    getUser: jest.fn(),
    getOnboardingStatus: jest.fn(),
}));

import { useAuthStore } from "./authContext";
import { loginWithKeycloak, refreshTokens, revokeTokens } from "./keycloakService";
import { getOnboardingStatus, getUser } from "./UserService";
import * as SecureStore from "expo-secure-store";

const mockLogin = loginWithKeycloak as jest.Mock;
const mockRefresh = refreshTokens as jest.Mock;
const mockRevoke = revokeTokens as jest.Mock;
const mockGetUser = getUser as jest.Mock;
const mockGetOnboardingStatus = getOnboardingStatus as jest.Mock;
const mockDeleteItem = SecureStore.deleteItemAsync as jest.Mock;

const HOUR = 60 * 60 * 1000;

beforeEach(() => {
    jest.clearAllMocks();
    useAuthStore.setState({
        id: null,
        email: null,
        firstName: null,
        lastName: null,
        dateOfBirth: null,
        isLoggedIn: false,
        hasOnboarded: false,
        hasCharacteristics: false,
        accessToken: null,
        refreshToken: null,
        accessTokenExpiresAt: null,
    });
});

describe("accessTokenExpired", () => {
    it("is expired when there is no token", () => {
        expect(useAuthStore.getState().accessTokenExpired()).toBe(true);
    });

    it("is not expired for a token with a comfortably future expiry", () => {
        useAuthStore.setState({ accessToken: "tok", accessTokenExpiresAt: Date.now() + HOUR });
        expect(useAuthStore.getState().accessTokenExpired()).toBe(false);
    });

    it("is expired once the expiry has passed", () => {
        useAuthStore.setState({ accessToken: "tok", accessTokenExpiresAt: Date.now() - 1000 });
        expect(useAuthStore.getState().accessTokenExpired()).toBe(true);
    });

    it("treats a token inside the 30s skew window as expired", () => {
        useAuthStore.setState({ accessToken: "tok", accessTokenExpiresAt: Date.now() + 10_000 });
        expect(useAuthStore.getState().accessTokenExpired()).toBe(true);
    });
});

describe("login", () => {
    it("stores tokens, expiry and user details on success", async () => {
        mockLogin.mockResolvedValue({
            accessToken: "access-1",
            refreshToken: "refresh-1",
            idToken: null,
            expiresIn: 300,
        });
        mockGetUser.mockResolvedValue({
            id: 7,
            email: "ada@example.com",
            firstName: "Ada",
            lastName: "Lovelace",
            dateOfBirth: "1990-05-21",
            consentedAt: "2026-06-01T00:00:00Z",
        });
        mockGetOnboardingStatus.mockResolvedValue({
            consented: true,
            hasCharacteristics: true,
            onboarded: true,
        });

        const before = Date.now();
        const ok = await useAuthStore.getState().login();
        const state = useAuthStore.getState();

        expect(ok).toBe(true);
        expect(state.isLoggedIn).toBe(true);
        expect(state.accessToken).toBe("access-1");
        expect(state.refreshToken).toBe("refresh-1");
        expect(state.email).toBe("ada@example.com");
        expect(state.id).toBe(7);
        expect(state.hasCharacteristics).toBe(true);
        expect(state.hasOnboarded).toBe(true);
        // 300s expiry recorded as an absolute timestamp.
        expect(state.accessTokenExpiresAt).toBeGreaterThanOrEqual(before + 300_000);
        expect(state.accessTokenExpiresAt).toBeLessThanOrEqual(Date.now() + 300_000);
    });

    it("marks a consented user who has no characteristic profile as not onboarded", async () => {
        mockLogin.mockResolvedValue({
            accessToken: "access-1",
            refreshToken: "refresh-1",
            idToken: null,
            expiresIn: 300,
        });
        mockGetUser.mockResolvedValue({
            id: 1,
            email: "john.doe@example.com",
            firstName: "John",
            lastName: "Doe",
            dateOfBirth: "1990-05-15",
            consentedAt: null,
        });
        // John has a saved profile but has never consented — the server reports him not onboarded.
        mockGetOnboardingStatus.mockResolvedValue({
            consented: false,
            hasCharacteristics: true,
            onboarded: false,
        });

        await useAuthStore.getState().login();
        const state = useAuthStore.getState();

        expect(state.hasCharacteristics).toBe(true);
        expect(state.hasOnboarded).toBe(false);
    });

    it("falls back to the consent flag when the onboarding status call fails", async () => {
        mockLogin.mockResolvedValue({
            accessToken: "access-1",
            refreshToken: "refresh-1",
            idToken: null,
            expiresIn: 300,
        });
        mockGetUser.mockResolvedValue({
            id: 7,
            email: "ada@example.com",
            firstName: "Ada",
            lastName: "Lovelace",
            dateOfBirth: "1990-05-21",
            consentedAt: "2026-06-01T00:00:00Z",
        });
        mockGetOnboardingStatus.mockResolvedValue(null);

        await useAuthStore.getState().login();
        const state = useAuthStore.getState();

        expect(state.hasCharacteristics).toBe(false);
        expect(state.hasOnboarded).toBe(true); // fell back to consentedAt
    });

    it("returns false and stays logged out when the user cancels", async () => {
        mockLogin.mockResolvedValue(null);

        const ok = await useAuthStore.getState().login();

        expect(ok).toBe(false);
        expect(useAuthStore.getState().isLoggedIn).toBe(false);
        expect(mockGetUser).not.toHaveBeenCalled();
    });

    it("returns false when user details cannot be fetched", async () => {
        mockLogin.mockResolvedValue({
            accessToken: "access-1",
            refreshToken: "refresh-1",
            idToken: null,
            expiresIn: 300,
        });
        mockGetUser.mockResolvedValue(null);

        const ok = await useAuthStore.getState().login();

        expect(ok).toBe(false);
        expect(useAuthStore.getState().isLoggedIn).toBe(false);
    });
});

describe("refreshAccessToken", () => {
    it("exchanges the refresh token and updates the stored tokens", async () => {
        useAuthStore.setState({ refreshToken: "refresh-old", accessToken: "stale" });
        mockRefresh.mockResolvedValue({
            accessToken: "access-new",
            refreshToken: "refresh-new",
            idToken: null,
            expiresIn: 300,
        });

        const token = await useAuthStore.getState().refreshAccessToken();
        const state = useAuthStore.getState();

        expect(token).toBe("access-new");
        expect(state.accessToken).toBe("access-new");
        expect(state.refreshToken).toBe("refresh-new");
        expect(mockRefresh).toHaveBeenCalledWith("refresh-old");
    });

    it("logs out when no refresh token is present", async () => {
        useAuthStore.setState({ refreshToken: null, isLoggedIn: true });

        const token = await useAuthStore.getState().refreshAccessToken();

        expect(token).toBeNull();
        expect(useAuthStore.getState().isLoggedIn).toBe(false);
        expect(mockRefresh).not.toHaveBeenCalled();
    });

    it("logs out when the refresh is rejected", async () => {
        useAuthStore.setState({ refreshToken: "refresh-old", isLoggedIn: true, accessToken: "stale" });
        mockRefresh.mockResolvedValue(null);

        const token = await useAuthStore.getState().refreshAccessToken();
        const state = useAuthStore.getState();

        expect(token).toBeNull();
        expect(state.isLoggedIn).toBe(false);
        expect(state.accessToken).toBeNull();
    });
});

describe("logout", () => {
    it("revokes the refresh token and clears all identity + token state", async () => {
        useAuthStore.setState({
            id: 7,
            email: "ada@example.com",
            isLoggedIn: true,
            hasOnboarded: true,
            accessToken: "access-1",
            refreshToken: "refresh-1",
            accessTokenExpiresAt: Date.now() + HOUR,
        });

        await useAuthStore.getState().logout();
        const state = useAuthStore.getState();

        expect(mockRevoke).toHaveBeenCalledWith("refresh-1");
        expect(state.isLoggedIn).toBe(false);
        expect(state.hasOnboarded).toBe(false);
        expect(state.id).toBeNull();
        expect(state.email).toBeNull();
        expect(state.accessToken).toBeNull();
        expect(state.refreshToken).toBeNull();
        expect(state.accessTokenExpiresAt).toBeNull();
    });

    it("wipes the persisted session from storage", async () => {
        useAuthStore.setState({ isLoggedIn: true, refreshToken: "refresh-1" });

        await useAuthStore.getState().logout();

        // clearStorage() removes the persisted store key from the device (SecureStore on native).
        expect(mockDeleteItem).toHaveBeenCalledWith("auth-store");
    });
});
