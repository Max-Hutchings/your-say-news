import { Platform } from "react-native";
import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import * as SecureStore from "expo-secure-store";
import type { SessionRestoreResult, User, UserState } from "../types";
import { hasFirebaseSession, logoutFirebase, signInWithTestAccount } from "./firebaseService";
import { getOnboardingStatus, getUser, verifySession } from "./UserService";

const isWeb = Platform.OS === "web";

const SIGNED_OUT_IDENTITY = {
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
} as const;

export const useAuthStore = create(
    persist<UserState>(
        (set) => ({
            _stateHydrated: isWeb,
            ...SIGNED_OUT_IDENTITY,
            login,
            completeLogin,
            restoreSession,
            logout,
            setHasOnboarded: (hasOnboarded) => set({ hasOnboarded }),
            setHasCharacteristics: (hasCharacteristics) => set({ hasCharacteristics }),
            setConsentedAt: (consentedAt) => set({ consentedAt }),
        }),
        {
            name: "auth-store",
            storage: isWeb
                ? createJSONStorage(() => localStorage)
                : createJSONStorage(() => ({
                    setItem: (key: string, value: string) => SecureStore.setItemAsync(key, value),
                    getItem: (key: string) => SecureStore.getItemAsync(key),
                    removeItem: (key: string) => SecureStore.deleteItemAsync(key),
                })),
            merge: (persistedState, currentState) => ({
                ...currentState,
                ...(persistedState as Partial<UserState>),
                _stateHydrated: true,
            }),
            onRehydrateStorage: () => () => useAuthStore.setState({ _stateHydrated: true }),
        },
    ),
);

async function login(email: string, password: string): Promise<boolean> {
    if (!await signInWithTestAccount(email, password)) {
        return false;
    }
    return completeLogin();
}

async function completeLogin(): Promise<boolean> {
    useAuthStore.setState(SIGNED_OUT_IDENTITY);
    const user = await getUser();
    if (!user) {
        await logout();
        return false;
    }
    await applyVerifiedUser(user);
    return true;
}

async function restoreSession(): Promise<SessionRestoreResult> {
    if (!await hasFirebaseSession()) {
        await logout();
        return "signed-out";
    }

    const check = await verifySession();
    if (check.state === "unauthenticated") {
        await logout();
        return "signed-out";
    }
    if (check.state === "unreachable") {
        useAuthStore.setState(SIGNED_OUT_IDENTITY);
        await useAuthStore.persist.clearStorage();
        return "unverified";
    }
    await applyVerifiedUser(check.user);
    return "signed-in";
}

async function applyVerifiedUser(user: User): Promise<void> {
    const status = await getOnboardingStatus();
    useAuthStore.setState({
        id: user.id,
        email: user.email,
        firstName: user.firstName,
        lastName: user.lastName,
        dateOfBirth: user.dateOfBirth,
        consentedAt: user.consentedAt,
        accountType: user.accountType,
        publisherStatus: user.publisherStatus,
        canPublish: user.canPublish,
        hasCharacteristics: status?.hasCharacteristics ?? false,
        hasOnboarded: status?.onboarded ?? false,
        isLoggedIn: true,
    });
}

async function logout(): Promise<void> {
    try {
        await logoutFirebase();
    } finally {
        useAuthStore.setState(SIGNED_OUT_IDENTITY);
        await useAuthStore.persist.clearStorage();
    }
}
