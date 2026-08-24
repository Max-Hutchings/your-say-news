# ADR-049 — Widened smoke test scope

Date: 2026-08-23

## Situation

[ADR-030](ADR-030-2026-07-25-browser-smoke-test-scope.md) deliberately limited the first browser
smoke suite to five capabilities: register, sign in, characteristics, video feed, article feed. It
recorded that voting, publishing and social flows stayed out of scope until the harness had earned
trust, and that more journeys should be added only where they protect a proven critical path.

The harness has since been extended once already, by an administrator account-management journey
that ADR-030 never listed. Meanwhile the product's two core loops — publishing a post, and voting
then seeing how others voted — had no end-to-end coverage at all. Those loops are the product;
everything else in the application exists to serve them.

Two problems surfaced while widening the suite, and both had to be decided rather than worked
around.

**Reaching results goes through a model.** Casting a vote navigates to Post Unwrapped, whose story
is Grok-generated. A smoke suite that waited on a model would be slow, costly and
non-deterministic, and would fail for reasons unrelated to the product being broken.

**Twenty voters is not twenty journeys.** Proving that sentiment aggregates correctly across a
population needs a population. Driving twenty Keycloak sign-ins through the browser would add
minutes to a serial suite and make the population the single most fragile thing in it.

Separately, the existing feed journey had been failing against fixtures pointing at posts 1046 and
1049 — seeded in `0006-seed-active-account-posts.xml`, which `db.changelog-seed.xml` does not
include. The fixtures had drifted from the seed data.

## Options considered

### 1. Leave the scope as ADR-030 set it

Honest to the original decision, but leaves publishing and voting — the two things the product is
for — proven only by unit and integration tests. Those do not cover presigned upload to object
storage, the publisher-rights gate as a user experiences it, or a reader actually reaching a
characteristic breakdown.

### 2. Widen the browser journeys and drive everything through the UI

Maximum fidelity. But twenty browser-driven voters would dominate the suite's runtime and failure
surface, and reaching results would require paging through generated Unwrapped story pages, making
every voting journey depend on a model call.

### 3. Widen the journeys, and use the API for population setup only

Add follow/unfollow, the three publish shapes, and the two voting journeys as real browser
journeys. Cast the twenty background votes through the real `POST /votes` endpoint with a real
per-account token, then verify the resulting aggregate in the browser.

## Decision

Choose option 3.

**Smoke tests do not exercise LLM behaviour.** The voting journeys use two dedicated posts
(`0014-seed-smoke-journey-posts.xml`) that carry no seeded votes. `UnwrappedMilestones` only queues
a generation job at 100 votes, so these posts never reach it, no Grok call is ever made, and the
Unwrapped screen offers its deterministic "See factual results" route into the factual sentiment
view. Post Unwrapped's *generated* content remains out of smoke scope entirely.

**Population setup may use the API; the journey may not.** The twenty seeded voters
(`0010-seed-smoke-vote-population.yaml`) cast their votes through the real endpoint with a real
token each, so the aggregate the browser reads is genuinely produced by the system. What is being
verified — a reader voting and being shown the breakdown — stays entirely in the browser. This
refines ADR-030's rule that "direct API calls must not replace the browser steps of the core
journeys": the votes are not a browser step of the journey under test, they are its precondition.

**Publishing is tested as an official publisher.** `PostServiceImpl` rejects any author who is not
an active official publisher, so the compose journeys sign in as Maya Patel (OFFICIAL/ACTIVE, and
not an administrator). A fourth journey asserts the negative: a plain reader is never offered the
composer.

**Feed assertions do not pin position.** The ranker boosts posts by followed authors, and
publishing adds posts, so both the follow and publish journeys legitimately reorder the feed.
Journeys now locate a post by its own card and assert that post's content, rather than asserting
what sits at index zero. The stale 1046/1049 fixtures are repointed at the curated posts the seed
changelog actually includes.

**Follow targets author no posts.** Riley follows Nora (seeded) and does not follow Bob, giving
each direction an independent precondition. Both are deliberately users who publish nothing, so
following one mid-suite cannot silently reorder another journey's feed.

### Added capabilities

6. **Follow another user** — from a signed-out start to a persisted follow, verified after reload.
7. **Unfollow a user** — the reverse, from a seeded existing relationship.
8. **Publish an image post** — compose, attach through the real file picker, presign and upload,
   publish.
9. **Publish a video post** — the same path with a single clip.
10. **Publish a text-only post** — no media.
11. **Population sentiment breakdown** — twenty seeded accounts vote, a reader adds theirs, and the
    political-leaning breakdown they are shown carries the exact per-cohort counts those votes
    imply.
12. **Results access after voting** — voting unlocks the results route and the card shows the vote
    as spent.

## Reason

Publishing and voting are the product's two core loops and were the largest untested gaps. Both
cross boundaries no other suite covers: object-storage upload through a presigned URL, the
publisher-rights gate as a user meets it, and aggregation from vote to rendered breakdown.

The population journey is the one that earns its keep most. Asserting a whole gradient — every
left-leaning voter agreed, every right-leaning voter disagreed, the middle split — would fail loudly
if cohort assignment, snapshotting or aggregation broke, in a way a single vote never could.

Keeping the model out of the suite is what makes any of this viable as a repeatable local check.

## What widening the suite found immediately

The publish journeys failed on their first real run for a reason that had nothing to do with the
tests. `localstack/init-aws.sh` created the `post-videos` bucket with no CORS policy. The web app
uploads media with a presigned PUT straight to S3, which is cross-origin from the Expo dev server,
so the browser's preflight failed and the upload hung before it began: **publishing an image or a
video from the web app could not work locally at all**. Native uploads are not subject to browser
CORS, which is why nothing had noticed. A bucket CORS policy is now applied at init.

Any real bucket needs the equivalent policy for its own origins before web publishing ships. That
is infrastructure work this ADR does not cover, but it must not be forgotten.

Three further pieces of drift surfaced, all of the same kind — the suite asserting against things
that no longer existed:

- `scripts/smoke-test.sh` blocked startup waiting for `posts/seed-1046-video.mp4`, a key
  `init-aws.sh` never uploads. The readiness gate timed out before Playwright ever started, so the
  suite had been failing without running.
- The feed fixtures named posts 1046/1049, seeded only in a file `db.changelog-seed.xml` does not
  include, and asserted five video posts when only four exist.
- The administrator journey asserted eleven accounts; twelve already existed before this change
  added twenty more.

The lesson is recorded here deliberately: a smoke suite that cannot reach its assertions reports
nothing, and silence was mistaken for health. Keep the runner's readiness gate, the LocalStack seed
script and the fixtures naming the same objects.

## Consequences and follow-up work

- Twenty accounts now exist in both `keycloak/realm-export.json` and the user seed. They must stay
  in sync; the Compose seed job reconciles the realm on every startup.
- The suite now mutates more state (follows, published posts, votes). It remains safe because the
  runner owns a disposable Compose project and removes its volumes.
- Smoke tests must not assert on Grok output. If a journey ever needs Post Unwrapped's generated
  story, that is a separate decision and probably a separate suite.
- Publishing journeys leave posts behind within a run, which is why feed assertions no longer pin
  position. Keep it that way when adding journeys.
- Still out of scope: Post Unwrapped generated content, the auto-post agent workflow, Pepper
  drafting, topics, and profiles beyond the follow relationship.
- Native mobile automation remains a later decision; see the web-primary reasoning in ADR-030.
- No CI workflow is added. Revisit once the widened suite has proved stable locally.
