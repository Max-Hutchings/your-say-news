# ADR-027 — Server-side YSN agent publishing

The namespace list in this ADR is partially superseded by
[ADR-032](ADR-032-2026-07-25-top-level-unwrapped-domain.md): Post Unwrapped is now a top-level
domain. The `ysnagent` decision remains active.

## Situation

Your Say News needs an official server-side publisher that can be triggered by an administrator,
research current top stories, choose a topic, ask the existing post-creation agent for a complete
post and publish it as the official `ysn` account without a client interface.

The existing `postagent` owns sourced post generation and its current product workflow requires a
human official to review a draft. The new workflow has a different responsibility: editorial
orchestration and autonomous publication. Combining both responsibilities would make it unclear
which caller is allowed to bypass human review and which account authored the resulting post.

## Options considered

1. Add top-story selection and autonomous publication directly to `postagent`.
2. Add a separate `agents.ysnagent` subdomain inside `post-service` that orchestrates
   `postagent` and `posts` through public Java contracts.
3. Deploy the new agent as a separate service and call `post-service` over HTTP.
4. Build an admin interface that runs the existing human-reviewed workflow.

For authorship:

1. publish as the administrator who triggered the run;
2. authenticate or impersonate a Keycloak user named `ysn`; or
3. resolve a fixed application-owned `ysn` official publisher through a trusted internal contract.

## Decision

Add `com.yoursay.agents.ysnagent` as a third role-specific subdomain beneath
`com.yoursay.agents`, alongside `postagent` and `unwrappedagent`. It remains inside the single
`post-service` deployable.

Expose one initial endpoint, `POST /admin/ysn-agent/posts`, protected server-side by the existing
Keycloak realm role `admin`. It returns `202 Accepted` after committing a durable orchestration
job. No mobile or web interface is required.

`ysnagent` researches current top stories, retains an auditable candidate/source set, chooses one
non-duplicate topic and passes a bounded brief to a public in-process `postagent` generation
contract. `postagent` returns the complete sourced draft; it does not publish it for this workflow.
`ysnagent` then verifies all required content, voting and citation fields and publishes through a
narrow public `posts` contract.

Every resulting post is authored by the fixed application account whose handle is `ysn`. That
account must be active, `OFFICIAL` and an `ACTIVE` publisher at publication time. The triggering
admin subject is retained only in private audit data. The server does not fabricate a bearer token,
impersonate `ysn` in Keycloak or attribute the post to the admin.

Jobs are idempotent and fail closed. Only one active run is allowed by default, publication is
unique per job, and incomplete drafts, invalid options, unsupported claims, bad citations or an
unauthorised `ysn` account never produce a post.

Media remains optional. The first version may publish text-only and must not automatically
republish arbitrary images found through web search.

## Reason

A separate orchestration domain keeps editorial selection and autonomous publication distinct from
the reusable post-writing capability. It makes the human-reviewed and autonomous paths explicit,
while retaining one generation implementation and one canonical post-validation implementation.

In-process public contracts preserve the DDD boundaries without adding network, authentication and
partial-failure complexity between code that already runs in one deployable. A durable job is
needed because two live research/generation steps may outlast an HTTP request and must retry safely.

A fixed application-owned author accurately represents the publication. Admin authentication
answers who may start the operation; it should not change who publicly authored the post.

## Consequences and follow-up work

- The `agents` namespace now contains planned `postagent`, `unwrappedagent` and `ysnagent`
  subdomains.
- `postagent` needs a public complete-draft generation contract that does not expose its internal
  generator or persistence.
- `posts` needs an idempotent trusted-agent publication contract that still enforces canonical post
  rules and active official-publisher status.
- The `ysn` application account must be provisioned and have a public official profile; it does not
  need interactive login for this workflow.
- Candidate sources, the selection decision, model/prompt versions, triggering admin subject and
  final post ID are retained for audit.
- Metrics and alerts are required for research/generation latency, validation failures, duplicate
  suppression, publication failures and successful runs.
- Recurring scheduling, an operational interface and rights-safe automated media are separate later
  decisions.
