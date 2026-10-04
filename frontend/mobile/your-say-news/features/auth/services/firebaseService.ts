import Constants from "expo-constants";
import { GoogleSignin } from "@react-native-google-signin/google-signin";
import { GoogleAuthProvider, signInWithCredential, signInWithEmailAndPassword, signOut } from "firebase/auth";
import type { LoginResult } from "../types";
import { firebaseAuth } from "./firebaseClient";

const extra = Constants.expoConfig?.extra ?? {};
const hostedGoogleAuth = extra.AUTH_MODE === "google";

if (hostedGoogleAuth) {
    GoogleSignin.configure({ webClientId: String(extra.GOOGLE_WEB_CLIENT_ID) });
}

export async function signInWithTestAccount(email: string, password: string): Promise<boolean> {
    try {
        await signInWithEmailAndPassword(firebaseAuth, email.trim(), password);
        return true;
    } catch {
        return false;
    }
}

export function usesHostedGoogleAuth(): boolean {
    return hostedGoogleAuth;
}

export async function signInWithGoogle(): Promise<LoginResult> {
    let idToken: string | null;
    try {
        await GoogleSignin.hasPlayServices({ showPlayServicesUpdateDialog: true });
        const response = await GoogleSignin.signIn();
        if (response.type !== "success") {
            return { status: "cancelled" };
        }
        idToken = response.data.idToken;
    } catch (error) {
        return signInFailed("google", errorCode(error));
    }
    if (!idToken) {
        return signInFailed("google", "missing_id_token");
    }
    try {
        await signInWithCredential(firebaseAuth, GoogleAuthProvider.credential(idToken));
        return { status: "signed-in" };
    } catch (error) {
        return signInFailed("firebase", errorCode(error));
    }
}

// Written to the device log (logcat tag ReactNativeJS), which is the only place a client-side
// sign-in failure is recorded. Only the bounded provider code is logged, never the message.
function signInFailed(stage: "google" | "firebase", code: string): LoginResult {
    console.warn(`Sign-in failed: stage=${stage} code=${code}`);
    return { status: "failed", stage, code };
}

// Google Sign-In errors carry Android status codes (e.g. "10" = DEVELOPER_ERROR); Firebase errors
// carry codes like "auth/invalid-credential".
function errorCode(error: unknown): string {
    const code = (error as { code?: unknown } | null)?.code;
    return typeof code === "string" || typeof code === "number" ? String(code) : "unknown";
}

export async function hasFirebaseSession(): Promise<boolean> {
    await firebaseAuth.authStateReady();
    return firebaseAuth.currentUser !== null;
}

export async function getFirebaseIdToken(forceRefresh = false): Promise<string | null> {
    await firebaseAuth.authStateReady();
    return firebaseAuth.currentUser?.getIdToken(forceRefresh) ?? null;
}

export async function logoutFirebase(): Promise<void> {
    await signOut(firebaseAuth);
    if (hostedGoogleAuth) {
        await GoogleSignin.signOut();
    }
}
