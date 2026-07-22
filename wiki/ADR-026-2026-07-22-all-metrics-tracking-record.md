# ADR-026 — ALL_METRICS

Date: 2026-07-22

## Situation

Metrics are discoverable from implementation code and individual plans, but there is no concise
place to understand what each metric measures or why it matters to users. A single table containing
every domain is complete but becomes difficult to scan and maintain as the register grows.

## Options considered

1. Keep metric definitions only beside their instrumentation code.
2. Maintain one large cross-domain table in this ADR.
3. Keep a central index here and a separate metric table for each domain.

## Decision

Choose option 3. `ALL_METRICS` is the reusable source of truth for metrics owned or explicitly
adopted by Your Say News. This ADR is the index; the linked files under `wiki/all-metrics/` own the
domain tables.

Backticked metric names are stable instrumentation contracts already chosen in code or a plan.
Plain-language names are dashboard measures or planned metrics whose final instrumentation name is
not yet defined. Planned measurements are labelled as such so the register does not imply that they
are already emitted.

## Metric domains

- [Platform and cross-domain backend metrics](all-metrics/platform.md) — 14 metrics
- [Topics metrics](all-metrics/topics.md) — 27 metrics
- [Feed metrics](all-metrics/feed.md) — 7 metrics
- [Posts and media metrics](all-metrics/posts.md) — 2 metrics
- [Votes and aggregation metrics](all-metrics/votes.md) — 4 metrics
- [Post Agent metrics](all-metrics/post-agent.md) — 8 metrics
- [Post Unwrapped metrics](all-metrics/post-unwrapped.md) — 10 metrics
- [Mobile metrics](all-metrics/mobile.md) — 2 metrics

## Reason

Domain tables are easier to scan, review and extend without losing the value of one central entry
point. Requiring an explicit user value discourages telemetry that is costly to operate but does not
support a product or reliability decision.

## Consequences and follow-up work

- This ADR and its linked domain files are intentionally living records.
- New instrumentation is incomplete until its row is present in the relevant domain table.
- A new metric domain requires a file under `wiki/all-metrics/` and a link in this index.
- Metric labels must remain bounded and must not contain PII, user IDs, post IDs, free text or
  characteristic values.
- Framework-provided runtime metrics remain outside the register unless the team explicitly adopts
  one for a user or product decision.
