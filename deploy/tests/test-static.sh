#!/usr/bin/env bash

set -Eeuo pipefail
TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$TEST_DIR/../.." && pwd)

bash "$TEST_DIR/test-registry-static.sh"
bash -n "$REPO_ROOT/deploy/offline/tests/test-static.sh"
printf 'Deployment static test suite passed.\n'
