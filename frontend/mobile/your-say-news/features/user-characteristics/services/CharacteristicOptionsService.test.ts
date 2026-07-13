import { CHARACTERISTIC_OPTION_FIELDS, type CharacteristicOptions } from "../types";
import { fetchCharacteristicOptions } from "./CharacteristicOptionsService";
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
    };
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
