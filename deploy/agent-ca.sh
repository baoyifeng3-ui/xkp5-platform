#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

usage() { printf 'Usage: agent-ca.sh --management-ip IP --output DIRECTORY\n'; }
management_ip=
output=
while (($#)); do
  case "$1" in
    --management-ip) management_ip=${2:-}; shift 2 ;;
    --output) output=${2:-}; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done

[[ "$management_ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || { printf 'Invalid management IP\n' >&2; exit 2; }
IFS=. read -r a b c d <<< "$management_ip"
for octet in "$a" "$b" "$c" "$d"; do ((octet <= 255)) || { printf 'Invalid management IP\n' >&2; exit 2; }; done
if command -v ip >/dev/null 2>&1; then
  ip -o addr show | grep -Fq " $management_ip/" || { printf 'Management IP is not assigned locally\n' >&2; exit 2; }
elif [[ "$management_ip" != 127.* ]] && ! hostname -I | tr ' ' '\n' | grep -Fxq "$management_ip"; then
  printf 'Management IP is not assigned locally\n' >&2; exit 2
fi
[[ -n "$output" && "$output" = /* ]] || { printf 'Output must be an absolute directory\n' >&2; exit 2; }

mkdir -p "$output"
if [[ ! -s "$output/ca.key" || ! -s "$output/ca.crt" ]]; then
  openssl genrsa -out "$output/ca.key" 4096 >/dev/null 2>&1
  openssl req -x509 -new -sha256 -days 3650 -key "$output/ca.key" -out "$output/ca.crt" \
    -subj '/CN=XKP5.0 Processing Agent CA' >/dev/null 2>&1
fi

config=$(mktemp)
csr=$(mktemp)
trap 'rm -f "$config" "$csr"' EXIT
printf '[req]\ndistinguished_name=dn\nreq_extensions=ext\nprompt=no\n[dn]\nCN=%s\n[ext]\nsubjectAltName=IP:%s\nextendedKeyUsage=serverAuth\n' \
  "$management_ip" "$management_ip" > "$config"
openssl genrsa -out "$output/server.key" 3072 >/dev/null 2>&1
openssl req -new -key "$output/server.key" -out "$csr" -config "$config" >/dev/null 2>&1
openssl x509 -req -sha256 -days 825 -in "$csr" -CA "$output/ca.crt" -CAkey "$output/ca.key" \
  -CAcreateserial -out "$output/server.crt" -extfile "$config" -extensions ext >/dev/null 2>&1
chmod 0600 "$output/ca.key" "$output/server.key"
chmod 0644 "$output/ca.crt" "$output/server.crt"
printf 'Agent TLS certificates ready in %s\n' "$output"
