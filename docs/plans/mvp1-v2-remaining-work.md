# MVP1 v2 — Remaining work

Audited on 23 July 2026 against commit `2d0c8b1` and the active
[MVP1 v2 roadmap](./mvp1-v2-roadmap.md). This is the completion checklist; it intentionally omits
work that is already materially implemented.

## Current position

The app already has the main foundations: registration/sign-in, consent and characteristic
onboarding; database-owned official publisher access; manual image/video post creation; binary and
single-select multiple-choice voting; vote-time characteristic snapshots; aggregate result sheets;
official-profile/follow scaffolding; a chronological follow-boost feed; and the backend post-agent
job/generation pipeline.

The largest missing product slice is **Post Unwrapped**. The top-level `unwrapped` package is only a
boundary marker today, with model integration reserved under its internal `unwrapped.agent`
package. **Topics are also not implemented**, and the existing official post-agent screen is still
an unwired template. The new server-side `ysnagent` official publisher is planned but has no
package, persistence or endpoint yet.

## Completion checklist

### 1. Finish the service consolidation

- [X] Prove the copied `user`, `usercharacteristic` and `social` domains running inside
  `post-service` with the full integration suite and a clean local startup.
- [ ] Remove the standalone `user-service`, its Gradle module, duplicate migrations/seeds, stale
  test-runner pane, compatibility REST-client configuration and unused bearer/client parameters.
- [ ] Update `CLAUDE.md`, the active roadmap and `docs/service-map.md` to describe the final
  single-deployable topology consistently.

### 2. Finish the aggregate and analysis contracts

- [ ] Define the aggregate-only Post Unwrapped input contract and versioned story output contract:
  slides, claims, figures, caveats, citations, model/prompt version, aggregate version and milestone.
- [ ] Choose and document the minimum sample, effect-size, uncertainty/confidence and
  multiple-comparison rules used to decide which cohort differences are safe to narrate.
- [ ] Extend vote snapshots/aggregation to every retained reportable field, including the three
  news-habit answers currently omitted, and define honest aggregation semantics for multi-select
  characteristics and intersections.
- [ ] Add per-option differences from overall and the selected statistical metadata to aggregate
  DTOs without exposing individual vote rows.
- [ ] Apply the same `suppressBelow=k` policy to the results API and analysis input, then make the
  release go/no-go decision for `k=0`.

### 3. Build Workstream T test data

- [ ] Seed a large, non-login test-voter population covering the important characteristic
  combinations, with self-describing debug names.
- [ ] Build one central deterministic fixture API/service for exact binary and multiple-choice vote
  distributions instead of hand-written vote rows.
- [ ] Add skew, sparse/noisy, milestone-concurrency and follow-up-isolation scenarios with exact
  expected aggregates and expected selected/omitted insights.

### 4. Build Post Unwrapped end to end

- [ ] Add the top-level `unwrapped` public contracts, persistence and migrations for versioned analyses,
  durable generation jobs and citations.
- [ ] Queue one idempotent job per `(post, milestone, analysisVersion)`, with configurable milestones,
  retry/backoff and safe concurrent-vote handling.
- [ ] Serve the newest eligible completed story while retaining the previous story during refresh;
  expose honest building, failed and insufficient-evidence states.
- [ ] Implement aggregate-only insight selection, cautious observed/context/interpretation wording,
  source validation and complete audit/version metadata.
- [ ] Add a full-screen mobile Post Unwrapped route with structured slides, progress, swipe/tap
  navigation, replay/exit, reduced-motion support and a link to the existing direct results explorer.
- [ ] Route every successful canonical vote into Post Unwrapped instead of opening the current raw
  results sheet as the final journey.
- [ ] Add the final follow-up choice table/API/UI, enforce one response per
  user/post/analysis-version, and prove it cannot alter canonical aggregates or milestones.

### 5. Complete official publishing and media

- [ ] Add the human review/approval/publish path needed by agent-created drafts; keep voting type and
  options immutable once a post is published.
- [ ] Add server-side upload verification plus the promised basic video transcode and poster
  generation path. Confirm recovery for interrupted/failed uploads.
- [ ] Record auditable subject/action/timestamp events for create, update, approval, publish and
  media writes without putting private identity into public post DTOs.
- [ ] Resolve the roadmap/code mismatch on post detail: either add the shared detail surface or
  formally make the existing immersive full-post feed card the MVP decision.

### 6. Finish official profiles, private accounts and feeds

- [ ] Restrict public profiles and follow targets to official publication accounts. Standard voter
  accounts are currently retrievable and followable and must not be publicly browsable.
