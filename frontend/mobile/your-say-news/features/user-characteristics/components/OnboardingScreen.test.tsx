import React from "react";
import { Alert, ScrollView } from "react-native";
import { fireEvent, render, waitFor } from "@testing-library/react-native";
import {
    CHARACTERISTIC_OPTION_FIELDS,
    type CharacteristicOptions,
    type IncomeBand,
    type IncomeProfile,
} from "../types";
import type { OnboardingForm } from "../answers";
import {
    fetchCharacteristicOptions,
    fetchIncomeProfile,
} from "../services/CharacteristicOptionsService";
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
    fetchIncomeProfile: jest.fn(),
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
const fetchProfile = fetchIncomeProfile as jest.MockedFunction<typeof fetchIncomeProfile>;
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
        incomeCatalog: {
            catalogVersion: "2026.1",
            profiles: [
                {
                    profileId: "GB-GBP-GROSS-2025-v1",
                    profileVersion: 1,
                    marketCode: "GB",
                    marketLabel: "United Kingdom",
                    currencyCode: "GBP",
                    residenceCountryCodes: ["UNITED_KINGDOM"],
                },
                {
                    profileId: "IN-INR-GROSS-2023-24-v1",
                    profileVersion: 1,
                    marketCode: "IN",
                    marketLabel: "India",
                    currencyCode: "INR",
                    residenceCountryCodes: ["INDIA"],
                },
            ],
        },
    };
}

function incomeProfile(marketCode: "GB" | "IN"): IncomeProfile {
    const india = marketCode === "IN";
    const personalBoundaries = india
        ? [200_000, 400_000, 700_000, 1_200_000, 2_000_000, 3_500_000]
        : [15_000, 25_000, 40_000, 60_000, 90_000, 140_000];
    const householdBoundaries = india
        ? [300_000, 600_000, 1_000_000, 1_800_000, 3_000_000, 5_000_000]
        : [20_000, 35_000, 55_000, 85_000, 130_000, 200_000];
    return {
        profileId: india ? "IN-INR-GROSS-2023-24-v1" : "GB-GBP-GROSS-2025-v1",
        profileVersion: 1,
        marketCode,
        marketLabel: india ? "India" : "United Kingdom",
        currencyCode: india ? "INR" : "GBP",
        residenceCountryCodes: [india ? "INDIA" : "UNITED_KINGDOM"],
        catalogVersion: "2026.1",
        sourceYear: india ? "2023-24" : "2025",
        sourceUrl: "https://example.test/evidence",
        derivation: "Reviewed local evidence",
        confidence: india ? "MEDIUM" : "HIGH",
        personalBands: screenIncomeBands(
            "PERSONAL",
            personalBoundaries,
            india ? "Under INR 2 lakh" : "Under GBP 15k"
        ),
        householdBands: screenIncomeBands(
            "HOUSEHOLD",
            householdBoundaries,
            india ? "Under INR 3 lakh" : "Under GBP 20k"
        ),
    };
}

function screenIncomeBands(
    measure: "PERSONAL" | "HOUSEHOLD",
    boundaries: number[],
    firstLabel: string
): IncomeBand[] {
    return Array.from({ length: 7 }, (_, index) => ({
        id: `${measure}_TIER_${index + 1}`,
        label: index === 0 ? firstLabel : `${measure.toLowerCase()} tier ${index + 1}`,
        lowerInclusive: index === 0 ? null : boundaries[index - 1],
        upperExclusive: index === 6 ? null : boundaries[index],
        tier: `TIER_${index + 1}`,
    }));
}

