/**
 * Pure onboarding logic — required-field validation and answer-payload building.
 *
 * Kept free of React/UI so it can be unit-tested directly and so the PII-separation
 * guarantee (no identity in the payload) lives in one auditable place.
 */

import type { CharacteristicAnswers } from "./types";

/** Raw selections held by the onboarding form. Values are enum constants (or free text/number). */
export type OnboardingForm = {
    // Location
    country: string;
    city: string;
    region: string;
    ukCounty: string | null;
    urbanRural: string | null;
    // Who you are
    ageRange: string | null;
    gender: string | null;
    genderSelfDescribe: string;
    sexAtBirth: string | null;
    sexualOrientation: string | null;
    maritalStatus: string | null;
    raceSelections: string[];
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
    newsFrequencyScore: number | null;
};

const SELF_DESCRIBE = "SELF_DESCRIBE";

/** True only when every required field has a value. */
export function isRequiredComplete(form: OnboardingForm): boolean {
    return (
        form.country.trim().length > 0 &&
        form.ageRange !== null &&
        form.gender !== null &&
        form.raceSelections.length > 0 &&
        form.sexAtBirth !== null &&
        form.height !== null &&
        form.weightRange !== null &&
        form.incomeRange !== null
    );
}

/**
 * Builds the submission payload. Carries ONLY characteristic answers — never identity
 * (userId / name / email), which is conveyed by the bearer token.
 */
export function buildCharacteristicAnswers(form: OnboardingForm): CharacteristicAnswers {
    return {
        country: form.country.trim(),
        city: emptyToNull(form.city),
        region: emptyToNull(form.region),
        ukCounty: form.ukCounty,
        urbanRural: form.urbanRural,
        ageRange: form.ageRange,
        gender: form.gender,
        genderSelfDescribe: form.gender === SELF_DESCRIBE ? form.genderSelfDescribe : "",
        sexAtBirth: form.sexAtBirth,
        sexualOrientation: form.sexualOrientation,
        maritalStatus: form.maritalStatus,
        race: form.raceSelections,
        countryOfBirth: form.countryOfBirth,
        citizenship: form.citizenship,
        religion: form.religion,
        religiosity: form.religiosity,
        politicalPersuasion: form.politicalPersuasion,
        education: form.education,
        occupation: form.occupation,
        employmentSector: form.employmentSector,
        universitySubject: form.universitySubject,
        incomeRange: form.incomeRange,
        height: form.height,
        weightRange: form.weightRange,
        eyeColor: form.eyeColor,
        parent: form.parent,
        newsFrequency: form.newsFrequencyScore,
    };
}

function emptyToNull(value: string): string | null {
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : null;
}
