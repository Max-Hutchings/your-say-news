# Local smoke tests

These Playwright journeys exercise the Expo web application, authentication, backend and local
infrastructure together. They run against disposable local state and are not part of CI.

Scope is set by [ADR-030](../wiki/ADR-030-2026-07-25-browser-smoke-test-scope.md) and widened by
[ADR-049](../wiki/ADR-049-2026-08-23-widened-smoke-test-scope.md).

## Run

Install dependencies and the Playwright Chromium build once:

```shell
bun install
bunx playwright install chromium
```

```shell
bun run smoke
```

The command starts its own Compose project, Quarkus and Expo processes, waits for readiness, runs
the browser journeys and removes only the smoke project's containers and volumes. It uses dedicated
high local ports, so the ordinary `bun run dev` stack can remain running.
Network access is needed while LocalStack's existing seed script downloads the video fixture.

Failure evidence is written to `smoke-tests/artifacts/`, including the HTML report, trace,
screenshot, video and backend/frontend logs.

## The journeys

| Spec | Covers |
| --- | --- |
| `registration-and-onboarding` | Register a new account, accept the privacy promise, complete characteristics, reach the feed |
| `returning-reader-feed` | Sign in as an onboarded reader; video and article feeds load and render |
| `follow-another-user` | Follow a user, and unfollow one already followed; both verified after reload |
| `publish-post` | Publish image, video and text-only posts; a non-publisher is not offered the composer |
| `vote-results` | Twenty seeded accounts vote, a reader adds theirs and sees the political-leaning breakdown; voting unlocks the results view |
| `admin-account-management` | An administrator changes account type and active state, and both persist |

`registration-and-onboarding` is currently **failing for a product reason, not a test one**: at the
Finances step the income-options request returns 200 and the currency shows as selected, but no
income bands are ever rendered, so onboarding cannot be completed. Fix the screen, not the test.

## Keep these three in step

The runner's readiness gate (`scripts/smoke-test.sh`), the media seed script
(`localstack/init-aws.sh`) and `expectedFeed.video.mediaKey` must all name the same object. When
they drifted apart the gate timed out before Playwright started, and the suite reported nothing at
all rather than reporting a failure.

## What these tests deliberately do not do

**No LLM behaviour.** Voting navigates to Post Unwrapped, whose story is Grok-generated. The voting
journeys use posts held below the hundred-vote generation milestone, so no job is ever queued and
the screen offers its deterministic "See factual results" route into the factual sentiment view. Do
not add a journey that asserts on generated content.

**No position-based feed assertions.** The ranker boosts posts by followed authors, and publishing
adds posts, so the follow and publish journeys legitimately reorder the feed. Locate a post by its
card (`post-card-{id}`) and assert that post's content.

## Test accounts

| Account | Role in the suite |
| --- | --- |
| `riley.reader` | The onboarded reader. Plain USER, so it cannot publish. |
| `maya.patel` | OFFICIAL/ACTIVE publisher used by the compose journeys, and not an administrator. |
| `yoursay.admin` | The administrator for the account-management journey. |
| `smoke.voter.01` … `20` | The seeded voting population, with characteristic profiles spread across political leaning, age, region and income. |
| `bob.johnson` / `nora.new` | Follow and unfollow targets. Both author no posts, so following one cannot reorder another journey's feed. |

Seeded accounts live in both `firebase/test-accounts.json` and
`liquibase/changelog/db/user-seeding/`. They must stay in sync; the Compose seed job reconciles the
Firebase Emulator on every startup.

## Authentication

Specifications use the provider-neutral `AuthenticationPage` operations. The initial local driver
uses the application's local Firebase sign-in form. A hosted provider should implement the same
register/sign-in operations rather than leaking provider-specific selectors into the journeys.

The population journey casts its twenty background votes through the real `POST /votes` endpoint
with a real per-account token rather than twenty browser sign-ins. That is setup, not the journey:
what is being verified — a reader voting and being shown the breakdown — stays in the browser.

The admin journey opens the isolated admin UI on `http://localhost:58083/admin/`, signs in as the
seeded administrator, changes Casey Morgan from a user to an official poster, persists the inactive
state, and reloads after each save to prove both changes reached the database. It restores the
account before the journey finishes.

## Overriding credentials

Any account can be overridden without editing tests:

```shell
SMOKE_READER_USERNAME=reader \
SMOKE_READER_EMAIL=reader@example.com \
SMOKE_READER_PASSWORD=password \
bun run smoke
```

The same pattern applies to `SMOKE_PUBLISHER_*`, `SMOKE_ADMIN_*` and `SMOKE_VOTER_PASSWORD`.
