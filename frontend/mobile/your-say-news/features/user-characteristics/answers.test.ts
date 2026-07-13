import {
    buildCharacteristicAnswers,
    createEmptyOnboardingForm,
    findFirstIncompleteStep,
    isRequiredComplete,
    type OnboardingForm,
} from "./answers";

const isComplete = (form: OnboardingForm) => isRequiredComplete(form, 16);

/** A fully-completed form used as the baseline; individual tests blank out fields. */
function completeForm(): OnboardingForm {
    return {
        country: "United Kingdom",
        city: "Bristol",
        region: "",
        ukCounty: "BRISTOL",
        urbanRural: "URBAN",
        age: 30,
        gender: "WOMAN",
        genderSelfDescribe: "",
        sexAtBirth: "FEMALE",
        sexualOrientation: "STRAIGHT_HETEROSEXUAL",
        maritalStatus: "SINGLE",
        raceSelections: ["WHITE_EUROPEAN"],
        countryOfBirth: "UNITED_KINGDOM",
        citizenship: ["BRITISH"],
        religion: "NO_RELIGION",
        religiosity: "NOT_RELIGIOUS",
        politicalPersuasion: "CENTRE_LEFT",
        education: "BACHELORS",
        occupation: "EMPLOYED_FULL_TIME",
        employmentSector: "IT_TECHNOLOGY",
        universitySubject: "COMPUTER_SCIENCE",
        currency: "USD",
        personalIncomeRange: "BETWEEN_50K_AND_75K",
        householdIncomeRange: "BETWEEN_100K_AND_150K",
        height: "FEET_5_4_TO_5_6",
        weightRange: "KG_60_69",
        eyeColor: "GREEN",
        parent: "NOT_PARENT_CAREGIVER",
        hasPet: "YES",
        petType: ["DOG"],
        chronotype: "NIGHT_OWL",
        outlook: "OPTIMIST",
        neurodivergent: "YES",
        neurodivergenceType: ["ADHD"],
        hasDisability: "NO",
        disabilityType: [],
        housingStatus: "OWN_MORTGAGE",
        propertyType: "FLAT_APARTMENT",
        newsFrequencyScore: 7,
        balancedNewsViewpoint: "YES",
        mainstreamNewsPercent: 60,
        betterWorldWithData: "YES",
    };
}

describe("isRequiredComplete", () => {
    it("returns true when every required field is filled", () => {
        expect(isComplete(completeForm())).toBe(true);
    });

    it("ignores optional location and conditional fields", () => {
        const form = {
            ...completeForm(),
            city: "",
            region: "",
            ukCounty: null,
            universitySubject: null,
        };
        expect(isComplete(form)).toBe(true);
    });

    it("does not require petType when the user has no pet", () => {
        const form = { ...completeForm(), hasPet: "NO", petType: [] };
        expect(isComplete(form)).toBe(true);
    });

    it("does not require a type when not neurodivergent or not disabled", () => {
        const form = {
            ...completeForm(),
            neurodivergent: "NO",
            neurodivergenceType: [],
            hasDisability: "NO",
            disabilityType: [],
        };
        expect(isComplete(form)).toBe(true);
    });

    it("requires a home type for renters (everyone with a fixed home)", () => {
        const form = { ...completeForm(), housingStatus: "PRIVATE_RENT", propertyType: null };
        expect(isComplete(form)).toBe(false);
    });

    it("does not require a home type when there is no fixed address", () => {
        const form = { ...completeForm(), housingStatus: "TEMPORARY_NO_FIXED", propertyType: null };
        expect(isComplete(form)).toBe(true);
    });

    it("requires the free-text description only when gender is self-describe", () => {
        expect(isComplete({ ...completeForm(), gender: "SELF_DESCRIBE", genderSelfDescribe: "" })).toBe(false);
        expect(isComplete({ ...completeForm(), gender: "SELF_DESCRIBE", genderSelfDescribe: "Agender" })).toBe(true);
    });

    it("rejects an age below the minimum of 16", () => {
        expect(isComplete({ ...completeForm(), age: 15 })).toBe(false);
        expect(isComplete({ ...completeForm(), age: 16 })).toBe(true);
    });

    it.each([
        [0, 0],
        [10, 100],
    ])("accepts news scale boundaries %i and %i", (newsFrequencyScore, mainstreamNewsPercent) => {
        expect(isComplete({ ...completeForm(), newsFrequencyScore, mainstreamNewsPercent })).toBe(true);
    });

    it.each([
        [-1, 50],
        [11, 50],
        [5, -1],
        [5, 101],
    ])("rejects out-of-range news values %i and %i", (newsFrequencyScore, mainstreamNewsPercent) => {
        expect(isComplete({ ...completeForm(), newsFrequencyScore, mainstreamNewsPercent })).toBe(false);
    });

    it.each([
        ["country", { country: "   " }],
        ["urbanRural", { urbanRural: null }],
        ["age", { age: null }],
        ["gender", { gender: null }],
        ["sexAtBirth", { sexAtBirth: null }],
        ["sexualOrientation", { sexualOrientation: null }],
        ["maritalStatus", { maritalStatus: null }],
        ["countryOfBirth", { countryOfBirth: null }],
        ["religion", { religion: null }],
        ["religiosity", { religiosity: null }],
        ["politicalPersuasion", { politicalPersuasion: null }],
        ["education", { education: null }],
        ["occupation", { occupation: null }],
        ["employmentSector", { employmentSector: null }],
        ["height", { height: null }],
        ["weightRange", { weightRange: null }],
        ["personalIncomeRange", { personalIncomeRange: null }],
        ["householdIncomeRange", { householdIncomeRange: null }],
        ["eyeColor", { eyeColor: null }],
        ["parent", { parent: null }],
        ["hasPet", { hasPet: null }],
        ["petType (when hasPet is YES)", { hasPet: "YES", petType: [] }],
        ["chronotype", { chronotype: null }],
        ["outlook", { outlook: null }],
        ["neurodivergent", { neurodivergent: null }],
        ["neurodivergenceType (when neurodivergent is YES)", { neurodivergent: "YES", neurodivergenceType: [] }],
        ["hasDisability", { hasDisability: null }],
        ["disabilityType (when hasDisability is YES)", { hasDisability: "YES", disabilityType: [] }],
        ["housingStatus", { housingStatus: null }],
        ["propertyType (with a fixed home)", { housingStatus: "PRIVATE_RENT", propertyType: null }],
        ["newsFrequencyScore", { newsFrequencyScore: null }],
        ["balancedNewsViewpoint", { balancedNewsViewpoint: null }],
        ["betterWorldWithData", { betterWorldWithData: null }],
        ["race (empty)", { raceSelections: [] }],
        ["citizenship (empty)", { citizenship: [] }],
    ])("returns false when %s is missing", (_label, override) => {
        const form = { ...completeForm(), ...override } as OnboardingForm;
        expect(isComplete(form)).toBe(false);
    });
});

