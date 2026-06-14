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
        genderSelfDescribe: "",
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
        incomeRange: "BETWEEN_50K_AND_100K",
        height: "FEET_5_4_TO_5_6",
        weightRange: "KG_60_69",
        eyeColor: "GREEN",
        parent: "NO",
        newsFrequencyScore: 7,
    };
}

describe("isRequiredComplete", () => {
    it("returns true when every required field is filled", () => {
        expect(isRequiredComplete(completeForm())).toBe(true);
    });

    it("ignores optional fields", () => {
        const form = {
            ...completeForm(),
            city: "",
            education: null,
            occupation: null,
            countryOfBirth: null,
            politicalPersuasion: null,
            religion: null,
        };
        expect(isRequiredComplete(form)).toBe(true);
    });

    it.each([
        ["country", { country: "   " }],
        ["ageRange", { ageRange: null }],
        ["gender", { gender: null }],
        ["sexAtBirth", { sexAtBirth: null }],
        ["height", { height: null }],
        ["weightRange", { weightRange: null }],
        ["incomeRange", { incomeRange: null }],
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
            incomeRange: "BETWEEN_50K_AND_100K",
            height: "FEET_5_4_TO_5_6",
            weightRange: "KG_60_69",
            eyeColor: "GREEN",
            parent: "NO",
            newsFrequency: 7,
        });
    });

    it("blanks empty optional free-text to null", () => {
        const answers = buildCharacteristicAnswers({ ...completeForm(), city: "  ", region: "" });
        expect(answers.city).toBeNull();
        expect(answers.region).toBeNull();
    });

    it("never includes identity (PII) in the payload", () => {
        // Even if identity-looking fields were smuggled onto the form, they must not leak.
        const tainted = { ...completeForm(), userId: 42, email: "a@b.com", name: "Ada" };
        const answers = buildCharacteristicAnswers(tainted as unknown as OnboardingForm);

        expect(answers).not.toHaveProperty("userId");
        expect(answers).not.toHaveProperty("email");
        expect(answers).not.toHaveProperty("name");
        expect(answers).not.toHaveProperty("firstName");
    });

    it("only keeps the self-described gender when that option is chosen", () => {
        const chosen = buildCharacteristicAnswers({
            ...completeForm(),
            gender: "SELF_DESCRIBE",
            genderSelfDescribe: "Agender",
        });
        expect(chosen.genderSelfDescribe).toBe("Agender");

        const notChosen = buildCharacteristicAnswers({
            ...completeForm(),
            gender: "WOMAN",
            genderSelfDescribe: "Agender",
        });
        expect(notChosen.genderSelfDescribe).toBe("");
    });
});
