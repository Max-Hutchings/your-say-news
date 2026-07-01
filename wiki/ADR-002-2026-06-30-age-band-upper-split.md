# ADR-002: Age Band Upper Split

Date: 2026-06-30

## Situation

Age is collected as a non-identifying band for aggregate voting analysis. The existing upper band was
`65+`, which grouped early retirement-age users and much older users together.

## Options Considered

1. Keep `65+`.
   This is simpler but less useful for analysis.

2. Split the upper band into `65-75` and `75+`.
   This keeps age banded while allowing better comparison between older age groups.

## Decision

Replace `65+` with `65-75` and `75+`.

## Reason

The split gives more useful aggregate analysis while still avoiding exact age or date-of-birth
exposure.

## Consequences

- Frontend options use `AGE_65_75` and `AGE_75_PLUS`.
- Backend `AgeRange` accepts `AGE_65_75` and `AGE_75_PLUS`.
- Do not reintroduce a single `AGE_65_PLUS` bucket without a new ADR.
