# XKP5.0 项目交接文档

更新时间：2026-08-24

本文面向接手 XKP5.0 平台开发、部署和问题排查的工程师。先阅读本文，再阅读 `README.md`、`docs/agents/operator-guide.md`、`docs/agents/administrator-guide.md` 和相关设计文档。

## 1. 项目边界

平台仓库：`xkp5-platform`

处理服务器 Agent 仓库：`xkp-agent`

不要把两个仓库混为一个项目，也不要在旧 `competition` 仓库继续提交 XKP5.0 改动。平台是 Vue 2 + Spring Boot + MySQL + FastDFS；Agent 是 Go Linux amd64 程序。

## 2. 当前网络拓扑

| 角色 | 地址/端口 | 说明 |
| --- | --- | --- |
| 管理平台开发前端 | `172.16.33.182:19244` | 浏览器访问，Vue dev server |
| 管理平台开发后端 | `172.16.33.182:19243` | Spring Boot，健康检查 `/health` |
| Agent HTTPS 网关 | `172.16.33.182:19443` | Agent 注册、心跳和 WSS 入口 |
| 新处理服务器 | `172.16.33.215` | 当前应使用的处理服务器 |
| 旧处理服务器 | `172.16.33.201` | 已废弃，禁止继续使用 |
| 用户电脑 | `172.16.33.182` | 同时是本地管理平台开发机 |

本地当前运行容器通常包括：`xkp5-current-java`、`xkp5-agent-gateway`、MySQL、FastDFS tracker/storage。前端开发服务不是 Docker 容器，需在 `vue` 目录启动。

## 3. 启动与检查

启动前端：

```powershell
cd xkp5-platform/vue
$env:MATCH_FRONTEND_PORT='19244'
$env:MATCH_BACKEND_URL='http://localhost:19243'
npm run serve
```

检查服务：

```powershell
Invoke-WebRequest http://127.0.0.1:19244
Invoke-WebRequest http://127.0.0.1:19243/health
Invoke-WebRequest https://127.0.0.1:19443/health -SkipCertificateCheck
docker ps
```

前端地址：`http://localhost:19244`。

如果只有前端关闭，直接重新执行上面的 `npm run serve`。后端容器通常使用开发镜像和挂载的 Agent 目录启动；不要随意删除数据库卷。

## 4. 账号与授权

开发环境曾使用以下账号进行验证：超级管理员 `admin123/admin123`，普通管理员 `admin/admin`，普通用户 `test1/test1`。这些是开发测试凭据，不得写入生产配置、文档外传或提交到代码库。生产账号必须重新创建并修改密码。

平台授权和 Agent 注册是两套机制：

- 平台授权决定管理平台本身是否可用。
- Agent 一次性注册凭据只用于某一台处理服务器首次注册，通常十分钟有效且只能使用一次。
- 管理平台 CA 私钥不能放进 Agent 包；包内只允许放 CA 公钥证书。

## 5. 已完成的主要功能

### 管理端与 UI

- 超级管理员、普通管理员、普通用户三套导航和权限隔离。
- 管理首页、课程、资源、用户、竞赛、实训、设备和平台设置已按参考模板完成统一 shell 和文件卡片风格。
- 课程支持封面、课程类型、资源文件、在线学习进度和实训入口。
- 资源和课程文件支持发送到容器，普通用户不能直接下载到本地。
- 比赛模式和实训模式普通用户界面隔离；切换模式后重新登录。
- 比赛预览按普通用户视角展示，不显示管理员控制内容。

### Agent 与处理服务器

- Agent 注册、心跳、在线状态、资源指标、启停、移除和命令记录。
- 添加服务器页面输入 IP 时只检测网络连通性；不再要求 Agent 先注册才能下载包。
- 网络不可达时禁止下载；网络可达且信息完整时允许下载。
- 下载完成后可用“搜索已注册 Agent”刷新平台列表。
- 部署包格式为 `.tar.gz`，包含 Agent、CA、安装脚本、systemd service、polkit 规则和说明。
- 安装入口会恢复权限并兼容 Ubuntu 20.04 的 `policykit-1`；只有仓库真正存在 `polkitd` 时才安装它。
- Agent GPU 采集已从 NVML 改为固定参数调用 `nvidia-smi`，目标是支持 `CGO_ENABLED=0` 静态编译。

### 镜像、容器和课程实训

- 私有镜像仓库数据模型、上传、审批、发布、部署和历史版本保留机制已实现大部分后端与合同测试。
- 比赛/实训容器按槽位和用户绑定关系管理；未绑定槽位不应在比赛模式切换时启动。
- 课程、资源和实训环境接口已经接入 Agent 命令通道，但真实处理服务器端到端验收仍需要专用测试主机。

