# XKP5 Quick Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 提供一个在线安装脚本，以及一个离线压缩包加安装脚本，使全新 Ubuntu 22.04 amd64 服务器可一次执行完成 XKP5 平台首次部署。

**Architecture:** 在线和离线入口只负责准备代码、Docker 与镜像，最终都调用 `deploy/quick-install.sh` 完成配置、证书、Compose 启动和健康检查。继续复用 `compose.prod.yml`、`deploy/host-identity.sh`、`deploy/agent-ca.sh` 与现有离线发布脚本，不建立第二套运行架构。

**Tech Stack:** POSIX/Bash、Ubuntu 22.04 amd64、Docker Engine、Docker Compose v2、OpenSSL、htpasswd、现有 Docker Compose 服务。

**Spec:** `docs/superpowers/specs/2026-08-31-quick-deployment-design.md`

## Global Constraints

- 目标系统仅支持 Ubuntu 22.04 amd64。
- 所有安装入口必须以 root 运行。
- 默认安装目录 `/opt/xkp5-platform`，配置目录 `/etc/xkp5`。
- 生产授权私钥和许可证不得自动生成或写入服务器。
- 已有容器、volume 或平台数据时默认拒绝首次安装。
- 失败时不得执行 `docker compose down -v`。
- 密码、Registry 凭据和比赛密钥不得输出到终端或日志。
- 快速安装只开放管理平台本身；处理服务器 Agent 不在本计划部署。

---

### Task 1: 修正生产 Compose 必填配置

**Files:**
- Modify: `compose.prod.yml`
- Modify: `.env.prod.example`
- Test: `deploy/tests/test-quick-deployment-static.sh`

**Interfaces:**
- Consumes: Compose 现有 `java`、`vue`、Registry 和 Agent TLS 变量。
- Produces: `MATCH_COMPETITION_PASSWORD_KEY` 与 `XKP_AGENT_MANAGEMENT_URL` 两个强制生产变量。

- [ ] **Step 1: Write the failing static test**

```bash
grep -F 'MATCH_COMPETITION_PASSWORD_KEY: ${MATCH_COMPETITION_PASSWORD_KEY:?Set MATCH_COMPETITION_PASSWORD_KEY}' compose.prod.yml
grep -F 'XKP_AGENT_MANAGEMENT_URL: ${XKP_AGENT_MANAGEMENT_URL:?Set XKP_AGENT_MANAGEMENT_URL}' compose.prod.yml
grep -q '^MATCH_COMPETITION_PASSWORD_KEY=' .env.prod.example
grep -q '^XKP_AGENT_MANAGEMENT_URL=' .env.prod.example
```

- [ ] **Step 2: Run test and confirm failure**

Run: `bash deploy/tests/test-quick-deployment-static.sh`
Expected: FAIL because Compose currently omits the competition password key and gives Agent management URL a hardcoded fallback.

- [ ] **Step 3: Add the minimum production variables**

In the `java.environment` block:

```yaml
MATCH_COMPETITION_PASSWORD_KEY: ${MATCH_COMPETITION_PASSWORD_KEY:?Set MATCH_COMPETITION_PASSWORD_KEY}
XKP_AGENT_MANAGEMENT_URL: ${XKP_AGENT_MANAGEMENT_URL:?Set XKP_AGENT_MANAGEMENT_URL}
```

In `.env.prod.example`:

```dotenv
MATCH_COMPETITION_PASSWORD_KEY=replace-with-32-byte-random-secret
XKP_AGENT_MANAGEMENT_URL=https://your-server-ip:19443
XKP_REGISTRY_AUTH_FILE=/etc/xkp/registry-auth/htpasswd
XKP_AGENT_PACKAGE_DIR=./xkp-agent
```

- [ ] **Step 4: Verify production configuration**

