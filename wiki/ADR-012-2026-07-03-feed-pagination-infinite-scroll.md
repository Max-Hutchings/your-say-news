# ADR-012: Feed Pagination (Infinite Scroll)

Date: 2026-07-03

## Situation

The immersive feed (ADR-011) fetched recent posts in one call — `GET /posts` returned up to 50
posts, all of them loaded and rendered up front. Each post is a full-screen card carrying a
multi-paragraph summary and media (a five-image carousel or an autoplaying video), so eagerly
loading fifty of them means fifty sets of presigned media URLs and fifty heavy cards for a reader
who may only ever swipe past three. It wastes bandwidth and slows first paint, and it doesn't scale
as the post count grows.

## Options Considered

1. Keep the single 50-post fetch. Simple, but loads far more than a reader sees and has a hard
   ceiling that silently hides older posts.
2. Classic "load more" button at the end of the feed. Explicit, but a tap breaks the swipe-through
   rhythm of a TikTok-style feed.
3. Page the feed and load the next page automatically as the reader nears the end (infinite scroll).
   The reader never sees a boundary; the feed just keeps going.

## Decision

Adopt option 3 — small pages loaded ahead of the reader.

- **Page size 5.** The feed loads the first 5 posts, then requests the next 5 as the reader reaches
  the **second-to-last** loaded post, appending each page. Loading one page ahead means the next
  posts are usually in hand before the reader arrives.
- **API contract.** `GET /posts?page={n}&size={m}` returns page `n` (0-based) of `m` posts, newest
  first. `size` defaults to 5 and is **capped server-side at 50** so a client can't pull the whole
  table in one call. A returned page shorter than `size` means the end of the feed.
- **DB paging is correct, not in-memory.** A collection fetch-join (`left join fetch p.media`) can't
  be paged in SQL — Hibernate would fetch every joined row and page in memory. `PostRepository.getRecent`
  therefore pages the post **ids** first (a plain, indexed `order by createdAt desc, id desc` query,
  no join), then fetches just that page's posts with their media. Ordering by `(createdAt, id)` keeps
  the sort stable so pages don't overlap or drop a post at the boundary.
- **Client.** `HomeFeed`'s `FlatList` drives loading via `onEndReached` with
  `onEndReachedThreshold={1}` — since items are one viewport tall, one viewport-length of threshold
  fires exactly as the second-to-last post comes on screen. A ref guards against overlapping loads,
  a short page sets an end-of-feed flag that stops further requests, and pull-to-refresh resets to
  page 0. Appended pages are de-duplicated by id so a post added between fetches can't produce a
  duplicate key.

## Reason

Loading a small page ahead of the reader gives the data we actually need, when we need it, while
keeping the seamless swipe of the immersive feed — no button, no visible boundary. Paging ids before
fetching media keeps paging correct and cheap at the database rather than loading everything and
slicing in memory. The server-side size cap keeps the endpoint safe regardless of what a client asks
for.

## Consequences

- `PostService.getRecent()` becomes `getRecent(int page, int size)` through the controller, service
  and repository; the old `RECENT_LIMIT = 50` constant is replaced by `DEFAULT_PAGE_SIZE = 5` and
  `MAX_PAGE_SIZE = 50`.
- `getRecent` now issues two queries per page (ids, then posts-with-media) instead of one. For the
  small pages the feed uses this is a good trade for correct DB-level paging.
- Frontend `getRecent(page, size)` sends `page`/`size` query params; `HomeFeed` tracks a paging
  cursor and an end-of-feed flag and shows a footer spinner while a page loads.
- Any future ranked/personalised feed will need a stable cursor (not just offset paging) to avoid
  skips when new posts arrive mid-scroll; offset paging is acceptable for this interim newest-first
  feed.
