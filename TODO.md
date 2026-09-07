# TODO

## 当前状态

- 离线安装流程已存在，入口为根目录 `install-xkp5-offline.sh`。
- 离线安装说明位于 `deploy/offline/README.md`。
- 离线安装器已补齐 `code-server-ca.sh` 的复制步骤，避免首次安装时报 TLS 脚本缺失。
- 离线安装器已移除重复注入 `MATCH_COMPETITION_PASSWORD_KEY` 的逻辑。
- 本机 `http://localhost:19140` 当前运行 2026-09-05 早上的 `match-v2_vue:5.0.1`，并代理到 `match-v2_java:5.0.1`；前端、代理和后端健康检查已通过。
- 升级本机后端前的数据库备份保存在 `backups/xkp5test-before-5.0.1-20260907-115956.sql`。
- 已清理被替代的离线构建备份、失败/测试容器和旧 Java/Vue 镜像标签；当前运行镜像、最新离线包、数据库备份和 9 月 2 日完整快照保留。
- 稳定候选与版本化发布实施计划已写入 `docs/superpowers/plans/2026-09-07-stable-candidate-and-versioned-release.md`。

## 待确认

- [ ] 按 `docs/superpowers/specs/2026-09-07-versioned-release-baseline-design.md` 整理候选源码并构建候选栈。
- [ ] 修复候选栈并完成关键功能验收，通过后再标记为 `5.0.2`。
- [ ] 从验收通过的源码和镜像构建 `5.0.2` 离线包，不复用旧 Java/Vue 镜像。
- [ ] 在目标服务器升级后验证服务和关键功能。
