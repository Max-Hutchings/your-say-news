# Stage 6 — Topics and theme signals

Implementation plan for MVP1 Stage 6. This stage adds a governed topic system to `post-service`
without changing the three-service map. The new `topics` domain owns the taxonomy, post-topic
assignments, inference jobs, private user interests and moderation. `posts` remains the
content owner; `feed` remains the read/ranking orchestrator. The governing taxonomy decision is
[`ADR-020`](../../wiki/ADR-020-2026-07-13-controlled-canonical-topic-taxonomy.md), and the
category-first feed decision is
[`ADR-021`](../../wiki/ADR-021-2026-07-14-category-first-feed-ranking.md).

## Outcome

At the end of this stage:

- the characteristics wizard ends with an optional topic-interest step where a user can select up
  to seven canonical topics;
- authors can optionally select up to three topics from the canonical taxonomy;
- every published post is queued for asynchronous classification against the same taxonomy;
- topic chips on a post open a paginated category feed ranked by topic-relative popularity and
  recency;
- a viewer does not immediately receive the same category post again unless unseen inventory is
  exhausted, in which case seen posts are massively deprioritised;
- admins can inspect and correct inferred assignments without destroying their provenance; and
- the Following/Latest feed remains chronological with the existing follow boost; and
- no cross-category “For You” page or behavioural recommendation ranker ships in MVP1.

The acceptance scenario is deliberately specific: a new user selects seven interests on the final
onboarding step and those choices are stored separately from their characteristics. Posts mentioning
a bill before Parliament and a policy change debated by MPs both acquire `legislation`, even when
their authors selected no topic.

## Decisions locked

### 1. One broad, controlled taxonomy

MVP1 uses one catalogue for onboarding interests, author selection, classification and browsing.
The IDs remain flat and multi-label for storage, but the UI groups them for scanning. This initial
catalogue is intentionally wide:

| Display group | Canonical topic IDs |
| --- | --- |
| Politics & government | `politics`, `elections`, `legislation`, `government-policy`, `local-government`, `democracy`, `civil-rights`, `immigration`, `public-services`, `defence` |
| World affairs | `international`, `diplomacy`, `war-conflict`, `humanitarian-affairs`, `global-development`, `europe`, `africa`, `middle-east`, `asia-pacific`, `americas` |
| Money & business | `economy`, `cost-of-living`, `business`, `jobs-work`, `taxation`, `trade`, `markets-investing`, `personal-finance`, `banking`, `cryptocurrency` |
| Society | `housing`, `health`, `mental-health`, `social-care`, `education`, `crime`, `justice`, `equality`, `family-parenting`, `religion-faith`, `community`, `media-journalism` |
| Science & technology | `technology`, `artificial-intelligence`, `cybersecurity`, `privacy-data`, `social-media`, `science`, `space`, `gaming`, `misinformation` |
| Climate & environment | `climate-change`, `environment`, `energy`, `sustainability`, `wildlife`, `agriculture`, `food-systems`, `weather`, `natural-disasters` |
| Transport & places | `transport`, `cars-motoring`, `rail`, `aviation`, `infrastructure`, `cities`, `rural-affairs`, `planning-development` |
| Culture & life | `arts-culture`, `books`, `film-television`, `music`, `celebrity`, `fashion`, `food-drink`, `travel`, `relationships`, `wellbeing` |
| Sport | `football`, `rugby`, `cricket`, `tennis`, `motorsport`, `athletics`, `cycling`, `golf`, `combat-sports`, `other-sport` |

Labels and short descriptions are product copy stored with each stable ID. Display groups organise
the picker but are not extra selectable topics or semantic parents. A post may be both
`legislation` and `housing`; the classifier does not automatically add every broad neighbouring
topic. IDs never change, and topics are retired rather than deleted so historical assignments and
user choices remain intelligible.

There is no public arbitrary-label path. `CreatePostRequest` accepts canonical IDs only, the
database enforces a foreign key to the taxonomy, and inference only returns known IDs. Adding a new
topic is a reviewed taxonomy migration plus classifier fixtures, not user-generated data.

### 2. Onboarding interests are separate personalisation data

Append a thirteenth “Topics of interest” step to the existing “Set up your lens” wizard. Selection
is optional: zero to seven canonical topics. The picker is searchable, grouped, shows a live
`n / 7` count and prevents an eighth selection in both client and server.

