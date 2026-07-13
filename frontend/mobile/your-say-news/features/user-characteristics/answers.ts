/**
 * Pure onboarding logic — required-field validation and answer-payload building.
 *
 * Kept free of React/UI so it can be unit-tested directly and so the PII-separation
 * guarantee (no identity in the payload) lives in one auditable place.
 */

import type { CharacteristicAnswers } from "./types";

/** Education answers that reveal the optional university-subject question. */
const HIGHER_EDUCATION_VALUES = [
    "HIGHER_EDUCATION_BELOW_DEGREE",
    "BACHELORS",
    "MASTERS",
    "DOCTORATE",
];

/** Raw selections held by the onboarding form. Values are enum constants (or free text/number). */
export type OnboardingForm = {
    // Location
    country: string;
    city: string;
    region: string;
    ukCounty: string | null;
    urbanRural: string | null;
    // Who you are. Age is a number (min 16); gender self-describe applies only when SELF_DESCRIBE.
    age: number | null;
    gender: string | null;
    genderSelfDescribe: string;
    sexAtBirth: string | null;
    sexualOrientation: string | null;
    maritalStatus: string | null;
    raceSelections: string[];
    // Background. Nationality is multi-select.
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
    currency: string;
    personalIncomeRange: string | null;
    householdIncomeRange: string | null;
    height: string | null;
    weightRange: string | null;
    eyeColor: string | null;
    // Family
    parent: string | null;
    // Lifestyle. `hasPet` holds the YES_NO chip value; `petType` (multi) only applies on "YES".
    hasPet: string | null;
    petType: string[];
    // Quirky
    chronotype: string | null;
    outlook: string | null;
    // Neurodiversity & disability. The yes/no holds a YES_NO chip value; the type list only applies on "YES".
    neurodivergent: string | null;
    neurodivergenceType: string[];
    hasDisability: string | null;
    disabilityType: string[];
    // Housing. `propertyType` applies to everyone except no-fixed-address.
    housingStatus: string | null;
    propertyType: string | null;
    // News habits
    newsFrequencyScore: number | null;
    balancedNewsViewpoint: string | null;
    mainstreamNewsPercent: number;
    betterWorldWithData: string | null;
};

/** A clean, resumable onboarding form. Kept here so the screen and draft storage share one shape. */
export function createEmptyOnboardingForm(): OnboardingForm {
    return {
        country: "",
        city: "",
        region: "",
        ukCounty: null,
        urbanRural: null,
        age: null,
        gender: null,
        genderSelfDescribe: "",
        sexAtBirth: null,
        sexualOrientation: null,
        maritalStatus: null,
        raceSelections: [],
        countryOfBirth: null,
        citizenship: [],
        religion: null,
        religiosity: null,
        politicalPersuasion: null,
        education: null,
        occupation: null,
        employmentSector: null,
        universitySubject: null,
        currency: "USD",
        personalIncomeRange: null,
        householdIncomeRange: null,
        height: null,
        weightRange: null,
        eyeColor: null,
        parent: null,
        hasPet: null,
        petType: [],
        chronotype: null,
        outlook: null,
        neurodivergent: null,
        neurodivergenceType: [],
        hasDisability: null,
        disabilityType: [],
        housingStatus: null,
        propertyType: null,
        newsFrequencyScore: null,
        balancedNewsViewpoint: null,
        mainstreamNewsPercent: 50,
        betterWorldWithData: null,
    };
}

/** True when the education answer makes the university-subject question relevant. */
export function isHigherEducation(education: string | null): boolean {
    return education !== null && HIGHER_EDUCATION_VALUES.includes(education);
}

/** True when housing means the user has no fixed home to describe a type for. */
function isNoFixedAddress(housingStatus: string | null): boolean {
    return housingStatus === "TEMPORARY_NO_FIXED";
}

export type IncompleteOnboardingStep = {
    step: number;
    fieldLabel: string;
};

/**
 * Finds the first required answer that is missing, in the same order as the wizard pages.
 * Returning the page as well as the label lets the UI take a user back to a skipped question
 * instead of making the final button appear unresponsive.
 */
