# ADR-006: Split Income Fields

Date: 2026-06-30

## Situation

Income was collected as a single annual household income band. That loses useful signal because a
person's own income and household income can differ materially.

The existing `100k-200k` bucket was also too broad.

## Options Considered

1. Keep one household income field.
   This is simpler, but mixes personal finances with household context.

2. Replace household income with personal income only.
   This improves individual-level analysis but loses household context.

3. Collect both personal and household income.
   This adds one extra required answer but preserves both useful aggregate axes.

## Decision

Collect both annual personal income and annual household income.

Split the old `100k-200k` band into `100k-150k` and `151k-200k`.

## Reason

The app's analysis depends on meaningful characteristic buckets. Personal and household income answer
different questions, and the narrower upper-middle bands avoid grouping materially different income
levels together.

## Consequences

- Frontend submits `personalIncomeRange` and `householdIncomeRange`.
- Backend stores `personal_income_range` and `household_income_range`.
- Post-service snapshots expose `personalIncomeRange` and `householdIncomeRange` as separate axes.
- Existing single income values are copied into both new fields during migration.
- The legacy `income_range` column remains nullable for compatibility but is no longer written by the
  application.
