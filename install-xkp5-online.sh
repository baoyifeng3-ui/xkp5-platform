#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

die() { printf '[xkp5-online] ERROR: %s\n' "$*" >&2; exit 1; }
usage() {
  cat <<'EOF'
Usage: install-xkp5-online.sh [options]
  --repo URL                     Git repository URL
  --ref BRANCH_OR_TAG            Release branch/tag (default main)
  --server-ip IP                 Fixed management IPv4
  --license-public-keys VALUE    keyId=base64X509PublicKey pairs
  --install-dir DIR              Install target (default /opt/xkp5-platform)
  --configure-ufw                Open 19140/tcp and 19443/tcp
  --non-interactive              Fail instead of prompting
  -h, --help
EOF
}

repo=
ref=main
server_ip=
license_public_keys=
install_dir=/opt/xkp5-platform
configure_ufw=0
non_interactive=0

while (($#)); do
  case "$1" in
    --repo) repo=${2:-}; shift 2 ;;
    --ref) ref=${2:-}; shift 2 ;;
    --server-ip) server_ip=${2:-}; shift 2 ;;
    --license-public-keys) license_public_keys=${2:-}; shift 2 ;;
    --install-dir) install_dir=${2:-}; shift 2 ;;
    --configure-ufw) configure_ufw=1; shift ;;
    --non-interactive) non_interactive=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done

[[ $(id -u) -eq 0 ]] || die 'Run as root'
if ((non_interactive)); then
  [[ -n $repo ]] || die '--repo is required in non-interactive mode'
  [[ -n $server_ip ]] || die '--server-ip is required in non-interactive mode'
  [[ -n $license_public_keys ]] || die '--license-public-keys is required in non-interactive mode'
else
  [[ -n $repo ]] || read -r -p 'Git repository URL: ' repo
  [[ -n $server_ip ]] || read -r -p 'Management server IPv4: ' server_ip
  [[ -n $license_public_keys ]] || read -r -p 'License public keys (keyId=base64): ' license_public_keys
fi
[[ -n $repo && -n $ref && -n $server_ip && -n $license_public_keys ]] || die 'Required values are missing'
if [[ -e $install_dir ]] && find "$install_dir" -mindepth 1 -print -quit | grep -q .; then
  die "Install directory is not empty: $install_dir"
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git gnupg openssl apache2-utils

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  . /etc/os-release
  printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu %s stable\n' \
    "$(dpkg --print-architecture)" "$VERSION_CODENAME" > /etc/apt/sources.list.d/docker.list
  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker

checkout_dir=$(mktemp -d /tmp/xkp5-online.XXXXXX)
trap 'rm -rf "$checkout_dir"' EXIT
git clone --branch "$ref" --depth 1 "$repo" "$checkout_dir/source"

docker build -f "$checkout_dir/source/deploy/registry/import-worker.Dockerfile" \
  -t xkp5/registry-importer:latest "$checkout_dir/source"

core_args=(
  --source-dir "$checkout_dir/source"
  --install-dir "$install_dir"
  --server-ip "$server_ip"
  --license-public-keys "$license_public_keys"
  --non-interactive
)
((configure_ufw)) && core_args+=(--configure-ufw)
"$checkout_dir/source/deploy/quick-install.sh" "${core_args[@]}"
