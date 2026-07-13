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
    personalIncomeRange: string | null;
    householdIncomeRange: string | null;
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
