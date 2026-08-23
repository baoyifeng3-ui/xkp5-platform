# Agent tar.gz 部署包设计

## 目标

将处理服务器 Agent 下载包从 ZIP 改为 Linux 原生的 tar.gz，避免工程师在 Ubuntu 上解压后丢失目录结构和可执行权限。

## 方案

- 后端继续使用一次性注册凭据生成包，但改用 `GZIPOutputStream` 和 `TarArchiveOutputStream` 输出 `备注名-xkp-agent.tar.gz`。
- 包内保留 Agent 二进制、部署脚本、CA 证书、带参数的 `install-server.sh` 和安装说明。
- `install-server.sh` 在调用底层安装脚本前恢复二进制及脚本权限；底层安装脚本也对二进制执行权限做防御性修复。
- 前端请求仍使用二进制响应，只调整下载文件名和界面提示。

## 验证

- 前端 Agent 页面契约测试通过。
- Java 后端编译通过。
- 生成的包使用 `tar -tzf` 检查清单，并确认入口脚本包含 `chmod +x`。
