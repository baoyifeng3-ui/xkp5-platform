#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

log_file=/var/log/xkp5-install.log
mkdir -p "$(dirname "$log_file")"
exec > >(tee -a "$log_file") 2>&1

log() { printf '[xkp5-install] %s\n' "$*"; }
die() { printf '[xkp5-install] ERROR: %s\n' "$*" >&2; exit 1; }
usage() {
  cat <<'EOF'
Usage: quick-install.sh [options]
  --source-dir DIR              Prepared XKP5 source/release directory
  --install-dir DIR             Install target (default /opt/xkp5-platform)
  --compose-file FILE           Compose filename in source (default compose.prod.yml)
  --ubuntu-version VERSION      Required Ubuntu version (default 22.04)
  --server-ip IP                Fixed management IPv4
  --license-public-keys VALUE   keyId=base64X509PublicKey pairs
  --configure-ufw               Open 19140/tcp and 19443/tcp
  --prepare-only                Write files/configuration without starting services
  --non-interactive             Fail instead of prompting
  -h, --help
EOF
}

source_dir=
install_dir=/opt/xkp5-platform
compose_file=compose.prod.yml
ubuntu_version=22.04
server_ip=
license_public_keys=
configure_ufw=0
non_interactive=0
prepare_only=0

while (($#)); do
  case "$1" in
    --source-dir) source_dir=${2:-}; shift 2 ;;
    --install-dir) install_dir=${2:-}; shift 2 ;;
    --compose-file) compose_file=${2:-}; shift 2 ;;
    --ubuntu-version) ubuntu_version=${2:-}; shift 2 ;;
    --server-ip) server_ip=${2:-}; shift 2 ;;
    --license-public-keys) license_public_keys=${2:-}; shift 2 ;;
    --configure-ufw) configure_ufw=1; shift ;;
    --prepare-only) prepare_only=1; shift ;;
    --non-interactive) non_interactive=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done

[[ $(id -u) -eq 0 ]] || die 'Run as root'
[[ -r /etc/os-release ]] || die '/etc/os-release is missing'
# shellcheck disable=SC1091
. /etc/os-release
[[ $ubuntu_version == 20.04 || $ubuntu_version == 22.04 ]] || die "Unsupported Ubuntu version: $ubuntu_version"
[[ ${ID:-} == ubuntu && ${VERSION_ID:-} == "$ubuntu_version" ]] || die "Ubuntu $ubuntu_version is required"
[[ $(dpkg --print-architecture) == amd64 ]] || die 'amd64 is required'

for command_name in docker curl openssl htpasswd install findmnt ip realpath; do
  command -v "$command_name" >/dev/null 2>&1 || die "Required command not found: $command_name"
done
docker compose version >/dev/null 2>&1 || die 'Docker Compose v2 is required'

safe_dir() {
  local value=${1:-}
  [[ $value == /* && $value != / && $value != /opt && $value != /root && $value != /home ]] \
    || die "Unsafe install directory: ${value:-<empty>}"
  [[ $value != *'/../'* && $value != */.. && $value != *'//'* ]] || die "Install directory is not normalized: $value"
}

safe_dir "$install_dir"
[[ -n $source_dir && -d $source_dir ]] || die 'A valid --source-dir is required'
source_dir=$(realpath "$source_dir")
[[ -f "$source_dir/$compose_file" ]] || die "Compose file not found: $source_dir/$compose_file"

