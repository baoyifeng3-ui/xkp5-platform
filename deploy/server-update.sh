#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)

log() {
  printf '[server-update] %s\n' "$*"
}

die() {
  printf '[server-update] ERROR: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: ./deploy/server-update.sh [options]

Options:
  --backup-dir DIR        Database backup directory (default: ../backups)
  --health-timeout SEC    Health-check timeout in seconds (default: 120)
  --dry-run               Print the update stages without changing anything
  -h, --help              Show this help
EOF
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

backup_dir=$(cd "$REPO_ROOT/.." && pwd)/backups
health_timeout=120
dry_run=0

while (($#)); do
  case "$1" in
    --backup-dir)
      backup_dir=${2:-}
      shift 2
      ;;
    --health-timeout)
      health_timeout=${2:-}
      shift 2
      ;;
    --dry-run)
      dry_run=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "Unknown argument: $1"
      ;;
  esac
done

[[ -n "$backup_dir" ]] || die "Backup directory must not be empty"
[[ "$health_timeout" =~ ^[1-9][0-9]*$ ]] || die "Health timeout must be a positive integer"

if ((dry_run)); then
  cat <<EOF
[server-update] Dry run only; no commands will be executed.
[server-update] 1. Validate Git, Docker, Compose, environment, branch, and worktree.
[server-update] 2. Back up MySQL to: $backup_dir
[server-update] 3. Fast-forward pull: origin/master
[server-update] 4. Build services: java vue (linux/amd64, cached bases)
[server-update] 5. Recreate and health-check: java
[server-update] 6. Recreate and health-check: vue
[server-update] 7. Print service status and update summary.
EOF
  exit 0
fi

for command_name in git docker curl gzip date; do
  require_command "$command_name"
done

compose_mode=
if docker compose version >/dev/null 2>&1; then
  compose_mode=plugin
elif command -v docker-compose >/dev/null 2>&1; then
  compose_mode=standalone
else
  die "Docker Compose was not found"
fi

compose() {
  if [[ "$compose_mode" == plugin ]]; then
    docker compose --env-file "$REPO_ROOT/.env" -f "$REPO_ROOT/compose.prod.yml" "$@"
  else
    docker-compose --env-file "$REPO_ROOT/.env" -f "$REPO_ROOT/compose.prod.yml" "$@"
  fi
}

wait_for_url() {
  local label=$1
  local url=$2
  local elapsed=0

  while ((elapsed < health_timeout)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      log "$label is healthy: $url"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done

  return 1
}

published_port() {
  local service=$1
  local container_port=$2
  local binding port

  binding=$(compose port "$service" "$container_port" | tail -n 1)
  port=${binding##*:}
  [[ "$port" =~ ^[0-9]+$ ]] || die "Unable to resolve published port for $service:$container_port"
  printf '%s\n' "$port"
}

cd "$REPO_ROOT"
[[ -f .env ]] || die "Production environment file not found: $REPO_ROOT/.env"
[[ -f compose.prod.yml ]] || die "Production Compose file not found"
[[ "$(git rev-parse --show-toplevel)" == "$REPO_ROOT" ]] || die "Script must run from its repository"
[[ "$(git branch --show-current)" == master ]] || die "Server repository must be on the master branch"
[[ -z "$(git status --porcelain)" ]] || die "Git worktree is not clean"

compose config >/dev/null
mysql_container=$(compose ps -q mysql)
[[ -n "$mysql_container" ]] || die "MySQL container is not running"
[[ "$(docker inspect --format '{{.State.Status}}' "$mysql_container")" == running ]] \
  || die "MySQL container is not running"

before_commit=$(git rev-parse HEAD)
mkdir -p "$backup_dir"
backup_file="$backup_dir/match-before-${before_commit:0:7}-$(date +%Y%m%d-%H%M%S).sql.gz"

log "Backing up database to $backup_file"
compose exec -T mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --events "$MYSQL_DATABASE"' \
  | gzip -c > "$backup_file"
[[ -s "$backup_file" ]] || die "Database backup is empty: $backup_file"
gzip -t "$backup_file" || die "Database backup is invalid: $backup_file"

log "Pulling origin/master with fast-forward only"
git pull --ff-only origin master
after_commit=$(git rev-parse HEAD)

export DOCKER_DEFAULT_PLATFORM=linux/amd64
export DOCKER_BUILDKIT=1

log "Building Java and Vue images"
compose build java vue

log "Recreating Java service"
compose up -d --no-deps --force-recreate java
backend_port=$(published_port java 19141)
if ! wait_for_url "Java" "http://127.0.0.1:$backend_port/health"; then
  compose logs --tail 120 java >&2 || true
  die "Java health check timed out after ${health_timeout}s"
fi

log "Recreating Vue service"
compose up -d --no-deps --force-recreate vue
frontend_port=$(published_port vue 19090)
if ! wait_for_url "Vue" "http://127.0.0.1:$frontend_port/"; then
  compose logs --tail 120 vue >&2 || true
  die "Vue health check timed out after ${health_timeout}s"
fi

compose ps
log "Update completed: ${before_commit:0:7} -> ${after_commit:0:7}"
log "Database backup: $backup_file"
