# Docs

Architecture designs and reference material for Your Say News.

- **Architecture** — system diagrams, domain boundaries, data-flow, and the anonymisation
  approach (how we report sentiment by characteristic without exposing PII) live here.
  - [`service-map.md`](./service-map.md) — the three-service architecture and why.
  - [`vote-aggregation.md`](./vote-aggregation.md) — the privacy aggregation contract (the PII boundary + `k`-anonymity lever).
  - [`feed-ranking.md`](./feed-ranking.md) — the swappable `FeedRanker` contract.
- **[`plans/`](./plans)** — individual feature implementation plans written before building.
- **[`test-accounts.md`](./test-accounts.md)** — seeded Keycloak/DB login accounts for dev.

## Conventions

- One document per topic, named in kebab-case (e.g. `vote-aggregation.md`).
- Keep diagrams as text/markdown where possible so they diff cleanly.
