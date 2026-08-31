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

printf 'Quick deployment static tests passed.\n'
