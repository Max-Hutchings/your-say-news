import { describe, expect, it } from "vitest";
import type { UnwrappedBenchmarkDimension, UnwrappedIncomeRangeDisplay } from "../types";
import { formatBenchmarkDimension } from "./formatBenchmarkDimension";

const householdIncome: UnwrappedIncomeRangeDisplay = {
  bucketId: "income|GB-GBP-GROSS-2025-v1|HOUSEHOLD|HOUSEHOLD_TIER_7",
  label: "GBP 200k or more",
  contextLabel: "Annual household income before tax in the United Kingdom",
  relativeLabel: "Top 5% locally",
  marketCode: "GB",
  marketLabel: "United Kingdom",
  currencyCode: "GBP",
  measure: "HOUSEHOLD",
  measureLabel: "Annual household income before tax",
  lowerInclusive: 200_000,
  upperExclusive: null,
  relativeTier: "TIER_7",
  profileId: "GB-GBP-GROSS-2025-v1",
  profileVersion: 1,
  bandId: "HOUSEHOLD_TIER_7",
};

describe("formatBenchmarkDimension", () => {
  it("shows the country and monetary range for household and personal income", () => {
    const household = incomeDimension("householdIncomeRange", householdIncome);
    const personal = incomeDimension("personalIncomeRange", {
      ...householdIncome,
      bucketId: "income|IN-INR-GROSS-2025-v1|PERSONAL|PERSONAL_TIER_3",
      label: "INR 500k to INR 900k",
      contextLabel: "Annual personal income before tax in India",
      marketCode: "IN",
      marketLabel: "India",
      currencyCode: "INR",
      measure: "PERSONAL",
      measureLabel: "Annual personal income before tax",
      lowerInclusive: 500_000,
      upperExclusive: 900_000,
      relativeTier: "TIER_3",
      profileId: "IN-INR-GROSS-2025-v1",
      bandId: "PERSONAL_TIER_3",
    });

    expect(formatBenchmarkDimension(household)).toBe(
      "Annual household income before tax · United Kingdom · GBP 200k or more",
    );
    expect(formatBenchmarkDimension(personal)).toBe(
      "Annual personal income before tax · India · INR 500k to INR 900k",
    );
  });

  it("never exposes an income bucket ID when resolved metadata is missing", () => {
    const dimension: UnwrappedBenchmarkDimension = {
      axis: "householdIncomeRange",
      bucket: householdIncome.bucketId,
    };

    expect(formatBenchmarkDimension(dimension)).toBe(
      "Household income range · Country-specific range unavailable",
    );
    expect(formatBenchmarkDimension(dimension)).not.toContain(householdIncome.bucketId);
  });

  it("uses a supplied display label for non-income dimensions", () => {
    expect(formatBenchmarkDimension({
      axis: "ageRange",
      bucket: "AGE_25_34",
      label: "Aged 25 to 34",
    })).toBe("Age range · Aged 25 to 34");
    expect(formatBenchmarkDimension({
      axis: "gender",
      bucket: "NON_BINARY",
    })).toBe("Gender · Non binary");
  });
});

function incomeDimension(
  axis: "personalIncomeRange" | "householdIncomeRange",
  income: UnwrappedIncomeRangeDisplay,
): UnwrappedBenchmarkDimension {
  return { axis, bucket: income.bucketId, label: income.label, income };
}
