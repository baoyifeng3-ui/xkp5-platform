# TODO

## 当前状态

- 2026-09-11 正式版本冻结进行中：目标 `5.1.0`，Agent `0.2.34` 已提交为 `e6539e1` 并推送 `main` 与 `5.1.0` 标签。平台全量发布门槛、源码提交、镜像摘要与离线包尚在完成中。

- 2026-09-11 登录背景新增 0–100% 蒙版透明度，默认 35% 保持原外观；背景图片始终完整显示，仅深色蒙版随滑块变化。新旧管理页、API、键值数据库、全局状态及登录页已统一，越界值由后端拒绝。真实浏览器以 42% 验证图片层为 1、蒙版为 0.42 后恢复 35%；误建的图片透明度键已删除。运行 Java/Vue 镜像均为 `background-overlay-20260911`。

- 2026-09-11 比赛实操页已补充“下载平台根证书”入口，复用 `/user/code-server/root-ca.pem`；每个项目只下载固定根 CA，动态服务器叶子证书无需逐台下载。

- 2026-09-10 后续统一切换修复：Java `switch-20260910c`、Vue `switch-20260910b` 已部署，Agent 仍为 `0.2.34`。教师接管启动/创建中环境、重复课堂请求去重、服务层课堂权限、真实阶段等待、双角色课堂版本同步、下课/比赛转换按钮禁用均已实施。报告：`docs/UNIFIED_SWITCH_QA_2026-09-10.md`。
- 本轮不是全部验收完成：VS Code 学生浏览器直连返回 `ERR_CERT_AUTHORITY_INVALID`，当前 CA 与本机已信任 CA 指纹及公钥不一致。安装当前 CA 的授权待确认；代码界面显示、输入保存及 WebSocket 验收未通过，不能沿用上一轮接口检查结论。
- 用户当前正式环境为 16 个课程/独立实训环境及 8 个比赛环境，全部保留。本轮测试不删除这些环境。

- 2026-09-10 联合修复验收完成：Java `match-v2_java:qa-fixes-20260910d`、Vue `match-v2_vue:qa-fixes-20260910c` 已部署；三台 Agent 均为 `0.2.34`。镜像按 ID 合并；升级仅允许更高版本；证书修复、创建状态回显、失败回报持久化、目录隔离、同用户先停后启及课堂/比赛切换已验证。详细报告：`docs/ENVIRONMENT_MODE_QA_2026-09-10.md`。
- 本轮 Java 55 类 264 项、Vue 15 个相关脚本及生产构建、Linux Agent 6 个相关包、MySQL 9 项通过。真实验证覆盖原有 8 个比赛环境恢复、双用户双服务器 A/B/课程切换、比赛往返、临时凭据恢复、VS Code/标注/Jupyter 服务、单组件冷启动、缺失容器、证书故障、离线上课及升级正反向。
- 本轮 `QA-20260910-*` 临时环境记录、组件容器及端口分配已清理；原有 8 个比赛环境保留。测试后平台为 TRAINING，课堂关闭，服务器模式 NORMAL。暂未创建正式长期课程/默认实训环境，实际开课仍须创建并选择课程或独立环境。
- 未进行全仓库全功能测试、长期压力测试、GPU 训练及 28 GB 镜像重新上传下发；前端保留既有大包/依赖警告。旧 stable 容器保持停止用于回退。

- 2026-09-10 镜像下发方案 1 已实现并部署：Java 完成 Agent 鉴权与任务校验后返回 `X-Accel-Redirect`，Nginx 从只读 registry staging 挂载直接 `sendfile` 归档，内部文件路径禁止外部直接访问；单台/批量推送语义不变。运行镜像为 Java/Vue `sendfile-20260910`。

- 2026-09-10 定位 28.86 GB 代码镜像三台下发均在精确 30 分钟失败：Agent 固定 30 分钟 context deadline 先触发，实际仅传输 8.6-14.4 GB；调整 Agent 上限为 3 小时、平台绝对回收为 3 小时 15 分钟，并关闭 Agent 归档路由 Nginx 响应缓冲。未实现断点续传。
- 2026-09-10 已部署 Java `match-v2_java:image-timeout-20260910`、Vue `match-v2_vue:image-timeout-20260910` 和三台 Agent `0.2.31`；Java 关键回归 44/44、Agent runtime/imagedeploy/protocol、Vue 构建通过。SSH Bridge 注册为本机登录启动任务 `XKP5-SSH-Bridge`，真实连接 xt-01 成功。

