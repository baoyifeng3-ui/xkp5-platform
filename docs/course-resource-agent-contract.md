# 课程资源下发 Agent 契约

## Command

管理端通过现有 Agent 命令租约发送 `DELIVER_COURSE_RESOURCE`，命令版本沿用环境命令版本 `1`。

```json
{
  "environmentId": "环境 UUID",
  "resourceId": "资源 UUID",
  "resourceType": "EBOOK|VIDEO|PPT|ARCHIVE",
  "storageKey": "FastDFS 资源标识",
  "targetPath": "training/7/12/course-resources/resource-uuid"
}
```

Agent 必须校验载荷字段完整、目标路径位于该环境工作区内，并通过受控存储客户端读取 `storageKey`。不得把资源写入宿主机工作区以外的目录。

## Result

成功回执使用现有 Agent 回执协议：

```json
{
  "success": true,
  "code": "RESOURCE_DELIVERED",
  "message": "resource copied to training workspace",
  "details": {
    "resourceId": "资源 UUID",
    "targetPath": "容器内相对路径",
    "sha256": "实际文件摘要"
  }
}
```

失败时 `success` 必须为 `false`，`code` 使用稳定的大写错误码，例如 `RESOURCE_NOT_FOUND`、`TARGET_PATH_INVALID`、`RESOURCE_CHECKSUM_MISMATCH` 或 `RESOURCE_COPY_FAILED`。

平台按 `environmentId:RESOURCE:resourceId:sha256` 作为幂等键。重复命令必须返回已有任务结果，不得重复写入文件。Agent 完成回执后，平台将 `course_resource_delivery` 更新为 `SUCCEEDED` 或 `FAILED`。
