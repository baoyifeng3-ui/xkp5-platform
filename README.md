# XKP5.0平台

本仓库是 XKP5.0 管理服务器的独立代码库，复用原竞赛平台的 Vue 2 与 Spring
Boot 基线，但后续提交、版本和部署均与原 `competition` 仓库分离。离线授权已经
接入；处理服务器监控已通过独立的 Ubuntu Agent 接入，Docker 容器编排、远程电源
控制和 CUDA MPS 调度将在后续阶段沿用同一条认证命令通道实现。

当前第一阶段已经提供三类独立工作区：

- 超级管理员：系统运维和普通管理员账号维护。
- 普通管理员：主页、课程、资源、实训、竞赛、用户和设备管理。
- 普通用户：课程平台、资源中心和实训环境。

竞赛管理继续复用原平台的比赛控制、赛程赛规、试卷题目、试卷评分、比赛账号、
比赛设备和平台设置；“比赛预览”以参赛用户视角只读显示原有四个比赛页面。

## 仓库边界

开发目录为 `xkp5-platform`。不要在原 `competition` 仓库提交 XKP5.0 的改动，
也不要把原仓库的 `.git`、`.env`、数据库目录、`node_modules` 或构建产物复制进来。

## 本地端口

| 服务 | 地址 |
| --- | --- |
| 前端 | http://localhost:19140 |
| 后端 | http://localhost:19141 |
| 健康检查 | http://localhost:19141/health |
| MySQL | localhost:3307 |
| FastDFS | 22122 / 23000 / 8888 |
| 处理 Agent TLS | https://管理服务器固定IP:19443 |

## 私有镜像仓库

超级管理员可在“镜像仓库”上传可续传的 Docker `save` 归档、审批并异步导入，按不可变
摘要独立发布图像标注或代码编辑镜像，再逐台推送到 Agent。普通管理员仅能查看目录、
摘要和推送状态。默认推送策略会在健康检查保护下更新容器，也可选择只缓存镜像；失败时
保留上一可用版本供显式回滚。

生产启用前必须配置 Registry 内部 CA、持久化 staging/import 目录和部署密钥。单文件
上限默认 20 GiB，总暂存容量默认 100 GiB，Registry 历史由超级管理员确认无引用后手工
清理。详细部署和轮转步骤见 `deploy/registry/README.md` 与
`docs/agents/operator-guide.md`。在真实 TLS Registry、导入器、fake Agent 和专用测试
账号齐备前，自动合同测试不代表真实端到端推送已验收。

## 第一次配置

环境要求：JDK 17、Maven 3.9、Node.js 16、Docker Desktop 与 Docker Compose。
Windows 开发可使用 PowerShell；生产服务器目标为 Ubuntu 22.04 amd64。

1. 创建本地配置并填写你自己的 MySQL 密码：

   ```bash
   cp .env.local.example .env.local
   ```

   本地开发使用 MySQL root 用户，因此 `.env.local` 中
   `MATCH_DB_PASSWORD` 和 `MYSQL_ROOT_PASSWORD` 必须相同。如果复用现有
   `match_mysql_data` 数据卷，请填写该数据卷当前的 root 密码。

2. 创建 Python 3.11 环境：

   ```bash
   conda env create -f python/environment.yml
   conda activate match-local
   which python
   ```

   将 `which python` 输出的绝对路径填到 `.env.local` 的
   `MATCH_PYTHON_EXECUTABLE`。

3. 如果旧的完整 Docker 项目还在运行，先停止它以释放 3307、8888 等端口：

   ```bash
   docker compose -f docker-compose154.yml down
   ```

   该命令保留数据库卷。不要追加 `-v`。

## 日常本地启动

只启动 MySQL 和 FastDFS：

```bash
make infra-up
make infra-status
```

### 后端（IDEA）

打开 `java/match-mgr` Maven 模块，运行 `com.match.Application`。Run
Configuration 设置：

- JDK：17
- Working directory：项目根目录
- Active profiles：`local`
- VM options：`--add-opens=java.base/java.lang=ALL-UNNAMED`
- Environment variables：至少设置
  `MATCH_DB_PASSWORD=你的密码;MATCH_PYTHON_EXECUTABLE=Conda Python绝对路径`

也可以在终端启动：

```bash
SPRING_PROFILES_ACTIVE=local \
MATCH_DB_PASSWORD='你的密码' \
MATCH_PYTHON_EXECUTABLE='/Conda环境中的绝对路径/python' \
mvn -f java/match-mgr/pom.xml spring-boot:run
```

后端启动时 Flyway 会自动升级数据库结构，并保留当前比赛数据。

### 前端

推荐 Node.js 16（项目已提供 `.nvmrc`）：

```bash
cd vue
nvm use
npm install
npm run serve
```

浏览器打开 http://localhost:19140。前端统一通过 `/api`、`/dataset` 和
`/files` 访问后端及资源，不需要修改 IP。

超级管理员初始账号为 `admin`，初始密码为 `admin`。第一次登录必须修改密码。
超级管理员登录后在“管理员账号”中创建日常使用的普通管理员；新管理员首次登录同样
必须修改初始密码。

### Windows Docker 整体启动

Docker Hub 网络可用时，可直接构建并启动完整管理服务器：

```powershell
Copy-Item .env.prod.example .env
docker compose --env-file .env -f compose.prod.yml up -d --build
docker compose --env-file .env -f compose.prod.yml ps
```

