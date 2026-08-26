# Test accounts

Seeded login accounts for local development. These exist in **two places that must stay in
sync by email**:

- **Firebase Authentication Emulator** - `firebase/test-accounts.json`. The
  `firebase-auth-seed` Compose job reconciles these accounts on every startup.
- **Database** — the user-domain seed changelogs under
  `liquibase/changelog/db/user-seeding/` (applied by the seeding step). Base users are in
  `0001`, additional active accounts in `0003`, the clean onboarding account in `0004`, and
  official publisher classifications in `0005`. The profiled reader is in `0006`; the initial
  bootstrap assignment is in `0007`, and `0008` separates it into the dedicated admin account.
  `0012` backfills consent for the four original accounts, and `0013` adds the profiled
  account that deliberately has no consent.

The temporary local join key is verified **email**. Hosted Firebase must use the immutable UID link
required by ADR-028 before release. Keep the emulator and database email lists identical.

## Accounts

| Name | Email | Password | Enabled | Account type | Characteristics | Seeded posts | Intended use |
|---|---|---|---|---|---|---:|---|
| YourSay Admin | admin@yoursay.com | password123 | yes | **Admin** | **none** | 0 | Admin account-management flows |
| John Doe | john.doe@example.com | password123 | yes | **Official** | filled | 10 | Established-user and publishing flows |
| Jane Smith | jane.smith@example.com | password123 | yes | **Official** | filled | 10 | Established-user flows |
| Bob Johnson | bob.johnson@example.com | password123 | **no** | User | filled | 0 | Inactive-user path; cannot log in |
| Alice Williams | alice.williams@example.com | password123 | yes | **Official** | filled | 10 | Established-user/feed flows |
| Maya Patel | maya.patel@example.com | password123 | yes | **Official** | filled | 10 | Established-user/feed flows |
| Theo Campbell | theo.campbell@example.com | password123 | yes | **Official** | filled | 10 | Established-user/feed flows |
| Casey Morgan | casey.morgan@example.com | password123 | yes | User | **none** | 0 | Clean consent and characteristics onboarding |
| Riley Reader | riley.reader@example.com | password123 | yes | User | filled | 0 | Fully onboarded reader; cannot publish |
| Sam Okafor | sam.okafor@example.com | password123 | yes | User | filled | 0 | Profile filled but consent never recorded; stops on the privacy promise |

Notes:

- Firebase `disabled` mirrors the seed `active` flag. **Bob is inactive**, so he cannot log in.
  This exercises the inactive-user path.
- `admin@yoursay.com` is the bootstrap **admin**. Firebase has no admin role; authorization comes
  from the application database.
- Official accounts map to database `account_type: OFFICIAL`; readers map to `account_type: USER`;
  and the dedicated administration account maps to `account_type: ADMIN`. Official test accounts
  have `publisher_status: ACTIVE`; users and admins have `publisher_status: NONE`.
- `casey.morgan` is the login-ready onboarding account. Casey has no consent timestamp and no
  `user_characteristic` row, so a fresh session goes through privacy consent and then the full
  characteristics wizard. Do not add characteristics or posts for Casey.
- `riley.reader` is the login-ready reader account. Riley has consent and a complete
  characteristic profile, so a fresh session goes directly to the feed without publishing access.
- `alice.williams` is no longer an onboarding fixture: Alice has a complete characteristic profile
  and seeded post data, so an already-consented Alice goes directly to the feed.
- `nora.new@example.com` and `blank.user@example.com` are database-only, unprofiled fixtures used by
  backend integration tests. They are intentionally absent from Firebase and cannot be used to log in.

> When adding or changing a test account, update **both** `firebase/test-accounts.json` and the seed
> changelog. A normal `docker compose up` or `bun run dev` restart reconciles Firebase.
