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
- `post-service` (:8082) — domains `posts`, `votes` (vote scaffolding present: `model`/`service`/`client`). **Votes stay here; `feed` joins them as a sibling domain — see the Stage 0 service map.**
- Keycloak auth with realm + test users auto-imported; mobile auth (`features/auth`) wired with bearer-injecting HTTP client.
- Onboarding characteristic option sets exist on the frontend (`features/user-characteristics/data/options.ts`) and mirror backend enums (race, sex-at-birth, income, height, weight, eye colour, parent, plus free/range fields: country/city, age range, gender, education, occupation, news frequency, country of birth, UK county, university subject).
- Liquibase migration/seed split, telemetry (OTel→Grafana LGTM), mprocs dev runner.

The core PII boundary is already modelled correctly: `CharacteristicAnswers` carries **no** identity — identity travels only in the bearer token. Preserve this everywhere.

---

## Stage 0 — Foundations & contracts

Lock the cross-cutting pieces every later stage depends on, so we don't rework them.

- **Privacy aggregation contract.** Define the DTO shape for "sentiment by characteristic" (vote tallies + percentages per characteristic bucket, never per-user rows). Build the query/aggregation layer behind an interface with a `suppressBelow=k` config (default `k=0` for MVP1, flip later). This is the single most important contract — get it right once.
- **Feed contract.** A `FeedRanker` interface returning post IDs for a user; MVP1 implementation = chronological + follow-boost. Keeps the real rec engine a drop-in later.
- **Service map (decided).** Three services, with clean DDD domains *inside* each. A low service count is cheaper to run and reason about; because every domain is a top-level package touched only through its controllers/interfaces/DTOs, extracting one into its own service later is a near-mechanical package move, not a rewrite. So we keep boundaries strict at the **domain** level and pay for extra services only when something actually needs to scale or deploy independently:

  | Service | Port | Domains it owns |
  | --- | --- | --- |
  | `user-service` | 8081 | `user` (Identity/PII, public profile), `usercharacteristic`, `social` (follow graph) |
  | `post-service` | 8082 | `posts` (create/view), `votes` (**votes + by-characteristic sentiment aggregation** — the privacy core), `feed` (**recommendation algorithm**, feed assembly/return) |
  | `agent-service` | new | `agent` — Unbiased-post creation (LLM + live web search) |

  **Why this split.** `votes` + aggregation stay in `post-service` next to the content they vote on; the privacy boundary is enforced at the **domain** layer (each vote carries a **characteristic snapshot**, so aggregation never query-time cross-joins into `user-service`), not by a service hop. `feed` is a read/orchestration domain in the same service as `posts`: it ranks local post content and calls `user-service` for the `social` follow graph (and later `votes`/`user-service` for ranking signals), so the rec engine swaps in behind `FeedRanker` without touching anyone else. `social` (follows) sits with `user` because a follow is a user-to-user relationship and pairs with the public profile. **`agent-service` is the only new service** — it's isolated, latency-heavy, separately scaled/metered (live web research), and depends on nothing else at write time. Wire `service.*` rest-client URLs in `application.properties` as each cross-service call comes online (`post-service` → `user-service` already exists).

Stage 0 is **backend contracts only** — no UI. The shared design-system work (confirming `constants/theme` tokens + `components/ui` primitives cover cards, chips, vote controls, bottom-sheet/modal) moves into the UI stages that first need each primitive, built just-in-time rather than as one upfront pass (chips in onboarding/results, vote controls in Stage 3, etc.).

**Demoable:** nothing user-facing; contracts + ADRs in `docs/`.

---

## Stage 1 — Sign-up, onboarding & the privacy promise

Get a real user from zero to a complete characteristic profile, with privacy messaging front and centre.

