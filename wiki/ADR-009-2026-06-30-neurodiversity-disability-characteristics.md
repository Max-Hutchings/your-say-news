# ADR-009: Neurodiversity & Disability Characteristics

Date: 2026-06-30

## Situation

We collect non-identifying characteristics so sentiment can be sliced by audience. Neurodivergence
and disability are meaningful axes for "how do different people feel about a topic" that we did not
yet capture. As with pets (ADR-007), the *kind* of neurodivergence/disability is only meaningful for
people who report having one.

These are sensitive categories, so the data must stay aggregate-only and never be joined to identity.

## Options Considered

1. Single yes/no flags only.
   Simple, but loses the more interesting breakdown by kind.

2. Two independent questions each (flag + type), both always asked.
   Captures the type but lets someone who answered "no" still record a type — contradictory data.

3. A flag plus a conditional type question shown only when the flag is yes (mirrors pets).
   Captures both axes and keeps the data internally consistent.

4. Multi-select type (a person may have several).
   More accurate, but needs collection tables on both sides and a more complex form; out of scope
   for a "very simple" addition.

## Decision

Add a dedicated closing onboarding step with four characteristics, modelled exactly on the pet
pattern (ADR-007) — single-select, conditional:

- `neurodivergent` (boolean) + `neurodivergenceType` — one of `ADHD`, `AUTISM`, `DYSLEXIA`,
  `DYSPRAXIA`, `DYSCALCULIA`, `OTHER`, shown and required only when `neurodivergent` is yes.
- `hasDisability` (boolean) + `disabilityType` — one of `PHYSICAL_MOBILITY`, `VISUAL`, `HEARING`,
  `COGNITIVE_LEARNING`, `CHRONIC_ILLNESS`, `MENTAL_HEALTH`, `OTHER`, shown and required only when
  `hasDisability` is yes.

Both flags are required to complete onboarding (consistent with ADR-003); the type is forced to null
whenever its flag is no. The step is the final onboarding page.

## Reason

The conditional keeps the data consistent — a "no" answer can never carry a type. Single-select
mirrors the existing pet implementation, keeping the change small. These axes say nothing identifying
on their own and remain in the aggregate-only model with no new PII exposure.

## Consequences

- Frontend submits `neurodivergent`/`hasDisability` (boolean) and the two types (enum string or null).
- Backend stores `neurodivergent`, `neurodivergence_type`, `has_disability`, `disability_type`; all
  columns nullable so existing rows and seed data stay valid, while the API enforces the flags as
  required and each type as required only when its flag is true.
- Migration `0010-add-neurodiversity-disability-characteristics.yaml` adds the four columns.
- Post-service vote snapshots expose all four as aggregation axes (snapshot is JSONB, so no
  post-service schema change was needed).
- Single-select is a deliberate simplification; multi-select can be revisited if the breakdown proves
  too coarse.
- Small-cell suppression matters more here than for lighter axes — keep the existing k-anonymity
  floor in mind before exposing these breakdowns.
