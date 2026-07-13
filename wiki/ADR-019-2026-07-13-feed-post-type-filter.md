# ADR-019 — Feed post type filter

## Situation

Readers need to switch between video posts and article-style posts without losing the ranked,
paginated feed. Article-style posts include both text-only stories and stories illustrated with
images.

## Options considered

1. Filter only the posts already loaded by the mobile app.
2. Add a persisted post-type field and migrate existing posts.
3. Derive the type from media and filter the ranked candidate set before pagination.

## Decision

The feed accepts an optional `type` query parameter with `VIDEO` and `ARTICLE` values. A post is a
video when any of its media items is a video. Every other post—including text-only and image-based
posts—is an article. The feed applies this filter after ranking and before page slicing. When the
parameter is absent, the feed remains unfiltered.

## Reason

The distinction already exists in post media, so another persisted field would duplicate state and
could drift out of sync. Server-side filtering before pagination guarantees full pages of the chosen
type, unlike filtering only the current mobile page.

## Consequences and follow-up work

- The mobile feed can toggle Video or Article while retaining the existing unfiltered view when
  neither is selected.
- Image posts intentionally remain in Article.
- Future post formats must decide whether they belong to Article or require another feed type.
