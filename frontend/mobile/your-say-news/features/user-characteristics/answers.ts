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
    personalIncomeRange: string | null;
    householdIncomeRange: string | null;
    height: string | null;
    weightRange: string | null;
    eyeColor: string | null;
    parent: string | null;
    newsFrequencyScore: number | null;
    // Lifestyle. `hasPet` holds the YES_NO chip value ("YES"/"NO"); `petType` only applies on "YES".
    hasPet: string | null;
    petType: string | null;
    // Quirky
    chronotype: string | null;
    outlook: string | null;
    // Neurodiversity & disability. The yes/no holds a YES_NO chip value; the type only applies on "YES".
    neurodivergent: string | null;
    neurodivergenceType: string | null;
    hasDisability: string | null;
    disabilityType: string | null;
    // Property. `propertyType` only applies when `housingStatus` is "OWN".
    housingStatus: string | null;
    propertyType: string | null;
    // News habits
    balancedNewsViewpoint: string | null;
    mainstreamNewsPercent: number;
    betterWorldWithData: string | null;
};

/** True only when every required field has a value. */
export function isRequiredComplete(form: OnboardingForm): boolean {
    return (
        form.country.trim().length > 0 &&
        form.urbanRural !== null &&
        form.ageRange !== null &&
        form.gender !== null &&
        form.raceSelections.length > 0 &&
        form.sexAtBirth !== null &&
        form.sexualOrientation !== null &&
        form.maritalStatus !== null &&
        form.countryOfBirth !== null &&
        form.citizenship !== null &&
        form.religion !== null &&
        form.religiosity !== null &&
        form.politicalPersuasion !== null &&
        form.education !== null &&
        form.occupation !== null &&
        form.employmentSector !== null &&
        form.height !== null &&
        form.weightRange !== null &&
        form.personalIncomeRange !== null &&
        form.householdIncomeRange !== null &&
        form.eyeColor !== null &&
        form.parent !== null &&
        form.newsFrequencyScore !== null &&
        form.hasPet !== null &&
        // petType is only required once the user says they have a pet.
        (form.hasPet !== "YES" || form.petType !== null) &&
        form.chronotype !== null &&
        form.outlook !== null &&
        form.neurodivergent !== null &&
        // neurodivergenceType is only required once the user says they are neurodivergent.
        (form.neurodivergent !== "YES" || form.neurodivergenceType !== null) &&
        form.hasDisability !== null &&
        // disabilityType is only required once the user says they have a disability.
        (form.hasDisability !== "YES" || form.disabilityType !== null) &&
        form.housingStatus !== null &&
        // propertyType is only required once the user says they own a property.
        (form.housingStatus !== "OWN" || form.propertyType !== null) &&
        form.balancedNewsViewpoint !== null &&
        form.betterWorldWithData !== null
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
        genderSelfDescribe: "",
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
        personalIncomeRange: form.personalIncomeRange,
        householdIncomeRange: form.householdIncomeRange,
        height: form.height,
        weightRange: form.weightRange,
        eyeColor: form.eyeColor,
        parent: deriveParent(form.parent, form.sexAtBirth),
        newsFrequency: form.newsFrequencyScore,
        hasPet: toBoolean(form.hasPet),
        // Drop any pet type unless the user actually owns a pet, so non-owners never carry one.
        petType: form.hasPet === "YES" ? form.petType : null,
        chronotype: form.chronotype,
        outlook: form.outlook,
        neurodivergent: toBoolean(form.neurodivergent),
        // Drop the type unless the user is neurodivergent, so others never carry one.
        neurodivergenceType: form.neurodivergent === "YES" ? form.neurodivergenceType : null,
        hasDisability: toBoolean(form.hasDisability),
        disabilityType: form.hasDisability === "YES" ? form.disabilityType : null,
        housingStatus: form.housingStatus,
        // Drop property type unless the user owns, so non-owners never carry one.
        propertyType: form.housingStatus === "OWN" ? form.propertyType : null,
    };
}

function deriveParent(parent: string | null, sexAtBirth: string | null): string | null {
    if (parent === null) return null;
    if (parent === "NO") return "NO";
    return sexAtBirth === "FEMALE" ? "MUM" : "DAD";
}

function toBoolean(yesNo: string | null): boolean | null {
    if (yesNo === null) return null;
    return yesNo === "YES";
}

function emptyToNull(value: string): string | null {
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : null;
}
