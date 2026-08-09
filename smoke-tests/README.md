# Local smoke tests

These Playwright journeys exercise the Expo web application, authentication, backend and local
infrastructure together. They run against disposable local state and are not part of CI.

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
the three browser journeys and removes only the smoke project's containers and volumes. It uses
dedicated high local ports, so the ordinary `bun run dev` stack can remain running.
Network access is needed while LocalStack's existing seed script downloads the video fixture.

Failure evidence is written to `smoke-tests/artifacts/`, including the HTML report, trace,
screenshot, video and backend/frontend logs.

## Authentication

Specifications use the provider-neutral `AuthenticationPage` operations. The initial local driver
uses the configured local provider's HTML form. A hosted provider should implement the same
register/sign-in operations rather than leaking provider-specific selectors into the journeys.

The admin journey opens the isolated admin UI on `http://localhost:58083/admin/`, signs in as the
seeded administrator, changes Casey Morgan from a user to an official poster, persists the inactive
state, and reloads after each save to prove both changes reached the database. It restores the
account before the journey finishes.

The returning-reader credentials can be overridden without editing tests:

```shell
SMOKE_READER_USERNAME=reader \
SMOKE_READER_EMAIL=reader@example.com \
SMOKE_READER_PASSWORD=password \
bun run smoke
```

The administrator credentials can be overridden in the same way:

```shell
SMOKE_ADMIN_USERNAME=admin \
SMOKE_ADMIN_EMAIL=admin@example.com \
SMOKE_ADMIN_PASSWORD=password \
bun run smoke
```