- **Characteristic-coverage review (do first).** Audit whether the captured set covers the breakdowns we promise. Confirmed gaps/additions to make before the onboarding flow is built — see *"Characteristic coverage"* below. The headline gap: **political persuasion is not currently captured** despite `CLAUDE.md` naming it as a core breakdown axis.
- **Sign-up / sign-in** end-to-end against Keycloak (registration, not just the seeded test users).
- **Characteristic onboarding flow** — multi-step UI consuming `options.ts`, persisting `CharacteristicAnswers` to `usercharacteristic` with **no identity attached**. Core characteristic axes are required; location free-text exceptions are captured in ADR-001.
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

- **Vote model** (`post-service` `votes` domain — build out the existing scaffold): one vote per user per post, yes/no on the support question, **stored against the user's characteristic snapshot, never against public identity**.
- **Vote UI** — clear yes/no on the support question; one vote per user, editable/lockable per product call (recommend lockable after first vote for MVP1).
- Capture the voter's characteristics at vote time (snapshot) so later characteristic changes don't retro-rewrite history.
- Enforce the PII boundary: the vote row may reference the user for "have I voted / one-per-user", but **aggregation never exposes that linkage**.

**Demoable:** users vote yes/no; votes persist; revote rules enforced.

---

## Stage 4 — Aggregated sentiment results & breakdowns

The heart of the product: how different kinds of people feel, with sortable breakdowns.

- **Results view** unlocked **after voting** — overall yes/no split for the post.
- **Breakdown by characteristic** — filter/sort by country, race, gender, age band, income, etc. (any characteristic captured). Each shows the yes/no split for that bucket.
- Lives in the **`post-service` `votes` domain** (votes + aggregation together). Powered by the Stage 0 aggregation layer: **counts + percentages only, never individual rows.** (MVP1: no suppression threshold — flagged risk; the `k` flip is ready.)
- UI: characteristic selector (chips/dropdown), animated bars/segments from theme tokens, "X people like you / X overall" framing without ever naming a person.

**Demoable:** after voting, a user explores "how do 25–34 / Black / UK / >£100k voters feel about this?"

---

## Stage 5 — Profiles, follows & the social feed

Identity-light social graph plus the feed that ties it together.

- **Personal profile page** — your posts, basic public profile (display name/handle/avatar — public-by-choice identity, distinct from the private PII used for aggregation). See vote/post counts.
- **Other users' profiles** — view anyone's posts; navigate from a post author to their profile.
- **Follow / unfollow** (**`user-service` `social` domain**) — follow graph, follower/following counts.
- **Main feed** (**`post-service` `feed` domain**) — hosts the `FeedRanker` (chronological + follow-boost for MVP1, real rec engine later): orchestrates local `posts` content + the `user-service` `social` follow graph; infinite scroll, pull-to-refresh, modern feed UX.

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

## Characteristic coverage — are we capturing everything?

Because the product *is* "sentiment sliced by who you are", the value of every aggregate is capped by the characteristics we collect. Audit + extend the set in Stage 1, **before** the onboarding UI is frozen.

**Captured today:** location (country/city), age range, gender, education, occupation, news frequency, race, sex at birth, height, weight, income, parent, eye colour, country of birth, UK county, university subject.

**Gaps to close for MVP1** (high signal for news-agreement, cheap to add as enums + onboarding steps):

- **Political persuasion / leaning** — **the priority gap.** Named explicitly in `CLAUDE.md` as a core breakdown yet absent from `options.ts`. Capture as a non-identifying band (e.g. Left / Centre-left / Centre / Centre-right / Right / Apolitical). Likely the strongest predictor of how someone votes on a support question.
- **Religion / religiosity** — affiliation and/or "how important is religion to you" band.
- **Region within country / urban-rural** — we have UK county only; add a country-agnostic region/state field and an urban / suburban / rural band so non-UK users are sortable.
- **Relationship/marital status** and **sexual orientation** — common opinion-research axes.
- **Citizenship / nationality** — distinct from country of birth and country of residence.
- **Employment sector / industry** — finer than the current occupation status list.

