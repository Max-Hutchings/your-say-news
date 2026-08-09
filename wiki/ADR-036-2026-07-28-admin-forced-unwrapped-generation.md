# ADR-036 — Administrator-triggered Unwrapped reconciliation

Date: 2026-07-28

## Situation

Automatic Unwrapped generation begins at configured vote milestones, starting at 100 canonical
votes. Administrators need a way to manually exercise that lifecycle for a specific post without
casting another vote. This is primarily an operational and testing trigger: it must prove the
normal reconciliation, generation, validation, and review path works rather than creating a
parallel administrator-only job path.

## Options considered

1. Run model generation synchronously from the admin HTTP request.
2. Enqueue a durable generation job directly from the admin service.
3. Mark the post for normal milestone reconciliation.

## Decision

Choose option 3.

The protected Unwrapped admin desk accepts a post ID and upserts the same durable reconciliation
marker written after a canonical vote. The existing reconciliation worker counts committed votes,
creates any configured milestone jobs that are due, and removes the marker. The existing
generation worker then captures the aggregate, generates and validates the story, and persists an
immutable draft in the standard review queue.

The manual trigger does not bypass the 100-vote minimum or invent a milestone from the current vote
count. A post below the first milestone produces no generation job. Repeated requests remain
idempotent through the reconciliation marker upsert and the unique
`(post_id, milestone, analysis_version)` job key. An unknown post is rejected before a marker is
written.

The admin response acknowledges that reconciliation was queued; it does not claim that a generation
job was created. The admin UI explains that only milestones already reached can create jobs.

## Reason

Entering through the same durable marker as voting verifies the complete production trigger path,
including milestone detection and job idempotency. It keeps provider latency and failure out of
the admin request and prevents the admin service from duplicating job eligibility logic.

## Consequences

- Administrators can manually request normal reconciliation for any existing post.
- Posts below the first configured milestone do not create an Unwrapped job or draft.
- The trigger can recover or test an eligible post without adding a synthetic vote.
- Human approval remains mandatory before any resulting story is served.
- A second generation for an already-created milestone requires a future explicit
  regeneration/version design; the current action reuses the existing job.
