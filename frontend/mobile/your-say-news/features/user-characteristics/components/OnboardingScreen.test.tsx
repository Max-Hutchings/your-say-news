import React from "react";
import { Alert } from "react-native";
import { fireEvent, render, waitFor } from "@testing-library/react-native";
import { CHARACTERISTIC_OPTION_FIELDS, type CharacteristicOptions } from "../types";
import type { OnboardingForm } from "../answers";
import { fetchCharacteristicOptions } from "../services/CharacteristicOptionsService";
import { submitCharacteristics } from "../services/CharacteristicService";
import {
    clearOnboardingDraft,
    loadOnboardingDraft,
    saveOnboardingDraft,
} from "../services/OnboardingDraftService";
import { OnboardingScreen } from "./OnboardingScreen";

const mockReplace = jest.fn();
const mockSetHasOnboarded = jest.fn();
const mockSetHasCharacteristics = jest.fn();
let mockUserId: number | null = null;
const mockAlert = jest.spyOn(Alert, "alert").mockImplementation(() => undefined);

jest.mock("expo-router", () => ({ useRouter: () => ({ replace: mockReplace }) }));

jest.mock("@/features/auth", () => ({
    useAuthStore: (selector: (state: object) => unknown) => selector({
        id: mockUserId,
        setHasOnboarded: mockSetHasOnboarded,
        setHasCharacteristics: mockSetHasCharacteristics,
    }),
}));

jest.mock("@/constants/theme", () => ({
    useTheme: () => ({ isDark: false }),
    getEditorial: () => ({
        bg: "#ffffff",
        ink: "#111111",
        muted: "#777777",
        secondary: "#555555",
        border: "#dddddd",
        lime: "#ccff00",
        teal: "#008080",
        track: "#eeeeee",
    }),
    EditorialFont: {
        sansBold: "System",
        sans: "System",
        serif: "System",
        mono: "System",
        monoSemiBold: "System",
    },
    AnimationDuration: { fast: 0, normal: 0 },
}));

jest.mock("@/components/ui", () => ({
    Eyebrow: ({ text }: { text: string }) => {
        const { Text } = jest.requireActual("react-native");
        return <Text>{text}</Text>;
    },
}));

jest.mock("../services/CharacteristicOptionsService", () => ({
    fetchCharacteristicOptions: jest.fn(),
}));

jest.mock("../services/CharacteristicService", () => ({
    submitCharacteristics: jest.fn(),
}));

jest.mock("../services/OnboardingDraftService", () => ({
    clearOnboardingDraft: jest.fn(),
    loadOnboardingDraft: jest.fn(),
    saveOnboardingDraft: jest.fn(),
}));

const fetchOptions = fetchCharacteristicOptions as jest.MockedFunction<typeof fetchCharacteristicOptions>;
const submit = submitCharacteristics as jest.MockedFunction<typeof submitCharacteristics>;
const clearDraft = clearOnboardingDraft as jest.MockedFunction<typeof clearOnboardingDraft>;
const loadDraft = loadOnboardingDraft as jest.MockedFunction<typeof loadOnboardingDraft>;
const saveDraft = saveOnboardingDraft as jest.MockedFunction<typeof saveOnboardingDraft>;

function validOptions(): CharacteristicOptions {
    return {
        schemaVersion: 1,
        minimumAge: 16,
        fields: Object.fromEntries(
            CHARACTERISTIC_OPTION_FIELDS.map((field) => [field, [{ label: field, value: field.toUpperCase() }]])
        ) as CharacteristicOptions["fields"],
    };
}

function completeForm(): OnboardingForm {
    return {
        country: "United Kingdom", city: "Bristol", region: "", ukCounty: "BRISTOL", urbanRural: "URBAN",
        age: 30, gender: "WOMAN", genderSelfDescribe: "", sexAtBirth: "FEMALE",
        sexualOrientation: "STRAIGHT_HETEROSEXUAL", maritalStatus: "SINGLE",
        raceSelections: ["WHITE_EUROPEAN"], countryOfBirth: "UNITED_KINGDOM", citizenship: ["BRITISH"],
        religion: "NO_RELIGION", religiosity: "NOT_RELIGIOUS", politicalPersuasion: "CENTRE_LEFT",
        education: "BACHELORS", occupation: "EMPLOYED_FULL_TIME", employmentSector: "IT_TECHNOLOGY",
        universitySubject: "COMPUTER_SCIENCE", currency: "USD",
        personalIncomeRange: "BETWEEN_50K_AND_75K", householdIncomeRange: "BETWEEN_100K_AND_150K",
        height: "FEET_5_4_TO_5_6", weightRange: "KG_60_69", eyeColor: "GREEN",
        parent: "NOT_PARENT_CAREGIVER", hasPet: "YES", petType: ["DOG"],
        chronotype: "NIGHT_OWL", outlook: "OPTIMIST", neurodivergent: "YES",
        neurodivergenceType: ["ADHD"], hasDisability: "NO", disabilityType: [],
        housingStatus: "OWN_MORTGAGE", propertyType: "FLAT_APARTMENT", newsFrequencyScore: 7,
        balancedNewsViewpoint: "YES", mainstreamNewsPercent: 60, betterWorldWithData: "YES",
    };
}

