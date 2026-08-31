#!/usr/bin/env bash
set -Eeuo pipefail

test_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$test_dir/../.." && pwd)

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

grep -Fq 'MATCH_COMPETITION_PASSWORD_KEY: ${MATCH_COMPETITION_PASSWORD_KEY:?Set MATCH_COMPETITION_PASSWORD_KEY}' "$repo_root/compose.prod.yml" \
  || fail 'Production Compose must require MATCH_COMPETITION_PASSWORD_KEY'
grep -Fq 'XKP_AGENT_MANAGEMENT_URL: ${XKP_AGENT_MANAGEMENT_URL:?Set XKP_AGENT_MANAGEMENT_URL}' "$repo_root/compose.prod.yml" \
  || fail 'Production Compose must require XKP_AGENT_MANAGEMENT_URL'
grep -q '^MATCH_COMPETITION_PASSWORD_KEY=' "$repo_root/.env.prod.example" \
  || fail '.env.prod.example must document MATCH_COMPETITION_PASSWORD_KEY'
grep -q '^XKP_AGENT_MANAGEMENT_URL=' "$repo_root/.env.prod.example" \
  || fail '.env.prod.example must document XKP_AGENT_MANAGEMENT_URL'

quick_install="$repo_root/deploy/quick-install.sh"
[[ -f "$quick_install" ]] || fail 'deploy/quick-install.sh is missing'
bash -n "$quick_install" || fail 'deploy/quick-install.sh has invalid syntax'
grep -q 'id -u' "$quick_install" || fail 'Installer must require root'
grep -q '/etc/os-release' "$quick_install" || fail 'Installer must verify Ubuntu release'
grep -q 'dpkg --print-architecture' "$quick_install" || fail 'Installer must verify amd64'
grep -q 'openssl rand -hex 32' "$quick_install" || fail 'Installer must generate strong secrets'
grep -q 'deploy/host-identity.sh' "$quick_install" || fail 'Installer must initialize host identity'
grep -q 'deploy/agent-ca.sh' "$quick_install" || fail 'Installer must initialize Agent TLS'
grep -q 'compose=(docker compose' "$quick_install" || fail 'Installer must use Docker Compose'
grep -q 'config --quiet' "$quick_install" || fail 'Installer must validate Compose'
grep -q 'curl -fsS' "$quick_install" || fail 'Installer must use strict HTTP health checks'
grep -q '127.0.0.1:19141/health' "$quick_install" || fail 'Installer must check backend health'
if grep -Eq 'docker compose .*down .*-(v|-v)' "$quick_install"; then
  fail 'Installer must not remove Docker volumes'
fi

printf 'Quick deployment static tests passed.\n'
