#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

die() { printf '[xkp5-offline] ERROR: %s\n' "$*" >&2; exit 1; }
printf '[xkp5-offline] Installer v7\n'
expected_archive_sha256=6382ca696bf6f25f1ac3e8af394c13895d7314ed1e70168b9c61987c9a827091
default_license_public_keys='production-2026-09=MCowBQYDK2VwAyEAhACmob5sstgGFW1COD6sy3BldAoMxG8XP3K5tYzHBoI='
detect_server_ip() {
  local detected
  detected=$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++)if($i=="src"){print $(i+1);exit}}')
  [[ -n $detected ]] || detected=$(hostname -I 2>/dev/null | awk '{print $1}')
  printf '%s\n' "$detected"
}
detect_archive() {
  local script_dir candidates
  script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
  shopt -s nullglob
  candidates=("$script_dir"/xkp5-offline-*.tar.gz)
  shopt -u nullglob
  [[ ${#candidates[@]} -eq 1 ]] || return 1
  printf '%s\n' "${candidates[0]}"
}
usage() {
  cat <<'EOF'
Usage: install-xkp5-offline.sh ARCHIVE [options]
  --server-ip IP
  --license-public-keys VALUE
  --install-dir DIR              Default /opt/xkp5-platform
  --restore-snapshot             Restore the packaged MySQL/FastDFS snapshot
  --configure-ufw
  --non-interactive             Accepted for compatibility; installation is always unattended
EOF
}

archive=
server_ip=
license_public_keys=
install_dir=/opt/xkp5-platform
restore_snapshot=0
configure_ufw=0
non_interactive=1

if (($#)) && [[ $1 != -* ]]; then archive=$1; shift; fi
while (($#)); do
  case "$1" in
    --server-ip) server_ip=${2:-}; shift 2 ;;
    --license-public-keys) license_public_keys=${2:-}; shift 2 ;;
    --install-dir) install_dir=${2:-}; shift 2 ;;
    --restore-snapshot) restore_snapshot=1; shift ;;
    --configure-ufw) configure_ufw=1; shift ;;
    --non-interactive) non_interactive=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done

[[ -n $archive ]] || archive=$(detect_archive) || die 'Place exactly one xkp5-offline-*.tar.gz beside this installer'

[[ $(id -u) -eq 0 ]] || die 'Run as root'
[[ -f $archive ]] || die 'Offline archive is required'
actual_archive_sha256=$(sha256sum "$archive" | awk '{print $1}')
[[ $actual_archive_sha256 == "$expected_archive_sha256" ]] \
  || die "Archive SHA256 mismatch: $actual_archive_sha256"
printf '[xkp5-offline] Archive SHA256 verified\n'
. /etc/os-release
[[ ${ID:-} == ubuntu ]] || die 'Ubuntu is required'
[[ $(dpkg --print-architecture) == amd64 ]] || die 'amd64 is required'
[[ -n $server_ip ]] || server_ip=$(detect_server_ip)
[[ -n $server_ip ]] || die 'Unable to detect the management server IPv4; use --server-ip'
[[ -n $license_public_keys ]] || license_public_keys=$default_license_public_keys

if tar -tf "$archive" | grep -Eq '(^/|(^|/)\.\.(/|$))'; then die 'Archive contains an unsafe path'; fi
extract_dir=$(mktemp -d /tmp/xkp5-offline.XXXXXX)
trap 'rm -rf "$extract_dir"' EXIT
tar -xf "$archive" -C "$extract_dir"
package_dir=$(find "$extract_dir" -mindepth 1 -maxdepth 1 -type d -print -quit)
[[ -n $package_dir && -f $package_dir/SHA256SUMS ]] || die 'Invalid offline package'
package_name=$(basename "$package_dir")
tar -xOf "$archive" "$package_name/release.env" > "$package_dir/release.env"
for required in release.env compose.offline.yml sanitize-portable-seed.sql images/match-v2-images.tar.gz data/mysql.sql.gz data/registry-data.tar.gz data/registry-staging.tar.gz; do
  [[ -f $package_dir/$required ]] || die "Package is missing $required"
done
find "$package_dir" -type f -name '*.sh' -exec sed -i 's/\r$//' {} +
find "$package_dir" -type f -name '*.sh' -exec chmod 0755 {} +
sed -i 's/\r$//' "$package_dir/release.env"
package_ubuntu_version=$(sed -n 's/^MATCH_RELEASE_UBUNTU_VERSION=//p' "$package_dir/release.env")
[[ $package_ubuntu_version == 20.04 || $package_ubuntu_version == 22.04 ]] || die 'Package Ubuntu version is missing or unsupported'
[[ ${VERSION_ID:-} == "$package_ubuntu_version" ]] || die "Ubuntu $package_ubuntu_version is required by this package"
package_data_mode=$(sed -n 's/^MATCH_RELEASE_DATA_MODE=//p' "$package_dir/release.env")
[[ $package_data_mode == FULL ]] && restore_snapshot=1

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  debs=("$package_dir"/docker-debs/*.deb)
  [[ -e ${debs[0]} ]] || die 'Bundled Docker packages are missing'
  dpkg -i "${debs[@]}" || true
  apt-get --fix-broken --no-download install -y
fi
systemctl enable --now docker
gzip -dc "$package_dir/images/match-v2-images.tar.gz" | docker load

if [[ -d $install_dir ]] && find "$install_dir" -mindepth 1 -print -quit | grep -q .; then
  if [[ -e /etc/xkp5/installed ]] \
      || docker ps -a -q --filter 'label=com.docker.compose.project=match-v2' | grep -q . \
      || [[ ! -f $install_dir/compose.offline.yml && ! -f $install_dir/release.env ]]; then
    die "Install directory is not empty: $install_dir"
  fi
  incomplete_dir="$install_dir.incomplete-$(date +%Y%m%d-%H%M%S)"
  mv "$install_dir" "$incomplete_dir"
  printf '[xkp5-offline] Archived incomplete installation: %s\n' "$incomplete_dir"
fi

source_dir="$extract_dir/install-source"
mkdir -p "$source_dir/deploy"
install -m 0644 "$package_dir/compose.offline.yml" "$source_dir/compose.offline.yml"
install -m 0644 "$package_dir/release.env" "$source_dir/release.env"
install -m 0755 "$package_dir/deploy/quick-install.sh" "$source_dir/deploy/quick-install.sh"
for file in host-identity.sh agent-ca.sh code-server-ca.sh _common.sh verify.sh uninstall.sh upgrade.sh reset-from-snapshot.sh; do
  [[ -f $package_dir/$file ]] && install -m 0755 "$package_dir/$file" "$source_dir/$file"
done
cp -a "$package_dir/xkp-agent" "$source_dir/xkp-agent"
sed -i 's#runtime/fastdfs/storage_data/data#runtime/fastdfs/storage_data#' "$source_dir/verify.sh"

core_args=(
  --source-dir "$source_dir"
  --install-dir "$install_dir"
  --compose-file compose.offline.yml
  --ubuntu-version "$package_ubuntu_version"
  --server-ip "$server_ip"
  --license-public-keys "$license_public_keys"
  --non-interactive
)
((configure_ufw)) && core_args+=(--configure-ufw)

if ((!restore_snapshot)); then
  bash "$package_dir/deploy/quick-install.sh" "${core_args[@]}"
  exit 0
fi

bash "$package_dir/deploy/quick-install.sh" "${core_args[@]}" --prepare-only
competition_key=$(openssl rand -base64 32 | tr -d '\n')
sed -i "s|^MATCH_COMPETITION_PASSWORD_KEY=.*|MATCH_COMPETITION_PASSWORD_KEY=$competition_key|" "$install_dir/.env"
# shellcheck source=/dev/null
source "$package_dir/_common.sh"
load_release_env "$package_dir/release.env"
restore_file_archives "$package_dir" "$install_dir"
restore_registry_archive "$package_dir"
log 'Starting MySQL and FastDFS for snapshot restore'
compose_at "$install_dir" up -d mysql fastdfs-tracker fastdfs-storage
wait_for_mysql "$install_dir"
restore_database "$package_dir" "$install_dir"
log 'Starting all application services'
compose_at "$install_dir" up -d
"$install_dir/verify.sh" --install-dir "$install_dir" --snapshot
printf '%s\n' "$MATCH_RELEASE_VERSION" > "$install_dir/.installed-version"
log "Snapshot installation completed: $MATCH_RELEASE_VERSION"
