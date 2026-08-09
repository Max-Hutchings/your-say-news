# ADR-035 — Defer Unwrapped prediction stories

Date: 2026-07-28

> **Amended by [ADR-036](ADR-036-2026-07-28-admin-forced-unwrapped-generation.md):**
> administrators may manually request the same milestone reconciliation used after a canonical
> vote; the 100-vote minimum and later configured milestones still determine job eligibility.

## Situation

ADR-029 introduced prediction stories so Post Unwrapped could show generated content before enough
canonical votes existed for observed demographic analysis. That added a second generation mode,
publication-time reconciliation, prediction-specific prompts and validation, nullable milestone
and aggregate fields, and client rendering for forecast cohorts.

The current product requirement is narrower: Post Unwrapped analyses actual aggregate vote data.
Before the first safe observed milestone, users can continue to the factual results without a
generated prediction.

## Options considered

1. Keep prediction generation but disable its scheduler.
2. Keep prediction types and stored schema for possible future reactivation.
3. Remove prediction from the active lifecycle and reintroduce it later through a new decision.

## Decision

Choose option 3.

Post Unwrapped generates only observed stories after the first 100-vote milestone and at later
configured milestones. Before 100 votes, the API returns `INSUFFICIENT_EVIDENCE` with an explicit
notice rather than a prediction or a misleading `BUILDING` state.

Remove the prediction enqueue endpoint, publication scan, model mode, prediction prompt and output
fields, prediction validation, client rendering, and prediction database shape. Existing prediction
jobs and their dependent stories, sources, and follow-up responses are deleted by migration before
the prediction-only columns are removed.

Human review remains mandatory for every observed story.

## Reason

The prediction path does not analyse vote data and is not required for the current Post Unwrapped
journey. Removing it leaves one lifecycle: capture aggregate data, select safe cohorts, generate the
arguments, validate them, persist a draft, obtain human approval, and serve it.

## Consequences

- Voters below the first observed milestone do not receive generated Unwrapped arguments.
- Prediction content already stored in development or deployed databases is removed.
- `mode`, `predictionVersion`, and `predictedCohorts` are no longer public or persisted concepts.
- Reintroducing forecasts requires a new ADR, schema migration, API contract, and explicit product
  approval.
- This ADR supersedes the prediction-specific decisions in ADR-029 and the prediction sections of
  the Post Unwrapped architecture plan.
