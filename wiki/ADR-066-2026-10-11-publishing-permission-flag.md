# ADR-066: Publishing permission flag

## Situation

Only `OFFICIAL` accounts could publish. `publisher_status` (`NONE`/`ACTIVE`/`SUSPENDED`) was tied
to the account type by a database constraint, and account types are mutually exclusive (ADR-034).
An administrator therefore could not publish without giving up admin access. We want to fix the
model now, before there is real account data to backfill.

## Options considered

1. Keep the model; admins who post use a second, `OFFICIAL` account.
2. Keep `publisher_status` and allow `ADMIN` accounts to be `ACTIVE`.
3. Replace `publisher_status` with an on/off publishing permission, separate from account type.

## Decision

Option 3.

- `your_say_user.publishing_enabled` (boolean) replaces `publisher_status`. Migration 0019 carries
  over `OFFICIAL` + `ACTIVE` as `true`; everything else becomes `false`.
- Account types stay `USER`, `OFFICIAL`, `ADMIN`.
- `USER` accounts can never publish (database check `ck_your_say_user_user_cannot_publish`).
- Defaults: on for `OFFICIAL`, off for `ADMIN`. Changing an account's type resets publishing to the new
  type's default; reassigning the same type keeps the current setting.
- Admins switch publishing on or off per account in the admin UI. `PUT /api/admin/users/{id}` takes an
  optional `publishingEnabled`; an explicit value wins over the type default, and `true` on a `USER`
  is refused with `USER_ACCOUNT_CANNOT_PUBLISH`.
- Can publish = account active and publishing enabled. Posting and Pepper drafting accept `OFFICIAL`
  or `ADMIN` accounts with the permission. Auto-post still publishes only as the official account.

## Reason

- One permission, one place: admins can post without a second account, and suspending an official
  poster is just turning publishing off.
- "Suspended" vs "none" was a distinction nobody used; a boolean is simpler to show and to audit.
- Changing it now avoids migrating real accounts later.

## Consequences and follow-up

- The API drops `publisherStatus` from the user and access views; clients use `canPublish`. The admin
  account view adds `publishingEnabled`.
- Edited seed changesets accept any checksum; existing local databases keep their seeded rows, which
  migration 0019 backfills.
- Supersedes the publisher-status parts of ADR-023 and ADR-034.
