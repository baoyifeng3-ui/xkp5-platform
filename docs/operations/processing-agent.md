# 处理服务器电源控制运维说明

XKP5.0 管理服务器通过固定局域网管理处理服务器。开机使用 Wake-on-LAN；关机由已认证
Agent 执行固定的 `/usr/bin/loginctl poweroff`。系统不接受任意命令文本，也不开放 Docker TCP
或远程 shell。

## 部署前检查

1. 为管理服务器和处理服务器配置固定 IPv4 地址，确认处于同一广播域。
2. 在处理服务器 BIOS/UEFI 和网卡中启用 Wake-on-LAN，并记录正确的有线网卡 MAC。
3. 允许管理服务器向局域网广播地址的 UDP 9 端口发送数据包。需要时通过
   `xkp.agent.wol.broadcast-address` 和 `xkp.agent.wol.port` 修改目标。
4. 在 Ubuntu 22.04 amd64 上以 systemd 安装 Agent，确认常驻单元使用 `User=root`，
   可以执行 `/usr/bin/loginctl poweroff`，且 Agent 身份文件和待上报命令文件保持 `0600`。
5. Agent 只信任平台内部 CA。管理 URL 必须使用服务器证书 IP SAN 中的固定 IP，禁止
   关闭 TLS 校验。

## 操作语义

普通管理员和超级管理员都可以执行开机、关机。普通管理员需要有效的平台授权；超级
管理员可在授权异常时进行维护。只有超级管理员可以注册、启用、停用或移除设备。

- 开机成功：仅表示 102 字节 WOL 魔术包已经发出，页面应继续等待设备心跳上线。
- 关机提交：在线 Agent 获得持久化命令并确认开始后，页面显示“等待离线确认”。
- Agent 返回关机命令已接受后，命令仍保持运行中；不能用这个响应替代心跳离线确认。
- 关机完成：开始执行至少 15 秒且心跳已经停止时，命令记为
  `SUCCEEDED/OFFLINE_CONFIRMED`。
- 关机失败：90 秒后 Agent 仍在线时，命令记为
  `FAILED/SHUTDOWN_NOT_CONFIRMED`；交付重试耗尽则为
  `DELIVERY_ATTEMPTS_EXHAUSTED`。

重复点击关机会返回同一条未完成命令，不会创建多个并发关机任务。命令租约、Agent
凭据和授权头不得写入支持日志或交给普通管理员。
禁用或移除 Agent 会原子取消其待处理、已租赁和运行中的命令，重新启用后不会执行旧命令。
Agent 在断网或重启后先重传本地结果，再继续长轮询；同一关机命令不会执行两次。

## ROOT 维护终端

ROOT 终端仅供超级管理员维护在线且已启用的 Ubuntu Agent。创建前必须确认
`OPEN_ROOT_TERMINAL`；每台 Agent 同时只允许一个会话。会话连续 10 分钟无终端 I/O
会关闭，最长运行 2 小时。Agent、浏览器任一方断开都会终止 PTY 并释放会话锁；会话
不可重新连接，重新打开必须创建新会话并换取新的单次票据。

生产部署必须同时满足以下条件：

1. 管理 API 使用 HTTPS，两个 `/terminal/v1/agent/`、`/terminal/v1/browser/`
   WebSocket 路径使用 WSS。反向代理必须使用 HTTP/1.1 并透传 `Upgrade`、
   `Connection` 和 `Sec-WebSocket-Protocol`，关闭响应缓冲；访问日志不得记录
   `Authorization` 或 WebSocket 子协议头。
2. `TERMINAL_AGENT_RELAY_URL` 指向外部 WSS Agent 路径，`MATCH_ALLOWED_ORIGINS`
   只列出浏览器实际使用的完整 HTTPS Origin。禁止 `*`、HTTP Origin 和关闭证书校验。
3. Agent 的 systemd 单元必须为 `User=root`、`Group=root`，宿主机必须提供可执行的
   `/bin/bash`、`/dev/ptmx` 和正常 PTY。Agent 只会启动固定的
   `/bin/bash --noprofile --norc`，平台命令不能选择程序、参数或环境变量。
4. 处理服务器防火墙只需允许到管理服务器 HTTPS/WSS 地址的出站连接。禁止为 Agent
   开放入站管理端口，也禁止开放 Docker TCP API。

自动化验收只使用非特权、确定性 echo PTY，不会启动 root shell。上线前另选一台隔离
Ubuntu 主机，移除业务数据和凭据并限制网络，由超级管理员打开一次终端：运行 `id -u`
确认结果为 `0`，检查二进制输入和窗口 resize，断开浏览器后确认 bash 进程组已被回收，
再验证主动关闭和空闲超时都会释放单会话锁。不得在教室正在使用的处理服务器上执行这项
真实 root 验收。

## 双容器实训环境

每套课程环境固定由一个图像标注容器和一个代码编辑容器组成。Agent 在
`environmentWorkspaceRoot` 下为环境创建唯一共享目录，并把同一绝对路径分别挂载到
`/root/data` 和 `/home/student/data`。容器还原只重建容器，不删除共享目录。

处理服务器接入前必须确认：