These choices are account-linked personalisation data, not anonymised characteristics:

- store them in dedicated `post-service` table `user_topic_interest`;
- keep them out of `OnboardingForm`, `CharacteristicAnswers`, vote snapshots, public profiles and
  sentiment breakdown axes; persist `interestTopicIds` as a separate field in the local draft;
- resolve the user from the bearer token; clients never send `userId`; and
- show different privacy copy on this final step: interests are private, used for discovery/future
  personalisation and can be changed later. Do not show the aggregate-only `PrivacyNote` there.

Interest selection does not change `hasCharacteristics` or the `user-service` onboarding-complete
contract. It is optional, so existing users are not sent back through onboarding. They get the same
picker under Settings → Topics of interest.

On final submission, save the full interest set first with an idempotent complete-replacement `PUT`,
then submit characteristics. “Skip for now” sets the list to empty and uses that same `PUT`, so a
retry after a partial earlier attempt cannot leave stale choices behind. If either call fails, keep
the draft; retrying the interest `PUT` is harmless and characteristics are never submitted before
the interest request succeeds.

### 3. Authors select; the classifier supplements

Topic selection is optional and limited to three canonical topics. Author choices are immediately
visible with source `AUTHOR`; confidence is reserved for inferred assignments. Inference may add at
most three more topics above the configured threshold. It never overwrites an author or admin
assignment.

If the classifier finds the same topic as the author, the single effective assignment remains
author-sourced. A post with no confident match is valid and remains unclassified; the classifier
must not force a misleading catch-all topic.

### 4. Versioned, explainable classifier first

Define a classifier interface so a later model can replace the implementation without changing
storage or callers. MVP1 starts with a deterministic multi-label classifier driven by a versioned
resource such as `topics-v1.json`:

- normalise case, Unicode and punctuation across support question, summary, case for/against and
  optional source titles/entities supplied later by Stage 7;
- score weighted phrases and entity aliases, with exclusions for ambiguous single words;
- emit only canonical topic IDs, confidence, basis (`TEXT`, `ENTITY` or `MIXED`) and matched rule
  identifiers;
- apply configurable minimum confidence and maximum inferred-topic count; and
- produce identical output for identical input + classifier version.

Matched rule IDs are stored for inspection; full duplicate excerpts are not. A word prefixed with
punctuation may contribute like any other text, but no arbitrary label is extracted or retained.

The classifier fixture set is written before the rules. Each topic needs representative positive
examples and ambiguous negative examples. In particular, `bill` must not mean legislation when it
means an invoice, and `party` must not mean elections when it means a social event. Startup
validation fails fast if rules reference unknown IDs or an active topic has no rule definition.

### 5. Inference is asynchronous and non-blocking

Post publication and author-selected topics commit together. The same transaction also creates one
classification job containing a minimal immutable input snapshot and content hash. A worker leases
pending jobs, classifies outside the transaction, then upserts inferred assignments in a short
transaction.

- A classifier outage never prevents publishing or hides author-selected topics.
- Jobs are idempotent on `(post_id, content_hash, classifier_version)`.
- Failed jobs use bounded exponential retry and become `FAILED` after five attempts.
- Admin re-run and taxonomy-version backfill both use the same job path.
- A worker may not reactivate a hidden assignment or replace an author/admin assignment.

The input contract includes optional source metadata and named entities now, even though manual
posts leave them empty. Stage 7 can populate those fields without another topic schema change.

### 6. Categories rank simply; interests do not create a For You feed

`user_topic_interest` records only the user's zero-to-seven durable onboarding/settings choices.
Do not infer interests from dwell time, votes, follows or topic page visits.

The Following/Latest feed continues to use `ChronologicalFollowBoostRanker`. Category browsing uses
a separate `CategoryPopularityRanker` over candidates from exactly one canonical topic. Its simple
ordering is:

1. calculate popularity from bounded recent engagement within that topic, not a global lifetime
   count;
2. apply recency decay and a deterministic `(postedAt, postId)` tie-break; and
3. exclude already impressed posts while unseen candidates exist, otherwise apply a deliberately
   large seen penalty so a small/exhausted category can still return content.

