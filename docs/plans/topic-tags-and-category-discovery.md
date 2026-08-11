# Topic tags and category discovery

Concise delivery plan for governed topic tags and category feeds.

Status: steps 1 and 2 are implemented on `feature/topic-tags`. Steps 3 to 7 remain separate slices.

This plan uses the terminology decided in
[ADR-020](../../wiki/ADR-020-2026-07-13-controlled-canonical-topic-taxonomy.md). It replaces the
older `topic` and `post_topic` naming in earlier topic plans where they conflict. Catalogue size and
admin management remain governed by
[ADR-043](../../wiki/ADR-043-2026-08-09-admin-managed-topic-catalogue.md).

## Goal

Use one governed topic tag catalogue across posts, classification, private user interests and
category feeds. Topic tags appear as clean chips without `#` and are not free-form creator text.

## Decisions

- Creators select up to three active topic tags from the catalogue.
- The classifier and admins assign tags from the same catalogue.
- Assignment history keeps source, confidence, classifier version and review state.
- Public reads and feeds use a separate effective tag projection.
- Users may privately select up to seven tag interests.
- Future entities get explicit tag relationship tables rather than a generic entity reference.

## Delivery plan

### 1. Establish the topic tag model

- Add and seed `topic_tag`.
- Add `post_topic_tag_assignment` for creator, classifier and admin provenance.
- Add indexed `effective_post_topic_tag` for post chips and feed searches.
- Add database constraints for valid tag IDs and source-specific metadata.

### 2. Accept creator selections

- Add up to three topic tag IDs to post creation.
- Reject unknown, retired or excess selections.
- Store them as `CREATOR` assignments.
- Return effective topic tag chips in post responses.

### 3. Add automatic classification

- Add a versioned classifier interface.
- Persist durable jobs idempotently by post content and classifier version.
- Record inferred assignments with confidence, version and review state.
- Support retries and explicit reclassification without overwriting creator or admin history.

### 4. Add private interests

- Add `user_topic_tag_interest`.
- Allow the authenticated user to replace zero to seven selections from onboarding or settings.
- Keep interests out of public profiles, vote data and characteristic reporting.

### 5. Build category feeds

- Select candidates through `effective_post_topic_tag`.
- Rank popularity relative to the selected tag with recency decay.
- Use deterministic ties and an opaque stable cursor.

### 6. Suppress seen posts

- Store private impressions only after a post is displayed.
- Exclude seen posts while unseen content remains.
- Reintroduce seen posts with a strong penalty after the category is exhausted.
- Apply a bounded retention policy.

### 7. Add correction tools and metrics

- Let admins accept, reject, add, remove and reclassify assignments.
- Audit corrections and make admin state override later classifier runs.
- Measure tag use, job health, feed quality and impression outcomes.
- Never log identity, post text or individual interest sets.

## First implementation slice

Build steps 1 and 2 first. This gives posts governed creator-selected tags and creates the indexed
relationship needed by later classification, interests and category feeds.

Follow TDD for each slice: write focused unit and integration tests first, implement the complete
slice, then run the test audit before the relevant suites.
