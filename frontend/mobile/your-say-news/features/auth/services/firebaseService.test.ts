jest.mock("expo-constants", () => ({
    __esModule: true,
    default: {
        expoConfig: {
            extra: {
                FIREBASE_PROJECT_ID: "demo-your-say-news",
                FIREBASE_API_KEY: "local-key",
                FIREBASE_APP_ID: "local-app",
                FIREBASE_AUTH_EMULATOR_URL: "http://localhost:9099",
            },
        },
    },
}));

import { GoogleSignin } from "@react-native-google-signin/google-signin";
import { GoogleAuthProvider, signInWithCredential, signInWithEmailAndPassword, signOut } from "firebase/auth";
import { firebaseAuth } from "./firebaseClient";
import {
    getFirebaseIdToken,
    hasFirebaseSession,
    logoutFirebase,
    signInWithGoogle,
    signInWithTestAccount,
    usesHostedGoogleAuth,
} from "./firebaseService";

const auth = firebaseAuth as unknown as {
    authStateReady: jest.Mock;
    currentUser: { getIdToken: jest.Mock } | null;
};

// Captured before beforeEach clears mocks: configure runs once, at module import.
const googleConfigureCallsAtImport = jest.mocked(GoogleSignin.configure).mock.calls.length;

beforeEach(() => {
    jest.clearAllMocks();
    auth.authStateReady = jest.fn().mockResolvedValue(undefined);
    auth.currentUser = null;
});

test("emulator builds never configure Google Sign-In", () => {
    expect(usesHostedGoogleAuth()).toBe(false);
    expect(googleConfigureCallsAtImport).toBe(0);
});

test("signs a seeded account into Firebase", async () => {
    jest.mocked(signInWithEmailAndPassword).mockResolvedValue({} as never);

    await expect(signInWithTestAccount(" riley.reader@example.com ", "password123"))
        .resolves.toBe(true);

    expect(signInWithEmailAndPassword).toHaveBeenCalledWith(
        firebaseAuth,
        "riley.reader@example.com",
        "password123",
    );
});

test("reports rejected credentials without exposing the Firebase error", async () => {
    jest.mocked(signInWithEmailAndPassword).mockRejectedValue(new Error("auth/wrong-password"));

    await expect(signInWithTestAccount("riley.reader@example.com", "wrong"))
        .resolves.toBe(false);
});

test("uses Firebase's restored user and refreshed ID token", async () => {
    const getIdToken = jest.fn().mockResolvedValue("fresh-id-token");
    auth.currentUser = { getIdToken };

    await expect(hasFirebaseSession()).resolves.toBe(true);
    await expect(getFirebaseIdToken(true)).resolves.toBe("fresh-id-token");

    expect(getIdToken).toHaveBeenCalledWith(true);
});

test("delegates logout to Firebase", async () => {
    jest.mocked(signOut).mockResolvedValue(undefined);

    await logoutFirebase();

    expect(signOut).toHaveBeenCalledWith(firebaseAuth);
    expect(GoogleSignin.signOut).not.toHaveBeenCalled();
});

test("exchanges the Google ID token for a Firebase session", async () => {
    const credential = { providerId: "google.com" } as never;
    jest.mocked(GoogleSignin.signIn)
        .mockResolvedValue({ type: "success", data: { idToken: "google-id-token" } } as never);
    jest.mocked(GoogleAuthProvider.credential).mockReturnValue(credential);
    jest.mocked(signInWithCredential).mockResolvedValue({} as never);

    await expect(signInWithGoogle()).resolves.toBe(true);

    expect(GoogleSignin.hasPlayServices).toHaveBeenCalledWith({ showPlayServicesUpdateDialog: true });
    expect(GoogleAuthProvider.credential).toHaveBeenCalledWith("google-id-token");
    expect(signInWithCredential).toHaveBeenCalledWith(firebaseAuth, credential);
});

test("a cancelled Google sign-in never reaches Firebase", async () => {
    jest.mocked(GoogleSignin.signIn).mockResolvedValue({ type: "cancelled", data: null });

    await expect(signInWithGoogle()).resolves.toBe(false);

    expect(GoogleAuthProvider.credential).not.toHaveBeenCalled();
    expect(signInWithCredential).not.toHaveBeenCalled();
});

test("a Google account without an ID token never reaches Firebase", async () => {
    jest.mocked(GoogleSignin.signIn).mockResolvedValue({ type: "success", data: { idToken: null } } as never);

    await expect(signInWithGoogle()).resolves.toBe(false);

    expect(GoogleAuthProvider.credential).not.toHaveBeenCalled();
    expect(signInWithCredential).not.toHaveBeenCalled();
});

test("reports a Firebase-rejected Google credential as a failed sign-in", async () => {
    jest.mocked(GoogleSignin.signIn)
        .mockResolvedValue({ type: "success", data: { idToken: "revoked-id-token" } } as never);
    jest.mocked(signInWithCredential).mockRejectedValue(new Error("auth/invalid-credential"));

    await expect(signInWithGoogle()).resolves.toBe(false);
});
