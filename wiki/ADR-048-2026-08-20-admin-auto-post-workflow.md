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
2. Use an authenticated Server-Sent Events stream backed by durable run state, with polling as a
   recovery path when a browser cannot establish or retain the stream.
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
must be requested, every region must be represented, ranks must be unique from 1 to 10, and duplicate
underlying events must be removed. These editorial requirements are owned by the agent prompt.
Application validation rejects missing or blank required fields and output that does not contain
ten stories. It does not duplicate editorial judgement such as importance or neutrality in Java.

Each LangChain4j agent keeps its system prompt and output requirements in separate Markdown
resources under `prompts`. The auto-post agent uses the same direct typed AI-service structure as
the working Unwrapped agent, with raw response metadata captured separately from the structured
draft. Provider output represents `publishedAt` as an explicitly described ISO-8601 UTC string,
then maps it to `Instant` after deserialisation. This keeps the generated JSON schema scalar and
prevents providers from returning a timestamp object that LangChain4j cannot parse.

Discovery output contains only the ten populated stories and cannot declare its own operational
success or failure. The application owns that decision and rejects missing or invalid output. The
adapter also checks raw Responses API metadata for a completed `web_search_call`. This prevents
provider failure text or search placeholders from being persisted as selectable news.

Auto-post does not force low reasoning effort. Live verification showed that Grok returned
structured placeholders without calling its required server-side search tool at low effort, while
the same model, prompt and schema completed live searches when using the provider default. This
matches the working Unwrapped agent configuration.

The public service implementation only coordinates the domain contract. Focused internal services
own access policy, candidate selection and draft synchronisation, publication, SSE delivery, DTO
assembly, discovery execution and publication-request mapping. Complex workflow methods read as a
summary of these named operations rather than mixing their implementation details.

Persist three related records:

- `auto_post_run` stores the search window, lifecycle, model and prompt version, triggering admin,
  selected candidate, post-agent handoff, final post reference and bounded failure details.
- `auto_post_candidate` stores the ranked headline, summary, primary region and deduplication key.
- `auto_post_candidate_source` stores ordered source provenance for each candidate.

Only an active application administrator can use the auto-post API. The SSE endpoint uses an
authenticated `fetch` stream so the existing bearer token is sent in the `Authorization` header;
tokens are never placed in URLs. Stream events are derived from durable state so reconnecting or
landing on another replica does not lose the workflow. The admin UI also polls the durable run in
parallel, so a failed run replaces the loading state even if SSE never reaches the backend.

Selecting a candidate does not immediately draft or publish. The UI shows a confirmation step.
Confirmation atomically marks that candidate as selected and sends a bounded brief, including its
headline, summary, region and sources, to the public `postagent` contract. `autopost` does not use
`postagent` repositories, generators or HTTP endpoints and does not implement drafting.

The returned publication-ready draft is shown in the admin UI. A second, explicit approval action
is required before publication. Publication is attributed to a fixed application-owned account
with display name **Your Say News** and handle `yoursay`. The account is seeded as active,
`OFFICIAL` and an `ACTIVE` publisher. It has no interactive Keycloak login. The triggering admin is
retained only in private audit data and is never shown as the post author.

Runs and handoffs are idempotent. One candidate can be selected per run and one final post can be
linked to the run. A failed post-agent draft can be explicitly retried by an administrator. The
post-agent creates a new job from the failed job's persisted prompt, so retry uses the exact same
input without repeating discovery. Invalid discovery
output, unavailable providers, stale or conflicting selections, missing official-account authority,
invalid drafts and publication failures fail closed.

Manual discovery runs make one provider attempt. A provider or output-contract fault moves the run
directly to `FAILED`, allowing the admin UI to show the persisted failure without a hidden retry
window. A later scheduled workflow may adopt a different retry policy as an explicit operational
decision.

Only failures after a candidate has been handed to the post agent expose **Retry draft**. Discovery
failures remain non-retryable from this action. A retry returns the existing auto-post run to
`DRAFTING`, points it at the replacement post-agent job and resumes the authenticated event stream.

Use the shared operation telemetry with `domain="autopost"`. Measure the public API, discovery job,
provider dependency, candidate validation, SSE lifetime, post-agent handoff and publication using
exactly one terminal `success`, `error` or `fault` outcome per undertaken operation. Logs contain
stable error or fault codes and trace correlation, but no tokens, email addresses, source text,
headlines, summaries, user IDs, run IDs, candidate IDs or post IDs as metric labels.
As a temporary provider diagnostic exception, an `AUTO_POST_WEB_SEARCH_MISSING` fault logs the
complete xAI response body once before the run fails. This response may contain generated headlines,
summaries and source URLs, but does not contain the API credential or request headers. Remove this
diagnostic after xAI's missing required-tool behaviour has been captured and resolved.
Each SSE event payload is also measured as the low-cardinality `sseEvent` I/O operation, including
its outcome and creation latency. Discovery and publication logs also contain a bounded `stage`
field so operators can distinguish provider request, structured-output parsing, candidate
persistence, post-agent handoff, draft generation, post persistence and finalisation failures.

### Failure analysis - why searched responses failed

The failed discovery responses were not caused by an empty news index. The provider returned HTTP
`200` with finish reason `STOP`, but the raw Responses API output contained no `web_search_call`.
Provider usage also reported `num_server_side_tools_used=0` and `num_sources_used=0`. Grok then
returned either a structured `FAILED` result or placeholder story content claiming that live search
had found no qualifying news. That wording was misleading because no search had been attempted.

The specific cause was the auto-post-only
`quarkus.langchain4j.openai.autopost.chat-model.reasoning-effort=low` setting. Reproduction with the
same Grok model, current-time window, prompt, web-search tool and strict JSON schema produced:

- low reasoning effort: zero web-search calls and fabricated failure or placeholder content;
- provider-default reasoning effort: 22 completed web-search calls and exactly ten stories.

The working Unwrapped agent already used provider-default reasoning effort. Auto-post therefore
removes the low-effort override and follows the same configuration. Its provider DTO also contains
only the required stories, so the model cannot classify its own operation as failed. Operational
failure belongs to Java.

The application does not trust response wording as evidence of research. It inspects the raw
provider output and requires at least one completed `web_search_call`. A response with no completed
search fails with `AUTO_POST_WEB_SEARCH_MISSING` or `AUTO_POST_WEB_SEARCH_FAILED`; no candidates are
persisted or presented as selectable. This distinguishes a successful HTTP response from a
successful researched response.

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
