# ADR-021 — Category-first feed ranking

## Situation

A behavioural “For You” feed would need many signals, careful diversity controls and enough usage
data to produce useful results. MVP1 already has a controlled topic taxonomy, which gives the
product a simpler discovery surface. Category discovery also needs to account for which posts are
popular in that topic and avoid repeatedly showing the same post to one viewer.

## Options considered

1. Build a cross-category personalised For You feed now from interests, follows and behaviour.
2. Keep every feed purely chronological and defer all ranking.
3. Ship category-first discovery: retain a simple Following/Latest feed, rank inside one topic at a
   time, and defer behavioural For You personalisation.

## Decision

Choose category-first discovery for MVP1. Do not ship or label a “For You” page yet.

- Following/Latest remains reverse-chronological with the existing followed-author boost.
- Each category feed considers only posts assigned to the selected canonical topic. It ranks them
  using popularity measured within that topic and a recency component, with deterministic ties and
  stable pagination.
- Feed-owned, private impression history is recorded only after a post is actually displayed to the
  authenticated viewer. Already displayed posts are excluded while unseen candidates remain. When
  a small category is exhausted, seen posts may reappear only with a large ranking penalty.
- Declared interests remain stored and editable but do not assemble or weight a cross-category feed
  in MVP1.

Exact popularity windows, engagement inputs, decay constants, impression retention and penalty
values belong in the Stage 6 implementation contract and must be pinned by tests before coding.

## Reason

Ranking inside a known category has a clear candidate set and an understandable success measure,
so it can produce useful discovery with far less behavioural data. Topic-relative popularity avoids
letting globally popular subjects drown out a smaller category. Recency prevents permanent winners,
while impression suppression gives users fresh content without making a sparse category appear
empty forever.

## Consequences and follow-up work

- Stage 6 needs a separate category ranker plus bounded, account-linked impression storage and a
  client signal for an actually displayed post.
- Dynamic popularity ranking needs an opaque stable cursor or ranking snapshot so pagination does
  not duplicate or skip posts as scores change.
- Impression data is personalisation state: it must not enter public profiles, post DTOs, vote
  snapshots, characteristic data or aggregate sentiment APIs, and it needs a bounded retention
  policy.
- Popularity can reinforce early winners or dominant viewpoints, so dashboards and tests must
  expose concentration and recency behaviour before release.
- A later For You proposal requires its own ADR, diversity safeguards and an offline/controlled
  comparison against the category-first baseline.
