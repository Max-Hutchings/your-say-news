import { buildCharacteristicAnswers, isRequiredComplete, type OnboardingForm } from "./answers";

/** A fully-completed form used as the baseline; individual tests blank out fields. */
function completeForm(): OnboardingForm {
    return {
        country: "United Kingdom",
        city: "Bristol",
        ageRange: "25–34",
        gender: "Woman",
        genderSelfDescribe: "",
        education: "Bachelor’s or equivalent",
        occupation: "Employed full-time",
        newsFrequencyScore: 7,
        sexAtBirth: "FEMALE",
        height: "FEET_5_4_TO_5_6",
        weightRange: "KG_60_69",
        incomeRange: "BETWEEN_50K_AND_100K",
        parent: "NO",
        eyeColor: "GREEN",
        countryOfBirth: "UNITED_KINGDOM",
        ukCounty: "BRISTOL",
        universitySubject: "COMPUTER_SCIENCE",
        raceSelections: ["WHITE"],
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
    it("maps the form onto the answer payload", () => {
        const answers = buildCharacteristicAnswers(completeForm());

        expect(answers).toEqual({
            location: { country: "United Kingdom", city: "Bristol" },
            ageRange: "25–34",
            gender: "Woman",
            genderSelfDescribe: "",
            education: "Bachelor’s or equivalent",
            occupation: "Employed full-time",
            newsFrequency: 7,
            race: ["WHITE"],
            sexAtBirth: "FEMALE",
            height: "FEET_5_4_TO_5_6",
            weightRange: "KG_60_69",
            incomeRange: "BETWEEN_50K_AND_100K",
            parent: "NO",
            eyeColor: "GREEN",
            countryOfBirth: "UNITED_KINGDOM",
            ukCounty: "BRISTOL",
            universitySubject: "COMPUTER_SCIENCE",
        });
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
            gender: "Prefer to self-describe",
            genderSelfDescribe: "Agender",
        });
        expect(chosen.genderSelfDescribe).toBe("Agender");

        const notChosen = buildCharacteristicAnswers({
            ...completeForm(),
            gender: "Woman",
            genderSelfDescribe: "Agender",
        });
        expect(notChosen.genderSelfDescribe).toBe("");
    });
});
