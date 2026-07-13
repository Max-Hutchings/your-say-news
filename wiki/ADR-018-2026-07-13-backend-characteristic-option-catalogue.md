# ADR-018 — Backend characteristic option catalogue

## Situation

Characteristic onboarding duplicated Java enum values, labels and ordering in a large TypeScript
file. Backend enum changes therefore required a coordinated frontend edit, and a missed edit could
offer a value the API rejected. Some backend enums also retain legacy constants so historical rows
remain readable; those constants must not reappear in onboarding.

## Options considered

1. Continue maintaining matching Java and TypeScript lists.
2. Generate TypeScript constants from the backend at build time.
3. Expose a versioned backend catalogue and load it when characteristic onboarding starts.
4. Make the entire questionnaire, layout and conditional behaviour server-driven.

## Decision

The user-service exposes `GET /user-characteristics/options`. Its versioned response contains the
minimum supported age and ordered `{ label, value }` choices for every enum-backed onboarding field.
The catalogue is curated: deprecated constants remain readable on historical rows, but are excluded
from the catalogue and rejected in new answers.

The mobile app loads and validates this catalogue at the beginning of characteristic onboarding.
It retries transient failures twice and then shows a manual retry screen. Boolean yes/no controls and
currency formatting remain frontend-owned because they are presentation choices, not characteristic
enums. The wizard layout and conditional question logic also remain frontend-owned.

## Reason

This makes accepted values, display labels and ordering a single backend-owned contract without
introducing the complexity and weaker design control of a fully server-driven UI. Versioning and
runtime validation prevent an incompatible catalogue from silently rendering incomplete questions.

## Consequences and follow-up

- Changing or adding an offered enum value normally requires only a backend catalogue change.
- Adding a new characteristic field still requires coordinated DTO, validation, persistence and UI
  work.
- Onboarding now depends on user-service availability at startup; automatic and manual retries make
  that failure explicit instead of falling back to stale duplicated enums.
- A future localisation pass can replace response labels with locale-aware labels or translation keys
  without changing the stable enum values.
