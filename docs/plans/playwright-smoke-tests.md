# Playwright smoke tests

## Goal

Add a local-only Playwright smoke-test module that proves the most important user journeys work
through the Expo web frontend, real authentication, `post-service`, Postgres and LocalStack.

The user journeys and their assertions must be authentication-provider agnostic. Keycloak is the
current local provider; hosted environments are expected to use Google-backed authentication.
Provider-specific selectors and navigation belong behind a small authentication driver, not in
the smoke specifications or their capability names.

The first suite is intentionally small. It gives broad confidence that the product is usable
without duplicating the detailed backend and frontend integration tests. CI execution, native
device automation and broader product coverage are deferred.

The scope decision and initial capability list are recorded in
[ADR-030](../../wiki/ADR-030-2026-07-25-browser-smoke-test-scope.md).

## Initial journeys

Implement two serial Chromium journeys:

1. **Registration and onboarding**
   - Open the signed-out application.
   - Start the account-creation flow and follow the configured local authentication provider.
   - Register a unique disposable user.
   - Confirm the browser returns to Your Say News as an authenticated user.
   - Accept the privacy promise.
   - Complete every required characteristics step with representative answers.
   - Submit and confirm the user reaches the feed.

2. **Returning reader and feed**
   - Sign in through the configured local authentication provider as the seeded `riley.reader`
     account.
   - Confirm the authenticated reader reaches the feed without being sent through onboarding.
   - Load the Video feed and confirm a video post, its story content and a usable media URL are
     rendered. Confirm the browser receives a successful video or poster response.
   - Switch to the Article feed and confirm an article post and its story content are rendered
     without an active video post.

These journeys cover the five initial capabilities without splitting one stateful user journey
into brittle, order-dependent micro-tests.

## Module layout

Create a root-level module that reuses the existing root `@playwright/test` dependency:

```text
smoke-tests/
  README.md
  compose.smoke.yaml
  playwright.config.ts
  fixtures/
    test-data.ts
  pages/
    authentication-page.ts
    characteristics-page.ts
    feed-page.ts
  specs/
    registration-and-onboarding.smoke.spec.ts
    returning-reader-feed.smoke.spec.ts
  artifacts/                         # ignored; reports, traces, screenshots and service logs
scripts/
  smoke-test.sh
```

Page objects should express user actions and stable page landmarks, not reproduce application
logic. Prefer accessible roles, labels and visible copy. Add a narrowly named `testID` to the
application only where there is no stable user-facing selector.

The authentication fixture exposes product-level operations such as `register()` and `signIn()`.
Its local implementation may understand Keycloak's pages, but specifications must not import a
Keycloak-named class, assert a Keycloak URL or depend on Keycloak-specific copy. A future hosted
Google authentication implementation should be selectable through configuration without rewriting
the journeys.

## Local environment and isolation

`bun run smoke` should be the single entry point.

The runner will:

1. verify Docker, Docker Compose, Bun, Chromium and the required smoke ports are available;
2. apply a smoke Compose override that removes fixed container names and maps dedicated high local
   ports, allowing the ordinary development stack to remain running;
3. start `compose.yaml` under a dedicated Compose project name such as `yoursay-smoke`;
4. wait for Postgres, the configured local authentication provider, migrations, seeding and
   LocalStack to be ready;
5. start `post-service` in Quarkus dev mode and Expo web as child processes;
6. wait for the smoke-only post-service and Expo ports (by default `58082` and `55173`);
7. run Playwright against the smoke-only Expo URL;
8. always stop the child processes and run `docker compose -p yoursay-smoke down -v` from a shell
   trap, including after interruption or test failure.

The dedicated Compose project gives the smoke run disposable database, local authentication and
LocalStack state. This allows registration and characteristics to use real persistence while
keeping the suite repeatable and preserving the normal developer volumes. The runner must never
issue `docker compose down -v` without the explicit smoke project name.

Service stdout and stderr should be written beneath `smoke-tests/artifacts/services/` so a failed
startup or browser assertion can be diagnosed from one place.

## Playwright configuration

- Chromium desktop only for the first version.
- One worker and serial execution because the journeys share a local environment.
- No retries locally; intermittent failures should remain visible while the suite is young.
- Capture a trace, screenshot and video on first failure.
- Keep HTML output beneath the ignored `smoke-tests/artifacts/` directory.
- Read base URLs and seeded credentials from environment variables, with local defaults matching
  `docs/test-accounts.md`.
- Generate the registration username and email per run. The disposable environment is the cleanup
  mechanism.
- Wait for visible state, navigation or specific network responses. Do not use fixed sleeps.

## Assertions

Assertions should pin meaningful user-visible outcomes:

- registration returns from the authentication provider to the application and shows the privacy
  promise;
- sign-in returns to the application and shows the feed controls;
- characteristics cannot be considered complete until the final submission succeeds and the feed
  appears;
- the Video filter is selected, a video post's support question is visible, the rendered media has
  a non-empty URL and its media/poster request succeeds;
- selecting Article causes an `ARTICLE` feed request and renders article story content without a
  visible active video.

Do not assert only that a URL changed, an element exists somewhere in the DOM or a response body is
non-null. Use known seed content where it makes the expected result deterministic.

## Implementation phases

### 1. Runner and configuration

- Add the root `smoke` package script.
- Add Playwright configuration, artifact ignores and the local runner.
- Make startup checks and cleanup safe and deterministic.
- Document the one-command local workflow and the expected Docker/Chromium prerequisites.

### 2. Reusable browser vocabulary

- Add small page objects for Your Say News, the provider-agnostic authentication boundary and the
  characteristics wizard.
- Keep credentials and generated registration data in fixtures.
- Add only the minimum application selectors required for stable browser interaction.

### 3. Initial journeys

- Implement registration plus consent and characteristics onboarding.
- Implement returning-reader sign-in plus Video and Article feed loading.
- Ensure a failed assertion retains enough browser and service evidence to reproduce the problem.

### 4. Verification

- Run `bun run smoke` twice from clean disposable environments to prove repeatability.
- Deliberately break one assertion or intercept one required request to prove each journey fails
  for the intended reason, then restore it.
- Run the repository's `test-audit` skill after the smoke tests are written.
- Confirm the ordinary `bun run dev` and `bun run test` workflows remain unchanged.

## Affected areas

- Root JavaScript tooling and scripts.
- New `smoke-tests` module.
- Local process orchestration only.
- Potential minimal accessibility/test identifiers in existing frontend components.

No production API, DTO, database migration or seed-data changes are planned. Existing seeded
accounts and posts are consumed as fixtures. Changing the application's authentication provider is
not part of this implementation.

## Out of scope

- CI configuration.
- Safari, Firefox or a browser matrix.
- iOS/Android device automation.
- Visual-regression baselines.
- Voting, publishing, profile, social, Post Unwrapped or agent flows.
- Exhaustive validation and error-path testing already owned by unit/integration suites.
- Proving video autoplay, sound or codec compatibility across real mobile devices.