**Review for relevance / sensitivity:** height, weight and eye colour add re-identification surface area (small buckets) while adding little to news sentiment — keep, but they make the no-threshold privacy risk worse and argue for the `k`-flip. Treat especially sensitive fields through aggregation and minimum-bucket privacy rules.

Every new characteristic must land in three places in lockstep: backend enum, `options.ts`/`CharacteristicAnswers`, and the seed test-user generator below — otherwise aggregates can't be tested against it.

---

## Workstream T — Test users & the central fixtures service (cross-cutting, spans Stages 1–4)

Aggregates are only meaningful at volume and across a realistic spread of characteristics, so we need many fake voters and a single place to drive them. Built alongside the stages they validate, not at the end.

- **Seeded test users (no Keycloak).** Generate a large population of users + full characteristic profiles purely as **Liquibase seed data** (`user-service` `db/seeding/`, context `seed`). They never sign in, so they stay out of Keycloak's realm — they exist only to own votes and fill aggregation tables. Keep PII separate exactly as real users do.
- **Self-describing names.** Each test user's display name encodes its characteristics for at-a-glance debugging of aggregates, e.g. `UK-F-25_34-Left-HighIncome-Asian` or `US-M-65plus-Right-LowIncome`. Makes a wrong breakdown obvious by eye without querying.
- **Coverage by construction.** Generate the population to cover the cross-product of the key breakdown axes (country × age × gender × political leaning × income at minimum) with enough users per bucket to exercise — and later stress — the aggregation layer (and the future `k`-threshold).
- **Central fixtures service.** One shared test utility/service that, before a test run, **fetches all seeded user data and their characteristics** (from `user-service`) and **generates fake votes** across posts to fill the `votes` tables on demand. Tests call this instead of each hand-rolling vote data — a single source of truth for "given these users, cast this distribution of votes". Primarily drives the `votes` domain (votes/aggregation) but reusable by `feed` and anything else that needs a populated graph.
- **Deterministic + parameterised.** Seeded RNG so a given config reproduces the same votes (stable assertions), but parameterisable to skew distributions (e.g. "Left-leaning UK voters mostly vote Yes on post X") so aggregation/breakdown tests assert **expected** splits, not just non-empty ones — per the `test-audit` bar.
- **Used to validate Stage 4.** This workstream is what proves the sentiment breakdowns are correct: cast a known distribution, then assert the by-characteristic aggregates match it exactly.

**Demoable:** a single command/fixture call populates thousands of characteristic-tagged votes; the Stage 4 results screens show rich, sortable breakdowns driven entirely by seeded data.

---

## Critical path (dependency order)

```
Stage 0 (contracts) → 1 (onboarding) → 2 (posts) → 3 (voting) → 4 (aggregation)
                                              └→ 5 (profiles/follows/feed)  [needs 2]
                                                        6 (agent)           [needs 2,3]
       Workstream T (test users + fixtures) ─────────── [spans 1–4, gates 4]
                                                        7 (hardening)       [continuous, gates release]
```

Stages 4 and 5 can run in parallel once 2–3 land. Stage 6 (agent) is isolated in its own service and can start as soon as Stage 2's post/support-question model is stable. Workstream T starts once the characteristic model is fixed in Stage 1 and must be in place to meaningfully test Stage 4.

## Top risks to watch

1. **Privacy / re-identification** — the no-threshold decision is the single biggest product risk given the brand promise. Keep the `k`-flip ready; revisit before launch.
2. **Agent quality & bias** — an "unbiased" agent that is subtly biased or cites weak sources undermines the whole differentiator. Heavy prompt + sourcing review needed.
3. **Agent latency/cost** — live web research per post is slow and metered; async + caching essential.
4. **Characteristic snapshotting** — get vote-time snapshots right early, or historical aggregates become wrong when users edit their profile.
5. **Characteristic coverage frozen too early** — adding an axis (e.g. political persuasion) after onboarding ships means re-onboarding users and back-filling test data. Close the coverage gaps in Stage 1 before the schema sets.
