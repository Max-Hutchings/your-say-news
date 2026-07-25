# ADR-030 — Browser smoke test scope

Date: 2026-07-25

## Situation

Your Say News has backend integration tests and frontend component/integration tests, but it does
not have a repeatable check that the real Expo web application, authentication provider,
`post-service`, Postgres and LocalStack work together from a user's perspective.

Keycloak currently provides authentication locally. Hosted services are expected to move to
Google-backed authentication, so smoke tests must describe and verify the authentication capability
without making Keycloak part of the product contract.

The first smoke suite needs to run locally without being added to CI. Its purpose is broad,
shallow confidence in the product's most important paths, not detailed validation of every API or
screen state.

Registration and characteristics mutate identity and application data. Running those checks
against a developer's persistent environment would pollute local data and make subsequent runs
order-dependent.

## Options considered

### 1. Direct backend smoke tests

Call a small set of REST endpoints and verify their responses.

This would be fast, but the existing Quarkus integration tests already cover backend HTTP,
persistence and security in much greater detail. It would not prove that browser redirects,
authentication hand-off, token handling, frontend routing, CORS or media rendering work together.

### 2. Playwright against the developer's existing stack

Drive the Expo web frontend and configured authentication provider while `bun run dev` is already
running.

This is simple to start, but registration and onboarding would leave persistent state behind.
Results would depend on the history of the developer's authentication and database state.

### 3. Playwright against a disposable local full stack

Start isolated local infrastructure on dedicated ports, drive the real browser UI and remove only
the smoke environment and its volumes after the run.

This takes longer than direct API tests, but exercises the integration boundaries users depend on
and remains repeatable.

## Decision

Choose option 3.

Create a root-level `smoke-tests` Playwright module. Its first target is the Expo web application
in Chromium. The suite drives user-visible controls through the real configured authentication
provider and backend; it does not bypass authentication by injecting tokens or mocking network
responses.

Smoke specifications use product-level authentication actions and outcomes only. Provider-specific
navigation and selectors are isolated in an authentication driver selected by environment
configuration. The initial local driver uses Keycloak, but test names, capability definitions,
page objects consumed by specifications and assertions remain provider-neutral. Moving hosted
environments to Google-backed authentication must require a driver/configuration change rather
than rewriting the core journeys.

The local runner owns a disposable Compose project and always cleans up that explicitly named
project. A Compose override removes fixed container names and maps smoke-only host ports, so it can
run alongside the ordinary development stack without sharing state. Smoke tests run serially and
use real seed data. They may observe network responses to diagnose or strengthen a user-visible
assertion, but direct API calls must not replace the browser steps of the core journeys.

No CI workflow is added in this stage.

### Initial core functionality

The initial suite covers only:

1. **Register**
   - A signed-out visitor can start account creation.
   - The configured authentication provider accepts a new unique test account.
   - Successful registration returns the browser to Your Say News as an authenticated user.

2. **Sign in**
   - A configured returning test reader can sign in through the authentication provider.
   - The authentication redirect completes and the authenticated application loads.
   - An already-onboarded reader reaches the feed rather than consent or characteristics.

3. **Fill out characteristics**
   - A new user sees and accepts the privacy promise before the questionnaire.
   - The user can complete all required characteristics steps with representative answers.
   - Successful submission persists the answers and takes the user to the feed.

4. **Load video posts**
   - The user can select or enter the Video feed.
   - A seeded video post renders its support question and video surface.
   - The video has a usable media URL and the browser receives a successful video or poster
     response.

5. **Load article posts**
   - The user can switch to the Article feed.
   - A seeded article post renders its support question and article content.
   - The active article is not incorrectly rendered as a video post.

The first implementation may combine these capabilities into a small number of complete journeys.
One registration-to-onboarding journey and one returning-reader-to-feed journey are preferred over
five order-dependent tests.

## Reason

Browser smoke testing adds confidence at boundaries the current suites do not cover: frontend
configuration, authentication redirects, token persistence, protected routing, CORS, backend
wiring and media delivery.

A disposable environment is essential because account creation and characteristics are stateful.
It provides deterministic seed data on every run and prevents test accounts from accumulating in
the normal development environment.

Keeping the scope to five capabilities makes failures understandable while the harness earns
trust. More flows should be added only when they protect a proven critical path without turning
the smoke suite into a slow duplicate of the integration suites.

## Consequences and follow-up work

- Add the Playwright module and a single local command that owns startup, readiness checks,
  execution and safe cleanup.
- Keep provider-specific browser mechanics behind an authentication driver; Keycloak is a local
  adapter, not part of the smoke-test contract.
- Add a hosted Google authentication driver/configuration only when hosted smoke execution is
  introduced.
- Store traces, screenshots, videos and service logs as ignored local artifacts.
- Keep Chromium/web as the only initial runtime; native mobile automation is a later decision.
- Do not add the smoke suite to CI yet.
- Voting, publishing, profiles, social flows, Post Unwrapped and agent workflows remain outside
  the initial scope.
- Revisit retries, browser coverage, CI execution and additional journeys after repeated local
  runs establish speed and reliability.
- The implementation plan is
  [Playwright smoke tests](../docs/plans/playwright-smoke-tests.md).
