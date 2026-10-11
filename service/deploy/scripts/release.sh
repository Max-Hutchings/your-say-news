#!/usr/bin/env sh
set -eu

release_directory=${1:?Usage: release.sh RELEASE_DIRECTORY}
deploy_root=${DEPLOY_ROOT:-/opt/your-say-news}
current_link="$deploy_root/current"
# Release directories kept after a successful deploy, newest first. The release it replaced is always
# kept as well, so a later failed deploy can still roll back to it.
releases_to_keep=${RELEASES_TO_KEEP:-3}
case "$releases_to_keep" in
  '' | *[!0-9]*)
    echo "RELEASES_TO_KEEP must be a whole number, not: $releases_to_keep" >&2
    exit 1
    ;;
esac
releases_root=$(readlink -f "$deploy_root/releases") || {
  echo "Release root does not exist: $deploy_root/releases" >&2
  exit 1
}

# Every deploy pulls a full image. Images used by any container (the running release) are kept, so
# this only frees space; a failed prune must never fail the deploy.
prune_unused_images() {
  docker image prune --all --force >/dev/null || echo 'Warning: unused Docker images were not pruned.' >&2
}

# Deletes real release directories beyond the newest $releases_to_keep, sparing the active and
# replaced releases. Symlinks are skipped so deletion never follows a link out of the releases root.
prune_old_releases() {
  active_release=$1
  replaced_release=$2
  kept=0
  # POSIX sh globs cannot sort by age, so ls -t supplies newest-first names one per line.
  # shellcheck disable=SC2012
  ls -1t "$releases_root" | while IFS= read -r release_name; do
    release_path="$releases_root/$release_name"
    if [ -L "$release_path" ] || [ ! -d "$release_path" ]; then
      continue
    fi
    if [ "$release_path" = "$active_release" ] || [ "$release_path" = "$replaced_release" ]; then
      kept=$((kept + 1))
      continue
    fi
    if [ "$kept" -lt "$releases_to_keep" ]; then
      kept=$((kept + 1))
      continue
    fi
    rm -rf -- "$release_path"
    echo "Removed old application release: $release_path"
  done
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

prune_unused_images

set +e
DEPLOY_ENV_FILE="$release_directory/runtime.env" \
  "$release_directory/scripts/deploy.sh"
deployment_status=$?
set -e

if [ "$deployment_status" -eq 0 ]; then
  ln -sfn "$release_directory" "$current_link"
  echo "Activated application release: $release_directory"
  # The release is already live, so a cleanup failure is reported but never fails the deploy.
  prune_old_releases "$release_directory" "$previous_release" \
    || echo 'Warning: old application releases were not all removed.' >&2
  prune_unused_images
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
