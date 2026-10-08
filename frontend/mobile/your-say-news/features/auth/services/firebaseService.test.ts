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
import { recorder } from "@/features/telemetry/services/telemetry";
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
    jest.spyOn(console, "warn").mockImplementation(() => undefined);
    jest.mocked(GoogleSignin.hasPlayServices).mockReset();
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

    await expect(signInWithGoogle()).resolves.toEqual({ status: "signed-in" });

    expect(GoogleSignin.hasPlayServices).toHaveBeenCalledWith({ showPlayServicesUpdateDialog: true });
    expect(GoogleAuthProvider.credential).toHaveBeenCalledWith("google-id-token");
    expect(signInWithCredential).toHaveBeenCalledWith(firebaseAuth, credential);
});

test("a cancelled Google sign-in never reaches Firebase", async () => {
    jest.mocked(GoogleSignin.signIn).mockResolvedValue({ type: "cancelled", data: null });

    await expect(signInWithGoogle()).resolves.toEqual({ status: "cancelled" });

    expect(GoogleAuthProvider.credential).not.toHaveBeenCalled();
    expect(signInWithCredential).not.toHaveBeenCalled();
});

test("a Google account without an ID token never reaches Firebase", async () => {
    jest.mocked(GoogleSignin.signIn).mockResolvedValue({ type: "success", data: { idToken: null } } as never);

    await expect(signInWithGoogle()).resolves
        .toEqual({ status: "failed", stage: "google", code: "missing_id_token" });

    expect(signInWithCredential).not.toHaveBeenCalled();
});

test("reports and logs Google's native error code when Google rejects the app", async () => {
    // Android DEVELOPER_ERROR: the installed app's signing certificate or client ID is not registered.
    jest.mocked(GoogleSignin.signIn).mockRejectedValue(
        Object.assign(new Error("DEVELOPER_ERROR: Follow troubleshooting instruction"), { code: "10" }),
    );

    const queued = jest.spyOn(recorder, "log");

    await expect(signInWithGoogle()).resolves.toEqual({ status: "failed", stage: "google", code: "10" });

    expect(queued.mock.calls).toEqual([[
        { level: "warn", name: "auth.sign_in_failed", attributes: { stage: "google", code: "10" } },
    ]]);
    expect(jest.mocked(console.warn).mock.calls).toEqual([["auth.sign_in_failed stage=google code=10"]]);
    expect(signInWithCredential).not.toHaveBeenCalled();
});

test("reports Firebase's error code when Firebase rejects the Google credential", async () => {
    jest.mocked(GoogleSignin.signIn)
        .mockResolvedValue({ type: "success", data: { idToken: "revoked-id-token" } } as never);
    jest.mocked(signInWithCredential).mockRejectedValue(
        Object.assign(new Error("Firebase: Error (auth/invalid-credential)."), {
            code: "auth/invalid-credential", customData: { email: "riley.reader@example.com" },
        }),
    );
    const queued = jest.spyOn(recorder, "log");

    await expect(signInWithGoogle()).resolves
        .toEqual({ status: "failed", stage: "firebase", code: "auth/invalid-credential" });
    // Firebase errors carry customData such as the email; only the code may reach the device log.
    expect(jest.mocked(console.warn).mock.calls)
        .toEqual([["auth.sign_in_failed stage=firebase code=auth/invalid-credential"]]);
    expect(queued.mock.calls).toEqual([[
        { level: "warn", name: "auth.sign_in_failed", attributes: { stage: "firebase", code: "auth/invalid-credential" } },
    ]]);
    expect(JSON.stringify(queued.mock.calls)).not.toContain("riley.reader@example.com");
});

test("a numeric Google status code is reported as its string form", async () => {
    jest.mocked(GoogleSignin.signIn).mockRejectedValue(Object.assign(new Error("DEVELOPER_ERROR"), { code: 10 }));

    await expect(signInWithGoogle()).resolves.toEqual({ status: "failed", stage: "google", code: "10" });
});

test("an error without a code is reported as unknown", async () => {
    jest.mocked(GoogleSignin.hasPlayServices).mockRejectedValue(new Error("boom"));

    await expect(signInWithGoogle()).resolves.toEqual({ status: "failed", stage: "google", code: "unknown" });
});
