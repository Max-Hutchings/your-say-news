/**
 * User-characteristics domain types.
 */

/** An enum-backed option: `value` must match the backend Java enum constant exactly. */
export type Option = {
    label: string;
    value: string;
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
    // Who you are
    ageRange: string | null;
    gender: string | null;
    genderSelfDescribe: string;
    sexAtBirth: string | null;
    sexualOrientation: string | null;
    maritalStatus: string | null;
    race: string[];
    // Background
    countryOfBirth: string | null;
    citizenship: string | null;
    religion: string | null;
    religiosity: string | null;
    politicalPersuasion: string | null;
    // Education & work
    education: string | null;
    occupation: string | null;
    employmentSector: string | null;
    universitySubject: string | null;
    // Finances & body
    incomeRange: string | null;
    height: string | null;
    weightRange: string | null;
    eyeColor: string | null;
    parent: string | null;
    newsFrequency: number | null;
};
