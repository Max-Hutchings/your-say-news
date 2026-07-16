# Stage 7 — Unbiased Post agent

Implementation plan for MVP1 Stage 7. The agent is a strict DDD domain inside `post-service`, not
a separately deployed service. It uses xAI's Grok Responses API with live web search to produce a
reviewable draft whose factual claims retain their supporting URLs.

## Outcome

At the end of this stage:

- an authenticated user can submit a bounded topic/request and immediately receive a generation job;
- generation continues asynchronously and survives client disconnects;
- Grok researches the live web and returns structured summary, case-for and case-against claims;
- every generated claim has one or more source URLs that were present in Grok's search citations;
- the user reviews and may edit the draft before publishing;
- approval creates a normal post with `isUnbiased=true`, linked sources and optional human-supplied
  media; and
- feed, profile, post detail, voting and sentiment treat the result like any other post apart from
  its visible unbiased badge.

## Architecture

Add `com.yoursay.agent` as a sibling of `posts`, `votes`, `feed` and `topics`.

- The public face is `AgentController`, `AgentService` and DTOs at the package top level.
- Persistence, Grok transport, prompts and worker logic remain in internal subpackages.
- `agent` calls the public `PostService` contract to publish an approved draft. It never reaches
  into `posts.model` or `posts.service`.
- `post-service` remains the only deployable changed by Stage 7.

The provider is behind `UnbiasedPostGenerator` so prompts, Grok models or the provider itself can be
changed without changing job persistence or HTTP contracts.

## Provider choice and cost controls

Use the xAI Responses API with:

- default model `grok-4.3`, configurable by `agent.grok.model`;
- configurable reasoning effort, default `low`;
- server-side `web_search`;
- strict JSON-schema output;
- a stable prompt-cache key for the versioned system prompt;
- bounded prompt and output sizes; and
- persisted provider model/response identifiers for diagnosis.

`grok-4.3` is the value default; `grok-4.5` can be selected by configuration for quality
experiments. Model aliases are not hard-coded outside configuration.

## Draft contract

Each draft contains:

- `summaryClaims[]`;
- `caseForClaims[]`;
- `caseAgainstClaims[]`;
- `supportQuestion`;
- `sources[]` with URL, title and publisher;
- `imageBrief`; and
- `imageSearchQuery`.

Each claim is `{ text, sourceUrls[] }`. The service rejects a provider result if any claim has no
source or cites a URL absent from the provider's collected search citations. The model is also
prompted to distinguish established facts from allegations, avoid false balance, represent
material uncertainty, and use the strongest available primary/independent sources.

The existing post strings are derived from the ordered claim text at publish time. Source mappings
remain available through Stage 7 source persistence rather than being discarded.

## HTTP API

### Start generation

`POST /agent/jobs`

```json
{ "request": "Cover the proposed change to UK voting age and the strongest arguments on each side." }
```

Returns `202` with the new job in `PENDING`.

### Poll generation

`GET /agent/jobs/{jobId}`

Returns only a job owned by the authenticated user. Status is one of:

- `PENDING`;
- `RESEARCHING`;
- `DRAFT_READY`;
- `FAILED`; or
- `PUBLISHED`.

The draft is present only for `DRAFT_READY`/`PUBLISHED`. Failure responses expose a bounded safe
error code/message, never the API key, raw provider request, provider reasoning or full searched
page content.

### Publish

`POST /agent/jobs/{jobId}/publish`

Accepts the reviewed text plus optional existing post-media upload references. It validates job
ownership and state, creates `isUnbiased=true` content through `PostService`, persists source
mapping and atomically marks the job `PUBLISHED`. Repeated publish calls return the same post rather
than creating duplicates.

## Persistence

### `agent_generation_job`

- UUID primary key;
- authenticated `user_id`;
- bounded original request;
- status, attempt count and next-attempt time;
- model and provider response ID;
- structured draft JSON;
- bounded error code/message;
- published post ID;
- created, updated, started and completed timestamps; and
- indexes for `(status, next_attempt_at)` and `(user_id, created_at desc)`.

Workers claim jobs atomically, use bounded exponential retry, and stop after three attempts.
Startup/polling may reclaim stale `RESEARCHING` jobs.

### `post_source`

- post ID foreign key;
- section (`SUMMARY`, `CASE_FOR`, `CASE_AGAINST`);
- claim and source order;
- URL, title and publisher; and
- uniqueness preventing duplicate mappings.

## Image policy

Do not automatically download or publish arbitrary Grok image-search results. Image search proves
relevance, not a reusable licence, and many news photographs are commercial copyrighted works.

MVP1 uses the existing human upload path. The draft supplies an image brief/search query so the
author can:

1. upload an image they own or have licensed;
2. choose a reusable image after checking its source-page licence and attribution; or
3. later request a clearly labelled AI-generated editorial illustration.

A future assisted image picker may integrate directly with a licence-aware provider/API and must
store creator, source page, licence identifier/URL, required attribution and verification time.
It must not trust licence text invented or merely repeated by the language model.

## Worker and reliability

- The create endpoint commits a `PENDING` job before returning.
- A scheduled worker claims a small bounded batch and performs provider I/O on virtual threads.
- A failed call records a retry without holding a database transaction open during network I/O.
- Provider timeouts and 429/5xx responses retry; invalid structured output and failed source
  validation fail closed.
- Job logs contain job ID, status, model, duration and counts, but not the user's raw request or
  generated article text.

## Tests

### Unit

- prompt/schema construction;
- provider response parsing;
- every claim must have sources;
- every claim URL must be among provider citations;
- deterministic conversion from claims to post fields; and
- retry/backoff/status transitions.

### Integration

- authenticated create returns `202` and persists ownership;
- one user cannot read or publish another user's job;
- a fake generator advances a job to `DRAFT_READY`;
- provider failure advances retry/final failure correctly;
- approval creates exactly one `isUnbiased=true` post with sources; and
- existing normal post creation still forces `isUnbiased=false`.

No test calls the live xAI API.

## Delivery order

1. Lock this plan and ADR-022.
2. Add generation-job migration, public DTOs and authenticated create/status endpoints.
3. Add the generator interface, Grok client, strict schema/prompt and worker.
4. Add publish/source persistence through the public `posts` contract.
5. Add mobile conversation, progress, review/edit, media attachment and publish flow.
6. Add unbiased badges and source rendering everywhere a post appears.
7. Add telemetry dashboards, cost/latency limits and evaluation fixtures for bias/source quality.
