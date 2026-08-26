# ADR-053 - Local Firebase authentication and cookie sessions

## Status

Accepted on 2026-08-25.

## Situation

Local development still ran Keycloak even though ADR-028 selected Firebase Authentication. Expo
must work on native devices and in web mode. The embedded admin UI must not keep bearer or refresh
tokens in browser storage.

## Options considered

1. Keep Keycloak locally and Firebase only in hosted environments.
2. Run the Firebase Authentication Emulator locally and use Firebase bearer tokens everywhere.
3. Run the emulator, use Firebase-managed sessions for Expo, and exchange the admin Firebase ID
   token for a backend-issued Firebase session cookie.

## Decision

Choose option 3 for the local development profile.

- Compose runs the Firebase Authentication Emulator and reconciles repository-owned test accounts.
- Expo uses the Firebase JavaScript SDK against the emulator. Firebase persists and refreshes the
  session; API requests carry a current ID token.
- The admin UI uses Firebase only during sign-in. `post-service` verifies the ID token, checks the
  active database `ADMIN` account, creates a 12-hour Firebase session cookie, then the UI signs out
  of Firebase.
- The admin session cookie is `HttpOnly`, `SameSite=Strict`, path `/`, and not `Secure` only because
  local development is HTTP. Admin mutations also require an origin check and double-submit CSRF
  token.
- Every backend replica verifies the signed Firebase cookie independently. No replica-local session
  store or gateway affinity is required.
- Local identity temporarily resolves existing users by verified email to preserve the current data
  model. Hosted migration still requires ADR-028's immutable Firebase UID link before release.

## Reason

This removes the local Keycloak service and database, keeps Expo native/web development usable, and
prevents admin authentication tokens from being readable by browser JavaScript after sign-in.
Signed Firebase cookies work across replicas without shared in-memory state.

## Consequences and follow-up work

- Hosted cookies must set `Secure=true` behind HTTPS.
- The hosted Firebase/Google migration and immutable UID schema remain separate work.
- Local email/password accounts are emulator-only and cannot authenticate against hosted Firebase.
- Authentication metrics classify expected rejection as `error` and Firebase availability failures
  as `fault`; tokens, subjects and emails are excluded from metrics and logs.
