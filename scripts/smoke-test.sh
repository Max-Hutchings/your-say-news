#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
smoke_project="yoursay-smoke"
smoke_compose="$repo_root/smoke-tests/compose.smoke.yaml"
artifacts_dir="$repo_root/smoke-tests/artifacts"
service_logs_dir="$artifacts_dir/services"
backend_pid=""
frontend_pid=""
cleanup_started=0
db_port="${SMOKE_DB_PORT:-55432}"
auth_port="${SMOKE_AUTH_PORT:-58080}"
storage_port="${SMOKE_STORAGE_PORT:-54566}"
backend_port="${SMOKE_BACKEND_PORT:-58082}"
frontend_port="${SMOKE_FRONTEND_PORT:-55173}"
admin_port="${SMOKE_ADMIN_PORT:-58083}"
application_url="http://localhost:${frontend_port}"
authentication_url="http://localhost:${auth_port}"
admin_url="http://localhost:${admin_port}"

export SMOKE_DB_PORT="$db_port"
export SMOKE_AUTH_PORT="$auth_port"
export SMOKE_STORAGE_PORT="$storage_port"

mkdir -p "$service_logs_dir"

listener_pids() {
  local port="$1"
  lsof -nP -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | sort -u
}

stop_listener() {
  local port="$1"
  local pids=()
  while IFS= read -r pid; do
    [[ -n "$pid" ]] && pids+=("$pid")
  done < <(listener_pids "$port")

  if (( ${#pids[@]} == 0 )); then
    return
  fi

  for pid in "${pids[@]}"; do
    kill -TERM "$pid" 2>/dev/null || true
  done

  local deadline=$((SECONDS + 5))
  while (( SECONDS < deadline )); do
    [[ -z "$(listener_pids "$port")" ]] && return
    sleep 0.2
  done

  while IFS= read -r pid; do
    [[ -n "$pid" ]] && kill -KILL "$pid" 2>/dev/null || true
  done < <(listener_pids "$port")
}

cleanup() {
  local exit_code=$?
  if (( cleanup_started == 1 )); then
    return
  fi
  cleanup_started=1
  trap - EXIT INT TERM

  echo
  echo "Stopping smoke-test services..."

  if [[ -n "$frontend_pid" ]]; then
    kill -TERM "$frontend_pid" 2>/dev/null || true
  fi
  if [[ -n "$backend_pid" ]]; then
    kill -TERM "$backend_pid" 2>/dev/null || true
  fi

  stop_listener "$frontend_port"
  stop_listener "$admin_port"
  stop_listener "$backend_port"

  docker compose \
    -p "$smoke_project" \
    -f "$repo_root/compose.yaml" \
    -f "$smoke_compose" \
    down -v --remove-orphans >/dev/null 2>&1 || true

  exit "$exit_code"
}

trap cleanup EXIT INT TERM

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command is not available: $command_name" >&2
    exit 1
  fi
}

require_command bun
require_command curl
require_command docker
require_command lsof

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose is not available." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "The Docker daemon is not available. Start Docker Desktop, then retry." >&2
  exit 1
fi

if [[ ! -d "$repo_root/node_modules/@playwright/test" ]]; then
  echo "Playwright is not installed. Run 'bun install' first." >&2
  exit 1
fi

chromium_path="$(
  cd "$repo_root"
  bun -e 'import { chromium } from "@playwright/test"; process.stdout.write(chromium.executablePath())'
)"
if [[ ! -x "$chromium_path" ]]; then
  echo "Playwright Chromium is not installed. Run 'bunx playwright install chromium' first." >&2
  exit 1
fi

reserved_ports=(
  "$db_port"
  "$auth_port"
  "$storage_port"
  "$backend_port"
  "$frontend_port"
  "$admin_port"
)
for port in "${reserved_ports[@]}"; do
  if [[ -n "$(listener_pids "$port")" ]]; then
    echo "Port $port is already in use." >&2
    echo "Stop the process using the smoke-test port or override the corresponding SMOKE_*_PORT." >&2
    exit 1
  fi
done

if [[ -z "${JAVA_HOME:-}" ]]; then
  export JAVA_HOME="/Users/maxpersonal/.sdkman/candidates/java/current"
fi
export PATH="$JAVA_HOME/bin:$PATH"

echo "Starting disposable smoke-test infrastructure..."
if ! docker compose \
  -p "$smoke_project" \
  -f "$repo_root/compose.yaml" \
  -f "$smoke_compose" \
  up -d --build \
  postgres firebase-auth firebase-auth-seed \
  liquibase-migrate liquibase-seed localstack; then
  echo "Smoke-test infrastructure failed to start." >&2
  docker compose \
    -p "$smoke_project" \
    -f "$repo_root/compose.yaml" \
    -f "$smoke_compose" \
    logs --no-color liquibase-migrate liquibase-seed firebase-auth >&2 || true
  exit 1
fi

wait_for_url() {
  local name="$1"
  local url="$2"
  local timeout_seconds="$3"
  local deadline=$((SECONDS + timeout_seconds))

  while (( SECONDS < deadline )); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "$name is ready."
      return
    fi
    sleep 1
  done

  echo "$name did not become ready at $url within ${timeout_seconds}s." >&2
  return 1
}

wait_for_completed_service() {
  local service="$1"
  local timeout_seconds="$2"
  local deadline=$((SECONDS + timeout_seconds))

  while (( SECONDS < deadline )); do
    local container_id status exit_code
    container_id="$(
      docker compose -p "$smoke_project" -f "$repo_root/compose.yaml" \
        ps -a -q "$service" 2>/dev/null | head -n 1
    )"
    if [[ -n "$container_id" ]]; then
      status="$(docker inspect -f '{{.State.Status}}' "$container_id")"
      exit_code="$(docker inspect -f '{{.State.ExitCode}}' "$container_id")"
      if [[ "$status" == "exited" && "$exit_code" == "0" ]]; then
        echo "$service completed successfully."
        return
      fi
      if [[ "$status" == "exited" && "$exit_code" != "0" ]]; then
        echo "$service failed with exit code $exit_code." >&2
        docker logs "$container_id" >&2 || true
        return 1
      fi
    fi
    sleep 1
  done

  echo "$service did not complete within ${timeout_seconds}s." >&2
  return 1
}

