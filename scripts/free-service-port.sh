#!/usr/bin/env bash
set -euo pipefail

if (( $# != 2 )); then
  echo "Usage: $0 <port> <service-name>" >&2
  exit 2
fi

port="$1"
service_name="$2"

if [[ ! "$port" =~ ^[0-9]+$ ]] || (( port < 1 || port > 65535 )); then
  echo "Invalid port for $service_name: $port" >&2
  exit 2
fi

if ! command -v lsof >/dev/null 2>&1; then
  echo "Cannot check port $port for $service_name because lsof is not installed." >&2
  exit 1
fi

listener_pids() {
  lsof -nP -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | sort -u
}

pids=()
while IFS= read -r pid; do
  [[ -n "$pid" ]] && pids+=("$pid")
done < <(listener_pids)

if (( ${#pids[@]} == 0 )); then
  exit 0
fi

printf '%s port %s is already in use; stopping listener PID(s):' "$service_name" "$port"
printf ' %s' "${pids[@]}"
echo

for pid in "${pids[@]}"; do
  kill -TERM "$pid" 2>/dev/null || true
done

deadline=$((SECONDS + 5))
while (( SECONDS < deadline )); do
  if [[ -z "$(listener_pids)" ]]; then
    echo "$service_name port $port is free."
    exit 0
  fi
  sleep 0.2
done

pids=()
while IFS= read -r pid; do
  [[ -n "$pid" ]] && pids+=("$pid")
done < <(listener_pids)

if (( ${#pids[@]} > 0 )); then
  printf '%s port %s did not stop cleanly; force-killing PID(s):' "$service_name" "$port"
  printf ' %s' "${pids[@]}"
  echo
  for pid in "${pids[@]}"; do
    kill -KILL "$pid" 2>/dev/null || true
  done
fi

sleep 0.2
if [[ -n "$(listener_pids)" ]]; then
  echo "Could not free $service_name port $port." >&2
  exit 1
fi

echo "$service_name port $port is free."
