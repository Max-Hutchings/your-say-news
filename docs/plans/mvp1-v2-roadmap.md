# Your Say News — MVP1 v2 Roadmap

> This is the active MVP1 planning document. It supersedes `mvp1-roadmap.md` without
> modifying or deleting that original plan. Stages, not dates: each stage is a buildable
> increment that leaves the app in a demoable state.

## What changed from MVP1 v1

- **Public users do not create posts in MVP1.** Only authorised Your Say News official accounts
  can create, review and publish content. The existing post-creation scope remains, but it is an
  official-only workflow.
- **Public users still vote.** The request's reference to officials alone being able to “vote” is
  interpreted as “post”, because public voting and characteristic analysis remain the core product
  journey described below.
- **Account identity and publishing control are separate.** `AccountType` identifies `STANDARD`
  versus `OFFICIAL`; `PublisherStatus` independently records `NONE`, `ACTIVE` or `SUSPENDED`.
  Publishing requires an active account with `OFFICIAL` type and `ACTIVE` publisher status.
- **Voting now leads into “Post Unwrapped”.** Instead of landing on a conventional results page,
  the voter sees a paged, high-energy visual story that explains the result, meaningful cohort
  differences and well-sourced context.
- **A data-analysis agent creates the story.** Its output is generated at configurable vote-count
  milestones, stored in Postgres and reused until a later milestone produces a newer analysis.
- **The final slide offers a second vote.** This follow-up response is captured separately and never
  changes the original vote or any canonical aggregate. Personalised analysis is deferred.

## Product decisions locked for MVP1 v2

| Area | MVP1 v2 decision | Notes / fast-follow |
| --- | --- | --- |
| Publishing | **Only `OFFICIAL` accounts with `ACTIVE` publisher status publish.** There is no public create-post entry point or API permission. | Account type and status are application-owned database fields; see ADR-023. |
| Account identity | **Persist `STANDARD` and `OFFICIAL` account types separately from publisher status.** | Future site-administration permissions are a separate decision and may coexist with either type. |
| Voting | **Authenticated users vote once canonically per post.** | The separate Post Unwrapped follow-up vote is captured for research but does not alter results. |
| Post-vote experience | **Post Unwrapped replaces the raw results landing page.** | Direct aggregate exploration can remain accessible from the story. |
| Analysis | **Aggregate-only agent analysis, cached by vote milestone.** | User-specific stories are deferred until the audience is large enough to justify them. |
| Feed discovery | **Categories first; no “For You” page in MVP1.** Keep Following/Latest chronological with an official-follow boost. Category feeds rank posts by popularity within the selected canonical topic, tempered by recency, and strongly suppress posts already shown to that user. | A behavioural/personalised recommender is deferred. Feed code remains behind an interface. |
| Unbiased Post agent | **Grok + live web search** inside the `post-service` `agent` domain pulls real sources at creation time, then synthesises summary + both sides + support question. | Official-operated only. Sources always linked; latency handled with durable async jobs. |
| Post media | **Images + video** via the existing S3/LocalStack setup. | Video needs an upload plus basic transcode/poster path. |
| Privacy guard | **Aggregate-only, no minimum bucket threshold** for MVP1. | Re-identification risk remains on tiny buckets. Build `suppressBelow=k` as a configuration flip and revisit before release. The analysis agent must not narrate suppressed or statistically unsafe cohorts. |

## What already exists (baseline)

- `user-service` (:8081) — domains `user`, `usercharacteristic`.
- `post-service` (:8082) — domains `posts`, `votes` (vote scaffolding present:
  `model`/`service`/`client`). Votes stay here; `feed`, `topics`, post-creation `agent`, and the new
  vote `analysis` domain are siblings.
- Keycloak auth with realm + test users auto-imported; mobile auth (`features/auth`) wired with a
  bearer-injecting HTTP client.
- Onboarding characteristic option sets exist on the frontend
  (`features/user-characteristics/data/options.ts`) and mirror backend enums.
