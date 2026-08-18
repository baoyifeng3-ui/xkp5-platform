#!/usr/bin/env bash
set -Eeuo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT

"$repo_root/deploy/agent-ca.sh" --management-ip 127.0.0.1 --output "$temp_dir/tls"
ca_fingerprint=$(openssl x509 -in "$temp_dir/tls/ca.crt" -noout -fingerprint -sha256)
"$repo_root/deploy/agent-ca.sh" --management-ip 127.0.0.1 --output "$temp_dir/tls"

openssl verify -CAfile "$temp_dir/tls/ca.crt" "$temp_dir/tls/server.crt" | grep -q ': OK$'
openssl x509 -in "$temp_dir/tls/server.crt" -noout -ext subjectAltName | grep -q 'IP Address:127.0.0.1'
[[ "$(openssl x509 -in "$temp_dir/tls/ca.crt" -noout -fingerprint -sha256)" == "$ca_fingerprint" ]]
[[ "$(stat -c '%a' "$temp_dir/tls/ca.key")" == 600 ]]
[[ "$(stat -c '%a' "$temp_dir/tls/server.key")" == 600 ]]

printf 'Agent CA tests passed.\n'