Run: `bash deploy/tests/test-quick-deployment-static.sh`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add compose.prod.yml .env.prod.example deploy/tests/test-quick-deployment-static.sh
git commit -m "fix: require production deployment secrets"
```

### Task 2: 实现共享一键安装核心

**Files:**
- Create: `deploy/quick-install.sh`
- Modify: `deploy/tests/test-quick-deployment-static.sh`

**Interfaces:**
- Consumes CLI: `--source-dir`, `--install-dir`, `--server-ip`, `--license-public-keys`, optional `--configure-ufw`, optional `--restore-snapshot`, optional `--non-interactive`.
- Produces: `/etc/xkp5/xkp5.env`, `/opt/xkp5-platform/.env`, TLS/Registry credentials, running Compose stack, `/var/log/xkp5-install.log`.

- [ ] **Step 1: Extend the failing static contract**

Test exact safety markers:

```bash
grep -q 'id -u' deploy/quick-install.sh
grep -q '/etc/os-release' deploy/quick-install.sh
grep -q 'dpkg --print-architecture' deploy/quick-install.sh
grep -q 'openssl rand -hex 32' deploy/quick-install.sh
grep -q 'deploy/host-identity.sh' deploy/quick-install.sh
grep -q 'deploy/agent-ca.sh' deploy/quick-install.sh
grep -q 'docker compose.*config --quiet' deploy/quick-install.sh
grep -q 'curl -fsS.*19141/health' deploy/quick-install.sh
! grep -q 'down -v' deploy/quick-install.sh
```

- [ ] **Step 2: Run and confirm failure**

Run: `bash deploy/tests/test-quick-deployment-static.sh`
Expected: FAIL because `quick-install.sh` does not exist.

- [ ] **Step 3: Implement argument and safety validation**

The script must:

```bash
[[ $(id -u) -eq 0 ]] || die 'Run as root'
. /etc/os-release
[[ ${ID:-} == ubuntu && ${VERSION_ID:-} == 22.04 ]] || die 'Ubuntu 22.04 is required'
[[ $(dpkg --print-architecture) == amd64 ]] || die 'amd64 is required'
[[ $install_dir == /* && $install_dir != / && $install_dir != /opt ]] || die 'Unsafe install directory'
ip -o addr show | grep -Fq " $server_ip/" || die 'Server IP is not assigned locally'
```

Reject an existing Compose project, `match_prod_mysql` volume, non-empty FastDFS data, Registry data, or target directory unless the explicitly safe snapshot path is active on a new host.

- [ ] **Step 4: Implement secret and certificate preparation**

Generate secrets without printing them:

```bash
db_password=$(openssl rand -hex 32)
competition_key=$(openssl rand -hex 32)
registry_password=$(openssl rand -hex 32)
registry_user=xkp-importer
```

Create `/etc/xkp5/xkp5.env` mode `0600`, call existing host identity and Agent CA scripts, create Registry CA/certificate with SAN `DNS:registry`, create root-owned importer secret files and bcrypt htpasswd.

- [ ] **Step 5: Implement atomic environment file and startup**

Write the existing production variables plus generated values to a temporary file, install it as `$install_dir/.env` mode `0600`, then run:

```bash
docker compose --env-file .env -f compose.prod.yml config --quiet
docker compose --env-file .env -f compose.prod.yml up -d --build
```

Poll `http://127.0.0.1:19141/health` and `http://127.0.0.1:19140/` for at most 180 seconds. On failure print bounded Compose logs, preserve data, and exit nonzero.

- [ ] **Step 6: Verify static and syntax checks**

Run:

```bash
bash -n deploy/quick-install.sh
bash deploy/tests/test-quick-deployment-static.sh
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add deploy/quick-install.sh deploy/tests/test-quick-deployment-static.sh
git commit -m "feat: add shared quick installation core"
```

### Task 3: 实现在线一键安装入口

**Files:**
- Create: `install-xkp5-online.sh`
- Modify: `deploy/tests/test-quick-deployment-static.sh`
- Modify: `README.md`

**Interfaces:**
- Consumes CLI: `--repo`, `--ref`, `--server-ip`, `--license-public-keys`, `--install-dir`, `--non-interactive`, `--configure-ufw`.
- Produces: cloned checkout passed to `deploy/quick-install.sh`.

- [ ] **Step 1: Add the failing online-entry test**

```bash
grep -q 'download.docker.com/linux/ubuntu' install-xkp5-online.sh
grep -q 'docker-ce' install-xkp5-online.sh
grep -q 'git clone' install-xkp5-online.sh
grep -q 'deploy/quick-install.sh' install-xkp5-online.sh
grep -q -- '--non-interactive' install-xkp5-online.sh
```

- [ ] **Step 2: Run and confirm failure**

Run: `bash deploy/tests/test-quick-deployment-static.sh`
Expected: FAIL because the online entry does not exist.

- [ ] **Step 3: Implement the online wrapper**

Use the official Docker apt keyring and repository, install `docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin git curl openssl apache2-utils`, enable Docker, clone the exact requested ref with:

```bash
git clone --branch "$ref" --depth 1 "$repo" "$install_dir"
```

Then execute the shared core with absolute paths and the validated parameters. Non-interactive mode must fail before installing packages if required values are absent.

- [ ] **Step 4: Verify and document**

Run:

```bash
bash -n install-xkp5-online.sh
bash deploy/tests/test-quick-deployment-static.sh
```

Add README command examples for interactive and non-interactive installation.

- [ ] **Step 5: Commit**

```bash
git add install-xkp5-online.sh deploy/tests/test-quick-deployment-static.sh README.md
git commit -m "feat: add one-command online installer"
```

### Task 4: 实现离线快速发布包与安装器

**Files:**
- Create: `install-xkp5-offline.sh`
- Create: `deploy/quick-offline-release.sh`
- Modify: `deploy/tests/test-quick-deployment-static.sh`
- Modify: `deploy/offline/README.md`

**Interfaces:**
- Release consumes: `--version`, optional `--with-snapshot`.
- Release produces: `dist/xkp5-offline-<version>.tar.gz` and root-level `install-xkp5-offline.sh`.
- Installer consumes: archive path plus shared install arguments.

- [ ] **Step 1: Add failing offline contracts**

```bash
grep -q 'sha256sum -c' install-xkp5-offline.sh
grep -q 'docker load' install-xkp5-offline.sh
grep -q 'deploy/quick-install.sh' install-xkp5-offline.sh
grep -q 'apt-get download' deploy/quick-offline-release.sh
grep -q 'docker save' deploy/quick-offline-release.sh
grep -q 'deploy/offline/build-release.sh' deploy/quick-offline-release.sh
```

- [ ] **Step 2: Run and confirm failure**

Run: `bash deploy/tests/test-quick-deployment-static.sh`
Expected: FAIL because offline quick scripts do not exist.

- [ ] **Step 3: Implement the release wrapper**

Require Ubuntu 22.04 amd64 and invoke the existing release builder. Download the Docker Engine/Compose `.deb` dependency closure into `docker-debs/`, save all images referenced by the release Compose plus `xkp5/registry-importer:latest`, copy shared installer files, then generate `SHA256SUMS` and final archive.

- [ ] **Step 4: Implement the offline installer**

Validate archive path and SHA-256 before any package or image change. Install bundled `.deb` files only when Docker/Compose v2 is absent, load image archives, extract application into a temporary directory, and call the shared core. `--restore-snapshot` is forwarded only after proving there is no existing platform data.

- [ ] **Step 5: Verify scripts and docs**

Run:

```bash
bash -n install-xkp5-offline.sh
bash -n deploy/quick-offline-release.sh
bash deploy/tests/test-quick-deployment-static.sh
```

Expected: PASS. Document build, transfer and single-command install in `deploy/offline/README.md`.

- [ ] **Step 6: Commit**

```bash
git add install-xkp5-offline.sh deploy/quick-offline-release.sh deploy/tests/test-quick-deployment-static.sh deploy/offline/README.md
git commit -m "feat: add one-command offline deployment package"
```

### Task 5: 最终验证与交付清单

**Files:**
- Modify: `deploy/tests/test-quick-deployment-static.sh`
- Modify: `README.md`

**Interfaces:**
- Consumes all scripts from Tasks 1-4.
- Produces a reproducible verification report and exact operator commands.

- [ ] **Step 1: Add final static protections**

Reject scripts containing private keys, printed generated secrets, unsafe delete targets, `down -v`, Docker TCP exposure, unbounded `rm -rf`, or non-TLS Registry URLs.

- [ ] **Step 2: Run all non-destructive checks**

```bash
bash deploy/tests/test-quick-deployment-static.sh
bash deploy/offline/tests/test-static.sh
docker compose --env-file .env.test -f compose.prod.yml config --quiet
```

Use a temporary `.env.test` containing fake but syntactically valid public values; do not start services in this static phase.

- [ ] **Step 3: Run disposable Ubuntu smoke tests when Docker is available**

Build the online script in a disposable Ubuntu 22.04 VM/container with mocked systemd boundaries, and build an offline release on a disposable builder. Confirm final archives contain scripts, images, Docker packages and a valid SHA-256 manifest. Do not run first-install logic against the developer's existing volumes.

- [ ] **Step 4: Finish operator documentation**

README must show exactly two primary commands: online installer and offline installer. Move advanced manual Compose instructions under an “advanced/manual deployment” heading rather than deleting them.

- [ ] **Step 5: Commit**

```bash
git add deploy/tests/test-quick-deployment-static.sh README.md
git commit -m "test: verify quick deployment workflows"
```

## Self-review

- Online install, offline package, shared core, production variables, TLS, credentials, existing-data refusal, logging, health checks and documentation all have explicit tasks.
- Scripts reuse the current Compose and offline release paths; no Ansible, Kubernetes or parallel deployment architecture is introduced.
- Production license issuance and Agent installation remain explicitly outside scope.
- Every nontrivial shell path has a runnable static or syntax check before implementation is considered complete.
