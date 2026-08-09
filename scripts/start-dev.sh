#!/usr/bin/env bash
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
  cat >&2 <<'EOF'
Docker is not installed or is not available on PATH.

Install and start Docker Desktop, then run:
  bun run dev
EOF
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  cat >&2 <<'EOF'
Docker Compose is not available.

Install or update Docker Desktop, then run:
  bun run dev
EOF
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  cat >&2 <<'EOF'
The Docker daemon is not available.

Start Docker Desktop and wait for it to finish starting, then run:
  bun run dev
EOF
  exit 1
fi

if [[ -z "${YOUR_SAY_NEWS_GROK_API_KEY:-}" ]]; then
  cat >&2 <<'EOF'
The Grok API key is not configured.

Export it before starting the development stack, then run:
  export YOUR_SAY_NEWS_GROK_API_KEY="your-api-key"
  bun run dev
EOF
  exit 1
fi

exec mprocs "$@"
