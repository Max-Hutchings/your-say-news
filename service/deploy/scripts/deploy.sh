#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
deploy_dir=$(CDPATH='' cd -- "$script_dir/.." && pwd)
runtime_env="${DEPLOY_ENV_FILE:-$deploy_dir/runtime.env}"
compose_file="$deploy_dir/compose.yaml"

if [ ! -f "$runtime_env" ]; then
  echo "Runtime environment file not found: $runtime_env" >&2
  echo "Copy env.example to runtime.env and populate it through the deployment secret store." >&2
  exit 1
fi

assert_digest() {
  variable_name="$1"
  value=$(sed -n "s/^${variable_name}=//p" "$runtime_env" | tail -n 1)

  if ! printf '%s\n' "$value" | grep -Eq '^.+@sha256:[0-9a-f]{64}$'; then
    echo "$variable_name must contain an immutable sha256 image digest." >&2
    exit 1
  fi
}

assert_digest POST_SERVICE_IMAGE
assert_digest MIGRATION_IMAGE
assert_digest ALLOY_IMAGE

compose() {
  docker compose --env-file "$runtime_env" \
    --file "$compose_file" \
    "$@"
}

compose pull post-service alloy
compose --profile migration pull migrate
compose --profile migration run --rm migrate
# Only the credential setup container's output is printed: CI logs are public, and it emits nothing
# but copy/permission errors. post-service logs stay on the host.
if ! compose up --detach --remove-orphans post-service alloy; then
  echo "Compose startup failed. firebase-credentials output:" >&2
  compose logs --no-color firebase-credentials >&2 || true
  exit 1
fi

PRIVATE_HEALTH_URL="${PRIVATE_HEALTH_URL:-http://127.0.0.1:8082/api/live}" \
  PUBLIC_HEALTH_URL="${PUBLIC_HEALTH_URL:-}" \
  "$script_dir/health-check.sh"

# A crash-looping Alloy silently drops all telemetry while post-service stays healthy, so check it
# after the health checks have given it time to fail. Its logs carry config and export errors only;
# the OTLP endpoint and credentials are GitHub secrets, which Actions masks in the public log.
alloy_container=$(compose ps --all --quiet alloy)
if [ -n "$alloy_container" ]; then
  alloy_state=$(docker inspect --format '{{.RestartCount}} {{.State.Status}}' "$alloy_container")
else
  alloy_state='no container'
fi
if [ "$alloy_state" != '0 running' ]; then
  echo "Alloy is not running cleanly (restarts, status: $alloy_state). Alloy output:" >&2
  compose logs --no-color --tail 50 alloy >&2 || true
  exit 1
fi

compose ps
