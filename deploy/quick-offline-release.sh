#!/usr/bin/env bash
set -Eeuo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/.." && pwd)
die() { printf '[xkp5-offline-release] ERROR: %s\n' "$*" >&2; exit 1; }
usage() {
  cat <<'EOF'
Usage: quick-offline-release.sh --version VERSION [--ubuntu-version VERSION] [--empty] [--reuse-images] [--env-file FILE] [--output-dir DIR]
Builds xkp5-offline-VERSION.tar.gz and copies install-xkp5-offline.sh beside it.
EOF
}

version=
env_file="$repo_root/.env"
output_dir="$repo_root/dist"
empty_release=0
reuse_images=0
ubuntu_version=22.04
while (($#)); do
  case "$1" in
    --version) version=${2:-}; shift 2 ;;
    --env-file) env_file=${2:-}; shift 2 ;;
    --output-dir) output_dir=${2:-}; shift 2 ;;
    --empty) empty_release=1; shift ;;
    --reuse-images) reuse_images=1; shift ;;
    --ubuntu-version) ubuntu_version=${2:-}; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done
[[ $version =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || die 'A valid --version is required'
[[ $ubuntu_version == 20.04 || $ubuntu_version == 22.04 ]] || die 'Only Ubuntu 20.04 and 22.04 are supported'
[[ $(id -u) -eq 0 ]] || die 'Run as root on the Ubuntu release builder'
. /etc/os-release
[[ ${ID:-} == ubuntu && ${VERSION_ID:-} == "$ubuntu_version" ]] || die "Ubuntu $ubuntu_version builder is required"
[[ $(dpkg --print-architecture) == amd64 ]] || die 'amd64 is required'

stage_dir="$output_dir/match-v2-$version"
old_archive="$output_dir/match-v2-$version.tar.gz"
final_archive="$output_dir/xkp5-offline-$version.tar.gz"

build_empty_release() {
  [[ -f $env_file ]] || die "Environment file not found: $env_file"
  for command_name in docker git gzip sha256sum tar; do command -v "$command_name" >/dev/null || die "Required command not found: $command_name"; done
  docker compose version >/dev/null 2>&1 || die 'Docker Compose v2 is required'
  [[ ! -e $stage_dir && ! -e $old_archive ]] || die "Release output already exists for version $version"
  mkdir -p "$stage_dir/images" "$stage_dir/data"

  if ((reuse_images)); then
    for image in match-v2_java:latest match-v2_vue:latest mysql:8.0 delron/fastdfs:latest registry:2 xkp5/registry-importer:latest; do
      docker image inspect "$image" >/dev/null 2>&1 || die "Required local image not found: $image"
    done
  else
    docker build -f "$repo_root/deploy/registry/import-worker.Dockerfile" -t xkp5/registry-importer:latest "$repo_root"
    docker compose --env-file "$env_file" -f "$repo_root/compose.prod.yml" build java vue
    docker pull mysql:8.0
    docker pull delron/fastdfs:latest
    docker pull registry:2
  fi
  docker tag match-v2_java:latest "match-v2_java:$version"
  docker tag match-v2_vue:latest "match-v2_vue:$version"
  docker save "match-v2_java:$version" "match-v2_vue:$version" mysql:8.0 delron/fastdfs:latest registry:2 xkp5/registry-importer:latest \
    | gzip -c > "$stage_dir/images/match-v2-images.tar.gz"

  printf '%s\n' '-- XKP5 empty database release' | gzip -c > "$stage_dir/data/mysql.sql.gz"
  empty_dir=$(mktemp -d)
  trap 'rm -rf "$empty_dir"' RETURN
  for archive in fastdfs-tracker fastdfs-storage dataset scoring registry-data registry-staging; do tar -czf "$stage_dir/data/$archive.tar.gz" -C "$empty_dir" .; done
  rm -rf "$empty_dir"
  trap - RETURN

  created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  git_commit=$(git -C "$repo_root" rev-parse HEAD)
  cat > "$stage_dir/release.env" <<EOF
MATCH_RELEASE_VERSION=$version
MATCH_RELEASE_GIT_COMMIT=$git_commit
MATCH_RELEASE_CREATED_AT=$created_at
MATCH_RELEASE_UBUNTU_VERSION=$ubuntu_version
MATCH_RELEASE_DATA_MODE=EMPTY
MATCH_RELEASE_DATABASE_PROFILE=PORTABLE_SEED
MATCH_DATASET_FILE_COUNT=0
MATCH_SCORING_FILE_COUNT=0
MATCH_FASTDFS_FILE_COUNT=0
MATCH_REGISTRY_FILE_COUNT=0
MATCH_FASTDFS_SAMPLE_PATH=
MATCH_REGISTRY_IMAGE=registry:2
MATCH_REGISTRY_IMPORTER_IMAGE=xkp5/registry-importer:latest
EOF
  printf 'XKP5 empty offline release\nVersion: %s\nCreated UTC: %s\nGit commit: %s\nArchitecture: linux/amd64\n' \
    "$version" "$created_at" "$git_commit" > "$stage_dir/MANIFEST.txt"
  install -m 0644 "$repo_root/compose.offline.yml" "$stage_dir/compose.offline.yml"
  install -m 0644 "$repo_root/deploy/offline/.env.example" "$stage_dir/.env.example"
  install -m 0644 "$repo_root/deploy/offline/README.md" "$stage_dir/README.md"
  install -m 0755 "$repo_root/deploy/host-identity.sh" "$stage_dir/host-identity.sh"
  install -m 0755 "$repo_root/deploy/agent-ca.sh" "$stage_dir/agent-ca.sh"
  install -m 0755 "$repo_root/deploy/code-server-ca.sh" "$stage_dir/code-server-ca.sh"
  install -m 0644 "$repo_root/deploy/offline/sanitize-portable-seed.sql" "$stage_dir/sanitize-portable-seed.sql"
  for script_name in _common.sh install.sh upgrade.sh reset-from-snapshot.sh verify.sh uninstall.sh; do
    install -m 0755 "$repo_root/deploy/offline/$script_name" "$stage_dir/$script_name"
  done
  (cd "$stage_dir" && find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS)
}

if ((empty_release)); then
  build_empty_release
else
  "$repo_root/deploy/offline/build-release.sh" --version "$version" --env-file "$env_file" --output-dir "$output_dir"
fi

[[ -d $stage_dir && -f $stage_dir/images/match-v2-images.tar.gz ]] || die 'Base offline release was not created'
[[ ! -e $final_archive ]] || die "Output already exists: $final_archive"

mkdir -p "$stage_dir/docker-debs/partial" "$stage_dir/deploy"
export DEBIAN_FRONTEND=noninteractive
for attempt in 1 2 3 4 5; do
  apt-get -o Acquire::Retries=5 -o Acquire::https::Timeout=60 update && break
  ((attempt < 5)) || die 'Unable to refresh the Docker package repository'
  sleep $((attempt * 2))
done
apt-get -o Acquire::Retries=5 -o Acquire::https::Timeout=60 install -y -o Dir::Cache::archives="$stage_dir/docker-debs" ca-certificates curl gnupg
install -m 0755 -d /etc/apt/keyrings
for attempt in 1 2 3 4 5; do
  curl -fsSL --connect-timeout 20 https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc && break
  ((attempt < 5)) || die 'Unable to download the Docker repository key'
  sleep $((attempt * 2))
done
chmod a+r /etc/apt/keyrings/docker.asc
. /etc/os-release
echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $VERSION_CODENAME stable" > /etc/apt/sources.list.d/docker.list
for attempt in 1 2 3 4 5; do
  apt-get -o Acquire::Retries=5 -o Acquire::https::Timeout=60 update && break
  ((attempt < 5)) || die 'Unable to refresh the Docker package repository'
  sleep $((attempt * 2))
done
for attempt in 1 2 3 4 5; do
  apt-get -o Acquire::Retries=5 -o Acquire::https::Timeout=60 install --download-only --reinstall -y -o Dir::Cache::archives="$stage_dir/docker-debs" \
    docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin \
    ca-certificates curl openssl apache2-utils kmod libsystemd0 && break
  ((attempt < 5)) || die 'Unable to collect all Docker packages'
  sleep $((attempt * 2))
done
find "$stage_dir/docker-debs" -maxdepth 1 -name '*.deb' -print -quit | grep -q . || die 'Docker .deb collection is empty'

install -m 0755 "$repo_root/deploy/quick-install.sh" "$stage_dir/deploy/quick-install.sh"
agent_root=$(cd "$repo_root/../xkp-agent" 2>/dev/null && pwd || true)
[[ -d "$agent_root/dist" && -d "$agent_root/deploy" ]] || die 'Go Agent repository with dist/ and deploy/ is required beside xkp5-platform'
rm -rf "$stage_dir/xkp-agent"
mkdir -p "$stage_dir/xkp-agent/dist" "$stage_dir/xkp-agent/deploy"
install -m 0755 "$agent_root/dist/xkp-agent-linux-amd64" "$stage_dir/xkp-agent/dist/xkp-agent-linux-amd64"
for agent_file in install.sh one-click-install.sh build-linux.sh verify.sh xkp-agent.service xkp-agent-power.rules; do
  [[ -f "$agent_root/deploy/$agent_file" ]] || die "Go Agent deployment file is missing: $agent_file"
  mode=0644; [[ $agent_file == *.sh ]] && mode=0755
  install -m "$mode" "$agent_root/deploy/$agent_file" "$stage_dir/xkp-agent/deploy/$agent_file"
done
find "$stage_dir" -type f -name '*.sh' -exec sed -i 's/\r$//' {} +

rm -f "$stage_dir/SHA256SUMS" "$old_archive"
(cd "$stage_dir" && find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS)
(cd "$stage_dir" && sha256sum -c SHA256SUMS >/dev/null)
tar -czf "$final_archive" -C "$output_dir" "match-v2-$version"
tar -tzf "$final_archive" >/dev/null
archive_sha256=$(sha256sum "$final_archive" | awk '{print $1}')
sed "s/^expected_archive_sha256=.*/expected_archive_sha256=$archive_sha256/" \
  "$repo_root/install-xkp5-offline.sh" > "$output_dir/install-xkp5-offline.sh"
chmod 0755 "$output_dir/install-xkp5-offline.sh"
printf 'Offline quick deployment ready:\n  %s\n  %s\n' "$final_archive" "$output_dir/install-xkp5-offline.sh"
