# ADR-039 — Administrator-only Post Unwrapped generation

Date: 2026-08-03

## Situation

Post Unwrapped makes paid provider calls and publishes generated analysis after human review. The
previous lifecycle allowed canonical voting and scheduled milestone reconciliation to create work.
That means stored or seeded vote totals can accidentally become provider-backed jobs without an
administrator choosing the post and accepting the cost.

The administration desk already provides an explicit `Run analysis` action. That action must be the
only production authority to enqueue generation.

## Decision

Post Unwrapped generation is administrator-only and opt-in.

- Casting a canonical vote must never write an Unwrapped reconciliation marker or analysis job.
- Loading seed data, starting or restarting the application, crossing a vote milestone and changing
  an analysis version must never enqueue generation.
- No periodic scan may infer work from stored vote totals.
- Only the administrator-protected `Run analysis` endpoint may mark one selected post for
  reconciliation.
- One explicit request produces at most one job for the highest eligible milestone and analysis
  version. Repeated clicks remain idempotent unless an administrator explicitly retries a failed
  job.
- Background workers may process administrator-created work, but they may not discover new work by
  scanning posts or votes.

The vote milestone remains an eligibility and story-versioning boundary; it is not an automatic
trigger.

## Reason

Provider expenditure must follow an intentional human action. A durable click-created queue retains
reliability without giving ordinary vote traffic, seed tooling, startup behaviour or version bumps
authority to spend money.

## Consequences

- Remove the votes-domain call to `UnwrappedMilestoneService`.
- Retain reconciliation only as the durable continuation of an authenticated administrator request.
- Tests must prove that a successful vote and seeded vote totals create no marker or job, while an
  administrator request creates exactly one current-milestone job.
- Operational cleanup of previously generated jobs or stories is a separate, explicit destructive
  action.

This decision amends the automatic observed-generation trigger in
[ADR-029](ADR-029-2026-07-25-versioned-unwrapped-story-lifecycle.md).
