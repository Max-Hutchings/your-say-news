import {
    CHARACTERISTIC_OPTION_FIELDS,
    type CharacteristicOptions,
    type IncomeBand,
    type IncomeProfile,
} from "../types";
import {
    fetchCharacteristicOptions,
    fetchIncomeProfile,
} from "./CharacteristicOptionsService";
import { YsnHttpClient } from "@/features/auth";

jest.mock("expo-constants", () => ({
    __esModule: true,
    default: {
        expoConfig: {
            extra: {
                CHARACTERISTIC_SERVICE_HOST: "http://localhost",
                CHARACTERISTIC_SERVICE_PORT: ":8081",
            },
        },
    },
}));

jest.mock("@/features/auth", () => ({
    YsnHttpClient: {
        getSecure: jest.fn(),
    },
}));

const getSecure = YsnHttpClient.getSecure as jest.Mock;
const get = jest.fn();

function validOptions(): CharacteristicOptions {
    return {
        schemaVersion: 1,
        minimumAge: 16,
        fields: Object.fromEntries(
            CHARACTERISTIC_OPTION_FIELDS.map((field) => [
                field,
                [{ label: `${field} label`, value: `${field.toUpperCase()}_VALUE` }],
            ])
        ) as CharacteristicOptions["fields"],
        incomeCatalog: {
            catalogVersion: "2026.1",
            profiles: [{
                profileId: "GB-GBP-GROSS-2025-v1",
                profileVersion: 1,
                marketCode: "GB",
                marketLabel: "United Kingdom",
                currencyCode: "GBP",
                residenceCountryCodes: ["UNITED_KINGDOM"],
            }],
        },
    };
}

function validIncomeProfile(): IncomeProfile {
    return {
        ...validOptions().incomeCatalog.profiles[0],
        catalogVersion: "2026.1",
        sourceYear: "2025",
        sourceUrl: "https://example.test/source",
        derivation: "Reviewed local evidence",
        confidence: "HIGH",
        personalBands: incomeBands("PERSONAL", [15_000, 25_000, 40_000, 60_000, 90_000, 140_000]),
        householdBands: incomeBands("HOUSEHOLD", [20_000, 35_000, 55_000, 85_000, 130_000, 200_000]),
    };
}

function incomeBands(
    measure: "PERSONAL" | "HOUSEHOLD",
    boundaries: number[]
): IncomeBand[] {
    return Array.from({ length: 7 }, (_, index) => ({
        id: `${measure}_TIER_${index + 1}`,
        label: `${measure === "PERSONAL" ? "Personal" : "Household"} tier ${index + 1}`,
        lowerInclusive: index === 0 ? null : boundaries[index - 1],
        upperExclusive: index === 6 ? null : boundaries[index],
        tier: `TIER_${index + 1}`,
    }));
}

describe("fetchCharacteristicOptions", () => {
    beforeEach(() => {
        get.mockReset();
        getSecure.mockReturnValue({ get });
    });

    it("returns the complete versioned backend catalogue", async () => {
        const options = validOptions();
        get.mockResolvedValueOnce({ data: options });

        await expect(fetchCharacteristicOptions([])).resolves.toEqual(options);
        expect(get).toHaveBeenCalledWith("http://localhost:8081/user-characteristics/options");
    });

    it("retries two failures and returns the successful third response", async () => {
        const options = validOptions();
        get
            .mockRejectedValueOnce(new Error("service starting"))
            .mockRejectedValueOnce(new Error("connection reset"))
            .mockResolvedValueOnce({ data: options });

        await expect(fetchCharacteristicOptions([0, 0])).resolves.toEqual(options);
        expect(get).toHaveBeenCalledTimes(3);
    });

    it("surfaces the final failure after all attempts", async () => {
        get.mockRejectedValue(new Error("offline"));

        await expect(fetchCharacteristicOptions([0, 0])).rejects.toThrow("offline");
        expect(get).toHaveBeenCalledTimes(3);
    });

    it("rejects an incomplete catalogue instead of rendering empty questions", async () => {
        const options = validOptions();
        delete (options.fields as Partial<CharacteristicOptions["fields"]>).petType;
        get.mockResolvedValue({ data: options });

        await expect(fetchCharacteristicOptions([])).rejects.toThrow(
            "The characteristic options response is missing petType"
        );
    });

    it.each([
        ["an unsupported schema", (options: CharacteristicOptions) => { options.schemaVersion = 2; }, "incompatible"],
        ["an invalid minimum age", (options: CharacteristicOptions) => { options.minimumAge = 0; }, "incompatible"],
        ["an empty field", (options: CharacteristicOptions) => { options.fields.petType = []; }, "missing petType"],
        ["a blank option label", (options: CharacteristicOptions) => { options.fields.petType[0].label = ""; }, "missing petType"],
        ["a blank option value", (options: CharacteristicOptions) => { options.fields.petType[0].value = ""; }, "missing petType"],
        ["a non-string option value", (options: CharacteristicOptions) => {
            (options.fields.petType[0] as unknown as { value: number }).value = 1;
        }, "missing petType"],
    ] as const)("rejects %s", async (_label, corrupt, expectedMessage) => {
        const options = validOptions();
        corrupt(options);
        get.mockResolvedValue({ data: options });

        await expect(fetchCharacteristicOptions([])).rejects.toThrow(expectedMessage);
    });
});

describe("fetchIncomeProfile", () => {
    beforeEach(() => {
        get.mockReset();
        getSecure.mockReturnValue({ get });
    });

    it("requests the selected market and currency and preserves distinct band sets", async () => {
        const profile = validIncomeProfile();
        get.mockResolvedValueOnce({ data: profile });

        await expect(fetchIncomeProfile("GB", "GBP")).resolves.toEqual(profile);
        expect(get).toHaveBeenCalledWith(
            "http://localhost:8081/user-characteristics/income-options?marketCode=GB&currencyCode=GBP"
        );
    });

    it("rejects a profile without household bands", async () => {
        const profile = validIncomeProfile();
        get.mockResolvedValueOnce({ data: { ...profile, householdBands: [] } });

        await expect(fetchIncomeProfile("GB", "GBP")).rejects.toThrow(
            "The income options response is incompatible with this app"
        );
    });

    it.each([
        ["a truncated band set", (profile: IncomeProfile) => {
            profile.personalBands = profile.personalBands.slice(0, 1);
        }],
        ["a gap", (profile: IncomeProfile) => {
            profile.personalBands[1].lowerInclusive = 16_000;
        }],
        ["a duplicate band id", (profile: IncomeProfile) => {
            profile.personalBands[1].id = profile.personalBands[0].id;
        }],
        ["an out-of-order tier", (profile: IncomeProfile) => {
            profile.personalBands[1].tier = "TIER_3";
        }],
    ])("rejects %s", async (_label, corrupt) => {
        const profile = validIncomeProfile();
        corrupt(profile);
        get.mockResolvedValueOnce({ data: profile });

        await expect(fetchIncomeProfile("GB", "GBP")).rejects.toThrow("incompatible");
    });

    it("rejects a valid profile returned for a different requested market or currency", async () => {
        get.mockResolvedValueOnce({ data: validIncomeProfile() });

        await expect(fetchIncomeProfile("IN", "INR")).rejects.toThrow(
            "does not match the requested market and currency"
        );
    });
});
