import { SENTIMENT_AXES, prettifyBucket } from "./axes";

describe("prettifyBucket", () => {
  it("title-cases a single-word enum", () => {
    expect(prettifyBucket("LEFT")).toBe("Left");
  });

  it("title-cases each word of a multi-word enum", () => {
    expect(prettifyBucket("CENTRE_LEFT")).toBe("Centre Left");
  });

  it("joins an age band's number run with an en dash", () => {
    expect(prettifyBucket("AGE_25_34")).toBe("Age 25–34");
  });

  it("renders a trailing PLUS as a plus sign", () => {
    expect(prettifyBucket("AGE_65_PLUS")).toBe("Age 65+");
  });
});

describe("SENTIMENT_AXES", () => {
  it("leads with political leaning — the strongest predictor — as the default axis", () => {
    expect(SENTIMENT_AXES[0]).toEqual({ field: "politicalPersuasion", label: "Political leaning" });
  });

  it("labels the personalIncomeRange field as Income", () => {
    const income = SENTIMENT_AXES.find((a) => a.field === "personalIncomeRange");
    expect(income?.label).toBe("Income");
  });

  it("uses backend field names (not labels) as the axis identifiers", () => {
    expect(SENTIMENT_AXES.map((a) => a.field)).toEqual([
      "politicalPersuasion",
      "ageRange",
      "gender",
      "race",
      "country",
      "region",
      "urbanRural",
      "religion",
      "education",
      "personalIncomeRange",
    ]);
  });
});
