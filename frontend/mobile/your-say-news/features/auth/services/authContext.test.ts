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
    verifySession: jest.fn(),
}));

import { useAuthStore } from "./authContext";
import { loginWithKeycloak, refreshTokens, revokeTokens } from "./keycloakService";
import { getOnboardingStatus, getUser, verifySession } from "./UserService";
import * as SecureStore from "expo-secure-store";

const mockLogin = loginWithKeycloak as jest.Mock;
const mockRefresh = refreshTokens as jest.Mock;
const mockRevoke = revokeTokens as jest.Mock;
const mockGetUser = getUser as jest.Mock;
const mockGetOnboardingStatus = getOnboardingStatus as jest.Mock;
const mockVerifySession = verifySession as jest.Mock;
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
        consentedAt: null,
        isLoggedIn: false,
        hasOnboarded: false,
        hasCharacteristics: false,
        accountType: "USER",
        publisherStatus: "NONE",
        canPublish: false,
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

    it("expires exactly at the 30s skew boundary but not one millisecond before", () => {
        jest.spyOn(Date, "now").mockReturnValue(1_000_000);
        useAuthStore.setState({ accessToken: "tok", accessTokenExpiresAt: 1_030_001 });
        expect(useAuthStore.getState().accessTokenExpired()).toBe(false);

        useAuthStore.setState({ accessTokenExpiresAt: 1_030_000 });
        expect(useAuthStore.getState().accessTokenExpired()).toBe(true);
        jest.restoreAllMocks();
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
            accountType: "OFFICIAL",
            publisherStatus: "ACTIVE",
            canPublish: true,
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
        expect(state.firstName).toBe("Ada");
        expect(state.lastName).toBe("Lovelace");
        expect(state.dateOfBirth).toBe("1990-05-21");
        expect(state.consentedAt).toBe("2026-06-01T00:00:00Z");
        expect(state.accountType).toBe("OFFICIAL");
        expect(state.publisherStatus).toBe("ACTIVE");
        expect(state.canPublish).toBe(true);
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
            consentedAt: "2026-06-01T00:00:00Z",
            accountType: "USER",
            publisherStatus: "NONE",
            canPublish: false,
        });
        // Consent alone is not enough when the characteristic profile is still missing.
        mockGetOnboardingStatus.mockResolvedValue({
            consented: true,
            hasCharacteristics: false,
            onboarded: false,
        });

        await useAuthStore.getState().login();
        const state = useAuthStore.getState();

        expect(state.hasCharacteristics).toBe(false);
        expect(state.hasOnboarded).toBe(false);
    });

    it("fails closed when onboarding status is unavailable even if the user has consented", async () => {
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
            accountType: "USER",
            publisherStatus: "NONE",
            canPublish: false,
        });
        mockGetOnboardingStatus.mockResolvedValue(null);

        await useAuthStore.getState().login();
        const state = useAuthStore.getState();

        expect(state.hasCharacteristics).toBe(false);
        expect(state.hasOnboarded).toBe(false);
    });

    it("returns false and stays logged out when the user cancels", async () => {
        mockLogin.mockResolvedValue(null);

        const ok = await useAuthStore.getState().login();

        expect(ok).toBe(false);
        expect(useAuthStore.getState().isLoggedIn).toBe(false);
        expect(mockGetUser).not.toHaveBeenCalled();
    });

    it("returns false when user details cannot be fetched", async () => {
        useAuthStore.setState({
            id: 44,
            email: "old@example.com",
            firstName: "Old",
            lastName: "Identity",
            dateOfBirth: "1970-01-01",
            consentedAt: "2026-01-01T00:00:00Z",
            hasOnboarded: true,
            hasCharacteristics: true,
            accountType: "OFFICIAL",
            publisherStatus: "ACTIVE",
            canPublish: true,
        });
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
        expect(useAuthStore.getState().accessToken).toBeNull();
        expect(useAuthStore.getState().refreshToken).toBeNull();
        expect(useAuthStore.getState().accessTokenExpiresAt).toBeNull();
        expect(useAuthStore.getState().id).toBeNull();
        expect(useAuthStore.getState().email).toBeNull();
        expect(useAuthStore.getState().firstName).toBeNull();
        expect(useAuthStore.getState().lastName).toBeNull();
        expect(useAuthStore.getState().dateOfBirth).toBeNull();
        expect(useAuthStore.getState().consentedAt).toBeNull();
        expect(useAuthStore.getState().accountType).toBe("USER");
        expect(useAuthStore.getState().publisherStatus).toBe("NONE");
        expect(useAuthStore.getState().canPublish).toBe(false);
        expect(useAuthStore.getState().hasOnboarded).toBe(false);
        expect(useAuthStore.getState().hasCharacteristics).toBe(false);
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
            accountType: "OFFICIAL",
            publisherStatus: "ACTIVE",
            canPublish: true,
            accessToken: "access-1",
            refreshToken: "refresh-1",
            accessTokenExpiresAt: Date.now() + HOUR,
        });

        await useAuthStore.getState().logout();
        const state = useAuthStore.getState();

        expect(mockRevoke).toHaveBeenCalledWith("refresh-1");
        expect(state.isLoggedIn).toBe(false);
        expect(state.hasOnboarded).toBe(false);
        expect(state.accountType).toBe("USER");
        expect(state.publisherStatus).toBe("NONE");
        expect(state.canPublish).toBe(false);
        expect(state.id).toBeNull();
        expect(state.email).toBeNull();
        expect(state.firstName).toBeNull();
        expect(state.lastName).toBeNull();
        expect(state.dateOfBirth).toBeNull();
        expect(state.consentedAt).toBeNull();
        expect(state.hasCharacteristics).toBe(false);
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

    it("clears the local session even when token revocation fails", async () => {
        useAuthStore.setState({
            id: 7,
            email: "ada@example.com",
            isLoggedIn: true,
            hasOnboarded: true,
            accessToken: "access-1",
            refreshToken: "refresh-1",
        });
        mockRevoke.mockRejectedValue(new Error("identity service unavailable"));

        await expect(useAuthStore.getState().logout()).resolves.toBeUndefined();

        const state = useAuthStore.getState();
        expect(state.isLoggedIn).toBe(false);
        expect(state.id).toBeNull();
        expect(state.email).toBeNull();
        expect(state.accessToken).toBeNull();
        expect(state.refreshToken).toBeNull();
        expect(mockDeleteItem).toHaveBeenCalledWith("auth-store");
    });
});

