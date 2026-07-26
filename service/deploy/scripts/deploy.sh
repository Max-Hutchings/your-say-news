#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
deploy_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
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

compose() {
  docker compose --env-file "$runtime_env" \
    --file "$compose_file" \
    "$@"
}

compose pull post-service alloy cloudflared
compose --profile migration pull migrate
compose --profile migration run --rm migrate
compose up --detach --remove-orphans post-service alloy cloudflared

PRIVATE_HEALTH_URL="${PRIVATE_HEALTH_URL:-http://127.0.0.1:8082/live}" \
  PUBLIC_HEALTH_URL="${PUBLIC_HEALTH_URL:-}" \
  "$script_dir/health-check.sh"

compose ps
