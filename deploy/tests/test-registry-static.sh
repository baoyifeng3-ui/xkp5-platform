#!/usr/bin/env bash

set -Eeuo pipefail

TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$TEST_DIR/../.." && pwd)

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

for compose_file in "$REPO_ROOT/compose.registry.yml" "$REPO_ROOT/compose.prod.yml" "$REPO_ROOT/compose.offline.yml"; do
  [[ -f "$compose_file" ]] || fail "Missing Compose file: $compose_file"
  grep -Eq '^  registry:' "$compose_file" || fail "Registry service missing: $compose_file"
  grep -Eq 'registry_data:/var/lib/registry' "$compose_file" || fail "Registry data volume missing: $compose_file"
  grep -Eq '/etc/xkp/registry-tls:ro' "$compose_file" || fail "Registry TLS mount missing: $compose_file"
  grep -Eq '^  registry-importer:' "$compose_file" || fail "Separate importer service missing: $compose_file"
  grep -Eq 'XKP_REGISTRY_STAGING_ROOT|registry_staging' "$compose_file" || fail "Importer staging configuration missing: $compose_file"
  importer_block=$(awk '/^  registry-importer:/{on=1} on{print} on && /^  [A-Za-z0-9_-]+:/{if ($0 !~ /^  registry-importer:/) exit}' "$compose_file")
  printf '%s\n' "$importer_block" | grep -Eq 'ca\.crt:ro' || fail "Importer must mount only the Registry CA: $compose_file"
  printf '%s\n' "$importer_block" | grep -Eq 'registry-secrets:ro|REGISTRY_USERNAME_FILE' || fail "Importer write credential boundary missing: $compose_file"
  if printf '%s\n' "$importer_block" | grep -Eq 'tls\.key|tls\.crt'; then
    fail "Importer must not mount Registry private key or server certificate: $compose_file"
  fi
  if awk '/^  registry:/{on=1; next} on && /^  [A-Za-z0-9_-]+:/{exit} on' "$compose_file" | grep -Eq '^    ports:'; then
    fail "Registry must not publish a public write port: $compose_file"
  fi
done

grep -q 'registry_image=$(env_value XKP_REGISTRY_IMAGE' "$REPO_ROOT/deploy/offline/build-release.sh" \
  || fail "Offline release export must resolve the Registry image from env"
grep -q 'registry_importer_image=$(env_value XKP_REGISTRY_IMPORTER_IMAGE' "$REPO_ROOT/deploy/offline/build-release.sh" \
  || fail "Offline release export must resolve the importer image from env"
grep -q 'MATCH_REGISTRY_IMAGE' "$REPO_ROOT/deploy/offline/_common.sh" \
  || fail "Offline image validation must consume release Registry metadata"
grep -q 'MATCH_REGISTRY_IMAGE:-\${XKP_REGISTRY_IMAGE' "$REPO_ROOT/compose.offline.yml" \
  || fail "Offline Compose must prefer packaged Registry metadata"
grep -q 'MATCH_REGISTRY_IMPORTER_IMAGE:-\${XKP_REGISTRY_IMPORTER_IMAGE' "$REPO_ROOT/compose.offline.yml" \
  || fail "Offline Compose must prefer packaged importer metadata"
grep -q 'if \[\[ -n "\${!key:-}" \]\]' "$REPO_ROOT/deploy/offline/build-release.sh" \
  || fail "Offline release must honor Compose precedence and ignore empty overrides"

for compose_file in "$REPO_ROOT/compose.prod.yml" "$REPO_ROOT/compose.offline.yml"; do
  java_block=$(awk '/^  java:/{on=1} on{print} on && /^  [A-Za-z0-9_-]+:/{if ($0 !~ /^  java:/) exit}' "$compose_file")
  if printf '%s\n' "$java_block" | grep -Eq 'docker.sock|/var/run/docker'; then
    fail "Java must not mount Docker Socket: $compose_file"
  fi
  if printf '%s\n' "$java_block" | grep -Eiq 'private\.key|private_key|LICENSE_PRIVATE'; then
    fail "Java must not receive a license private key: $compose_file"
  fi
  printf '%s\n' "$java_block" | grep -Eq 'XKP_REGISTRY_CA_FILE|registry-tls/ca\.crt:ro' || fail "Java Registry CA configuration missing: $compose_file"
done

printf 'Registry deployment static tests passed.\n'
