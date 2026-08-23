# 课程平台本地验证

在 `vue` 目录执行以下检查，不需要物理服务器或处理服务器 Agent：

```powershell
npm run test:course-platform
npm run test:platform-ui
npm run test:preview
npm run build
```

这些检查覆盖课程管理、资源上传入口、资源下发入口、学习进度、授权预览、模型验证、角色导航和页面构建。

Java 课程服务测试需要 Maven 依赖缓存或联网环境：

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace `
  maven:3.9.9-eclipse-temurin-8 `
  mvn -q -Dtest='*Course*Test' test
```

Docker Desktop 恢复后，再使用 `deploy/dev/start-current-stack.ps1` 启动隔离应用栈，并运行 `deploy/dev/smoke-test.ps1` 验证登录、课程 API 和资源接口。

物理服务器只在验证 Agent 实际执行资源复制、容器启动和 T100 推理时需要接入。
