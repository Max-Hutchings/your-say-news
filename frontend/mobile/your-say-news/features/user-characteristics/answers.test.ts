import { buildCharacteristicAnswers, isRequiredComplete, type OnboardingForm } from "./answers";

/** A fully-completed form used as the baseline; individual tests blank out fields. */
function completeForm(): OnboardingForm {
    return {
        country: "United Kingdom",
        city: "Bristol",
        region: "",
        ukCounty: "BRISTOL",
        urbanRural: "URBAN",
        ageRange: "AGE_25_34",
        gender: "WOMAN",
        sexAtBirth: "FEMALE",
        sexualOrientation: "HETEROSEXUAL",
        maritalStatus: "SINGLE",
        raceSelections: ["WHITE"],
        countryOfBirth: "UNITED_KINGDOM",
        citizenship: "UNITED_KINGDOM",
        religion: "NO_RELIGION",
        religiosity: "NOT_RELIGIOUS",
        politicalPersuasion: "CENTRE_LEFT",
        education: "BACHELORS",
        occupation: "EMPLOYED_FULL_TIME",
        employmentSector: "IT_TECHNOLOGY",
        universitySubject: "COMPUTER_SCIENCE",
        personalIncomeRange: "BETWEEN_50K_AND_100K",
        householdIncomeRange: "BETWEEN_100K_AND_150K",
        height: "FEET_5_4_TO_5_6",
        weightRange: "KG_60_69",
        eyeColor: "GREEN",
        parent: "NO",
        hasPet: "YES",
        petType: "DOG",
        chronotype: "NIGHT_OWL",
        outlook: "OPTIMIST",
        neurodivergent: "YES",
        neurodivergenceType: "ADHD",
        hasDisability: "NO",
        disabilityType: null,
        housingStatus: "OWN",
        propertyType: "FLAT",
        newsFrequencyScore: 7,
        balancedNewsViewpoint: "YES",
        mainstreamNewsPercent: 60,
        betterWorldWithData: "YES",
    };
}

