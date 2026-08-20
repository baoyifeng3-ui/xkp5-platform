# XKP5.0 处理服务器运维手册

本文供超级管理员和服务器运维人员使用。管理服务器保存处理服务器身份、监控数据和
审计记录；独立 `xkp-agent` 程序安装在 Ubuntu 22.04 amd64 处理服务器上，通过固定
局域网 IP 主动连接管理服务器。不要开放 Docker TCP，也不要把宿主机 Docker socket
映射给管理服务器。

## 当前功能边界

当前阶段提供注册、身份认证、5 秒心跳、CPU/内存/磁盘、RTX 2080/NVML、Docker 状态、
30 天分钟级历史、在线判定、启用/禁用/移除、Wake-on-LAN、安全关机、双容器训练环境
生命周期和受控 root 终端。所有命令通过持久化队列租约交付；Agent 只接受固定版本的
关机、环境和终端命令，不接受服务端传入的通用 shell 文本。

## 初始化内部 CA

管理服务器必须使用不会变化的局域网 IPv4 地址。首次部署执行：

```bash
sudo ./deploy/agent-ca.sh \
  --management-ip 192.168.1.10 \
  --output /etc/xkp/agent-tls
```

脚本生成长期 CA 与仅包含该固定 IP SAN 的服务器证书。`ca.key` 和 `server.key` 权限为
`0600`；不要复制私钥到处理服务器。处理服务器只需要 `ca.crt`。将生产 `.env` 中
`XKP_AGENT_TLS_DIR` 指向此目录，并确认 Compose 只通过 Nginx 暴露 19443。

续期服务器证书时用相同 IP 和目录再次运行命令，然后重建 Vue/Nginx 容器。脚本保留
已有 CA，只更新服务器叶证书，因此已安装 Agent 不需要更换 CA。若 CA 私钥丢失或轮换，
必须将新 `ca.crt` 分发到每台 Agent 并更新配置；不能通过关闭 TLS 校验绕过。

## 注册处理服务器

1. 在超级管理员“处理服务器”页面点击“生成注册码”，填写可识别的机房或设备标签。
2. 一次性注册码只显示一次，十分钟后失效；通过受控渠道交给现场运维人员。
3. 在处理服务器从独立 `xkp-agent` 仓库构建 Linux amd64 文件，执行：

   ```bash
   sudo ./deploy/install.sh \
     --binary ./dist/xkp-agent-linux-amd64 \
     --management-url https://192.168.1.10:19443 \
     --ca ./ca.crt \
     --registration-token '一次性注册码' \
     --display-name 'GPU Server 01' \
     --workspace /srv/xkp
   ```

4. 安装器完成注册后删除临时注册码，只将 Agent ID 和随机凭据写入
   `/etc/xkp-agent/agent.yml`，文件权限为 `0600`。
5. 执行 `sudo ./deploy/verify.sh`，再确认页面在约 5 秒内显示在线。

同一注册码重复使用必须失败。重新安装同一机器时优先保留原身份文件；确需重新注册，
先在平台移除旧记录并在 Agent 主机明确清除旧身份，然后生成新注册码。

## 日常维护

- “禁用”立即使该 Agent 的 Bearer 凭据返回 `403`，同时保留历史监控和审计。
- “启用”恢复原身份继续上报；无需重新生成注册码。
- “移除”是软删除，用于永久退役，历史数据仍保留。不要把移除当临时停用。
- Agent 最后一次被接受的心跳不超过 15 秒时显示在线；网络恢复后下一次成功心跳自动
  重新上线。
- 平台授权到期不会删除监控历史，但后续容器启动命令会被管理端和 Agent 双重拒绝。
- 普通管理员在授权有效时可以开关机；超级管理员可在授权异常时执行维护开关机。
- WOL 需要 BIOS/UEFI 和网卡启用网络唤醒，并由同一局域网的管理服务器向 UDP 9 端口
  发送广播包。接口成功只表示发包完成，不保证硬件已启动。
- Agent systemd 服务通过安装器部署的最小 polkit 规则执行 `/usr/bin/loginctl poweroff`。关机发起后，管理端以
  心跳停止确认完成；90 秒后仍在线会记录 `SHUTDOWN_NOT_CONFIRMED`。
- 训练环境的创建、启动、停止和还原只使用经过校验的模板、端口和工作目录；还原会重建
  两个容器但保留共享工作区。不要在 Agent 主机手工创建同名容器。