describe("findFirstIncompleteStep", () => {
    it("identifies a skipped news-viewpoint answer on its original page", () => {
        expect(findFirstIncompleteStep({ ...completeForm(), balancedNewsViewpoint: null }, 16)).toEqual({
            step: 10,
            fieldLabel: "Seeing more than one news viewpoint",
        });
    });

    it("returns the earliest missing answer rather than the current final-page answer", () => {
        expect(findFirstIncompleteStep({
            ...completeForm(),
            country: "",
            housingStatus: null,
        }, 16)).toEqual({
            step: 0,
            fieldLabel: "Country of residence",
        });
    });
});

describe("createEmptyOnboardingForm", () => {
    it("returns the complete stable draft shape", () => {
        expect(createEmptyOnboardingForm()).toEqual({
            country: "", city: "", region: "", ukCounty: null, urbanRural: null,
            age: null, gender: null, genderSelfDescribe: "", sexAtBirth: null,
            sexualOrientation: null, maritalStatus: null, raceSelections: [], countryOfBirth: null,
            citizenship: [], religion: null, religiosity: null, politicalPersuasion: null,
            education: null, occupation: null, employmentSector: null, universitySubject: null,
            currency: "USD", personalIncomeRange: null, householdIncomeRange: null, height: null,
            weightRange: null, eyeColor: null, parent: null, hasPet: null, petType: [],
            chronotype: null, outlook: null, neurodivergent: null, neurodivergenceType: [],
            hasDisability: null, disabilityType: [], housingStatus: null, propertyType: null,
            newsFrequencyScore: null, balancedNewsViewpoint: null, mainstreamNewsPercent: 50,
            betterWorldWithData: null,
        });
    });
});

