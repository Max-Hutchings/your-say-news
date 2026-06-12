# Your Say News — MVP1 Roadmap

> Stages, not dates. Each stage is a buildable increment that leaves the app in a
> demoable state. Ordered so every stage builds on a working one beneath it.

## Product decisions locked for MVP1

These were decided up front and shape the stages below:

| Area | MVP1 decision | Notes / fast-follow |
| --- | --- | --- |
| Recommendation feed | **Chronological + follows** (reverse-chron, boost followed accounts). Real rec engine is post-MVP1. | Feed code structured behind an interface so the ranker can be swapped. |
| Unbiased Post agent | **LLM + live web search** — Claude with a web-search/fetch tool pulls real sources at creation time, then synthesises summary + both sides + support question. | Sources always linked. Latency handled async (see Stage 6). |
| Post media | **Images + video** via the existing S3/LocalStack setup. | Video needs an upload + (basic) transcode/poster path. |
| Privacy guard | **Aggregate-only, no minimum bucket threshold** for MVP1. | ⚠️ Re-identification risk on tiny buckets. Aggregation layer built so a `k`-anonymity threshold is a single config flip — **top privacy fast-follow.** |

## What already exists (baseline)

- `user-service` (:8081) — domains `user`, `usercharacteristic`.
- `post-service` (:8082) — domains `posts`, `votes` (vote scaffolding present: `model`/`service`/`client`).
- Keycloak auth with realm + test users auto-imported; mobile auth (`features/auth`) wired with bearer-injecting HTTP client.
- Onboarding characteristic option sets exist on the frontend (`features/user-characteristics/data/options.ts`) and mirror backend enums (race, sex-at-birth, income, height, weight, eye colour, parent, plus free/range fields: country/city, age range, gender, education, occupation, news frequency, country of birth, UK county, university subject).
- Liquibase migration/seed split, telemetry (OTel→Grafana LGTM), mprocs dev runner.

The core PII boundary is already modelled correctly: `CharacteristicAnswers` carries **no** identity — identity travels only in the bearer token. Preserve this everywhere.

---

## Stage 0 — Foundations & contracts

Lock the cross-cutting pieces every later stage depends on, so we don't rework them.

- **Privacy aggregation contract.** Define the DTO shape for "sentiment by characteristic" (vote tallies + percentages per characteristic bucket, never per-user rows). Build the query/aggregation layer behind an interface with a `suppressBelow=k` config (default `k=0` for MVP1, flip later). This is the single most important contract — get it right once.
- **Feed contract.** A `FeedRanker` interface returning post IDs for a user; MVP1 implementation = chronological + follow-boost. Keeps the real rec engine a drop-in later.
- **Service map.** Decide where new domains live. Proposed: keep within the two services for MVP1 to avoid premature service sprawl — `social` (follows) + `feed` in their own modules or folded into `user-service`/`post-service`; the **unbiased-post agent** as its own service (`agent-service`) because it has distinct deps (LLM client, web fetch) and scaling/latency profile.
- **Shared design system pass.** Confirm `constants/theme` tokens + `components/ui` primitives cover cards, chips, buttons, vote controls, bottom-sheet/modal — the feed and results UI lean on these heavily.

**Demoable:** nothing user-facing; contracts + ADRs in `docs/`.

---

## Stage 1 — Sign-up, onboarding & the privacy promise

Get a real user from zero to a complete characteristic profile, with privacy messaging front and centre.

- **Sign-up / sign-in** end-to-end against Keycloak (registration, not just the seeded test users).
- **Characteristic onboarding flow** — multi-step UI consuming `options.ts`, persisting `CharacteristicAnswers` to `usercharacteristic` with **no identity attached**. "Prefer not to say" honoured throughout.
- **Privacy marketing & consent** — clear, explicit screens during sign-up: *what we collect, that only aggregated/anonymised characteristics are ever shown, that name/email/exact identity are never tied to a vote publicly.* Explicit consent recorded.
- Backend: persist + validate characteristic enums; ensure PII (name/email) lives only in `user`, characteristics in `usercharacteristic`, joined only by the authenticated subject server-side.

**Demoable:** a new account can sign up, read the privacy promise, and complete their full characteristic profile.

---

## Stage 2 — Posts: create, store, render

The content backbone. Standard user-authored posts first; the agent comes later.

- **Post model**: headline, summary/body, **support question** (the "do you agree with X?" prompt), author, timestamps, media refs, `isUnbiased` flag (false here).
- **Create-post flow** (mobile) — headline + summary + support question, **image + video upload** to S3/LocalStack (upload, validation, poster/thumbnail for video). Handle upload progress + failure.
- **Post detail + card components** in `features/posts` — modern card with media, used by feed and profile.
- Backend `posts` domain endpoints (create / get / list by author), assets served from S3.

**Demoable:** a user creates a text+media post and views it.

---

## Stage 3 — Voting on the support question

The interaction that generates all the sentiment data.

