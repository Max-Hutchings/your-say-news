# Database-backed country income profiles

Detailed implementation plan for
[ADR-045](../../wiki/ADR-045-2026-08-11-database-versioned-income-profiles.md). This replaces the
hard-coded income profile catalogue
with versioned database records, completes vote-time range preservation, and gives the direct
results API and Post Unwrapped the same real country-specific income context.

## Goal

- Store many country-specific personal and household income profiles in Postgres.
- Make one profile version active for new answers while retaining every historical version.
- Show real local ranges to users instead of programmatic tier names.
- Supply the same real range, country, currency and definition to Post Unwrapped.
- Preserve aggregate-only reporting and never collect or infer exact income.

## Decisions already made

- Residence country selects the economic market. Country of birth and post jurisdiction do not.
- Currency is part of a market profile, not enough to define one by itself.
- Profiles contain separate annual gross personal and household bands.
- Published profiles and bands are immutable.
- A changed range creates a new profile row and new band rows.
- `active = true` means accepted for new answers.
- Activating a new version deactivates the previous version in the same transaction.
- Inactive published versions remain resolvable indefinitely.
- Numeric results are country/profile-specific. Cross-market analysis uses relative local position.
- The API and Unwrapped share one display resolver.

## Current state and gaps

- `IncomeProfileCatalog` builds country profiles and ranges in Java at application startup.
- `user_characteristic` already stores version-2 profile, currency, market, band-code and tier
  strings, but they have no foreign keys to a profile catalogue.
- The mobile onboarding flow already fetches profile-specific bands and submits a nested income
  answer.
- `CharacteristicSnapshotMapper` reduces version-2 income to `V2_TIER_n`, discarding the profile,
  currency and local band identities.
- Direct sentiment DTOs return raw bucket strings. The frontend applies `prettifyBucket`, which
  cannot turn a relative tier into the real local range.
- `PostAnalysisAggregateBuilder` and `CohortDisplayNames` also see only the relative tier, so the
  Unwrapped brief cannot contain the real amount range.
- Most current non-GB/IN profiles are code-generated fallbacks. They must not automatically become
  active database profiles without the evidence review required by ADR-031.

## Target flow

```text
Reviewed database profile
    -> active onboarding options
    -> validated characteristic answer with profile/band references
    -> vote-time snapshot with local band identity and relative tier
    -> privacy-safe aggregate
    -> shared batch display resolver
    -> direct results API and Post Unwrapped brief
```

Ranges are generated when a reviewed profile is created, not when a user answers or requests a
result. Saving an answer validates and derives references. Result generation resolves those frozen
references into display data.

## 1. Database model

Create `0017-create-income-profile-catalogue.yaml` under `liquibase/changelog/db/user-migrations`.
The master changelog uses `includeAll`, so the numeric prefix places it after the existing `0016`
migration.

### `income_profile`

| Column | Type | Rule |
| --- | --- | --- |
| `id` | `BIGINT` identity | Internal primary key |
| `public_id` | `VARCHAR(96)` | Stable API/snapshot identity, globally unique |
| `profile_key` | `VARCHAR(64)` | Stable version family, for example `GB-GBP-GROSS` |
| `version` | `INT` | Positive and unique within `profile_key` |
| `active` | `BOOLEAN` | Not null, default `false` |
| `market_code` | `VARCHAR(8)` | Stable economic market code |
| `market_label` | `VARCHAR(120)` | Governed user-facing market name |
| `currency_code` | `VARCHAR(3)` | Uppercase ISO 4217 shape |
| `income_basis` | `VARCHAR(32)` | Initially `GROSS_ANNUAL` |
| `personal_definition` | `VARCHAR(240)` | Exact question meaning |
| `household_definition` | `VARCHAR(240)` | Exact question meaning |
| `source_year` | `VARCHAR(32)` | Source period without false date precision |
| `effective_from` | `DATE` | Earliest intended use date |
| `published_at` | `TIMESTAMPTZ` | Null for drafts; set on first activation |
| `deactivated_at` | `TIMESTAMPTZ` | Set when replaced or manually withdrawn |
| `supersedes_profile_id` | `BIGINT` | Optional self-reference to the previous version |
| `created_at` | `TIMESTAMPTZ` | Audit timestamp |
| `updated_at` | `TIMESTAMPTZ` | Draft audit timestamp |

