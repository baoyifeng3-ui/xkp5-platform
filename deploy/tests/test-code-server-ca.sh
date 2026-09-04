#!/usr/bin/env bash
set -Eeuo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT

"$repo_root/deploy/code-server-ca.sh" --ca-dir "$temp_dir/ca" --agent-id agent-1 --agent-ip 10.247.115.42
fingerprint=$(openssl x509 -in "$temp_dir/ca/rootCA.pem" -noout -fingerprint -sha256)
"$repo_root/deploy/code-server-ca.sh" --ca-dir "$temp_dir/ca" --agent-id agent-2 --agent-ip 10.247.115.43

openssl verify -CAfile "$temp_dir/ca/rootCA.pem" "$temp_dir/ca/agents/agent-1/code-cert.pem" | grep -q ': OK$'
openssl x509 -in "$temp_dir/ca/agents/agent-1/code-cert.pem" -noout -ext subjectAltName | grep -q 'IP Address:10.247.115.42'
[[ "$(openssl x509 -in "$temp_dir/ca/rootCA.pem" -noout -fingerprint -sha256)" == "$fingerprint" ]]
[[ "$(stat -c '%a' "$temp_dir/ca/ca.key")" == 600 ]]
[[ "$(stat -c '%a' "$temp_dir/ca/agents/agent-1/code-cert-key.pem")" == 600 ]]
printf 'code-server CA test passed\n'