`FeedContext` and `RankablePost` gain the category, popularity and impression inputs needed by that
ranker. Feed assembly owns private, account-linked impression history and records an impression only
after the client reports that the post was actually displayed—not when it was merely returned in a
prefetched page. The implementation contract must set bounded score windows, decay constants,
impression retention and the seen penalty before code is written.

Declared interests do not combine categories or change their weights in MVP1. Do not infer
interests from dwell time, votes, follows or category visits. A behavioural cross-category “For
You” page is deferred until a separate ADR defines signals, diversity safeguards and evaluation
against this category-feed baseline.

### 7. Moderation changes state, not history

An inferred assignment has a source, classifier version, confidence and review state. Admins may:

- verify it;
- hide or restore it;
- add a different canonical topic as source `ADMIN`; or
- queue the post for reclassification.

Verification does not rewrite the source to `AUTHOR`. Every admin action records action, assignment,
admin token subject, optional note and timestamp. Public DTOs expose effective topic ID/label only;
classifier confidence, evidence, interests and audit identity remain private.

## Domain boundaries

Add `com.yoursay.topics` as a sibling of `posts`, `votes` and `feed` in `post-service`.

```text
onboarding/settings ── replace current user's interests ─> topics
posts ── validate/attach author topics + enqueue input ──> topics
posts <── batch effective topic summaries ─────────────── topics
feed  ── topic candidate IDs ───────────────────────────> topics
feed  ── bounded engagement counts ─────────────────────> votes
feed  ── batch post DTOs in requested order ────────────> posts
```

Only top-level `TopicService` interfaces and DTOs cross those boundaries. No domain imports another
domain's `model`, `repository` or implementation package.

- `topics` owns taxonomy, assignments, classification jobs, user-interest rows and review audit.
- `posts` accepts `topicIds`, supplies the immutable classification input and batch-assembles topic
  summaries into `PostDto`.
- `feed` owns `/feed/topics/{topicId}` because it assembles ranked/read post DTOs. The topic domain
  returns eligible candidate IDs; it does not reach into post persistence or decide rank order.

Store `postCreatedAt` on the assignment so category candidate reads and deterministic tie-breaking
do not query the `posts` table. Post creation timestamps are immutable, so this denormalisation has
one writer and no sync problem. The feed domain stores per-user impression history separately; it
must never appear in public post, topic, vote or characteristic DTOs.

## Persistence and migration

Create the next migration (currently `0009-create-topic-system.xml`) with:

### `topic_group`

- `id` varchar primary key, `display_name` and `sort_order`; and
- nine governed display rows matching the catalogue above.

Groups organise the client catalogue only; they are not selectable topic IDs.

### `topic`

- `id` varchar primary key;
- `display_name`, `description`, `group_id` foreign key and topic sort order;
- `status` (`ACTIVE` or `RETIRED`); and
- `created_at`, `updated_at`.

The 88 initial taxonomy rows ship in this schema migration, not the optional seed context.

### `post_topic`

- composite primary key `(post_id, topic_id)` and foreign keys to `post` and `topic`;
- `post_created_at` for stable browse ordering;
- `source` (`AUTHOR`, `INFERRED`, `ADMIN`);
- nullable `inference_basis`, `confidence`, `classifier_version` and rule-ID JSON;
- `review_state` (`NOT_REQUIRED`, `UNREVIEWED`, `VERIFIED`, `HIDDEN`); and
- created/updated/reviewed timestamps.

Constraints enforce inferred confidence in `[0,1]`, inference metadata only on inferred rows, and
one effective row per post/topic. Index `(topic_id, review_state, post_created_at desc, post_id desc)`
for browse and `(post_id, review_state)` for batch post decoration.

### `topic_classification_job`

- identity, `post_id`, classifier version and content hash;
- minimal input snapshot as JSONB;
- nullable result summary JSONB containing raw inferred canonical IDs, author/inference overlap counts
  and agreement category, but no post text;
- `status` (`PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`), attempts and next-attempt time;
- lease timestamps, bounded last-error detail and created/updated timestamps; and
- uniqueness on `(post_id, content_hash, classifier_version)`.

Index pending work by `(status, next_attempt_at)`. Worker leasing must be safe across multiple
`post-service` instances.

### `user_topic_interest`

