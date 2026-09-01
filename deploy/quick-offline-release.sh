#!/usr/bin/env bash
set -Eeuo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/.." && pwd)
die() { printf '[xkp5-offline-release] ERROR: %s\n' "$*" >&2; exit 1; }
usage() {
  cat <<'EOF'
Usage: quick-offline-release.sh --version VERSION [--env-file FILE] [--output-dir DIR]
Builds xkp5-offline-VERSION.tar.gz and copies install-xkp5-offline.sh beside it.
EOF
}

version=
env_file="$repo_root/.env"
output_dir="$repo_root/dist"
while (($#)); do
  case "$1" in
    --version) version=${2:-}; shift 2 ;;
    --env-file) env_file=${2:-}; shift 2 ;;
    --output-dir) output_dir=${2:-}; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done
[[ $version =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || die 'A valid --version is required'
[[ $(id -u) -eq 0 ]] || die 'Run as root on the Ubuntu release builder'
. /etc/os-release
[[ ${ID:-} == ubuntu && ${VERSION_ID:-} == 22.04 ]] || die 'Ubuntu 22.04 is required'
[[ $(dpkg --print-architecture) == amd64 ]] || die 'amd64 is required'

"$repo_root/deploy/offline/build-release.sh" --version "$version" --env-file "$env_file" --output-dir "$output_dir"

stage_dir="$output_dir/match-v2-$version"
old_archive="$output_dir/match-v2-$version.tar.gz"
final_archive="$output_dir/xkp5-offline-$version.tar.gz"
[[ -d $stage_dir && -f $stage_dir/images/match-v2-images.tar.gz ]] || die 'Base offline release was not created'
[[ ! -e $final_archive ]] || die "Output already exists: $final_archive"

mkdir -p "$stage_dir/docker-debs/partial" "$stage_dir/deploy"
docker run --rm -v "$stage_dir/docker-debs:/out" ubuntu:22.04 bash -Eeuc '
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y ca-certificates curl gnupg
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  . /etc/os-release
  echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $VERSION_CODENAME stable" > /etc/apt/sources.list.d/docker.list
  apt-get update
  apt-get --download-only --reinstall -y -o Dir::Cache::archives=/out \
    docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin \
    ca-certificates curl openssl apache2-utils
'
find "$stage_dir/docker-debs" -maxdepth 1 -name '*.deb' -print -quit | grep -q . || die 'Docker .deb collection is empty'

install -m 0755 "$repo_root/deploy/quick-install.sh" "$stage_dir/deploy/quick-install.sh"
cp -a "$repo_root/xkp-agent" "$stage_dir/xkp-agent"

rm -f "$stage_dir/SHA256SUMS" "$old_archive"
(cd "$stage_dir" && find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS)
(cd "$stage_dir" && sha256sum -c SHA256SUMS >/dev/null)
tar -czf "$final_archive" -C "$output_dir" "match-v2-$version"
tar -tzf "$final_archive" >/dev/null
install -m 0755 "$repo_root/install-xkp5-offline.sh" "$output_dir/install-xkp5-offline.sh"
printf 'Offline quick deployment ready:\n  %s\n  %s\n' "$final_archive" "$output_dir/install-xkp5-offline.sh"
