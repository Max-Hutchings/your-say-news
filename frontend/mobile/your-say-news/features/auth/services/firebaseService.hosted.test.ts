jest.mock("expo-constants", () => ({
    __esModule: true,
    default: {
        expoConfig: {
            extra: {
                AUTH_MODE: "google",
                GOOGLE_WEB_CLIENT_ID: "123-dev.apps.googleusercontent.com",
                FIREBASE_PROJECT_ID: "your-say-news-development",
                FIREBASE_API_KEY: "hosted-key",
                FIREBASE_APP_ID: "hosted-app",
            },
        },
    },
}));

import { GoogleSignin } from "@react-native-google-signin/google-signin";
import { signOut } from "firebase/auth";
import { firebaseAuth } from "./firebaseClient";
import { logoutFirebase, usesHostedGoogleAuth } from "./firebaseService";

test("hosted builds configure Google Sign-In with the web client id", () => {
    expect(usesHostedGoogleAuth()).toBe(true);
    expect(GoogleSignin.configure).toHaveBeenCalledWith({ webClientId: "123-dev.apps.googleusercontent.com" });
});

test("hosted logout also clears the cached Google account", async () => {
    jest.mocked(signOut).mockResolvedValue(undefined);

    await logoutFirebase();

    expect(signOut).toHaveBeenCalledWith(firebaseAuth);
    expect(GoogleSignin.signOut).toHaveBeenCalledTimes(1);
});
