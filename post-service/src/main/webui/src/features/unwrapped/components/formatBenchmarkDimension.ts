import type { UnwrappedBenchmarkDimension } from "../types";

export function formatBenchmarkDimension(dimension: UnwrappedBenchmarkDimension) {
  if (isIncomeAxis(dimension.axis)) {
    return formatIncomeDimension(dimension);
  }
  return `${formatAxis(dimension.axis)} · ${dimension.label ?? formatEnum(dimension.bucket)}`;
}

function formatIncomeDimension(dimension: UnwrappedBenchmarkDimension) {
  if (!dimension.income) {
    return `${formatAxis(dimension.axis)} · Country-specific range unavailable`;
  }
  return [
    dimension.income.measureLabel,
    dimension.income.marketLabel,
    dimension.income.label,
  ].join(" · ");
}

function isIncomeAxis(axis: string) {
  return axis === "personalIncomeRange" || axis === "householdIncomeRange";
}

function formatAxis(axis: string) {
  return formatEnum(axis.replace(/([a-z])([A-Z])/g, "$1_$2"));
}

function formatEnum(value: string) {
  return value.replaceAll("_", " ").toLowerCase().replace(/^./, (letter) => letter.toUpperCase());
}
