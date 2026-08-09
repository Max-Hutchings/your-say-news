# ADR-028 — Google authentication and application-owned authorization

## Status

Accepted on 2026-07-27.

The application-owned authorisation boundary is accepted, with Firebase Authentication selected as
the managed Google-compatible identity and session broker. Keycloak may be removed from the remote
implementation after migration tests pass.

## Situation

The production-like development environment has a £20 monthly infrastructure budget and fewer than
100 authorised testers. Running Keycloak and its own PostgreSQL database adds memory, database and
operational cost that is disproportionate at this stage.

The product will admit Google accounts only. Authentication must still work from the native Android
application, and possession or discovery of the API hostname must not grant access.

The current architecture already separates identity from important product authorisation:

- ADR-023 makes `AccountType` and `PublisherStatus` application-database facts rather than identity
  provider roles.
- ADR-027 uses a Keycloak `admin` realm role to start the YSN agent, while its `ysn` publisher is an
  application-owned account.
- Future administration must invite/revoke testers and manage admin/publisher permissions with an
  audit record.

Email addresses are mutable and are not safe durable identifiers. The application also needs
permission changes and revocation to take effect without waiting for a long-lived external token.

## Options considered

### 1. Retain Keycloak

Keycloak provides a mature issuer, sessions and role mapping, but requires another always-on
container, database/schema, upgrade path, backup and administrative surface.

### 2. Trust Google identity tokens and Google account attributes as application permissions

This removes Keycloak, but conflates authentication with product authorisation. Google cannot be
the source of truth for invitations, `ADMIN`, official-account or publisher status. Email-based
allowlists are also vulnerable to identity drift.

### 3. Use Google for identity and the application for admission, sessions and permissions

The Android client authenticates with Google. The backend validates a Google assertion, links the
user through the immutable Google `sub`, checks the application invitation and establishes a
short-lived application session. PostgreSQL remains authoritative for access and permissions.

### 4. Use Firebase for Google identity/sessions and the application for admission and permissions

The Android client authenticates with Google through Firebase Authentication. Firebase owns
identity-token issuance, refresh sessions, signing-key rotation and identity-session revocation.
The backend validates the Firebase assertion and checks the application invitation, activity and
permissions in PostgreSQL on protected operations.

This avoids an application-built token issuer while retaining the domain-authorisation boundary
needed for future broker or cloud migration.

## Decision

Choose option 4.

Google is the only external identity provider for this stage. The native Android flow uses the
supported Firebase Authentication and Credential Manager integration. The backend must validate,
at minimum:

- the Firebase ID-token signature against the broker's published keys;
- issuer and Firebase project;
- audience for the configured environment;
- expiry;
- revocation for security-sensitive operations where immediate identity-session revocation is
  required; and
- `email_verified` when email is displayed or used to contact the tester.

The application links identity using the case-sensitive Firebase UID carried in `sub` and retains
the external issuer/subject mapping required for a future broker migration. Email is
profile/contact data, not a primary key or sole authorisation check.

An authenticated Google account is admitted only when it matches an active application invitation
or existing active application identity. Account activity, application permission, `AccountType`
and `PublisherStatus` are PostgreSQL facts.

Introduce an application permission model independent of publisher classification:

```text
USER   — normal authorised tester
ADMIN  — site administration and privileged agent operations
```

An account may be `ADMIN` and either `STANDARD` or `OFFICIAL`. Publishing remains governed by
ADR-023:

```text
canPublish = accountActive
             && accountType == OFFICIAL
             && publisherStatus == ACTIVE
```

`POST /admin/ysn-agent/posts` from ADR-027 must require application `ADMIN` rather than a Keycloak
realm role. The fixed `ysn` application account and publisher checks remain unchanged.

Firebase owns access-token signing, key rotation, refresh sessions and broker revocation. The
implementation must still validate native Android storage, logout and lost-device behaviour,
replay protections and audience separation between local, development and future production
projects. It must not add a second application-issued JWT or refresh-token layer.

Bootstrap the first `ADMIN` by immutable broker `sub` through a one-time audited migration or
privileged operational command. Do not commit the subject, email, access token or refresh token to
Git, Terraform variables or Terraform state.

Every invitation, activation, revocation, permission change, account-type change and publisher
status change records:

- acting application subject;
- target application subject/invitation;
- action and before/after state;
- timestamp;
- request/correlation ID; and
- non-secret reason where required.

## Reason

This removes two unnecessary development workloads without making product permissions dependent on
Google administration. It follows ADR-023's existing separation: the identity provider proves who
the caller is, while the application decides what that caller may do.

Using `sub` prevents account linking from breaking when an email changes. Managed Firebase ID and
refresh tokens avoid operating a security-sensitive issuer. Checking application activity and
permissions in PostgreSQL keeps invitation, permission and publisher revocation under application
control. The broker boundary can later move to AWS Cognito or another managed issuer without
rewriting the domain authorisation model.

## Consequences and follow-up work

- Remove Keycloak and its PostgreSQL container from the remote deployment after migration tests
  pass. Retaining Keycloak in local Compose during the transition is temporary.
- Replace Keycloak realm/client configuration in Quarkus and Expo with Firebase Authentication and
  Google Sign-In.
- Add invitation, external-identity, application-permission, broker-revocation metadata and audit
  schema; do not build application refresh-token tables.
- Add one-time admin bootstrap tooling and a recovery/runbook requiring both developers.
- Add an admin-page roadmap item for invite/revoke, application permissions, official account and
  publisher status. The first remote release may use audited operational tooling instead.
- Add integration tests for invalid signature/issuer/audience/expiry/nonce, uninvited account,
  revoked account, `USER`, `ADMIN` and publisher combinations.
- Add tests proving a newly revoked admin cannot start the YSN agent.
- Keep authentication tokens, emails and broker/external subjects out of metrics and ordinary logs.
- Register separate Firebase projects/audiences and Google client IDs for local, development and
  future production environments.
- Review consent screen, privacy policy, data processing and account deletion requirements before
  admitting real testers.
- Update ADR-023 implementation notes that still describe a cross-service `user-service`; ADR-025
  now places the user domains in `post-service`.
- This ADR supersedes only the Keycloak-authentication and Keycloak-admin-role portions of ADR-023
  and ADR-027. Their application-domain decisions remain accepted.
