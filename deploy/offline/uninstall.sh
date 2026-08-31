#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=_common.sh
source "$SCRIPT_DIR/_common.sh"

usage() {
  cat <<'EOF'
Usage: uninstall.sh [--install-dir ABSOLUTE_PATH]

Stops and removes Match containers and the Compose network. Persistent data,
the MySQL volume and images are retained.
EOF
}

install_dir=$SCRIPT_DIR
while (($#)); do
  case "$1" in
    --install-dir) install_dir=${2:-}; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done

assert_safe_install_dir "$install_dir"
[[ -f "$install_dir/.env" ]] || die "Installation not found: $install_dir"
[[ -f "$install_dir/release.env" ]] || die "Release metadata not found: $install_dir/release.env"
compose_at "$install_dir" down
log "Containers removed; persistent data and images were retained"
