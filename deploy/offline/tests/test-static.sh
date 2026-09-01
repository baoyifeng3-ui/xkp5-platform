#!/usr/bin/env bash

set -Eeuo pipefail

TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
OFFLINE_DIR=$(cd "$TEST_DIR/.." && pwd)
REPO_ROOT=$(cd "$OFFLINE_DIR/../.." && pwd)

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

for script in "$OFFLINE_DIR"/*.sh; do
  bash -n "$script" || fail "Syntax check failed: $script"
done

for script in build-release.sh install.sh upgrade.sh reset-from-snapshot.sh verify.sh uninstall.sh; do
  "$OFFLINE_DIR/$script" --help >/dev/null || fail "Help failed: $script"
done

(
  # shellcheck source=../_common.sh
  source "$OFFLINE_DIR/_common.sh"
  assert_safe_install_dir /opt/match-v2
)

for unsafe_path in '' / /root /home /opt /opt/match-v2/../.. /opt//match-v2; do
  if (
    # shellcheck source=../_common.sh
    source "$OFFLINE_DIR/_common.sh"
    assert_safe_install_dir "$unsafe_path"
  ) >/dev/null 2>&1; then
    fail "Unsafe path was accepted: ${unsafe_path:-<empty>}"
  fi
done

if grep -Eq '^[[:space:]]+build:' "$REPO_ROOT/compose.offline.yml"; then
  fail "Offline Compose must not contain build directives"
fi

grep -q 'image: match-v2_java:latest' "$REPO_ROOT/compose.prod.yml" \
  || fail "Production Java image name must be stable"
grep -q 'image: match-v2_vue:latest' "$REPO_ROOT/compose.prod.yml" \
  || fail "Production Vue image name must be stable"
for compose_file in "$REPO_ROOT/compose.prod.yml" "$REPO_ROOT/compose.offline.yml"; do
  grep -q 'XKP_LICENSE_PUBLIC_KEYS:' "$compose_file" \
    || fail "Production Compose must configure the license public keys: $compose_file"
  grep -q 'XKP_HOST_IDENTITY_FILE: /etc/xkp/host-identity.json' "$compose_file" \
    || fail "Production Compose must configure the host identity path: $compose_file"
  grep -q '/etc/xkp/host-identity.json:/etc/xkp/host-identity.json:ro' "$compose_file" \
    || fail "Production Compose must mount the host identity read-only: $compose_file"
  grep -q '\${XKP_AGENT_PORT:-19443}:19443' "$compose_file" \
    || fail "Production Compose must expose the Agent TLS listener: $compose_file"
  grep -q '/etc/xkp/agent-tls:ro' "$compose_file" \
    || fail "Production Compose must mount Agent TLS files read-only: $compose_file"
  if grep -q 'XKP_LICENSE_TEST_PUBLIC_KEY\|XKP_DEVELOPMENT_IDENTITY' "$compose_file"; then
    fail "Production Compose must not contain development licensing settings: $compose_file"
  fi
  if grep -qi 'private.key\|private_key\|PRIVATE_KEY' "$compose_file"; then
    fail "Production Compose must never contain a license private key: $compose_file"
  fi
done
grep -q 'host-identity.sh' "$OFFLINE_DIR/build-release.sh" \
  || fail "Release build must include host-identity.sh"
grep -q 'agent-ca.sh' "$OFFLINE_DIR/build-release.sh" \
  || fail "Release build must include agent-ca.sh"
grep -q 'host-identity.sh' "$OFFLINE_DIR/install.sh" \
  || fail "Offline install must initialize the host identity"

temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT
cp "$REPO_ROOT/compose.offline.yml" "$temp_dir/compose.offline.yml"
cp "$OFFLINE_DIR/.env.example" "$temp_dir/.env"
printf 'MATCH_RELEASE_VERSION=test-release\n' > "$temp_dir/release.env"

# shellcheck source=../_common.sh
source "$OFFLINE_DIR/_common.sh"
images=$(compose_at "$temp_dir" config --images | sort -u)
expected_images=$(printf '%s\n' \
  delron/fastdfs:latest \
  match-v2_java:test-release \
  match-v2_vue:test-release \
  mysql:8.0 \
  registry:2 \
  xkp5/registry-importer:latest | sort)
[[ "$images" == "$expected_images" ]] || fail "Unexpected offline image set"

compose_at "$temp_dir" config >/dev/null

grep -q 'name: match-v2_mysql_data' "$REPO_ROOT/compose.offline.yml" \
  || fail "MySQL volume name must be stable"
pull_policy_count=$(grep -c 'pull_policy: never' "$REPO_ROOT/compose.offline.yml")
[[ "$pull_policy_count" == 7 ]] || fail "Every offline service must disable image pulls"
grep -q 'docker image prune -f' "$OFFLINE_DIR/build-release.sh" \
  || fail "Release build must prune dangling images after verification"
if grep -Eq 'docker compose .*down .*-(v|-v)' "$OFFLINE_DIR/uninstall.sh"; then
  fail "Uninstall must not remove volumes"
fi

printf 'Offline deployment static tests passed.\n'