Constraints and indexes:

- unique `public_id`;
- unique `(profile_key, version)`;
- check `version > 0`;
- check currency code matches `^[A-Z]{3}$`;
- check active rows have `published_at IS NOT NULL`;
- partial unique index on `(market_code, currency_code, income_basis) WHERE active`;
- index `(market_code, currency_code, active)` for onboarding lookup; and
- prohibit self-supersession.

`active` is a selection flag, not deletion or validity of historical data. An inactive profile may
still be referenced by characteristics, votes, results and Unwrapped.

### `income_profile_country`

| Column | Type | Rule |
| --- | --- | --- |
| `income_profile_id` | `BIGINT` | Profile foreign key |
| `country_code` | `VARCHAR(64)` | Stable residence-country enum value |

Use `(income_profile_id, country_code)` as the primary key and index `country_code`. This supports
one-country profiles and explicitly reviewed regional profiles without assuming all users of a
currency share one distribution.

### `income_band`

| Column | Type | Rule |
| --- | --- | --- |
| `id` | `BIGINT` identity | Internal primary key |
| `income_profile_id` | `BIGINT` | Profile foreign key |
| `band_code` | `VARCHAR(48)` | Stable within profile, such as `PERSONAL_TIER_3` |
| `measure` | `VARCHAR(16)` | `PERSONAL` or `HOUSEHOLD` |
| `display_order` | `INT` | Positive order within measure |
| `lower_inclusive` | `BIGINT` | Null only for the first band |
| `upper_exclusive` | `BIGINT` | Null only for the final band |
| `relative_tier` | `VARCHAR(16)` | Governed internal tier |

Use whole currency units because profile boundaries are deliberately rounded. If a future currency
requires sub-unit thresholds, introduce an explicit minor-unit scale in a new schema version rather
than silently changing the meaning of existing values.

Constraints and indexes:

- unique `(income_profile_id, band_code)`;
- unique `(income_profile_id, measure, display_order)`;
- unique `(income_profile_id, measure, relative_tier)` for the initial seven-band model;
- check measure is `PERSONAL` or `HOUSEHOLD`;
- check `display_order > 0`;
- check a closed band has `lower_inclusive < upper_exclusive`; and
- foreign key to `income_profile` with no cascading delete.

Contiguity, first/last open ends and full tier coverage require domain validation because they span
multiple rows.

### `income_profile_source`

| Column | Type | Rule |
| --- | --- | --- |
| `id` | `BIGINT` identity | Primary key |
| `income_profile_id` | `BIGINT` | Profile foreign key |
| `publisher` | `VARCHAR(200)` | Source owner |
| `dataset` | `VARCHAR(300)` | Dataset/table name |
| `source_url` | `TEXT` | Evidence location |
| `retrieved_at` | `DATE` | Retrieval date |
| `derivation` | `TEXT` | How cut points were produced and rounded |
| `confidence` | `VARCHAR(16)` | `HIGH`, `MEDIUM` or `LOW` |

Require at least one source before activation. Sources on a published profile are immutable.

### Characteristic references

Add nullable columns to `user_characteristic`:

```text
income_profile_ref_id BIGINT
personal_income_band_ref_id BIGINT
household_income_band_ref_id BIGINT
```

Use additive names because `income_profile_id` currently contains a string public ID. Backfill the
new foreign keys from the existing version-2 string fields after the matching profiles and bands
exist. Retain the current strings during a compatibility window, then remove redundant columns only
after reads, seeds and vote snapshot creation no longer depend on them.

Database foreign keys prove that referenced rows exist. Service validation must additionally prove
both bands belong to the referenced profile and use the correct measure.