- Liquibase migration/seed split, telemetry (OTel → Grafana LGTM), and the mprocs dev runner.

The core PII boundary is already modelled correctly: `CharacteristicAnswers` carries no identity;
identity travels only in the bearer token. Preserve this for voting, aggregation and agent analysis.

---

## Stage 0 — Foundations, contracts and publisher-authorisation model

Lock the cross-cutting pieces every later stage depends on.

- **Publisher-authorisation ADR (decided).** ADR-023 stores `AccountType` and `PublisherStatus` on
  the application user record. The derived rule is
  `accountActive && accountType == OFFICIAL && publisherStatus == ACTIVE`; Keycloak remains the authenticator.
- **Privacy aggregation contract.** Define “sentiment by characteristic” DTOs: vote tallies and
  percentages per characteristic bucket, never per-user rows. Build the aggregation layer behind
  an interface with `suppressBelow=k` configuration (default `k=0` for MVP1).
- **Analysis input contract.** The analysis agent receives only aggregate results, post context,
  sample sizes and safe statistical metadata. It never receives voter identifiers or individual
  characteristic snapshots.
- **Analysis output contract.** Versioned story containing ordered slides, claims, supporting
  figures, caveats, source citations, model/prompt version, aggregate version and vote milestone.
- **Feed contract.** A `FeedRanker` returns post IDs for a user. Following/Latest uses chronological
  ordering plus followed-official boost; Stage 7 adds category ranking. There is no cross-category
  “For You” ranker or page in MVP1.
- **Service map.** Keep two deployable services and strict DDD domains:

  | Service | Port | Domains it owns |
  | --- | --- | --- |
  | `user-service` | 8081 | `user` (identity/PII, private voter account, official publication profile, account type and publisher status), `usercharacteristic`, `social` (users following official accounts) |
  | `post-service` | 8082 | `posts`, `votes` (canonical/follow-up votes + aggregation), `feed`, `topics`, `agent` (official post creation), `analysis` (cached post-vote stories) |

  `votes` remains beside the content and stores vote-time characteristic snapshots. `analysis`
  consumes only the public aggregate contract from `votes`; keeping it separate prevents agent
  concerns, research citations and cache lifecycle from leaking into core vote correctness.

Stage 0 is backend contracts and ADRs only. Shared UI primitives are built just in time in the
stages that first use them.

**Demoable:** no user-facing change; the privacy, publisher, aggregate and analysis contracts are
decided and documented.

---

## Stage 1 — Sign-up, onboarding and the privacy promise

Get a real voter from zero to a complete characteristic profile, with privacy messaging front and
centre.

- **Characteristic-coverage review first.** Confirm the captured set covers every breakdown the
  product promises. Political persuasion remains the headline gap; see “Characteristic coverage”.
- **Sign-up / sign-in** end-to-end against Keycloak, including registration rather than only seeded
  test users.
- **Characteristic onboarding flow** — multi-step UI consuming `options.ts`, persisting
  `CharacteristicAnswers` to `usercharacteristic` with no identity included in the DTO.
- **Privacy marketing and consent** — explain what is collected, that only aggregated/anonymised
  characteristics are shown, and that name/email/exact identity are never publicly tied to a vote.
  Record explicit consent.
- Backend validates characteristic enums and keeps PII in `user`, characteristics in
  `usercharacteristic`, joined only for the authenticated subject server-side.
- Normal accounts default to `STANDARD/NONE`. Only controlled internal seeding/operations can assign
  `OFFICIAL/ACTIVE` in MVP1; public signup can never self-select it.

**Demoable:** a new user signs up, reads the privacy promise and completes their characteristic
profile, but sees no create-post capability.

---

## Stage 2 — Official posts: create, store and render

Retain the complete post-creation backbone, but make it available only to authorised Your Say News
officials.

- **Post model:** headline, summary/body, support question (“do you agree with X?”), official
  author/publication profile, timestamps, media refs and `isUnbiased` flag (`false` here).
