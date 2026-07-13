#!/usr/bin/env bash
set -uo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
frontend_dir="$repo_root/frontend/mobile/your-say-news"

user_service_status=0
post_service_status=0
frontend_status=0

echo "Running user-service tests with Testcontainers..."
(cd "$repo_root" && QUARKUS_HTTP_TEST_PORT=0 ./gradlew :user-service:test) || user_service_status=$?

echo
echo "Running post-service tests with Testcontainers..."
(cd "$repo_root" && QUARKUS_HTTP_TEST_PORT=0 ./gradlew :post-service:test) || post_service_status=$?

echo
echo "Running frontend tests..."
(cd "$frontend_dir" && bun run test -- --runInBand) || frontend_status=$?

echo
echo "Test summary:"
printf '  user-service: %s\n' "$([[ $user_service_status == 0 ]] && echo PASS || echo FAIL)"
printf '  post-service: %s\n' "$([[ $post_service_status == 0 ]] && echo PASS || echo FAIL)"
printf '  frontend:     %s\n' "$([[ $frontend_status == 0 ]] && echo PASS || echo FAIL)"

if (( user_service_status != 0 || post_service_status != 0 || frontend_status != 0 )); then
  exit 1
fi
