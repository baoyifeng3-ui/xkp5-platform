# 镜像直达流程变更与验证记录

## 已部署行为

- 镜像文件身份为镜像仓库名和原始文件名，归档固定保存到 `registry-staging/files/<repository>/<filename>`。
- 上传会话仅传输分片；不计算归档或分片 SHA-256。上传完成后快速检查 TAR 和 `manifest.json` 引用并同步启用。
- 镜像文件可直接下发，不再经过审核、Registry 导入或发布操作。
- Agent 按 `repository:tag` 判断本机同名镜像；确认覆盖后执行 `docker load`，失败信息原样返回平台。
- Agent 心跳上报全部 Docker 镜像和容器，平台服务器清单直接展示最新心跳数据。
- 服务启动后扫描固定归档目录，可在索引缺失时重建 `image_file` 记录。

## 部署结果

- Flyway V54 已执行成功，数据库当前版本为 54。
- Java 容器：`match-v2_java:simple-image-flow`，健康接口 HTTP 200，数据库状态正常。
- Vue 容器：`match-v2_vue:simple-image-flow`，首页 HTTP 200。
- xt-01、xt-02、xt-03 Agent 均升级到 `0.2.29`，心跳正常。
- 现有代码编辑归档已通过固定路径恢复为 `xkp/code:v1`。
- 升级前数据库备份：`backups/match-before-simple-image-20260909-0050.sql`。

## 验证结果

- Java：`ImageUploadServiceTest`、`ImageFileServiceTest`、`SimpleImageDeploymentTest`、`AgentDockerInventoryTest` 通过。
- Agent：协议、直接镜像下发和 Docker 清单投影相关测试通过；Linux amd64 静态二进制构建成功。
- Vue：简化镜像流程契约测试与生产构建通过。
- 上传成功后对话框立即关闭并重新加载页面数据；推送列表展示 Agent 上报的百分比、传输字节数和 Docker 加载阶段。
- 小型 TAR 实际上传后立即出现在启用列表，删除后物理文件和索引均移除。
- xt-03 已有 `xkp/code:v1` 时，直接下发任务返回 `SUCCEEDED`，未重复下载归档。
- 结构可读但内容无效的测试 TAR 能上传启用；目标 Agent 返回 `FAILED`，错误为“镜像文件无效，请更换有效的 Docker 镜像归档”，符合设计。

## 已知限制

- 目标服务器完整覆盖下发会传输整个 TAR，不支持断点续传；现有需求选择了更简单的直接下载实现。
- 服务器清单来自最近一次 Agent 心跳，不是点击页面时实时查询 Docker。
- Java 全量基线仍有与本功能无关的终端、授权、账号和参与者登录测试失败；本次运行结果为 711 项中 5 项失败、4 项错误。与新规则冲突的旧归档预检查测试已更新并单独通过。
- Agent 全量测试在 Windows 主机上仍受 Linux 构建标签、文件权限断言和已有容器/NVIDIA 测试改动影响；本次相关包测试和 Linux amd64 静态构建通过。
