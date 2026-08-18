# XKP5.0 处理服务器运维手册

本文供超级管理员和服务器运维人员使用。管理服务器保存处理服务器身份、监控数据和
审计记录；独立 `xkp-agent` 程序安装在 Ubuntu 22.04 amd64 处理服务器上，通过固定
局域网 IP 主动连接管理服务器。不要开放 Docker TCP，也不要把宿主机 Docker socket
映射给管理服务器。

## 当前功能边界

当前阶段提供注册、身份认证、5 秒心跳、CPU/内存/磁盘、RTX 2080/NVML、Docker 状态、
30 天分钟级历史、在线判定和启用/禁用/移除。命令轮询当前固定返回空列表，不执行关机、
容器创建、删除、启动、停止或还原；这些操作会在后续阶段接入同一认证通道。

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

Windows 只作为开发机。生产 Agent 的 systemd、Docker、NVML、证书文件权限和 RTX 2080
数据必须在真实 Ubuntu 处理服务器上完成验收。
