# ADR-028 — Google authentication and application-owned authorization

## Status

Proposed.

This ADR requires approval before the Keycloak implementation is removed.

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

## Proposed decision

Choose option 3.

Google is the only external identity provider for this stage. The native Android flow must use an
authorization-code/PKCE-compatible Google flow supported by the chosen Android library. The
backend must validate, at minimum:

- token signature against Google's published keys;
- issuer;
- audience/authorised presenter for the configured Android/backend clients;
- expiry;
- nonce and code-verifier properties where the selected flow requires them; and
- `email_verified` when email is displayed or used to contact the tester.

The application links identity using Google's case-sensitive `sub`. Email is profile/contact data,
not a primary key or sole authorisation check.

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

The backend should exchange the verified Google sign-in result for short-lived application access
and rotating refresh sessions so that API authorisation is stable and revocable. Before
implementation, a security design must specify:

- token signing/key rotation and public-key discovery;
- access/refresh lifetimes;
- refresh rotation, reuse detection and revocation;
- secure Android storage;
- logout and lost-device behaviour;
- audience separation between environments;
- CSRF/replay protections for every flow; and
- migration to a managed broker such as AWS Cognito if operating an issuer is no longer justified.

This ADR does not authorise an improvised custom JWT implementation. If the team cannot meet those
requirements safely, use a reviewed managed Google-compatible token broker for the development
environment while preserving the same application-owned invitation/permission model.

Bootstrap the first `ADMIN` by immutable Google `sub` through a one-time audited migration or
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

Using `sub` prevents account linking from breaking when an email changes. Short-lived application
sessions allow immediate invitation/permission revocation and present a stable bearer-token
boundary to the Quarkus API. That boundary can later be implemented behind AWS Cognito or another
managed issuer without rewriting the domain authorisation model.

## Consequences and follow-up work

- Remove Keycloak and its PostgreSQL container from the remote deployment after migration tests
  pass. Retaining Keycloak in local Compose during the transition is temporary.
- Replace Keycloak realm/client configuration in Quarkus and Expo.
- Add invitation, external-identity, application-permission, session/revocation and audit schema.
- Add one-time admin bootstrap tooling and a recovery/runbook requiring both developers.
- Add an admin-page roadmap item for invite/revoke, application permissions, official account and
  publisher status. The first remote release may use audited operational tooling instead.
- Add integration tests for invalid signature/issuer/audience/expiry/nonce, uninvited account,
  revoked account, `USER`, `ADMIN` and publisher combinations.
- Add tests proving a newly revoked admin cannot start the YSN agent.
- Keep authentication tokens, emails and Google subjects out of metrics and ordinary logs.
- Register separate Google client IDs/audiences for local and remote environments.
- Review consent screen, privacy policy, data processing and account deletion requirements before
  admitting real testers.
- Update ADR-023 implementation notes that still describe a cross-service `user-service`; ADR-025
  now places the user domains in `post-service`.
- If this ADR is accepted, it supersedes only the Keycloak-authentication and Keycloak-admin-role
  portions of ADR-023 and ADR-027. Their application-domain decisions remain accepted.

