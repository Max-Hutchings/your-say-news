# ADR-059 - Android release in CI after dev deploy

## Status

Accepted on 2026-10-03. Builds on ADR-056 (main auto-deploys to dev) and ADR-057 (Android Play
release via EAS).

## Situation

- Android releases were run by hand from a laptop with `eas build ... --auto-submit`.
- Every `eas` command loaded the hosted Expo config locally, which required a local copy of
  `google-services.json` and placeholder variables. CI would have needed the same file.
- `app.config.prod.js` was misnamed: it served the dev app (`APP_ENV=development`), and `AGENTS.md`
  still described it as stale Keycloak config.

## Options considered

1. Release on every `main` push that changes the Expo app, after tests pass.
2. Release only from a manual "Run workflow" button.
3. Release on every `main` push, whether or not the app changed.

## Decision

Option 1. A `release-android` job in `ci.yml`:

- `needs: [deploy-development]`, so the app ships only after every test job passes and the dev
  server deploy succeeds. The app never reaches testers ahead of the API it calls.
- Runs only when the push changed `frontend/mobile/your-say-news`.
- Runs `eas build -p android --profile development-store --auto-submit --no-wait`. EAS builds and
  submits to the Play internal track; the runner only queues the build.
- Authenticates with one GitHub secret, `EXPO_TOKEN` (an Expo robot user token).

Supporting changes:

- `app.config.prod.js` renamed to `app.config.hosted.js` (config for any hosted backend: dev now,
  prod later).
- `app.config.hosted.js` requires `GOOGLE_SERVICES_JSON` only when `EAS_BUILD=true` (EAS build
  servers). Laptops and CI no longer need the Firebase file or placeholder variables.

## Reason

- Releasing with the server deploy keeps the app and API in step.
- The path check avoids spending EAS builds and Play version codes on backend-only pushes.
- The Firebase file stays only in EAS, so GitHub holds no Firebase config.

## Consequences

- Releases still land as Play **drafts** (ADR-057) until the app is published once; then switch
  the `development-store` submit profile to `releaseStatus: completed`.
- Each release uses one EAS build from the organisation's plan allowance.
- `--no-wait` means a failed EAS build or submit does not fail CI. Check the EAS dashboard.
- The path check compares against `github.event.before`; a force-push to `main` that rewrites
  history may skip or trigger a release unexpectedly.
- Local manual releases are now just
  `eas build -p android --profile development-store --auto-submit`.
