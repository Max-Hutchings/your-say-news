#!/usr/bin/env bash
set -u

if [[ "${YSN_SKIP_COMPOSE_CHECK:-}" == "1" ]]; then
  echo "Skipping Docker Compose preflight because YSN_SKIP_COMPOSE_CHECK=1."
  exit 0
fi

if ! command -v docker >/dev/null 2>&1; then
  cat >&2 <<'EOF'
Warning: Docker was not found, so the app services were not started.

Install or start Docker, then run:
  bun run dev
EOF
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  cat >&2 <<'EOF'
Warning: Docker Compose is not available, so the app services were not started.

Install Docker Compose, then run:
  bun run dev
EOF
  exit 1
fi

missing=()
wait_timeout_seconds="${YSN_COMPOSE_CHECK_TIMEOUT_SECONDS:-120}"
wait_interval_seconds=1

check_running_service() {
  local service="$1"
  local container_id status health

  container_id="$(docker compose ps -a -q "$service" 2>/dev/null | head -n 1)"
  if [[ -z "$container_id" ]]; then
    missing+=("$service: not created")
    return
  fi

  status="$(docker inspect -f '{{.State.Status}}' "$container_id" 2>/dev/null || true)"
  health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id" 2>/dev/null || true)"

  if [[ "$status" != "running" ]]; then
    missing+=("$service: $status")
    return
  fi

  if [[ "$health" != "none" && "$health" != "healthy" ]]; then
    missing+=("$service: running but $health")
  fi
}

check_keycloak_service() {
  local service="keycloak"
  local container_id status

  container_id="$(docker compose ps -a -q "$service" 2>/dev/null | head -n 1)"
  if [[ -z "$container_id" ]]; then
    missing+=("$service: not created")
    return
  fi

  status="$(docker inspect -f '{{.State.Status}}' "$container_id" 2>/dev/null || true)"
  if [[ "$status" != "running" ]]; then
    missing+=("$service: $status")
    return
  fi

  if ! curl -fsS "http://localhost:8080/realms/your-say-news/.well-known/openid-configuration" >/dev/null 2>&1; then
    missing+=("$service: realm discovery endpoint is not reachable on localhost:8080")
  fi
}

check_completed_service() {
  local service="$1"
  local container_id status exit_code

  container_id="$(docker compose ps -a -q "$service" 2>/dev/null | head -n 1)"
  if [[ -z "$container_id" ]]; then
    missing+=("$service: not created")
    return
  fi

  status="$(docker inspect -f '{{.State.Status}}' "$container_id" 2>/dev/null || true)"
  exit_code="$(docker inspect -f '{{.State.ExitCode}}' "$container_id" 2>/dev/null || true)"

  if [[ "$status" != "exited" || "$exit_code" != "0" ]]; then
    missing+=("$service: $status, exit code $exit_code")
  fi
}

check_compose_services() {
  missing=()

  check_running_service "postgres"
  check_running_service "keycloak-db"
  check_keycloak_service
  check_completed_service "keycloak-seed-users"
  check_running_service "localstack"
  check_running_service "otel-lgtm"
  check_completed_service "liquibase-migrate"
  check_completed_service "liquibase-seed"
}

deadline=$((SECONDS + wait_timeout_seconds))
printed_wait_message=0
last_pending=""

while true; do
  check_compose_services

  if (( ${#missing[@]} == 0 )); then
    if (( printed_wait_message == 1 )); then
      echo
    fi
    echo "Docker Compose infrastructure is ready."
    exit 0
  fi

  if (( SECONDS >= deadline )); then
    break
  fi

  if (( printed_wait_message == 0 )); then
    echo "Waiting for Docker Compose infrastructure to become ready..."
    printed_wait_message=1
  fi

  # Reprint the pending list only when it changes, so a slow Liquibase job or an
  # unfinished realm import is visible rather than looking like a silent hang.
  pending="$(printf '%s; ' "${missing[@]}")"
  if [[ "$pending" != "$last_pending" ]]; then
    printf '  still waiting on: %s\n' "${pending%; }"
    last_pending="$pending"
  fi

  sleep "$wait_interval_seconds"
done

if (( ${#missing[@]} > 0 )); then
  {
    echo "Warning: Docker Compose infrastructure is not ready, so this application process was not started."
    echo
    echo "Expected the long-running Compose services to be running/healthy and startup jobs to have completed."
    echo "Current issues:"
    for issue in "${missing[@]}"; do
      echo "  - $issue"
    done
    echo
    echo "Restart the managed Compose stack with 'r' in mprocs, or quit and run:"
    echo "  bun run dev"
    echo
    echo "To bypass this preflight intentionally:"
    echo "  YSN_SKIP_COMPOSE_CHECK=1 bun run dev"
    echo
    echo "To wait longer before warning:"
    echo "  YSN_COMPOSE_CHECK_TIMEOUT_SECONDS=180 bun run dev"
  } >&2
  exit 1
fi
