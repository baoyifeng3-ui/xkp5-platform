# XKP5 快速部署设计

日期：2026-08-31  
状态：待书面确认

## 目标

为 Ubuntu 22.04 amd64 提供两种首次安装方式：

1. 完全离线：一个发布压缩包加一个安装脚本。
2. 联网安装：一个安装脚本完成依赖安装、拉取、配置、构建和启动。

两种方式最终都使用现有 `compose.prod.yml`，不建立第二套运行架构。

## 用户入口

### 离线

交付：

```text
xkp5-offline-<version>.tar.gz
install-xkp5-offline.sh
```

执行：

```bash
sudo ./install-xkp5-offline.sh xkp5-offline-<version>.tar.gz
```

### 在线

交付：

```text
install-xkp5-online.sh
```

执行：

```bash
sudo ./install-xkp5-online.sh
```

脚本交互询问固定管理 IP、授权公钥；在线脚本额外询问仓库 URL 和发布分支。所有问题均支持命令行参数，便于自动化。

## 共享安装核心

新增一个包内共享脚本 `deploy/quick-install.sh`，在线和离线入口只负责取得安装材料，然后调用它。共享核心负责：

- 拒绝非 root、非 Ubuntu 22.04、非 amd64。
- 验证管理 IP 属于本机。
- 检查目标目录、容器、数据卷和运行数据，默认拒绝覆盖已有安装。
- 自动生成数据库密码、比赛密码密钥、Registry 用户名和密码。
- 密码写入 root 所有、权限 `0600` 的 `/etc/xkp5/xkp5.env`，终端不输出明文。
- 调用现有 `deploy/host-identity.sh` 生成主机身份。
- 调用现有 `deploy/agent-ca.sh` 生成 Agent CA 和服务端证书。
- 生成只供 Compose 内部 `registry` DNS 名使用的 Registry CA 和 TLS 证书。
- 生成 Registry `htpasswd` 和 importer 的 username/password 文件。
- 修正并写入 `.env`，校验 `docker compose config --quiet`。
- 启动服务，等待 MySQL 健康、Java `/health` 和 Vue 首页。
- 成功后只输出访问地址、初始账号和后续授权导入说明。

脚本使用临时文件和原子重命名写配置。失败时保留日志和数据，不执行 `docker compose down -v`。

## 在线安装

`install-xkp5-online.sh`：

- 安装 `ca-certificates`、`curl`、`git`、`openssl`、`apache2-utils`、Docker Engine 和 Compose v2。
- 克隆用户指定仓库和分支到 `/opt/xkp5-platform`。
- 构建 `xkp5/registry-importer:latest`。
- 构建并启动 Java、Vue 及 Compose 依赖服务。
- 使用官方 Ubuntu/Docker 软件源；软件源不可用时停止并报告，不切换不受信任镜像源。

在线脚本支持：

```text
--repo URL
--ref BRANCH_OR_TAG
--server-ip IP
--license-public-keys VALUE
--install-dir DIR
--non-interactive
```

非交互模式缺少任何必填值时立即失败。

## 离线发布与安装

新增 `deploy/quick-offline-release.sh`，只允许在联网 Ubuntu 22.04 amd64 构建机运行。它复用现有离线发布逻辑，并额外把以下内容打入压缩包：

- 全部 Compose 镜像归档。
- Docker Engine、containerd、Buildx 和 Compose v2 的 Ubuntu 22.04 amd64 `.deb` 包及依赖。
- 应用目录、现有离线校验脚本、共享安装核心。
- 可选的 MySQL、FastDFS、赛卷和评分数据快照。
- SHA-256 清单。

离线安装器先验证 SHA-256；无 Docker 时用包内 `.deb` 安装，有 Docker 时验证版本和 Compose v2。随后加载镜像并调用共享安装核心。

数据快照默认不恢复。只有传入 `--restore-snapshot` 且目标不存在任何平台数据时才恢复。已有数据或 volume 时该参数仍然拒绝执行。

## 必需配置修复

生产 Compose 增加：

```yaml
MATCH_COMPETITION_PASSWORD_KEY: ${MATCH_COMPETITION_PASSWORD_KEY:?Set MATCH_COMPETITION_PASSWORD_KEY}
XKP_AGENT_MANAGEMENT_URL: ${XKP_AGENT_MANAGEMENT_URL:?Set XKP_AGENT_MANAGEMENT_URL}
```

`.env.prod.example` 同步增加上述变量，以及 Registry auth 文件和 Agent 包目录的明确配置。

快速安装不会自动签发平台许可证。用户只提供生产授权公钥；平台启动后仍通过“平台授权”下载申请文件并导入运营方签发的许可证。生产私钥永不进入安装包或服务器。

## 网络与端口

安装完成后提示：

- 用户访问：`19140/tcp`。
- Agent TLS：`19443/tcp`，仅处理服务器网络可访问。
- 后端 `19141`、MySQL `3307`、FastDFS 端口默认仅供服务器管理或受控局域网使用，不建议暴露公网。
- Registry 不发布宿主机写端口。

脚本不自动修改云厂商安全组。若使用 UFW，只在用户显式传入 `--configure-ufw` 时开放 `19140` 和 `19443`。

## 日志、验证与清理

安装日志写入 `/var/log/xkp5-install.log`，敏感值必须脱敏。

自动验证：

```bash
docker compose --env-file .env -f compose.prod.yml config --quiet
docker compose --env-file .env -f compose.prod.yml ps
curl -fsS http://127.0.0.1:19141/health
curl -fsSI http://127.0.0.1:19140/
```

增加 shell 静态测试，覆盖：

- 非 root、错误系统、错误架构和无效 IP。
- 非交互缺参。
- 密钥和密码不输出到日志。
- 已有容器、volume 或数据时拒绝覆盖。
- 离线校验失败时不加载镜像。
- Compose 必填变量完整。
- 安装路径均为明确绝对路径，不允许 `/`、`/opt` 或空值。

## 非目标

- 不自动生成生产授权私钥或许可证。
- 不部署处理服务器 Agent。
- 不自动配置公网域名、HTTPS 证书或云安全组。
- 不实现 Kubernetes、Ansible 或多节点高可用。
- 本次不新增升级脚本；已有 `deploy/server-update.sh` 和离线 `upgrade.sh` 继续使用，后续另行统一。