describe("restoreSession", () => {
    const riley = {
        id: 10,
        email: "riley.reader@example.com",
        firstName: "Riley",
        lastName: "Reader",
        dateOfBirth: "1993-09-14",
        consentedAt: "2026-07-21T09:00:00Z",
        accountType: "USER" as const,
        publisherStatus: "NONE" as const,
        canPublish: false,
    };

    it("honours a stored session the server still accepts, refreshing every identity field from the server", async () => {
        useAuthStore.setState({
            // Restored from storage without the logged-in flag, and carrying a stale elevated
            // identity: every one of these must be overruled by what the server returns.
            isLoggedIn: false,
            accessToken: "stored-access",
            refreshToken: "stored-refresh",
            id: 99,
            email: "someone.else@example.com",
            firstName: "Someone",
            lastName: "Else",
            dateOfBirth: "1970-01-01",
            accountType: "ADMIN",
            publisherStatus: "ACTIVE",
            canPublish: true,
            hasCharacteristics: false,
            consentedAt: null,
        });
        mockVerifySession.mockResolvedValue({ state: "valid", user: riley });
        mockGetOnboardingStatus.mockResolvedValue({
            consented: true,
            hasCharacteristics: true,
            onboarded: true,
        });

        await expect(useAuthStore.getState().restoreSession()).resolves.toBe("signed-in");

        const state = useAuthStore.getState();
        expect(state.isLoggedIn).toBe(true);
        expect(state.id).toBe(10);
        expect(state.email).toBe("riley.reader@example.com");
        expect(state.firstName).toBe("Riley");
        expect(state.lastName).toBe("Reader");
        expect(state.dateOfBirth).toBe("1993-09-14");
        expect(state.consentedAt).toBe("2026-07-21T09:00:00Z");
        expect(state.hasCharacteristics).toBe(true);
        expect(state.hasOnboarded).toBe(true);
        // The stale elevated identity must not survive — canPublish drives the publish affordance.
        expect(state.accountType).toBe("USER");
        expect(state.publisherStatus).toBe("NONE");
        expect(state.canPublish).toBe(false);
    });

    it("fails closed on onboarding flags when the status call fails on an otherwise valid session", async () => {
        useAuthStore.setState({
            isLoggedIn: false,
            accessToken: "stored-access",
            refreshToken: "stored-refresh",
            hasCharacteristics: true,
            hasOnboarded: true,
        });
        mockVerifySession.mockResolvedValue({ state: "valid", user: riley });
        mockGetOnboardingStatus.mockResolvedValue(null);

        await expect(useAuthStore.getState().restoreSession()).resolves.toBe("signed-in");

        const state = useAuthStore.getState();
        expect(state.isLoggedIn).toBe(true);
        expect(state.hasCharacteristics).toBe(false);
        expect(state.hasOnboarded).toBe(false);
    });

    it("wipes a stored session the server rejects, so it cannot be served to the next person", async () => {
        useAuthStore.setState({
            id: 10,
            email: "riley.reader@example.com",
            consentedAt: "2026-07-21T09:00:00Z",
            isLoggedIn: true,
            hasCharacteristics: true,
            hasOnboarded: true,
            accessToken: "expired-access",
            refreshToken: "expired-refresh",
        });
        mockVerifySession.mockResolvedValue({ state: "unauthenticated" });

        await expect(useAuthStore.getState().restoreSession()).resolves.toBe("signed-out");

        const state = useAuthStore.getState();
        expect(state.isLoggedIn).toBe(false);
        expect(state.id).toBeNull();
        expect(state.email).toBeNull();
        expect(state.consentedAt).toBeNull();
        expect(state.hasCharacteristics).toBe(false);
        expect(state.accessToken).toBeNull();
        expect(state.refreshToken).toBeNull();
        // The persisted copy goes too — no manual clearing should ever be required.
        expect(mockDeleteItem).toHaveBeenCalledWith("auth-store");
    });

    it("keeps a session the server could not be reached to judge, without claiming they are signed in", async () => {
        useAuthStore.setState({
            id: 10,
            email: "riley.reader@example.com",
            isLoggedIn: true,
            accessToken: "stored-access",
            refreshToken: "stored-refresh",
        });
        mockVerifySession.mockResolvedValue({ state: "unreachable" });

        await expect(useAuthStore.getState().restoreSession()).resolves.toBe("unverified");

        const state = useAuthStore.getState();
        expect(state.accessToken).toBe("stored-access");
        expect(state.refreshToken).toBe("stored-refresh");
        expect(mockDeleteItem).not.toHaveBeenCalled();
        // Credentials survive, but an unvouched-for session must not count as signed in — the route
        // guard reads this flag, so leaving it true would admit the very session we could not verify.
        expect(state.isLoggedIn).toBe(false);
    });

    it("does not call the server, or wipe anything, when nothing was stored", async () => {
        useAuthStore.setState({
            isLoggedIn: false,
            accessToken: null,
            refreshToken: null,
        });

        await expect(useAuthStore.getState().restoreSession()).resolves.toBe("signed-out");

        expect(mockVerifySession).not.toHaveBeenCalled();
        expect(mockDeleteItem).not.toHaveBeenCalled();
    });

    it("checks and wipes leftover tokens even when the logged-in flag was not persisted", async () => {
        useAuthStore.setState({
            isLoggedIn: false,
            accessToken: "orphaned-access",
            refreshToken: "orphaned-refresh",
            email: "riley.reader@example.com",
        });
        mockVerifySession.mockResolvedValue({ state: "unauthenticated" });

        await expect(useAuthStore.getState().restoreSession()).resolves.toBe("signed-out");

        expect(mockVerifySession).toHaveBeenCalledTimes(1);
        const state = useAuthStore.getState();
        expect(state.accessToken).toBeNull();
        expect(state.refreshToken).toBeNull();
        expect(state.email).toBeNull();
        expect(mockDeleteItem).toHaveBeenCalledWith("auth-store");
    });

    it("checks a logged-in flag that outlived its tokens", async () => {
        useAuthStore.setState({
            isLoggedIn: true,
            accessToken: null,
            refreshToken: null,
        });
        mockVerifySession.mockResolvedValue({ state: "unauthenticated" });

        await expect(useAuthStore.getState().restoreSession()).resolves.toBe("signed-out");

        expect(mockVerifySession).toHaveBeenCalledTimes(1);
        expect(useAuthStore.getState().isLoggedIn).toBe(false);
    });
});

