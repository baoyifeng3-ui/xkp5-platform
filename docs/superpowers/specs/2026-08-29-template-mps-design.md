# 模板级 MPS GPU 调度设计

## 目标

为代码编辑容器增加可选的 NVIDIA MPS 调度限制。只有勾选“启用 MPS”的模板创建的环境使用 MPS；未勾选模板继续使用现有 Docker GPU 路径。

## 行为

- GPU 关闭时不能启用 MPS，也不能填写 MPS GPU 比例。
- GPU 开启、MPS 关闭时，容器按现有方式使用 GPU，不启用 MPS。
- GPU 和 MPS 都开启时，MPS GPU 比例必填，范围为 1-100。
- 现有模板默认 `mpsEnabled=false`，已有环境无需迁移。
- MPS 服务启动或配置失败时，受限环境创建/启动失败并返回明确错误；不影响同一服务器上的非 MPS 容器。
- MPS 配置必须随 Agent 重启和服务器重启自动恢复，并在环境停止/删除时释放对应配置。

## 实现边界

- 管理端模板数据、模板校验、环境组件协议增加 `mpsEnabled` 字段。
- Agent Docker 创建配置仅在 `mpsEnabled=true` 时执行 MPS 配置；`false` 时保持现有 DeviceRequests 和资源配置。
- 处理服务器需要提供 MPS 生命周期检查和按容器比例配置的适配层，不能由前端直接执行宿主机命令。
- API 及数据库对旧记录使用 false 默认值。

## 验收

1. 关闭 MPS 的模板创建容器时不启动 MPS，GPU 仍可用。
2. 开启 MPS 的模板创建容器时可检查 MPS 状态和配置比例。
3. 无效组合在管理端和 Agent 端均被拒绝。
4. MPS 启动失败不会改变非 MPS 环境行为。
5. Agent、管理端单元测试和前端契约测试通过。