export function findFirstIncompleteStep(
    form: OnboardingForm,
    minimumAge: number
): IncompleteOnboardingStep | null {
    const incomplete = (step: number, fieldLabel: string): IncompleteOnboardingStep => ({ step, fieldLabel });

    if (form.country.trim().length === 0) return incomplete(0, "Country of residence");
    if (!hasValue(form.urbanRural)) return incomplete(0, "Settlement type");

    if (form.age === null || !Number.isFinite(form.age) || form.age < minimumAge) {
        return incomplete(1, `Your age (${minimumAge} or older)`);
    }
    if (!hasValue(form.gender)) return incomplete(1, "Gender");
    if (form.gender === "SELF_DESCRIBE" && form.genderSelfDescribe.trim().length === 0) {
        return incomplete(1, "Describe your gender");
    }
    if (!hasValue(form.sexAtBirth)) return incomplete(1, "Sex registered at birth");

    if (form.raceSelections.length === 0) return incomplete(2, "Ethnic background");
    if (!hasValue(form.sexualOrientation)) return incomplete(2, "Sexual orientation");
    if (!hasValue(form.maritalStatus)) return incomplete(2, "Relationship status");

    if (!hasValue(form.countryOfBirth)) return incomplete(3, "Country of birth");
    if (form.citizenship.length === 0) return incomplete(3, "Nationality");

    if (!hasValue(form.politicalPersuasion)) return incomplete(4, "Political leaning");
    if (!hasValue(form.religion)) return incomplete(4, "Religion");
    if (!hasValue(form.religiosity)) return incomplete(4, "Importance of religion");

    if (!hasValue(form.education)) return incomplete(5, "Highest education");
    if (!hasValue(form.occupation)) return incomplete(5, "Current work or study status");
    if (!hasValue(form.employmentSector)) return incomplete(5, "Industry / sector");

    if (!hasValue(form.height)) return incomplete(6, "Height");
    if (!hasValue(form.weightRange)) return incomplete(6, "Weight");
    if (!hasValue(form.eyeColor)) return incomplete(6, "Eye colour");

    if (!hasValue(form.parent)) return incomplete(7, "Parent or caregiver");
    if (!hasValue(form.hasPet)) return incomplete(7, "Pet ownership");
    if (form.hasPet === "YES" && form.petType.length === 0) return incomplete(7, "Kinds of pet");

    if (!hasValue(form.chronotype)) return incomplete(8, "Morning or evening person");
    if (!hasValue(form.outlook)) return incomplete(8, "Feelings about the future");

    if (!hasValue(form.personalIncomeRange)) return incomplete(9, "Personal income");
    if (!hasValue(form.householdIncomeRange)) return incomplete(9, "Household income");

    if (!hasValue(form.balancedNewsViewpoint)) return incomplete(10, "Seeing more than one news viewpoint");
    if (!inRange(form.mainstreamNewsPercent, 0, 100)) return incomplete(10, "Mainstream news percentage");
    if (!inRange(form.newsFrequencyScore, 0, 10)) return incomplete(10, "News-following frequency");
    if (!hasValue(form.betterWorldWithData)) return incomplete(10, "Public-opinion data");

    if (!hasValue(form.neurodivergent)) return incomplete(11, "Neurodivergence");
    if (form.neurodivergent === "YES" && form.neurodivergenceType.length === 0) {
        return incomplete(11, "Neurodivergence type");
    }
    if (!hasValue(form.hasDisability)) return incomplete(11, "Long-term condition or limitation");
    if (form.hasDisability === "YES" && form.disabilityType.length === 0) {
        return incomplete(11, "Condition or limitation type");
    }

    if (!hasValue(form.housingStatus)) return incomplete(12, "Housing situation");
    if (!isNoFixedAddress(form.housingStatus) && !hasValue(form.propertyType)) {
        return incomplete(12, "Type of home");
    }

    return null;
}

/** True only when every required field has a valid value. */
export function isRequiredComplete(form: OnboardingForm, minimumAge: number): boolean {
    return findFirstIncompleteStep(form, minimumAge) === null;
}

function hasValue(value: string | null): value is string {
    return value !== null && value.trim().length > 0;
}

function inRange(value: number | null, minimum: number, maximum: number): value is number {
    return value !== null && Number.isFinite(value) && value >= minimum && value <= maximum;
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
        age: form.age,
        gender: form.gender,
        // Free-text self-description is carried only when the user chose to self-describe.
        genderSelfDescribe: form.gender === "SELF_DESCRIBE" ? form.genderSelfDescribe.trim() : "",
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
        // University subject only applies to higher-education answers.
        universitySubject: isHigherEducation(form.education) ? form.universitySubject : null,
        personalIncomeRange: form.personalIncomeRange,
        householdIncomeRange: form.householdIncomeRange,
        height: form.height,
        weightRange: form.weightRange,
        eyeColor: form.eyeColor,
        parent: form.parent,
        hasPet: toBoolean(form.hasPet),
        // Drop any pet types unless the user actually owns a pet, so non-owners never carry any.
        petType: form.hasPet === "YES" ? form.petType : [],
        chronotype: form.chronotype,
        outlook: form.outlook,
        neurodivergent: toBoolean(form.neurodivergent),
        // Drop the types unless the user is neurodivergent, so others never carry any.
        neurodivergenceType: form.neurodivergent === "YES" ? form.neurodivergenceType : [],
        hasDisability: toBoolean(form.hasDisability),
        disabilityType: form.hasDisability === "YES" ? form.disabilityType : [],
        housingStatus: form.housingStatus,
        // No-fixed-address users have no home type to report.
        propertyType: isNoFixedAddress(form.housingStatus) ? null : form.propertyType,
        newsFrequency: form.newsFrequencyScore,
        balancedNewsViewpoint: toBoolean(form.balancedNewsViewpoint),
        mainstreamNewsPercent: form.mainstreamNewsPercent,
        betterWorldWithData: toBoolean(form.betterWorldWithData),
    };
}

function toBoolean(yesNo: string | null): boolean | null {
    if (yesNo === null) return null;
    return yesNo === "YES";
}

function emptyToNull(value: string): string | null {
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : null;
}
