#!/usr/bin/env sh
set -eu

release_directory=${1:?Usage: release.sh RELEASE_DIRECTORY}
deploy_root=${DEPLOY_ROOT:-/opt/your-say-news}
current_link="$deploy_root/current"
releases_root=$(readlink -f "$deploy_root/releases") || {
  echo "Release root does not exist: $deploy_root/releases" >&2
  exit 1
}

validate_release_files() {
  validated_release=$1
  for required_file in compose.yaml runtime.env scripts/deploy.sh scripts/health-check.sh; do
    required_path="$validated_release/$required_file"
    if [ ! -f "$required_path" ]; then
      echo "Release file is missing: $required_path" >&2
      return 1
    fi
    canonical_required_path=$(readlink -f "$required_path") || {
      echo "Release file cannot be resolved: $required_path" >&2
      return 1
    }
    if [ "$canonical_required_path" != "$required_path" ]; then
      echo "Release file must not be a symlink or escape its release: $required_path" >&2
      return 1
    fi
  done
}

release_directory=$(readlink -f "$release_directory") || {
  echo 'Release directory does not exist.' >&2
  exit 1
}

case "$release_directory" in
  "$releases_root"/*) ;;
  *)
    echo "Release directory must be inside $deploy_root/releases." >&2
    exit 1
    ;;
esac

validate_release_files "$release_directory"

previous_release=''
if [ -L "$current_link" ]; then
  previous_release=$(readlink -f "$current_link")
  case "$previous_release" in
    "$releases_root"/*) ;;
    *) previous_release='' ;;
  esac
  if [ -n "$previous_release" ] && ! validate_release_files "$previous_release"; then
    previous_release=''
  fi
fi

set +e
DEPLOY_ENV_FILE="$release_directory/runtime.env" \
  "$release_directory/scripts/deploy.sh"
deployment_status=$?
set -e

if [ "$deployment_status" -eq 0 ]; then
  ln -sfn "$release_directory" "$current_link"
  echo "Activated application release: $release_directory"
  exit 0
fi

echo "Application release failed with status $deployment_status." >&2

if [ -z "$previous_release" ] || [ ! -f "$previous_release/runtime.env" ]; then
  echo 'No previous application release is available for rollback.' >&2
  exit "$deployment_status"
fi

echo "Restoring the previous application containers from $previous_release." >&2
docker compose \
  --env-file "$previous_release/runtime.env" \
  --file "$previous_release/compose.yaml" \
  up --detach --remove-orphans post-service alloy

PRIVATE_HEALTH_URL="${PRIVATE_HEALTH_URL:-http://127.0.0.1:8082/api/live}" \
  PUBLIC_HEALTH_URL="${PUBLIC_HEALTH_URL:-}" \
  "$previous_release/scripts/health-check.sh"

echo 'Previous application containers were restored; database migrations were not reversed.' >&2
exit "$deployment_status"
