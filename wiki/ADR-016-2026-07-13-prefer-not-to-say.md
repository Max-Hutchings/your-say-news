# ADR-016: "Prefer not to say" is not an option

## Situation

The user-characteristics reform (see `docs/characteristics/`) repeatedly proposed adding a
`Prefer not to say` / `PREFER_NOT_TO_SAY` response to sensitive fields (identity, sex at birth,
religion, religiosity, politics, income, housing, family). The product's core value is aggregate,
anonymised sentiment broken down by characteristic. Sparse "no answer" buckets weaken every
breakdown and give no analytical signal.

## Options considered

1. Add `Prefer not to say` to sensitive fields and treat it as a complete answer.
2. Add `Prefer not to say` only to the most sensitive fields.
3. Do not offer `Prefer not to say` on any characteristic. Every characteristic is a required
   choice from real, substantive options.

## Decision

**Option 3.** `Prefer not to say` is never an option on any user characteristic. Onboarding
collects a real answer for every field from its substantive option set. Genuine-uncertainty
answers that carry signal (for example `Not sure` on political leaning, `Mixed / depends` on
outlook) are allowed where they describe a real state; a refusal-to-answer escape hatch is not.

## Reason

- The product only works if characteristics are populated; aggregate breakdowns need every
  respondent placed in a real bucket.
- A `Prefer not to say` bucket carries no analytical signal and fragments small groups further.
- Anonymity is already protected by design: PII is kept separate from characteristics, and
  breakdowns are only ever shown in aggregate. Users do not need per-field refusal to stay
  anonymous.

## Consequences and follow-up work

- No characteristic field defines a `PREFER_NOT_TO_SAY` enum value or option.
- Required-completion logic keeps every characteristic mandatory; there is no "answered by
  refusing" path.
- The `docs/characteristics/` reform notes drop all `Prefer not to say` recommendations.
- Distinguish refusal (not offered) from real uncertainty (`Not sure`, `Mixed / depends`,
  `Other`), which remains allowed where it is a genuine, substantive answer.
