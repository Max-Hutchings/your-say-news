jest.mock("expo-secure-store", () => ({
    setItemAsync: jest.fn(),
    getItemAsync: jest.fn().mockResolvedValue(null),
    deleteItemAsync: jest.fn(),
}));
jest.mock("./firebaseService", () => ({
    hasFirebaseSession: jest.fn(),
    logoutFirebase: jest.fn(),
    signInWithGoogle: jest.fn(),
    signInWithTestAccount: jest.fn(),
    usesHostedGoogleAuth: jest.fn(() => false),
}));
jest.mock("./UserService", () => ({
    getOnboardingStatus: jest.fn(),
    getUser: jest.fn(),
    verifySession: jest.fn(),
}));

import {
    hasFirebaseSession,
    logoutFirebase,
    signInWithGoogle,
    signInWithTestAccount,
    usesHostedGoogleAuth,
} from "./firebaseService";
import { getOnboardingStatus, getUser, verifySession } from "./UserService";
import { useAuthStore } from "./authContext";

const user = {
    id: 8,
    email: "riley.reader@example.com",
    firstName: "Riley",
    lastName: "Reader",
    dateOfBirth: "1991-04-12",
    consentedAt: "2026-08-01T10:00:00Z",
    accountType: "USER" as const,
    publisherStatus: "NONE" as const,
    canPublish: false,
};

beforeEach(() => {
    jest.clearAllMocks();
    jest.spyOn(console, "warn").mockImplementation(() => undefined);
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
        isLoggedIn: false,
        hasOnboarded: false,
        hasCharacteristics: false,
    });
    jest.mocked(getUser).mockResolvedValue(user);
    jest.mocked(getOnboardingStatus).mockResolvedValue({
        consented: true,
        hasCharacteristics: true,
        onboarded: true,
    });
});

test("seeded Firebase account signs in and loads the application user", async () => {
    jest.mocked(signInWithTestAccount).mockResolvedValue(true);

    await expect(useAuthStore.getState().login(user.email, "password123"))
        .resolves.toEqual({ status: "signed-in" });

    expect(useAuthStore.getState()).toMatchObject({
        ...user,
        isLoggedIn: true,
        hasOnboarded: true,
        hasCharacteristics: true,
    });
});

test("invalid Firebase credentials do not call the backend", async () => {
    jest.mocked(signInWithTestAccount).mockResolvedValue(false);

    await expect(useAuthStore.getState().login(user.email, "wrong"))
        .resolves.toEqual({ status: "failed", stage: "firebase", code: "invalid_credentials" });

    expect(getUser).not.toHaveBeenCalled();
    expect(useAuthStore.getState().isLoggedIn).toBe(false);
});

test("hosted builds sign in through Google instead of the test-account password", async () => {
    jest.mocked(usesHostedGoogleAuth).mockReturnValueOnce(true);
    jest.mocked(signInWithGoogle).mockResolvedValue({ status: "signed-in" });

    await expect(useAuthStore.getState().login("", "")).resolves.toEqual({ status: "signed-in" });

    expect(signInWithTestAccount).not.toHaveBeenCalled();
    expect(useAuthStore.getState()).toMatchObject({ ...user, isLoggedIn: true });
});

test("a cancelled Google sign-in never calls the backend or signs in", async () => {
    jest.mocked(usesHostedGoogleAuth).mockReturnValueOnce(true);
    jest.mocked(signInWithGoogle).mockResolvedValue({ status: "cancelled" });

    await expect(useAuthStore.getState().login("", "")).resolves.toEqual({ status: "cancelled" });

    expect(getUser).not.toHaveBeenCalled();
    expect(useAuthStore.getState().isLoggedIn).toBe(false);
});

test("a failed Google sign-in is returned as-is and never calls the backend", async () => {
    const failure = { status: "failed", stage: "google", code: "10" } as const;
    jest.mocked(usesHostedGoogleAuth).mockReturnValueOnce(true);
    jest.mocked(signInWithGoogle).mockResolvedValue(failure);

    await expect(useAuthStore.getState().login("", "")).resolves.toEqual(failure);

    expect(getUser).not.toHaveBeenCalled();
});

test("a Google account with no Your Say user is signed back out of Firebase", async () => {
    jest.mocked(usesHostedGoogleAuth).mockReturnValueOnce(true);
    jest.mocked(signInWithGoogle).mockResolvedValue({ status: "signed-in" });
    jest.mocked(getUser).mockResolvedValue(null);

    await expect(useAuthStore.getState().login("", ""))
        .resolves.toEqual({ status: "failed", stage: "server", code: "user_unavailable" });
    expect(console.warn).toHaveBeenCalledWith("Sign-in failed: stage=server code=user_unavailable");

    expect(logoutFirebase).toHaveBeenCalledTimes(1);
    expect(useAuthStore.getState()).toMatchObject({ id: null, email: null, isLoggedIn: false });
});

test("Firebase session restores after a web reload", async () => {
    jest.mocked(hasFirebaseSession).mockResolvedValue(true);
    jest.mocked(verifySession).mockResolvedValue({ state: "valid", user });

    await expect(useAuthStore.getState().restoreSession()).resolves.toBe("signed-in");

    expect(useAuthStore.getState()).toMatchObject({
        ...user,
        isLoggedIn: true,
        hasOnboarded: true,
        hasCharacteristics: true,
    });
});

test("missing Firebase session clears every persisted identity field", async () => {
    useAuthStore.setState({ ...user, isLoggedIn: false, hasOnboarded: true, hasCharacteristics: true });
    jest.mocked(hasFirebaseSession).mockResolvedValue(false);

    await expect(useAuthStore.getState().restoreSession()).resolves.toBe("signed-out");

    expect(logoutFirebase).toHaveBeenCalledTimes(1);
    expect(useAuthStore.getState()).toMatchObject({
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
    });
});

test("unreachable backend clears cached identity without destroying the Firebase session", async () => {
    useAuthStore.setState({ ...user, isLoggedIn: true, hasOnboarded: true, hasCharacteristics: true });
    jest.mocked(hasFirebaseSession).mockResolvedValue(true);
    jest.mocked(verifySession).mockResolvedValue({ state: "unreachable" });

    await expect(useAuthStore.getState().restoreSession()).resolves.toBe("unverified");

    expect(logoutFirebase).not.toHaveBeenCalled();
    expect(useAuthStore.getState()).toMatchObject({
        id: null,
        email: null,
        firstName: null,
        lastName: null,
        dateOfBirth: null,
        consentedAt: null,
        isLoggedIn: false,
    });
});

test("logout clears Firebase and application session state", async () => {
    useAuthStore.setState({ isLoggedIn: true, email: user.email });

    await useAuthStore.getState().logout();

    expect(logoutFirebase).toHaveBeenCalledTimes(1);
    expect(useAuthStore.getState()).toMatchObject({ isLoggedIn: false, email: null });
});
