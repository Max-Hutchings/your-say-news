/**
 * Pure onboarding logic — required-field validation and answer-payload building.
 *
 * Kept free of React/UI so it can be unit-tested directly and so the PII-separation
 * guarantee (no identity in the payload) lives in one auditable place.
 */

import type { CharacteristicAnswers } from "./types";

/** Raw selections held by the onboarding form. */
export type OnboardingForm = {
    country: string;
    city: string;
    ageRange: string | null;
    gender: string | null;
    genderSelfDescribe: string;
    education: string | null;
    occupation: string | null;
    newsFrequencyScore: number | null;
    sexAtBirth: string | null;
    height: string | null;
    weightRange: string | null;
    incomeRange: string | null;
    parent: string | null;
    eyeColor: string | null;
    countryOfBirth: string | null;
    ukCounty: string | null;
    universitySubject: string | null;
    raceSelections: string[];
};

const SELF_DESCRIBE = "Prefer to self-describe";

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
 * Builds the submission payload. Carries ONLY characteristic answers — never
 * identity (userId / name / email), which is conveyed by the bearer token.
 */
export function buildCharacteristicAnswers(form: OnboardingForm): CharacteristicAnswers {
    return {
        location: { country: form.country, city: form.city },
        ageRange: form.ageRange,
        gender: form.gender,
        genderSelfDescribe: form.gender === SELF_DESCRIBE ? form.genderSelfDescribe : "",
        education: form.education,
        occupation: form.occupation,
        newsFrequency: form.newsFrequencyScore,
        race: form.raceSelections,
        sexAtBirth: form.sexAtBirth,
        height: form.height,
        weightRange: form.weightRange,
        incomeRange: form.incomeRange,
        parent: form.parent,
        eyeColor: form.eyeColor,
        countryOfBirth: form.countryOfBirth,
        ukCounty: form.ukCounty,
        universitySubject: form.universitySubject,
    };
}
