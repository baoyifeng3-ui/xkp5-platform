#!/usr/bin/env bash

set -Eeuo pipefail

TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SCRIPT="$TEST_DIR/../host-identity.sh"
temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT

mkdir -p "$temp_dir/bin"
printf '%s\n' '#!/usr/bin/env bash' 'printf "%s\n" "root-uuid-fixture"' > "$temp_dir/bin/findmnt"
chmod 0755 "$temp_dir/bin/findmnt"
printf '%s\n' 'machine-fixture' > "$temp_dir/machine-id"
printf '%s\n' 'product-fixture' > "$temp_dir/product-uuid"

PATH="$temp_dir/bin:$PATH" \
XKP_MACHINE_ID_FILE="$temp_dir/machine-id" \
XKP_PRODUCT_UUID_FILE="$temp_dir/product-uuid" \
XKP_HOST_IDENTITY_OUTPUT="$temp_dir/host-identity.json" \
  "$SCRIPT"

expected='{"machineId":"machine-fixture","productUuid":"product-fixture","rootFilesystemUuid":"root-uuid-fixture"}'
actual=$(cat "$temp_dir/host-identity.json")
[[ "$actual" == "$expected" ]] || { printf 'Unexpected identity JSON: %s\n' "$actual" >&2; exit 1; }
[[ $(stat -c '%a' "$temp_dir/host-identity.json") == 444 ]] || { printf 'Identity mode must be 0444\n' >&2; exit 1; }

: > "$temp_dir/product-uuid"
PATH="$temp_dir/bin:$PATH" \
XKP_MACHINE_ID_FILE="$temp_dir/machine-id" \
XKP_PRODUCT_UUID_FILE="$temp_dir/product-uuid" \
XKP_HOST_IDENTITY_OUTPUT="$temp_dir/two-identifiers.json" \
  "$SCRIPT"

printf '%s\n' 'Host identity tests passed.'
