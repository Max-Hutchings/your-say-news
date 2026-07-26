# Admin account management

## Goal

Replace the placeholder browser admin workspace with an authenticated account-management desk.
Active admins can list every application account, change its type between `USER`, `OFFICIAL` and
`ADMIN`, and activate or deactivate it.

## Backend

- Migrate `account_type` from `STANDARD` to `USER` and add `ADMIN`.
- Keep `publisher_status` as the operational publishing state:
  - `OFFICIAL` accounts are assigned `ACTIVE` when promoted;
  - `USER` and `ADMIN` accounts always carry `NONE`.
- Add admin-specific DTOs and endpoints under `/api/admin/users`, outside the `/admin` SPA root.
- Authenticate with Keycloak, then authorize account management from the active database account.
- Reject authenticated API requests made by an existing inactive database account.
- Seed a dedicated `admin@yoursay.com` account as the initial application admin so established
  official-publisher fixtures keep their separate publishing responsibility.

## Admin web UI

- Add a browser PKCE login using a dedicated public Keycloak client.
- Keep domain code under `src/features`, route-level composition under `src/pages`, and shared
  primitives under `src/shared`.
- Replace the placeholder with a responsive account ledger:
  - search and filter the complete account list;
  - edit account type;
  - activate/deactivate accounts;
  - show explicit loading, empty, forbidden and error states.

## Verification

- Unit-test account-type publishing invariants.
- Integration-test listing, promotion, deactivation, authorization and inactive-account denial.
- Test the admin API client and account-management interactions in the React UI.
- Run backend tests, admin UI tests/typecheck/build, and the mobile tests affected by the enum
  rename.