wait_for_storage_object() {
  local bucket="$1"
  local key="$2"
  local timeout_seconds="$3"
  local deadline=$((SECONDS + timeout_seconds))

  while (( SECONDS < deadline )); do
    if docker compose \
      -p "$smoke_project" \
      -f "$repo_root/compose.yaml" \
      -f "$smoke_compose" \
      exec -T localstack \
      awslocal s3api head-object --bucket "$bucket" --key "$key" \
      >/dev/null 2>&1; then
      echo "LocalStack media fixture is ready."
      return
    fi
    sleep 1
  done

  echo "LocalStack did not seed s3://${bucket}/${key} within ${timeout_seconds}s." >&2
  echo "The video smoke journey requires network access for the repository's media seed script." >&2
  return 1
}

wait_for_url \
  "Authentication provider" \
  "${authentication_url}/emulator/v1/projects/demo-your-say-news/config" \
  180
wait_for_completed_service "firebase-auth-seed" 120
wait_for_completed_service "liquibase-migrate" 120
wait_for_completed_service "liquibase-seed" 120
wait_for_url "LocalStack" "http://localhost:${storage_port}/_localstack/health" 180
# Must name a key localstack/init-aws.sh actually uploads, and the one the video journey asserts
# on (see expectedFeed.video.mediaKey in smoke-tests/fixtures/test-data.ts). Keep the three in step.
wait_for_storage_object "post-videos" "posts/seed-2003-video.mp4" 180

echo "Starting post-service..."
(
  cd "$repo_root"
  exec env \
    QUARKUS_OTEL_ENABLED=false \
    QUARKUS_HTTP_PORT="$backend_port" \
    QUARKUS_HTTP_CORS_ORIGINS="$application_url,$admin_url" \
    FIREBASE_AUTH_EMULATOR_HOST="localhost:${auth_port}" \
    QUARKUS_QUINOA_DEV_SERVER_PORT="$admin_port" \
    QUARKUS_DATASOURCE_USERNAME=app_user \
    QUARKUS_DATASOURCE_PASSWORD=app_password \
    QUARKUS_DATASOURCE_JDBC_URL="jdbc:postgresql://localhost:${db_port}/app_db" \
    QUARKUS_DATASOURCE_REACTIVE_URL="postgresql://localhost:${db_port}/app_db" \
    QUARKUS_S3_ENDPOINT_OVERRIDE="http://localhost:${storage_port}" \
    VITE_API_ORIGIN="http://localhost:${backend_port}" \
    VITE_ADMIN_PORT="$admin_port" \
    VITE_FIREBASE_AUTH_EMULATOR_URL="$authentication_url" \
    ./gradlew :post-service:quarkusDev --console=plain
) >"$service_logs_dir/post-service.log" 2>&1 &
backend_pid=$!

wait_for_url "post-service" "http://localhost:${backend_port}/live" 180 || {
  tail -n 120 "$service_logs_dir/post-service.log" >&2 || true
  exit 1
}

wait_for_url "Admin web UI" "${admin_url}/admin/" 180 || {
  tail -n 120 "$service_logs_dir/post-service.log" >&2 || true
  exit 1
}

echo "Starting Expo web..."
(
  cd "$repo_root/frontend/mobile/your-say-news"
  exec env \
    APP_ENV=dev \
    CI=1 \
    EXPO_PUBLIC_AUTH_BASE_URL="$authentication_url" \
    EXPO_PUBLIC_POST_SERVICE_HOST=http://localhost \
    EXPO_PUBLIC_POST_SERVICE_PORT=":${backend_port}" \
    bunx expo start --web --clear --port "$frontend_port"
) >"$service_logs_dir/expo.log" 2>&1 &
frontend_pid=$!

wait_for_url "Expo web" "$application_url" 180 || {
  tail -n 120 "$service_logs_dir/expo.log" >&2 || true
  exit 1
}

echo "Running Playwright smoke tests..."
cd "$repo_root"
exec_status=0
SMOKE_BASE_URL="$application_url" \
SMOKE_AUTH_ORIGIN="$authentication_url" \
SMOKE_ADMIN_URL="$admin_url" \
SMOKE_API_ORIGIN="http://localhost:${backend_port}" \
bunx playwright test \
  --config smoke-tests/playwright.config.ts || exec_status=$?

if (( exec_status != 0 )); then
  echo
  echo "Smoke tests failed. Evidence: $artifacts_dir" >&2
  exit "$exec_status"
fi

echo "Smoke tests passed."