- composite primary key `(user_id, topic_id)`;
- `selected_at`, `selected_via` (`ONBOARDING` or `SETTINGS`) and required `ordinal` for stable
  display of the user's choices;
- foreign key to local `topic`, but no database foreign key into `user-service` tables.

The service resolves a valid user through `user-service`, validates zero to seven distinct active
IDs, takes a per-user transaction lock, then replaces that user's set atomically. The composite key
prevents topic duplicates; `ordinal` is constrained to `0..6` and unique per user, so the database
also makes an eighth row impossible. Rows are private and never joined into vote or aggregation
queries.

### `feed_post_impression`

- composite primary key `(user_id, post_id)` with no foreign key into `user-service`;
- `first_impressed_at`, `last_impressed_at` and bounded `impression_count`; and
- index `(user_id, last_impressed_at desc)` for retention-bounded seen-candidate lookup.

The feed domain upserts this row only from an authenticated display-impression call. A scheduled
retention job deletes expired rows in bounded batches. Impression history is operational
personalisation data, not public engagement: it is never returned through topic, post, profile,
vote or characteristic APIs and does not contribute to the post's popularity score.

### `topic_review_audit`

- assignment identifiers, action, admin subject, optional bounded note and timestamp.

Seed data adds representative author and inferred assignments for existing seeded posts. A one-off
backfill queues every existing real post through the normal idempotent job path; it does not write
classification results directly.

## HTTP and DTO contract

All user endpoints require `user`; review endpoints require `admin` server-side.

### Taxonomy

`GET /topics` returns active topics in display order:

```json
[
  {
    "id": "legislation",
    "label": "Legislation",
    "description": "Laws and law-making",
    "groupId": "politics-government",
    "groupLabel": "Politics & government"
  }
]
```

### Onboarding/settings interests

`GET /topics/interests` returns only the authenticated user's current canonical topic summaries.

`PUT /topics/interests` treats the body as a complete replacement:

```json
{
  "topicIds": ["housing", "artificial-intelligence", "football"],
  "entryPoint": "ONBOARDING"
}
```

Zero to seven distinct active IDs succeed and return the effective ordered list. Eight IDs,
duplicates, unknown IDs, retired IDs or an unknown entry point return domain `400`. `entryPoint` is
bounded analytics context, not an authorisation input. The user is derived from the bearer token;
no request or response exposes another user's interests.

### Create/read posts

Extend `POST /posts` with an optional `topicIds` array. More than three, duplicates, unknown IDs or
retired IDs return a domain `400`; an omitted/empty array remains valid for old clients.

```json
{
  "summary": "...",
  "supportQuestion": "...",
  "media": [],
  "topicIds": ["housing", "local-government"]
}
```

Extend `PostDto` with effective topics, batch-loaded for list/feed responses:

```json
"topics": [
  { "id": "housing", "label": "Housing" }
]
```

All post read paths—single post, author profile, recent feed, Following/Latest feed and category
feed—must return the same topic shape. Batch lookup is required; do not introduce one topic query
per post.

### Topic browsing

The first page uses `GET /feed/topics/{topicId}?size=5`; subsequent pages send the opaque
`nextCursor` returned by the previous response:

```json
{
  "topic": { "id": "legislation", "label": "Legislation" },
  "posts": [],
  "nextCursor": null,
  "hasMore": false,
  "viewerInterest": true
}
```

Return category-ranked posts with an opaque stable cursor that carries the ranking snapshot/tie-break
needed to avoid duplicates while popularity changes. `(postCreatedAt, postId)` is the deterministic
final tie-break. Unknown/retired topics are `404`; a valid topic with no posts is `200` with an empty
list.

`POST /feed/impressions` accepts one or more actually displayed post IDs plus bounded surface
context (`CATEGORY` and its canonical `topicId` here). The backend derives the viewer from the
bearer token, validates the posts, and idempotently upserts private impression state. Prefetching a
category response must not call this endpoint; the mobile viewability rule that constitutes
“displayed” is fixed in the implementation contract and tested before release.

### Admin review

- `GET /admin/topics/assignments?reviewState=UNREVIEWED&page=0&size=25`
- `PATCH /admin/topics/posts/{postId}/{topicId}` to `VERIFY`, `HIDE` or `RESTORE`
- `POST /admin/topics/posts/{postId}` to add a canonical admin assignment
- `POST /admin/topics/posts/{postId}/reclassify`

