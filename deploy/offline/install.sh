#!/usr/bin/env bash

set -Eeuo pipefail

PACKAGE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=_common.sh
source "$PACKAGE_DIR/_common.sh"

usage() {
  cat <<'EOF'
Usage: install.sh --install-dir ABSOLUTE_PATH --env-file FILE

Installs this release on a new server. Existing Match data is never overwritten.
EOF
}

install_dir=
env_file=
while (($#)); do
  case "$1" in
    --install-dir) install_dir=${2:-}; shift 2 ;;
    --env-file) env_file=${2:-}; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done

assert_safe_install_dir "$install_dir"
[[ -f "$env_file" ]] || die "Environment file not found: $env_file"
require_amd64
for command_name in docker gzip sha256sum tar curl; do
  require_command "$command_name"
done
docker compose version >/dev/null
docker info >/dev/null

verify_package "$PACKAGE_DIR"
load_release_env "$PACKAGE_DIR/release.env"
check_runtime_empty "$install_dir"

log "Initializing the Ubuntu host identity"
"$PACKAGE_DIR/host-identity.sh"

mkdir -p "$(dirname "$install_dir")"
available_kb=$(df -Pk "$(dirname "$install_dir")" | awk 'NR == 2 {print $4}')
package_kb=$(du -sk "$PACKAGE_DIR" | awk '{print $1}')
required_kb=$((package_kb * 3))
(( available_kb >= required_kb )) || die "Insufficient disk space: need about ${required_kb} KiB, have ${available_kb} KiB"

load_images "$PACKAGE_DIR"
check_loaded_images "$MATCH_RELEASE_VERSION"
install_control_files "$PACKAGE_DIR" "$install_dir" "$env_file"
validate_install_config "$install_dir"
restore_file_archives "$PACKAGE_DIR" "$install_dir"

log "Starting MySQL and FastDFS"
compose_at "$install_dir" up -d mysql fastdfs-tracker fastdfs-storage
wait_for_mysql "$install_dir"
restore_database "$PACKAGE_DIR" "$install_dir"

log "Starting application services"
compose_at "$install_dir" up -d java vue
"$install_dir/verify.sh" --install-dir "$install_dir" --snapshot
printf '%s\n' "$MATCH_RELEASE_VERSION" > "$install_dir/.installed-version"
log "Installation completed: $MATCH_RELEASE_VERSION"
