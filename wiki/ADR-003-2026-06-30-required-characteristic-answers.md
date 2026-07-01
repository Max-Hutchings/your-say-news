# ADR-003: Required Characteristic Answers

Date: 2026-06-30

## Situation

Your Say News depends on characteristic data to show anonymous aggregate voting patterns. The
onboarding UI previously offered "Prefer not to say" for many characteristic axes, which allowed
users to complete onboarding without contributing useful data for those axes.

## Options Considered

1. Keep "Prefer not to say".
   This maximizes user comfort, but weakens the core product because aggregate breakdowns become
   sparse or unknown.

2. Remove "Prefer not to say" from characteristic choices.
   This makes the onboarding data more complete and aligned with the purpose of the app.

## Decision

Remove "Prefer not to say" from characteristic onboarding options and matching backend enums.

## Reason

The point of the app is to compare voting results by user characteristics while preserving anonymity.
Users should still be protected by aggregation and minimum-bucket privacy rules, but core
characteristic answers should be required.

## Consequences

- Frontend option lists must not include `PREFER_NOT_TO_SAY`.
- Backend characteristic enums must not accept `PREFER_NOT_TO_SAY`.
- Seed data must use concrete characteristic values.
- Optional free-text location fields, such as city / nearest city and region / state / county, remain
  governed by ADR-001.
