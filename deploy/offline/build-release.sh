#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)
# shellcheck source=_common.sh
source "$SCRIPT_DIR/_common.sh"

usage() {
  cat <<'EOF'
Usage: build-release.sh --version VERSION [options]

Options:
  --env-file FILE     Production env file (default: REPO_ROOT/.env)
  --output-dir DIR    Release output directory (default: REPO_ROOT/dist)
  --dry-run           Validate inputs and print the intended release path
  --skip-prune        Keep dangling images after a successful release
  -h, --help          Show this help
EOF
}

version=
env_file="$REPO_ROOT/.env"
output_dir="$REPO_ROOT/dist"
dry_run=0
prune_images=1

while (($#)); do
  case "$1" in
    --version) version=${2:-}; shift 2 ;;
    --env-file) env_file=${2:-}; shift 2 ;;
    --output-dir) output_dir=${2:-}; shift 2 ;;
    --dry-run) dry_run=1; shift ;;
    --skip-prune) prune_images=0; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done

[[ "$version" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || die "Invalid or missing release version"
[[ -f "$env_file" ]] || die "Production env file not found: $env_file"
[[ -f "$REPO_ROOT/compose.prod.yml" ]] || die "compose.prod.yml not found"
[[ -d "$REPO_ROOT/fastdfs/tracker_data" ]] || die "FastDFS tracker data directory not found"
[[ -d "$REPO_ROOT/fastdfs/storage_data" ]] || die "FastDFS storage data directory not found"
[[ -d "$REPO_ROOT/download/dataset" ]] || die "Dataset directory not found"
[[ -d "$REPO_ROOT/python" ]] || die "Scoring directory not found"

env_value() {
  local key=$1
  local fallback=$2
  local value
  if [[ -n "${!key:-}" ]]; then
    printf '%s\n' "${!key}"
    return
  fi
  value=$(awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$env_file" 2>/dev/null || true)
  value=${value#\"}; value=${value%\"}; value=${value#\'}; value=${value%\'}
  printf '%s\n' "${value:-$fallback}"
}

registry_image=$(env_value XKP_REGISTRY_IMAGE registry:2)
registry_importer_image=$(env_value XKP_REGISTRY_IMPORTER_IMAGE xkp5/registry-importer:latest)

release_name="match-v2-$version"
stage_dir="$output_dir/$release_name"
archive_file="$output_dir/$release_name.tar.gz"

if (( dry_run )); then
  log "Dry run successful"
  log "Release directory: $stage_dir"
  log "Release archive: $archive_file"
  exit 0
fi

require_amd64
for command_name in docker git gzip sha256sum tar curl; do
  require_command "$command_name"
done
docker compose version >/dev/null
docker info >/dev/null

[[ ! -e "$stage_dir" ]] || die "Release directory already exists: $stage_dir"
[[ ! -e "$archive_file" ]] || die "Release archive already exists: $archive_file"
mkdir -p "$stage_dir/images" "$stage_dir/data"

source_stopped=0
compose_source() {
  docker compose --project-name "$PROJECT_NAME" --env-file "$env_file" -f "$REPO_ROOT/compose.prod.yml" "$@"
}

restore_source() {
  local exit_code=$?
  if (( source_stopped )); then
    log "Restoring source services"
    compose_source up -d || true
  fi
  return "$exit_code"
}
trap restore_source EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

log "Building application images"
compose_source build java vue
compose_source pull registry registry-importer
java_image_id=$(compose_source images -q java | head -n 1)
vue_image_id=$(compose_source images -q vue | head -n 1)
[[ -n "$java_image_id" ]] || die "Unable to resolve the Java image"
[[ -n "$vue_image_id" ]] || die "Unable to resolve the Vue image"
docker tag "$java_image_id" "match-v2_java:$version"
docker tag "$vue_image_id" "match-v2_vue:$version"

check_loaded_images "$version" "$registry_image" "$registry_importer_image"

log "Stopping application writes"
source_stopped=1
compose_source stop vue java

log "Exporting MySQL"
compose_source exec -T mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --events "$MYSQL_DATABASE"' \
  | gzip -c > "$stage_dir/data/mysql.sql.gz"

log "Stopping stateful services for the file snapshot"
compose_source stop fastdfs-storage fastdfs-tracker mysql

tar --numeric-owner -czf "$stage_dir/data/fastdfs-tracker.tar.gz" -C "$REPO_ROOT/fastdfs/tracker_data" .
tar --numeric-owner -czf "$stage_dir/data/fastdfs-storage.tar.gz" -C "$REPO_ROOT/fastdfs/storage_data" .
tar --numeric-owner -czf "$stage_dir/data/dataset.tar.gz" -C "$REPO_ROOT/download/dataset" .
tar --numeric-owner -czf "$stage_dir/data/scoring.tar.gz" -C "$REPO_ROOT/python" .

log "Exporting runtime images"
docker save \
  "match-v2_java:$version" \
  "match-v2_vue:$version" \
  mysql:8.0 \
  delron/fastdfs:latest \
  "$registry_image" \
  "$registry_importer_image" \
  | gzip -c > "$stage_dir/images/match-v2-images.tar.gz"

dataset_file_count=$(find "$REPO_ROOT/download/dataset" -type f | wc -l | tr -d ' ')
scoring_file_count=$(find "$REPO_ROOT/python" -type f | wc -l | tr -d ' ')
fastdfs_file_count=$(find "$REPO_ROOT/fastdfs/storage_data/data" -type f 2>/dev/null | wc -l | tr -d ' ')
git_commit=$(git -C "$REPO_ROOT" rev-parse HEAD)
created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)

fastdfs_sample=
sample_file=$(find "$REPO_ROOT/fastdfs/storage_data/data" -type f -path '*/00/00/*' -print -quit 2>/dev/null || true)
if [[ -n "$sample_file" ]]; then
  relative_sample=${sample_file#"$REPO_ROOT/fastdfs/storage_data/data/"}
  fastdfs_sample="/files/group1/M00/$relative_sample"
fi

{
  printf 'MATCH_RELEASE_VERSION=%q\n' "$version"
  printf 'MATCH_RELEASE_GIT_COMMIT=%q\n' "$git_commit"
  printf 'MATCH_RELEASE_CREATED_AT=%q\n' "$created_at"
  printf 'MATCH_DATASET_FILE_COUNT=%q\n' "$dataset_file_count"
  printf 'MATCH_SCORING_FILE_COUNT=%q\n' "$scoring_file_count"
  printf 'MATCH_FASTDFS_FILE_COUNT=%q\n' "$fastdfs_file_count"
  printf 'MATCH_FASTDFS_SAMPLE_PATH=%q\n' "$fastdfs_sample"
  printf 'MATCH_REGISTRY_IMAGE=%q\n' "$registry_image"
  printf 'MATCH_REGISTRY_IMPORTER_IMAGE=%q\n' "$registry_importer_image"
} > "$stage_dir/release.env"

{
  printf 'Match V2 offline release\n'
  printf 'Version: %s\n' "$version"
  printf 'Created UTC: %s\n' "$created_at"
  printf 'Git commit: %s\n' "$git_commit"
  printf 'Architecture: linux/amd64\n'
  printf 'Java image: %s\n' "$(docker image inspect --format '{{.Id}}' "match-v2_java:$version")"
  printf 'Vue image: %s\n' "$(docker image inspect --format '{{.Id}}' "match-v2_vue:$version")"
  printf 'MySQL image: %s\n' "$(docker image inspect --format '{{.Id}}' mysql:8.0)"
  printf 'FastDFS image: %s\n' "$(docker image inspect --format '{{.Id}}' delron/fastdfs:latest)"
  printf 'Registry image (%s): %s\n' "$registry_image" "$(docker image inspect --format '{{.Id}}' "$registry_image")"
  printf 'Registry importer image (%s): %s\n' "$registry_importer_image" "$(docker image inspect --format '{{.Id}}' "$registry_importer_image")"
  printf 'Dataset files: %s\n' "$dataset_file_count"
  printf 'Scoring files: %s\n' "$scoring_file_count"
  printf 'FastDFS storage files: %s\n' "$fastdfs_file_count"
} > "$stage_dir/MANIFEST.txt"

install -m 0644 "$REPO_ROOT/compose.offline.yml" "$stage_dir/compose.offline.yml"
install -m 0644 "$SCRIPT_DIR/.env.example" "$stage_dir/.env.example"
install -m 0644 "$SCRIPT_DIR/README.md" "$stage_dir/README.md"
install -m 0755 "$REPO_ROOT/deploy/host-identity.sh" "$stage_dir/host-identity.sh"
install -m 0755 "$REPO_ROOT/deploy/agent-ca.sh" "$stage_dir/agent-ca.sh"
for script_name in _common.sh install.sh upgrade.sh reset-from-snapshot.sh verify.sh uninstall.sh; do
  install -m 0755 "$SCRIPT_DIR/$script_name" "$stage_dir/$script_name"
done

(cd "$stage_dir" && find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS)
verify_package "$stage_dir"

log "Restarting source services"
compose_source up -d
source_stopped=0

backend_binding=$(compose_source port java 19141 | tail -n 1)
backend_port=${backend_binding##*:}
backend_port=${backend_port:-19141}
frontend_binding=$(compose_source port vue 19090 | tail -n 1)
frontend_port=${frontend_binding##*:}
frontend_port=${frontend_port:-19140}
for _ in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:$backend_port/health" >/dev/null 2>&1 \
    && curl -fsS "http://127.0.0.1:$frontend_port/" >/dev/null 2>&1; then
    source_healthy=1
    break
  fi
  sleep 2
done
[[ ${source_healthy:-0} == 1 ]] || die "Source services restarted but the backend health check failed"
source_running_count=$(compose_source ps --status running -q | wc -l | tr -d ' ')
[[ "$source_running_count" == 5 ]] || die "Source restart expected 5 running containers; found $source_running_count"

log "Creating release archive"
tar -czf "$archive_file" -C "$output_dir" "$release_name"
tar -tzf "$archive_file" >/dev/null

if (( prune_images )); then
  log "Removing dangling images"
  docker image prune -f
fi

log "Release created: $archive_file"
