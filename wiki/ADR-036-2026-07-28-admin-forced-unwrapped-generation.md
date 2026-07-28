# ADR-036 — Administrator-forced Unwrapped generation

Date: 2026-07-28

## Situation

Automatic Unwrapped generation begins at configured vote milestones, starting at 100 canonical
votes. Administrators also need to create a draft for a specific post on demand, including for
testing, editorial preparation, or recovery when a post has not reached an automatic milestone.
The result must use the existing generation, validation, and review lifecycle.

## Options considered

1. Run model generation synchronously from the admin HTTP request.
2. Mark the post for normal milestone reconciliation.
3. Enqueue a normal durable generation job at the post's current canonical vote count.

## Decision

Choose option 3.

The protected Unwrapped admin desk accepts a post ID and enqueues a generation job whose milestone
is the current canonical vote count. The existing worker captures the aggregate, generates and
validates the story, and persists an immutable draft in the standard review queue.

The operation is idempotent for the same post, vote count, and analysis version. Repeated requests
return the existing job instead of creating duplicate provider work. An unknown post is rejected
before a job is written.

This is an explicit administrator override of ADR-035's automatic 100-vote trigger. A forced draft
can therefore be generated and approved below 100 votes. The normal review requirement and
low-evidence caveats still apply, and the admin UI warns about the override.

## Reason

Reusing the durable job path preserves retry, validation, immutable story, and review behaviour.
It also keeps provider latency and failure out of the admin request while avoiding a parallel
generation implementation.

## Consequences

- Administrators can deliberately create an Unwrapped draft for any existing post.
- Below-threshold drafts may contain no statistically eligible demographic cohorts.
- Human approval remains mandatory before any forced story is served.
- A second generation at an unchanged vote count requires a future explicit regeneration/version
  design; the current action reuses the existing job.