- root 终端仅供超级管理员维护在线且启用的 Agent。浏览器和 Agent 都使用一次性票据，
  连接受绝对时限、空闲时限、消息大小和速率限制约束；终端输入不会写入普通日志。

## 比赛容器与模式迁移

比赛模式启用前，超级管理员应在“比赛容器”中逐台选择处理服务器，并仅为实际需要的
槽位预创建容器。每台服务器固定显示 1 至 4 号槽位，但没有用户绑定的槽位不会在进入
比赛模式时启动，从而避免占用 CPU、内存和 GPU。创建时必须选择图像标注与代码编辑
模板的明确版本；后续恢复继续使用已固定的版本和指纹，不自动跟随新版本。

容器创建完成后检查以下项目：

- 两个容器名称、组件状态和配置指纹都存在；
- 端口映射与工作目录符合该槽位记录；
- 就绪状态为 `READY`；
- 异常时先处理 Agent 在线状态、Docker、镜像、runtime、端口或磁盘问题，再执行恢复。

普通管理员负责用户绑定和平台模式切换，超级管理员不能代替普通管理员切换全局模式或
绑定用户。进入与退出分别要求精确输入 `ENTER COMPETITION` 和 `EXIT COMPETITION`。
迁移严格执行停止后再启动/恢复的阶段屏障。若某台 Agent 变为 `DEGRADED`，在“比赛容器”
中依据失败码修复基础设施，再用迁移编号执行重试；已经成功的步骤不会重复执行。

退出比赛模式时，平台只恢复进入前快照中实际运行的实训环境。不要在迁移期间手工启动
同名容器。管理服务重启后会恢复未完成迁移，但仍应检查迁移详情确认最终状态。

常用诊断命令：

```bash
sudo systemctl status xkp-agent
sudo journalctl -u xkp-agent --since '30 minutes ago'
sudo ./deploy/verify.sh
openssl s_client -connect 192.168.1.10:19443 -CAfile ./ca.crt </dev/null
```

日志不得出现注册码、Authorization 头或完整 Agent 凭据。排障时可以提供时间、Agent
显示名和稳定错误码，不要发送 `/etc/xkp-agent/agent.yml`。

## 故障恢复

| 现象 | 检查与处理 |
| --- | --- |
| 注册返回未登录 | 管理服务器版本过旧；确认 `/agent/v1/**` 已排除网页登录拦截。 |
| `TOKEN_EXPIRED` / `TOKEN_CONSUMED` | 在超级管理员页面重新生成一次性注册码。 |
| TLS 证书错误 | 核对 URL 使用固定 IP、证书 IP SAN、系统时间及 Agent 的 `ca.crt`。 |
| 页面超过 15 秒离线 | 检查 systemd、19443 连通性、DNS/路由和管理服务器时间。 |
| GPU 指标为 `--` | 检查 NVIDIA 驱动、`/dev/nvidia*` 权限和 NVML；其他指标应继续上报。 |
| Docker 指标不可用 | 检查 Docker 服务、Agent 用户的 docker 组和 `/var/run/docker.sock`。 |
| 工作目录磁盘不可用 | 检查 `workspacePath` 是否存在/已挂载；CPU、内存和系统盘仍会保留。 |
| 唤醒包已发送但设备未上线 | 检查 BIOS/UEFI WOL、网卡 WOL、MAC 地址、交换机广播策略和 UDP 9。 |
| 关机一直等待或失败 | 检查命令记录、Agent 日志、systemd 权限；不要通过开放远程 shell 绕过。 |
| 环境操作停在等待或失败 | 检查 Agent 在线状态、授权、模板镜像、Docker runtime、端口和共享工作目录；查看组件级错误码。 |
| 模式迁移显示 `DEGRADED` | 打开迁移详情定位失败 Agent/步骤，修复 Docker、镜像、端口或磁盘后由超级管理员重试；不要重复切换全局模式。 |
| root 终端无法连接 | 确认 Agent 在线且启用、管理端 WSS 证书受信、子协议未被代理删除，并重新申请一次性票据。 |

Windows 只作为开发机。生产 Agent 的 systemd、Docker、NVML、证书文件权限和 RTX 2080
数据必须在真实 Ubuntu 处理服务器上完成验收。