- [ ] Add publication description and authoritative post count/history to the profile contract and
  complete profile empty/error/loading coverage.
- [ ] Turn the private account/settings area into the promised consent, characteristic and
  preference management surface.
- [ ] Replace the inert, always-selected **For you** tab with real **Latest/Following** discovery;
  retain follow boost, infinite scroll and pull-to-refresh, and verify stable pagination.

### 7. Implement topics and category discovery

- [ ] Add the governed canonical taxonomy, official selection of up to three topics and private
  onboarding/editable interests of up to seven topics.
- [ ] Add inferred topic assignments with provenance, confidence, classifier version and review
  state, backed by durable idempotent classification/reclassification jobs.
- [ ] Render effective topic chips and build category feeds using topic-relative popularity,
  recency decay and stable pagination.
- [ ] Record private impressions and strongly suppress already-seen posts until unseen content is
  exhausted.
- [ ] Add audited operational correction tools plus topic/classifier/feed metrics without logging
  identity, post text or individual interest sets.

### 8. Finish the official post-agent workflow

- [ ] Wire the mobile `PepperCompose` screen to start and poll the existing backend job API, including
  progress, retry and terminal failure states.
- [ ] Render the generated sourced draft, voting type and ordered options; let the official edit and
  reorder them before approval.
- [ ] Connect approval to publication, persist `publishedPostId`, require human-selected/verified
  media and render the unbiased badge across feed, profile and detail surfaces.
- [ ] Reconfirm production Grok/live-search configuration, citation validation and server-side
  official-only protection for generation, job reads, approval and publication.

### 9. Build the YSN official publishing agent

- [ ] Provision the fixed application account with handle `ysn` as active,
  `AccountType.OFFICIAL` and `PublisherStatus.ACTIVE`; add a public internal lookup that fails
  closed if it cannot publish.
- [ ] Add `com.yoursay.agents.ysnagent` with public controller/service/DTOs, a durable job migration
  and the state flow from research through publication.
- [ ] Add `POST /admin/ysn-agent/posts`, protected by the Keycloak `admin` realm role, returning
  `202` after persisting one idempotent job. Do not add a client interface.
- [ ] Research current top stories into an auditable candidate/source set, select one credible
  non-duplicate topic and persist the selection rationale and provider/model/prompt versions.
- [ ] Expose a public in-process `postagent` contract that accepts the selected brief and returns a
  complete sourced post draft without exposing generator or repository internals.
- [ ] Validate every required post field, voting configuration and claim citation, then publish
  idempotently through the public `posts` contract as `ysn`, never as the triggering admin.
- [ ] Add bounded retries for transient research/generation failures, one-active-run protection,
  recent-topic deduplication, fail-closed editorial validation, audit events and queue/publication
  telemetry.
- [ ] Keep media optional and text-only when needed; do not automatically publish arbitrary images
  found through web search.

### 10. Pass the release gates

- [ ] Add focused backend and frontend tests for every new slice above, especially publisher
  authorisation, aggregate privacy, milestone idempotency, analysis quality, follow-up isolation,
  official-only profiles, topics and all three agent workflows; run `test-audit` after each changed
  suite.
- [ ] Complete the authorisation and privacy audits, including proof that public signup cannot gain
  publisher status and no API/agent request pairs vote data with identity.
- [ ] Implement the metrics catalogued under `wiki/all-metrics`, with dashboards/alerts for queues,
  cache hits, latency, failures, costs, source quality, aggregation and feed/topic performance.
- [ ] Load/performance-test feed and category queries, aggregate snapshots, milestone concurrency,
  media delivery and story payload/render time.
- [ ] Polish empty, error, retry and stale-analysis states; complete screen-reader, focus, contrast,
  touch-target and reduced-motion checks on the core loop.
- [ ] Run a clean Compose smoke test and make backend build/Testcontainers, frontend typecheck,
  lint and Jest all green in CI. Remove the current React `act(...)` test warnings.

## Explicitly not required for MVP1

Public post creation, personalised Post Unwrapped stories, published follow-up results,
multi-select/ranked/write-in voting, a behavioural cross-category “For You” recommender and a
self-service publisher-admin UI remain deferred. The initial `ysnagent` also has no client
interface, recurring schedule, caller-selected topic or automatic web-image publication.

## Verification snapshot

- Frontend typecheck, lint and all **213 tests** passed during this audit; the Jest run still emits
  React `act(...)` warnings.
- The selected pure backend unit suites passed and `post-service` compiled.
- The full backend integration suite could not run because Docker/Testcontainers was unavailable;
  this is an unverified release gate, not an observed application failure.
