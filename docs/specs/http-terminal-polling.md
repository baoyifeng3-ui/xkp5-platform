# HTTP 轮询终端方案

## 目标

为超级管理员提供不依赖浏览器 WebSocket、TLS 主机名或开发服务器代理的远程终端入口。Agent 到管理平台的现有安全命令通道保持不变，浏览器仅通过管理平台的普通 HTTP API 读写终端缓冲区。

## 会话接口

现有 `POST /operations/processing-agents/{agentId}/terminal-sessions` 创建会话后，新增：

- `GET /operations/terminal-sessions/{sessionId}/output?cursor={n}`：返回从 `cursor` 开始的输出、下一游标和会话状态。
- `POST /operations/terminal-sessions/{sessionId}/input`：JSON `{ "data": "..." }`，写入输入队列并返回接受的字节数。
- `POST /operations/terminal-sessions/{sessionId}/resize`：JSON `{ "columns": 120, "rows": 30 }`。
- 现有 `DELETE /operations/terminal-sessions/{sessionId}` 继续关闭会话并清理 Agent PTY。

所有接口只允许超级管理员访问；会话不是 `WAITING_BROWSER` 或 `ACTIVE` 时，读写返回明确的会话状态错误。输出使用内存环形缓冲区，单会话限制 1 MiB，游标落后于缓冲区时返回 `reset=true` 和当前完整尾部，防止浏览器断线后永久卡住。

## Agent 命令

Agent 保持一个终端 PTY。Agent 轮询管理平台取得输入队列，执行写入 PTY；PTY 输出通过 Agent 心跳扩展字段或独立长轮询上报到管理平台。每个输入和输出批次携带会话 ID、单调序列和租约令牌，重复批次幂等处理。

## 前端行为

终端组件打开后每 250ms 拉取输出；用户输入按行或 4 KiB 分批提交；窗口大小变化调用 resize 接口。网络异常时显示“正在重连”，重连成功后使用最后游标继续读取。关闭按钮先关闭会话，再销毁终端组件。

## 验证标准

1. 浏览器不产生任何 WebSocket 请求。
2. 本地 HTTP 页面可看到 `/bin/bash` 的提示符和命令输出。
3. 刷新浏览器后可从最后游标继续读取。
4. 关闭浏览器后 Agent PTY 在超时内释放，下一次会话可创建。
