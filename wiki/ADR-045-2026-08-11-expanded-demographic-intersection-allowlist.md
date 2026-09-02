# ADR-045 - Expanded demographic intersection allowlist

Date: 2026-08-11

## Situation

Post Unwrapped initially generated five reviewed two-characteristic combinations. Benchmarking
showed that this excluded useful, understandable groups involving household income, political
persuasion, employment and geography.

Generating every possible pair would increase the comparison family, surface less meaningful
relationships and permit combinations that have not received product or privacy review.

## Options considered

1. Keep the original five combinations.
2. Generate every pair of reportable characteristics.
3. Expand the governed allowlist with selected combinations of existing core characteristics.

## Decision

Choose option 3.

Retain the original combinations:

- age range + gender;
- age range + occupation;
- age range + employment sector;
- personal income + gender; and
- political persuasion + personal income.

Add these combinations:

- household income + gender;
- age range + personal income;
- age range + household income;
- political persuasion + age range;
- political persuasion + gender;
- political persuasion + household income;
- gender + occupation;
- gender + employment sector;
- region + household income;
- region + employment sector;
- urban/rural + household income; and
- region + urban/rural.

The votes aggregate builder and Unwrapped narration selector must enforce the same 17-pair
allowlist. Intersections must retain exclusive membership semantics and the existing privacy,
sample and cohort-share rules.

Advance the named rule set from `cohort-rules-v1` to `cohort-rules-v2`. The additional searched
comparisons change multiple-comparison-adjusted values, so generated stories must record the new
rule-set version.

## Reason

The selected additions describe recognisable economic, political, workplace and geographical
groups that can support useful researched explanations. Keeping an explicit allowlist preserves a
reviewable product boundary and avoids arbitrary sensitive or semantically weak combinations.

## Consequences

- Aggregate snapshots contain more two-characteristic cohorts and tested comparisons.
- Adjusted q-values may change because correction covers the complete expanded comparison family.
- The narration shortlist still selects at most one non-redundant intersection per option.
- Further combinations require another explicit product and privacy review.
