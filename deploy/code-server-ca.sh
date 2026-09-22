#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

usage() { printf 'Usage: code-server-ca.sh --ca-dir DIRECTORY --agent-id ID --agent-ip IP\n'; }
ca_dir= agent_id= agent_ip=
while (($#)); do
  case "$1" in
    --ca-dir) ca_dir=${2:-}; shift 2 ;;
    --agent-id) agent_id=${2:-}; shift 2 ;;
    --agent-ip) agent_ip=${2:-}; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done

[[ -n "$ca_dir" && "$ca_dir" = /* ]] || { printf 'CA directory must be absolute\n' >&2; exit 2; }
[[ "$agent_id" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$ ]] || { printf 'Invalid agent ID\n' >&2; exit 2; }
[[ "$agent_ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || { printf 'Invalid agent IP\n' >&2; exit 2; }
IFS=. read -r a b c d <<< "$agent_ip"
for octet in "$a" "$b" "$c" "$d"; do ((octet <= 255)) || { printf 'Invalid agent IP\n' >&2; exit 2; }; done

mkdir -p "$ca_dir/agents/$agent_id"
chmod 0700 "$ca_dir" "$ca_dir/agents" "$ca_dir/agents/$agent_id"
if [[ ! -s "$ca_dir/ca.key" || ! -s "$ca_dir/rootCA.pem" ]]; then
  openssl genrsa -out "$ca_dir/ca.key" 4096 >/dev/null 2>&1
  openssl req -x509 -new -sha256 -days 3650 -key "$ca_dir/ca.key" -out "$ca_dir/rootCA.pem" \
    -subj '/CN=XKP5 Code Server Root CA' >/dev/null 2>&1
fi

config=$(mktemp)
csr=$(mktemp)
trap 'rm -f "$config" "$csr"' EXIT
printf '[req]\ndistinguished_name=dn\nreq_extensions=ext\nprompt=no\n[dn]\nCN=%s\n[ext]\nsubjectAltName=IP:%s\nextendedKeyUsage=serverAuth\n' \
  "$agent_ip" "$agent_ip" > "$config"
leaf_dir="$ca_dir/agents/$agent_id"
openssl genrsa -out "$leaf_dir/code-cert-key.pem" 3072 >/dev/null 2>&1
openssl req -new -key "$leaf_dir/code-cert-key.pem" -out "$csr" -config "$config" >/dev/null 2>&1
openssl x509 -req -sha256 -days 825 -in "$csr" -CA "$ca_dir/rootCA.pem" -CAkey "$ca_dir/ca.key" \
  -CAcreateserial -out "$leaf_dir/code-cert.pem" -extfile "$config" -extensions ext >/dev/null 2>&1
chmod 0600 "$ca_dir/ca.key" "$leaf_dir/code-cert-key.pem"
chmod 0644 "$ca_dir/rootCA.pem" "$leaf_dir/code-cert.pem"
