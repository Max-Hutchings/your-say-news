# Topics and category feeds

Implementation plan for the first topics slice. Adds a governed topic list, up to three topics per
post, and a feed per topic that reuses the existing feed algorithm with one extra SQL filter.

Supersedes part of [ADR-020](../../wiki/ADR-020-2026-07-13-controlled-canonical-topic-taxonomy.md)
(see "ADR needed" below). Builds on
[ADR-042](../../wiki/ADR-042-2026-08-09-keyset-feed-candidate-pagination.md), which already gave the
feed gap-free keyset pagination and SQL-level filtering.

## Scope

**In:** topic catalogue + migration, `post_topic` (max 3), topic filter on the feed, mobile tab strip
with a "More" dropdown, author topic picker on create-post, admin list/add page, seed backfill.

**Out:** the classifier and inference jobs, private user interests, impression history, topic-relative
popularity ranking, topic moderation of existing assignments. Category feeds use the **existing**
`ChronologicalFollowBoostRanker`, not `CategoryPopularityRanker`.

## The 20 topics

Canonical IDs reuse ADR-020's spelling so expanding to its full 80+ list later is additive.
`sport` is the one new ID (ADR-020 splits sport by discipline, which is too fine at this volume).

| Display group | ID | Label |
| --- | --- | --- |
| Politics & government | `politics` | Politics |
| Politics & government | `elections` | Elections |
| Politics & government | `immigration` | Immigration |
| World affairs | `international` | World |
| World affairs | `war-conflict` | War & conflict |
| Money & business | `economy` | Economy |
| Money & business | `cost-of-living` | Cost of living |
| Money & business | `business` | Business |
| Money & business | `jobs-work` | Jobs & work |
| Society | `housing` | Housing |
| Society | `health` | Health |
| Society | `education` | Education |
| Society | `crime` | Crime |
| Science & technology | `technology` | Technology |
| Science & technology | `artificial-intelligence` | AI |
| Climate & environment | `climate-change` | Climate |
| Climate & environment | `energy` | Energy |
| Transport & places | `transport` | Transport |
| Culture & life | `arts-culture` | Culture |
| Sport | `sport` | Sport |

## Schema and migration

`liquibase/changelog/db/migrations/0014-add-topics.xml` — **two changeSets in one file**, so the DDL
and the catalogue rows stay separable but ship together.

```
0014-create-topic-tables   (DDL)
0014-seed-canonical-topics (the 20 rows)
```

### Why the rows go in `migrations/`, not `seeding/`

The user requirement is that hosted environments get these rows. `seeding/` is dev/test data applied
by the `liquibase-seed` container under context `seed`; hosted envs never run it. `migrations/` is
applied by both the app's `migrate-at-start` and the `liquibase-migrate` container, everywhere.

CLAUDE.md says "never mix seed-data inserts into a schema changeSet". This is **reference data**, not
seed data - the app is broken without it, exactly like an enum. Keeping it as its own changeSet
honours the rule's intent. **CLAUDE.md's database section needs a line recording this distinction.**

### `topic`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `varchar(64)` PK | canonical ID, never changes |
| `label` | `varchar(80)` not null | product copy, editable |
| `display_group` | `varchar(64)` not null | groups the dropdown; not selectable itself |
| `display_order` | `int` not null | tab-strip order; admin additions get `max + 1` |
| `active` | `boolean` not null default true | retire, never delete |
| `created_at` | `timestamptz` not null | |

Check constraint: `id ~ '^[a-z0-9-]{2,64}$'`.

### `post_topic`

| Column | Type | Notes |
| --- | --- | --- |
| `post_id` | `bigint` not null | FK → `post(id)`, on delete cascade |
| `topic_id` | `varchar(64)` not null | FK → `topic(id)`, **no** cascade |
| `created_at` | `timestamptz` not null | |

- PK `(post_id, topic_id)` - dedupes assignment and serves post → topics.
- **Index `idx_post_topic_topic (topic_id, post_id)`** - serves the category feed's `exists` lookup.
- Max 3 enforced in the service and by `@Size(max = 3)` on the request, not by the DB.

The FK to `topic` is what makes a fixed list real: an unknown topic ID cannot be written.

## ADR needed: ADR-043

ADR-020 states "Adding or retiring a topic is a reviewed migration". The requirement here is that
**admins add topics at runtime through the admin UI**. That is a direct reversal and must be
recorded, along with the reference-data-in-migrations decision and the 20-topic starting subset.

`wiki/ADR-043-2026-08-09-admin-managed-topic-catalogue.md`, covering: fixed catalogue over
user-created topics; catalogue ships as reference data in `migrations/`; admins extend it at runtime;
retirement via `active` rather than delete; category feeds reuse the chronological ranker until a
later ADR introduces topic-relative popularity.

## Backend

### `topics` domain (new, `com.yoursay.topics`)

Follows the standard shape - public face at the top, everything else internal.

```
com.yoursay.topics/
  TopicController.java        GET  /topics                     (role: user)  - active topics, ordered
  AdminTopicController.java   GET  /api/admin/topics            (role: admin) - all, incl. retired
                              POST /api/admin/topics            (role: admin) - create
  TopicService.java           interface: list, listForAdmin, create, assign/read post topics
  dto/TopicDto.java           id, label, displayGroup, displayOrder, active
  dto/CreateTopicRequest.java id, label, displayGroup  (@Pattern on id, @NotBlank label)
  model/Topic.java, TopicRepository.java, PostTopic.java, PostTopicRepository.java
  service/TopicServiceImpl.java
```

`@RunOnVirtualThread`, imperative - matching `AdminUserController`, not the reactive feed path.
Create rejects a duplicate ID with `409` and assigns `display_order = max + 1`, so a new topic lands
in the dropdown rather than jumping into the tab strip.

