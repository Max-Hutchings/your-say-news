# ADR-042: Keyset Feed Candidate Pagination

Date: 2026-08-09

## Situation

The MVP1 feed (`GET /feed`, `com.yoursay.feed`) works, and the `FeedRanker` seam that ADR-021 and
`docs/feed-ranking.md` set up is the right shape — a pure ordering function that a real recommender
can replace later. The layer **underneath** that seam is not enterprise-standard, and four defects
compound on every single feed request:

1. **The candidate window is re-fetched whole, per page.** `FeedServiceImpl` always calls
   `postService.getRecent(0, MAX_RANKING_WINDOW)` — 250 posts — ranks all 250, then does
   `.skip(page * size).limit(size)` **in memory**. Page 6 of a 5-post feed costs exactly the same
   database work as page 0, and 245 of the 250 mapped posts are discarded.
2. **Mapping a post list is N+1.** `PostServiceImpl.mapPostsWithOptions` issues one
   `optionRepository.listByPostId(...)` per post. A 250-post window is therefore ~252 queries to
   return 5 posts to the reader.
3. **The sort key is unindexed.** Every feed and `/posts` query orders by `created_at desc, id desc`
   and no migration creates an index on it. Today the seed table is small enough to hide it; a
   sequential scan plus sort is what it becomes.
4. **The type filter runs after ranking, inside the window, so the feed ends early.**
   `FeedPostType.matches` filters the already-ranked 250 in memory. The app defaults to the VIDEO
   filter, so a reader hits "no more posts" as soon as the newest 250 posts stop containing videos —
   a **false end-of-feed**, not the real one. It is also a hard 250-post ceiling on the whole feed.

Defects 1 and 4 are both consequences of the same root cause: **offset paging over a bounded,
re-ranked, in-memory window**. Offset paging is also unstable — a post published while a reader
scrolls shifts the window and pushes a post they have not seen yet past the page boundary, where it
is silently skipped. `HomeFeed`'s de-duplication hides the resulting duplicates, not the skips.

## Options Considered

1. **Leave paging alone; fix only the N+1 and the missing index.** Cheapest, and it removes the
   worst cost. But it keeps the false end-of-feed, keeps the 250-post ceiling, and keeps every page
   re-scanning from the top of the table. It fixes the symptom that is easy to measure and leaves
   the two that readers actually feel.

2. **Materialised fan-out (`feed_entry` row per follower, written on publish).** The standard
   large-scale social feed. Reads become one indexed keyset scan. Rejected: it buys read throughput
   we have no traffic to need, and charges write amplification, a backfill migration and a new
   consistency problem for it. Premature at MVP.

3. **Keyset (cursor) pagination over the candidate source, with the type filter pushed into SQL.**
   The feed pages by an opaque cursor over the immutable `(created_at, id)` ordering instead of an
   offset; the candidate query returns exactly one page; `FeedRanker` orders that page. Fixes all
   four defects and leaves the ranker interface untouched.

A fourth possibility — keeping a global follow-boost by paging two keyset streams (followed authors,
then everyone else) and merging them — was considered while choosing between these and is addressed
under Consequences.

## Decision

Adopt option 3.

- **The feed is cursor-paged, not offset-paged.** `GET /feed?cursor={token}&size={n}&type={t}`
  returns `{ posts, nextCursor }`. The cursor is an opaque base64url token encoding the
  `(createdAt, id)` of the last post on the page; `nextCursor` is `null` when the page is the last
  one. Absent `cursor` means the first page. Clients treat the token as opaque and never construct
  one.
- **The candidate query fetches one page, from the cursor.** `PostRepository.findPageAfter` applies
  the keyset predicate `(created_at, id) < (:cursorCreatedAt, :cursorId)` under
  `order by created_at desc, id desc`, so paging never re-scans the rows already served. There is no
  `MAX_RANKING_WINDOW` any more, and therefore no post-count ceiling on the feed.
- **The post-type filter is a SQL predicate, not a post-ranking in-memory filter.** VIDEO is
  `exists (select 1 from post_media m where m.post_id = p.id and m.media_type = 'VIDEO')`; ARTICLE is
  the `not exists` of the same. A page is therefore always full when more matching posts exist, so
  the end of the feed is the real end.
- **Vote options are fetched in one batched query.** `PostVoteOptionRepository.listByPostIds`
  replaces the per-post call, and `PostServiceImpl` groups the result in memory. This applies to
  every list-returning path (`getRecent`, `getByUser`, the feed), not just the feed.
- **Add the index the ordering needs.** Migration `0014` creates
  `idx_post_created_at_id on post(created_at desc, id desc)`.
- **`FeedRanker` is unchanged.** It still receives a `FeedContext` and a candidate list and returns
  ids best-first. What changes is that the candidate list handed to it is one page, retrieved by
  cursor, rather than a 250-post window sliced in memory afterwards.

## Reason

Keyset pagination is the correct primitive for an append-ordered feed: cost is constant per page
rather than proportional to depth, and because the cursor names a row in an immutable total order,
concurrent publishing cannot make a reader skip a post. Offset paging can give neither guarantee.

Pushing the type filter into SQL is what removes the false end-of-feed, and it only becomes possible
once the candidate query pages properly — the filter and the pagination are the same fix, which is
why option 1 cannot deliver half of it.

The N+1 and the missing index are unambiguous defects with no design tension; they are corrected
here because the feed is the hot path that exposes them, and the batched fetch benefits every caller
of the post domain.

We stop short of option 2 deliberately. The `FeedRanker` seam already lets a real recommender land
without touching assembly, and a materialised feed would commit us to a write-side design before we
have the read traffic or the ranking signals to justify one.

## Consequences

- **The follow-boost becomes page-local.** `ChronologicalFollowBoostRanker` now boosts followed
  authors within the page it is given, not across the corpus. This is a deliberate behaviour change,
  and it is the one real trade in this ADR. Restoring a global boost needs the two-stream merge
  described above, which we are **not** doing, because "every post by everyone you follow, oldest
  included, before any fresh news from anyone else" is a worse feed than a chronological one — and
  it is what the 250-post window was accidentally concealing. A genuine personalised ordering is
  Stage 6 / post-MVP1 work behind the unchanged `FeedRanker` interface.
- **ADR-012 is superseded for `/feed`.** Its `page`/`size` contract and its infinite-scroll client
  logic stay valid for `GET /posts`, which keeps offset paging; `/feed` moves to cursors, and
  `HomeFeed` tracks a `nextCursor` instead of a page number. The end-of-feed signal changes from
  "a short page" to "`nextCursor` is null" — a short page is no longer meaningful, since the SQL
  filter fills pages.
- **`MAX_RANKING_WINDOW` is deleted** along with the in-memory `skip`/`limit`. `size` is still
  capped server-side at 50.
- **`FeedService.getFeed` changes shape** to take a cursor and return a `FeedPage` DTO. It is an
  internal-only contract (the mobile client is the sole consumer), so it changes in place rather
  than being versioned.
- **A malformed or truncated cursor is rejected with 400**, not silently treated as the first page,
  so a client bug surfaces instead of quietly restarting the reader's feed.
- Follow-up, not in scope here: recording impressions for repeat suppression, and the category feed
  — both already specified as Stage 6 in `docs/feed-ranking.md`.