- 2026-09-10 六项实训/比赛问题本轮修复并部署 `match-v2_java:six-bugs-20260910`：重复删除不再取消自身删除任务；模式切换的启动/停止命令使用 control payload；账号创建的比赛实训环境纳入模式切换；模板创建要求当前启用镜像文件、成功推送记录和在线服务器镜像清单同时满足；SSH Bridge 已在本机 19245 启动。Java 回归 34/34 通过，Agent runtime/imagedeploy/protocol 通过，前端返回 200。

- 2026-09-10 完成实训/比赛/镜像队列根因排查：三台 Agent 被同时进行的大镜像下发任务占用，导致创建、删除、一键上课和比赛切换排队；已释放旧失败任务。后续确认真实问题是 28.86 GB 归档触发 Agent 固定 30 分钟上限，现已调整为 Agent 3 小时、平台 3 小时 15 分钟。模板只接受当前启用镜像文件且必须存在成功推送记录。新增流程基线文档 `docs/TRAINING_ENVIRONMENT_FLOW_BASELINE_2026-09-10.md`。
- 2026-09-10 回归：数据库阻塞命令与活动镜像下发均为 0；Go Agent runtime/imagedeploy/protocol、Vue 模板/实训/管理模式契约通过。Java Maven 集成测试因启动阶段长时间无输出未完成；Vue 旧比赛契约仍锁定过时路由字符串，Go 容器权限测试在 Windows 宿主不满足 Linux 权限断言，均待修订后纳入发布门槛。

- 2026-09-10 模板选择器改为只显示当前启用镜像文件的原始名称，并以成功推送记录作为可用性条件；历史 target_image（包括不存在的 v1/v2 标签）不再直接展示。模板不绑定服务器，实训环境仍按目标服务器检查镜像。

- 2026-09-09 清理并切换到最新 stable 应用：仅保留 `xkp5-stable-java`（`match-v2_java:flow-20260909b`）和 `xkp5-stable-vue`（`match-v2_vue:flow-20260909c`）运行；旧 stable/失败备份容器已删除。Vue 页面目标 Agent 版本已确认是 `0.2.30`。数据库、Registry、FastDFS 和测试环境容器未删除或停用。

- 2026-09-09 修复新容器部署导致的授权无效：重建 Java 容器时恢复挂载 `.xkp5-host-identity.json` 主机指纹、`XKP_LICENSE_PUBLIC_KEYS` 和 32 字节 `MATCH_COMPETITION_PASSWORD_KEY`；此前缺少指纹导致有效授权被判 INVALID，短密钥导致应用无法启动。当前 stable Java 已正常启动，数据库授权记录未修改。

- 2026-09-09 镜像流程优化源码完成：上传按文件名去重、仅做 TAR 头/manifest 快检、自动生成仓库与 latest 标签；服务器推送支持同名确认覆盖；服务器清单支持实际镜像检查、镜像/容器删除及 Docker 错误回传；模板从在线服务器实际镜像创建并固定 image ID，模板引用镜像禁止删除；删除平台容器复用实训环境删除任务并在 Agent 成功后清理环境。Java 相关回归 130/130 通过，Go 新增镜像清单/下发包通过，Vue 构建与镜像流程契约通过。比赛环境容器从服务器清单删除尚未接入比赛环境删除任务；真实远端 Agent/浏览器链路尚未验证。
- 2026-09-09 已部署最新镜像流程到本机 stable：Java `match-v2_java:flow-20260909b`、Vue `match-v2_vue:flow-20260909`，Flyway 迁移 55 已成功应用，应用启动并监听 19141，前端 19140 返回 200。旧容器保留为 `xkp5-stable-java-failed-20260909`、`xkp5-stable-java-old-20260909`、`xkp5-stable-vue-old-20260909` 以便回退。后端健康接口未匿名验证（该接口要求 Token）。

