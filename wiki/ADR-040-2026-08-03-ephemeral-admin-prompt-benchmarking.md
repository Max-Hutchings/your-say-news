# ADR-040 — Ephemeral administrator prompt benchmarking

Date: 2026-08-03

## Situation

Administrators need to compare alternative Post Unwrapped system prompts against the same post and
aggregate evidence. The production generation path is intentionally durable: an explicit admin
action creates reconciliation work, a scheduled worker generates a story, and that story enters the
publication review queue. Using that persistence lifecycle for experiments would mix disposable
prompt trials with publishable drafts and make comparisons depend on separate aggregate captures.

## Options considered

1. Queue every prompt variant as a normal analysis job and distinguish benchmark stories later.
2. Add a separate persisted benchmark job and result model.
3. Run a bounded administrator-only comparison directly, returning ephemeral results.

## Decision

Use a direct, ephemeral comparison endpoint for one to three system-prompt variants, presented in
the admin UI as three independently runnable lanes.

- The endpoint remains protected by the administrator role.
- It captures one aggregate snapshot and one deterministic cohort selection per request, then reuses
  the same research request if that request contains multiple variants.
- Each supplied prompt replaces the complete production system message for its first provider call.
- A model-correctable draft-format failure may be retried up to four times with a deterministic
  validation-repair suffix. Every variant returns its attempt count and exact effective system
  prompt, and the admin UI exposes that provenance rather than presenting a repaired result as the
  untouched editor prompt.
- The normal user-message output contract, provider tools, citation extraction and draft validation
  remain in force.
- Benchmarking does not create reconciliation markers, analysis jobs, stories, sources or review
  entries.
- Each variant succeeds or fails independently and results retain request order.
- The admin page submits one lane at a time and preserves other completed lanes. A lane is replaced
  only after its rerun succeeds, so iteration does not erase useful comparisons.
- The current production prompt is exposed only through the authenticated admin API to initialise
  the editors; it remains defined once in backend code.

## Reason

Ephemeral results keep experiments out of the publication lifecycle. A three-lane workspace bounds
the number of visible comparisons, while independent actions let an administrator control provider
spend and iterate without losing earlier results. Reusing the production preparation and validation
boundaries makes the system prompt the intended generation variable. Bounded repair attempts absorb
provider formatting variance without weakening validation, while explicit prompt provenance keeps
the comparison honest about the instructions that produced a successful result.

## Consequences

- Benchmark requests hold an HTTP connection while provider work completes and are not resumable
  after a page reload.
- Results are intentionally not auditable or publishable; a chosen prompt must still be promoted to
  the production configuration and run through normal generation and review.
- Provider calls may complete independently, so a lane-level failure does not discard valid sibling
  results.
- A lane can consume up to five provider calls when its drafts repeatedly violate correctable output
  constraints. The UI shows the attempt count and effective repair prompt so administrators can
  account for that spend and avoid attributing a repaired result solely to the editor text.
- Independently generated lanes capture the then-current aggregate; vote changes between clicks may
  therefore update the evidence supplied to a later lane.
- This is a narrow exception to ADR-039's scheduled processing rule: it is an explicit admin-paid
  action but has no authority to create publishable work.
