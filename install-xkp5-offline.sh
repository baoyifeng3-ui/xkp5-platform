#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

die() { printf '[xkp5-offline] ERROR: %s\n' "$*" >&2; exit 1; }
usage() {
  cat <<'EOF'
Usage: install-xkp5-offline.sh ARCHIVE [options]
  --server-ip IP
  --license-public-keys VALUE
  --install-dir DIR              Default /opt/xkp5-platform
  --restore-snapshot             Restore the packaged MySQL/FastDFS snapshot
  --configure-ufw
  --non-interactive
EOF
}

archive=
server_ip=
license_public_keys=
install_dir=/opt/xkp5-platform
restore_snapshot=0
configure_ufw=0
non_interactive=0

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

[[ $(id -u) -eq 0 ]] || die 'Run as root'
[[ -f $archive ]] || die 'Offline archive is required'
. /etc/os-release
[[ ${ID:-} == ubuntu && ${VERSION_ID:-} == 22.04 ]] || die 'Ubuntu 22.04 is required'
[[ $(dpkg --print-architecture) == amd64 ]] || die 'amd64 is required'
if ((non_interactive)); then
  [[ -n $server_ip && -n $license_public_keys ]] || die 'Server IP and license public keys are required in non-interactive mode'
else
  [[ -n $server_ip ]] || read -r -p 'Management server IPv4: ' server_ip
  [[ -n $license_public_keys ]] || read -r -p 'License public keys (keyId=base64): ' license_public_keys
fi

if tar -tzf "$archive" | grep -Eq '(^/|(^|/)\.\.(/|$))'; then die 'Archive contains an unsafe path'; fi
extract_dir=$(mktemp -d /tmp/xkp5-offline.XXXXXX)
trap 'rm -rf "$extract_dir"' EXIT
tar -xzf "$archive" -C "$extract_dir"
package_dir=$(find "$extract_dir" -mindepth 1 -maxdepth 1 -type d -print -quit)
[[ -n $package_dir && -f $package_dir/SHA256SUMS ]] || die 'Invalid offline package'
(cd "$package_dir" && sha256sum -c SHA256SUMS)

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  debs=("$package_dir"/docker-debs/*.deb)
  [[ -e ${debs[0]} ]] || die 'Bundled Docker packages are missing'
  apt-get --no-download install -y "${debs[@]}"
fi
systemctl enable --now docker
gzip -dc "$package_dir/images/match-v2-images.tar.gz" | docker load

source_dir="$extract_dir/install-source"
mkdir -p "$source_dir/deploy"
install -m 0644 "$package_dir/compose.offline.yml" "$source_dir/compose.offline.yml"
install -m 0644 "$package_dir/release.env" "$source_dir/release.env"
install -m 0755 "$package_dir/deploy/quick-install.sh" "$source_dir/deploy/quick-install.sh"
for file in host-identity.sh agent-ca.sh _common.sh verify.sh uninstall.sh upgrade.sh reset-from-snapshot.sh; do
  [[ -f $package_dir/$file ]] && install -m 0755 "$package_dir/$file" "$source_dir/$file"
done
cp -a "$package_dir/xkp-agent" "$source_dir/xkp-agent"

core_args=(
  --source-dir "$source_dir"
  --install-dir "$install_dir"
  --compose-file compose.offline.yml
  --server-ip "$server_ip"
  --license-public-keys "$license_public_keys"
  --non-interactive
)
((configure_ufw)) && core_args+=(--configure-ufw)

if ((!restore_snapshot)); then
  "$package_dir/deploy/quick-install.sh" "${core_args[@]}"
  exit 0
fi

"$package_dir/deploy/quick-install.sh" "${core_args[@]}" --prepare-only
# shellcheck source=/dev/null
source "$package_dir/_common.sh"
verify_package "$package_dir"
load_release_env "$package_dir/release.env"
restore_file_archives "$package_dir" "$install_dir"
log 'Starting MySQL and FastDFS for snapshot restore'
compose_at "$install_dir" up -d mysql fastdfs-tracker fastdfs-storage
wait_for_mysql "$install_dir"
restore_database "$package_dir" "$install_dir"
log 'Starting all application services'
compose_at "$install_dir" up -d
"$install_dir/verify.sh" --install-dir "$install_dir" --snapshot
printf '%s\n' "$MATCH_RELEASE_VERSION" > "$install_dir/.installed-version"
log "Snapshot installation completed: $MATCH_RELEASE_VERSION"
