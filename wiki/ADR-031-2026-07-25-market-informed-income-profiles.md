# ADR-031 — Market-informed income profiles

Date: 2026-07-25

## Situation

Onboarding offers one numeric income-band list for every currency. The mobile app changes only the
label prefix, does not submit currency, and sends the same backend `IncomeRange` enum for GBP, INR
and every other choice. The stored value and vote snapshot therefore have no reliable currency or
local economic meaning.

Nominally identical amounts are materially different across economies. Currency alone is also
insufficient: EUR and other currencies are used across markets with different income distributions.
The product needs understandable local choices and defensible aggregate comparison without
collecting exact income or exposing sensitive local amounts.

## Options considered

### 1. Keep one band list and replace the currency prefix

This preserves the current model but knowingly presents unreasonable choices and makes unlike
answers look equivalent.

### 2. Convert one reference list with live foreign-exchange rates

This makes numbers move with the selected currency, but spot FX measures market exchange, not local
income distributions or purchasing power. It also introduces runtime third-party availability,
non-reproducible historical answers and frequent threshold churn.

### 3. Ask for exact income and normalize it on the server

This would simplify band assignment and future rebucketing, but exact income is unnecessarily
sensitive, conflicts with the product's band-first privacy posture and increases breach and
re-identification impact.

### 4. Use immutable, versioned market-and-currency income profiles

Each reviewed profile has separate gross annual personal and household bands in local currency,
derived primarily from official income distributions. Purchasing-power data is a documented
fallback, not a live conversion. Bands map server-side to broad, currency-neutral ordinal tiers for
aggregate reporting.

## Decision

Choose option 4.

The backend owns immutable income profiles keyed by economic market and ISO currency. Residence
context selects the market and the user's currency selects the amount unit; unsupported combinations
fail explicitly. Currency is therefore domain data, not presentation-only formatting. This
supersedes that narrow statement in ADR-018 while preserving its backend-owned, versioned catalogue
direction.

Profiles use separately researched personal and household cut points, approximately anchored to
P10, P25, P50, P75, P90 and P95 and rounded for local comprehension. Each band maps to a canonical
ordinal tier used for reporting. Source publisher, dataset, measure, reference year, derivation,
rounding and confidence are recorded. Threshold changes create a new profile version; onboarding
never depends on live FX or a live third-party data service.

New characteristic answers persist the currency, market, profile/version, selected band IDs and
server-derived tiers.

Vote snapshots carry two distinct aggregate-safe representations:

- the canonical tiers and answer version used for reviewed cross-market analysis; and
- the immutable profile/band IDs plus ISO currency needed to reproduce the selected local range in
  direct post results.

Direct user-facing results display local ranges grouped and labelled by currency. They do not
present canonical tiers such as `TIER_3`, and they do not imply that nominal ranges in different
currencies are directly comparable. Profile/band identifiers resolve through retained immutable
catalogue versions; exact user income is never collected or snapshotted.

Existing nominal range values are retained as `LEGACY_NOMINAL_V1`. Because their currency was never
stored, they are not inferred from residence, converted, or combined with the new cross-market
tiers. Users must refresh their finance answers before contributing to version-2 income analysis.

## Reason

Official market distributions make choices meaningful to users, while immutable versions make an
answer reproducible after sources and economies change. Purchasing-power calibration is more
appropriate than spot FX when a direct distribution is unavailable, but recording the fallback and
its confidence prevents false precision.

Server-derived ordinal tiers support useful cross-market aggregate comparisons without storing exact
income in vote snapshots. Separating personal and household definitions preserves ADR-006 and avoids
pretending that one distribution describes both measures. Refusing to guess the semantics of legacy
rows is more accurate than a convenient but irreversible backfill.

Retaining the selected currency-qualified band alongside the tier supports understandable direct
results without reversing the privacy decision: the snapshot still contains a governed range, not
an amount. Keeping the direct local-band axis distinct from the internal tier axis prevents the UI
from presenting unlike nominal ranges as one ordered scale.

## Consequences and follow-up work

- Replace the single `IncomeRange` choice model for new answers with a profile catalogue and
  versioned income-answer value object.
- Add profile/currency/market/band/tier persistence while retaining old range columns and values for
  a compatibility window.
- Extend the options API and save DTO additively, then migrate current clients to version 2 before
  rejecting legacy-shaped new answers.
- Research and review GB/GBP and IN/INR first. Expand supported choices only when both personal and
  household evidence packs meet the profile standard.
- A currency selection may have multiple market profiles; the UI must use stable residence context
  and must clear selected bands whenever the profile changes.
- Historical vote snapshots remain unchanged. Reporting must isolate or exclude legacy nominal
  income and apply minimum-bucket suppression to the new tiers.
- Direct results use immutable currency-qualified personal/household band buckets and labels.
  Internal cross-market analysis continues to use the separate canonical tier fields.
- Profile definitions remain available indefinitely for historical reads and are reviewed at least
  annually; updates publish new versions rather than mutating old thresholds.
- The implementation and rollout details are in
  [Currency-aware income bands](../docs/plans/currency-aware-income-bands.md).