function completeForm(): OnboardingForm {
    return {
        country: "United Kingdom", countryCode: "UNITED_KINGDOM",
        city: "Bristol", region: "", ukCounty: "BRISTOL", urbanRural: "URBAN",
        age: 30, gender: "WOMAN", genderSelfDescribe: "", sexAtBirth: "FEMALE",
        sexualOrientation: "STRAIGHT_HETEROSEXUAL", maritalStatus: "SINGLE",
        raceSelections: ["WHITE_EUROPEAN"], countryOfBirth: "UNITED_KINGDOM", citizenship: ["BRITISH"],
        religion: "NO_RELIGION", religiosity: "NOT_RELIGIOUS", politicalPersuasion: "CENTRE_LEFT",
        education: "BACHELORS", occupation: "EMPLOYED_FULL_TIME", employmentSector: "IT_TECHNOLOGY",
        universitySubject: "COMPUTER_SCIENCE", currency: "GBP",
        incomeCatalogVersion: "2026.1", incomeProfileId: "GB-GBP-GROSS-2025-v1",
        incomeProfileVersion: 1, incomeMarketCode: "GB",
        personalIncomeBandId: "PERSONAL_TIER_1", householdIncomeBandId: "HOUSEHOLD_TIER_1",
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
        fetchProfile.mockImplementation(async (marketCode) => incomeProfile(marketCode as "GB" | "IN"));
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

    it("starts Body basics with a fresh scroll view at the top", async () => {
        fetchOptions.mockResolvedValue(validOptions());

        const screen = render(<OnboardingScreen />);
        for (let nextStep = 2; nextStep <= 6; nextStep += 1) {
            fireEvent.press(await screen.findByText("Continue"));
            await screen.findByText(`STEP ${nextStep} OF 13`);
        }
        const educationAndWorkScrollView = screen.UNSAFE_getByType(ScrollView);

        fireEvent.press(screen.getByText("Continue"));

        expect(await screen.findByText("Body basics")).toBeTruthy();
        const bodyBasicsScrollView = screen.UNSAFE_getByType(ScrollView);
        expect(bodyBasicsScrollView).not.toBe(educationAndWorkScrollView);
        expect(bodyBasicsScrollView.props.contentOffset).toEqual({ x: 0, y: 0 });
    });

    it("loads locally meaningful Indian personal and household income bands", async () => {
        mockUserId = 5;
        loadDraft.mockResolvedValue({
            form: {
                ...completeForm(),
                country: "India",
                countryCode: "INDIA",
                currency: "INR",
                incomeProfileId: "IN-INR-GROSS-2023-24-v1",
                incomeMarketCode: "IN",
                personalIncomeBandId: null,
                householdIncomeBandId: null,
            },
            nextStep: 9,
        });
        fetchOptions.mockResolvedValue(validOptions());

        const screen = render(<OnboardingScreen />);

        expect(await screen.findByText("Bands are calibrated for India and used only in aggregate.")).toBeTruthy();
        expect(screen.getByText("Under INR 2 lakh")).toBeTruthy();
        expect(screen.getByText("Under INR 3 lakh")).toBeTruthy();
        expect(fetchProfile).toHaveBeenCalledWith("IN", "INR");
    });

    it("refetches and clears both prior bands when the market currency changes", async () => {
        mockUserId = 5;
        loadDraft.mockResolvedValue({
            form: {
                ...completeForm(),
                country: "Nepal",
                countryCode: "NEPAL",
            },
            nextStep: 9,
        });
        fetchOptions.mockResolvedValue(validOptions());

        const screen = render(<OnboardingScreen />);
        expect(await screen.findByText("Under GBP 15k")).toBeTruthy();

        fireEvent.press(screen.getByText("INR"));

        expect(await screen.findByText("Under INR 2 lakh")).toBeTruthy();
        expect(screen.queryByText("Under GBP 15k")).toBeNull();
        fireEvent.press(screen.getByText("Continue"));

        await waitFor(() => expect(saveDraft).toHaveBeenCalledWith(
            5,
            expect.objectContaining({
                currency: "INR",
                incomeProfileId: "IN-INR-GROSS-2023-24-v1",
                personalIncomeBandId: null,
                householdIncomeBandId: null,
            }),
            10
        ));
        expect(fetchProfile).toHaveBeenCalledWith("IN", "INR");
    });

    it("does not render bands from a stale profile version", async () => {
        mockUserId = 5;
        loadDraft.mockResolvedValue({ form: completeForm(), nextStep: 9 });
        fetchOptions.mockResolvedValue(validOptions());
        fetchProfile.mockResolvedValue({
            ...incomeProfile("GB"),
            catalogVersion: "2025.9",
        });

        const screen = render(<OnboardingScreen />);

        expect(await screen.findByText("We couldn’t load income bands for this currency.")).toBeTruthy();
        expect(screen.queryByText("Under GBP 15k")).toBeNull();
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
