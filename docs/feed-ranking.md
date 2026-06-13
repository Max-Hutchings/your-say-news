# Feed Ranking — the swappable ranker contract

**Status:** Contract locked (Stage 0) · **Lives in:** `post-service`, `feed` domain

The recommendation engine is post-MVP1. So MVP1 ships a deliberately simple feed **behind an
interface**, so the real ranker drops in later without touching feed assembly or any caller.

## The shape

Public types at the top of `com.yoursay.feed`; the implementation is internal to `feed/service`.

| Type | Role |
| --- | --- |
| `FeedRanker` | Public interface: `List<Long> rank(FeedContext, List<RankablePost>)` — orders candidate posts, returns their ids best-first. **Pure and synchronous** — it ranks candidates handed to it and does no I/O. |
| `RankablePost` | Minimal view a ranker needs: `postId`, `authorId` (for follow-boost), `postedAt` (for recency). |
| `FeedContext` | Per-request signals: `userId`, `followedAuthorIds`. New ranking signals (votes, interests, recency windows) are added here without changing the method shape. |
| `ChronologicalFollowBoostRanker` | MVP1 implementation (internal): followed authors boosted above the rest, newest-first within each group. |

## Why this altitude

- **The ranker is a pure ordering function over candidates**, not a data-fetcher. Gathering
  candidates and the follow set — the async **feed-assembly** layer — is a separate concern that
  lands in Stage 5. Keeping them apart means the real rec engine only has to implement `FeedRanker`;
  it never has to know how candidates were fetched.
- **Follow-boost reads `FeedContext.followedAuthorIds`**, which Stage 5 populates from the
  `user-service` `social` domain via rest-client. The ranker itself stays decoupled from `social`.

## Stage status

- **Done (Stage 0):** the `FeedRanker` interface, the value types, and
  `ChronologicalFollowBoostRanker`, with ordering pinned by `FeedRankerTest` (follow-boost, pure
  reverse-chronological with no follows, null-timestamp handling).
- **Stage 5:** feed assembly (fetch candidates from `posts`, follow set from `social`), the
  `GET /feed` endpoint, and infinite-scroll/pull-to-refresh UI. Swapping in a smarter ranker later is
  a new `FeedRanker` implementation and nothing else.
