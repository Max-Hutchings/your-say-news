# ADR-017: Collect current age as a number, not an age band

## Situation

Onboarding collected age as a fixed band (`AgeRange` enum: `18-24`, `25-34`, …). A stored band
goes stale: a user who selects `18-19` is silently in the wrong bucket a year later, and the data
never self-corrects. The product reports sentiment by age, so drifting age data degrades every
age breakdown over time.

## Options considered

1. Keep the age band enum and periodically re-prompt users to update it.
2. Collect and store an exact date of birth, deriving age on read.
3. Collect current age as a number, store the derived birth year, and recompute age each year.

## Decision

**Option 3.** Ask for the user's current age as an integer (minimum 16). Store the derived
`birthYear = currentYear - age` — not the band and not an exact date of birth. Compute
`age = currentYear - birthYear` on read so it auto-increments by one every year with no user
action. Aggregate reporting bands (`16-17`, `18-19`, `20-24`, `25-34`, `35-44`, `45-54`, `55-64`,
`65-74`, `75-84`, `85+`) are derived from the computed age purely as a reporting axis.

## Reason

- A number that recomputes yearly stays correct forever; a stored band does not.
- Birth year alone is not PII, unlike an exact date of birth, so it keeps the characteristic data
  clean of identifying detail (see the PII rule in `CLAUDE.md`).
- Reporting still uses bands, so aggregate breakdowns and small-group handling are unchanged.

## Consequences and follow-up work

- Replace the `AgeRange` enum input with an integer age field and a stored `birthYear`; validate a
  minimum age of 16 and reject anything lower.
- Bands become a derived reporting axis computed from age, not a stored value.
- Existing band answers cannot be converted to an exact age. Keep them as legacy reporting-band
  values or prompt those users to re-enter their age; new answers store `birthYear`.
- Never store or report an exact date of birth as a characteristic.