Admin responses may include classifier metadata and rule IDs, but never user topic interests or
vote/user-characteristic data.

## Backend workstream

1. Add the migration, taxonomy rows and representative seed assignments.
2. Create the `topics` public face (`TopicController`, `TopicService`, DTOs) and private
   `model/`, `service/` and `classifier/` implementation packages.
3. Implement authenticated interest read/replace with an authoritative seven-topic limit,
   transactional replacement and no cross-service database join.
4. Implement canonical-topic validation and merge rules: `HIDDEN` review state wins; existing
   `AUTHOR`/`ADMIN` assignments are never overwritten; only an unreviewed `INFERRED` row may be
   refreshed by inference.
5. Extend post create/read contracts and implement a single batch topic lookup for post lists.
6. Add the idempotent classification job writer, worker, retry policy, versioned rules and fixture
   corpus. Keep the classifier pure so most tests need no Quarkus boot.
7. Add topic candidate paging to `TopicService`, bounded topic engagement inputs from `votes`,
   ordered batch post reads to `PostService`, impression persistence/reporting in `feed`, and
   assemble `/feed/topics/{topicId}` with `CategoryPopularityRanker`.
8. Add admin review/audit endpoints.
9. Extend `FeedContext`/`RankablePost` with category popularity and seen-post signals while keeping
   the Following/Latest rank order unchanged.
10. Implement the metrics, traces, dashboards, alerts and structured logging contract below.

## Frontend workstream

Create `features/topics/` with `index.ts` as its only public face.

1. Add topic DTOs/services for taxonomy, interests, browse and admin review APIs.
2. Build a reusable grouped/searchable topic selector with configurable limits. It shows the
   selected count, disables only unselected items at the limit and keeps selected items removable.
3. Append a thirteenth final step to `OnboardingScreen` using the topics feature's public
   `TopicInterestPicker`. Extend the resumable draft container with separate `interestTopicIds`,
   keeping them out of `OnboardingForm` and `CharacteristicAnswers`. Replace the usual aggregate
   privacy note with accurate private personalisation copy on this step.
4. Final submission saves the complete interest set—including empty—through the idempotent
   replacement API with `entryPoint=ONBOARDING` before saving characteristics. Keep the draft on
   either failure. “Skip for now” submits an empty replacement rather than silently bypassing
   persistence.
5. Add a Settings → Topics of interest screen using the same seven-topic picker so existing and new
   users can edit choices without repeating characteristics onboarding; its replacements use
   `entryPoint=SETTINGS`.
6. Add `TopicPicker` to manual post creation: searchable controlled options, selected chips, a
   three-topic limit and accessible remove controls. It submits IDs only.
7. Add compact `TopicChip`/`TopicChipRow` rendering to every post layout without displacing the
   always-visible support question and vote controls. Chips navigate to the topic route.
8. Add thin route `app/(protected)/topics/[topicId].tsx`, backed by `TopicFeedScreen`, with title,
   ranked infinite paging, display-impression reporting, refresh and empty/loading/error/retry
   states.
9. Add a minimally linked admin-only review route. Decode roles for presentation, but rely on the
   backend `admin` check for security. The screen shows pending assignments, reason IDs and
   verify/hide/replace/re-run actions.
10. Use editorial theme tokens and domain public exports only; route files remain composition-only.

## Observability contract

Build a domain-specific `TopicMetrics` wrapper over the existing Micrometer/OpenTelemetry setup.
The generic `DomainMetrics` continues to report HTTP operation rates, latency and error codes;
`TopicMetrics` adds bounded product and worker signals. Metric names and tags are contracts: tests
pin them so dashboards do not silently break.

### Interest-selection metrics

The replacement service diffs the committed old and new sets, then records metrics only after the
transaction succeeds:

| Metric | Type and bounded tags | Purpose |
| --- | --- | --- |
| `yoursay.topics.interest.changes` | Counter: `topic_id`, `group_id`, `action=added|removed`, `entry_point=onboarding|settings` | Rank topics selected during onboarding over any time window and distinguish later edits. |
| `yoursay.topics.interest.current` | Gauge: `topic_id`, `group_id` | Current number of users selecting each topic. |
| `yoursay.topics.interest.set.size` | Distribution summary: `entry_point` | Distribution of 0–7 selections and onboarding skip rate. |
| `yoursay.topics.interest.replace` | Counter: `entry_point`, `outcome=success|client_error|server_error`, bounded `error_code` | Save reliability and validation failures. |
| `yoursay.topics.interest.replace.duration` | Timer: `entry_point`, `outcome` | Interest-save latency. |

