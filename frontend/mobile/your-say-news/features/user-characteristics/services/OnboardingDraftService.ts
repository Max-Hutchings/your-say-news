import { Platform } from "react-native";
import * as SecureStore from "expo-secure-store";
import type { OnboardingForm } from "../answers";

const STORAGE_KEY = "user-characteristics-onboarding-drafts";

type StoredDraft = {
    form: OnboardingForm;
    nextStep: number;
};

type DraftsByUser = Record<string, StoredDraft>;

/**
 * Stores incomplete answers per signed-in user. Native values live in the platform secure store;
 * web uses the same browser-local persistence model as the authenticated session. Drafts are never
 * sent to the aggregate API until the complete form is explicitly submitted.
 */
export async function loadOnboardingDraft(userId: number): Promise<StoredDraft | null> {
    const drafts = await readDrafts();
    return drafts[String(userId)] ?? null;
}

export async function saveOnboardingDraft(
    userId: number,
    form: OnboardingForm,
    nextStep: number
): Promise<void> {
    const drafts = await readDrafts();
    drafts[String(userId)] = { form, nextStep };
    await writeDrafts(drafts);
}

export async function clearOnboardingDraft(userId: number): Promise<void> {
    const drafts = await readDrafts();
    delete drafts[String(userId)];
    await writeDrafts(drafts);
}

async function readDrafts(): Promise<DraftsByUser> {
    try {
        const raw = Platform.OS === "web"
            ? window.localStorage.getItem(STORAGE_KEY)
            : await SecureStore.getItemAsync(STORAGE_KEY);
        return raw ? (JSON.parse(raw) as DraftsByUser) : {};
    } catch {
        // A corrupt or unavailable local store should never stop onboarding.
        return {};
    }
}

async function writeDrafts(drafts: DraftsByUser): Promise<void> {
    const value = JSON.stringify(drafts);
    if (Platform.OS === "web") {
        window.localStorage.setItem(STORAGE_KEY, value);
        return;
    }
    await SecureStore.setItemAsync(STORAGE_KEY, value);
}
