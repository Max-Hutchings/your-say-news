# Database-backed country income profiles

Implementation plan for
[ADR-045](../../wiki/ADR-045-2026-08-11-database-versioned-income-profiles.md).

## Outcome

- Country-specific range profiles live in Postgres.
- Only active profiles are offered for new characteristic answers.
- A replacement profile is a new row, so old answers and votes keep their original meaning.
- APIs show real local ranges instead of programmatic tiers.
- Post Unwrapped receives the same structured local range data as the user-facing API.
- No exact income or identity enters vote aggregates or agent prompts.

## Data model

### `income_range_profile`

Stores the version family, public ID, version, `active` state, market, currency, income definition,
source period and lifecycle timestamps. A partial unique index permits only one active row for each
market, currency and income basis.

### `income_range_band`

Stores seven personal and seven household bands for each profile. Each row has a stable band code,
measure, display order, lower-inclusive amount, upper-exclusive amount and relative tier.

### `income_range_profile_country`

Maps residence-country enum values to a profile. This supports both one-country markets and an
explicitly reviewed regional profile such as the euro area.

### `income_range_profile_source`

Stores publisher, dataset, URL, retrieval date, derivation and confidence. A draft cannot be
activated without source evidence.

### `user_characteristic` references

Add foreign keys for the selected profile, personal band and household band. Retain the current
public string IDs for API compatibility. A database trigger proves that the band rows belong to the
selected profile, match their personal/household measures and agree with the stored public codes.

## Profile lifecycle

1. Create a draft with `active = false` and `published_at = null`.
2. Add country mappings, fourteen contiguous bands and at least one source.
3. Validate that each measure starts open-ended, has no gaps or overlaps, and finishes open-ended.
4. Lock the current active profile for the same market, currency and basis.
5. Deactivate the old profile and timestamp it.
6. Publish and activate the draft, linking it to the superseded row.
7. Commit both changes together.

Published band, source and country rows are protected by database triggers. Historical display
resolution accepts active and inactive published versions. New answers accept active versions only.

## Runtime flow

```text
Active DB profile
  -> onboarding income options
  -> validated characteristic answer with DB references
  -> vote snapshot with immutable public profile and band identity
  -> privacy-safe aggregate
  -> shared range display resolver
  -> direct sentiment API and Post Unwrapped request
```

Ranges are not calculated when a user answers. The reviewed boundaries are generated before the
profile is published and stored in the database. Answer collection only validates and saves the
chosen profile and band references.

## API result contract

An income bucket retains its stable aggregate key and adds:

```json
{
  "bucket": "income|GB-GBP-GROSS-2025-v1|PERSONAL|PERSONAL_TIER_3",
  "label": "GBP 25k to GBP 40k",
  "income": {
    "contextLabel": "Annual personal income before tax in the United Kingdom",
    "relativeLabel": "25th to 50th percentile locally",
    "marketCode": "GB",
    "marketLabel": "United Kingdom",
    "currencyCode": "GBP",
    "measure": "PERSONAL",
    "lowerInclusive": 25000,
    "upperExclusive": 40000,
    "profileId": "GB-GBP-GROSS-2025-v1",
    "profileVersion": 1,
    "bandId": "PERSONAL_TIER_3"
  }
}
```

Non-income buckets keep their existing response shape. The mobile client uses `label` when present
and falls back to its enum prettifier for existing characteristics.

## Post Unwrapped boundary

The aggregate service enriches income cohort dimensions before computing the aggregate version.
The selector preserves the structured income object and produces a headline-ready display name.
The research request therefore contains real ranges and provenance, but only for already aggregated
cohorts. Raw vote snapshots and user IDs remain outside the Unwrapped domain.

## Delivery and verification

1. Add the Liquibase schema, reference data, constraints, triggers and existing-v2 backfill.
2. Replace the Java-generated catalogue with database queries and transactional activation.
3. Persist the three characteristic foreign keys while keeping the stable public response fields.
4. Freeze local range provenance in the vote snapshot.
5. Enrich direct sentiment buckets and Post Analysis aggregate dimensions with one shared resolver.
6. Use the returned label and context in all four mobile chart modes.
7. Verify profile boundaries, residence mappings, activation, historical resolution and database
   immutability with Postgres integration tests.
8. Verify exact snapshot fields, aggregate schema, PII allowlists, aggregate-version changes,
   Unwrapped request structure and all mobile chart modes.