- 2026-09-09 实训/比赛切换规则已简化并完成源码回归：课程与独立实训保持“同用户先停后启”，比赛进入先检查所有绑定比赛环境；退出比赛只停止比赛容器，不再自动 RESTORE 重建或启动赛前实训；比赛模式切换期间关闭课堂会话；等待启动在依赖失败或 5 分钟超时后明确失败；前端异常状态停止轮询。Java 相关模式/环境/权限测试、Vue 实训行为测试与构建、MySQL 9/9、Agent 5 包测试通过。真实远端 Agent、已登录浏览器业务链路和带实际账号的容器切换尚未完成，原因是当前 SSH 凭据无法登录 xt-01/02/03，测试库实训环境为空。

- 2026-09-09 修复实训环境创建误报镜像版本不一致：创建校验优先读取 Agent 心跳中的 Docker 镜像仓库/标签清单，只有旧 Agent 未上报清单时才回退到旧版成功推送记录；缺失时显示具体服务器和镜像。TrainingEnvironmentServiceTest 8/8 通过，相关 Java/Vue/数据库/Agent 回归通过。尚未部署到 stable，真实页面创建仍需远端账号和 SSH 凭据。

- 2026-09-09 镜像直达下发遇到同服务器已有活动任务时，错误提示已由 `DEPLOYMENT_ALREADY_ACTIVE` 改为包含服务器和当前镜像的中文说明；SimpleImageDeploymentTest 通过。尚未部署到 stable。

- 2026-09-09 修复镜像下发标签漂移：Agent 执行 `docker load` 后读取归档返回的实际镜像名或镜像 ID，自动重新标记为上传记录指定的完整目标（如 `xkp/anno:v1`），并在完成前校验目标标签存在；新增镜像下发回归测试通过。尚未部署到 stable。

- 2026-09-09 镜像仓库专项复测：Java registry 70 项通过；Vue 镜像仓库、批量推送、上传性能、直达重试、大文件完成 5 项通过；流程契约通过；Agent 直达镜像和协议包通过。修复上传完成接口改为后台合并校验，避免大 TAR 请求超时；批量推送和直达上传测试契约已与当前简化流程对齐。完整 Agent/全项目回归仍有既有 NVIDIA 采集测试在 Windows 编译失败；真实浏览器上传、远端 Agent Docker load 因当前 SSH/登录会话限制未执行。

- 2026-09-09 镜像上传改为单次普通 multipart 文件上传，不再由前端切片；后端新增 `/admin/image-files/upload`，流式保存后复用 TAR 校验和镜像文件启用流程。相关 Java 打包、Vue 构建及镜像仓库契约通过。旧分片接口保留兼容历史任务；stable 尚未部署。

- 2026-09-09 镜像上传成功后立即关闭上传对话框并刷新镜像、推送和服务器清单，已移除 1.5 秒延时刷新；推送列表新增实时百分比、已传输/总大小和 Docker 加载阶段进度条，前端已部署。

- 2026-09-09 镜像管理已切换为简化直达流程并部署：`.tar` 上传只做 TAR 头及 `manifest.json` 引用快检，不做归档/分片 SHA-256；文件按“镜像仓库+文件名”固定存储并自动启用，`upload_id` 不再参与启用和下发；移除前端审核、导入、发布步骤，直接选择服务器下发。三台 Agent 已升级到 `0.2.29` 并上报镜像/容器清单。真实验证：同名镜像直接返回成功，无效归档由目标服务器 `docker load` 返回明确错误。详情见 `docs/IMAGE_DELIVERY_TEST_REPORT_2026-09-09.md`。

- 2026-09-08 镜像归档上传改为按镜像组、组件和版本无条件覆盖：创建新任务前级联删除旧任务、发布/推送记录及 staging；前端选择文件后不再续传旧任务。Java 编译待 Maven 环境可用后验证。
- 2026-09-08 推送创建任务时取消归档预检查，避免 upload_id 不一致时在创建推送阶段误阻断；实际下载阶段仍要求归档存在，缺失时返回重新上传发布提示。
- 2026-09-08 Java 推送归档读取增加按最终 SHA-256/期望 SHA-256 查找其他 upload_id 的兜底，修复数据库 upload_id 与 staging 目录不一致时的可恢复场景；当前图像标注归档物理文件已丢失，仍需重新上传。

