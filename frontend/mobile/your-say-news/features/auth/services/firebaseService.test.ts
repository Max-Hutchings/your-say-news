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

import { signInWithEmailAndPassword, signOut } from "firebase/auth";
import { firebaseAuth } from "./firebaseClient";
import {
    getFirebaseIdToken,
    hasFirebaseSession,
    logoutFirebase,
    signInWithTestAccount,
} from "./firebaseService";

const auth = firebaseAuth as unknown as {
    authStateReady: jest.Mock;
    currentUser: { getIdToken: jest.Mock } | null;
};

beforeEach(() => {
    jest.clearAllMocks();
    auth.authStateReady = jest.fn().mockResolvedValue(undefined);
    auth.currentUser = null;
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
});