### `posts` changes

- `CreatePostRequest` gains `@Size(max = 3) List<String> topicIds` (optional).
- `PostDto` gains `List<TopicDto> topics` so feed cards can render chips without a second call.
- `PostServiceImpl.create` validates every ID exists and is active, then writes `post_topic`.
  Unknown or retired ID → `400`, never a silent drop.
- Post reads decorate topics in one batched query keyed by post ID - no N+1 per feed page.

### `feed` changes

The whole point of this design: **one new filter, no new ranker.**

- `PostPageRequest` gains `String topicId` (null = all topics).
- `PostRepository.findPageAfter` adds one condition when it is set:
  `exists (select 1 from PostTopic pt where pt.post = p and pt.topicId = :topicId)` - served by
  `idx_post_topic_topic`.
- `FeedController` gains `@QueryParam("topic")`, passed straight through to `PostPageRequest`.
- `FeedRanker`, `FeedContext` and `ChronologicalFollowBoostRanker` are **unchanged**. Cursor
  semantics are unchanged, because the scan order is still `createdAt desc, id desc`.
- An unknown topic ID → `400` (consistent with the invalid-cursor behaviour), not an empty feed.

## Frontend - mobile

### `FeedTabs` becomes functional

Keep the current design exactly: mono-type rounded chips, active chip filled with lime.

- Fetch topics from `GET /topics` once, cache in the feature.
- Tabs render: `For you` (no filter, default active) + the first 4 topics by `displayOrder` + a
  trailing **`More ▾`** chip.
- `More` opens a modal sheet listing **all** topics, sectioned by `displayGroup`. Selecting one
  applies it as the active filter; the selected topic is swapped into the strip so the active chip
  is always visible.
- Selecting a tab resets the cursor and refetches - same code path as the existing type filter.

`HomeFeed` already owns the cursor reset for `postType`; topic joins that state. `getFeed()` in
`PostService.ts` gains a `topicId` parameter alongside `postType`.

### Create-post picker

Without this no new post gets a topic. `CreatePostScreen` gains an optional grouped picker capped at
3, with a live `n / 3` count and the 4th selection blocked client- and server-side.

### Post cards

Render effective topic chips from `PostDto.topics`. Tapping a chip switches the feed to that topic.

## Frontend - admin UI

`post-service/src/main/webui` currently renders `UsersPage` directly from `App.tsx` with no router.
Adding a second page needs top-level navigation.

- Add a nav control to `Masthead` switching between **Accounts** and **Topics**. Local state in
  `App.tsx` is enough - no router dependency for two pages.
- `src/features/topics/` - `services/topicAdminApi.ts` (reusing the `adminRequest` pattern),
  `hooks/useAdminTopics.ts`, `components/TopicLedger.tsx`, `components/AddTopicForm.tsx`.
- `src/pages/topics/TopicsPage.tsx` + `topics-page.css`, matching the existing editorial styling.
- The form takes label + display group (a select of existing groups); the ID is slugified from the
  label and shown read-only before submit, so admins cannot invent a malformed ID.

### One addition beyond the ask

A **retire toggle** on each row (`active` on/off). Without it, the first mistyped topic is permanent
and public. It is one endpoint and one switch, and the `active` column exists regardless. Say the
word and I will drop it and ship list + add only.

## Seed backfill

`liquibase/changelog/db/seeding/0012-seed-post-topics.xml` assigns 1-3 topics to the existing seeded
posts, so the tab strip shows real content in dev instead of empty topic feeds. Dev/test only - the
catalogue itself is in `migrations/`.

## Tests

Following TDD - tests first, then the code, then the `test-audit` skill.

**Backend unit**
- `TopicServiceImplTest` - duplicate ID rejected, `display_order` = max + 1, slug validation, retired
  topics excluded from the public list but present for admin.
- Extend `FeedRankerTest`? No - the ranker is untouched. That is the design working.

**Backend integration** (`@QuarkusTest` + Testcontainers)
- `TopicControllerTest` - public list returns active topics in `displayOrder`; admin create requires
  the `admin` role and a `user`-only caller gets `403`.
- `PostControllerTest` - create with 3 topics persists them; 4 topics → `400`; unknown ID → `400`;
  retired ID → `400`; `PostDto.topics` round-trips.
- `FeedControllerTest` - `?topic=housing` returns only housing posts; **paging with a topic filter is
  gap-free across pages** (the assertion that matters most); unknown topic → `400`; topic + type
  filter compose.

**Frontend**
- `FeedTabs.test.tsx` - renders 4 topics + More; selecting a tab calls back with the topic ID; the
  More sheet lists all topics grouped; a dropdown selection becomes the visible active chip.
- `HomeFeed.test.tsx` - switching topic resets paging and refetches from cursor null.
- `CreatePostScreen.test.tsx` - 4th selection blocked, count renders `n / 3`.
- `TopicsPage.test.tsx` - lists topics, add form posts and the new topic appears.

## Delivery order

1. ADR-043 + the CLAUDE.md reference-data line.
2. Migration `0014` (DDL + catalogue) and the `topics` domain with its public read endpoint.
3. `post_topic` writes: `CreatePostRequest.topicIds`, `PostDto.topics`, batched decoration.
4. Feed filter: `PostPageRequest.topicId`, repository condition, `?topic=` param.
5. Mobile: `FeedTabs` + More sheet, `getFeed` topic param, post-card chips.
6. Mobile: create-post picker.
7. Admin UI: masthead nav, topics page, list/add (+ retire).
8. Seed backfill, then full backend + frontend suites and `test-audit`.

Steps 2-4 are shippable behind no flag: with no topics assigned, `?topic=` simply returns empty and
the mobile strip keeps its current behaviour until step 5.
