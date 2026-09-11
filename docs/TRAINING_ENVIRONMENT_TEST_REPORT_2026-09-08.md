# 实训环境测试与修复记录

日期：2026-09-08。

## 当前结论

自动化回归通过，整体实机验收尚未完成。不能将单元测试、静态契约测试或数据库临时表测试等同于真实页面与远端容器联调通过。

本轮源码修复未部署到 stable Java 或远端 Agent。前端开发预览位于 http://localhost:19150，代理当前 stable 后端。当前 stable 地址为 http://localhost:19140。

## 已复现并修复

| 问题 | 修复与验证 |
| --- | --- |
| 管理员环境列表 VS Code 地址仅为 `https://` | 补齐主机及端口；Java 测试先失败后通过 |
| 切换环境保留旧工具，操作目标与显示不一致 | 切换时清空工具及报告；启动等待结束时核对环境；行为测试通过 |
| 预览页可能调用真实工具启动 | `openTool` 增加预览保护；行为测试通过 |
| 管理员模型验证入口、返回入口指向普通用户页面 | 使用管理员演示路由；返回按钮表达式执行测试通过 |
| 数据集全部上传失败仍显示成功，重试重复发送成功文件 | 按失败结果提示，仅重试未成功文件，提交期间锁定目标；行为测试通过 |
| 数据集缺少文件名时空指针异常 | 上传入口拒绝缺失、空白和点目录文件名；Java 测试通过 |
| 数据集命令提交失败后遗留存储文件 | 失败时清理本次上传文件；成功任务保留至 Agent 完成；Java 测试通过 |
| Agent 拒绝单模板环境，与页面、Java 和协议契约不一致 | 执行器接受一或两个组件；标注单容器、编辑单容器完整生命周期测试通过 |
| 文件传输允许父目录路径和已有软链接越界 | 校验相对路径及已有软链接，使用唯一临时文件并清理；真实临时目录读写测试通过 |
| 实训报告延迟保存使用切换后的课程及内容 | 捕获编辑时课程和内容；忽略旧课程延迟读取结果；行为测试通过 |

已同步修正失效测试：课程下发事件使用实际 `TRANSFER_FILE` 类型；比赛答题环境由独立实践页启动；创建表单移除后端不接收的端口字段；Agent 容器测试使用临时 TLS 文件夹具。

## 执行结果

| 层次 | 范围 | 结果与限制 |
| --- | --- | --- |
| Java | environment、account、transfer、course 包 | 148 项，0 失败，0 错误；含生命周期、端口池、课堂策略、课程资源、报告、上传等现有测试；多数依赖使用 mock |
| Vue | 9 个相关脚本 | 全部通过，包含新增真实组件方法行为测试；既有契约脚本多为源码结构断言 |
| Vue 构建 | `npm run build` | 通过；存在资源体积、旧 Browserslist 数据及 Node 弃用警告 |
| Agent | container、modeldeploy、transfer、runtime、protocol | 5 个包全部通过；新增传输测试使用真实本地文件系统；Docker 生命周期使用 fake Docker API |
| MySQL | 按现有结构创建临时表，检查创建、删除、乐观锁、过期更新拒绝、分配唯一性、端口唯一性、活动任务唯一性、端口释放、回滚 | 9/9 通过，无持久业务数据写入 |
| MySQL 迁移 | 当前库 `flyway_schema_history` | 52 条成功；实训环境表查询为 0 条 |
| HTTP | 6 个实训相关接口未登录访问 | 均返回 HTTP 401；未执行已认证业务请求 |
| 浏览器 | 当前 stable 登录页 | 正常显示；无可用已登录会话，未执行创建或工具点击链路 |

## 可重复执行

在 `xkp5-platform` 根目录执行：

```powershell
./scripts/test-training-database.ps1
docker run --rm -v "${PWD}/java/match-mgr:/workspace" -v xkp5-m2:/root/.m2 -w /workspace maven:3.9-eclipse-temurin-17 mvn -q '-Dtest=com.match.environment.**,com.match.account.**,com.match.transfer.**,com.match.course.**' '-DargLine=--add-opens=java.base/java.lang=ALL-UNNAMED' test
```

在 `vue` 目录执行 `node scripts/test-training-behavior.js` 和 `npm run build`；其余相关脚本为 account-environment-creation、admin-demo-entry、competition-environment-access、course-platform、management-mode、t100-paper、training-environment-creation、user-training-environment 对应的 `scripts/test-*.js`。

在同级 `xkp-agent` 根目录执行：

```powershell
docker run --rm -v "${PWD}:/workspace" -v xkp-agent-go-mod:/go/pkg/mod -v xkp-agent-go-build:/root/.cache/go-build -w /workspace golang:1.23-bookworm go test -count=1 ./internal/container ./internal/modeldeploy ./internal/transfer ./internal/runtime ./internal/protocol
```

## 尚需实机验收

- 使用普通管理员和普通用户测试账号登录，配置独立可清理的环境，验证创建、批量结果、还原、单项和整组删除及最终数据库状态。
- 验证上课、下课、同用户环境互斥切换、等待依赖、离线服务器及失败恢复。
- 实际打开标注、VS Code、Jupyter，验证 TLS、iframe、刷新、全屏以及桌面和移动布局。
- 真实上传和下发文件，验证 Agent 目录内容、校验和、数据库任务终态及存储清理。
- 验证模型文件列表、部署、覆盖、命令失败，以及真实 T100 图片和 URL 验证。
- 实际编辑报告、自动保存、导出、交换空间和作业空间上传。
- 本轮后端与 Agent 修复部署后，重复上述完整链路；开发前端预览本身不会更新后端和远端 Agent。

## 已知边界

- JDK 8 构建因既有 `AgentPackageService` 使用 `InputStream.readAllBytes()` 失败；本轮改用与运行后端一致的 JDK 17 验证，未修订项目 Java 版本基线。
- 文件路径检查拒绝已存在的软链接；未提供对恶意并发替换目录的原子隔离保证。
- 上传文件清理验证覆盖方法内命令提交失败；事务提交阶段异常及存储删除服务失败仍需故障注入验证。
- 未执行全项目回归，不代表其他模块原有失败已解决。