## 2. Profile lifecycle and active rule

Implement lifecycle operations in the `usercharacteristic` domain service, not in controllers.

### Create and edit draft

- A draft starts with `active = false` and `published_at = null`.
- Draft metadata, country mappings, bands and sources may be edited.
- Run full validation on demand and again during activation.

### Activate first version

In one transaction:

1. Lock the profile family/current-profile query.
2. Validate metadata, residence mappings, sources and both band sets.
3. Confirm the version is greater than every published version in the profile family.
4. Confirm no existing published row has the same public ID.
5. Set `published_at`, `active = true` and clear `deactivated_at`.
6. Commit; the partial unique index protects concurrent activation.

### Replace an active version

In one transaction:

1. Lock the current active profile for the market/currency/basis.
2. Validate the new inactive draft.
3. Set the old profile to `active = false` and set `deactivated_at`.
4. Set the new row's `supersedes_profile_id` to the old profile.
5. Set the new row to `active = true` and set `published_at`.
6. Commit both state changes together.

The old profile remains readable. Existing users are not silently remapped. Product may prompt them
to refresh their finance answer; future votes use whichever version is frozen in their current
characteristic answer.

### Withdraw without replacement

Allow an active profile to become inactive when its evidence is no longer acceptable. New answers
then receive an explicit unsupported-market response. Historical resolution continues. Record the
reason and `deactivated_at`; do not delete or reactivate the profile.

### Immutability enforcement

Use both service rules and database protection:

- application code rejects edits to any row with `published_at IS NOT NULL` except the allowed
  `active true -> false`, `deactivated_at` and supersession linkage changes;
- database triggers reject changes to published semantic columns, band rows, country mappings and
  sources; and
- foreign keys use restrictive deletion.

This prevents a manual database edit from changing historical meaning.

## 3. Backend domain implementation

Keep the DDD boundary described in `CLAUDE.md`:

- public controller and service interfaces remain at
  `com.yoursay.user.usercharacteristic`;
- public DTOs remain in `com.yoursay.user.usercharacteristic.dto`;
- entities and repositories remain internal under `model`;
- service implementations remain internal under `service`; and
- votes and Unwrapped use public interfaces/DTOs, never the profile repositories.

Add internal entities and repositories for profiles, countries, bands and sources. Replace the
hard-coded `IncomeProfileCatalog` with a database-backed implementation while preserving the public
profile DTO contract where it remains useful.

Expose public service operations for:

```java
IncomeCatalogDto getActiveCatalog();
IncomeProfileDto getActiveProfile(String marketCode, String currencyCode);
ResolvedIncomeAnswer resolveNewAnswer(IncomeAnswerDto answer, String residenceCountryCode);
List<IncomeCohortDisplayDto> resolveDisplays(List<IncomeCohortKeyDto> keys);
```

`resolveNewAnswer` accepts only an active profile. `resolveDisplays` accepts active or inactive
published profiles because historical votes require both.

Use one batch query for display resolution to avoid one database query per result bucket or
Unwrapped candidate. Cache immutable published profile versions by public ID. Cache the active
catalogue separately and invalidate that cache after activation or withdrawal.

Do not cache mutable drafts in the runtime answer path.

## 4. API contracts

### Onboarding catalogue

Retain:

```text
GET /user-characteristics/options
GET /user-characteristics/income-options?marketCode={market}&currencyCode={currency}
```

Change their source from Java constants to active database rows. Return no inactive profiles in the
onboarding catalogue. An unsupported country/market/currency pair returns an explicit `404` or
governed domain error; it never falls back to another country or live FX conversion.

Include stable public IDs and numeric boundaries. Generate localized labels on the server from the
stored boundaries, currency and market locale. Do not persist presentation strings as the only
meaning of a band.

### Saving an answer

Keep the nested versioned income answer shape. The client submits stable public profile and band
codes. The backend translates them to internal foreign keys, validates active/residence/profile
membership and derives tiers.

Reject:

