# ADR-048 - Direct Pepper draft streaming

Date: 2026-08-20

## Situation

Pepper post generation used a durable queue that returned a job ID and required polling. A person
creating a post is waiting in the compose screen, needs immediate progress, and must recover their
editable draft after an app refresh, replica failure or app restart.

The product also needs truthful AI provenance. The old `isUnbiased` flag described a quality claim
that the system cannot guarantee. The fact the system can prove is whether Pepper generated the
post.

The feature therefore needs one decision covering generation, recovery, editing, persistence,
citations, publication, provenance, failure handling and operations.

## Options considered

### Generation and progress

1. Keep the durable job queue and add SSE over queued jobs.
2. Generate synchronously but buffer the response until the complete draft is ready.
3. Persist first, generate immediately on the serving replica, and stream progress over SSE.
4. Add shared event infrastructure or a separate generation service before shipping the feature.

### Recovery

1. Make live streams portable between replicas using shared state.
2. Keep the live stream replica-local and route reconnects using a stored replica ID.
3. Do not reconnect live streams; recover only completed drafts from the database.

### Draft data

1. Store only the latest editable content.
2. Store the original prompt, immutable generated result and a separate editable copy.
3. Create a normal post immediately and use post editing as the draft system.

### Citations

1. Require every generated citation to remain attached.
2. Allow citations to be freely added, removed or altered.
3. Allow removal, but validate every retained citation against Pepper's generated sources.

### Publication and provenance

1. Give Pepper drafts a separate approval and publishing workflow.
2. Publish through normal post creation and derive AI provenance from the owned Pepper draft.
3. Accept a client-supplied AI boolean or retain the `isUnbiased` claim.

## Decision

### 1. Generate directly and stream one request

Choose generation option 3. `POST /agent/drafts` authenticates the user, persists the prompt and
draft attempt, starts generation immediately on the serving replica, and holds that SSE response
open until completion. There is no generation queue, polling loop, worker or automatic retry.

Events are ordered as:

1. `RECEIVED`, including the persisted draft ID and serving replica ID.
2. `GENERATING`.
3. One terminal `FINISHED` or `FAILED` event.

`FINISHED` contains the complete generated result. A server or provider fault produces `FAILED`
and exposes only this stable user message:

`Pepper AI is having trouble, please try again later.`

The old `agent_generation_job` table may remain temporarily for migration safety and history, but
the Pepper runtime does not read from or write to it.

### 2. Use replica-affine live recovery and database fallback

Choose recovery option 2, with option 3 as the fallback. The mobile app stores the active draft ID
and replica ID from `RECEIVED`. After refresh it reconnects to
`GET /agent/drafts/{draftId}/events` with both values. The load balancer must route that request to
the original replica, and the server accepts the reconnect only when that replica is alive and
still owns the stream.

Live generation is not moved to another replica. If the stream cannot be recovered, the
authenticated user can call `GET /agent/drafts/latest` to retrieve their newest unpublished draft
from Postgres. Opening Pepper performs this lookup and restores the result. Draft lookup,
reconnection, editing and publication are owner-scoped.

### 3. Persist an audit copy and an editable copy

Choose draft-data option 2. `pepper_ai_draft_post` stores:

- the owner, initial prompt and serving replica;
- generation status and a `success` outcome;
- immutable generated content and a separate editable working copy;
- safe failure details and bounded provider metadata;
- an optimistic-lock version, timestamps and the published post link.

The prompt is persisted before the provider call. A failed attempt remains stored with its prompt,
terminal status and `success=false`. A successful result sets `success=true`. Editing changes only
the working copy, never the generated audit copy.

`PUT /agent/drafts/{draftId}` autosaves the editable copy using optimistic version checking. The
mobile client serialises and coalesces overlapping saves so a late response cannot overwrite a
newer local edit.

### 4. Make Pepper compose comfortable but keep normal post editing

Focusing the Pepper prompt expands the text area to occupy a much larger part of the screen.
Blurring it returns the field to its original compact size. The generated result fills the normal
post fields, including voting type and options, and every field remains editable before
publication.

### 5. Allow citation removal without allowing citation invention

Choose citation option 3. Generated citations begin in the editable draft and can be removed.
This lets an editor remove a citation when they substantially change or delete the text it
supported. A client cannot add a source or alter the URL, title or publisher of a generated source;
publication validates the retained records as an exact subset of the immutable result.

Selected citations are copied to ordered rows in `post_source`. Published citations render after
the article text and supporting arguments, at the bottom of the article.

### 6. Reuse normal publication and derive provenance on the server

Choose publication option 2. Approval and publication use the existing `POST /posts` flow, with the
same validation and behaviour as a manually created post. A Pepper draft ID supplies provenance;
the server verifies that the draft is owned, successful and finished. It validates submitted
citations against the draft while accepting the normal post fields as the editor's final content.

`post.ai_draft_id` links the result to its draft and makes repeated publication idempotent. The
server sets `post.is_ai_generated=true` only from validated draft provenance. Manual clients cannot
set or spoof it. Manual posts remain `false`.

Remove `isUnbiased` from the API, application model and UI, and rename its database column to
`is_ai_generated`. The UI label states the factual provenance `AI GENERATED`; it makes no claim
about balance or quality.

### 7. Instrument the direct path without recording user content

Metrics cover bounded request traffic, generation state/outcome, provider and server fault class,
and generation latency. Logs and traces use bounded operation, outcome and fault fields. Prompts,
generated text, user IDs, draft IDs, replica IDs and source values are not metric labels or log
fields.

The posts dashboard describes direct generation and SSE activity, not queue depth or worker
throughput. Fault metrics distinguish provider faults from internal server faults while the mobile
app always receives the same safe message.

## Reason

Direct SSE gives immediate progress without queue delay or retry behaviour that does not help a
waiting editor. Persisting before generation protects the prompt and attempt across refreshes and
failures. Replica affinity is honest about the first implementation's in-memory stream boundary,
while latest-draft recovery protects completed work without introducing shared event
infrastructure.

The immutable result preserves what Pepper produced; the editable copy lets the user remain the
editor. Exact-subset citation validation permits responsible removal without letting changed text
misrepresent a source. Reusing normal post publication avoids a second approval system.

Server-derived provenance prevents clients from falsely applying an AI label. Replacing
`isUnbiased` records a verifiable fact rather than an unsupported quality claim. Bounded telemetry
makes failures diagnosable without exposing user content or creating high-cardinality metrics.

## Consequences and follow-up work

- A live reconnect depends on load-balancer routing to the original healthy replica.
- Restarting that replica ends its in-progress stream. The persisted attempt remains available,
  but generation does not automatically resume elsewhere.
- Generation has no automatic retry. Users receive the safe retry-later message and may start a
  new attempt themselves.
- The newest unpublished draft is the recovery unit. Pepper does not provide a draft-history UI.
- The generated audit copy consumes additional storage and is intentionally not changed by edits.
- Removing all citations is valid; the editor is responsible for the final article and sources.
- The normal post endpoint gains Pepper provenance validation but remains the only publication
  path.
- Cross-replica live-stream recovery would require shared event infrastructure and a new ADR.
