# ADR-013: Sentiment Results Gated Behind "Must Have Voted"

Date: 2026-07-07

## Situation

Stage 4 exposes the heart of the product — aggregated, anonymised "how different kinds of people
feel" results — over REST. The Stage 0 aggregation engine (`SentimentAggregator` +
`SentimentBreakdownDto`) was already built and unit-tested; what was missing was the HTTP surface
that serves it and the rules governing who may read it and how.

Two product/privacy constraints shape that surface:

1. **Results are unlocked _after_ voting** (roadmap Stage 4). A reader should form and cast their
   own opinion before seeing the crowd's, and the vote is what generates the data in the first
   place.
2. A breakdown must be **honest**. Slicing by an axis that does not exist would silently return a
   single all-`UNKNOWN` bucket — a real-looking result that means nothing.

## Options Considered

1. **One endpoint, axis as an optional query param** (`GET /votes/{postId}/sentiment?axis=…`).
   Fewer routes, but conflates "overall" and "by-axis" into one handler and one response shape and
   makes the axis feel optional rather than a distinct resource.
2. **Two endpoints** — `GET /votes/{postId}/sentiment` (overall) and
   `GET /votes/{postId}/sentiment/{axis}` (by characteristic). Each is a clear, cacheable resource;
   the axis is a path segment, so an unknown axis is a clean 404-shaped concept validated up front.
3. **Gate results client-side only** (unlock the UI after voting, leave the API open). Simple, but
   trivially bypassed and leaks the aggregate to non-voters — unacceptable for the product promise.
4. **On an unknown axis, return the all-`UNKNOWN` bucket** the engine already produces. No extra
   code, but returns a misleading result that looks valid.

## Decision

Adopt **option 2 + server-side gating + reject unknown axes (400)**.

- **Two GET endpoints** in the `votes` domain controller (`VoteController`), returning the existing
  `SentimentBreakdownDto` JSON unchanged:
  - `GET /votes/{postId}/sentiment` → overall (`SentimentAggregator.overallSentiment`).
  - `GET /votes/{postId}/sentiment/{axis}` → by characteristic (`sentimentByCharacteristic`).
- **Gating, enforced server-side as defence in depth** (a new `VoteService.assertResultsUnlocked`,
  called before any aggregate is computed):
  - Role `user` required (as for the rest of `VoteController`) → wrong role **403**, unauthenticated
    **401**.
  - **404** if the post does not exist — checked first, so an unknown post is disclosed as
    not-found before anything vote-related is revealed.
  - **403** (`VOTE_RESULTS_LOCKED`) if the caller has not voted on the post (reusing
    `getMyVote(...)` presence to decide). This is the core new rule.
  - **400** (`VOTE_UNKNOWN_AXIS`) if `{axis}` is not a real `CharacteristicSnapshot` field, validated
    against a new shared `CharacteristicSnapshot.AXES` set (`isAxis(...)`) so controller and tests
    share one source of truth rather than trusting `bucketFor` to silently bucket into `UNKNOWN`.
- **Injection.** The controller injects the already-public `SentimentAggregator` directly (simpler
  than routing aggregation through `VoteService`); gating logic lives in `VoteService` where it is
  unit-testable. The endpoints stay imperative + `@RunOnVirtualThread` like the rest of the domain.
- **Post existence** is checked inside the `votes` domain via a native existence read on the `post`
  table (`VoteRepository.postExists`) — the votes domain already references that table by the
  `fk_votes_post` foreign key — avoiding a bridge into the reactive `posts` persistence unit for a
  boolean.

## Reason

Two endpoints keep each result a clean, distinct resource and make "unknown axis" a validation
concern resolved before any work. Gating on the **server** (not just the UI) is the only way to keep
the promise that aggregates are earned by voting and never leaked to a passer-by. Rejecting an
unknown axis with a 400 stops the engine returning a plausible-looking but meaningless all-`UNKNOWN`
bucket. Throughout, the response is still the aggregate-only `SentimentBreakdownDto` — counts and
percentages, never an individual vote or identity — so the PII boundary is untouched.

## Consequences

- New public surface on the `votes` domain: `VoteService.assertResultsUnlocked`,
  `CharacteristicSnapshot.AXES` / `isAxis`, and two `VoteApiException` factories
  (`resultsLocked` → 403, `unknownAxis` → 400).
- `CharacteristicSnapshot.AXES` must stay in lockstep with the `bucketFor` switch; a unit test pins
  that they match (every declared axis resolves) so the set cannot silently drift.
- Correctness is proven by an integration test that seeds a **known, deterministic** vote
  distribution with characteristic snapshots straight into the votes table and asserts the
  by-characteristic breakdown matches it **exactly** (per-bucket counts and percentages) — the
  essential Stage 4 slice of Workstream T.
- The "must have voted" gate makes a truly empty (zero-vote) result unreachable over HTTP for a
  voted caller; the engine's empty-result behaviour is covered at the unit level instead.
- Follow-up: the `k`-anonymity suppression threshold remains a config flip
  (`votes.aggregation.suppress-below`, default 0) behind the same endpoints — no API change needed
  to harden it later.
