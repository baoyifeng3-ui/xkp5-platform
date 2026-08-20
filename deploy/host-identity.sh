#!/usr/bin/env bash

set -Eeuo pipefail

die() {
  printf 'xkp host identity: %s\n' "$*" >&2
  exit 1
}

(( EUID == 0 )) || die "must be run as root"

output_file=${XKP_HOST_IDENTITY_OUTPUT:-/etc/xkp/host-identity.json}
machine_id_file=${XKP_MACHINE_ID_FILE:-/etc/machine-id}
product_uuid_file=${XKP_PRODUCT_UUID_FILE:-/sys/class/dmi/id/product_uuid}

read_identifier() {
  local file=$1 value=
  if [[ -r "$file" ]]; then
    IFS= read -r value < "$file" || true
    value=${value//$'\r'/}
    value=${value//$'\n'/}
    value=${value//$'\t'/}
    value=${value// /}
  fi
  [[ -z "$value" || "$value" =~ ^[A-Za-z0-9._:/-]+$ ]] \
    || die "identifier contains unsupported characters"
  printf '%s' "$value"
}

machine_id=$(read_identifier "$machine_id_file")
product_uuid=$(read_identifier "$product_uuid_file")
root_uuid=$(findmnt -no UUID / 2>/dev/null | head -n 1 || true)
root_uuid=${root_uuid//$'\r'/}
root_uuid=${root_uuid// /}
[[ -z "$root_uuid" || "$root_uuid" =~ ^[A-Za-z0-9._:/-]+$ ]] \
  || die "root filesystem UUID contains unsupported characters"

identifier_count=0
[[ -n "$machine_id" ]] && identifier_count=$((identifier_count + 1))
[[ -n "$product_uuid" ]] && identifier_count=$((identifier_count + 1))
[[ -n "$root_uuid" ]] && identifier_count=$((identifier_count + 1))
(( identifier_count >= 2 )) || die "at least two host identifiers are required"

output_dir=$(dirname "$output_file")
mkdir -p "$output_dir"
temp_file=$(mktemp "$output_dir/.host-identity.XXXXXX")
trap 'rm -f "$temp_file"' EXIT

{
  printf '{'
  separator=
  if [[ -n "$machine_id" ]]; then
    printf '%s"machineId":"%s"' "$separator" "$machine_id"
    separator=,
  fi
  if [[ -n "$product_uuid" ]]; then
    printf '%s"productUuid":"%s"' "$separator" "$product_uuid"
    separator=,
  fi
  if [[ -n "$root_uuid" ]]; then
    printf '%s"rootFilesystemUuid":"%s"' "$separator" "$root_uuid"
  fi
  printf '}\n'
} > "$temp_file"

chown root:root "$temp_file"
chmod 0444 "$temp_file"
mv -f "$temp_file" "$output_file"
trap - EXIT
printf 'XKP5.0 host identity initialized at %s\n' "$output_file"
