# ADR-011: Immersive Full-Screen Post Feed

Date: 2026-07-02

## Situation

The first feed was an editorial front page: a lead card over a "More Stories Today" list of compact
cards, each tapping through to a detail screen to read the full story. That splits a single story
across two screens and buries the summary. We want a seamless, minimal-tap experience for mobile
readers where the whole story — a 2-3 paragraph summary included — is shown up front, one post at a
time, and where media (multiple images, or a video) is front and centre.

## Options Considered

1. Keep the lead/compact list and detail screen, just enlarge the cards.
   Familiar, but keeps the two-screen split and the tap-through, and never shows the full story in the
   feed.

2. One long vertically scrolling column of full stories.
   Shows everything, but posts run into each other with no clear "one story at a time" rhythm and no
   natural place to autoplay a video.

3. A vertically paged, one-post-per-screen feed (TikTok-style), with the whole story shown in place.
   Each post fills the screen; swiping up snaps to the next. The summary scrolls within the card with a
   visible scrollbar; there is no detail screen.

## Decision

Adopt option 3.

- **One post per screen.** The feed is a vertically paged `FlatList` (`pagingEnabled`, item height =
  viewport height); swiping up snaps to the next post. The "More Stories Today" section and the
  post-detail route/screen are removed — there is no tapping into a post.
- **Full story in place.** The `summary` column is widened to `TEXT` (migration
  `0004-widen-post-summary.xml`) and now holds 2-3 paragraphs. It scrolls within a bounded region of
  the card with a persistent custom scrollbar on the right so it's clear there's more to read.
- **Media.** A post carries **either up to five images** (a horizontally swipeable carousel with dot
  indicators) **or a single video** (via `expo-video`) that autoplays, muted and looping, as soon as
  its post is the on-screen one and pauses when it scrolls away. The active post is tracked through the
  list's viewability callback.

## Reason

Showing the whole story on one screen removes a tap and the context-switch of a detail page, which is
the seamless experience we want on mobile. Paging gives a clear one-story-at-a-time rhythm and a
natural anchor for "the on-screen post" so exactly one video plays at a time. The five-image ceiling
matches what the `post_media` table already supports and is cheap to enforce; a visible scrollbar is
the honest affordance for a summary that no longer has a detail screen to expand into.

## Consequences

- Post-service `summary` becomes `TEXT`; `CreatePostRequest.summary` max rises to 4000 and the composer
  cap to 2000 characters. `create()` now validates at most 5 images and at most 1 video per post.
- Seed data (`0001-seed-posts.xml`) is rewritten with five stories, multi-paragraph summaries, and
  varied media (a 5-image carousel, a video, a 3-image set, a text-only post, a single image).
  `localstack/init-aws.sh` fetches placeholder portrait images and a short clip so the demo feed shows
  real media; it degrades gracefully to empty objects when offline.
- The composer supports multi-image selection (up to 5) with per-item removal, mutually exclusive with
  a single video.
- `expo-video` is added as a native dependency (config plugin in `app.json`), so this needs a dev-client
  rebuild, not Expo Go.
- The post-detail route (`app/(protected)/post/[id].tsx`), `PostDetail`/`PostDetailScreen`, and the
  `More Stories Today` layout are removed. `PostCard` is now a single full-screen variant.
- Editing/adding migrations means the Liquibase images must be rebuilt for `docker compose` (the app's
  migrate-at-start picks them up in dev automatically).
- Voting from the card is still not wired; Agree/Disagree are shown in place for the coming vote flow.
