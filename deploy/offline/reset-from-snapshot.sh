#!/usr/bin/env bash

set -Eeuo pipefail

PACKAGE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=_common.sh
source "$PACKAGE_DIR/_common.sh"

usage() {
  cat <<'EOF'
Usage: reset-from-snapshot.sh --install-dir ABSOLUTE_PATH

DESTRUCTIVE: backs up the current installation, removes its MySQL volume and
runtime files, then restores this release snapshot. Interactive confirmation is
required.
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
verify_package "$PACKAGE_DIR"
load_release_env "$PACKAGE_DIR/release.env"
target_host=$(hostname)
expected="RESET $target_host TO $MATCH_RELEASE_VERSION"

cat <<EOF
This will replace all Match data on $target_host.

Install directory: $install_dir
Runtime directory: $install_dir/runtime
Docker volume: $MYSQL_VOLUME
Snapshot version: $MATCH_RELEASE_VERSION

A backup will be written below $install_dir/backups before deletion.
Type exactly: $expected
EOF
read -r confirmation
[[ "$confirmation" == "$expected" ]] || die "Confirmation did not match; no data was changed"

timestamp=$(date +%Y%m%d-%H%M%S)
backup_dir="$install_dir/backups/reset-$timestamp"
mkdir -p "$backup_dir"
backup_database "$install_dir" "$backup_dir/mysql.sql.gz"
tar --numeric-owner -czf "$backup_dir/runtime.tar.gz" -C "$install_dir/runtime" .
docker run --rm -v "${XKP_REGISTRY_VOLUME_NAME:-match-v2_registry_data}:/source:ro" \
  "${MATCH_REGISTRY_IMAGE:-registry:2}" sh -c 'tar -czf - -C /source .' > "$backup_dir/registry-data.tar.gz"
install -m 0600 "$install_dir/.env" "$backup_dir/env"
install -m 0644 "$install_dir/release.env" "$backup_dir/release.env"

log "Stopping the existing installation"
compose_at "$install_dir" down
docker volume rm "$MYSQL_VOLUME"
docker volume rm "${XKP_REGISTRY_VOLUME_NAME:-match-v2_registry_data}"

runtime_dir="$install_dir/runtime"
assert_safe_install_dir "$runtime_dir"
find "$runtime_dir" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +

load_images "$PACKAGE_DIR"
check_loaded_images "$MATCH_RELEASE_VERSION"
install -m 0644 "$PACKAGE_DIR/compose.offline.yml" "$install_dir/compose.offline.yml"
install -m 0644 "$PACKAGE_DIR/release.env" "$install_dir/release.env"
install -m 0755 "$PACKAGE_DIR/_common.sh" "$install_dir/_common.sh"
install -m 0755 "$PACKAGE_DIR/verify.sh" "$install_dir/verify.sh"
install -m 0755 "$PACKAGE_DIR/uninstall.sh" "$install_dir/uninstall.sh"
validate_install_config "$install_dir"
restore_file_archives "$PACKAGE_DIR" "$install_dir"
restore_registry_archive "$PACKAGE_DIR"

compose_at "$install_dir" up -d mysql fastdfs-tracker fastdfs-storage
wait_for_mysql "$install_dir"
restore_database "$PACKAGE_DIR" "$install_dir"
compose_at "$install_dir" up -d java vue
"$install_dir/verify.sh" --install-dir "$install_dir" --snapshot
printf '%s\n' "$MATCH_RELEASE_VERSION" > "$install_dir/.installed-version"
log "Snapshot reset completed. Pre-reset backup: $backup_dir"
