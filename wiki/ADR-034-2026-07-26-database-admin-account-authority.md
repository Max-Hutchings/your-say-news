# ADR-034 — Database admin account authority

## Situation

The browser administration workspace now needs to expose user identity data and account-changing
controls. Administrators must be able to promote other accounts to administrator, make accounts
official publishers, and deactivate accounts immediately.

The existing model stores `STANDARD` and `OFFICIAL` account types in Postgres, while Keycloak has a
separate `admin` realm role. Relying on the Keycloak role for new administrators would require every
promotion to update two systems and would leave issued tokens stale. The account's existing
`active` flag currently affects publishing only, not the rest of the authenticated API.

## Options considered

1. Keep account type and administrator permission separate, updating both Postgres and Keycloak.
2. Make Keycloak's realm role the sole administrator authority.
3. Make the application user row the authority for the three mutually exclusive account types.

## Decision

Choose option 3.

`your_say_user.account_type` stores exactly:

```text
USER | OFFICIAL | ADMIN
```

Keycloak authenticates the browser through Authorization Code with PKCE. The backend then resolves
the token's email to the local user row and permits account-management operations only when that
row is active and has type `ADMIN`. A promoted administrator therefore receives the capability on
their next API request without a Keycloak role mutation or token refresh.

The existing Keycloak `user` realm role remains the broad authenticated-application role during
the transition. The legacy Keycloak `admin` role is not authoritative for account management.

`OFFICIAL` means an official poster. Promoting an account to `OFFICIAL` assigns publisher status
`ACTIVE`; changing it to `USER` or `ADMIN` assigns publisher status `NONE`. The existing
`publisher_status` field remains available for operational publishing suspension.

The database `active` flag is authoritative for application functionality. An existing inactive
local account is rejected by authenticated API requests, including administration requests.
Keycloak may still authenticate that person; this separation lets deactivation take effect
immediately without managing identity-provider state.

The admin list exposes only the PII required for account administration: id, name, email, creation
date, account type and active state. It does not expose date of birth, characteristic answers,
consent details or private interests.

The React admin assets remain publicly loadable so the single-page application can initiate the
OIDC redirect. All user data and mutations remain protected server-side.

## Reason

A single application-owned authority makes promotions and deactivations immediate and atomic.
Mutually exclusive types match the product vocabulary, while retaining publisher status preserves
the independent operational control already used by posting endpoints.

## Consequences and follow-up

- Migrate existing `STANDARD` values to `USER`.
- Seed one active database administrator for local bootstrap.
- Existing code and clients must adopt the renamed `USER` enum value.
- Future admin APIs should reuse the same active database-admin check.
- A future permission model may replace the single `ADMIN` type if administration needs granular
  capabilities.
- Removing legacy Keycloak `admin` assignments is separate cleanup after all admin endpoints use
  database authorization.
