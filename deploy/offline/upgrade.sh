#!/usr/bin/env bash

set -Eeuo pipefail

PACKAGE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=_common.sh
source "$PACKAGE_DIR/_common.sh"

usage() {
  cat <<'EOF'
Usage: upgrade.sh --install-dir ABSOLUTE_PATH

Updates Java/Vue images and configuration. MySQL, FastDFS, dataset and scoring
data are preserved.
EOF
}

install_dir=
while (($#)); do
  case "$1" in
    --install-dir) install_dir=${2:-}; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done

assert_safe_install_dir "$install_dir"
[[ -f "$install_dir/.env" ]] || die "Existing installation not found: $install_dir"
[[ -f "$install_dir/release.env" ]] || die "Existing release metadata not found"
require_amd64
for command_name in docker gzip sha256sum curl; do
  require_command "$command_name"
done
docker compose version >/dev/null
docker info >/dev/null

verify_package "$PACKAGE_DIR"
load_release_env "$PACKAGE_DIR/release.env"
new_version=$MATCH_RELEASE_VERSION
load_release_env "$install_dir/release.env"
old_version=$MATCH_RELEASE_VERSION
[[ "$new_version" != "$old_version" ]] || die "Release $new_version is already installed"

timestamp=$(date +%Y%m%d-%H%M%S)
backup_dir="$install_dir/backups/upgrade-$timestamp"
mkdir -p "$backup_dir"
backup_database "$install_dir" "$backup_dir/mysql.sql.gz"
install -m 0644 "$install_dir/release.env" "$backup_dir/release.env"
install -m 0644 "$install_dir/compose.offline.yml" "$backup_dir/compose.offline.yml"

load_images "$PACKAGE_DIR"
check_loaded_images "$new_version"
install -m 0644 "$PACKAGE_DIR/compose.offline.yml" "$install_dir/compose.offline.yml"
install -m 0644 "$PACKAGE_DIR/release.env" "$install_dir/release.env"
install -m 0755 "$PACKAGE_DIR/_common.sh" "$install_dir/_common.sh"
install -m 0755 "$PACKAGE_DIR/verify.sh" "$install_dir/verify.sh"
install -m 0755 "$PACKAGE_DIR/uninstall.sh" "$install_dir/uninstall.sh"
validate_install_config "$install_dir"

log "Recreating Java and Vue; persistent services are unchanged"
compose_at "$install_dir" up -d --no-deps --force-recreate java vue

if ! "$install_dir/verify.sh" --install-dir "$install_dir"; then
  printf 'Rollback metadata: cp %q %q && cp %q %q\n' \
    "$backup_dir/release.env" "$install_dir/release.env" \
    "$backup_dir/compose.offline.yml" "$install_dir/compose.offline.yml" >&2
  die "Upgrade verification failed. Database backup: $backup_dir/mysql.sql.gz"
fi

printf '%s\n' "$new_version" > "$install_dir/.installed-version"
log "Upgrade completed: $old_version -> $new_version"
