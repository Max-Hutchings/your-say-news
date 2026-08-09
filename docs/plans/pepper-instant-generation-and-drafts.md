# Pepper — instant generation, SSE progress, recoverable drafts

Replaces the polled job queue for post creation with immediate generation streamed over SSE, and
makes the human's in-progress draft survive a page refresh.

Supersedes the worker/queue parts of `stage7-unbiased-post-agent.md`. Everything else in Stage 7
(source verification, image policy, publish contract) still stands.

## Situation

`POST /agent/jobs` writes a `PENDING` row and returns `202`. `AgentJobWorker` polls every 2s,
claims the job, calls Grok, and writes `DRAFT_READY`. The client then polls.

Three problems:

- **The queue is the wrong shape.** It was copied from Unwrapped, where generation is
  milestone-triggered and nobody is watching. Post creation is the opposite — an official publisher
  pressed a button and is staring at the screen. Concurrency here is one.
- **No progress feedback.** A polled job row can only say `RESEARCHING` for 30-120s.
- **Retry semantics are useless to a waiting human.** `AgentJobProcessor.fail` backs off 1min then
  2min. Nobody is still on the page.

Not a problem: the 2s poll interval. The call itself is a grok-4.5 reasoning + web-search request
(`quarkus.langchain4j.openai.grok.timeout=300s`). Realistic latency is 30-120s. No architecture
makes that instant — the goal is **instant to start, visible throughout, never lost**.

## Decisions

### 1. Generation starts on submission, not on a poll

`AgentServiceImpl.start` commits the `PENDING` row, then dispatches generation on a virtual thread
before returning. No waiting to be claimed.

The `@Scheduled` task stays but changes job: it drops to a **60s reaper** that only reclaims jobs
stuck in `RESEARCHING` past a timeout (server restarted mid-call). It is the recovery path, not the
normal path.

Retry stays for provider 429/5xx, but immediate (bounded, in-process) rather than backed off to
minutes — a retry a waiting human will actually see.

### 2. Progress over SSE

`GET /agent/jobs/{jobId}/events` → `text/event-stream`, one-way, ownership-checked like the
existing `GET`.

Events carry `{ status, phase, draft?, error? }`:

| phase | emitted when |
|---|---|
| `RESEARCHING` | generation dispatched |
| `DRAFTING` | provider returned, validation starting |
| `VERIFYING_SOURCES` | `AgentDraftValidator` running |
| `DRAFT_READY` | terminal — carries the draft |
| `FAILED` | terminal — carries safe error code/message |

Implementation notes:

- A `jobId`-keyed broadcaster in `postagent.service`. The endpoint returns `Multi<AgentJobEvent>`
  with `@RestStreamElementType(MediaType.APPLICATION_JSON)`.
- **On subscribe, replay current state immediately.** A client that connects late — or reconnects
  after a refresh — must not see a blank stream. If the job is already terminal, emit the terminal
  event and complete the stream.
- Reconnect contract for the frontend: `GET /agent/jobs/{jobId}` for authoritative state first,
  then subscribe. SSE is a progress channel, never the source of truth.
- This is the one place we use `Multi` rather than imperative virtual threads — streaming is
  inherently push-based. Everything behind it stays imperative.

### 3. `pepper_ai_draft_post` — the editable draft, separate from the job

`agent_generation_job.draft` already persists provider output, so why a second table:

- **`agent_generation_job.draft` is immutable evidence** — exactly what the model returned, the
  output that passed source verification against Grok's citations. Editing it in place destroys the
  audit trail that made the unbiased badge trustworthy.
- **`pepper_ai_draft_post` is the human's mutable working copy** — what they've typed so far. This
  is what must survive a refresh, a closed laptop, or a switch to another device.

Table (`0015-create-pepper-ai-draft-post.xml`; singular name to match `agent_generation_job` /
`post`):

- `id` uuid PK
- `job_id` uuid FK → `agent_generation_job`, **unique** (one working draft per generation)
- `user_id` bigint — ownership checked independently of the job
- `content` jsonb — the edited `AgentDraftDto` shape
- `status` varchar — `EDITING` / `PUBLISHED`
- `published_post_id` bigint nullable
- `version` integer — optimistic locking, so two open tabs can't silently clobber each other
- `created_at`, `updated_at`
- index on `(user_id, updated_at desc)` for "resume where I left off"

Endpoints:

- `PUT /agent/jobs/{jobId}/draft` — autosave from the review screen, returns the new `version`;
  rejects a stale `version` with `409`.
- `GET /agent/jobs/{jobId}/draft` — the working copy, seeded from the job's draft on first read.

Source URLs stay server-owned: a human can edit claim *text*, but cannot invent a source that never
appeared in the provider citations. Re-run the URL half of `AgentDraftValidator` on save.

### 4. Publish needs a new `PostService` method

`PostService.create` forces `isUnbiased=false` by contract and returns `Uni<PostDto>`. The agent
cannot reuse it.

Add an imperative method to the public posts contract — `PostDto publishAgentPost(AgentPublishCommand)`
— taking derived post fields, the resolved author, media references and the verified source list.
Imperative per the project default; the existing `Uni` methods are legacy, and this plan does not
convert them.

Also adds `post_source` (Stage 7's table, still unbuilt): post FK, section
(`SUMMARY`/`CASE_FOR`/`CASE_AGAINST`), claim + source order, URL/title/publisher, uniqueness
preventing duplicate mappings.

Publish is idempotent — a repeat call returns the same post, keyed on
`pepper_ai_draft_post.published_post_id`.

### 5. Fix the package/path split first

Files under `posts/postagent/` currently declare three different packages: `AgentController.java`
says `com.yoursay.agents.postagent`, `AgentService.java` says `com.yoursay.posts.postagent`, and the
tests import the latter while living in the former. Directory paths match none of them.

Do the move to `com.yoursay.agents.postagent` (per CLAUDE.md) with paths aligned, before adding
files — otherwise this plan doubles the mess.

## Delivery order

1. **ADR-043** — queue → immediate generation + SSE, and why the draft is a separate table.
2. Package/path alignment to `com.yoursay.agents.postagent`, no behaviour change.
3. Immediate dispatch + reaper; delete the 2s claim loop.
4. SSE endpoint, broadcaster, replay-on-subscribe.
5. `pepper_ai_draft_post` + autosave/read endpoints with optimistic locking.
6. `publishAgentPost` on `PostService`, `post_source`, idempotent publish.
7. Review screen: live phases, autosave, resume-after-refresh, image upload, publish.

## Tests

**Unit**

- Dispatch marks `RESEARCHING` before any provider call.
- Reaper reclaims a stale `RESEARCHING` job and leaves a fresh one alone.
- Broadcaster replays terminal state to a subscriber that arrives after completion.
- Draft save rejects a stale `version` and rejects a source URL absent from the job's citations.
- Publish twice returns the same post id and creates one post.

**Integration**

- Submit → SSE stream emits `RESEARCHING` … `DRAFT_READY` with the draft, against a fake generator.
- A second user cannot subscribe to, read, save or publish another user's job.
- Refresh mid-generation: re-`GET` then re-subscribe yields correct state with no lost work.
- Publish creates exactly one `isUnbiased=true` post with its sources; normal create still forces
  `isUnbiased=false`.

No test calls the live xAI API.