if [[ -z $server_ip && $non_interactive -eq 0 ]]; then read -r -p 'Management server IPv4: ' server_ip; fi
if [[ -z $license_public_keys && $non_interactive -eq 0 ]]; then read -r -p 'License public keys (keyId=base64): ' license_public_keys; fi
[[ $server_ip =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || die 'A valid --server-ip is required'
IFS=. read -r ip1 ip2 ip3 ip4 <<< "$server_ip"
for octet in "$ip1" "$ip2" "$ip3" "$ip4"; do ((octet <= 255)) || die 'Invalid server IPv4'; done
ip -o addr show | grep -Fq " $server_ip/" || die 'Server IP is not assigned locally'
[[ $license_public_keys =~ ^[^=,[:space:]]+=[A-Za-z0-9+/=_-]+(,[^=,[:space:]]+=[A-Za-z0-9+/=_-]+)*$ ]] \
  || die 'A valid --license-public-keys value is required'

if [[ -d $install_dir ]] && find "$install_dir" -mindepth 1 -print -quit | grep -q .; then
  die "Install directory is not empty: $install_dir"
fi
if docker ps -a --format '{{.Labels}}' | grep -Eq 'com\.docker\.compose\.project=(match-v2|xkp5-platform)'; then
  die 'Existing XKP5 Compose containers were found'
fi
if docker volume ls --format '{{.Name}}' | grep -Eq '(^|_)(match_prod_mysql|mysql_data)$|^match-v2_registry_data$'; then
  die 'Existing XKP5 Docker data volumes were found'
fi

log "Installing application files into $install_dir"
mkdir -p "$install_dir"
cp -a "$source_dir/." "$install_dir/"
cd "$install_dir"

config_dir=/etc/xkp5
agent_tls_dir=/etc/xkp/agent-tls
registry_tls_dir=/etc/xkp/registry-tls
registry_auth_dir=/etc/xkp/registry-auth
registry_secrets_dir=/etc/xkp/registry-secrets
code_server_tls_dir=/etc/xkp/code-server-tls
mkdir -p "$config_dir" "$agent_tls_dir" "$registry_tls_dir" "$registry_auth_dir" "$registry_secrets_dir" "$code_server_tls_dir"
chmod 0700 "$config_dir" "$registry_tls_dir" "$registry_auth_dir" "$registry_secrets_dir" "$code_server_tls_dir"

db_password=$(openssl rand -hex 32)
competition_key=$(openssl rand -base64 32 | tr -d '\n')
registry_password=$(openssl rand -hex 32)
registry_user=xkp-importer

host_identity_script="$install_dir/deploy/host-identity.sh"
agent_ca_script="$install_dir/deploy/agent-ca.sh"
code_server_ca_script="$install_dir/deploy/code-server-ca.sh"
[[ -x $host_identity_script ]] || host_identity_script="$install_dir/host-identity.sh"
[[ -x $agent_ca_script ]] || agent_ca_script="$install_dir/agent-ca.sh"
[[ -x $code_server_ca_script ]] || code_server_ca_script="$install_dir/code-server-ca.sh"
[[ -x $host_identity_script && -x $agent_ca_script && -x $code_server_ca_script ]] || die 'Host identity or TLS script is missing'
log 'Initializing host identity and Agent TLS'
XKP_HOST_IDENTITY_OUTPUT=/etc/xkp/host-identity.json "$host_identity_script"
"$agent_ca_script" --management-ip "$server_ip" --output "$agent_tls_dir"
"$code_server_ca_script" --ca-dir "$code_server_tls_dir" --agent-id bootstrap --agent-ip "$server_ip"
rm -rf "$code_server_tls_dir/agents"

log 'Creating internal Registry TLS and credentials'
openssl genrsa -out "$registry_tls_dir/ca.key" 4096 >/dev/null 2>&1
openssl req -x509 -new -sha256 -days 3650 -key "$registry_tls_dir/ca.key" -out "$registry_tls_dir/ca.crt" \
  -subj '/CN=XKP5 Internal Registry CA' >/dev/null 2>&1
registry_config=$(mktemp)
registry_csr=$(mktemp)
trap 'rm -f "$registry_config" "$registry_csr"' EXIT
printf '[req]\ndistinguished_name=dn\nreq_extensions=ext\nprompt=no\n[dn]\nCN=registry\n[ext]\nsubjectAltName=DNS:registry\nextendedKeyUsage=serverAuth\n' > "$registry_config"
openssl genrsa -out "$registry_tls_dir/tls.key" 3072 >/dev/null 2>&1
openssl req -new -key "$registry_tls_dir/tls.key" -out "$registry_csr" -config "$registry_config" >/dev/null 2>&1
openssl x509 -req -sha256 -days 825 -in "$registry_csr" -CA "$registry_tls_dir/ca.crt" \
  -CAkey "$registry_tls_dir/ca.key" -CAcreateserial -out "$registry_tls_dir/tls.crt" \
  -extfile "$registry_config" -extensions ext >/dev/null 2>&1
printf '%s\n' "$registry_user" > "$registry_secrets_dir/username"
printf '%s\n' "$registry_password" > "$registry_secrets_dir/password"
htpasswd -Bbn "$registry_user" "$registry_password" > "$registry_auth_dir/htpasswd"
chmod 0600 "$registry_tls_dir/ca.key" "$registry_tls_dir/tls.key" "$registry_secrets_dir/"* "$registry_auth_dir/htpasswd"
chmod 0644 "$registry_tls_dir/ca.crt" "$registry_tls_dir/tls.crt"

release_version=
registry_image=registry:2
registry_importer_image=xkp5/registry-importer:latest
if [[ -f $install_dir/release.env ]]; then
  # release.env is generated by the checksummed offline release builder.
  # shellcheck disable=SC1091
  source "$install_dir/release.env"
  release_version=${MATCH_RELEASE_VERSION:-}
  registry_image=${MATCH_REGISTRY_IMAGE:-$registry_image}
  registry_importer_image=${MATCH_REGISTRY_IMPORTER_IMAGE:-$registry_importer_image}
fi

env_tmp=$(mktemp "$config_dir/.xkp5.env.XXXXXX")
cat > "$env_tmp" <<EOF
MATCH_FRONTEND_PORT=19140
MATCH_BACKEND_PORT=19141
MATCH_DB_PORT=3307
XKP_AGENT_PORT=19443
MATCH_DB_NAME=match
MATCH_DB_USER=root
MATCH_DB_PASSWORD=$db_password
MYSQL_ROOT_PASSWORD=$db_password
MATCH_COMPETITION_PASSWORD_KEY=$competition_key
MATCH_PARTICIPANT_LOGIN_GATE_ENABLED=true
MATCH_ALLOWED_ORIGINS=http://$server_ip:19140
TERMINAL_AGENT_RELAY_URL=wss://$server_ip:19443/terminal/v1/agent
XKP_AGENT_MANAGEMENT_URL=https://$server_ip:19443
XKP_LICENSE_PUBLIC_KEYS=$license_public_keys
XKP_AGENT_TLS_DIR=$agent_tls_dir
XKP_CODE_SERVER_TLS_DIR=$code_server_tls_dir
XKP_CODE_SERVER_ROOT_CA_FILE=$code_server_tls_dir/rootCA.pem
XKP_CODE_SERVER_CA_KEY_FILE=$code_server_tls_dir/ca.key
XKP_AGENT_PACKAGE_DIR=$install_dir/xkp-agent
MATCH_SSH_BRIDGE_URL=${MATCH_SSH_BRIDGE_URL:-http://127.0.0.1:19245}
MATCH_SSH_BRIDGE_TOKEN=${MATCH_SSH_BRIDGE_TOKEN:-xkp5-development-ssh-bridge}
XKP_REGISTRY_IMAGE=$registry_image
XKP_REGISTRY_IMPORTER_IMAGE=$registry_importer_image
XKP_REGISTRY_TLS_DIR=$registry_tls_dir
XKP_REGISTRY_CA_FILE=$registry_tls_dir/ca.crt
XKP_REGISTRY_AUTH_FILE=$registry_auth_dir/htpasswd
XKP_REGISTRY_IMPORTER_SECRETS_DIR=$registry_secrets_dir
XKP_REGISTRY_STAGING_DIR=/var/lib/xkp/registry/staging
XKP_REGISTRY_IMPORT_DIR=/var/lib/xkp/registry/import
XKP_REGISTRY_STAGING_ROOT=/data/registry-staging
XKP_REGISTRY_IMPORT_ROOT=/data/registry-import
XKP_REGISTRY_MAX_FILE_BYTES=21474836480
XKP_REGISTRY_VOLUME_NAME=match-v2_registry_data
MATCH_RELEASE_VERSION=$release_version
EOF
install -o root -g root -m 0600 "$env_tmp" "$config_dir/xkp5.env"
install -o root -g root -m 0600 "$env_tmp" "$install_dir/.env"
rm -f "$env_tmp" "$registry_config" "$registry_csr"
trap - EXIT

compose=(docker compose --env-file "$install_dir/.env" -f "$install_dir/$compose_file")
log 'Validating Compose configuration'
"${compose[@]}" config --quiet
if ((prepare_only)); then
  log "Preparation complete: $install_dir"
  exit 0
fi
log 'Starting XKP5 services'
"${compose[@]}" up -d --build

wait_url() {
  local label=$1 url=$2 remaining=180
  until curl -fsS "$url" >/dev/null 2>&1; do
    remaining=$((remaining - 2))
    if ((remaining <= 0)); then
      "${compose[@]}" logs --tail 100 >&2 || true
      die "$label health check timed out"
    fi
    sleep 2
  done
  log "$label is ready"
}
wait_url backend http://127.0.0.1:19141/health
wait_url frontend http://127.0.0.1:19140/

if ((configure_ufw)); then
  command -v ufw >/dev/null 2>&1 || die 'ufw is not installed'
  ufw allow 19140/tcp
  ufw allow 19443/tcp
fi

touch "$config_dir/installed"
chmod 0600 "$config_dir/installed"
"${compose[@]}" ps
log "Installation complete: http://$server_ip:19140"
log 'Initial administrator: admin (change the password immediately)'
log 'Import the signed license from Platform License after first login.'
