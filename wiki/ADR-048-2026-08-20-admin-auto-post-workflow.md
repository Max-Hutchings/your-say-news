# ADR-048 - Admin-managed auto-post workflow

Supersedes [ADR-027](ADR-027-2026-07-24-server-ysn-agent-publishing.md). The planned `ysnagent`
was never implemented.

## Situation

Administrators need a manual workflow for finding the most important current stories and turning
one into an official Your Say News post. The workflow must show ten non-duplicate UK, US and global
stories from the previous 24 hours, let an administrator inspect and select one, then delegate
drafting to the existing `postagent`. The administrator must approve the returned draft before it
is published.

The earlier `ysnagent` decision selected one story and published autonomously through a trigger-only
API. That no longer matches the product requirement. Discovery candidates also need durable audit
data rather than existing only in a model response or browser session.

## Options considered

1. Implement the autonomous `agents.ysnagent` design from ADR-027.
2. Add current-story discovery and administrator workflow responsibilities to `postagent`.
3. Add a top-level `autopost` domain that orchestrates discovery, review and the `postagent`
   handoff inside the existing `post-service` deployable.
4. Deploy auto-posting as a separate service.

For progress updates:

1. Poll the run endpoint from the admin UI.
2. Use an authenticated Server-Sent Events stream backed by durable run state.
3. Use a bidirectional WebSocket.

## Decision

Add `com.yoursay.autopost` as a top-level domain inside `post-service`. Its public controller,
service contract and DTOs sit at the domain root and in `dto`; its research agent, persistence,
validation and worker implementations remain internal subpackages. This replaces the unimplemented
`agents.ysnagent` design.

The admin UI gains a **Your Say official posts** tab. Its **Create new** action:

1. creates a durable auto-post run for the exact 24 hours ending when the request is accepted;
2. opens an authenticated SSE stream for that run;
3. asynchronously asks the auto-post research agent for ten ranked stories; and
4. displays the persisted candidates when discovery completes.

Each candidate has one primary region, `UK`, `US` or `GLOBAL`, plus a rank, neutral headline,
short summary, stable within-run deduplication key and one or more sources. Exactly ten candidates
must be returned, every region must be represented, ranks must be unique from 1 to 10, and duplicate
underlying events are rejected. Provider source URLs are retained and validated against the live
web-search citations before the candidate set becomes reviewable.

Persist three related records:

- `auto_post_run` stores the search window, lifecycle, model and prompt version, triggering admin,
  selected candidate, post-agent handoff, final post reference and bounded failure details.
- `auto_post_candidate` stores the ranked headline, summary, primary region and deduplication key.
- `auto_post_candidate_source` stores ordered source provenance for each candidate.

Only an active application administrator can use the auto-post API. The SSE endpoint uses an
authenticated `fetch` stream so the existing bearer token is sent in the `Authorization` header;
tokens are never placed in URLs. Stream events are derived from durable state so reconnecting or
landing on another replica does not lose the workflow.

Selecting a candidate does not immediately draft or publish. The UI shows a confirmation step.
Confirmation atomically marks that candidate as selected and sends a bounded brief, including its
headline, summary, region and sources, to the public `postagent` contract. `autopost` does not use
`postagent` repositories, generators or HTTP endpoints and does not implement drafting.

The returned publication-ready draft is shown in the admin UI. A second, explicit approval action
is required before publication. Publication is attributed to a fixed application-owned account
with display name **Your Say News** and handle `yoursay`. The account is seeded as active,
`OFFICIAL` and an `ACTIVE` publisher. It has no interactive Keycloak login. The triggering admin is
retained only in private audit data and is never shown as the post author.

Runs and handoffs are idempotent. One candidate can be selected per run, one post-agent draft can
be created for that selection, and one final post can be linked to the run. Invalid discovery
output, unavailable providers, stale or conflicting selections, missing official-account authority,
invalid drafts and publication failures fail closed.

Use the shared operation telemetry with `domain="autopost"`. Measure the public API, discovery job,
provider dependency, candidate validation, SSE lifetime, post-agent handoff and publication using
exactly one terminal `success`, `error` or `fault` outcome per undertaken operation. Logs contain
stable error or fault codes and trace correlation, but no tokens, email addresses, source text,
headlines, summaries, user IDs, run IDs, candidate IDs or post IDs as metric labels.

## Reason

`autopost` is an editorial workflow, not another author persona. A top-level domain makes its
durable workflow and administrator-facing language explicit while leaving balanced post drafting
inside `postagent`. Keeping both domains in one deployable allows a narrow in-process contract and
avoids network and authentication complexity between code that shares the same runtime.

Persisting the complete candidate set makes the selection explainable and lets an administrator
recover after navigation, browser refresh or SSE reconnection. SSE provides timely one-way progress
without the additional protocol and state required by WebSockets.

The fixed account makes public authorship accurate. Separating the selection confirmation from final
draft approval prevents a single click from publishing model output.

## Consequences and follow-up work

- ADR-027 and the planned `ysnagent` package are retired; no migration of runtime data is needed.
- `com.yoursay.posts.postagent` maintains a public trusted handoff contract for the fixed official
  publisher. Its internal drafting implementation remains outside `autopost`.
- The posts domain must retain canonical validation and idempotent publication; `autopost` must not
  bypass those rules.
- The official account seed is application data for local/test environments. Production
  provisioning must create the same immutable account classification before auto-post is enabled.
- The first release is text-only unless an administrator adds rights-safe media through an existing
  reviewed workflow. Discovery sources are evidence, not permission to reuse media.
- Recurring scheduling is deliberately deferred. A future scheduler should create the same durable
  run through the public service contract, use a service trigger identity, preserve the 24-hour
  window and keep all review and publication rules unchanged.
- Auto-post traffic, latency and outcome panels are included in the posts-domain Grafana dashboard.
  Alerts for discovery/provider faults, invalid candidate sets, stalled runs, handoff faults and
  publication faults remain operational follow-up work.