- an inactive profile for a new answer;
- a profile that does not support the residence country;
- personal or household band codes belonging to another profile;
- a personal band submitted as household or the reverse;
- client-supplied amounts, display labels or relative tiers; and
- mixed legacy and version-2 income fields.

Reading an existing answer may return an inactive profile identity because that is the user's
historical selection. Include a `refreshRequired` signal when the selected profile is no longer
active so the mobile app can route the user back through finance without invalidating other answers.

### Direct sentiment results

Evolve `BucketSentiment` additively from a raw bucket string to a structured display contract:

```json
{
  "bucketId": "GB-GBP-GROSS-2025-v1:PERSONAL_TIER_3",
  "label": "GBP 25,000 to GBP 40,000",
  "contextLabel": "Annual personal income before tax in the United Kingdom",
  "relativeLabel": "25th to 50th percentile locally",
  "marketCode": "GB",
  "currencyCode": "GBP",
  "lowerInclusive": 25000,
  "upperExclusive": 40000,
  "total": 340,
  "choices": []
}
```

Only income buckets populate the income-specific context fields. Ordinary enum-backed buckets use
the same `bucketId` and governed `label` pattern without income metadata.

Resolve labels after aggregation and suppression. Do not expose profile source metadata as a result
axis or user characteristic.

## 5. Vote snapshot and aggregation

Extend `UserCharacteristicView` and `CharacteristicSnapshot` with separate personal and household
income snapshot objects containing:

```text
answerVersion
profilePublicId
profileVersion
marketCode
currencyCode
bandCode
relativeTier
```

Do not add database entity references or exact user income to JSON. Stable public IDs keep the vote
snapshot independent of internal database primary keys and allow historical resolution after data
migration or restoration.

Keep two distinct grouping keys:

- local key: profile public ID plus measure plus band code; and
- cross-market key: measure plus relative tier.

The direct numeric income axis uses the local key. Internal cross-market analysis uses the relative
key and a human relative-position label. Never place different local keys under one numeric label.

Historical votes already containing only `LEGACY_*` or `V2_TIER_*` remain readable:

- legacy nominal values remain excluded from versioned country results;
- tier-only version-2 snapshots may contribute to relative analysis if their semantics are known;
  and
- neither can produce a country-specific numeric label without a frozen profile/band identity.

For country-first local results, either require a country/market filter or return market-qualified
buckets. If multiple published profile versions occur in one post, keep different numeric ranges
separate. Do not merge overlapping versions and silently change their meaning.

## 6. Post Unwrapped integration

Unwrapped must receive the same resolved data as the direct API, not a separately formatted tier.

After `PostAnalysisAggregateBuilder` has applied privacy floors and
`BoundedHybridInsightSelector` has shortlisted cohorts:

1. Collect the distinct income cohort keys from selected candidates.
2. Resolve them in one batch through the public income display service.
3. Attach structured `IncomeCohortContextDto` data to the relevant selected dimensions.
4. Serialize that structured context in `UnwrappedResearchRequest`.
5. Instruct the model to use the provided display range and definition exactly.

Example agent context:

```json
{
  "axis": "personalIncomeRange",
  "marketCode": "GB",
  "marketLabel": "United Kingdom",
  "currencyCode": "GBP",
  "measureLabel": "Annual personal income before tax",
  "lowerInclusive": 25000,
  "upperExclusive": 40000,
  "displayLabel": "GBP 25,000 to GBP 40,000",
  "relativePosition": "25th to 50th percentile locally",
  "profilePublicId": "GB-GBP-GROSS-2025-v1",
  "profileVersion": 1
}
```

Agent rules:

- call this a range, never the voter's exact income;
- include the market when quoting a numeric range;
- do not convert the amount into another currency;
- do not describe relative tiers as equivalent living standards;
- do not introduce a range absent from the selected aggregate context; and
- never receive raw votes, user IDs or individual characteristic records.

Country-specific income candidates use local keys. A cross-market income candidate receives only a
relative local-position label and no numeric boundary, because no single real monetary range exists.