- **Official create-post flow** — headline + summary + support question, image + video upload to
  S3/LocalStack, validation, video poster/thumbnail, upload progress and failure recovery.
- **Backend endpoints** for create/get/list by official author. Every create/update/publish endpoint
  enforces the Stage 0 publisher capability server-side; hiding the mobile control is not security.
- **No public authoring surface.** Ordinary users have no create route, draft store, media-upload
  permission or post-write endpoint access.
- **Post detail and card components** in `features/posts`, shared by feeds and official publication
  profiles. Assets are served from S3.
- Audit the official subject, action and timestamp for content writes without exposing private
  identity in public post DTOs.

**Demoable:** an authorised official creates a text/media post and views it; an ordinary signed-in
user can read it but cannot create or modify one.

---

## Stage 3 — Voting on the support question

Build the interaction that generates sentiment data.

- **Canonical vote model** in the `post-service` `votes` domain: one yes/no vote per user per post,
  stored with the user's characteristic snapshot and never exposed alongside public identity.
- **Vote UI** — clear yes/no choice on the support question. Lock the canonical vote after first
  submission for MVP1.
- Capture characteristics at vote time so later profile edits do not rewrite history.
- The vote row may retain a private subject reference for “have I voted?” and uniqueness, but the
  aggregation and analysis contracts never expose that linkage.
- A successful vote routes directly into the Post Unwrapped journey. If no cached analysis exists
  yet, show an honest “story is building” state plus safe current totals rather than inventing an
  explanation.

**Demoable:** users vote yes/no once; votes persist, duplicate canonical votes are rejected, and the
post-vote route opens.

---

## Stage 4 — Aggregated sentiment results and breakdowns

Build the factual data layer that powers both direct exploration and Post Unwrapped.

- **Overall result:** yes/no counts, percentages and total sample size.
- **Breakdown by characteristic:** country, race, gender, age band, income and every other captured
  axis. Each bucket returns counts, percentages and sample size.
- Lives in `post-service` `votes`; counts and percentages only, never individual rows. The
  `suppressBelow=k` hook applies consistently to APIs and agent input.
- Add statistical metadata needed for safe narration: sample size, absolute/percentage-point
  difference from overall, and confidence/uncertainty measures selected in the Stage 0 contract.
- **Direct results explorer** remains accessible from Post Unwrapped for users who want the data:
  characteristic selector, animated theme-based bars/segments and “people like you / overall”
  framing without naming a person.

**Demoable:** a known seeded vote distribution returns exact overall and by-characteristic results,
and the direct explorer renders those values accurately.

---

## Stage 5 — Post Unwrapped vote journey and cached analysis agent

Turn the results into a clear, data-backed visual story after every vote.

### Analysis generation and caching

- Add a strict `post-service` **`analysis` domain**. It reads Stage 4 aggregate DTOs, optionally
  performs live research, and writes a structured story; it never queries individual vote rows.
- Generate asynchronously at **configurable cumulative vote milestones**, for example 10, 25, 50,
  100, 250, 500 and 1,000 votes, then a configurable growth rule. Values are configuration, not
  hard-coded product logic.
- Crossing a milestone queues one idempotent analysis job for `(post, milestone, analysisVersion)`.
  Concurrent votes must not create duplicate runs. Failures retry safely without blocking voting.
- Serve the newest completed analysis whose milestone is at or below the current canonical vote
  count. Keep the prior completed story available while a new one is generating.
- Persist a `vote_analysis` record/table with at least: post ID, milestone, canonical vote count,
  aggregate snapshot/version, status, structured slides/claims, cited sources, model and prompt
  versions, generation timestamps, error/retry metadata and supersession link/state.
- Agent outputs are cacheable and auditable. A model/prompt change can intentionally generate a new
  version without overwriting the historical story a user saw.

### Data-backed reasoning and research

- Find meaningful divergences across characteristics and intersections where the data supports it;
  for example, working-age men voting differently from the overall population.
