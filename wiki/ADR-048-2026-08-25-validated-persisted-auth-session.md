# ADR-048 — Validated persisted auth session

Date: 2026-08-25

## Situation

The mobile/web client persists its auth store (`auth-store`) to `localStorage` on web and
SecureStore on native, so a returning user does not have to sign in again. Nothing ever checked
that a restored session was still real.

Two failures followed from that:

1. **A dead session kept its identity.** Once an access token expired past the realm's
   `ssoSessionIdleTimeout` (1800s), every authenticated call failed. `getOnboardingStatus()` returns
   `null` on failure and the routing gate read that as "this user has no characteristic profile", so
   a fully onboarded account — seeded ones included — was sent into the characteristics wizard. The
   only way out was clearing site data by hand.
2. **A fresh sign-in deferred to the stored user.** `app/index.tsx` skipped `completeLogin` whenever
   a persisted `isLoggedIn` was present, even when the URL carried a fresh `code` + `state` from
   Keycloak. Signing in as a different account returned the previously stored identity.

The second is the more serious: it is an identity-bleed bug. Whoever the browser last held is who
you got, regardless of who authenticated.

## Options considered

1. **Do not persist the session at all.** Sign in on every app open. Removes both failures outright
   but throws away the returning-user experience, and on native it is a real regression.
2. **Expire locally from the stored `accessTokenExpiresAt`.** No network call, but it trusts a
   client clock and cannot see a server-side revocation, a deleted account, or a wiped realm — all
   of which happen routinely in this project's local dev loop.
3. **Validate the restored session against the server on startup, and wipe it if the server rejects
   it.** One request on boot; the server stays the authority on who is signed in.

## Decision

Option 3, with a three-way outcome so a network failure is not confused with a rejection:

- `verifySession()` (`features/auth/services/UserService.ts`) calls `GET /your-say-user` and
  classifies the result as `valid` / `unauthenticated` / `unreachable`. **Only `401` and `403` count
  as `unauthenticated`** — the server actively rejecting the caller is the sole proof that a session
  is dead. Everything else (no response at all, or a `5xx` fault) is `unreachable`, because
  destroying someone's credentials over a transient outage is the same class of mistake this ADR
  exists to fix.
- `restoreSession()` (`features/auth/services/authContext.ts`) runs on startup and returns
  `"signed-in"`, `"signed-out"` or `"unverified"`:
  - `unauthenticated` → full `logout()`, which already wipes the store, `localStorage`,
    `sessionStorage` and JS-readable cookies. **No manual clearing is ever required.**
  - `unreachable` → credentials are kept (they may still be good) but `isLoggedIn` is cleared, so
    the route guard in `app/_layout.tsx` cannot admit a session nobody vouched for. A backend hiccup
    must not sign people out, and it must not wave them through either.
  - `valid` → identity, consent and onboarding flags are refreshed from the server, overruling
    whatever was persisted.
- A sign-in redirect always wins. `app/index.tsx` no longer short-circuits on a persisted
  `isLoggedIn`; if `code` + `state` are present the exchange runs and the new identity replaces the
  old one.
- `completeLogin()` resets every identity field to its signed-out default before applying the new
  user, so no field of the previous account (consent, onboarding flags, `accountType`,
  `publisherStatus`) can survive a change of identity.

The routing rules moved into `features/auth/onboardingRoute.ts`
(`resolveOnboardingDestination`) so the protected index and the characteristics wizard cannot drift
apart. Consent is asked for on its own: a user with a characteristic profile but no consent goes to
the consent page and then to the feed — the wizard never re-runs when answers are already held.

## Reason

The server is the only thing that knows whether a token is still good, whether the account still
exists, and what that account has consented to. Persisted client state is a cache, and this codebase
was treating it as the source of truth for identity.

Separating `unauthenticated` from `unreachable` is what makes the wipe safe to do automatically. A
single "the call failed" signal cannot distinguish "your session is dead" from "the API is down",
and the previous code guessed — always in the direction that made the user re-enter data.

## Consequences and follow-up

- One extra request on app start. It replaces the status call the protected index was already
  making on mount, so the practical cost is nil.
- Users whose token has expired are now signed out cleanly rather than dropped into onboarding.
- Seeded accounts created before consent existed (ids 1–4) had no `consented_at` and stopped on the
  consent gate forever; `liquibase/changelog/db/user-seeding/0012-seed-original-account-consent.yaml`
  records consent for them.
- **Still open:** `POST /user-characteristics` has no server-side consent check. The client is now
  ordered correctly, but the API would still accept a characteristic profile from an account that
  never consented. That gate belongs on the backend and is not addressed here.
- **Still open:** the `unverified` outcome routes to sign-in with no retry or explanatory state. A
  user on a flaky connection sees a sign-in screen rather than "we could not reach the server".
