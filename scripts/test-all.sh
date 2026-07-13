#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
  cat >&2 <<'EOF'
The Docker daemon is not available, and the backend tests require Testcontainers.

Start Docker Desktop and wait for it to finish starting, then run:
  bun run test
EOF
  exit 1
fi

cd "$repo_root"
exec mprocs --config mprocs.test.yaml "$@"
