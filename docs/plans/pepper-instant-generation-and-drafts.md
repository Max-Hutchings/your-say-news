# Pepper - direct generation and recoverable post drafts

Pepper creates an editable post draft immediately from the mobile compose screen. This plan
supersedes the queue and polling workflow in `stage7-unbiased-post-agent.md`.

## Product contract

- `POST /agent/drafts` persists the prompt first, starts generation immediately and holds one SSE
  response open until a terminal event.
- Events are ordered `RECEIVED`, `GENERATING`, then `FINISHED` or `FAILED`. `FINISHED` includes the
  complete draft. `FAILED` includes only `Pepper AI is having trouble, please try again later.`
- The client stores the returned draft and replica IDs. After refresh it reconnects with both IDs.
  A live generation is recoverable only through the original replica.
- `GET /agent/drafts/latest` returns the current user's newest unpublished draft, including a
  terminal result. Opening Pepper restores this draft.
- `PUT /agent/drafts/{id}` autosaves editable content with optimistic version checking.
- Pepper publication uses the normal `POST /posts` path. Server-owned draft provenance sets
  `isAiGenerated=true`; clients cannot set this flag directly.

## Persistence

`pepper_ai_draft_post` stores:

- owner, initial prompt, replica, status and success;
- immutable generated content and a separate editable working copy;
- safe failure details, model metadata, version and publication link; and
- timestamps for newest-unpublished recovery.

Failed attempts remain stored with their prompt and `success=false`. `post.ai_draft_id` makes
publication idempotent. `post_source` stores the selected source URL, title, publisher and order.

## Citation rules

- Generated citations are copied into the editable draft.
- Editors may remove citations but cannot add or alter citations not present in Pepper's output.
- Publication validates the selected citation records against the saved draft.
- Published sources render after the article text and supporting arguments.

## Operations

Generation is direct and has no job worker, polling loop or automatic retry. Bounded metrics cover
SSE/API traffic, generation outcome, latency and failure code. Logs contain bounded operation and
fault fields only, never prompts, user IDs or draft IDs.

## Verification

- SSE ordering, full terminal result, persistence and safe failure.
- Ownership on latest, reconnect, save and publication.
- Replica mismatch and missing live replica failure.
- Refresh recovery, autosave and citation removal in the mobile app.
- Manual spoofing cannot create an AI-labelled post.
- Pepper publication is idempotent and persists ordered sources.