## 6. Agent 正确构建与部署

Agent 生产二进制必须静态编译，避免 Ubuntu 20.04 出现 `GLIBC_2.32/2.34 not found`：

```bash
cd xkp-agent
CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -o dist/xkp-agent-linux-amd64 ./cmd/xkp-agent
```

当前 Agent GPU 采集使用 `nvidia-smi`，不再需要 `go-nvml` 才能编译静态版本。生成后必须检查：

```bash
ldd dist/xkp-agent-linux-amd64
```

期望输出为 `not a dynamic executable`。如果出现 GLIBC 版本依赖，不得上传到平台部署包目录。

部署服务器：

```bash
tar -xzf XT-01-xkp-agent.tar.gz
cd XT-01-xkp-agent
sudo bash install-server.sh
sudo systemctl status xkp-agent --no-pager
sudo ./deploy/verify.sh
```

Agent 只主动访问管理平台，不应开放 Docker TCP 或额外 Agent 入站端口。管理 URL 必须使用管理平台固定 IP 和 `19443`，不能使用 `localhost`。

## 7. 当前已知问题与风险

1. **静态 Agent 二进制必须替换到平台挂载目录**：`xkp-agent/dist/xkp-agent-linux-amd64` 被 `.gitignore` 忽略，二进制不一定包含在 Git 提交中。接手人每次切换机器或清理目录后都必须重新构建并核对 `ldd`。
2. **真实 Agent 端到端尚未完全验收**：需要 Ubuntu 20.04/22.04、Docker、sysbox-runc、NVIDIA runtime、GPU、polkit、受信 CA 和可达的 `172.16.33.182:19443`。
3. **Docker Desktop 代理可能阻止构建**：当前环境曾配置 `127.0.0.1:7890`，代理未启动时 Docker Hub 和 Maven/Go 下载会失败。先检查 Docker Desktop proxy，再判断是否是代码问题。
4. **旧部署包不可复用**：旧 ZIP 包、旧动态 Agent 包以及包含 `policykit-1 polkitd` 的包都必须删除，不能重复执行。
5. **工作区有用户未提交文件**：`java/match-mgr/src/main/java/com/match/course/web/CourseDeliveryController.java` 当前存在未提交改动。接手人不得直接 reset 或 checkout 覆盖它，应先确认来源并单独处理。
6. **当前开发前端是手动进程**：关闭终端或机器重启后 `19244` 会停止，需要重新运行 `npm run serve`。
7. **生产镜像仓库仍需要真实 TLS Registry 和专用测试账号验收**，合同测试通过不等于真实推送链路已验收。

## 8. 测试命令

前端 Agent/UI 相关：

```powershell
cd xkp5-platform/vue
npm run test:agents
npm run test:platform-ui
npm run test:course-platform
npm run build
```

Agent Go 测试：

```bash
cd xkp-agent
GOPROXY=https://goproxy.cn,direct go test ./internal/collect ./internal/client ./internal/container ./internal/protocol ./internal/runtime
```

重点检查 Agent 静态编译、包文件清单、服务健康检查和真实注册心跳，不要只看前端“下载成功”提示。

## 9. 接手后的推荐顺序

1. 保留当前数据库和用户未提交文件，先确认 `git status`。
2. 启动 `19243`、`19443`、`19244`，确认三个健康检查均为 200。
3. 重新构建静态 Agent，并执行 `ldd` 检查。
4. 从“处理服务器”页面用 `172.16.33.215` 下载新包，检查 tar 清单中包含 service 和 polkit 文件。
5. 在隔离 Ubuntu 测试主机安装 Agent，确认 systemd、心跳和平台“搜索已注册 Agent”。
6. 再做课程资源下发、实训容器、比赛模式和镜像推送的真实端到端验收。
7. 最后再处理 UI 细节和生产部署自动化，不要在 Agent 基础链路未验收前继续扩展容器功能。

## 10. 重要操作禁令

- 不要使用 `git reset --hard`、`git checkout --` 覆盖未确认的用户改动。
- 不要删除 MySQL/FastDFS 数据卷。
- 不要使用废弃服务器 `172.16.33.201`。
- 不要把注册凭据、CA 私钥、生产密码提交到 Git。
- 不要把动态 glibc 版本过高的 Agent 重新打包。
- 不要把真实生产服务器当作首次测试机执行 root 终端、关机或容器清理操作。

