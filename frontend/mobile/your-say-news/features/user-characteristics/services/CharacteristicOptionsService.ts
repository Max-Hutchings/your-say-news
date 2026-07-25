import Constants from "expo-constants";
import { YsnHttpClient } from "@/features/auth";
import {
    CHARACTERISTIC_OPTION_FIELDS,
    type CharacteristicOptions,
    type IncomeBand,
    type IncomeProfile,
    type Option,
} from "../types";

const extra = Constants.expoConfig?.extra ?? {};
const OPTIONS_URL =
    `${extra.CHARACTERISTIC_SERVICE_HOST}${extra.CHARACTERISTIC_SERVICE_PORT}/user-characteristics/options`;
const INCOME_OPTIONS_URL =
    `${extra.CHARACTERISTIC_SERVICE_HOST}${extra.CHARACTERISTIC_SERVICE_PORT}/user-characteristics/income-options`;

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

export async function fetchIncomeProfile(
    marketCode: string,
    currencyCode: string
): Promise<IncomeProfile> {
    const response = await YsnHttpClient.getSecure().get(
        `${INCOME_OPTIONS_URL}?marketCode=${encodeURIComponent(marketCode)}`
        + `&currencyCode=${encodeURIComponent(currencyCode)}`
    );
    const profile = parseIncomeProfile(response.data);
    if (profile.marketCode !== marketCode || profile.currencyCode !== currencyCode) {
        throw new Error("The income options response does not match the requested market and currency");
    }
    return profile;
}

function parseCharacteristicOptions(value: unknown): CharacteristicOptions {
    if (!isObject(value) || value.schemaVersion !== 1 || !isPositiveInteger(value.minimumAge)
        || !isObject(value.fields) || !isIncomeCatalog(value.incomeCatalog)) {
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

function parseIncomeProfile(value: unknown): IncomeProfile {
    if (!isObject(value)) {
        throw new Error("The income options response is incompatible with this app");
    }
    const record: Record<string, unknown> = value;
    if (!isIncomeProfileSummary(record)
        || typeof record.catalogVersion !== "string"
        || typeof record.sourceYear !== "string"
        || typeof record.sourceUrl !== "string"
        || typeof record.derivation !== "string"
        || typeof record.confidence !== "string"
        || !isIncomeBands(record.personalBands, "PERSONAL")
        || !isIncomeBands(record.householdBands, "HOUSEHOLD")) {
        throw new Error("The income options response is incompatible with this app");
    }
    return value as IncomeProfile;
}

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null;
}

function isPositiveInteger(value: unknown): value is number {
    return typeof value === "number" && Number.isInteger(value) && value > 0;
}

function isIncomeCatalog(value: unknown): boolean {
    return isObject(value)
        && typeof value.catalogVersion === "string"
        && Array.isArray(value.profiles)
        && value.profiles.length > 0
        && value.profiles.every(isIncomeProfileSummary);
}

function isIncomeProfileSummary(value: unknown): boolean {
    return isObject(value)
        && typeof value.profileId === "string"
        && isPositiveInteger(value.profileVersion)
        && typeof value.marketCode === "string"
        && typeof value.marketLabel === "string"
        && typeof value.currencyCode === "string"
        && Array.isArray(value.residenceCountryCodes)
        && value.residenceCountryCodes.every((country) => typeof country === "string");
}

function isIncomeBands(value: unknown, measure: "PERSONAL" | "HOUSEHOLD"): value is IncomeBand[] {
    if (!Array.isArray(value) || value.length !== 7) {
        return false;
    }
    const ids = new Set<string>();
    for (let index = 0; index < value.length; index += 1) {
        const band = value[index];
        if (!isObject(band)
            || typeof band.id !== "string"
            || !band.id.startsWith(`${measure}_`)
            || ids.has(band.id)
            || typeof band.label !== "string"
            || band.label.length === 0
            || (band.lowerInclusive !== null
                && (typeof band.lowerInclusive !== "number" || !Number.isFinite(band.lowerInclusive)))
            || (band.upperExclusive !== null
                && (typeof band.upperExclusive !== "number" || !Number.isFinite(band.upperExclusive)))
            || band.tier !== `TIER_${index + 1}`
            || (index === 0 && band.lowerInclusive !== null)
            || (index > 0 && band.lowerInclusive !== value[index - 1].upperExclusive)
            || (index === value.length - 1 && band.upperExclusive !== null)
            || (typeof band.lowerInclusive === "number"
                && typeof band.upperExclusive === "number"
                && band.lowerInclusive >= band.upperExclusive)) {
            return false;
        }
        ids.add(band.id);
    }
    return true;
}

function isOption(value: unknown): value is Option {
    return isObject(value) &&
        typeof value.label === "string" && value.label.length > 0 &&
        typeof value.value === "string" && value.value.length > 0;
}

function wait(milliseconds: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, milliseconds));
}
