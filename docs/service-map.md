# Service Map — MVP1

**Status:** Decided (Stage 0) · **Supersedes:** the service-per-domain map in earlier roadmap drafts

## Decision

MVP1 runs **two backend services**, with strict DDD **domains** inside each:

| Service | Port | Domains |
| --- | --- | --- |
| `user-service` | 8081 | `user` (identity/PII, public profile), `usercharacteristic`, `social` (follow graph) |
| `post-service` | 8082 | `posts` (create/view), `votes` (votes **+ by-characteristic sentiment aggregation**), `feed` (ranking/assembly), `topics` (taxonomy, classification and private user interests), `agent` (Grok-backed unbiased-post creation) |

No new deployable service is required for MVP1. Votes, feed, topics and the agent are domains
inside the two existing services, not standalone services.

## Why

- **Low service count is cheaper to run and reason about.** Each extra service adds deployment,
  networking, and cross-service-call cost. We pay that only where something genuinely needs to scale
  or deploy independently.
- **Strict DDD makes the boundary, not the service.** Every domain is a top-level package whose only
  public face is its controllers, public interfaces and DTOs (see `CLAUDE.md`). Nothing reaches into
  another domain's `model`/`service`. So a domain can be **extracted into its own service later as a
  near-mechanical package move** — the boundary is already enforced in code.
- **`votes` stays with `posts`** because votes are cast on local post content and the privacy
  boundary is enforced at the **domain** layer: each vote carries an anonymised
  `CharacteristicSnapshot` (see [vote-aggregation.md](./vote-aggregation.md)), so aggregation never
  query-time cross-joins into `user-service`. A service hop would add nothing here.
- **`feed` stays with `posts`** because it ranks local post content. It calls `user-service` for the
  `social` follow graph via the existing rest-client pattern; the ranker is swappable behind an
  interface (see [feed-ranking.md](./feed-ranking.md)).
- **`topics` stays with `posts`** because it classifies and indexes local post content, owns private
  user topic interests, then supplies canonical signals to `feed`. Its public interface
  keeps those records separate from both post persistence and aggregate-only user characteristics.
- **`social` (follows) sits with `user`** because a follow is a user-to-user relationship that pairs
  with the public profile.
- **`agent` stays with `posts`** because work is durable and asynchronous, and approval writes a
  local post. Its strict domain boundary keeps later extraction possible if load justifies it.

## Consequences

- New cross-service calls are wired as `service.*` rest-client URLs in `application.properties` as
  each comes online (`post-service` → `user-service` already exists). The `feed` → `social` call
  arrives in Stage 5.
- If a domain ever outgrows its host service, extraction is a package move plus a new module — not a
  redesign — precisely because the domain boundary was kept strict from the start.
