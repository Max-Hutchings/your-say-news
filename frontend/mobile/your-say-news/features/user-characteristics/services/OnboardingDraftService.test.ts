import {
    clearOnboardingDraft,
    loadOnboardingDraft,
    saveOnboardingDraft,
} from "./OnboardingDraftService";
import { createEmptyOnboardingForm } from "../answers";

const mockStorage = new Map<string, string>();

jest.mock("expo-secure-store", () => ({
    getItemAsync: jest.fn((key: string) => Promise.resolve(mockStorage.get(key) ?? null)),
    setItemAsync: jest.fn((key: string, value: string) => {
        mockStorage.set(key, value);
        return Promise.resolve();
    }),
}));

beforeEach(() => {
    mockStorage.clear();
});

describe("OnboardingDraftService", () => {
    it("restores a completed page at its next step for the same user", async () => {
        const form = { ...createEmptyOnboardingForm(), country: "United Kingdom", urbanRural: "URBAN" };

        await saveOnboardingDraft(42, form, 1);

        await expect(loadOnboardingDraft(42)).resolves.toEqual({ form, nextStep: 1 });
    });

    it("keeps different users' sensitive draft answers isolated", async () => {
        const firstUser = { ...createEmptyOnboardingForm(), country: "United Kingdom", ageRange: "AGE_25_34" };
        const secondUser = { ...createEmptyOnboardingForm(), country: "Japan", ageRange: "AGE_35_44" };

        await saveOnboardingDraft(42, firstUser, 2);
        await saveOnboardingDraft(99, secondUser, 5);
        await clearOnboardingDraft(42);

        await expect(loadOnboardingDraft(42)).resolves.toBeNull();
        await expect(loadOnboardingDraft(99)).resolves.toEqual({ form: secondUser, nextStep: 5 });
    });
});
