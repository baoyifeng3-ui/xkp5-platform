#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

log() { printf '[xkp5-online-update] %s\n' "$*"; }
die() { printf '[xkp5-online-update] ERROR: %s\n' "$*" >&2; exit 1; }
usage() {
  cat <<'EOF'
Usage: update-xkp5-online.sh [options]
  --repo URL             Default https://github.com/baoyifeng3-ui/xkp5-platform.git
  --ref BRANCH_OR_TAG    Default main
  --install-dir DIR      Default /opt/xkp5-platform
  --health-timeout SEC   Default 180
  --dry-run
EOF
}

repo=https://github.com/baoyifeng3-ui/xkp5-platform.git
ref=main
install_dir=/opt/xkp5-platform
health_timeout=180
dry_run=0
while (($#)); do
  case "$1" in
    --repo) repo=${2:-}; shift 2 ;;
    --ref) ref=${2:-}; shift 2 ;;
    --install-dir) install_dir=${2:-}; shift 2 ;;
    --health-timeout) health_timeout=${2:-}; shift 2 ;;
    --dry-run) dry_run=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done

if ((dry_run)); then
  cat <<EOF
[xkp5-online-update] Dry run only.
[xkp5-online-update] 1. Clone $repo at $ref.
[xkp5-online-update] 2. Build Java, Vue, and Registry Importer images.
[xkp5-online-update] 3. Back up MySQL under $install_dir/backups/.
[xkp5-online-update] 4. Switch versioned application images; preserve MySQL, FastDFS, and volumes.
[xkp5-online-update] 5. Health-check Java and Vue; rollback application metadata on failure.
EOF
  exit 0
fi

