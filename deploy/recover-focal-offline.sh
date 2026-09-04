#!/usr/bin/env bash
set -Eeuo pipefail
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
[[ $(id -u) -eq 0 ]] || { printf 'Run as root\n' >&2; exit 1; }
(cd "$script_dir" && sha256sum -c SHA256SUMS)
dpkg -i "$script_dir"/*.deb || true
apt-get --fix-broken --no-download install -y
dpkg --configure -a
systemctl enable --now docker
docker --version
docker compose version