`topic_id` is safe as a metric label because it is restricted to the 88 canonical values. Never add
`user_id`, email, token subject, post ID, free text, the selected set or exception messages as metric
labels. Refresh the current-count gauge from one grouped database query at most once per minute;
because every instance sees the same values, Grafana uses `max by (topic_id, group_id)`, not `sum`,
to avoid double-counting across replicas.

The change counter measures committed add/remove events, not unique people; an idempotent retry emits
no second event. The current gauge is the unique current-user count because `(user_id, topic_id)` is
the table primary key. Show both together so repeated edits cannot be mistaken for current reach.

The onboarding dashboard must answer, without a database query:

- Which topics were added most often during onboarding over the selected hour/day/week?
- How is each topic trending compared with the previous equal period?
- What are the current top topics and distribution by display group?
- How many users choose 0 through 7 interests?
- What percentage of interest replacements fail, and why?

The primary Grafana query is the equivalent of
`topk(10, sum by (topic_id) (increase(yoursay_topics_interest_changes_total{entry_point="onboarding", action="added"}[$__range])))`.
Dashboard queries must use rates/increases for counters so process restarts do not corrupt trends.

Interest-save error codes are a closed enum such as `NONE`, `TOO_MANY_TOPICS`, `DUPLICATE_TOPIC`,
`UNKNOWN_TOPIC`, `RETIRED_TOPIC`, `INVALID_ENTRY_POINT`, `USER_LOOKUP_FAILED`, `WRITE_CONFLICT` and
`INTERNAL_ERROR`. Adding a code requires updating the dashboard/test allow-list.

### Classification, browse and moderation metrics

- Classification jobs: queue depth, oldest pending age, queued/succeeded/retried/terminal-failed
  totals, attempts, duration and classifier version.
- Classification output: classified versus valid-unclassified posts, inferred assignments per post,
  aggregate confidence distribution and assignment counts by canonical topic/source.
- Author/inference agreement: compare the classifier's raw above-threshold output with the author's
  selected set before merge precedence removes duplicates. Record:
  - `yoursay.topics.classification.author.agreement` per post with bounded tags
    `agreement=exact|partial|none|no_author_topics` and `classifier_version`;
  - `yoursay.topics.classification.author.topic.comparison` per topic with bounded tags `topic_id`,
    `result=matched|author_only|inferred_only` and `classifier_version`; and
  - a Jaccard-overlap distribution (`intersection / union`) by classifier version for posts that
    have at least one author topic.
- Topic browsing: requests, empty results, errors and latency by canonical topic.
- Category ranking: candidate/unseen/seen counts, recycled-seen responses, score-age distribution
  and impression-report success/failure by canonical topic or bounded error code. Never use user or
  post IDs as metric labels.
- Moderation: pending review count and verify/hide/restore/reclassify totals by canonical topic.
- Topic decoration/feed assembly: batch lookup duration, missing-assignment count and assembly
  failures so an N+1 or partial-read regression is visible.

Keep tag values bounded enums or canonical IDs. Job IDs, post IDs and stack traces belong in logs,
not metric labels.

Agreement definitions are exact: `exact` means both non-empty sets are identical; `partial` means
their intersection is non-empty but the sets differ; `none` means both were compared with an empty
intersection; `no_author_topics` is reported separately and excluded from agreement-rate
denominators. Per-topic comparison counters are emitted only when the author supplied at least one
topic. An idempotent job retry must not increment these counters again.

Author selections are a useful human comparison signal, not labelled ground truth: an author may
choose an incomplete or overly broad set, while inference may correctly add another topic. Name
dashboard panels “agreement” or “overlap,” never “accuracy,” “precision” or “false positive.” Persist
the raw inferred IDs and overlap summary on the classification job result (no post text) so a metric
can be audited after the fact even when an inferred match is merged into an existing `AUTHOR` row.

