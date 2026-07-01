# ADR-004: Remove Self-Describe Gender

Date: 2026-06-30

## Situation

Gender is a required characteristic axis for aggregate analysis. The onboarding flow allowed
"Prefer to self-describe", which produced free-text gender values outside the normalized buckets.

## Options Considered

1. Keep self-describe gender.
   This is more expressive, but creates sparse free-text buckets that are harder to aggregate.

2. Remove self-describe gender and keep normalized gender buckets.
   This keeps the characteristic useful for reporting and avoids one-off free-text values.

## Decision

Remove self-describe gender from the UI and backend enum.

## Reason

The app needs normalized characteristic buckets for aggregate voting breakdowns. Free-text
self-describe values are not practical for that purpose.

## Consequences

- Gender options are `WOMAN`, `MAN`, and `NON_BINARY`.
- The frontend no longer shows a free-text gender field.
- The backend no longer accepts `SELF_DESCRIBE`.
- Existing `SELF_DESCRIBE` rows are migrated to `NON_BINARY` and their free text is cleared.
