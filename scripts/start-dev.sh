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

agent_provider=${AGENT_PROVIDER:-openai}
case "$agent_provider" in
  openai)
    agent_api_key=${YOUR_SAY_NEWS_OPENAI_API_KEY:-}
    agent_api_key_name=YOUR_SAY_NEWS_OPENAI_API_KEY
    ;;
  grok)
    agent_api_key=${YOUR_SAY_NEWS_GROK_API_KEY:-${XAI_API_KEY:-}}
    agent_api_key_name=YOUR_SAY_NEWS_GROK_API_KEY
    ;;
  *)
    echo 'AGENT_PROVIDER must be either openai or grok.' >&2
    exit 1
    ;;
esac

if [[ -z "$agent_api_key" ]]; then
  cat >&2 <<EOF
The $agent_provider API key is not configured.

Export it before starting the development stack, then run:
  export $agent_api_key_name="your-api-key"
  bun run dev
EOF
  exit 1
fi

exec mprocs "$@"
