import Constants from "expo-constants";
import { YsnHttpClient } from "@/features/auth";
import {
    CHARACTERISTIC_OPTION_FIELDS,
    type CharacteristicOptions,
    type Option,
} from "../types";

const extra = Constants.expoConfig?.extra ?? {};
const OPTIONS_URL =
    `${extra.CHARACTERISTIC_SERVICE_HOST}${extra.CHARACTERISTIC_SERVICE_PORT}/user-characteristics/options`;

const RETRY_DELAYS_MS = [250, 750];

/**
 * Loads the backend-owned onboarding choices, retrying transient startup/network failures twice
 * before surfacing the error to the retry screen.
 */
export async function fetchCharacteristicOptions(
    retryDelaysMs: readonly number[] = RETRY_DELAYS_MS
): Promise<CharacteristicOptions> {
    let lastError: unknown;

    for (let attempt = 0; attempt <= retryDelaysMs.length; attempt += 1) {
        try {
            const response = await YsnHttpClient.getSecure().get(OPTIONS_URL);
            return parseCharacteristicOptions(response.data);
        } catch (error) {
            lastError = error;
            if (attempt < retryDelaysMs.length) {
                await wait(retryDelaysMs[attempt]);
            }
        }
    }

    throw lastError instanceof Error
        ? lastError
        : new Error("Could not load characteristic options");
}

function parseCharacteristicOptions(value: unknown): CharacteristicOptions {
    if (!isObject(value) || value.schemaVersion !== 1 || !isPositiveInteger(value.minimumAge) || !isObject(value.fields)) {
        throw new Error("The characteristic options response is incompatible with this app");
    }

    for (const field of CHARACTERISTIC_OPTION_FIELDS) {
        const options = value.fields[field];
        if (!Array.isArray(options) || options.length === 0 || !options.every(isOption)) {
            throw new Error(`The characteristic options response is missing ${field}`);
        }
    }

    return value as CharacteristicOptions;
}

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null;
}

function isPositiveInteger(value: unknown): value is number {
    return typeof value === "number" && Number.isInteger(value) && value > 0;
}

function isOption(value: unknown): value is Option {
    return isObject(value) &&
        typeof value.label === "string" && value.label.length > 0 &&
        typeof value.value === "string" && value.value.length > 0;
}

function wait(milliseconds: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, milliseconds));
}
