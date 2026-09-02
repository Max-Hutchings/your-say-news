#!/usr/bin/env bash
set -euo pipefail

output_file=${1:?Usage: render-runtime-env.sh OUTPUT_FILE}
temporary_file="${output_file}.tmp.$$"

cleanup() {
  rm -f "$temporary_file"
}
trap cleanup EXIT

required_variables=(
  POST_SERVICE_IMAGE
  MIGRATION_IMAGE
  ALLOY_IMAGE
  DB_URL
  DB_REACTIVE_URL
  MIGRATION_DB_USERNAME
  MIGRATION_DB_PASSWORD
  DB_USERNAME
  DB_PASSWORD
  OIDC_AUTH_SERVER_URL
  OIDC_CLIENT_ID
  S3_ENDPOINT
  S3_ACCESS_KEY_ID
  S3_SECRET_ACCESS_KEY
  POSTS_MEDIA_BUCKET
  GRAFANA_CLOUD_OTLP_ENDPOINT
  GRAFANA_CLOUD_OTLP_AUTHORIZATION
)

missing_variables=()
for variable_name in "${required_variables[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    missing_variables+=("$variable_name")
  fi
done

agent_provider=${AGENT_PROVIDER:-openai}
case "$agent_provider" in
  openai)
    selected_agent_api_key=${OPENAI_API_KEY:-}
    selected_agent_api_key_name=OPENAI_API_KEY
    selected_agent_model=${OPENAI_MODEL:-gpt-5.6}
    ;;
  grok)
    selected_agent_api_key=${XAI_API_KEY:-}
    selected_agent_api_key_name=XAI_API_KEY
    selected_agent_model=${GROK_MODEL:-grok-4.5}
    ;;
  *)
    echo 'AGENT_PROVIDER must be either openai or grok.' >&2
    exit 1
    ;;
esac

if [[ -z "$selected_agent_api_key" ]]; then
  printf 'Missing deployment value for selected AI provider: %s\n' \
    "$selected_agent_api_key_name" >&2
  exit 1
fi

if (( ${#missing_variables[@]} > 0 )); then
  printf 'Missing deployment values: %s\n' "${missing_variables[*]}" >&2
  exit 1
fi

assert_digest() {
  local variable_name=$1
  local value=${!variable_name}

  if [[ ! "$value" =~ ^[^[:space:]]+@sha256:[0-9a-f]{64}$ ]]; then
    printf '%s must contain an immutable sha256 image digest.\n' "$variable_name" >&2
    exit 1
  fi
}

assert_single_line() {
  local variable_name=$1
  local value=$2

  if [[ "$value" == *$'\n'* || "$value" == *$'\r'* ]]; then
    printf '%s must be a single-line value.\n' "$variable_name" >&2
    exit 1
  fi
}

write_literal() {
  local variable_name=$1
  local value=$2

  assert_single_line "$variable_name" "$value"
  printf '%s=%s\n' "$variable_name" "$value" >> "$temporary_file"
}

write_quoted() {
  local variable_name=$1
  local value=$2
  local escaped_value

  assert_single_line "$variable_name" "$value"
  escaped_value=${value//\'/\\\'}
  printf "%s='%s'\n" "$variable_name" "$escaped_value" >> "$temporary_file"
}

assert_digest POST_SERVICE_IMAGE
assert_digest MIGRATION_IMAGE
assert_digest ALLOY_IMAGE

vote_suppression_threshold=${VOTE_SUPPRESSION_THRESHOLD:-5}
if [[ ! "$vote_suppression_threshold" =~ ^[0-9]+$ ]] || (( vote_suppression_threshold < 5 )); then
  echo 'VOTE_SUPPRESSION_THRESHOLD must be an integer of at least 5.' >&2
  exit 1
fi

umask 077
: > "$temporary_file"

write_literal POST_SERVICE_IMAGE "$POST_SERVICE_IMAGE"
write_literal MIGRATION_IMAGE "$MIGRATION_IMAGE"
write_literal ALLOY_IMAGE "$ALLOY_IMAGE"
write_literal POST_SERVICE_HOST_PORT "${POST_SERVICE_HOST_PORT:-8082}"
write_quoted DB_URL "$DB_URL"
write_quoted DB_REACTIVE_URL "$DB_REACTIVE_URL"
write_quoted MIGRATION_DB_USERNAME "$MIGRATION_DB_USERNAME"
write_quoted MIGRATION_DB_PASSWORD "$MIGRATION_DB_PASSWORD"
write_quoted DB_USERNAME "$DB_USERNAME"
write_quoted DB_PASSWORD "$DB_PASSWORD"
write_literal DB_JDBC_MAX_SIZE "${DB_JDBC_MAX_SIZE:-4}"
write_literal DB_REACTIVE_MAX_SIZE "${DB_REACTIVE_MAX_SIZE:-8}"
write_quoted OIDC_AUTH_SERVER_URL "$OIDC_AUTH_SERVER_URL"
write_quoted OIDC_CLIENT_ID "$OIDC_CLIENT_ID"
write_quoted S3_ENDPOINT "$S3_ENDPOINT"
write_literal S3_REGION "${S3_REGION:-auto}"
write_quoted S3_ACCESS_KEY_ID "$S3_ACCESS_KEY_ID"
write_quoted S3_SECRET_ACCESS_KEY "$S3_SECRET_ACCESS_KEY"
write_quoted POSTS_MEDIA_BUCKET "$POSTS_MEDIA_BUCKET"
write_literal AGENT_PROVIDER "$agent_provider"
write_quoted AGENT_API_KEY "$selected_agent_api_key"
write_literal AGENT_MODEL "$selected_agent_model"
write_quoted GRAFANA_CLOUD_OTLP_ENDPOINT "$GRAFANA_CLOUD_OTLP_ENDPOINT"
write_quoted GRAFANA_CLOUD_OTLP_AUTHORIZATION "$GRAFANA_CLOUD_OTLP_AUTHORIZATION"
write_literal VOTE_SUPPRESSION_THRESHOLD "$vote_suppression_threshold"

chmod 0600 "$temporary_file"
mv "$temporary_file" "$output_file"
trap - EXIT
