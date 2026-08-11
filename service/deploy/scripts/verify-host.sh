#!/usr/bin/env sh
set -eu

readonly status_directory="${BOOTSTRAP_STATUS_DIRECTORY:-/var/lib/your-say-news}"
readonly failure_marker="$status_directory/bootstrap-failed"
readonly completion_marker="$status_directory/bootstrap-complete"

if [ -f "$failure_marker" ]; then
  echo 'The VM bootstrap reported a failure:' >&2
  sed -n '1,20p' "$failure_marker" >&2
  exit 1
fi

if [ ! -f "$completion_marker" ]; then
  echo "VM bootstrap is not complete: $completion_marker is missing." >&2
  exit 1
fi

for command_name in docker cloudflared; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required host command is unavailable: $command_name" >&2
    exit 1
  fi
done

docker info >/dev/null
docker compose version >/dev/null
systemctl is-active --quiet cloudflared-ysn.service

echo 'VM bootstrap, Docker and the Cloudflare Tunnel connector are ready.'
