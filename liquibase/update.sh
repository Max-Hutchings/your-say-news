#!/usr/bin/env sh
# Shared Liquibase runner used by the migration and seeding containers.
#
# Runs `liquibase update` against the shared app database once per service.
# Each service is given its own --search-path so the includeAll changeSets are
# recorded under the same "db/migrations/..." / "db/seeding/..." filenames the
# Quarkus app uses at migrate-at-start. That keeps the DATABASECHANGELOG history
# consistent between this container and the running services (no double-runs).
#
# Behaviour is selected by env vars (set in the respective Dockerfile):
#   CHANGELOG_USER / CHANGELOG_POST  - changelog path per service (master vs seed)
#   CHANGELOG_CONTEXTS               - when set (e.g. "seed"), limits which changeSets run
set -eu

: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

CHANGELOG_USER="${CHANGELOG_USER:-db/db.changelog-master.yaml}"
CHANGELOG_POST="${CHANGELOG_POST:-db/db.changelog-master.xml}"

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

echo "==> user-service: $CHANGELOG_USER"
run /liquibase/changelog/user-service "$CHANGELOG_USER"

echo "==> post-service: $CHANGELOG_POST"
run /liquibase/changelog/post-service "$CHANGELOG_POST"

echo "==> Liquibase run complete."
