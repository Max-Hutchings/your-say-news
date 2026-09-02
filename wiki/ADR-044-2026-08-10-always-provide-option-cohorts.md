# ADR-044 - Always provide option cohorts

Date: 2026-08-10

## Situation

Post Unwrapped is intended to explain voting options through characteristic groups. The existing
selector requires a 10 percentage-point difference and an adjusted `q <= 0.05`. Those statistical
gates can leave an option without any group even when there are large, privacy-safe cohorts in the
vote data. The resulting general option article does not provide the characteristic-led experience.

## Options considered

1. Keep the effect and significance gates and allow general option articles.
2. Lower the thresholds until the current fixture passes.
3. Always shortlist the strongest available privacy-safe groups, while retaining the calculated
   effect and uncertainty values as descriptive context.

## Decision

Choose option 3.

- Every option receives a deterministic shortlist whenever at least one governed, privacy-safe
  characteristic group is available.
- Percentage-point difference and adjusted significance no longer decide whether a group can enter
  the shortlist.
- Existing privacy protections remain: at least 100 post votes, at least 30 members for a
  single-axis group, at least 40 for an intersection, and at least 5% audience share.
- A group containing the whole audience is excluded because it has no outside comparison group.
- Two-characteristic groups must use exclusive membership and match the governed intersection
  allowlist.
- Ranking remains deterministic. The strongest available groups are ranked separately for every
  option, so the same group may legitimately be supplied for more than one option.
- Effect size, confidence interval and adjusted q-value remain in the aggregate and model input as
  descriptive facts. They are not eligibility gates.
- An empty shortlist remains possible only when no governed group passes the privacy sample floors.
  The system must not invent a group in that case.

## Reason

The product value is a characteristic-led explanation for every option, not only options with a
large statistically corrected difference. Keeping the privacy and governance floors prevents tiny
or sensitive groups from entering generated prose, while removing the effect and significance
gates prevents valid options from silently becoming generic arguments.

Allowing the same group on opposing options is honest when that is what the available aggregate
data supports. The agent receives the exact option-specific counts and percentages needed to
explain the different reasons members of that group may have chosen each option.

## Consequences

- Unwrapped candidates must be described as privacy-safe, not statistically significant.
- Reviewers may see modest, neutral or repeated cohort patterns and must judge the quality of the
  researched explanation rather than treating selection as proof of a demographic effect.
- Multiple-comparison statistics remain available for transparency and future presentation.
- This decision supersedes ADR-028's effect-size and false-discovery eligibility gates.
- This decision supersedes ADR-041's empty-shortlist path when any governed privacy-safe group is
  available. ADR-041 still applies when no such group exists.