describe("isRequiredComplete", () => {
    it("returns true when every required field is filled", () => {
        expect(isRequiredComplete(completeForm())).toBe(true);
    });

    it("ignores optional location and conditional fields", () => {
        const form = {
            ...completeForm(),
            city: "",
            region: "",
            ukCounty: null,
            universitySubject: null,
        };
        expect(isRequiredComplete(form)).toBe(true);
    });

    it("does not require petType when the user has no pet", () => {
        const form = { ...completeForm(), hasPet: "NO", petType: null };
        expect(isRequiredComplete(form)).toBe(true);
    });

    it("does not require a type when not neurodivergent or not disabled", () => {
        const form = {
            ...completeForm(),
            neurodivergent: "NO",
            neurodivergenceType: null,
            hasDisability: "NO",
            disabilityType: null,
        };
        expect(isRequiredComplete(form)).toBe(true);
    });

    it("does not require propertyType unless the user owns a property", () => {
        const form = { ...completeForm(), housingStatus: "RENT", propertyType: null };
        expect(isRequiredComplete(form)).toBe(true);
    });

    it.each([
        ["country", { country: "   " }],
        ["urbanRural", { urbanRural: null }],
        ["ageRange", { ageRange: null }],
        ["gender", { gender: null }],
        ["sexAtBirth", { sexAtBirth: null }],
        ["sexualOrientation", { sexualOrientation: null }],
        ["maritalStatus", { maritalStatus: null }],
        ["countryOfBirth", { countryOfBirth: null }],
        ["citizenship", { citizenship: null }],
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
        ["petType (when hasPet is YES)", { hasPet: "YES", petType: null }],
        ["chronotype", { chronotype: null }],
        ["outlook", { outlook: null }],
        ["neurodivergent", { neurodivergent: null }],
        ["neurodivergenceType (when neurodivergent is YES)", { neurodivergent: "YES", neurodivergenceType: null }],
        ["hasDisability", { hasDisability: null }],
        ["disabilityType (when hasDisability is YES)", { hasDisability: "YES", disabilityType: null }],
        ["housingStatus", { housingStatus: null }],
        ["propertyType (when housingStatus is OWN)", { housingStatus: "OWN", propertyType: null }],
        ["newsFrequencyScore", { newsFrequencyScore: null }],
        ["balancedNewsViewpoint", { balancedNewsViewpoint: null }],
        ["betterWorldWithData", { betterWorldWithData: null }],
        ["race (empty)", { raceSelections: [] }],
    ])("returns false when %s is missing", (_label, override) => {
        const form = { ...completeForm(), ...override } as OnboardingForm;
        expect(isRequiredComplete(form)).toBe(false);
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
            ageRange: "AGE_25_34",
            gender: "WOMAN",
            genderSelfDescribe: "",
            sexAtBirth: "FEMALE",
            sexualOrientation: "HETEROSEXUAL",
            maritalStatus: "SINGLE",
            race: ["WHITE"],
            countryOfBirth: "UNITED_KINGDOM",
            citizenship: "UNITED_KINGDOM",
            religion: "NO_RELIGION",
            religiosity: "NOT_RELIGIOUS",
            politicalPersuasion: "CENTRE_LEFT",
            education: "BACHELORS",
            occupation: "EMPLOYED_FULL_TIME",
            employmentSector: "IT_TECHNOLOGY",
            universitySubject: "COMPUTER_SCIENCE",
            personalIncomeRange: "BETWEEN_50K_AND_100K",
            householdIncomeRange: "BETWEEN_100K_AND_150K",
            height: "FEET_5_4_TO_5_6",
            weightRange: "KG_60_69",
            eyeColor: "GREEN",
            parent: "NO",
            newsFrequency: 7,
            hasPet: true,
            petType: "DOG",
            chronotype: "NIGHT_OWL",
            outlook: "OPTIMIST",
            neurodivergent: true,
            neurodivergenceType: "ADHD",
            hasDisability: false,
            disabilityType: null,
            housingStatus: "OWN",
            propertyType: "FLAT",
        });
    });

    it.each([
        ["pet owner keeps their pet type", "YES", "CAT", true, "CAT"],
        ["non-owner sends false and drops any pet type", "NO", "CAT", false, null],
    ])("%s", (_label, hasPet, petType, expectedHasPet, expectedPetType) => {
        const answers = buildCharacteristicAnswers({ ...completeForm(), hasPet, petType });
        expect(answers.hasPet).toBe(expectedHasPet);
        expect(answers.petType).toBe(expectedPetType);
    });

    it("maps a null pet-ownership answer to a null boolean", () => {
        const answers = buildCharacteristicAnswers({ ...completeForm(), hasPet: null, petType: null });
        expect(answers.hasPet).toBeNull();
        expect(answers.petType).toBeNull();
    });

    it.each([
        ["neurodivergent keeps their type", "YES", "AUTISM", true, "AUTISM"],
        ["non-neurodivergent sends false and drops the type", "NO", "AUTISM", false, null],
    ])("%s", (_label, neurodivergent, neurodivergenceType, expectedFlag, expectedType) => {
        const answers = buildCharacteristicAnswers({
            ...completeForm(),
            neurodivergent,
            neurodivergenceType,
        });
        expect(answers.neurodivergent).toBe(expectedFlag);
        expect(answers.neurodivergenceType).toBe(expectedType);
    });

    it.each([
        ["disabled keeps their type", "YES", "HEARING", true, "HEARING"],
        ["non-disabled sends false and drops the type", "NO", "HEARING", false, null],
    ])("%s", (_label, hasDisability, disabilityType, expectedFlag, expectedType) => {
        const answers = buildCharacteristicAnswers({
            ...completeForm(),
            hasDisability,
            disabilityType,
        });
        expect(answers.hasDisability).toBe(expectedFlag);
        expect(answers.disabilityType).toBe(expectedType);
    });

    it.each([
        ["owner keeps their property type", "OWN", "HOUSE", "HOUSE"],
        ["renter drops any property type", "RENT", "HOUSE", null],
        ["living with parents drops any property type", "LIVE_WITH_PARENTS", "FLAT", null],
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

    it.each([
        ["female parent derives MUM", "YES", "FEMALE", "MUM"],
        ["male parent derives DAD", "YES", "MALE", "DAD"],
        ["non-parent passes NO through", "NO", "FEMALE", "NO"],
    ])("%s", (_label, parent, sexAtBirth, expectedParent) => {
        const answers = buildCharacteristicAnswers({ ...completeForm(), parent, sexAtBirth });
        expect(answers.parent).toBe(expectedParent);
    });
});
