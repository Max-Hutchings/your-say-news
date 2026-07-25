/**
 * User-characteristics domain types.
 */

/** An enum-backed option: `value` must match the backend Java enum constant exactly. */
export type Option = {
    label: string;
    value: string;
};

export const CHARACTERISTIC_OPTION_FIELDS = [
    "urbanRural",
    "gender",
    "sexAtBirth",
    "race",
    "sexualOrientation",
    "maritalStatus",
    "countryOfBirth",
    "citizenship",
    "ukCounty",
    "religion",
    "religiosity",
    "politicalPersuasion",
    "education",
    "occupation",
    "employmentSector",
    "universitySubject",
    "height",
    "weightRange",
    "incomeRange",
    "eyeColor",
    "parent",
    "petType",
    "chronotype",
    "outlook",
    "neurodivergenceType",
    "disabilityType",
    "housingStatus",
    "propertyType",
] as const;

export type CharacteristicOptionField = (typeof CHARACTERISTIC_OPTION_FIELDS)[number];

/** Versioned, backend-owned enum choices required to render characteristic onboarding. */
export type CharacteristicOptions = {
    schemaVersion: number;
    minimumAge: number;
    fields: Record<CharacteristicOptionField, Option[]>;
    incomeCatalog: IncomeCatalog;
};

export type IncomeProfileSummary = {
    profileId: string;
    profileVersion: number;
    marketCode: string;
    marketLabel: string;
    currencyCode: string;
    residenceCountryCodes: string[];
};

export type IncomeCatalog = {
    catalogVersion: string;
    profiles: IncomeProfileSummary[];
};

export type IncomeBand = {
    id: string;
    label: string;
    lowerInclusive: number | null;
    upperExclusive: number | null;
    tier: string;
};

export type IncomeProfile = IncomeProfileSummary & {
    catalogVersion: string;
    sourceYear: string;
    sourceUrl: string;
    derivation: string;
    confidence: string;
    personalBands: IncomeBand[];
    householdBands: IncomeBand[];
};

export type IncomeAnswer = {
    answerVersion: 2;
    catalogVersion: string;
    profileId: string;
    profileVersion: number;
    marketCode: string;
    currencyCode: string;
    personalBandId: string;
    householdBandId: string;
};

/**
 * The characteristic answers a user submits during onboarding. Keys mirror the backend
 * `UserCharacteristicDto` exactly so the payload maps 1:1.
 *
 * Deliberately carries NO identity (no userId / name / email): the authenticated identity travels
 * in the bearer token only, keeping PII separate from the characteristic data we aggregate on.
 */
export type CharacteristicAnswers = {
    // Location
    country: string;
    countryCode: string | null;
    city: string | null;
    region: string | null;
    ukCounty: string | null;
    urbanRural: string | null;
    // Who you are. `age` is a number (min 16); the server stores the derived birth year (ADR-017).
    age: number | null;
    gender: string | null;
    genderSelfDescribe: string;
    sexAtBirth: string | null;
    sexualOrientation: string | null;
    maritalStatus: string | null;
    race: string[];
    // Background. `citizenship` (nationality) is multi-select.
    countryOfBirth: string | null;
    citizenship: string[];
    religion: string | null;
    religiosity: string | null;
    politicalPersuasion: string | null;
    // Education & work
    education: string | null;
    occupation: string | null;
    employmentSector: string | null;
    universitySubject: string | null;
    // Finances & body
    income: IncomeAnswer | null;
    height: string | null;
    weightRange: string | null;
    eyeColor: string | null;
    parent: string | null;
    // Lifestyle. `petType` is multi-select and only carried when `hasPet` is true.
    hasPet: boolean | null;
    petType: string[];
    // Quirky
    chronotype: string | null;
    outlook: string | null;
    // Neurodiversity & disability. Types are multi-select, only carried when the flag is true.
    neurodivergent: boolean | null;
    neurodivergenceType: string[];
    hasDisability: boolean | null;
    disabilityType: string[];
    // Housing
    housingStatus: string | null;
    propertyType: string | null;
    // News habits
    newsFrequency: number | null;
    balancedNewsViewpoint: boolean | null;
    mainstreamNewsPercent: number | null;
    betterWorldWithData: boolean | null;
};