describe("OnboardingScreen option loading", () => {
    beforeEach(() => {
        jest.clearAllMocks();
        mockUserId = null;
        loadDraft.mockResolvedValue(null);
        saveDraft.mockResolvedValue();
        clearDraft.mockResolvedValue();
        submit.mockResolvedValue();
    });

    it("loads options immediately and lets the user retry a failed startup load", async () => {
        fetchOptions
            .mockRejectedValueOnce(new Error("offline"))
            .mockResolvedValueOnce(validOptions());

        const screen = render(<OnboardingScreen />);

        expect(fetchOptions).toHaveBeenCalledTimes(1);
        expect(await screen.findByText("We couldn’t load the questions")).toBeTruthy();

        fireEvent.press(screen.getByRole("button", { name: "Try again" }));

        await waitFor(() => expect(fetchOptions).toHaveBeenCalledTimes(2));
        expect(await screen.findByText("Set up your lens")).toBeTruthy();
        expect(screen.getByText("Country of residence *")).toBeTruthy();
        expect(screen.getByText("urbanRural")).toBeTruthy();
    });

    it("restores a complete draft and finishes through the PII-free characteristic submission path", async () => {
        const form = completeForm();
        mockUserId = 5;
        loadDraft.mockResolvedValue({ form, nextStep: 12 });
        fetchOptions.mockResolvedValue(validOptions());

        const screen = render(<OnboardingScreen />);
        fireEvent.press(await screen.findByText("Finish setup"));

        await waitFor(() => expect(submit).toHaveBeenCalledTimes(1));
        const payload = submit.mock.calls[0][0];
        expect(payload).toMatchObject({
            country: "United Kingdom",
            age: 30,
            race: ["WHITE_EUROPEAN"],
            citizenship: ["BRITISH"],
            hasPet: true,
            petType: ["DOG"],
            housingStatus: "OWN_MORTGAGE",
            propertyType: "FLAT_APARTMENT",
        });
        expect(payload).not.toHaveProperty("userId");
        expect(payload).not.toHaveProperty("name");
        expect(payload).not.toHaveProperty("email");
        expect(saveDraft).toHaveBeenCalledWith(5, form, 12);
        expect(clearDraft).toHaveBeenCalledWith(5);
        expect(mockSetHasCharacteristics).toHaveBeenCalledWith(true);
        expect(mockSetHasOnboarded).toHaveBeenCalledWith(true);
        expect(mockReplace).toHaveBeenCalledWith("/(protected)");
    });

    it("returns an incomplete restored draft to the skipped required question", async () => {
        mockUserId = 5;
        loadDraft.mockResolvedValue({
            form: { ...completeForm(), balancedNewsViewpoint: null },
            nextStep: 12,
        });
        fetchOptions.mockResolvedValue(validOptions());

        const screen = render(<OnboardingScreen />);
        fireEvent.press(await screen.findByText("Finish setup"));

        expect(mockAlert).toHaveBeenCalledWith(
            "Answer required",
            "Please answer “Seeing more than one news viewpoint” before continuing."
        );
        expect(await screen.findByText("News habits")).toBeTruthy();
        expect(saveDraft).not.toHaveBeenCalled();
        expect(submit).not.toHaveBeenCalled();
        expect(clearDraft).not.toHaveBeenCalled();
        expect(mockSetHasCharacteristics).not.toHaveBeenCalled();
        expect(mockSetHasOnboarded).not.toHaveBeenCalled();
        expect(mockReplace).not.toHaveBeenCalled();
    });

    it("lets Continue advance while preserving the incomplete draft", async () => {
        mockUserId = 5;
        fetchOptions.mockResolvedValue(validOptions());

        const screen = render(<OnboardingScreen />);
        fireEvent.press(await screen.findByText("Continue"));

        await waitFor(() => expect(saveDraft).toHaveBeenCalledWith(5, expect.any(Object), 1));
        expect(await screen.findByText("STEP 2 OF 13")).toBeTruthy();
        expect(mockAlert).not.toHaveBeenCalled();
    });

    it("keeps the draft and auth state unchanged when submission fails", async () => {
        const form = completeForm();
        mockUserId = 5;
        loadDraft.mockResolvedValue({ form, nextStep: 12 });
        fetchOptions.mockResolvedValue(validOptions());
        submit.mockRejectedValue(new Error("service unavailable"));

        const screen = render(<OnboardingScreen />);
        fireEvent.press(await screen.findByText("Finish setup"));

        await waitFor(() => expect(mockAlert).toHaveBeenCalledWith("Couldn’t save", expect.any(String)));
        expect(saveDraft).toHaveBeenCalledWith(5, form, 12);
        expect(clearDraft).not.toHaveBeenCalled();
        expect(mockSetHasCharacteristics).not.toHaveBeenCalled();
        expect(mockSetHasOnboarded).not.toHaveBeenCalled();
        expect(mockReplace).not.toHaveBeenCalled();
    });
});
