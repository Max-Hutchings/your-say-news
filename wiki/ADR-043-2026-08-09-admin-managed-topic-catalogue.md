# ADR-043: Admin-managed topic catalogue and topic-filtered feeds

Date: 2026-08-09

## Situation

Topics are the last unimplemented pillar of MVP1 discovery. `ADR-020` designed the full system —
an 80+ ID canonical taxonomy, private user interests, an asynchronous classifier with provenance and
review state — and `ADR-021` designed category feeds ranked by topic-relative popularity with private
impression suppression. None of it is built. There is no `topics` package, no migration and no topic
column; the mobile feed's category strip (`FeedTabs`) is five hardcoded, inert chips.

We want discovery working now, at a fraction of that scope. Three facts shape the decision:

- `ADR-042` has just given the feed keyset cursor pagination with filtering pushed into SQL. A feed
  restricted to one topic is now one additional predicate on an existing indexed scan, not a new
  ranking subsystem.
- `ADR-020` states that adding or retiring a topic is "a reviewed taxonomy migration". In practice a
  news product needs a new topic on the day a story breaks, not on the next deploy.
- `ADR-020`'s catalogue is reference data the application cannot function without, but this
  repository has never shipped data to hosted environments. `liquibase/changelog/db/seeding/` is
  applied only by the `liquibase-seed` container under context `seed`, which hosted environments do
  not run, and `CLAUDE.md` forbids putting inserts in a schema changeSet.

## Options considered

**On who owns the catalogue**

1. Free-text topics authored by users at post time.
2. A fixed list compiled into the application as a Java enum.
3. A fixed list in a `topic` table, seeded on migration, extended by admins at runtime.

**On how the catalogue reaches hosted environments**

1. `seeding/`, plus a new mechanism to make hosted environments run the seed container.
2. `migrations/`, as a changeSet separate from the DDL changeSet in the same file.
3. Application-startup reconciliation of a checked-in resource file against the table.

**On ranking inside a topic**

1. Build `CategoryPopularityRanker` now, per `ADR-021` — topic-relative popularity with recency decay
   plus impression suppression.
2. Reuse the existing `ChronologicalFollowBoostRanker` unchanged and filter the candidate set.

## Decision

**A fixed, governed catalogue in a `topic` table, extended by admins at runtime.** Posts carry up to
three topics through a `post_topic` join table with a foreign key to `topic`, so an unknown topic ID
cannot be written. Topics are retired by clearing an `active` flag, never deleted, so historical
assignments stay intelligible.

**This reverses `ADR-020`'s "adding a topic is a reviewed migration".** Admins add topics through a
new page in the existing admin UI (`GET`/`POST /api/admin/topics`, `admin` realm role). The catalogue
remains controlled — there is still no public arbitrary-label path, and `CreatePostRequest` accepts
canonical IDs only — but the control point moves from the deploy pipeline to an authorised human.
A new topic is created with `display_order = max + 1`, so it appears in the dropdown rather than
displacing a curated tab.

**The catalogue ships as reference data in `migrations/`,** as its own changeSet alongside — never
inside — the DDL changeSet that creates the tables. Reference data is distinguished from seed data:
the application is broken without it in every environment, exactly like an enum, whereas seed data
exists only to populate a developer's machine. `CLAUDE.md`'s database section is amended to record
the distinction; the "no seed inserts in a schema changeSet" rule is unchanged.

**MVP1 starts with 20 topics, not `ADR-020`'s 80+.** IDs reuse `ADR-020`'s exact spelling so growth is
purely additive; `sport` is the single new ID, replacing that ADR's ten sporting disciplines, which
are far too fine-grained at our post volume. `display_group` is retained from `ADR-020` to group the
picker.

**Topic feeds reuse `ChronologicalFollowBoostRanker` unchanged.** `GET /feed?topic={id}` adds one
`exists` predicate against `post_topic`, served by `idx_post_topic_topic (topic_id, post_id)`. The
scan order stays `created_at desc, id desc`, so `ADR-042`'s cursor semantics and gap-free guarantee
carry over unmodified. **`FeedRanker`, `FeedContext` and `RankablePost` do not change.**

Explicitly deferred, all still governed by `ADR-020` and `ADR-021`: the classifier and its inference
jobs, private user topic interests, impression history and repeat suppression, topic-relative
popularity ranking, and moderation of existing assignments.

## Reason

Free-text topics defeat the purpose of topics. "AI", "A.I." and "Artificial Intelligence" become
three feeds holding a third of the posts each, and the value of a topic is concentration. They are
also user-generated content, which brings abuse, spam and a merge/rename tooling burden with no
offsetting product gain.

A database table beats a Java enum because the foreign key is what makes the fixed list enforceable
at the data layer, and because it is the only option that permits runtime extension without a
deploy. Runtime extension is worth reversing `ADR-020` for: the cost of a missing topic is a story
nobody can find on the day it matters, and the risk it introduces — a badly named topic — is
contained by admin-only access and reversible through retirement.

Reference data belongs in `migrations/` because that is the changelog every environment runs, via
both the application's `migrate-at-start` and the `liquibase-migrate` container. Routing it through
`seeding/` would mean teaching hosted environments to run a container built for developer fixtures,
which conflates two things this repository has deliberately kept apart. Startup reconciliation was
rejected because it would fight the admin-managed table on every deploy.

Twenty topics rather than eighty because an empty topic feed is a worse experience than an absent
one. At current post volume, eighty topics would produce roughly seventy-five dead ends. Growing the
list costs one migration or one admin action; shrinking it does not, since `ADR-020` requires
retirement over deletion and a retired topic persists in historical assignments forever.

Reusing the chronological ranker is the point of the `FeedRanker` seam paying off. Topic-relative
popularity needs engagement volume per topic that we do not yet have, and it would require the
impression storage, decay constants and ranking-snapshot cursor that `ADR-021` correctly insists must
be pinned by tests first. Filtering an already-correct feed is the honest version of the feature we
can ship now.

## Consequences

- `post-service` gains a reactive `topics` domain. Topic validation and page decoration run inside
  the existing Hibernate Reactive `posts` and `feed` pipelines, so the domain shares their session
  and transaction model without blocking or joining across persistence models.
- `PostDto` gains a `topics` list, decorated by one batched query per page. Any new post-listing path
  must use that batch and not reintroduce an N+1, which `ADR-042` has just removed.
- `CreatePostRequest.topicIds` is validated against active topics; an unknown or retired ID is a
  `400` rather than a silent drop, matching `ADR-042`'s treatment of a malformed cursor.
- An unknown `?topic=` on the feed is a `400`, not an empty page, so a client bug is visible.
- The admin UI gains its second page and therefore top-level navigation. Two pages are switched by
  local state in `App.tsx`; a router is deferred until there is a third.
- The follow-boost is page-local per `ADR-042`, and remains so inside a topic feed.
- Existing posts have no topics until backfilled. A dev-only seeding changeSet assigns topics to
  seeded posts; production posts predating this change stay untopiced until the deferred classifier
  or an author edit assigns them.
- Because admins can create topics, telemetry and any future classifier fixtures must treat the topic
  ID set as unbounded at runtime. Metric labels must come from a bounded allow-list, not from the
  live table, or cardinality will grow without limit.
- A later ADR is still required before topic interests or topic-relative popularity affect ranking,
  and before any cross-category "For You" surface.
