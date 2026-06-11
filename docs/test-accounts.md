# Test accounts

Seeded login accounts for local/dev environments. These exist in **two places that must stay in
sync by email**:

- **Keycloak** — `keycloak/realm-export.json` (auto-imported on startup), realm `your-say-news`.
- **Database** — `user-service/src/main/resources/db/seeding/0001-seed-your-say-users.yaml`
  (applied by the seeding step).

The join key between a Keycloak identity and its `YourSayUser` row is **email** (`YourSayUser.email`
is unique, and the backend provisions users from token claims). Keep the email lists identical.

## Accounts

| Username | Email | Password | Enabled | Roles |
|---|---|---|---|---|
| john.doe | john.doe@example.com | password123 | yes | user, admin |
| jane.smith | jane.smith@example.com | password123 | yes | user |
| bob.johnson | bob.johnson@example.com | password123 | **no** | user |
| alice.williams | alice.williams@example.com | password123 | yes | user |

Notes:

- Keycloak `enabled` mirrors the seed `active` flag. **Bob is inactive** (`active: false` in the
  seed → `enabled: false` in Keycloak), so he cannot log in — this is intentional, to exercise the
  inactive-user path.
- `john.doe` is the only **admin**.
- Realm: `your-say-news`. Backend client: `app-backend`. Frontend (mobile/web, PKCE) client:
  `frontend-client`.

> When adding or changing a test account, update **both** the realm export and the seed changelog
> so they continue to match by email.
