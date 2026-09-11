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
grep -q -- '--ubuntu-version' "$quick_install" || fail 'Installer must accept the package Ubuntu version'
grep -q 'dpkg --print-architecture' "$quick_install" || fail 'Installer must verify amd64'
grep -q 'openssl rand -hex 32' "$quick_install" || fail 'Installer must generate strong secrets'
grep -q 'competition_key=.*openssl rand -base64 32' "$quick_install" || fail 'Competition key must be a 32-byte Base64 value'
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
focal_recovery="$repo_root/deploy/recover-focal-offline.sh"
[[ -f "$offline_install" ]] || fail 'install-xkp5-offline.sh is missing'
[[ -f "$offline_release" ]] || fail 'deploy/quick-offline-release.sh is missing'
[[ -f "$focal_recovery" ]] || fail 'deploy/recover-focal-offline.sh is missing'
bash -n "$offline_install" || fail 'install-xkp5-offline.sh has invalid syntax'
bash -n "$offline_release" || fail 'deploy/quick-offline-release.sh has invalid syntax'
bash -n "$focal_recovery" || fail 'deploy/recover-focal-offline.sh has invalid syntax'
grep -q 'sha256sum -c' "$focal_recovery" || fail 'Focal recovery must verify its package checksums'
grep -q 'expected_archive_sha256=' "$offline_install" || fail 'Offline installer must pin the complete archive checksum'
grep -q 'detect_server_ip' "$offline_install" || fail 'Offline installer must auto-detect the management IPv4'
grep -q 'detect_archive' "$offline_install" || fail 'Offline installer must auto-detect the adjacent release archive'
grep -q '^default_license_public_keys=' "$offline_install" || fail 'Offline installer must provide the production public key'
if grep -q 'read -r -p' "$offline_install"; then fail 'Offline installer must not require interactive input'; fi
grep -q 'actual_archive_sha256=.*sha256sum' "$offline_install" || fail 'Offline installer must verify the complete archive checksum'
grep -q 'tar -xOf.*release.env' "$offline_install" \
  || fail 'Offline installer must re-extract release metadata from the verified archive'
grep -q 'incomplete-' "$offline_install" || fail 'Offline installer must archive incomplete prior installs'
grep -q "sed -i 's/\\\\r\$//'" "$offline_install" || fail 'Offline installer must normalize packaged shell script line endings'
grep -q "release.env.*sed -i\|sed -i.*release.env" "$offline_install" || fail 'Offline installer must normalize release metadata line endings'
grep -q "name '\*.sh'.*chmod" "$offline_install" || fail 'Offline installer must restore shell script execute permissions'
grep -q 'bash.*quick-install.sh' "$offline_install" || fail 'Offline installer must invoke the shared installer through bash'
grep -q 'MATCH_COMPETITION_PASSWORD_KEY:' "$repo_root/compose.offline.yml" || fail 'Offline Compose must wire the Java competition key'
grep -q 'source_dir/compose.offline.yml' "$offline_install" || fail 'Offline installer must patch the packaged Compose file'
grep -q 'openssl rand -base64 32' "$offline_install" || fail 'Offline installer must generate a valid competition key'
grep -q 'storage_data/data.*storage_data' "$offline_install" || fail 'Offline installer must align FastDFS snapshot verification counts'
grep -q 'MATCH_RELEASE_UBUNTU_VERSION' "$offline_install" || fail 'Offline installer must enforce the package Ubuntu version'
grep -q 'MATCH_RELEASE_DATA_MODE' "$offline_install" || fail 'Offline installer must auto-restore complete snapshot packages'
grep -q 'dpkg -i' "$offline_install" || fail 'Offline installer must stage local packages with dpkg'
grep -q 'fix-broken.*no-download\|no-download.*fix-broken' "$offline_install" || fail 'Offline installer must resolve staged packages without network access'
grep -q 'docker load' "$offline_install" || fail 'Offline installer must load packaged images'
grep -q 'deploy/quick-install.sh\|quick-install.sh' "$offline_install" || fail 'Offline installer must call the shared core'
grep -q 'apt-get.*download\|apt-get.*--download-only' "$offline_release" || fail 'Offline release must collect Docker packages'
grep -q 'apt-get.*install.*--download-only' "$offline_release" || fail 'Offline release Docker package download must use the install subcommand'
grep -q 'Unable to collect all Docker packages' "$offline_release" || fail 'Offline release must retry complete Docker package collection'
grep -q 'kmod libsystemd0' "$offline_release" || fail 'Focal releases must include host baseline systemd dependencies'
[[ $(grep -c 'Unable to refresh the Docker package repository' "$offline_release") -ge 2 ]] || fail 'Offline release must retry both Docker repository refreshes'
[[ $(grep -c 'Dir::Cache::archives=.*docker-debs' "$offline_release") -ge 2 ]] || fail 'Offline release must collect bootstrap package dependencies'
grep -q 'docker save\|match-v2-images.tar.gz' "$offline_release" || fail 'Offline release must contain Docker images'
grep -q 'deploy/offline/build-release.sh' "$offline_release" || fail 'Offline release must reuse the existing release builder'
grep -q -- '--empty' "$offline_release" || fail 'Offline release must support an empty-data package'
grep -q -- '--reuse-images' "$offline_release" || fail 'Offline release must support packaging verified local images'
grep -q -- '--ubuntu-version' "$offline_release" || fail 'Offline release must support Ubuntu 20.04 packages'
grep -q 'MATCH_RELEASE_UBUNTU_VERSION' "$offline_release" || fail 'Offline release must record its Ubuntu version'
grep -q 'MATCH_RELEASE_DATA_MODE=EMPTY' "$offline_release" || fail 'Empty release must declare its data mode'
grep -q 'for attempt in 1 2 3 4 5' "$offline_release" || fail 'Offline release must retry Docker GPG key downloads'
if grep -q -- '--retry-all-errors' "$offline_release"; then fail 'Offline release must support Ubuntu 20.04 curl'; fi
grep -q 'XKP5 empty database release' "$offline_release" || fail 'Empty release must create an empty database snapshot'
grep -q "sed -i 's/\\\\r\$//'" "$offline_release" || fail 'Offline release must normalize shell script line endings'

quick_scripts=("$quick_install" "$online_install" "$offline_install" "$offline_release")
if grep -Eqi 'license.*private[_ -]?key|XKP_LICENSE_PRIVATE|docker[[:space:]]+(-H|--host)[[:space:]]+tcp://' "${quick_scripts[@]}"; then
  fail 'Quick deployment scripts contain forbidden private-key or Docker TCP configuration'
fi
if grep -Eq 'docker compose .*down .*-(v|-v)|rm -rf[[:space:]]+(/|/opt|/root|\$install_dir)([[:space:]]|$)' "${quick_scripts[@]}"; then
  fail 'Quick deployment scripts contain a destructive operation'
fi
if grep -Eh 'printf.*(db_password|competition_key|registry_password)|echo.*(db_password|competition_key|registry_password)' "${quick_scripts[@]}" \
  | grep -Ev '>.*(registry_secrets_dir|env_tmp)'; then
  fail 'Generated secrets must not be printed'
fi

printf 'Quick deployment static tests passed.\n'
