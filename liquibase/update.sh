#!/usr/bin/env sh
# Shared Liquibase runner used by the migration and seeding containers.
#
# Runs `liquibase update` against the app database from the central changelog.
# The search path ensures includeAll changeSets are recorded under the
# same "db/migrations/..." / "db/seeding/..." logical filenames the Quarkus app
# uses at migrate-at-start. That keeps DATABASECHANGELOG consistent between this
# container and the running service (no double-runs).
#
# Behaviour is selected by env vars (set in the respective Dockerfile):
#   CHANGELOG_FILE      - changelog path (master vs seed)
#   CHANGELOG_CONTEXTS  - when set (e.g. "seed"), limits which changeSets run
set -eu

: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

CHANGELOG_FILE="${CHANGELOG_FILE:-db/db.changelog-master.xml}"

# Seed mode only: forget prior seeding changeSets so every one re-runs on the next update. Combined
# with the 0000-reset-seed-data changeSets (which TRUNCATE first), this makes each seed run a clean
# drop-and-reseed. The migration container never sets CHANGELOG_CONTEXTS, so migrations are untouched.
reset_seed_history() {
  echo "==> Resetting seed history (drop-and-reseed)"
  liquibase \
    --url="$DB_URL" \
    --username="$DB_USERNAME" \
    --password="$DB_PASSWORD" \
    execute-sql --sql="DELETE FROM databasechangelog WHERE filename LIKE 'db/seeding/%';"
}

run() {
  search_path="$1"
  changelog="$2"
  set -- \
    --search-path="$search_path" \
    --changelog-file="$changelog" \
    --url="$DB_URL" \
    --username="$DB_USERNAME" \
    --password="$DB_PASSWORD"
  if [ -n "${CHANGELOG_CONTEXTS:-}" ]; then
    liquibase "$@" update --contexts="$CHANGELOG_CONTEXTS"
  else
    liquibase "$@" update
  fi
}

if [ -n "${CHANGELOG_CONTEXTS:-}" ]; then
  reset_seed_history
fi

echo "==> central changelog: $CHANGELOG_FILE"
run /liquibase/changelog "$CHANGELOG_FILE"

echo "==> Liquibase run complete."