### Structured logs and traces

Emit JSON logs with stable fields: `domain=topics`, `operation`, `outcome`, `error_code`, trace/span
IDs and, where relevant, classifier version, job ID, post ID, attempt, selected count and
added/removed counts. Interest-save logs may record counts and `entry_point`, but never user ID,
email, token subject or the topic IDs selected by that individual.

- Expected validation failures log once at `INFO`/`WARN` with a bounded domain error code and no
  stack trace.
- Retryable worker failures log at `WARN` with attempt/next-attempt; terminal failures log once at
  `ERROR` with the exception stack.
- Do not log bearer tokens, request bodies, post text, classifier input snapshots, matched text,
  individual interest sets or admin notes.
- Avoid duplicate exception logs: the layer that owns retry/translation logs the error once.

Create spans for `topics.interests.replace`, `topics.classify`, `topics.browse` and admin review.
Span attributes follow the same privacy/cardinality rules as metrics. Cross-service user resolution
and post assembly remain child spans so Grafana/Tempo exposes where latency and failures originate.

### Dashboards and alerts

Add `grafana/dashboards/your-say-news-topics.json` with two dashboard sections:

1. **Topic interests** — onboarding additions over time, current top topics, group share, set-size
   distribution, zero-selection rate, save throughput/error codes and p50/p95 latency.
2. **Topic system health** — classification queue depth/oldest age, retry/failure rate, duration,
   valid-unclassified rate, exact/partial/no author agreement over time and by classifier version,
   per-topic match/author-only/inferred-only counts, assignments by topic, browse latency/errors and
   moderation hide rate.

Show both exact agreement and any-overlap rate, where any-overlap is
`(exact + partial) / (exact + partial + none)`. For each canonical topic, show
`matched / (matched + author_only)` as author-topic coverage. Keep `inferred_only` visible alongside
it, but do not label that count as wrong.

The release environment must retain interest counters for at least 90 days (or remote-write them to
equivalent long-term storage); otherwise “over time” comparisons are not reliable. Local LGTM data
is useful for development but is not the product analytics retention plan.

Initial alerts:

- interest replacement server-error ratio above 2% for 10 minutes;
- interest replacement p95 above 750 ms for 15 minutes;
- oldest pending classification job above 5 minutes;
- terminal classification failures or retry rate above the configured baseline; and
- a sharp change in valid-unclassified, author-agreement or moderation-hide rate compared with the
  previous period. Agreement alerts require a minimum comparison sample so low volume does not
  create noise.

Treat trend alerts as starting thresholds and tune them with seed/load data before release. Missing
telemetry is also a failure: alert when the worker is enabled but its queue/heartbeat series vanish.
Provision alert rules under `grafana/provisioning/alerting/` and mount them in Compose alongside the
existing dashboard provisioning so local behavior matches the checked-in configuration.

## Test strategy and release gates

Write both unit and integration tests, then run the `test-audit` skill after modifying each suite.

### Backend unit tests

- exact classifier outputs for every fixture, including multi-label and no-match cases;
- ambiguous negatives such as invoice `bill`, birthday `party` and computer `virus` versus health;
- deterministic confidence, threshold and maximum-topic behaviour;
- exact, partial, none and no-author agreement classification plus exact Jaccard values;
- author/admin/hidden precedence across reclassification;
- job idempotence, retry ceiling and stale-lease recovery;
- agreement metrics emit once only after a job result commits, including when a matching inferred
  topic is merged into an existing author assignment;
- interest replacement with zero, seven, duplicate and eight-topic inputs;
- concurrent interest replacements never merge into an invalid set or exceed seven rows;
- successful interest diffs increment the exact topic/action/entry-point counters once, while a
  rolled-back replacement emits no product metric;
- metric-tag validation rejects unbounded/non-canonical topic and error-code values;
- unchanged chronological/follow ordering when topic signals are present.

### Backend integration tests

- migration constraints and required taxonomy rows against Testcontainers Postgres;
- authenticated interest read/replace, exact seven-topic persistence, complete replacement and
  isolation between two users;
- `400` for duplicate, unknown, retired, invalid-entry-point or eight-topic interest requests, with
  the previous valid set left unchanged;