- Rank insights by effect size, sample strength and relevance—not by whichever subgroup looks most
  dramatic. Correct for sparse buckets and broad subgroup searching so chance differences are not
  presented as discoveries.
- Separate three things on every claim:
  1. **Observed:** what this vote data shows.
  2. **Context:** externally researched facts from linked, credible sources.
  3. **Interpretation:** a cautious hypothesis for why the pattern may exist.
- Do not turn correlation into causation or stereotype a demographic. Wording such as “may be
  connected to” must replace unsupported “because” claims.
- Research questions may include tax exposure, employment patterns, policy impact or well-supported
  attitude research. Prefer primary statistics and reputable research; retain citations and access
  dates.
- If the evidence is weak, conflicting or the sample is too small, say so and omit the explanatory
  claim. The agent must be able to produce a shorter “not enough evidence yet” story.

### Mobile story experience

- Build **Post Unwrapped** as a full-screen, swipe/tap-through sequence inspired by the pace and
  clarity of Spotify Wrapped without copying its branding or assets.
- Slides can include: the user's submitted choice, overall split, strongest well-supported cohort
  difference, researched context, another useful comparison, methodology/caveat and a link to the
  direct results explorer.
- Slides render structured data; do not store or execute agent-generated UI code. Use the editorial
  design tokens, accessible motion, reduced-motion support, progress indicator and replay/exit.
- The story is the same for all users at a given cached analysis version in MVP1. User-characteristic
  personalised slide selection is explicitly deferred.

### Final follow-up vote

- The final slide asks whether the user would vote differently after seeing the story.
- Store this as a separate **follow-up response**, including post ID, private user subject reference,
  original canonical choice, follow-up choice, analysis version/milestone viewed and timestamp.
- Enforce one follow-up response per user/post/analysis version for MVP1. A later analysis version may
  collect another response, giving a clean longitudinal research record.
- Follow-up responses never update the canonical vote table, characteristic snapshot, aggregate
  counts, milestone triggers or story currently shown. They are not published in MVP1 results.

**Demoable:** after voting, a user watches a sourced Post Unwrapped story, opens the exact underlying
aggregate data and submits a follow-up choice; the original result remains byte-for-byte unchanged.

---

## Stage 6 — Official profiles, follows and the social feed

Keep the useful social discovery model while removing public-user publishing and public voter
profiles from MVP1.

- **Official publication profiles** — display name, handle, avatar, description, post count and
  published post history. Public identity belongs to the publication/official account, distinct
  from private voter identity and characteristics.
- **Private user account/settings page** — own consent, characteristics and preferences; no public
  post history because users cannot publish in MVP1.
- **Follow / unfollow official accounts** in the `user-service` `social` domain, with follower and
  following counts.
- **Following/Latest feed** in `post-service` `feed` — chronological with followed-official boost,
  infinite scroll and pull-to-refresh. Do not label it “For You”.

**Demoable:** a user follows an official publication, sees its posts rise in the feed and browses its
profile; ordinary voter accounts are not publicly browsable.

---

## Stage 7 — Topics and theme signals

Make official posts discoverable by meaning through the governed topic system. The detailed build
order remains in [`stage6-topics-and-theme-signals.md`](stage6-topics-and-theme-signals.md), subject
to this v2 roadmap's official-only publishing rule.

- **Broad controlled taxonomy** spanning politics, world affairs, money, society, science, climate,
  transport, culture and sport. Officials choose up to three canonical topics; clients cannot
  submit arbitrary public labels.
- **Onboarding interests** — optional final wizard step with up to seven canonical topics, stored in
  a dedicated `post-service` table, not in `CharacteristicAnswers` or vote snapshots.
- **Inferred canonical topics** — versioned classifier maps post text and named entities to the same
  taxonomy with provenance, confidence, classifier version and review state.
- **Reliable asynchronous classification** — publishing does not wait for inference. Idempotent jobs
  retry safely and support reclassification/backfill.
