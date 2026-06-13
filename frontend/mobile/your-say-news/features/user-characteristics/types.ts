/**
 * User-characteristics domain types.
 */

/** An enum-backed option: `value` must match the backend Java enum constant exactly. */
export type Option = {
    label: string;
    value: string;
};

/**
 * The characteristic answers a user submits during onboarding.
 *
 * Deliberately carries NO identity (no userId / name / email): the authenticated
 * identity travels in the bearer token only, keeping PII separate from the
 * characteristic data we aggregate on.
 */
export type CharacteristicAnswers = {
    location: { country: string; city: string };
    ageRange: string | null;
    gender: string | null;
    genderSelfDescribe: string;
    education: string | null;
    occupation: string | null;
    newsFrequency: number | null;
    race: string[];
    sexAtBirth: string | null;
    height: string | null;
    weightRange: string | null;
    incomeRange: string | null;
    parent: string | null;
    eyeColor: string | null;
    countryOfBirth: string | null;
    ukCounty: string | null;
    universitySubject: string | null;
};