- 2026-09-08 实训环境本轮源码修复及自动化回归完成：Java 148/148、Vue 9 个相关脚本及构建、MySQL 9/9、Agent 5 个包通过。修复管理员工具地址、环境切换、上传失败提示与文件清理、单模板执行器、传输路径边界及报告延迟保存串课程等问题。详情见 `docs/TRAINING_ENVIRONMENT_TEST_REPORT_2026-09-08.md`。
- 实训环境尚未整体验收：缺少已登录测试会话，当前库实训环境为空；真实创建/启动/还原/删除、工具、T100、资源与报告页面联调待完成。本轮 Java/Agent 修复尚未部署；前端开发预览为 `http://localhost:19150`。

- 离线安装流程已存在，入口为根目录 `install-xkp5-offline.sh`。
- 离线安装说明位于 `deploy/offline/README.md`。
- 离线安装器已补齐 `code-server-ca.sh` 的复制步骤，避免首次安装时报 TLS 脚本缺失。
- 离线安装器已移除重复注入 `MATCH_COMPETITION_PASSWORD_KEY` 的逻辑。
- 本机 `http://localhost:19140` 当前运行候选版 `candidate-d095ff7`：Java 镜像 `sha256:f41e900a...`，Vue 镜像 `sha256:ddf3a590...`；正式端口、CORS、授权按钮、数据库及两台 Agent 心跳已验证。
- 升级本机后端前的数据库备份保存在 `backups/xkp5test-before-5.0.1-20260907-115956.sql`。
- 已清理被替代的离线构建备份、失败/测试容器和旧 Java/Vue 镜像标签；当前运行镜像、最新离线包、数据库备份和 9 月 2 日完整快照保留。
- 稳定候选与版本化发布实施计划已写入 `docs/superpowers/plans/2026-09-07-stable-candidate-and-versioned-release.md`。
- 候选分支 `release/5.0.2-candidate` 当前提交为 `697dcf9`；版本清单和部署静态测试通过，但 Java 全量测试为 706 中 13 失败、5 错误，Agent 全量测试也有失败，尚未达到发布门槛。
- 2026-09-07 当前环境复测结果已记录在 `docs/TEST_REPORT_2026-09-07.md`：Vue 构建通过、Python 5 项通过；15 个 Vue 契约测试失败，Agent 构建/测试失败，Java 因未安装 Maven 未执行，部署 shell 测试因 WSL 缺少 `/bin/bash` 未执行；未创建正式版本标签或归档包。
- 授权工具已支持 `--duration 30d`、`--duration 60天`、`--duration 永久/permanent`，并保留 `--expires-at` 自定义到期时间；两者互斥。
- 授权工具已新增 Windows WinForms 图形界面 `授权工具-GUI.ps1`，启动批处理会直接打开界面，可选择请求文件、授权时长、单位名称和新的输出文件。
- 授权诊断页面已精简为授权状态、单位、期限、剩余天数和导入时间；内部实例、指纹、公钥等字段已隐藏，后端诊断接口返回当前授权导入时间。
- 授权诊断页已补回“下载 .xkpreq”和“导入授权”操作入口，并同步到当前 stable/candidate 前端容器。
- 授权导入报错“未知密钥”已确认是签发工具 key-id 与后端公钥配置不一致；GUI 现支持 `XKP_LICENSE_KEY_ID` 覆盖默认值，并在错误提示中明确说明配对要求。
- 2026-09-07 已恢复 stable/candidate Java 与 Vue 容器运行；Vue 使用最新授权页面静态资源，Java 暂恢复原候选可执行 JAR，待完整 Maven 打包完成后再切换后端改动。
- stable 后端已完成 Maven 可执行 JAR 打包并运行 `match-v2_java:license-info`；健康检查通过，已注入与授权工具匹配的 `production-2026-09` 公钥，授权诊断接口需登录后验证。
- 授权模块端到端复测通过：超级管理员登录、请求生成与下载、60 天授权签发、授权导入、授权状态与诊断信息（单位/到期时间/导入时间）均已验证；stable 当前授权单位为 `TestOrg`，到期时间为 2026-11-06。
- 镜像仓库整体测试报告已记录在 `docs/IMAGE_REGISTRY_TEST_REPORT_2026-09-07.md`：Vue 构建通过；Java registry 60 项中 56 通过、4 失败；Vue registry 契约和部署流程契约失败；真实 Registry/importer/Agent 联调未执行，尚未达到发布门槛。
- 镜像仓库修复后复测通过：Java registry 60/60、Vue registry UI 契约、假 Registry/importer/Agent 流程契约和 Vue 构建均通过；真实联调仍需独立临时环境，暂不宣称生产无 bug。
- 已取消镜像仓库卡死任务 `329b4747-e8a2-4ae4-8bcf-83d9dd4dd338`（镜像 `ffa02c56-2953-4424-b24f-22d26ef2af23`），状态改为 `CANCELLED/REJECTED`，临时 staging 已清理；其余两个待审核任务保留。
- 镜像仓库已支持删除失败/已取消/已驳回的上传与导入任务；上传界面遇到非 `UPLOADING/PENDING_REVIEW` 的旧断点会自动创建新任务，避免 `upload is not writable`。Java registry 60/60、UI 契约、流程契约和 Vue 构建通过，最新 Java/Vue 已部署到 stable。
- 运维主页已重设计为仪表盘：四项运行指标、服务器健康环图、资源分布柱图、运行提醒和常用入口；仅修改 `OperationsHome.vue`，数据接口和其他功能不变，最新资源已同步 stable。
- 新增服务器报错“Code-server TLS 根证书不可用”已修复：创建持久化 `.xkp5-code-server-tls` CA 并挂载到 Java `/etc/xkp/code-server-tls`；后端健康检查通过，xt-03 部署包生成返回 HTTP 200 且包含证书与安装脚本。
- 已修复已删除断点导致的 `upload not found`：断点探测 404 不再弹全局错误，界面会清除旧上传 ID；未勾选校验时立即创建新任务并由服务器计算 SHA-256。真实 API 创建、取消、删除冒烟测试通过，前端资源 `app.9a619645.js` 已部署。
- 镜像目录中的未完成、失败、已导入和已发布任务均已提供“删除镜像”入口；删除会级联清理上传分片、发布和推送历史。`zy-anno.tar` 的平台记录与 staging 已清零，可重新上传；Agent 运行容器未停止。
- 已修复镜像推送下载归档 HTTP 400：Java 归档目录统一为持久化 `/data/registry-staging`，Compose 与 stable 运行容器显式配置 `JAVA_TOOL_OPTIONS=-Dxkp.registry.staging-dir=/data/registry-staging`，避免容器重启后归档落到 `/tmp` 丢失。
- 已确认导入成功后的 archive 不再删除，保留用于 Agent 推送和失败重试；导入、归档下载、部署、删除回归测试 22/22 通过，stable 后端已部署最新 JAR，健康检查通过。

## 待确认

- [ ] 按 `docs/superpowers/specs/2026-09-07-versioned-release-baseline-design.md` 整理候选源码并构建候选栈。
- [ ] 修复候选栈并完成关键功能验收，通过后再标记为 `5.0.2`。
- [ ] 从验收通过的源码和镜像构建 `5.0.2` 离线包，不复用旧 Java/Vue 镜像。
- [ ] 先修复并重新验证 Java/Agent 基线测试失败，再继续候选构建。
- [ ] 修复 Vue 授权路由契约测试与当前路由结构不一致的问题，并重跑全部 Vue 契约测试。
- [ ] 在目标服务器升级后验证服务和关键功能。
- [ ] 修复并复测 `docs/TEST_REPORT_2026-09-07.md` 中列出的 Vue、Agent、Java 与部署环境问题后，才允许创建正式版本标签和离线归档。

- 2026-09-09 修复模板服务器镜像选择失败：Agent 心跳仅有 images、containers 为 null 时，后端不再误判整个 Docker 清单不可用；相关 5 项 DockerInventory、1 项模板、9 项实训环境测试通过，Java 新容器 flow-20260909d 已启动。