- **Vote model** (`votes` domain, scaffold already present): one vote per user per post, yes/no on the support question, **stored against the user's characteristic snapshot, never against public identity**.
- **Vote UI** — clear yes/no on the support question; one vote per user, editable/lockable per product call (recommend lockable after first vote for MVP1).
- Capture the voter's characteristics at vote time (snapshot) so later characteristic changes don't retro-rewrite history.
- Enforce the PII boundary: the vote row may reference the user for "have I voted / one-per-user", but **aggregation never exposes that linkage**.

**Demoable:** users vote yes/no; votes persist; revote rules enforced.

---

## Stage 4 — Aggregated sentiment results & breakdowns

The heart of the product: how different kinds of people feel, with sortable breakdowns.

- **Results view** unlocked **after voting** — overall yes/no split for the post.
- **Breakdown by characteristic** — filter/sort by country, race, gender, age band, income, etc. (any characteristic captured). Each shows the yes/no split for that bucket.
- Powered by the Stage 0 aggregation layer: **counts + percentages only, never individual rows.** (MVP1: no suppression threshold — flagged risk; the `k` flip is ready.)
- UI: characteristic selector (chips/dropdown), animated bars/segments from theme tokens, "X people like you / X overall" framing without ever naming a person.

**Demoable:** after voting, a user explores "how do 25–34 / Black / UK / >£100k voters feel about this?"

---

## Stage 5 — Profiles, follows & the social feed

Identity-light social graph plus the feed that ties it together.

- **Personal profile page** — your posts, basic public profile (display name/handle/avatar — public-by-choice identity, distinct from the private PII used for aggregation). See vote/post counts.
- **Other users' profiles** — view anyone's posts; navigate from a post author to their profile.
- **Follow / unfollow** (`social` domain) — follow graph, follower/following counts.
- **Main feed** — the `FeedRanker` (chronological + follow-boost): posts from followed accounts surfaced, blended with recent posts; infinite scroll, pull-to-refresh, modern feed UX.

**Demoable:** follow an account, see their posts rise in your feed, browse profiles, view your own post history.

---

## Stage 6 — The Unbiased Post agent

The differentiator. Built last because it depends on the post + support-question + media pipeline already working.

- **`agent-service`** — hosts the post-creation agent (Claude, latest model) with a **strong unbiased system prompt** and a **web-search/fetch tool** over official news sources + social feeds.
- **Conversational creation flow** (mobile) — user speaks/types what they want covered; agent researches, then produces: **neutral summary**, **what side A believes**, **what side B believes**, **linked sources** (always), and a **generated support question** asking which side the user agrees with.
- **Fact-grounding + sourcing** — every claim backed by a linked source; agent instructed to prefer verifiable facts and present both sides proportionally.
- **Async handling** — research has latency; creation runs as a job with progress UI, producing a draft the user reviews/approves before publish.
- **Unbiased mark** — `isUnbiased=true` posts render a distinct corner badge everywhere they appear (feed, profile, detail).
- Unbiased posts flow into the same vote + aggregation pipeline as any other post.

**Demoable:** user talks to the agent → reviews a sourced, balanced post with an auto-generated support question → publishes it with the unbiased badge.

---

## Stage 7 — Hardening for MVP1 release

- **Tests:** Quarkus + Testcontainers on every new domain (real Postgres/Keycloak/S3); React Testing Library on onboarding, voting, results breakdowns, feed, agent flow. Run `test-audit` after each suite.
- **Privacy audit:** prove no endpoint returns a vote next to identity; review aggregation outputs for the small-bucket risk; final go/no-go on whether `k`-threshold ships with MVP1.
- **Telemetry:** traces/metrics on agent runs (latency, source counts), vote/aggregation queries, feed build.
- **Performance:** feed + aggregation query load, media delivery, agent timeouts/retries.
- Polish, empty/error states, accessibility pass on the core loop.

---

## Critical path (dependency order)

```
Stage 0 (contracts) → 1 (onboarding) → 2 (posts) → 3 (voting) → 4 (aggregation)
                                              └→ 5 (profiles/follows/feed)  [needs 2]
                                                        6 (agent)           [needs 2,3]
                                                        7 (hardening)       [continuous, gates release]
```

Stages 4 and 5 can run in parallel once 2–3 land. Stage 6 (agent) is isolated in its own service and can start as soon as Stage 2's post/support-question model is stable.

## Top risks to watch

1. **Privacy / re-identification** — the no-threshold decision is the single biggest product risk given the brand promise. Keep the `k`-flip ready; revisit before launch.
2. **Agent quality & bias** — an "unbiased" agent that is subtly biased or cites weak sources undermines the whole differentiator. Heavy prompt + sourcing review needed.
3. **Agent latency/cost** — live web research per post is slow and metered; async + caching essential.
4. **Characteristic snapshotting** — get vote-time snapshots right early, or historical aggregates become wrong when users edit their profile.
