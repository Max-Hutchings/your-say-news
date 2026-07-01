# ADR-007: Pet Ownership Characteristics

Date: 2026-06-30

## Situation

We collect non-identifying characteristics so sentiment can be sliced by audience. Pet ownership is a
useful lifestyle axis (pet owners vs non-owners, and by kind of pet) that we did not yet capture.

The kind of pet is only meaningful for people who own one — a "what kind of pet?" answer is
nonsensical for a non-owner.

## Options Considered

1. Add a single pet question only ("Do you have a pet?").
   Simple, but loses the more interesting breakdown by kind of pet.

2. Add two independent questions, both always asked.
   Captures the type but lets a non-owner record a pet type, which is contradictory data.

3. Add ownership plus a conditional pet-type question shown only to owners.
   Captures both axes and keeps the data internally consistent.

## Decision

Add two characteristics:

- `hasPet` — a yes/no answer, stored as a boolean.
- `petType` — one of `DOG`, `CAT`, `FISH`, `BIRD`, `REPTILE`, shown and required only when
  `hasPet` is yes, and forced to null otherwise.

The pet-type question is conditional in the onboarding wizard (the existing wizard supports
conditional rendering, so we render it only when the user answers yes).

## Reason

Both axes give useful aggregate breakdowns, and the conditional keeps the data consistent — a
non-owner can never carry a pet type. Pet ownership says nothing identifying about a person, so it
fits the aggregate-only model with no new PII exposure.

## Consequences

- Frontend submits `hasPet` (boolean) and `petType` (enum string or null).
- Backend stores `has_pet` and `pet_type`; both columns are nullable so existing rows and seed data
  remain valid, while the API enforces `hasPet` as required and `petType` as required only for owners.
- Migration `0008-add-pet-characteristics.yaml` adds the two columns.
- Post-service vote snapshots expose `hasPet` and `petType` as aggregation axes (snapshot is JSONB, so
  no post-service schema change was needed).
- Do not record a `petType` for a non-owner.