describe("completeLogin identity replacement", () => {
    it("drops the previous identity the moment the new credentials are stored, before the server replies", async () => {
        useAuthStore.setState({
            id: 10,
            email: "riley.reader@example.com",
            firstName: "Riley",
            consentedAt: "2026-07-21T09:00:00Z",
            isLoggedIn: true,
            hasCharacteristics: true,
            hasOnboarded: true,
            accountType: "ADMIN",
            publisherStatus: "ACTIVE",
            canPublish: true,
        });

        // Hold getUser open so we can inspect the window between storing the new token and
        // learning who it belongs to. The old identity must already be gone by then — otherwise
        // Riley's details sit alongside somebody else's access token.
        let releaseGetUser: (user: unknown) => void = () => undefined;
        mockGetUser.mockReturnValue(new Promise((resolve) => {
            releaseGetUser = resolve;
        }));
        mockGetOnboardingStatus.mockResolvedValue({
            consented: false,
            hasCharacteristics: false,
            onboarded: false,
        });

        const pending = useAuthStore.getState().completeLogin({
            accessToken: "nora-access",
            refreshToken: "nora-refresh",
            expiresIn: 300,
        });

        const inFlight = useAuthStore.getState();
        expect(inFlight.accessToken).toBe("nora-access");
        expect(inFlight.id).toBeNull();
        expect(inFlight.email).toBeNull();
        expect(inFlight.firstName).toBeNull();
        expect(inFlight.consentedAt).toBeNull();
        expect(inFlight.isLoggedIn).toBe(false);
        expect(inFlight.hasCharacteristics).toBe(false);
        expect(inFlight.accountType).toBe("USER");
        expect(inFlight.canPublish).toBe(false);

        releaseGetUser({
            id: 5,
            email: "nora.new@example.com",
            firstName: "Nora",
            lastName: "New",
            dateOfBirth: "1999-09-09",
            consentedAt: null,
            accountType: "USER",
            publisherStatus: "NONE",
            canPublish: false,
        });
        await pending;
    });

    it("does not let the previous user's details survive a sign-in as somebody else", async () => {
        // Whoever was signed in before, with a full profile and consent on record.
        useAuthStore.setState({
            id: 10,
            email: "riley.reader@example.com",
            firstName: "Riley",
            lastName: "Reader",
            consentedAt: "2026-07-21T09:00:00Z",
            isLoggedIn: true,
            hasCharacteristics: true,
            hasOnboarded: true,
            accountType: "ADMIN",
            publisherStatus: "ACTIVE",
            canPublish: true,
        });

        // The newly signed-in user is un-onboarded and has no consent.
        mockGetUser.mockResolvedValue({
            id: 5,
            email: "nora.new@example.com",
            firstName: "Nora",
            lastName: "New",
            dateOfBirth: "1999-09-09",
            consentedAt: null,
            accountType: "USER",
            publisherStatus: "NONE",
            canPublish: false,
        });
        mockGetOnboardingStatus.mockResolvedValue({
            consented: false,
            hasCharacteristics: false,
            onboarded: false,
        });

        await expect(
            useAuthStore.getState().completeLogin({
                accessToken: "nora-access",
                refreshToken: "nora-refresh",
                expiresIn: 300,
            })
        ).resolves.toBe(true);

        const state = useAuthStore.getState();
        expect(state.id).toBe(5);
        expect(state.email).toBe("nora.new@example.com");
        expect(state.consentedAt).toBeNull();
        expect(state.hasCharacteristics).toBe(false);
        expect(state.hasOnboarded).toBe(false);
        expect(state.accountType).toBe("USER");
        expect(state.publisherStatus).toBe("NONE");
        expect(state.canPublish).toBe(false);
    });
});