Update `CohortDisplayNames` to consume governed display metadata. Remove its fallback that turns
`V2_TIER_3` into user-facing prose.

## 7. Frontend changes

### Onboarding

- Continue asking residence before finance.
- Render the active profile and bands returned by the backend.
- Clearly distinguish annual personal income before tax from annual household income before tax.
- When residence/profile changes, clear both selected band IDs.
- When an existing answer references an inactive profile, show a focused request to refresh only
  the finance answers.
- Never calculate, convert or regex-transform ranges on the client.

### Results

- Replace `prettifyBucket` for governed result labels with the API's `label`.
- Require or show country context for numeric income ranges.
- Separate personal and household income selectors.
- Show the relative-position explanation as supporting context, not as `TIER_n`.
- Never combine numeric ranges from different markets, currencies or versions into one visual
  bucket.
- Preserve the existing privacy-suppressed empty state.

## 8. Initial data and migration

### Seed profiles

Create a dedicated Liquibase data migration for reviewed profiles. Seed only profiles whose
personal and household evidence packs meet ADR-031.

- Import GB/GBP and IN/INR with their exact current public IDs only after source review.
- Import other generated fallback profiles as inactive drafts, or omit them until reviewed.
- Record every source and derivation in `income_profile_source`.
- Validate profile data through the same domain validator used by later activation.

### Backfill characteristic foreign keys

1. Match current version-2 `income_profile_id` strings to `income_profile.public_id`.
2. Match personal and household band codes within that profile.
3. Populate the three new reference columns.
4. Report unmatched rows and stop the migration rather than guessing.
5. Add foreign keys after a successful backfill.

Legacy version-1 rows remain unchanged and keep all profile references null.

### Compatibility sequence

1. Add catalogue tables and profile reference columns.
2. Seed reviewed profile rows and bands.
3. Backfill current version-2 characteristics.
4. Deploy code that reads database profiles and dual-reads old/new characteristic references.
5. Switch new writes to foreign keys while retaining public string values for compatibility.
6. Extend new vote snapshots with local identities.
7. Deploy result and Unwrapped resolution.
8. Audit all old string readers and seeds.
9. Remove redundant characteristic string columns only in a separate later migration.

Do not rewrite historical vote JSON. Compatibility adapters handle older snapshot shapes.

## 9. Administration

The first release may manage profile data through reviewed Liquibase changes. The domain model must
also support a later admin UI without changing lifecycle rules.

Planned admin operations:

```text
GET  /admin/income-profiles
POST /admin/income-profiles
PUT  /admin/income-profiles/{id}
POST /admin/income-profiles/{id}/validate
POST /admin/income-profiles/{id}/activate
POST /admin/income-profiles/{id}/withdraw
```

Only drafts may be edited. Activate and withdraw are explicit commands, role-gated to admins and
audited. The admin UI must show which version an active profile superseded and how many current
characteristic answers reference each version before withdrawal.

No endpoint deletes a published profile or band.

## 10. Privacy, security and observability

- Keep exact income absent from every request, table, snapshot and prompt.
- Treat income range, country and profile as quasi-identifiers.
- Apply existing minimum sample and intersection floors before direct display or Unwrapped
  selection.
- Consider a higher or merged threshold for rare upper-income bands before public launch.
- Resolve display data only for already-surfaced aggregate buckets.
- Do not log selected user band IDs or boundaries alongside user identity.
- Record operational metrics for active-profile lookup, unsupported market, inactive-answer save,
  legacy income exclusion, display-resolution failure and profile activation.
- Activation audit records include admin identity, old/new profile IDs and timestamps, but no user
  answers.
- A missing historical profile is a data-integrity error, not permission to substitute the active
  profile.

## 11. TDD and verification plan

Write the complete focused test set first, prove the new tests fail for the expected missing
behaviour, run the `test-audit` skill, implement the production changes, then run the targeted
suites once after the coherent implementation. Do not repeatedly run the full suite after each
small edit.

### Domain unit tests