- **Visible category discovery** — effective topics render as tappable chips. Category feeds rank by
  topic-relative popularity with recency decay, stable pagination and complete empty/loading/error
  states.
- **Seen-post suppression** records a private impression only when shown. Seen posts are excluded
  while enough unseen content exists, then return only with a strong penalty.
- **Interests remain future inputs.** They are private/editable but do not assemble a personalised
  cross-category feed in MVP1.
- **Observable and correctable** — measure interest trends, classifier agreement/health and browse
  performance without recording identity, post text or individual interest sets in logs. Controlled
  operational tooling can verify/hide/replace/re-run assignments with an audit trail; it must use the
  account/publisher model selected in Stage 0 rather than a new Keycloak role.

**Demoable:** related official posts appear in the same category, a user browses them without
immediate repetition, and a new user can select private topic interests.

---

## Stage 8 — The official Unbiased Post agent

Retain the differentiating creation agent, but make the entire flow official-operated.

- **`post-service` `agent` domain** — configurable Grok models, balanced-reporting prompt and live
  web search.
- **Official conversational creation flow** — an authorised publisher speaks/types the subject; the
  agent produces a neutral summary, what each side believes, linked sources and a support question.
- **Fact grounding and sourcing** — claims backed by validated citations; prefer primary and strong
  independent reporting, represent uncertainty and avoid false balance.
- **Durable async jobs** with progress UI; the official reviews and approves a draft before publish.
- **Human-reviewed media** — the agent supplies an image brief/search query, but the official owns or
  verifies and uploads the selected image.
- **Unbiased mark** — `isUnbiased=true` renders a distinct badge in feed, official profile and detail.
- Published unbiased posts enter the same public vote, aggregate-analysis and Post Unwrapped flow.
- Ordinary users cannot access draft generation, approval or publication endpoints.

**Demoable:** an authorised official asks the agent for coverage, reviews a sourced balanced draft,
publishes it with the badge, and users vote and receive Post Unwrapped.

---

## Stage 9 — Hardening for MVP1 release

- **Tests:** Quarkus + Testcontainers for every domain and React Testing Library for onboarding,
  official publishing authorisation, voting, breakdowns, Post Unwrapped, follow-up vote isolation,
  feeds, topics and both agent flows. Run `test-audit` after each changed suite.
- **Authorisation audit:** prove an ordinary authenticated user cannot create, upload, generate,
  approve, update or publish a post; prove an authorised official can; prove public signup cannot
  grant publisher status.
- **Privacy audit:** prove no endpoint or analysis-agent request returns a vote beside identity;
  review small-bucket narration and decide whether `k` suppression ships enabled.
- **Analysis quality evaluation:** deterministic aggregate fixtures, expected insight selection,
  sparse/noisy data cases, unsupported-causation rejection, citation validation and human review of
  representative stories.
- **Telemetry:** onboarding interests, topic classifier/queue, post agent, analysis milestone queue,
  cache hit rate, generation latency/cost/failure, source counts, aggregation queries and feed build.
  Logs contain no individual votes or characteristic sets.
- **Performance:** feed/topic queries, aggregate snapshots, milestone concurrency, media delivery,
  agent timeouts/retries and story payload/render time.
- Polish empty/error/stale-analysis states and complete an accessibility pass on the core loop.

---

## Characteristic coverage — are we capturing everything?

Because the product is “sentiment sliced by who you are”, aggregate and analysis quality are capped
by the characteristics collected. Audit and extend them in Stage 1 before onboarding is frozen.

**Captured today:** location (country/city), age range, gender, education, occupation, news
frequency, race, sex at birth, height, weight, income, parent, eye colour, country of birth, UK
county and university subject.

**Gaps to close for MVP1:**

- **Political persuasion / leaning** — priority gap. Use a non-identifying band such as Left,
  Centre-left, Centre, Centre-right, Right and Apolitical.
