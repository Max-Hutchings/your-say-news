# Test accounts

Seeded login accounts for local/dev environments. These exist in **two places that must stay in
sync by email**:

- **Keycloak** — `keycloak/realm-export.json` (auto-imported on startup), realm `your-say-news`.
- **Database** — the user-service seed changelogs under
  `user-service/src/main/resources/db/seeding/` (applied by the seeding step). Base users are in
  `0001`, additional active accounts in `0003`, the clean onboarding account in `0004`, and
  official publisher classifications in `0005`.

The join key between a Keycloak identity and its `YourSayUser` row is **email** (`YourSayUser.email`
is unique, and the backend provisions users from token claims). Keep the email lists identical.

## Accounts

| Username | Email | Password | Enabled | Account type | Roles | Characteristics | Seeded posts | Intended use |
|---|---|---|---|---|---|---|---:|---|
| john.doe | john.doe@example.com | password123 | yes | **Official** | user, admin | filled | 10 | Admin and established-user flows |
| jane.smith | jane.smith@example.com | password123 | yes | **Official** | user | filled | 10 | Established-user flows |
| bob.johnson | bob.johnson@example.com | password123 | **no** | Standard user | user | filled | 0 | Inactive-user path; cannot log in |
| alice.williams | alice.williams@example.com | password123 | yes | **Official** | user | filled | 10 | Established-user/feed flows |
| maya.patel | maya.patel@example.com | password123 | yes | **Official** | user | filled | 10 | Established-user/feed flows |
| theo.campbell | theo.campbell@example.com | password123 | yes | **Official** | user | filled | 10 | Established-user/feed flows |
| casey.morgan | casey.morgan@example.com | password123 | yes | Standard user | user | **none** | 0 | Clean consent and characteristics onboarding |

Notes:

- Keycloak `enabled` mirrors the seed `active` flag. **Bob is inactive** (`active: false` in the
  seed → `enabled: false` in Keycloak), so he cannot log in — this is intentional, to exercise the
  inactive-user path.
- `john.doe` is the only **admin**.
- Official accounts map to database `account_type: OFFICIAL`; standard users map to
  `account_type: STANDARD`. All five official test accounts have `publisher_status: ACTIVE`.
- `casey.morgan` is the login-ready onboarding account. Casey has no consent timestamp and no
  `user_characteristic` row, so a fresh session goes through privacy consent and then the full
  characteristics wizard. Do not add characteristics or posts for Casey.
- `alice.williams` is no longer an onboarding fixture: Alice has a complete characteristic profile
  and seeded post data, so an already-consented Alice goes directly to the feed.
- `nora.new@example.com` and `blank.user@example.com` are database-only, unprofiled fixtures used by
  backend integration tests. They are intentionally absent from Keycloak and cannot be used to log in.
- Realm: `your-say-news`. Backend client: `app-backend`. Frontend (mobile/web, PKCE) client:
  `frontend-client`.

> When adding or changing a test account, update **both** the realm export and the seed changelog
> so they continue to match by email.
