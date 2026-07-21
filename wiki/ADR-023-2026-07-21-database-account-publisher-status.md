# ADR-023 — Database account type and publisher status

## Situation

MVP1 v2 removes public post creation. Ordinary signed-in accounts can read and vote, while
identifiable Your Say News official accounts can create posts, upload post media and use the
unbiased-post creation agent.

Keycloak currently authenticates users and supplies broad realm roles. The application also has an
unused `UserRole` enum, but no persisted account classification or publishing state. We need to:

- identify official accounts in public profiles and post attribution;
- suspend or disable publishing without removing an account's official identity;
- enforce publishing rules in backend write endpoints; and
- seed existing post authors as officials while retaining standard non-official accounts.

## Options considered

### 1. Add an `official` Keycloak realm role

Keycloak would include the role in access tokens and Quarkus could enforce it with
`@RolesAllowed`.

This makes a Your Say News business classification dependent on identity-provider administration.
Changes can remain effective in an already-issued token until it expires, and official profile data
would still need a separate application representation. It would also create two potential sources
of truth if the database retained official-account display data.

### 2. Store only an `OFFICIAL` account type in the application database

This makes official identity clear and application-owned, but cannot distinguish an active
publisher from an official account whose publishing ability is withheld or suspended.

### 3. Store account type and publisher status separately in the application database

Account type expresses what an account is; publisher status expresses whether it may currently
publish. Keycloak remains responsible for authentication only.

## Decision

Choose option 3.

Persist the following string-backed enums on `your_say_user`:

```java
public enum AccountType {
    STANDARD,
    OFFICIAL
}

public enum PublisherStatus {
    NONE,
    ACTIVE,
    SUSPENDED
}
```

The authoritative publishing rule is:

```text
canPublish = accountActive && accountType == OFFICIAL && publisherStatus == ACTIVE
```

All three conditions are mandatory. An inactive account cannot publish. A `STANDARD` account cannot publish even if invalid data assigns it
`ACTIVE`. An `OFFICIAL` account with `NONE` or `SUSPENDED` remains identifiable as official but
cannot publish.

New public registrations default to `STANDARD` and `NONE`. An official account provisioned for
publishing defaults to `OFFICIAL` and `ACTIVE`. Changing an official account back to `STANDARD`
must also reset publisher status to `NONE`.

The database stores enum names, never ordinal numbers. Schema constraints restrict values and
prevent a standard account from carrying an active or suspended publisher status.

For MVP1, account type and publisher status are changed only through controlled migrations,
seeding or internal operations. There is no public promotion or status-management endpoint.

Existing seeded accounts that author posts become `OFFICIAL` / `ACTIVE`. Seeded accounts without
posts remain `STANDARD` / `NONE`, ensuring both account paths are represented locally and in tests.

Backend enforcement applies to every posting write entry point, including:

- post creation;
- post-media upload presigning; and
- starting unbiased-post agent generation.

Future post update, delete, approval and publication endpoints must use the same rule. Read, vote
and ordinary profile endpoints require authentication as appropriate but do not require publishing
ability.

`user-service` owns the classification and returns a PII-minimal authenticated-account access DTO
containing the internal user ID, account type, publisher status and derived `canPublish` value.
`post-service` forwards the caller's bearer token and uses that contract before performing a
publishing action. It must not trust a caller-supplied account ID, account type or status.

The mobile client uses the derived capability to hide posting controls from accounts that cannot
publish. This is presentation only; backend enforcement remains authoritative.

Public DTOs may expose that an author is official, so the UI can display an official badge. They do
not expose publisher status: suspension is an operational fact, not public profile information.

## Reason

Account type and publisher status answer different questions:

- `AccountType` provides the clear, durable identity distinction required for official accounts.
- `PublisherStatus` provides independent operational control without erasing that identity.

Keeping both in the application database puts the decision beside official profiles and post
ownership, takes effect immediately, avoids Keycloak/database drift and leaves room for future site
administration without forcing admin permissions into a mutually exclusive account type.

## Consequences and follow-up work

- Replace the unused `UserRole` model with `AccountType`; do not extend it into a competing realm-role
  hierarchy.
- Add Liquibase migration and seed changes for both columns, constraints and existing accounts.
- Add a user-domain access contract and enforce it on all current posting write endpoints.
- Add integration tests covering `OFFICIAL/ACTIVE`, `OFFICIAL/SUSPENDED`, `OFFICIAL/NONE` and
  `STANDARD/NONE` posting attempts.
- Hide the frontend create-post control when `canPublish` is false and test both visible and hidden
  states.
- A later ADR must define site-administration permissions and the audited UI/workflow used to grant,
  suspend or revoke publishing. Admin permissions may coexist with either account type.
- Keycloak realm-role cleanup is separate work. This ADR prevents realm roles from becoming the
  source of truth for official identity or publishing ability.
