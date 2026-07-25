# YSN official publishing agent

Status: planned for MVP1 v2
Roadmap stage: Stage 9
Primary domain: `post-service/com.yoursay.agents.ysnagent`

## Goal

Add a server-side agent that autonomously publishes a current, sourced post as the Your Say News
official account with handle `ysn`.

An authenticated account with the Keycloak realm role `admin` starts a run through one REST
endpoint. The server then:

1. researches current top stories from credible sources;
2. selects one newsworthy topic that is not a recent duplicate;
3. hands a bounded topic brief to `postagent`;
4. receives the complete sourced post draft directly through `postagent`'s public Java contract;
5. validates every required post field, voting option and citation; and
6. publishes the post through the public `posts` contract as `ysn`.

There is no mobile or web interface in this stage. The admin caller triggers the run but is never
recorded as the post author.

## Domain boundaries

### `agents.ysnagent`

Owns:

- the admin-only trigger endpoint;
- the durable orchestration job and state machine;
- top-story discovery, candidate normalisation and topic selection;
- recent-topic and concurrent-run deduplication;
- final completeness, source and publish-readiness validation;
- publication orchestration and run telemetry.

Its public face sits at the package top level: `YsnAgentController`, `YsnAgentService` and the REST
DTOs. Research clients, prompts, persistence, workers and validators remain in internal
subpackages.

### `agents.postagent`

Owns full post research and content generation. Add a public in-process generation contract that
accepts a selected-topic brief and returns the same complete, versioned draft contract used by the
official human-reviewed workflow.

`ysnagent` must not call `postagent` internals, reuse its repositories or invoke its HTTP endpoints.
The existing human workflow remains human-reviewed; only `ysnagent` is allowed to take its returned
draft into the autonomous validation and publication path.

### `posts`

Owns canonical post validation and persistence. Add a narrowly scoped public contract for trusted
agent publication that:

- resolves the configured author handle `ysn` inside the application user domain;
- requires that account to be active with `AccountType.OFFICIAL` and
  `PublisherStatus.ACTIVE`;
- creates an `isUnbiased=true` post with its immutable voting configuration and source mappings;
- returns the published post ID; and
- is idempotent for the originating `ysnagent` job ID.

The contract is an internal Java domain boundary, not another public write endpoint. `ysnagent`
must never fabricate a bearer token, impersonate the triggering admin or reach into post
repositories.

### `user`

Provision the application-owned public account with handle `ysn` through controlled deployment
data. It does not need an interactive Keycloak login for this workflow. Publication fails closed if
the account is missing, inactive, not official or not an active publisher.

## REST API

### Trigger one publishing run

`POST /admin/ysn-agent/posts`

- requires `@RolesAllowed("admin")` server-side;
- accepts no topic or author override—the agent chooses the topic and always publishes as `ysn`;
- accepts an optional `Idempotency-Key` header;
- returns `202 Accepted` with `jobId`, initial status and creation time; and
- creates no post in the request transaction.

This is the only client-facing endpoint required for the initial release. Job state remains
observable through persisted records, structured logs, traces and metrics until a genuine
operational need justifies a status endpoint or interface.

## Orchestration

The trigger commits a `PENDING` job. A scheduled worker atomically claims a bounded batch and moves
each job through:

`PENDING → RESEARCHING → SELECTING → GENERATING → VALIDATING → PUBLISHING → PUBLISHED`

Any terminal failure becomes `FAILED` with a bounded safe error code. Provider timeouts and
retryable 429/5xx failures use bounded exponential retry. Invalid, incomplete or unsupported
content fails closed and is never published.

Only one active run is allowed by default. Repeating the same idempotency key returns the original
job, and publication is unique by job ID so retries cannot create two posts.

## Top-story research and selection

The research step uses live search behind a provider-neutral interface and stores a compact,
auditable candidate set. Each candidate contains:

- headline and concise topic description;
- canonical URL, publisher and publication time where available;
- access time and provider/model version;
- corroborating source URLs;
- freshness and selection signals; and
- rejection reason when filtered out.

Selection favours recency, public relevance, source quality, corroboration and suitability for a
clear support question. It rejects obvious duplicates of recent `ysn` posts, thinly sourced
rumours, unsafe unsupported allegations and candidates that cannot support a meaningful vote.

The selected brief sent to `postagent` includes the topic, why it matters and the discovered source
URLs. `postagent` still performs and validates its own research for every claim; discovery sources
are leads, not automatically accepted evidence.

## Publish-readiness validation

Before publication, `ysnagent` verifies:

- non-blank summary, support question, case for and case against;
- a valid voting type;
- fixed Agree/Disagree options for binary posts, or 2–5 ordered, non-blank,
  case-insensitively distinct options for multiple-choice posts;
- every factual claim has at least one source and every cited URL was returned in `postagent`'s
  collected generation citations;
- source URL, title and publisher fields are complete and deduplicated;
- the selected topic is not a recent semantic duplicate;
- content and contract versions are supported; and
- the configured `ysn` publisher is still authorised immediately before the write.

Canonical validation still runs inside `posts`; `ysnagent` validation is an additional fail-closed
editorial gate, not a replacement.

Media is optional in the current post contract. The first release publishes text-only when no
rights-safe media is available. It must not scrape or republish arbitrary web-search images.

## Persistence

Add `ysn_agent_job` with at least:

- UUID primary key and optional unique idempotency key;
- triggering admin subject for private audit only;
- status, attempt count and next-attempt time;
- compact candidate set and selected-topic brief;
- research provider/model/prompt versions;
- generated draft snapshot and validation version;
- bounded error code/message;
- published post ID;
- created, updated, started and completed timestamps; and
- uniqueness on `published_post_id` and the originating publication key.

The admin subject is operational audit data. It is never exposed in the public post or used as the
author.

## Tests

### Unit

- candidate freshness, source-quality and recent-topic filters;
- deterministic selection from fixed candidate fixtures;
- draft completeness, voting-option and source validation;
- state transitions, retry/backoff and terminal failures;
- idempotency and active-run exclusion; and
- validation rejection for unsupported or duplicate stories.

### Integration

- unauthenticated and non-admin callers receive `401`/`403`;
- an admin call receives `202` and persists exactly one job;
- repeated idempotency keys return the same job;
- a fake researcher and fake `postagent` contract produce one published post;
- the post author is `ysn`, never the triggering admin;
- missing/suspended/non-official `ysn` accounts fail closed;
- invalid drafts and unverified citations never reach `posts`;
- retry or worker concurrency cannot create duplicate posts; and
- no test calls a live model or search provider.

Run `test-audit` after writing or changing these tests.

## Delivery order

1. Record the domain, authorisation and fixed-author decision in ADR-027.
2. Provision the `ysn` official application account and public lookup contract.
3. Add the `ysnagent` public boundary, migration, trigger endpoint and durable worker.
4. Add top-story research, candidate storage, selection and recent-topic deduplication.
5. Expose the complete-draft generation contract from `postagent`.
6. Add publish-readiness validation and the idempotent trusted-agent publication contract in
   `posts`.
7. Add end-to-end tests, audit events, metrics, traces and operational failure alerts.

## Explicitly deferred

- a mobile or web interface;
- scheduled or recurring automatic runs;
- caller-supplied topics, authors or editorial instructions;
- automated use of arbitrary web images; and
- automatic retries of editorial/validation failures without a new admin trigger.
