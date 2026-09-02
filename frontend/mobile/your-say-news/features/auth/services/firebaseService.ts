import { signInWithEmailAndPassword, signOut } from "firebase/auth";
import { firebaseAuth } from "./firebaseClient";

export async function signInWithTestAccount(email: string, password: string): Promise<boolean> {
    try {
        await signInWithEmailAndPassword(firebaseAuth, email.trim(), password);
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
}
