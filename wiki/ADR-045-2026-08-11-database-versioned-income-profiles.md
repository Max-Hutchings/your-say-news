# ADR-045 - Database-backed versioned income profiles

Date: 2026-08-11

## Situation

Income tiers are useful internally, but `TIER_3` does not tell a user what income range it means.
The real monetary boundaries also differ by country because the same nominal amount has different
local purchasing power and income-distribution meaning.

The original catalogue generated profiles in Java. That does not scale well to many countries and
makes every range revision a deployment. Editing a published range in place would also change the
meaning of historical answers, votes and Post Unwrapped output.

## Options considered

1. Keep profile data in Java or resource files. This gives Git history but makes catalogue updates
   code deployments.
2. Keep one mutable database profile per country. This is simple, but rewrites historical meaning.
3. Store immutable, versioned database profiles and select the current one with an `active` flag.

## Decision

Choose option 3.

One `income_range_profile` row represents the full range profile for a market, currency and income
basis. It owns separate personal and household bands, residence-country mappings and source
evidence.

- `active = true` means the profile can be selected for a new answer.
- `active = false` with no `published_at` means an editable draft.
- `active = false` with `published_at` means an immutable historical version.
- Only one profile can be active for a market, currency and income basis.
- Changing any published range creates a new profile and band rows. Activation retires the old row
  and activates the replacement in one transaction.
- Published bands, sources and country mappings cannot be updated or deleted.

Profiles are market-specific, not currency-only. Two countries using the same currency may have
different ranges. All boundaries are whole local-currency units with lower-inclusive and
upper-exclusive semantics. Exact income is never collected.

Versioned user answers store foreign keys to the profile, personal band and household band. The
existing stable public codes remain in API contracts and vote snapshots. The database and service
validate that both bands belong to the same profile and use the correct measure.

At vote time, the anonymised snapshot retains:

- public profile ID and version;
- market and currency;
- local personal or household band ID; and
- the server-derived relative tier.

Direct sentiment results resolve the frozen bucket into a structured range containing its label,
country, currency, boundaries, measure and relative local position. Post Unwrapped receives that
same structured object after aggregation and privacy suppression. It never receives an individual
answer, exact income or user identity.

## Reason

This preserves what every historical vote meant while allowing the catalogue to grow and be
updated without code changes. Users and Post Unwrapped get understandable local amounts, while the
relative tier remains available for carefully governed cross-market analysis.

A single resolver prevents the mobile results and Post Unwrapped from describing the same cohort
differently.

## Consequences and follow-up

- Postgres becomes the source of truth for all country income ranges and provenance.
- Liquibase seeds the initial 20 profiles that the application already supports.
- Inactive published profiles must be retained as long as an answer or vote can reference them.
- Legacy answers remain isolated and continue to use their legacy labels.
- Numeric income cohorts remain profile-specific. Cross-country comparisons may use relative local
  position, but must not claim one shared monetary range.
- A later admin workflow can create and validate drafts before calling the same activation service.
- The implementation details and verification plan are recorded in
  [Database-backed country income profiles](../docs/plans/database-backed-country-income-profiles.md).
