# ADR-008: Quirky Characteristics Page

Date: 2026-06-30

## Situation

We collect non-identifying characteristics so sentiment can be sliced by audience. Alongside the
demographic axes we wanted a small set of lighter, "fun" data points that still plausibly correlate
with how people feel about the news — the kind that make a reader wonder "I wonder how people like
that feel about this story". Pet ownership (ADR-007) was the first; we now add chronotype and outlook.

We also wanted these grouped into their own onboarding step rather than scattered through the
demographic steps, so the tone shift is clear to the user.

## Options Considered

1. Add the new questions to existing demographic steps.
   Cheapest, but mixes a playful tone into serious demographic collection and gives no clear home for
   future quirky axes.

2. Add a dedicated "Quirky questions" step that groups pets, chronotype and outlook.
   One clear place for light-touch axes; pets move out of the Body & finances step into it.

## Decision

Add a dedicated **Quirky questions** onboarding step and two new characteristics:

- `chronotype` — one of `MORNING_LARK`, `NIGHT_OWL`, `IN_BETWEEN`.
- `outlook` — one of `OPTIMIST`, `PESSIMIST`, `DEPENDS` (optimist / pessimist about the future).

The step also hosts the existing pet questions (`hasPet`, conditional `petType`). All three are
**required** to complete onboarding, consistent with the other characteristic answers (ADR-003).

## Reason

Each axis gives a useful aggregate breakdown and says nothing identifying about a person, so they fit
the aggregate-only model with no new PII exposure. Grouping them keeps the wizard's demographic steps
focused and gives future quirky axes an obvious home.

## Consequences

- Frontend submits `chronotype` and `outlook` (enum strings); the wizard grows from eight to nine
  steps, with pets relocated into the new step.
- Backend stores `chronotype` and `outlook`; both columns are nullable so existing rows and seed data
  remain valid, while the API enforces both as required during onboarding.
- Migration `0009-add-quirky-characteristics.yaml` adds the two columns.
- Post-service vote snapshots expose `chronotype` and `outlook` as aggregation axes (snapshot is JSONB,
  so no post-service schema change was needed).