- **Religion / religiosity** — affiliation and/or importance band.
- **Region within country / urban-rural** — country-agnostic region/state plus urban/suburban/rural.
- **Relationship/marital status and sexual orientation** — common opinion-research axes.
- **Citizenship / nationality** — distinct from birthplace and residence.
- **Employment sector / industry** — more informative than occupation status alone.

Height, weight and eye colour may add re-identification surface while contributing little to news
sentiment. Review their value before retaining them; regardless, sensitive fields require aggregate
handling and safe agent-narration rules.

Every characteristic must land together in the backend enum, frontend
`options.ts`/`CharacteristicAnswers`, and seeded fixture generator.

---

## Workstream T — Test voters and central fixtures (spans Stages 1–5)

- **Seeded test users, no Keycloak:** generate a large population and characteristic profiles as
  Liquibase seed data. They never sign in and exist to own deterministic test votes.
- **Self-describing names:** encode key characteristics for debugging, such as
  `UK-F-25_34-Left-HighIncome-Asian`.
- **Coverage by construction:** cover country × age × gender × political leaning × income with
  enough voters per bucket to test aggregation, agent insight ranking and future `k` suppression.
- **Central fixtures service:** fetch seeded users/characteristics and create parameterised vote
  distributions across posts rather than hand-writing votes per test.
- **Deterministic and skewable:** seeded RNG with scenarios such as “working-age men differ by 18
  percentage points” so tests assert exact aggregates and which insight should or should not be
  selected.
- **Milestone and follow-up fixtures:** cross several vote thresholds, verify one analysis job per
  threshold, and prove follow-up votes never affect canonical counts or trigger milestones.

**Demoable:** one fixture call produces thousands of known characteristic-tagged votes, exact result
breakdowns and repeatable Post Unwrapped analysis inputs.

---

## Critical path

```text
Stage 0 (contracts + publisher decision)
  → 1 (onboarding)
  → 2 (official posts)
  → 3 (canonical voting)
  → 4 (aggregation)
  → 5 (Post Unwrapped + cached analysis + follow-up vote)

Stage 2 → 6 (official profiles/follows/feed) → 7 (topics)
Stage 2 → 8 (official post agent); its published posts rejoin Stages 3–5
Workstream T spans Stages 1–5 and gates meaningful Stage 4/5 verification
Stage 9 hardening is continuous and gates release
```

Stages 4 and 6 can proceed in parallel after the required post/vote foundations land. The analysis
domain can begin once the Stage 0 contract is fixed, but Post Unwrapped cannot be accepted until the
Stage 4 aggregate source and Workstream T fixtures are proven.

## Top risks to watch

1. **Privacy / re-identification** — small cohorts can expose or strongly imply individual choices,
   and natural-language narration may amplify the risk. Apply the same suppression rules to APIs
   and agent input.
2. **Analysis overclaiming** — subgroup searches can find chance patterns; external facts do not
   prove why this audience voted a certain way. Require effect-size/sample checks and clearly label
   observation, context and hypothesis.
3. **Agent quality, sources, latency and cost** — both creation and analysis agents need durable
   async work, citation checks, monitoring and versioned caching.
4. **Publisher-authorisation drift** — all posting write endpoints must use the ADR-023 database
   rule; do not introduce a competing Keycloak role or client-only check.
5. **Follow-up contamination** — the Post Unwrapped second vote must be physically and logically
   separate from canonical aggregates and analysis milestone counts.
6. **Characteristic snapshot correctness** — later profile changes must not rewrite history.
7. **Characteristic coverage frozen too early** — adding high-signal axes later requires
   re-onboarding and fixture changes.
8. **Topic feedback loops** — correctable labels, recency-aware ranking, repeat suppression and
   observability remain essential.

## Explicitly deferred beyond MVP1

- Public/user-authored posts and the associated moderation pipeline.
- Personalised Post Unwrapped analysis based on the individual voter's characteristics.
- Publishing follow-up-vote results or using them to change canonical sentiment.
- A cross-category behavioural “For You” recommender.
- Self-service staff/publisher administration UI; future admin permissions require a separate ADR.
