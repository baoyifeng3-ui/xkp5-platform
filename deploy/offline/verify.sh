#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=_common.sh
source "$SCRIPT_DIR/_common.sh"

usage() {
  cat <<'EOF'
Usage: verify.sh [--install-dir ABSOLUTE_PATH] [--snapshot]

Checks containers, image versions, database, frontend and backend. --snapshot
also compares restored file counts and a FastDFS sample with the release snapshot.
EOF
}

install_dir=$SCRIPT_DIR
snapshot_check=0
while (($#)); do
  case "$1" in
    --install-dir) install_dir=${2:-}; shift 2 ;;
    --snapshot) snapshot_check=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done

assert_safe_install_dir "$install_dir"
[[ -f "$install_dir/.env" ]] || die "Environment file not found: $install_dir/.env"
load_release_env "$install_dir/release.env"
require_command docker
require_command curl

running_count=$(compose_at "$install_dir" ps --status running -q | wc -l | tr -d ' ')
[[ "$running_count" == 7 ]] || die "Expected 7 running containers; found $running_count"

mysql_id=$(compose_at "$install_dir" ps -q mysql)
mysql_health=$(docker inspect --format '{{.State.Health.Status}}' "$mysql_id")
[[ "$mysql_health" == healthy ]] || die "MySQL is not healthy: $mysql_health"

for service in java vue; do
  container_id=$(compose_at "$install_dir" ps -q "$service")
  running_image=$(docker inspect --format '{{.Image}}' "$container_id")
  expected_image=$(docker image inspect --format '{{.Id}}' "match-v2_${service}:$MATCH_RELEASE_VERSION")
  [[ "$running_image" == "$expected_image" ]] || die "$service is not running release $MATCH_RELEASE_VERSION"
done

backend_binding=$(compose_at "$install_dir" port java 19141 | tail -n 1)
frontend_binding=$(compose_at "$install_dir" port vue 19090 | tail -n 1)
backend_port=${backend_binding##*:}
frontend_port=${frontend_binding##*:}
backend_port=${backend_port:-19141}
frontend_port=${frontend_port:-19140}

for _ in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:$backend_port/health" >/dev/null 2>&1; then
    backend_ready=1
    break
  fi
  sleep 2
done
[[ ${backend_ready:-0} == 1 ]] || die "Backend health check failed on port $backend_port"
curl -fsS "http://127.0.0.1:$frontend_port/" >/dev/null || die "Frontend check failed on port $frontend_port"

table_count=$(compose_at "$install_dir" exec -T mysql sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '\''$MYSQL_DATABASE'\''"')
(( table_count > 0 )) || die "Business database contains no tables"

if (( snapshot_check )); then
  dataset_count=$(find "$install_dir/runtime/dataset" -type f | wc -l | tr -d ' ')
  scoring_count=$(find "$install_dir/runtime/scoring" -type f | wc -l | tr -d ' ')
  fastdfs_count=$(find "$install_dir/runtime/fastdfs/storage_data" -type f 2>/dev/null | wc -l | tr -d ' ')
  registry_count=$(docker run --rm -v "${XKP_REGISTRY_VOLUME_NAME:-match-v2_registry_data}:/source:ro" \
    "${MATCH_REGISTRY_IMAGE:-registry:2}" sh -c 'find /source -type f | wc -l' | tr -d ' ')
  [[ "$dataset_count" == "$MATCH_DATASET_FILE_COUNT" ]] || die "Dataset file count mismatch"
  [[ "$scoring_count" == "$MATCH_SCORING_FILE_COUNT" ]] || die "Scoring file count mismatch"
  [[ "$fastdfs_count" == "$MATCH_FASTDFS_FILE_COUNT" ]] || die "FastDFS file count mismatch"
  [[ "$registry_count" == "$MATCH_REGISTRY_FILE_COUNT" ]] || die "Registry file count mismatch"
  if [[ -n "${MATCH_FASTDFS_SAMPLE_PATH:-}" ]]; then
    curl -fsS "http://127.0.0.1:$frontend_port$MATCH_FASTDFS_SAMPLE_PATH" >/dev/null \
      || die "FastDFS sample is unavailable: $MATCH_FASTDFS_SAMPLE_PATH"
  fi
fi

java_id=$(compose_at "$install_dir" ps -q java)
allowed_origin=$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$java_id" \
  | sed -n 's/^MATCH_ALLOWED_ORIGINS=//p' | cut -d, -f1)
if [[ -n "$allowed_origin" ]]; then
  curl -fsS -o /dev/null -D - -X OPTIONS \
    -H "Origin: $allowed_origin" \
    -H 'Access-Control-Request-Method: GET' \
    "http://127.0.0.1:$backend_port/health" \
    | grep -qi "^Access-Control-Allow-Origin: $allowed_origin" \
    || die "CORS check failed for $allowed_origin"
fi

log "Verification passed for release $MATCH_RELEASE_VERSION"
