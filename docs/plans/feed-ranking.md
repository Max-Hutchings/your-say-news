# Feed Ranking — the swappable ranker contract

**Status:** Contract locked (Stage 0) · **Lives in:** `post-service`, `feed` domain

The behavioural “For You” recommendation engine is post-MVP1. MVP1 keeps two deliberately simple
discovery modes **behind interfaces**: a chronological Following/Latest feed and category feeds
ranked within one canonical topic. A later recommender can replace or compose rankers without
changing feed assembly or callers.

## The shape

Public types at the top of `com.yoursay.feed`; the implementation is internal to `feed/service`.

| Type | Role |
| --- | --- |
| `FeedRanker` | Public interface: `List<Long> rank(FeedContext, List<RankablePost>)` — orders candidate posts, returns their ids best-first. **Pure and synchronous** — it ranks candidates handed to it and does no I/O. |
| `RankablePost` | Minimal view a ranker needs: `postId`, `authorId`, `postedAt`; category ranking later adds canonical topics and a bounded engagement/popularity value. |
| `FeedContext` | Per-request signals: `userId`, `followedAuthorIds`; category ranking later adds the selected topic and previously impressed post IDs. New signals are added without changing the method shape. |
| `ChronologicalFollowBoostRanker` | MVP1 implementation (internal): followed authors boosted above the rest, newest-first within each group. |
| `CategoryPopularityRanker` | Stage 6 implementation: ranks only candidates in the selected canonical topic by topic-relative popularity tempered by recency, then strongly penalises posts already impressed to the viewer. |

## Why this altitude

- **The ranker is a pure ordering function over candidates**, not a data-fetcher. Gathering
  candidates and the follow set — the async **feed-assembly** layer — is a separate concern that
  lands in Stage 5. Keeping them apart means the real rec engine only has to implement `FeedRanker`;
  it never has to know how candidates were fetched.
- **Follow-boost reads `FeedContext.followedAuthorIds`**, which Stage 5 populates from the
  `user-service` `social` domain via rest-client. The ranker itself stays decoupled from `social`.
- **Category popularity is local to the selected topic**, rather than a global post score. The
  scoring window and recency decay prevent historically popular posts from owning a category
  forever; deterministic tie-breaking and a stable cursor prevent pagination churn.
- **An impression is private feed state, not a vote or public activity.** Feed assembly records it
  only after the client actually displays the post. Seen candidates are omitted when unseen supply
  is available and receive a deliberately large penalty when a small category must recycle them.

## Stage status

- **Done (Stage 0):** the `FeedRanker` interface, the value types, and
  `ChronologicalFollowBoostRanker`, with ordering pinned by `FeedRankerTest` (follow-boost, pure
  reverse-chronological with no follows, null-timestamp handling).
- **Stage 5:** feed assembly (fetch candidates from `posts`, follow set from `social`), the
  Following/Latest endpoint, and infinite-scroll/pull-to-refresh UI.
- **Stage 6:** category feed assembly, topic-relative popularity/recency scoring, private impression
  history and repeat suppression. Exact score constants and impression-retention bounds are pinned
  by the Stage 6 contract and tests before implementation.
- **Post-MVP1:** a cross-category behavioural “For You” ranker and page, only after a later ADR
  defines its signals, diversity protections and evaluation against the category-feed baseline.