[[ $(id -u) -eq 0 ]] || die 'Run as root'
[[ -n $repo && -n $ref ]] || die 'Repository and ref are required'
[[ $install_dir == /* && $install_dir != / && $install_dir != /opt && $install_dir != /root ]] || die 'Unsafe install directory'
[[ $health_timeout =~ ^[1-9][0-9]*$ ]] || die 'Health timeout must be a positive integer'
for command_name in git docker curl gzip sha256sum; do command -v "$command_name" >/dev/null || die "Required command not found: $command_name"; done
docker compose version >/dev/null 2>&1 || die 'Docker Compose v2 is required'
docker info >/dev/null 2>&1 || die 'Docker is not running'
[[ -f $install_dir/.env && -f $install_dir/release.env && -f $install_dir/compose.offline.yml ]] || die "Existing installation not found: $install_dir"

compose() {
  (
    set -a
    # shellcheck disable=SC1090
    source "$install_dir/release.env"
    set +a
    docker compose --project-name match-v2 --env-file "$install_dir/.env" -f "$install_dir/compose.offline.yml" "$@"
  )
}

published_port() {
  local service=$1 container_port=$2 binding port
  binding=$(compose port "$service" "$container_port" | tail -n 1)
  port=${binding##*:}
  [[ $port =~ ^[0-9]+$ ]] || die "Unable to resolve $service port"
  printf '%s\n' "$port"
}

wait_url() {
  local label=$1 url=$2 elapsed=0
  while ((elapsed < health_timeout)); do
    curl -fsS "$url" >/dev/null 2>&1 && { log "$label is healthy"; return 0; }
    sleep 2
    elapsed=$((elapsed + 2))
  done
  return 1
}

set_env_value() {
  local file=$1 key=$2 value=$3
  if grep -q "^${key}=" "$file"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$file"
  else
    printf '%s=%s\n' "$key" "$value" >> "$file"
  fi
}

checkout_dir=$(mktemp -d /tmp/xkp5-online-update.XXXXXX)
temporary_container=
rollback_needed=0
backup_dir=
cleanup() {
  local status=$?
  set +e
  [[ -n $temporary_container ]] && docker rm -f "$temporary_container" >/dev/null 2>&1
  if ((status != 0 && rollback_needed)); then
    log "Update failed; restoring application metadata from $backup_dir"
    cp "$backup_dir/.env" "$install_dir/.env"
    cp "$backup_dir/release.env" "$install_dir/release.env"
    cp "$backup_dir/compose.offline.yml" "$install_dir/compose.offline.yml"
    compose up -d --no-deps --force-recreate registry-importer java vue
  fi
  rm -rf "$checkout_dir"
  exit "$status"
}
trap cleanup EXIT

log "Cloning $repo at $ref"
git clone --branch "$ref" --depth 1 "$repo" "$checkout_dir/source"
commit=$(git -C "$checkout_dir/source" rev-parse HEAD)
new_version=online-${commit:0:12}
old_version=$(sed -n 's/^MATCH_RELEASE_VERSION=//p' "$install_dir/release.env")
[[ $new_version != "$old_version" ]] || die "Version $new_version is already installed"

source_compose=(docker compose --project-name xkp5-online-build --env-file "$install_dir/.env" -f "$checkout_dir/source/compose.prod.yml")
log 'Building Java and Vue images'
"${source_compose[@]}" build java vue
java_image=$("${source_compose[@]}" images -q java | head -n 1)
vue_image=$("${source_compose[@]}" images -q vue | head -n 1)
[[ -n $java_image && -n $vue_image ]] || die 'Unable to resolve built application images'
docker tag "$java_image" "match-v2_java:$new_version"
docker tag "$vue_image" "match-v2_vue:$new_version"

log 'Building Registry Importer image'
mkdir -p "$checkout_dir/source/java/match-mgr/target"
temporary_container=$(docker create "$java_image")
docker cp "$temporary_container:/app/app.jar" "$checkout_dir/source/java/match-mgr/target/app.jar"
docker rm "$temporary_container" >/dev/null
temporary_container=
docker build -f "$checkout_dir/source/deploy/registry/import-worker.Dockerfile" \
  -t "xkp5/registry-importer:$new_version" "$checkout_dir/source"

timestamp=$(date +%Y%m%d-%H%M%S)
backup_dir="$install_dir/backups/online-update-$timestamp"
mkdir -p "$backup_dir"
log "Backing up MySQL to $backup_dir/mysql.sql.gz"
compose exec -T mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --events "$MYSQL_DATABASE"' \
  | gzip -c > "$backup_dir/mysql.sql.gz"
[[ -s $backup_dir/mysql.sql.gz ]] || die 'Database backup is empty'
gzip -t "$backup_dir/mysql.sql.gz" || die 'Database backup is invalid'
cp "$install_dir/.env" "$backup_dir/.env"
cp "$install_dir/release.env" "$backup_dir/release.env"
cp "$install_dir/compose.offline.yml" "$backup_dir/compose.offline.yml"

install -m 0644 "$checkout_dir/source/compose.offline.yml" "$install_dir/compose.offline.yml"
mkdir -p "$install_dir/xkp-agent"
cp -a "$checkout_dir/source/xkp-agent/." "$install_dir/xkp-agent/"
find "$install_dir/xkp-agent" -type f -name '*.sh' -exec sed -i 's/\r$//' {} +
set_env_value "$install_dir/release.env" MATCH_RELEASE_VERSION "$new_version"
set_env_value "$install_dir/release.env" MATCH_RELEASE_GIT_COMMIT "$commit"
set_env_value "$install_dir/release.env" MATCH_RELEASE_CREATED_AT "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
set_env_value "$install_dir/release.env" MATCH_REGISTRY_IMPORTER_IMAGE "xkp5/registry-importer:$new_version"
set_env_value "$install_dir/.env" MATCH_RELEASE_VERSION "$new_version"
set_env_value "$install_dir/.env" MATCH_REGISTRY_IMPORTER_IMAGE "xkp5/registry-importer:$new_version"
compose config --quiet

rollback_needed=1
log "Switching application release $old_version -> $new_version"
compose up -d --no-deps --force-recreate registry-importer java vue
backend_port=$(published_port java 19141)
frontend_port=$(published_port vue 19090)
wait_url Java "http://127.0.0.1:$backend_port/health" || die 'Java health check failed'
wait_url Vue "http://127.0.0.1:$frontend_port/" || die 'Vue health check failed'
printf '%s\n' "$new_version" > "$install_dir/.installed-version"
rollback_needed=0
compose ps
log "Update completed: $old_version -> $new_version"
log "Database backup: $backup_dir/mysql.sql.gz"