访问 `http://localhost:19140`。如果构建提示无法连接 `auth.docker.io:443`，这是
Docker Desktop 到 Docker Hub 的网络或代理问题，不是项目代码或数据库错误。

## 停止

前端和后端在 IDEA/终端中按停止按钮或 `Ctrl+C`。停止基础设施但保留数据：

```bash
make infra-down
```

不要使用 `docker compose down -v`，否则会删除数据库卷。

## 数据库备份与恢复

```bash
make db-backup
make db-restore FILE=backups/match-20260731-120000.sql
```

备份保存在 `backups/`。恢复前应先再做一次备份。

## 赛卷资源

现有赛卷文件放在：

```text
download/dataset/A/
download/dataset/B/
python/a/annotations.xml
python/b/annotations.xml
```

管理员在管理页选择当前试卷；普通用户登录后直接进入管理员选择的卷。管理端可通过
“新增试卷”登记单字母卷名，系统会自动创建 `python/{卷名小写}/annotations.xml`
空模板和 `download/dataset/{卷名大写}/` 空目录。运维人员补入有效标注和数据文件、
管理员新增题目后，该试卷才可启用。

## Ubuntu 22.04 部署

服务器安装 Docker 与 Compose，创建生产配置，填写强密码和注册码工具生成的
Ed25519 公钥，然后初始化主机身份：

```bash
cp .env.prod.example .env
sudo ./deploy/host-identity.sh
make prod-up
```

生产环境缺少 `XKP_LICENSE_PUBLIC_KEYS` 或主机身份文件时会拒绝启动。平台不保存
任何授权私钥。平台首次运行后，普通管理员在“平台授权”下载平台信息，将文件交给
授权运营方，收到注册码文件后在同一页面导入。详细流程见
`docs/licensing/operator-guide.md` 和 `docs/licensing/administrator-guide.md`。

处理服务器接入前，由超级管理员生成内部 CA 和服务器证书，再在“处理服务器”页面
生成十分钟有效的一次性注册码。Ubuntu Agent 只主动连接管理服务器，不对局域网暴露
Docker TCP。详细步骤见 `docs/agents/operator-guide.md`；普通管理员查看在线状态、资源
占用和告警的方法见 `docs/agents/administrator-guide.md`。

比赛环境按每台处理服务器固定四个槽位管理。超级管理员先创建并验证所需槽位的双容器，
普通管理员再绑定用户；未绑定槽位在比赛模式切换时不会启动。普通用户在比赛模式与实训
模式使用完全隔离的界面，模式切换后必须重新登录。未绑定用户仍可答题，但不会获得实操
环境链接。迁移失败会保留为可诊断、可重试的 `DEGRADED` 状态，退出时只恢复进入前实际
运行的实训环境。完整操作见上述两份 Agent 运维与管理员手册。

具备真实 MySQL、受信 TLS、在线 fake Agent 和专用测试账号时，可用环境变量运行比赛模式
冒烟流程：

```powershell
powershell -NoProfile -File scripts/test-competition-mode-flow-contract.ps1
powershell -NoProfile -File scripts/test-competition-mode-flow.ps1
```

脚本不会内置账号、密码或票据，也不会创建或删除已有容器与绑定；缺少测试夹具时只运行
合同和语法检查，不能据此声称真实端到端通过。

访问 `http://服务器局域网IP:19140`。后端端口为 19141，MySQL 宿主端口为
3307。生产前应将本地备份 SQL 恢复到服务器，而不是复制 MySQL 数据目录。

停止生产服务并保留数据：

```bash
make prod-down
```

### 服务器日常更新

本地提交并推送到 Gitee 后，在服务器项目目录执行：

```bash
./deploy/server-update.sh
```

脚本会先将数据库备份到项目同级的 `backups/`，再快进拉取 `master`、构建并
依次更新 Java 和 Vue。MySQL、FastDFS、数据库卷、上传文件和 `.env` 不会被
重建或删除。也可以使用等价入口：

```bash
make server-update
```

首次使用或修改脚本后可先检查执行顺序：

```bash
./deploy/server-update.sh --dry-run
make server-update-test
```

### 完全离线部署

在联网的 212 源服务器构建带完整数据快照的版本化发布包：

```bash
make offline-test
make offline-release VERSION=20260807-01
```

打包过程中会短暂停止服务以取得一致的 MySQL、FastDFS、赛卷和评分资源快照，完成后自动恢复服务。发布包生成在 `dist/match-v2-20260807-01.tar.gz`，并在校验成功后清理 `<none>` 悬空镜像。

将发布包复制到完全离线的 Ubuntu x86_64/amd64 服务器，解压并创建该服务器独立的配置：

```bash
tar -xzf match-v2-20260807-01.tar.gz
cd match-v2-20260807-01
cp .env.example /root/match-v2.env
vi /root/match-v2.env
sudo ./install.sh --install-dir /opt/match-v2 --env-file /root/match-v2.env
```

对已有服务器只更新应用镜像并保留数据：

```bash
sudo ./upgrade.sh --install-dir /opt/match-v2
```

完整操作和快照重置说明见发布包内的 `README.md`。

训练服务器不由本项目部署。管理员可持续新增训练服务器；每台服务器配置 4 个
节点，每个节点提供 4 个槽位。槽位会根据基础端口自动生成 VSCode、CVAT 和
T100 地址，例如第 1 至 4 个槽位使用 9091-9094、8081-8084、5001-5004。
