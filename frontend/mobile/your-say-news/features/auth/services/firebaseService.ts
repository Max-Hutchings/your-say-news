import Constants from "expo-constants";
import { GoogleSignin } from "@react-native-google-signin/google-signin";
import { GoogleAuthProvider, signInWithCredential, signInWithEmailAndPassword, signOut } from "firebase/auth";
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

export async function signInWithGoogle(): Promise<boolean> {
    try {
        await GoogleSignin.hasPlayServices({ showPlayServicesUpdateDialog: true });
        const response = await GoogleSignin.signIn();
        if (response.type !== "success" || !response.data.idToken) {
            return false;
        }
        const credential = GoogleAuthProvider.credential(response.data.idToken);
        await signInWithCredential(firebaseAuth, credential);
        return true;
    } catch {
        return false;
    }
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
