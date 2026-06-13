# Vote Aggregation — the privacy contract

**Status:** Contract locked (Stage 0) · **Lives in:** `post-service`, `votes` domain

This is the single most important contract in the product: reporting **how different kinds of people
feel about a topic** as aggregates, **without ever exposing who voted how**. Get it right once.

## The shape

All public types sit at the top of the `com.yoursay.votes` package (the domain's public face);
everything else is internal to `votes/service`.

| Type | Role |
| --- | --- |
| `CharacteristicSnapshot` | Anonymised, point-in-time copy of a voter's **categorical** characteristics, frozen onto each vote. **No identity** — no user id, name, email, exact DOB or postcode. The bridge that lets aggregation slice by characteristic without touching `user-service`. |
| `BucketSentiment` | One breakdown row: `bucket`, `yesCount`, `noCount`, `total`, `yesPct`, `noPct`. **Counts and percentages only.** The atom of the contract — if a field here could name a person, the contract is broken. |
| `SentimentBreakdownDto` | The public result for one post along one axis: `postId`, `characteristic`, `List<BucketSentiment>`, `suppressedBuckets`. Aggregate-by-construction. |
| `SentimentAggregator` | Public interface: `overallSentiment(postId)` and `sentimentByCharacteristic(postId, axis)`, both returning `Uni<SentimentBreakdownDto>`. |

Internal: `SentimentTally` (the pure grouping/percentage/suppression engine), `VoteSnapshot`
(`voteFor` + snapshot), `SentimentAggregatorImpl` (wires repository + config → engine).

## The PII boundary (non-negotiable)

- Identity travels **only** in the bearer token and lives **only** in `user-service`'s `user` domain.
- A vote row may reference the user **server-side** for "have I voted / one-per-user", but
  **aggregation output never carries that linkage** — `SentimentBreakdownDto` has no per-user field.
- Aggregation reads the **snapshot on the vote**, never a live join into `user-service`. This also
  means later profile edits don't retro-rewrite historical aggregates (roadmap risk #4).

## k-anonymity suppression

The small-bucket re-identification risk is handled by a single lever:

```
votes.aggregation.suppress-below = 0   # application.properties, MVP1 default
```

`sentimentByCharacteristic` withholds any bucket whose `total < k` and reports how many in
`suppressedBuckets`. **MVP1 ships `k=0` (no suppression)** — a known, flagged risk (roadmap risk #1).
Hardening is a **one-line config flip**, no code change; the suppression path is already implemented
and tested. This is the top privacy fast-follow.

## Stage status

- **Done (Stage 0):** the full contract, the `SentimentTally` engine, the `k`-suppression logic, and
  the OVERALL aggregation path — all exercised by `SentimentTallyTest` with pinned expected values.
- **Stage 3:** persist the `CharacteristicSnapshot` onto each `Vote` at vote time. Until then
  `SentimentAggregatorImpl` attaches `CharacteristicSnapshot.empty()`, so by-characteristic breakdowns
  return a single `UNKNOWN` bucket. The maths they rely on is already proven.
- **Stage 4:** the results UI consumes `SentimentBreakdownDto`; the central fixtures workstream casts
  known vote distributions and asserts the breakdowns match exactly.