describe("buildCharacteristicAnswers", () => {
    it("maps the form onto the answer payload with backend enum values", () => {
        const answers = buildCharacteristicAnswers(completeForm());

        expect(answers).toEqual({
            country: "United Kingdom",
            city: "Bristol",
            region: null,
            ukCounty: "BRISTOL",
            urbanRural: "URBAN",
            age: 30,
            gender: "WOMAN",
            genderSelfDescribe: "",
            sexAtBirth: "FEMALE",
            sexualOrientation: "STRAIGHT_HETEROSEXUAL",
            maritalStatus: "SINGLE",
            race: ["WHITE_EUROPEAN"],
            countryOfBirth: "UNITED_KINGDOM",
            citizenship: ["BRITISH"],
            religion: "NO_RELIGION",
            religiosity: "NOT_RELIGIOUS",
            politicalPersuasion: "CENTRE_LEFT",
            education: "BACHELORS",
            occupation: "EMPLOYED_FULL_TIME",
            employmentSector: "IT_TECHNOLOGY",
            universitySubject: "COMPUTER_SCIENCE",
            personalIncomeRange: "BETWEEN_50K_AND_75K",
            householdIncomeRange: "BETWEEN_100K_AND_150K",
            height: "FEET_5_4_TO_5_6",
            weightRange: "KG_60_69",
            eyeColor: "GREEN",
            parent: "NOT_PARENT_CAREGIVER",
            hasPet: true,
            petType: ["DOG"],
            chronotype: "NIGHT_OWL",
            outlook: "OPTIMIST",
            neurodivergent: true,
            neurodivergenceType: ["ADHD"],
            hasDisability: false,
            disabilityType: [],
            housingStatus: "OWN_MORTGAGE",
            propertyType: "FLAT_APARTMENT",
            newsFrequency: 7,
            balancedNewsViewpoint: true,
            mainstreamNewsPercent: 60,
            betterWorldWithData: true,
        });
    });

    it("carries the parent/caregiver answer straight through (no derivation)", () => {
        const answers = buildCharacteristicAnswers({ ...completeForm(), parent: "PARENT_CAREGIVER_UNDER_18" });
        expect(answers.parent).toBe("PARENT_CAREGIVER_UNDER_18");
    });

    it("carries a self-described gender only when self-describe is chosen", () => {
        expect(
            buildCharacteristicAnswers({ ...completeForm(), gender: "SELF_DESCRIBE", genderSelfDescribe: " Agender " })
                .genderSelfDescribe
        ).toBe("Agender");
        expect(
            buildCharacteristicAnswers({ ...completeForm(), gender: "WOMAN", genderSelfDescribe: "ignored" })
                .genderSelfDescribe
        ).toBe("");
    });

    it("only sends a university subject for higher-education answers", () => {
        expect(buildCharacteristicAnswers({ ...completeForm(), education: "MASTERS" }).universitySubject).toBe("COMPUTER_SCIENCE");
        expect(buildCharacteristicAnswers({ ...completeForm(), education: "SECONDARY_SCHOOL" }).universitySubject).toBeNull();
    });

    it.each([
        ["pet owner keeps their pet types", "YES", ["CAT", "FISH"], true, ["CAT", "FISH"]],
        ["non-owner sends false and drops any pet types", "NO", ["CAT"], false, []],
    ])("%s", (_label, hasPet, petType, expectedHasPet, expectedPetType) => {
        const answers = buildCharacteristicAnswers({ ...completeForm(), hasPet, petType } as OnboardingForm);
        expect(answers.hasPet).toBe(expectedHasPet);
        expect(answers.petType).toEqual(expectedPetType);
    });

    it("maps a null pet-ownership answer to a null boolean and no pet types", () => {
        const answers = buildCharacteristicAnswers({ ...completeForm(), hasPet: null, petType: [] });
        expect(answers.hasPet).toBeNull();
        expect(answers.petType).toEqual([]);
    });

    it.each([
        ["neurodivergent keeps their types", "YES", ["AUTISM", "ADHD"], true, ["AUTISM", "ADHD"]],
        ["non-neurodivergent sends false and drops the types", "NO", ["AUTISM"], false, []],
    ])("%s", (_label, neurodivergent, neurodivergenceType, expectedFlag, expectedType) => {
        const answers = buildCharacteristicAnswers({
            ...completeForm(),
            neurodivergent,
            neurodivergenceType,
        } as OnboardingForm);
        expect(answers.neurodivergent).toBe(expectedFlag);
        expect(answers.neurodivergenceType).toEqual(expectedType);
    });

    it.each([
        ["disabled keeps their types", "YES", ["HEARING"], true, ["HEARING"]],
        ["non-disabled sends false and drops the types", "NO", ["HEARING"], false, []],
    ])("%s", (_label, hasDisability, disabilityType, expectedFlag, expectedType) => {
        const answers = buildCharacteristicAnswers({
            ...completeForm(),
            hasDisability,
            disabilityType,
        } as OnboardingForm);
        expect(answers.hasDisability).toBe(expectedFlag);
        expect(answers.disabilityType).toEqual(expectedType);
    });

    it.each([
        ["owner keeps their home type", "OWN_OUTRIGHT", "DETACHED", "DETACHED"],
        ["renter keeps their home type", "PRIVATE_RENT", "TERRACED", "TERRACED"],
        ["no fixed address drops any home type", "TEMPORARY_NO_FIXED", "FLAT_APARTMENT", null],
    ])("%s", (_label, housingStatus, propertyType, expectedType) => {
        const answers = buildCharacteristicAnswers({
            ...completeForm(),
            housingStatus,
            propertyType,
        });
        expect(answers.housingStatus).toBe(housingStatus);
        expect(answers.propertyType).toBe(expectedType);
    });

    it("blanks empty optional free-text to null", () => {
        const answers = buildCharacteristicAnswers({ ...completeForm(), city: "  ", region: "" });
        expect(answers.city).toBeNull();
        expect(answers.region).toBeNull();
    });

    it("trims whitespace from country in the payload", () => {
        const answers = buildCharacteristicAnswers({ ...completeForm(), country: "  United Kingdom  " });
        expect(answers.country).toBe("United Kingdom");
    });

    it("never includes identity (PII) in the payload", () => {
        const tainted = { ...completeForm(), userId: 42, email: "a@b.com", name: "Ada" };
        const answers = buildCharacteristicAnswers(tainted as unknown as OnboardingForm);

        expect(answers).not.toHaveProperty("userId");
        expect(answers).not.toHaveProperty("email");
        expect(answers).not.toHaveProperty("name");
        expect(answers).not.toHaveProperty("firstName");
    });
});
