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

online_install="$repo_root/install-xkp5-online.sh"
[[ -f "$online_install" ]] || fail 'install-xkp5-online.sh is missing'
bash -n "$online_install" || fail 'install-xkp5-online.sh has invalid syntax'
grep -q 'download.docker.com/linux/ubuntu' "$online_install" || fail 'Online installer must use the official Docker repository'
grep -q 'docker-ce' "$online_install" || fail 'Online installer must install Docker Engine'
grep -q 'git clone' "$online_install" || fail 'Online installer must clone the requested repository'
grep -q 'deploy/quick-install.sh' "$online_install" || fail 'Online installer must call the shared core'
grep -q -- '--non-interactive' "$online_install" || fail 'Online installer must support non-interactive mode'

offline_install="$repo_root/install-xkp5-offline.sh"
offline_release="$repo_root/deploy/quick-offline-release.sh"
[[ -f "$offline_install" ]] || fail 'install-xkp5-offline.sh is missing'
[[ -f "$offline_release" ]] || fail 'deploy/quick-offline-release.sh is missing'
bash -n "$offline_install" || fail 'install-xkp5-offline.sh has invalid syntax'
bash -n "$offline_release" || fail 'deploy/quick-offline-release.sh has invalid syntax'
grep -q 'sha256sum -c' "$offline_install" || fail 'Offline installer must verify checksums'
grep -q 'docker load' "$offline_install" || fail 'Offline installer must load packaged images'
grep -q 'deploy/quick-install.sh\|quick-install.sh' "$offline_install" || fail 'Offline installer must call the shared core'
grep -q 'apt-get.*download\|apt-get.*--download-only' "$offline_release" || fail 'Offline release must collect Docker packages'
grep -q 'docker save\|match-v2-images.tar.gz' "$offline_release" || fail 'Offline release must contain Docker images'
grep -q 'deploy/offline/build-release.sh' "$offline_release" || fail 'Offline release must reuse the existing release builder'

quick_scripts=("$quick_install" "$online_install" "$offline_install" "$offline_release")
if grep -Eqi 'license.*private[_ -]?key|XKP_LICENSE_PRIVATE|docker[[:space:]]+(-H|--host)[[:space:]]+tcp://' "${quick_scripts[@]}"; then
  fail 'Quick deployment scripts contain forbidden private-key or Docker TCP configuration'
fi
if grep -Eq 'docker compose .*down .*-(v|-v)|rm -rf[[:space:]]+["'"']?(/|/opt|/root|\$install_dir)["'"']?([[:space:]]|$)' "${quick_scripts[@]}"; then
  fail 'Quick deployment scripts contain a destructive operation'
fi
if grep -Eq 'printf.*(db_password|competition_key|registry_password)|echo.*(db_password|competition_key|registry_password)' "${quick_scripts[@]}"; then
  fail 'Generated secrets must not be printed'
fi

printf 'Quick deployment static tests passed.\n'
