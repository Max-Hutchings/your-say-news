# ADR-062 - Open sign-up with admin deactivation

Date: 2026-10-09. Supersedes the admission rule in ADR-028.

## Situation

- ADR-028 admitted a Google account only if it matched an invitation or an existing active user row.
- `1bb3b05` implemented that: `DatabaseFirebaseRoleResolver` gave the `user` role only to emails with
  an active `your_say_user` row.
- No invite feature exists, and the first-sign-in endpoint (`GET /your-say-user`) that creates the
  row itself requires the `user` role. Every new Google account on dev got 403, shown in the app as
  "could not load your account (server: user_unavailable)". Prod would behave the same.
- `ActiveAccountRequestFilter` (ADR-034) already blocks deactivated accounts on every request and
  lets unknown accounts through so first sign-in can provision them.

## Options considered

1. **Admin-panel invites.** An admin adds an email; first sign-in links to it. Duplicates the Play
   tester list during testing and must be removed for a public launch.
2. **Open sign-up, admin deactivation.** Any verified Firebase account gets the `user` role unless
   its row is deactivated.

## Decision

Option 2.

- `hasActiveUserAccess(email)` is now `!isInactive(email)`: unknown and active accounts are users,
  deactivated ones are not.
- Admins block access with `PUT /api/admin/users/{id}` and `active: false`. It takes effect on the
  next request (no role, and `ActiveAccountRequestFilter` returns `USER_ACCOUNT_INACTIVE`).
- Firebase must still verify the token and the email must be verified.

## Reason

- Industry practice: store tester lists (Play internal testing) control who gets a test build, and
  the backend lets in anyone who can sign in. A public news app needs open sign-up in prod anyway.
- One tester list, not two. Dev sign-up behaves the way prod will.

## Consequences

- The Play tester list controls distribution only, not API access. Anyone with the app's Firebase
  config can create an account. Acceptable for dev data; abuse controls (rate limits) belong to the
  public launch.
- An invite-only mode can be added later as a toggle if a controlled beta is needed.

## Follow-up fixes in the same change

Open sign-up exposed three ways a first sign-in could still fail or slip past a block:

- **Email case.** Lookups match email exactly, so a differently capitalised email would miss a
  deactivated row and create a fresh active account. `VerifiedFirebaseIdentity` now lowercases the
  email, and migration `0018` lowercases stored emails and adds `CHECK (email = lower(email))`.
- **No surname.** Single-name Google accounts used to fail with `USER_IDENTITY_CLAIMS_MISSING`. Only
  email and first name are now required; a missing surname is stored as `""`.
- **Duplicate names.** `handle` is unique and derived from the name, so a second "Max Hutchings"
  failed on the constraint. New accounts take the next free handle (`max.hutchings.2`).
