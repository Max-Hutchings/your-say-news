#!/usr/bin/env sh
set -eu

private_url="${PRIVATE_HEALTH_URL:-http://127.0.0.1:8082/api/live}"
public_url="${PUBLIC_HEALTH_URL:-}"
attempts="${HEALTH_CHECK_ATTEMPTS:-12}"
delay_seconds="${HEALTH_CHECK_DELAY_SECONDS:-5}"

check_url() {
  url="$1"
  label="$2"
  attempt=1

  while [ "$attempt" -le "$attempts" ]; do
    if curl --fail --silent --show-error --max-time 10 "$url" >/dev/null; then
      echo "$label health check passed: $url"
      return 0
    fi

    if [ "$attempt" -lt "$attempts" ]; then
      sleep "$delay_seconds"
    fi
    attempt=$((attempt + 1))
  done

  echo "$label health check failed after $attempts attempts: $url" >&2
  return 1
}

check_url "$private_url" "Private"

if [ -n "$public_url" ]; then
  check_url "$public_url" "Public"
else
  echo "PUBLIC_HEALTH_URL is unset; public tunnel health check skipped."
fi
