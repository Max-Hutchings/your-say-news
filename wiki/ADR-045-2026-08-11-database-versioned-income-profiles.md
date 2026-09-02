# ADR-045 - Database-backed versioned income profiles

Date: 2026-08-11

## Situation

ADR-031 established market-informed income profiles: a person's residence market and currency
select locally meaningful annual personal and household income ranges, while a server-derived
relative tier supports carefully governed comparison. Exact income is never collected.

The first implementation keeps profile definitions in `IncomeProfileCatalog` Java code. That is
manageable for a pilot, but it does not scale cleanly to many countries, profile revisions,
evidence records and later administration. It also makes a profile change a code change and
deployment concern.

Income profiles have historical meaning. If a published range is edited in place, an old vote can
silently change from meaning `GBP 25,000 to GBP 40,000` to a different range. The characteristic
answer, direct result and Post Unwrapped explanation must all retain the meaning that existed when
the vote was cast.

The existing vote snapshot currently keeps only the relative `V2_TIER_n` income bucket. That is
insufficient for displaying the local range or supplying its real country-specific boundaries to
Post Unwrapped.

## Options considered

### 1. Keep profiles in Java or packaged resource files

This provides Git review and immutable deployments, but becomes cumbersome as the catalogue grows
and requires a deployment for every profile publication.

### 2. Store one mutable row for each market and currency

This makes administration simple, but editing boundaries changes the meaning of historical
answers and vote snapshots.

### 3. Store immutable profile versions in the database with an active flag

Every revision creates a new profile row and new band rows. The new profile becomes active and the
previous profile becomes inactive in one transaction. Inactive published profiles remain available
for historical resolution.

## Decision

Choose option 3.

The `usercharacteristic` domain owns database-backed income profiles, bands, residence-country
mappings and source evidence. The database is the source of truth; Java code does not contain
country thresholds.

Each profile row has an `active` boolean:

- `active = true` means the profile is offered for new characteristic answers.
- `active = false` and `published_at IS NULL` means the row is an editable draft.
- `active = false` and `published_at IS NOT NULL` means the row is a retained historical version.

At most one active profile may exist for a market, currency and income basis. Publishing a new
version inserts or completes a new row, deactivates the previous row and activates the new row in
one transaction. The database enforces the single-active-profile invariant with a partial unique
index.

Once a profile has been active, its market, currency, definitions, bands, sources and version cannot
be edited. Its `active` value may transition from `true` to `false`; the row and its bands must not
be deleted. A correction or threshold change creates a new version row. Previously published rows
must not be reactivated.

Each profile contains both annual gross personal-income and annual gross household-income band
sets. Every band stores lower-inclusive and upper-exclusive amounts, its measure, display order and
relative reporting tier. Monetary boundaries use fixed integer amounts in the profile currency,
never floating point.

Profiles are keyed by economic market and ISO currency, not currency alone. Countries sharing EUR
may have different profiles. Residence-country mappings determine which active profiles a user may
select. The post's jurisdiction does not determine a voter's income profile.

Characteristic answers reference the selected profile and personal and household band rows. The
server validates that the profile is active for a new answer, that it is compatible with the user's
residence, and that both bands belong to it. Relative tiers are derived from the stored band rows;
the client never submits trusted boundaries, labels or tiers.

Vote-time characteristic snapshots retain both representations:

- the immutable public profile identity/version, market, currency and local band identities needed
  to reproduce the country-specific ranges; and
- the personal and household relative tiers used for governed cross-market analysis.

The direct sentiment API and Post Unwrapped use one shared, batch-oriented display resolver. It
turns aggregate income dimensions into structured country, currency, measure, boundaries, local
range label and relative-position context. Resolution occurs only after privacy suppression. Raw
individual answers or vote snapshots never enter the Unwrapped prompt.

Numeric income cohorts are profile-specific. Different countries, currencies or profile versions
are not combined under one numeric range. A cross-market cohort may use a human relative-position
label, but must not claim a single monetary range.

## Reason

Database storage supports a large catalogue and later admin workflows without sacrificing
historical accuracy. The active flag makes the selection rule simple, while immutable version rows
preserve what an answer and vote meant at the time.

Keeping local range identity and relative position separately serves two different product needs:
users and the agent get understandable real ranges, while internal analysis can compare a person's
position within their local distribution without pretending nominal amounts are equivalent.

A shared resolver prevents the mobile results and Post Unwrapped from describing the same cohort
differently. Resolving only aggregate, privacy-safe cohorts maintains the separation between user
identity, characteristics and published reporting.

## Consequences

- Add `income_profile`, `income_profile_country`, `income_band` and `income_profile_source` tables.
- Add database references from version-2 characteristic answers to the selected profile and bands.
- Keep stable public profile and band codes in API contracts and vote JSON; database numeric IDs
  remain internal.
- Replace the hard-coded `IncomeProfileCatalog` with repositories and services owned by the
  `usercharacteristic` domain.
- Retain every published profile and band required by an answer or vote snapshot.
- Seed the initial reviewed profiles through Liquibase. A later admin workflow may create drafts and
  publish new versions using the same domain service.
- Extend vote snapshots so local profile and band identities survive vote-time freezing.
- Extend direct result DTOs and Unwrapped candidate context with resolved, structured display data.
- Keep legacy income answers isolated. Do not infer a country, currency or normalized tier for
  legacy rows. New vote snapshots omit legacy income enums and report income as unknown until the
  characteristic answer is upgraded to a valid country profile.
- Enforce versioned income snapshot structure in the vote database. A stored income bucket must
  match its structured profile, measure, band and relative tier; tier-only or inconsistent values
  are rejected rather than displayed with a fallback label.
- Treat unresolved aggregate income buckets as data-integrity failures. Direct results and Post
  Unwrapped must never invent a monetary label from a bare tier.
- Apply minimum cohort suppression before resolving or returning income labels.
- ADR-031 remains authoritative for market-informed ranges, source quality and no exact-income
  collection. This ADR supersedes only its packaged-resource implementation direction.
- The implementation sequence is defined in
  [Database-backed country income profiles](../docs/plans/database-backed-country-income-profiles.md).
