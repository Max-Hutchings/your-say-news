#!/usr/bin/env bash
set -euo pipefail

deploy_root=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
test_directory=$(mktemp -d /tmp/ysn-deploy-tests.XXXXXX)

cleanup() {
  rm -rf "$test_directory"
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_file_contains() {
  local file=$1
  local expected=$2
  grep -Fqx -- "$expected" "$file" || fail "$file does not contain: $expected"
}

export POST_SERVICE_IMAGE='ghcr.io/max-hutchings/your-say-news-post-service-snapshot@sha256:1111111111111111111111111111111111111111111111111111111111111111'
export MIGRATION_IMAGE='ghcr.io/max-hutchings/your-say-news-migrations-snapshot@sha256:2222222222222222222222222222222222222222222222222222222222222222'
export ALLOY_IMAGE='grafana/alloy@sha256:3333333333333333333333333333333333333333333333333333333333333333'
export DB_URL='jdbc:postgresql://pg-development.example:5432/your_say_news?sslmode=require'
export DB_REACTIVE_URL='postgresql://pg-development.example:5432/your_say_news?sslmode=require'
export MIGRATION_DB_USERNAME='ysn_migration'
export MIGRATION_DB_PASSWORD='representative-migration-password'
export DB_USERNAME='ysn_runtime'
export DB_PASSWORD="representative'password\$with # symbols"
export OIDC_AUTH_SERVER_URL='https://auth-development.yoursaynews.com/realms/your-say-news'
export OIDC_CLIENT_ID='your-say-news-mobile-development'
export S3_ENDPOINT='https://9538d45e127bdb7d6b1bf1ecf9020146.eu.r2.cloudflarestorage.com'
export S3_ACCESS_KEY_ID='development-r2-access-key'
export S3_SECRET_ACCESS_KEY="representative'r2\$secret"
export POSTS_MEDIA_BUCKET='your-say-news-media-development'
export AGENT_PROVIDER='openai'
export OPENAI_API_KEY='openai-representative-development-key'
export OPENAI_MODEL='gpt-5.6-custom'
export XAI_API_KEY='xai-unselected-development-key'
export GRAFANA_CLOUD_OTLP_ENDPOINT='https://otlp-gateway-prod-eu-west.grafana.net/otlp'
export GRAFANA_CLOUD_OTLP_AUTHORIZATION='Basic representative-authorization=='

runtime_env="$test_directory/runtime.env"
"$deploy_root/scripts/render-runtime-env.sh" "$runtime_env"

[[ $(stat -c '%a' "$runtime_env") == 600 ]] || fail 'runtime.env must use mode 0600'
assert_file_contains "$runtime_env" "POST_SERVICE_IMAGE=$POST_SERVICE_IMAGE"
assert_file_contains "$runtime_env" "MIGRATION_IMAGE=$MIGRATION_IMAGE"
assert_file_contains "$runtime_env" "ALLOY_IMAGE=$ALLOY_IMAGE"
assert_file_contains "$runtime_env" "DB_PASSWORD='representative\'password\$with # symbols'"
assert_file_contains "$runtime_env" 'AGENT_PROVIDER=openai'
assert_file_contains "$runtime_env" "AGENT_API_KEY='openai-representative-development-key'"
assert_file_contains "$runtime_env" 'AGENT_MODEL=gpt-5.6-custom'
if grep -Fq -- 'XAI_API_KEY=' "$runtime_env" \
  || grep -Fq -- 'OPENAI_API_KEY=' "$runtime_env" \
  || grep -Fq -- 'xai-unselected-development-key' "$runtime_env"; then
  fail 'runtime.env exposes provider-specific or unselected AI credentials'
fi
assert_file_contains "$runtime_env" 'VOTE_SUPPRESSION_THRESHOLD=5'
docker compose --env-file "$runtime_env" --file "$deploy_root/compose.yaml" config --quiet
compose_config="$test_directory/compose-config.yaml"
docker compose --env-file "$runtime_env" --file "$deploy_root/compose.yaml" \
  --profile migration config > "$compose_config"
grep -Fq -- 'QUARKUS_HTTP_ROOT_PATH: /api' "$compose_config" \
  || fail 'Compose does not expose the remote API below /api'
grep -Fq -- "image: $POST_SERVICE_IMAGE" "$compose_config" \
  || fail 'Compose does not use the exact post-service snapshot digest'
grep -Fq -- "image: $MIGRATION_IMAGE" "$compose_config" \
  || fail 'Compose does not use the exact migration snapshot digest'
grep -Fq -- 'AGENT_PROVIDER: openai' "$compose_config" \
  || fail 'Compose does not pass the selected AI provider'
grep -Fq -- 'AGENT_API_KEY: openai-representative-development-key' "$compose_config" \
  || fail 'Compose does not pass the selected AI credential'
grep -Fq -- 'AGENT_MODEL: gpt-5.6-custom' "$compose_config" \
  || fail 'Compose does not pass the selected AI model'
if grep -Fq -- 'XAI_API_KEY:' "$compose_config" \
  || grep -Fq -- 'OPENAI_API_KEY:' "$compose_config" \
  || grep -Fq -- 'xai-unselected-development-key' "$compose_config"; then
  fail 'Compose exposes provider-specific or unselected AI credentials'
fi

grok_runtime_env="$test_directory/grok-runtime.env"
AGENT_PROVIDER='grok' OPENAI_API_KEY='openai-unselected-development-key' \
  XAI_API_KEY='xai-representative-development-key' \
  GROK_MODEL='grok-4.6-custom' \
  "$deploy_root/scripts/render-runtime-env.sh" "$grok_runtime_env"
assert_file_contains "$grok_runtime_env" 'AGENT_PROVIDER=grok'
assert_file_contains "$grok_runtime_env" "AGENT_API_KEY='xai-representative-development-key'"
assert_file_contains "$grok_runtime_env" 'AGENT_MODEL=grok-4.6-custom'
if grep -Fq -- 'openai-unselected-development-key' "$grok_runtime_env"; then
  fail 'Grok runtime.env exposes the unselected OpenAI credential'
fi

default_openai_runtime_env="$test_directory/default-openai-runtime.env"
env -u AGENT_PROVIDER -u OPENAI_MODEL \
  OPENAI_API_KEY='openai-default-model-key' \
  "$deploy_root/scripts/render-runtime-env.sh" "$default_openai_runtime_env"
assert_file_contains "$default_openai_runtime_env" 'AGENT_PROVIDER=openai'
assert_file_contains "$default_openai_runtime_env" 'AGENT_MODEL=gpt-5.6'

default_grok_runtime_env="$test_directory/default-grok-runtime.env"
env -u GROK_MODEL AGENT_PROVIDER='grok' \
  XAI_API_KEY='grok-default-model-key' \
  "$deploy_root/scripts/render-runtime-env.sh" "$default_grok_runtime_env"
assert_file_contains "$default_grok_runtime_env" 'AGENT_MODEL=grok-4.5'

if AGENT_PROVIDER='openai' OPENAI_API_KEY='' \
  "$deploy_root/scripts/render-runtime-env.sh" "$test_directory/missing-provider-key.env" \
  >/dev/null 2>&1; then
  fail 'render-runtime-env accepted a missing OpenAI key for the selected provider'
fi

if AGENT_PROVIDER='grok' XAI_API_KEY='' \
  "$deploy_root/scripts/render-runtime-env.sh" "$test_directory/missing-grok-key.env" \
  >/dev/null 2>&1; then
  fail 'render-runtime-env accepted a missing Grok key for the selected provider'
fi

if AGENT_PROVIDER='unsupported' \
  "$deploy_root/scripts/render-runtime-env.sh" "$test_directory/invalid-provider.env" \
  >/dev/null 2>&1; then
  fail 'render-runtime-env accepted an unsupported AI provider'
fi

if OPENAI_MODEL=$'gpt-5.6\nINJECTED=value' \
  "$deploy_root/scripts/render-runtime-env.sh" "$test_directory/multiline-model.env" \
  >/dev/null 2>&1; then
  fail 'render-runtime-env accepted a multiline AI model value'
fi

if POST_SERVICE_IMAGE='mutable-image:latest' \
  "$deploy_root/scripts/render-runtime-env.sh" "$test_directory/invalid-digest.env" >/dev/null 2>&1; then
  fail 'render-runtime-env accepted a mutable application image'
fi

if VOTE_SUPPRESSION_THRESHOLD=4 \
  "$deploy_root/scripts/render-runtime-env.sh" "$test_directory/unsafe-threshold.env" >/dev/null 2>&1; then
  fail 'render-runtime-env accepted a privacy suppression threshold below 5'
fi

for mutable_variable in POST_SERVICE_IMAGE MIGRATION_IMAGE ALLOY_IMAGE; do
  mutable_runtime_env="$test_directory/mutable-${mutable_variable}.env"
  cp "$runtime_env" "$mutable_runtime_env"
  sed -i "s|^${mutable_variable}=.*|${mutable_variable}=mutable-image:latest|" "$mutable_runtime_env"
  if DEPLOY_ENV_FILE="$mutable_runtime_env" \
    "$deploy_root/scripts/deploy.sh" >/dev/null 2>&1; then
    fail "deploy.sh accepted mutable $mutable_variable"
  fi
done

fake_bin="$test_directory/fake-bin"
mkdir -p "$fake_bin"
command_log="$test_directory/commands.log"

cat > "$fake_bin/docker" <<'FAKE_DOCKER'
#!/usr/bin/env sh
printf 'docker %s\n' "$*" >> "$YSN_TEST_COMMAND_LOG"
FAKE_DOCKER

cat > "$fake_bin/curl" <<'FAKE_CURL'
#!/usr/bin/env sh
printf 'curl %s\n' "$*" >> "$YSN_TEST_COMMAND_LOG"
FAKE_CURL

cat > "$fake_bin/cloudflared" <<'FAKE_CLOUDFLARED'
#!/usr/bin/env sh
exit 0
FAKE_CLOUDFLARED

cat > "$fake_bin/systemctl" <<'FAKE_SYSTEMCTL'
#!/usr/bin/env sh
test "$1" = is-active
test "$2" = --quiet
test "$3" = cloudflared-ysn.service
FAKE_SYSTEMCTL

chmod 0755 "$fake_bin/"*

YSN_TEST_COMMAND_LOG="$command_log" PATH="$fake_bin:$PATH" \
  DEPLOY_ENV_FILE="$runtime_env" HEALTH_CHECK_ATTEMPTS=1 \
  "$deploy_root/scripts/deploy.sh" >/dev/null

expected_deploy_log="$test_directory/expected-deploy.log"
cat > "$expected_deploy_log" <<EXPECTED_DEPLOY
docker compose --env-file $runtime_env --file $deploy_root/compose.yaml pull post-service alloy
docker compose --env-file $runtime_env --file $deploy_root/compose.yaml --profile migration pull migrate
docker compose --env-file $runtime_env --file $deploy_root/compose.yaml --profile migration run --rm migrate
docker compose --env-file $runtime_env --file $deploy_root/compose.yaml up --detach --remove-orphans post-service alloy
curl --fail --silent --show-error --max-time 10 http://127.0.0.1:8082/api/live
docker compose --env-file $runtime_env --file $deploy_root/compose.yaml ps
EXPECTED_DEPLOY
cmp "$expected_deploy_log" "$command_log" || fail 'deploy.sh command order changed'

status_directory="$test_directory/bootstrap-status"
mkdir -p "$status_directory"
printf 'complete\n' > "$status_directory/bootstrap-complete"
YSN_TEST_COMMAND_LOG="$command_log" PATH="$fake_bin:$PATH" \
  BOOTSTRAP_STATUS_DIRECTORY="$status_directory" \
  "$deploy_root/scripts/verify-host.sh" >/dev/null

printf 'failed\n' > "$status_directory/bootstrap-failed"
if YSN_TEST_COMMAND_LOG="$command_log" PATH="$fake_bin:$PATH" \
  BOOTSTRAP_STATUS_DIRECTORY="$status_directory" \
  "$deploy_root/scripts/verify-host.sh" >/dev/null 2>&1; then
  fail 'verify-host accepted a failed bootstrap marker'
fi

release_test_root="$test_directory/release-root"
new_release="$release_test_root/releases/new-commit"
previous_release="$release_test_root/releases/previous-commit"
outside_release="$release_test_root/outside-release"
outside_execution_marker="$test_directory/outside-release-executed"
outside_error="$test_directory/outside-release-error.log"
mkdir -p "$new_release/scripts" "$previous_release/scripts" "$outside_release/scripts"
cp "$deploy_root/compose.yaml" "$outside_release/compose.yaml"
cp "$runtime_env" "$outside_release/runtime.env"
cp "$deploy_root/scripts/health-check.sh" "$outside_release/scripts/health-check.sh"
cat > "$outside_release/scripts/deploy.sh" <<OUTSIDE_DEPLOY
#!/usr/bin/env sh
touch '$outside_execution_marker'
exit 0
OUTSIDE_DEPLOY
chmod 0755 "$outside_release/scripts/deploy.sh"

if DEPLOY_ROOT="$release_test_root" \
  "$deploy_root/scripts/release.sh" "$release_test_root/releases/../outside-release" \
  >/dev/null 2>"$outside_error"; then
  fail 'release.sh accepted a path traversal outside the releases directory'
fi
assert_file_contains "$outside_error" \
  "Release directory must be inside $release_test_root/releases."
[[ ! -e "$outside_execution_marker" ]] || fail 'path-traversal release was executed'

ln -s "$outside_release" "$release_test_root/releases/symlink-escape"
if DEPLOY_ROOT="$release_test_root" \
  "$deploy_root/scripts/release.sh" "$release_test_root/releases/symlink-escape" \
  >/dev/null 2>"$outside_error"; then
  fail 'release.sh accepted a symlink outside the releases directory'
fi
assert_file_contains "$outside_error" \
  "Release directory must be inside $release_test_root/releases."
[[ ! -e "$outside_execution_marker" ]] || fail 'symlinked outside release was executed'

nested_symlink_release="$release_test_root/releases/nested-symlink"
mkdir -p "$nested_symlink_release"
cp "$deploy_root/compose.yaml" "$nested_symlink_release/compose.yaml"
cp "$runtime_env" "$nested_symlink_release/runtime.env"
ln -s "$outside_release/scripts" "$nested_symlink_release/scripts"
if DEPLOY_ROOT="$release_test_root" \
  "$deploy_root/scripts/release.sh" "$nested_symlink_release" \
  >/dev/null 2>"$outside_error"; then
  fail 'release.sh accepted required scripts through an outside symlink'
fi
assert_file_contains "$outside_error" \
  "Release file must not be a symlink or escape its release: $nested_symlink_release/scripts/deploy.sh"
[[ ! -e "$outside_execution_marker" ]] || fail 'nested-symlink release was executed'

outside_health_check="$outside_release/outside-health-check.sh"
cat > "$outside_health_check" <<'OUTSIDE_HEALTH'
#!/usr/bin/env sh
exit 0
OUTSIDE_HEALTH
chmod 0755 "$outside_health_check"

health_symlink_release="$release_test_root/releases/health-symlink"
mkdir -p "$health_symlink_release/scripts"
cp "$deploy_root/compose.yaml" "$health_symlink_release/compose.yaml"
cp "$runtime_env" "$health_symlink_release/runtime.env"
cat > "$health_symlink_release/scripts/deploy.sh" <<'VALID_DEPLOY'
#!/usr/bin/env sh
exit 0
VALID_DEPLOY
chmod 0755 "$health_symlink_release/scripts/deploy.sh"
ln -s "$outside_health_check" "$health_symlink_release/scripts/health-check.sh"
if DEPLOY_ROOT="$release_test_root" \
  "$deploy_root/scripts/release.sh" "$health_symlink_release" \
  >/dev/null 2>"$outside_error"; then
  fail 'release.sh accepted an outside health-check symlink'
fi
assert_file_contains "$outside_error" \
  "Release file must not be a symlink or escape its release: $health_symlink_release/scripts/health-check.sh"

cp "$deploy_root/compose.yaml" "$new_release/compose.yaml"
cp "$deploy_root/compose.yaml" "$previous_release/compose.yaml"
cp "$runtime_env" "$new_release/runtime.env"
cp "$runtime_env" "$previous_release/runtime.env"
cp "$deploy_root/scripts/health-check.sh" "$new_release/scripts/health-check.sh"
cp "$deploy_root/scripts/health-check.sh" "$previous_release/scripts/health-check.sh"

cat > "$new_release/scripts/deploy.sh" <<'SUCCESSFUL_DEPLOY'
#!/usr/bin/env sh
exit 0
SUCCESSFUL_DEPLOY
chmod 0755 "$new_release/scripts/deploy.sh"
cp "$new_release/scripts/deploy.sh" "$previous_release/scripts/deploy.sh"

DEPLOY_ROOT="$release_test_root" "$deploy_root/scripts/release.sh" "$new_release" >/dev/null
[[ $(readlink -f "$release_test_root/current") == "$new_release" ]] \
  || fail 'successful release did not update the current symlink'

ln -sfn "$previous_release" "$release_test_root/current"
cat > "$new_release/scripts/deploy.sh" <<'FAILED_DEPLOY'
#!/usr/bin/env sh
exit 17
FAILED_DEPLOY
chmod 0755 "$new_release/scripts/deploy.sh"
: > "$command_log"

set +e
YSN_TEST_COMMAND_LOG="$command_log" PATH="$fake_bin:$PATH" \
  DEPLOY_ROOT="$release_test_root" HEALTH_CHECK_ATTEMPTS=1 \
  "$deploy_root/scripts/release.sh" "$new_release" >/dev/null 2>&1
release_status=$?
set -e

[[ $release_status -eq 17 ]] || fail "failed release returned $release_status instead of 17"
[[ $(readlink -f "$release_test_root/current") == "$previous_release" ]] \
  || fail 'failed release changed the current symlink'
assert_file_contains "$command_log" \
  "docker compose --env-file $previous_release/runtime.env --file $previous_release/compose.yaml up --detach --remove-orphans post-service alloy"
assert_file_contains "$command_log" \
  'curl --fail --silent --show-error --max-time 10 http://127.0.0.1:8082/api/live'
if grep -Fq -- '--profile migration' "$command_log"; then
  fail 'rollback attempted to reverse or rerun database migrations'
fi

malicious_previous="$release_test_root/releases/malicious-previous"
rollback_escape_marker="$test_directory/rollback-health-executed"
mkdir -p "$malicious_previous/scripts"
cp "$deploy_root/compose.yaml" "$malicious_previous/compose.yaml"
cp "$runtime_env" "$malicious_previous/runtime.env"
cp "$new_release/scripts/deploy.sh" "$malicious_previous/scripts/deploy.sh"
cat > "$outside_health_check" <<ROLLBACK_HEALTH
#!/usr/bin/env sh
touch '$rollback_escape_marker'
exit 0
ROLLBACK_HEALTH
chmod 0755 "$outside_health_check"
ln -s "$outside_health_check" "$malicious_previous/scripts/health-check.sh"
ln -sfn "$malicious_previous" "$release_test_root/current"
: > "$command_log"

set +e
YSN_TEST_COMMAND_LOG="$command_log" PATH="$fake_bin:$PATH" \
  DEPLOY_ROOT="$release_test_root" HEALTH_CHECK_ATTEMPTS=1 \
  "$deploy_root/scripts/release.sh" "$new_release" >/dev/null 2>&1
malicious_rollback_status=$?
set -e

[[ $malicious_rollback_status -eq 17 ]] \
  || fail "invalid rollback release returned $malicious_rollback_status instead of 17"
[[ ! -e "$rollback_escape_marker" ]] || fail 'rollback executed an outside health-check script'
if grep -Fq -- "--env-file $malicious_previous/runtime.env" "$command_log"; then
  fail 'rollback restored containers from a release with escaping required files'
fi

echo 'Deployment script tests passed.'
