#!/usr/bin/env bash

set -Eeuo pipefail

TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$TEST_DIR/../.." && pwd)
SCRIPT="$REPO_ROOT/deploy/server-update.sh"

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

bash -n "$SCRIPT" || fail "Shell syntax check failed"
"$SCRIPT" --help >/dev/null || fail "Help command failed"
dry_run_output=$("$SCRIPT" --dry-run)

grep -q 'Back up MySQL' <<<"$dry_run_output" || fail "Dry run must show the backup stage"
grep -q 'Fast-forward pull' <<<"$dry_run_output" || fail "Dry run must show the Git stage"
grep -q 'Build services: java vue' <<<"$dry_run_output" || fail "Dry run must limit builds to application services"
grep -q 'Recreate and health-check: java' <<<"$dry_run_output" || fail "Dry run must update Java first"
grep -q 'Recreate and health-check: vue' <<<"$dry_run_output" || fail "Dry run must update Vue after Java"

grep -q 'git pull --ff-only origin master' "$SCRIPT" || fail "Git update must be fast-forward only"
grep -q 'compose build java vue' "$SCRIPT" || fail "Only Java and Vue may be built"
grep -q 'force-recreate java' "$SCRIPT" || fail "Java must be recreated explicitly"
grep -q 'force-recreate vue' "$SCRIPT" || fail "Vue must be recreated explicitly"
grep -q 'mysqldump' "$SCRIPT" || fail "Database backup is required"

if grep -Eq 'compose (.* )?down|docker volume rm|git (reset|clean)|rm -rf' "$SCRIPT"; then
  fail "Script contains a destructive command"
fi

grep -q '^server-update:$' "$REPO_ROOT/Makefile" || fail "Makefile server-update target is missing"
grep -q '^server-update-test:$' "$REPO_ROOT/Makefile" || fail "Makefile server-update-test target is missing"
grep -q '^\t\./deploy/server-update.sh$' "$REPO_ROOT/Makefile" || fail "Makefile must delegate to the update script"

printf 'Server update static tests passed.\n'