- Draft validation pins exact accepted GB and IN boundaries.
- Validation rejects missing personal/household bands, gaps, overlaps, reversed ranges, duplicate
  order/tier/code, invalid currencies, missing residence countries and missing evidence.
- Publishing a replacement deactivates the old profile and activates the new profile.
- Concurrent activation cannot leave two active profiles.
- Published semantic data cannot be edited or deleted.
- An inactive historical profile resolves display data but is rejected for a new answer.
- Answer resolution rejects cross-profile and wrong-measure band codes.
- Batch display resolution returns exact boundaries, currency, market, measure and relative label.

### Backend integration tests

- Liquibase creates constraints, foreign keys and the partial unique active index.
- Seeded reviewed profiles have one active version per market/currency/basis.
- Options endpoints return active rows only and exact ordered bands.
- Saving and reading an answer stores the expected profile/band foreign keys and derived tiers.
- Replacing a profile leaves old answers readable and marks them for refresh.
- Vote creation freezes local identity and relative tier without user identity.
- Old vote snapshot JSON still deserializes with legacy semantics.
- Direct results return exact country-specific labels after suppression.
- Different countries and profile versions do not merge into a numeric bucket.
- Missing historical references fail visibly instead of using the current active profile.

### Unwrapped tests

- Selected local income candidates contain exact structured range context.
- The agent request contains country, currency, measure, bounds and display label.
- The agent never receives raw votes, user IDs or exact income.
- Cross-market candidates contain only relative-position context, not a fabricated numeric range.
- Inactive historical profiles still resolve for old votes.
- Suppressed income cohorts are not resolved or included in the request.
- `CohortDisplayNames` never emits `Tier 3` or `V2 Tier 3`.

### Frontend tests

- Onboarding renders exact backend GBP and INR labels.
- Residence/profile changes clear both income selections.
- Inactive saved profiles trigger a finance refresh state.
- Results render API labels without `prettifyBucket`.
- Numeric income results always show country context.
- Personal and household labels remain distinct.

### Migration tests

- Representative version-2 rows backfill to the exact profile and band foreign keys.
- An unmatched public profile or band code aborts instead of guessing.
- Legacy version-1 rows remain unchanged with null references.
- A second version can be added and activated without changing version-1 rows.

After implementation, use Java from
`/Users/maxpersonal/.sdkman/candidates/java/current`, run backend formatting/lint checks where
configured, targeted backend unit/integration tests, targeted frontend Jest tests, then the normal
repository test command before a PR.

## 12. Rollout

1. Review the GB and IN evidence packs and approve exact personal and household definitions.
2. Deploy additive tables, constraints, initial profiles and characteristic reference columns.
3. Deploy the database-backed catalogue behind the existing options API.
4. Backfill and verify version-2 characteristic references.
5. Release enriched vote snapshots while keeping old snapshot compatibility.
6. Release structured direct-result labels and country-first income presentation.
7. Release structured Unwrapped income context and prompt rules.
8. Monitor unsupported markets, inactive profile saves, suppression and resolver failures.
9. Add further countries only after their evidence and bands pass the same review.
10. Add the admin draft/publish UI when operational profile management is needed.

Rollback disables new profile activation and income reporting but does not delete database profiles,
answers or snapshots. Previously stored public IDs remain resolvable.

## Acceptance criteria

- The database, not Java constants, is the source of all active and historical income profiles.
- Exactly one profile is active for a market/currency/basis combination.
- Publishing a new version adds a row, deactivates the previous row and preserves historical reads.
- Published profile semantics and bands cannot be edited or deleted.
- New answers use only active profiles compatible with residence country.
- Vote snapshots preserve local profile/band identity and relative tiers without identity or exact
  income.
- Public numeric results show real market-qualified ranges and never `TIER_n`.
- Post Unwrapped receives the same real structured range context as the public result resolver.
- Cross-market analysis never claims one monetary range or equivalent living standards.
- Legacy and tier-only snapshots remain readable without fabricated country or currency meaning.
- All income output remains subject to privacy suppression.
