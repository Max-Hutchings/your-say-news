# ADR-046 - Cohort re-identification floors

Date: 2026-08-12

## Situation

ADR-044 removed the effect-size and false-discovery gates from cohort selection, leaving eligibility
decided by privacy and governance only. It stated that the existing privacy protections "remain" but
did not re-examine whether those floors are still the right ones now that they are the *sole*
barrier between a characteristic group and published prose.

The floors are set in `PostAnalysisAggregateBuilder`: at least 100 votes on the post, at least 30
members for a single-axis group, at least 40 for an intersection, at least 5% audience share, and
the group must be smaller than the whole audience.

No identifier can reach the model. The chain is `votes -> VoteSnapshot(optionId, snapshot) ->
cohorts -> option brief`, and `VoteSnapshot` drops `user_id` at the first step; every field of
`CharacteristicSnapshot` is an enum-coded constant with no free text. The residual risk is therefore
not disclosure of a name or an email, but **re-identification from a rare combination of
characteristics** — inferring who someone is, and how they voted, from the group description alone.

That risk is not uniform across axes. `gender` and `ageRange` are broad. `sexualOrientation`,
`religion`, `race`, `disabilityType` and `neurodivergenceType` are special-category data, and a
30-member group on one of those, on a post with a local or niche audience, carries more inferential
risk than a 30-member age band.

## Options considered

1. Keep the current floors unchanged for every axis.
2. Raise the floor for the special-category axes specifically (for example 100 members), keeping 30
   for the broad ones.
3. Keep the special-category axes in the aggregate for transparency but exclude them from the
   cohorts offered to the model, so they never appear in published prose.

## Decision

Choose option 1. The existing floors — 100 post votes, 30 single-axis, 40 intersection, 5% share,
and the whole-audience exclusion — stay as they are for all axes at this stage.

## Reason

At current audience volumes a group must clear 30 members *and* 5% of the post's voters, which on a
100-vote post means the group is at minimum a thirtieth of a small, self-selected audience rather
than a named individual. Raising the floors now would suppress most cohorts on most posts and
undermine the characteristic-led experience ADR-044 was written to deliver, in exchange for guarding
against a scenario the product has not yet reached.

This is explicitly a decision about *this stage*, not a judgement that 30 is permanently sufficient.

## Consequences or follow-up work

- Revisit this ADR when any of the following becomes true: posts routinely draw audiences large
  enough that 5% share stops being a meaningful constraint; Unwrapped begins publishing to a
  geographically narrow audience; or a special-category cohort appears in published prose and reads
  as identifying on review.
- The named revisit trigger is the point of this record. Without it, "adequate at this stage" decays
  silently into "adequate", which is how privacy floors go stale.
- `modelInputCarriesNoVoterIdentityEvenWhenTheVoterHasFullProfileData` in
  `UnwrappedAdminControllerTest` now proves the identifier boundary: a real account with a name,
  email, handle and date of birth casts a counted vote, and none of those values, nor any
  per-person field, may appear in the payload sent to the provider. It asserts values rather than
  field names, because the previous guard searched for the string `"email"` and would not have
  noticed a leaked address.
- That test covers identifiers only. It cannot detect re-identification risk, which is a judgement
  about group size and sensitivity, not a string that can be searched for.