1. `docker info` 可看到 `sysbox-runc` 和 `nvidia` 两个 Runtime。
2. `nvidia-smi` 能识别 RTX 2080，root systemd 服务可以访问 Docker、GPU 和所需设备。
3. `zy-anno` 与 `zy-contestv2` 镜像已经加载到本机，生产环境不要依赖临时联网拉取。
4. 规划的宿主机端口未被占用，共享数据盘容量和文件权限满足课程要求。
5. 不开放 Docker TCP API；Agent 仅通过本机 Unix Socket 控制由 XKP 标签标识的容器。

## 安全验收

软件测试只能验证魔术包内容、权限、命令状态机和 TLS 通道。上线前必须在真实 Ubuntu
处理服务器执行一次物理验收：设备完全关机后能被唤醒；在线设备能安全关机；错误 MAC
不会影响其他设备；网络中断恢复后 Agent 能重新上线。还需验证两个容器使用同一共享目录、
启动与停止保持成对、还原后测试文件仍存在、代码容器能调用 RTX 2080。

## 本地端到端回归

`deploy/tests/fake-agent-training-environment-flow.ps1` 只允许连接本机地址，并会替换当前
授权、创建随机 Agent、模板和环境记录，因此只能对一次性开发数据库执行。运行前准备一名
普通管理员、一名超级管理员、一个已存在的普通用户和课程，以及与当前平台信息匹配的有效
测试授权文件：

```powershell
$env:XKP_TEST_ALLOW_LICENSE_REPLACE = "YES"
$env:XKP_TEST_LICENSE_FILE = "C:\temp\development.xkplic"
$env:XKP_TEST_ADMIN_USERNAME = "test-admin"
$env:XKP_TEST_ADMIN_PASSWORD = "change-me"
$env:XKP_TEST_SUPER_ADMIN_USERNAME = "test-super-admin"
$env:XKP_TEST_SUPER_ADMIN_PASSWORD = "change-me"
$env:XKP_TEST_USER_ID = "101"
$env:XKP_TEST_COURSE_ID = "201"

.\deploy\tests\fake-agent-training-environment-flow.ps1
```

脚本依次验证双容器创建、重复启动幂等、成对停止、保留共享目录的还原、重复结果上报，
以及单组件失败后的 `DEGRADED` 状态和组件明细。测试通过不代表真实 Docker Runtime、
NVIDIA GPU 或物理开关机已经验收；这些项目仍须在 Ubuntu 处理服务器执行。

终端 relay 验收脚本只允许连接受信任证书的 loopback HTTPS/WSS 一次性容器环境。它会
导入测试授权、注册临时 Agent，并用确定性 echo 适配器验证二进制回显、resize、票据
重放拒绝、第二会话冲突、断连清理和注入的短空闲时钟，同时检查 MySQL 与管理容器日志
中不存在随机 sentinel。示例：

```powershell
$env:XKP_TEST_ALLOW_TERMINAL_RELAY = "YES"
$env:XKP_TEST_TERMINAL_IDLE_SECONDS = "2"
$env:MATCH_TERMINAL_TEST_CLOCK_OFFSET_SECONDS = "602"
$env:XKP_TEST_LICENSE_FILE = "C:\temp\development.xkplic"
$env:XKP_TEST_SUPER_ADMIN_USERNAME = "test-super-admin"
$env:XKP_TEST_SUPER_ADMIN_PASSWORD = "change-me"
$env:XKP_TEST_MYSQL_CONTAINER = "xkp-terminal-mysql"
$env:XKP_TEST_MANAGEMENT_CONTAINER = "xkp-terminal-management"
$env:XKP_TEST_DB_PASSWORD = "change-me"

.\integration\agent\test-terminal-relay-flow.ps1
```

开发配置中的 `MATCH_TERMINAL_TEST_CLOCK_OFFSET_SECONDS` 只在 `DEVELOPMENT` 授权环境生效，
把管理服务 Clock 向前推进 `600 + XKP_TEST_TERMINAL_IDLE_SECONDS` 秒；生产环境始终忽略该值。
脚本仍会通过 MySQL 只读查询检查 sentinel，不能用于修改生产会话数据。

## 管理主页概览口径

普通管理员主页每 15 秒读取一次持久化概览，不会为了刷新页面而同步连接处理服务器。
启用且最近 15 秒内有心跳的 Agent 计为在线，其他启用 Agent 计为离线。用户在最近五
分钟内登录或上报过认证页面活动时计为在线；同一用户的多个有效会话只计一次。

CPU、GPU、GPU 显存、内存和磁盘占用使用每台在线 Agent 最近一次有效指标，再按报告该指标的
Agent 求平均。未报告的指标不参与平均；没有任何有效采样时页面显示“暂无数据”，不会
用零代替缺失值。刷新失败时页面保留上一次成功快照并标记数据暂未更新。

首批告警类别包括：离线 Agent、`DEGRADED` 或 `ERROR` 环境、未完成或失败的环境操作、
未完成或失败的 Agent 命令，以及不可用或临近到期的授权。概览只提供计数，调查和处置
仍在设备、实训环境及授权页面完成。
