# ADR-010: Property Characteristics

Date: 2026-06-30

## Situation

We collect non-identifying characteristics so sentiment can be sliced by audience. Housing tenure
(live with parents / rent / own) is a strong correlate of how people feel about many topics, and was
not yet captured. As with pets (ADR-007), the *kind* of owned property is only meaningful for owners.

## Options Considered

1. A single tenure question only.
   Simple, but loses the owner house-vs-flat breakdown.

2. Tenure plus a property-type question always asked.
   Lets a renter record a property type — contradictory data.

3. Tenure plus a conditional property-type question shown only to owners.
   Captures both axes and keeps the data consistent (mirrors the pet pattern).

## Decision

Add a dedicated closing onboarding step (after the neurodiversity & disability step) with two
characteristics, modelled on the pet pattern but keyed on an enum value rather than a boolean:

- `housingStatus` — one of `LIVE_WITH_PARENTS`, `RENT`, `OWN`.
- `propertyType` — one of `HOUSE`, `FLAT`, shown and required only when `housingStatus` is `OWN`,
  and forced to null otherwise.

Both are required to complete onboarding (consistent with ADR-003).

## Reason

The conditional keeps the data consistent — a non-owner can never carry a property type. Keying the
conditional on the `OWN` enum value rather than a yes/no keeps the step to two questions instead of
three. These axes say nothing identifying on their own and stay in the aggregate-only model with no
new PII exposure.

## Consequences

- Frontend submits `housingStatus` and `propertyType` (enum string or null).
- Backend stores `housing_status` and `property_type`; both columns nullable so existing rows and seed
  data stay valid, while the API enforces `housingStatus` as required and `propertyType` as required
  only for owners.
- Migration `0011-add-property-characteristics.yaml` adds the two columns.
- Post-service vote snapshots expose both as aggregation axes (snapshot is JSONB, so no post-service
  schema change was needed).
- Do not record a `propertyType` for a non-owner.
