#!/usr/bin/env bash
set -Eeuo pipefail
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
script="$repo_root/update-xkp5-online.sh"
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

[[ -f $script ]] || fail 'update-xkp5-online.sh is missing'
bash -n "$script" || fail 'Online update script has invalid syntax'
"$script" --help >/dev/null || fail 'Online update help failed'
dry_run=$("$script" --dry-run)
grep -q 'Back up MySQL' <<<"$dry_run" || fail 'Online update must back up MySQL'
grep -q 'Build Java, Vue, and Registry Importer' <<<"$dry_run" || fail 'Online update must build all application images'
grep -q 'Health-check Java and Vue' <<<"$dry_run" || fail 'Online update must health-check the application'
grep -q 'git clone.*--branch.*--depth 1' "$script" || fail 'Online update must clone one requested ref'
grep -q 'mysqldump' "$script" || fail 'Online update must create a database backup'
grep -q 'compose.offline.yml' "$script" || fail 'Online update must update offline installations'
grep -q 'docker tag' "$script" || fail 'Online update must retain versioned application images'
grep -q 'registry-importer' "$script" || fail 'Online update must update Registry Importer'
grep -q 'rollback' "$script" || fail 'Online update must restore old application metadata on failure'
if grep -Eq 'docker compose .*down .*-(v|-v)|docker volume rm|git (reset|clean)|rm -rf[[:space:]]+(/|/opt|/root|\$install_dir)' "$script"; then
  fail 'Online update contains a destructive operation'
fi
printf 'Online update static tests passed.\n'
