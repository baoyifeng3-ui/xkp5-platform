# Registry 导入进度设计

## 目标

镜像审批后展示真实导入阶段、百分比、镜像层计数、已用时间和最后更新时间；导入完成自动变为可发布，长时间无更新显示可能卡住。

## 数据

`image_artifact` 增加 `import_stage`、`import_progress`、`import_completed_layers`、`import_total_layers`、`import_updated_at`。旧记录默认 0%，不影响查询。

## 进度规则

- 校验归档：5-10%
- 读取清单并统计层数：20%
- `skopeo` 每开始复制一个 blob 时按已完成层数映射到 20-90%
- Registry 返回摘要：95%
- 摘要写入成功：100%

前端轮询已有 artifact 接口，显示阶段和层数。`IMPORTING` 且 120 秒无更新时间时标记“可能卡住”。

## 失败与恢复

失败保留最后进度、失败码和原因。租约恢复或重新导入时进度重新从校验阶段开始。完成记录固定为 100%。