- create with canonical topics, read round-trip and create-without-topics compatibility;
- `400` for duplicate, unknown, retired or over-limit selections;
- successful async inference and a forced-failure retry without duplicate assignments;
- author/inference exact, partial, none and no-author fixtures produce the expected persisted result
  summaries and exact metric deltas;
- existing-post backfill through the job path;
- topic browse ordering, pagination, empty valid topic and hidden-assignment exclusion;
- category popularity is calculated within the requested topic, recency changes the expected order,
  deterministic ties stay stable, and a popularity update between pages causes no duplicate;
- an actually displayed post is suppressed on the next category request, a merely prefetched post
  is not marked seen, and exhausted categories recycle seen posts only in penalised order;
- consistent topic decoration across all post/feed endpoints;
- `/q/metrics` exposes the expected interest, job and error series after representative operations,
  with exact counter deltas and no identity/high-cardinality labels;
- current-interest gauges match the seeded database counts and are not summed across replicas in
  the dashboard query;
- validation, retryable and terminal failures produce the expected structured log level/error code
  without tokens, emails, user IDs, post text or selected-topic lists;
- `403` for non-admin review calls plus audit rows for successful admin actions.

### Frontend tests

- final onboarding step loads the grouped catalogue, searches, selects/removes topics and prevents
  an eighth selection;
- topic IDs never appear in the submitted `CharacteristicAnswers` payload;
- selected interests save before characteristics, both calls are retry-safe, and failures retain
  the local draft;
- “Skip for now” sends an empty interest replacement before completing characteristics;
- Settings loads and completely replaces the current user's interests;
- picker loads only canonical options, enforces three and submits IDs;
- chips render and navigate in both portrait and stacked post layouts;
- category feed ranking, pagination, display-impression reporting, repeat suppression, exhausted
  inventory fallback, refresh, empty, error and retry behaviour;
- admin controls are not linked for ordinary users and surface API failures safely.

### Ship gates

- No request or table can create a topic outside the canonical taxonomy.
- A user can persist zero to seven interests but never eight, and one user's interests are never
  returned to another user.
- Topic interests are absent from characteristic DTOs, vote snapshots, public profiles and
  sentiment breakdowns; the final onboarding step does not repeat the aggregate-only privacy claim.
- Publishing succeeds when inference is disabled or failing.
- Reprocessing the same post/version creates no duplicate assignments.
- Human-hidden assignments remain hidden after a worker run.
- Category feed queries are indexed and perform a bounded number of reads per page.
- Popularity is topic-relative and time-bounded; unseen posts outrank seen posts whenever unseen
  inventory exists, and impression history is private and retention-bounded.
- The classifier fixture corpus has positive and ambiguous-negative coverage for every topic, and
  the legislation acceptance pair classifies exactly as specified.
- Existing Following/Latest ranking tests pass unchanged; category ranking tests pin exact order for
  popularity, recency, deterministic ties and seen-post suppression.
- No public response exposes classifier evidence, another user's interests, admin identity, vote
  identity or characteristic rows.
- Grafana dashboards load with working queries for onboarding-topic trends, current popularity,
  interest-save errors, author/inference agreement and classification health; alert rules parse and
  reference existing series.
- Telemetry inspection confirms no user identity, bearer token, post body or individual interest set
  appears in topic metrics, logs or traces.

## Delivery order

Deliver in reversible vertical slices:

1. **Contract and data:** ADR, taxonomy, migration, DTOs and classifier fixtures.
2. **Onboarding interests:** private interest API/table, final wizard step, resumable two-service
   submission, Settings editor, aggregate interest metrics and dashboard panels.
3. **Author topics:** create validation, post decoration, picker and chips.
4. **Inference:** job path, deterministic classifier, retries, worker metrics/logging and
   existing-post backfill.
5. **Discovery:** category popularity ranker, impression history, topic feed endpoint and mobile
   browse screen.
6. **Control:** admin moderation/audit.
7. **Feed seam and hardening:** prove Following/Latest ordering is stable, category pagination does
   not replay posts and impression retention is bounded,
   validate dashboard/alert provisioning, run full backend/frontend suites and complete the test
   audits.

Use `topics.inference.enabled` to deploy storage and explicit topics before activating workers. Do
not build a “For You” page until a later ADR defines weights, diversity constraints and an offline
comparison against both the chronological/follow and category-popularity baselines.
