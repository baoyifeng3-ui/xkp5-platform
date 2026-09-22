# XKP5.0 测试记录（2026-09-16，持续更新）

> 记录说明：本文按时间追加。较早的“未修复/未恢复”观察可能被后续章节更正；以最新章节为准。源码修复回归通过不等于已部署版本通过。

## 2026-09-21 修复回归补录

- 本地修复涉及四点：模型 URL 请求 JSON、`SampleController` 隐藏异常堆栈、删除账号清理课程进度、学生读取章节校验课程已发布。
- Java 全量回归：154 suites、750 tests，0 failures、0 errors、0 skipped。
- Go Agent `internal/dockerinventory` 与 `internal/protocol`：通过；多标签删除方案未保留，F05 仍是未解决问题。
- Vue 修复行为、训练行为、课程平台契约及 Python 5 项测试通过；生产构建通过并保留既有资源体积警告。
- 当前线上容器仍为 `background-overlay-20260911`，未部署这些本地修复，不能把线上问题标记为已关闭。

## 最新修复回归状态（2026-09-20）

本地源码已修改，尚未部署，运行环境不能认定缺陷已关闭。此前“未修改源码”仅对应早期测试阶段。

- F01：TrainingValidation URL 转换请求显式使用 application/json。新增 test-validation-json-behavior.js 执行实际 validate 方法，校验 URL、请求头、成功结果与 loading 恢复，通过。
- F06：SampleController 输入错误使用简短 400 业务响应，下载异常使用固定错误文本，不再经 Exception 重载拼接堆栈。新增 SampleValidationRegressionTest 在修复前失败、修复后通过。仅覆盖此接口，不代表其他控制器已全局消除堆栈泄露。
- F07：账号删除事务加入对应用户课程进度删除，新增 deletingStudentRemovesLearningProgress 回归通过。数据库迁移/已存在孤儿记录清理不包含在此改动中。
- F08：学生章节接口读取课程并校验 enabled；新增 CourseVisibilityRegressionTest，草稿/不存在课程拒绝且不读取章节，已发布课程正常读取，通过。
- Java 本轮 mvn 离线全量测试退出 0：154 suites、750 tests、0 failures、0 errors、0 skipped，证据 target/surefire-reports。旧 747 项为之前版本结果。
- Vue 上轮两个相关脚本及生产构建通过；本轮新增 JSON 行为脚本通过。构建体积警告仍存在。
- 撤回上轮 Agent 按可变标签逐个删除的改动及相应测试修改：存在标签漂移误删风险，且用镜像 ID 请求时可能在解除最后一个标签后再次删除已不存在 ID。Agent 仓库已无内容差异，dockerinventory/protocol 回归通过。F05 多标签删除仍未解决，三机残留不能记为清理成功。
- 授权请求疑点已更正：409 为下载字节数而非 HTTP 409；重复下载 HTTP 400 符合一次性下载设计。

剩余：修复部署及真实联调、多标签安全删除、浏览器逐按钮与嵌入复测、比赛交卷评分完整闭环、大文件/压力/断网、GPU 模型推理、有效授权到期与全新安装回滚。未以单元测试代替这些验收。

## 2026-09-21 回归

- Python T100 单元测试：5/5 通过。
- Go Agent `internal/dockerinventory` 与 `internal/protocol`：通过；多标签删除改动未保留，F05 仍未修复。
- Vue 修复行为、训练行为、课程平台契约：通过；前后端 HTTP 200。
- Java Maven 离线全量回归：154 suites、750 tests、0 failures、0 errors、0 skipped。
- 运行环境复核：TRAINING、课堂 inactive、24 个环境 STOPPED；当前运行容器仍使用旧镜像，以上 Java 修复尚未部署到运行环境。
- 浏览器控制仍无法连接，逐按钮/嵌入验收继续阻塞。

## 当前可测性结论

- 可在当前环境继续验证：源码单元/契约测试、接口权限与数据边界、课程/资源/账号/实训状态、部署静态检查、已有 Agent 的协议操作。
- 需要独立夹具或部署后才能完成：本地修复线上回归、真实比赛答题评分、有效授权替换/到期、大文件压力/断网恢复、GPU/T100 推理、全新机器安装升级回滚。
- 当前运行 `background-overlay-20260911` Java/Vue 镜像没有包含本地修复；未执行部署，因此 F01/F06/F07/F08 只确认“本地修复回归通过”，不能写成生产已解决。

本报告区分自动化测试、实际业务验证、测试脚本失败与未完成项。不是全功能验收通过证明。源码提交 59ea8e0；运行 Java/Vue 镜像 background-overlay-20260911，源码测试通过不代表部署版本逐功能通过。

## 本轮结案结论

状态：本轮测试结束，尚未达到全功能验收通过条件。未完成项目保持未测/阻塞，不计入成功。

- 已确认主要问题：模型 URL 请求 Content-Type 不匹配导致 415；多标签镜像无法删除；部分 Linux 脚本无执行权限；错误响应包含 Java 堆栈。
- 待复现观察：平台内嵌工具加载异常、启动等待时间短暂负数；浏览器控制认证故障使后续界面复测受阻，不能以接口成功替代浏览器结论。
- 覆盖缺口：全页面逐按钮/兼容性、完整比赛答题评分闭环、有效授权替换与到期、模板全部操作、大文件/压力/断网、GPU/T100 模型推理、全新主机安装升级回滚。
- 实测通过统计：Java 747 项、Vue 49 脚本、Python 5 项、数据库 9 项、Agent 14 个含测试包；43 门课程的 585 个 PDF 资源可访问。这些是不同计数单位，不能合并成总体通过率。
- 结案实时核验：TRAINING，课堂 active=false/stopping=false，24 个环境 STOPPED；QA 临时账号 0、QA 临时课程 0。三个服务器各残留一个本轮 QA 镜像、两个标签，受多标签删除缺陷阻塞；未强制删除。
- 未修改应用源码。仅维护本报告与 TODO；测试运行产生构建/测试输出、审计和命令记录。

## 成功

| 测试点 | 结果/证据 |
|---|---|
| Vue 契约和行为脚本 | 49/49 通过（脚本数量，不是独立断言数量） |
| Vue 生产构建 | 成功，有体积警告 |
| Java 全量单元测试 | Docker Maven 运行完成，152 suites、747 tests、0 failures、0 errors、0 skipped；证据 java/match-mgr/target/surefire-reports/TEST-*.xml |
| Python T100 单元测试 | Python 3.11.4，5/5 通过 |
| 真实 Agent Go 模块 | project/xkp-agent 在 Linux Go 1.23 容器、复用本机依赖缓存，14 个含测试包通过，2 个包无测试 |
| SSH Bridge | go test 成功但无测试文件，仅编译层面 |
| 实训数据库约束 | 9/9 通过，脚本回滚未留持久行 |
| 比赛模式、镜像部署、镜像仓库流程契约 | 三个脚本通过；假 Registry/importer/Agent 不代表真实大文件传输 |
| Linux 部署静态检查 | agent-ca、host-identity、quick-deployment、registry、test-static 5 入口直接通过；code-server-ca、online-update、server-update 在临时副本补可执行权限后通过；offline/test-static 在含 Docker Compose CLI 的隔离容器通过 |
| 三类账号登录 | 超管、管理员、学生登录成功 |
| 管理主页实际加载 | 13 用户、43 课程、585 资源、3/3 服务器在线；撤回之前零数据误报 |
| 课程表单 | 空名称/类别保存被提示阻止，浏览器新建 QA 草稿成功 |
| 课程增删改发布 | API 修改后回读一致；发布学生可见；停用学生不可见；删除后列表确认不存在，临时课程清理完成 |
| 学生权限边界 | 学生 GET admin/courses、admin/training-environments 返回 403 |
| 下课 | 浏览器确认按钮与停止中禁用/提示生效；后端最终 active=false、stopping=false，24 环境 STOPPED |
| 学生独立实训启动 | 点击 VS Code 后由 STOPPED 到 STARTING 到 RUNNING |
| 工具独立访问 | xt-02 图像标注任务页、JupyterLab、VS Code 工作区均完整加载 |
| Jupyter 保存 | 新建 QA-20260916-save.txt，输入保存，刷新后文本一致，contents API 回读相同 |
| VS Code 保存 | /home/student/data/QA-20260916-code.txt 保存成功；刷新后截图确认内容一致，Jupyter API 跨工具回读一致；两文件已清理 |
| 模型 URL 校验 | not-a-url 禁止提交，http URL 允许提交 |

## 已观察失败/异常

| 编号 | 测试点 | 证据与边界 |
|---|---|---|
| F01 | 模型验证图片 URL | 学生页面输入 http://localhost:19140/favicon.ico 点击开始验证，出现 HTTP 415 和重复错误提示；已确认 request.js 默认 application/x-www-form-urlencoded，而后端 @RequestBody 要求 JSON；同接口发送 JSON 可进入业务校验。此问题阻断 URL 模式，并非模型推理失败 |
| F02 | 实训工具平台内嵌 | VS Code/Jupyter/图像标注均显示“工具页面尚未完成加载”、about:blank；同浏览器独立打开成功，需排查嵌入限制，保存/WebSocket不能用此结果替代 |
| F03 | Linux 脚本执行权限 | Git 模式 100644：deploy/code-server-ca.sh、deploy/server-update.sh、update-xkp5-online.sh；Linux archive 直接调用 Permission denied，临时副本 chmod 后相应测试通过 |
| F04 | 实训启动等待文案 | 启动瞬间观察到“已等待 -2 秒”；可能时钟差，尚未反复复现 |

## 测试脚本失败（不直接等于产品缺陷）

- fake-agent-power-flow：超管执行在“Expected exactly one leased shutdown command”失败；诊断确认两次提交 code=200、相同 commandId，零等待 poll 返回 0 条。lease 截断到秒而 available_at 含毫秒；临时副本改 waitSeconds=2 后全流程通过（去重、拒绝过期租约、离线确认、禁用取消）。当前列表仅原三台，无临时 Agent 残留。
- test-unified-switch.py：24 环境停止通过，重复上课返回 startedAt 字符串断言失败。源码有重复请求复用逻辑；时间序列化/数据库精度与真实重复启动需区分。该脚本断言失败没有 finally，先前遗留课堂已在本轮下课清理。
- test-image-import-contract.ps1：要求完成后 deleteImportedArchive；当前源码已移除删除，项目规则要求保留归档供 Agent 下发。因此属于旧契约与现有流程不一致，撤回此前“导入顺序缺陷”结论。

## 更正和历史观察

- 普通管理员访问超管注册码接口 403 是正确权限拦截，不是失败。
- 管理主页零数据是在异步加载期间观察，复测有真实数据，不是权限过滤缺陷。
- 运维主页 34 项失败推送是已存在记录，不足以证明当前上传/推送失败。
- Python、Maven、Linux、Agent Go 依赖阻塞均已有可用替代方案，不再标记永久阻塞。
- Windows 工作区 .sh CRLF 无法直接在 Bash 执行；Linux git archive 使用 LF。测试代码没有修改。

## 本轮后续验证

- 课堂：8 个课程环境全部 RUNNING，allReady=true；学生独立环境启动被课堂锁拒绝；下课 stopping=true，停止期间重开被拒绝，最终 24 环境 STOPPED。
- 重复上课复测：首次 startedAt 为 14:40:52.373304606，第二次为 14:40:52.373，毫秒一致。源码 setStartedAt(now) 与写库 updatedAt 为两次取时，且数据库毫秒精度，可出现时间展示差；没有证据证明重复启动。原脚本失败保留为时间响应一致性问题。
- 模式往返：3 台 Agent 进入 COMPETITION 与返回 TRAINING 均 SUCCEEDED；旧学生会话失效，实训密码在比赛下拒绝，既有生成比赛凭据登录成功；回实训后原密码恢复。未重置密码。
- 学生比赛接口：user/me、competition、countdown 200；实训接口在比赛返回 403，符合隔离；当前比赛已结束，实际答题提交/评分闭环未更改既有排期。
- 管理页面冒烟：课程、资源、实训、设备、用户、学情、设置、帮助、比赛控制/赛规/试卷/评分/预览已进入；不能把页面显示等同所有按钮通过。设置页面原值保存成功。
- 运维页面：授权诊断与重新校验、管理员账号、镜像仓库页面可访问；授权当前即将到期，未更换授权。
- 交换空间目录创建/读取/删除通过；交换空间、作业空间文本文件上传、下载字节比对、删除通过。
- 数据集：transferId e8192982-5942-4d94-90d7-792676caaacd 最终 SUCCEEDED；Jupyter 回读 datasets/QA-20260916-dataset.txt 内容一致，已删除测试文件。
- 镜像：全新 8 KiB Docker save 归档上传通过，三台真实 Agent 下发均 SUCCEEDED，镜像清单证实存在；未创建容器。平台文件记录和本机测试镜像已删除。
- Agent/终端集成脚本契约、开发 smoke 静态脚本通过。

## 新确认问题及清理限制

- F05 多标签镜像删除失败：三台均复现。下发会形成原始标签 xkp/qa-20260916:latest 和平台 xkp/files/xkp-qa-20260916-image-...:latest，两者同 ID。DELETE inventory/images 正确提交后命令 FAILED/DOCKER_INVENTORY_FAILED，Docker 报 image is referenced in multiple repositories。DockerInventoryService 使用 image ID，Agent ImageRemove 未处理多标签。当前三台各残留一个测试镜像、两个标签（仅 17 字节测试层，无运行容器），未强制删除或修改代码。
- F06 错误响应泄露实现细节：sample/httpToBase64 JSON 提交非法 URL，返回 code=500，msg 包含完整 Java 堆栈；应使用校验错误及有限信息。
- T100 资源阻塞：向运行中 172.16.33.214:21500/api/v2/t100 提交现有 test.jpg，HTTP 200 但业务 code=1002，提示 A 卷 detection.tflite 未部署；未实际完成模型推理。

## 当前收尾状态

平台 TRAINING，课堂关闭；已完成返回实训后 24 环境 STOPPED 验证。为文件清理再启动的 test1 独立环境已停止。临时课程、资源目录、资源文件、Jupyter/VS Code 文件和数据集文件已清理。测试审计/命令/传输记录保留，未清除历史。远端测试镜像受 F05 阻塞残留，详见上文。

## 尚未完成/不能宣称通过

- 未覆盖每个页面所有按钮与边界组合；账户重置密码、破坏性清空、真实比赛答题评分、模板全部 CRUD、授权替换没有全部执行。
- 当前已结束比赛未重置排期/删除旧成绩，缺少不影响旧数据的完整参赛夹具。
- 大文件上传、28 GB 级推送耗时/断点/断网恢复、负载压力和 GPU 训练不被 8 KiB 镜像覆盖。
- 图像标注任务完整闭环、Jupyter 内核运算、所有内嵌工具保存/WebSocket 帧级证据未覆盖；独立窗口保存已通过。
- fake-agent 实训授权替换脚本未在独立可丢弃授权库执行；完整离线安装/升级/回滚没有在全新机器执行，静态检查不替代安装验收。

## 构建警告

Webpack 入口约 9.55 MiB，应用 JS 约 6.48 MiB，字体约 15.6 MiB，部分图片较大；未测实网耗时，不能等同已确认性能故障。


## 继续测试补充：内核、标注、全部课程资源

| 测试点 | 结果 | 证据与限制 |
|---|---|---|
| Jupyter 内核创建 | 成功 | POST /api/kernels 创建 python3 临时内核 |
| Jupyter WebSocket 执行 | 成功 | channels WebSocket execute_request 执行 sum(range(101))，收到 execute_reply status=ok 和 QA_KERNEL_RESULT 5050 |
| Jupyter 临时内核清理 | 成功 | DELETE /api/kernels/{本轮ID} 返回 204；未写 notebook |
| 图像标注任务创建/图片处理 | 成功 | API 创建临时任务 201，上传 test.jpg 202，任务 1 帧、1 job |
| 标注保存与回读 | 成功 | 矩形 [10,10,40,40] 保存 200，GET 坐标一致 |
| 标注修改与回读 | 成功 | 改为 [15,15,45,45] 后 GET 一致 |
| 标注临时任务清理 | 成功 | 两次临时任务均 DELETE 204，仅清理本轮创建对象 |
| 课程 PDF 资源可访问性 | 成功 | 全部 43 门课程、585 个关联资源，预览接口和文件请求均成功，585/585 具有 %PDF 文件头；Range 小范围读取，非全文视觉验收 |
| 嵌入相关响应头检查 | 未确定根因 | 标注 /tasks 无 X-Frame-Options/CSP；Jupyter /lab 的 CSP 为 frame-ancestors self *。不能据此认定嵌入全链路正常 |
| 本轮浏览器交互 | 环境阻塞 | 浏览器控制入口报 unsupported Codex auth method: apikey，无法进行新增鼠标绘制/页面内核操作；以上标注与内核结果是实际服务协议测试，不冒充浏览器验收 |

首次标注保存请求遗漏协议必填 group 返回 400；补齐 group=0 后保存/修改通过，归为测试请求错误，不登记产品缺陷。F02 保留为前轮浏览器观察，根因与其他浏览器复现仍待确认。


## 继续测试补充：账户、作业隔离、授权拒绝

以下为真实 HTTP 接口测试，非浏览器逐按钮测试。

| 测试点 | 结果 | 验证依据 |
|---|---|---|
| 临时学生账号创建与登录 | 成功 | 创建后使用生成凭据登录；凭据未记录 |
| 同名账号再次创建 | 成功（正确拒绝） | 第二次请求业务码非 200 |
| 账号禁用 | 成功 | 禁用后新登录拒绝，旧会话 user/me 返回 401 |
| 账号重新启用 | 成功 | 恢复启用后可重新登录 |
| 账号信息修改 | 成功 | 修改备注，列表回读一致 |
| 账号删除 | 成功 | 删除后列表中不存在 |
| 作业列表跨用户隔离 | 成功 | 第二临时用户列表不含 test1 上传的测试文件 |
| 跨用户作业下载 | 成功（正确拒绝） | 已知文件 ID 请求返回业务 404 |
| 跨用户作业删除 | 成功（正确拒绝） | 返回业务 404，原所有者文件仍存在 |
| 无效授权文件导入 | 成功（正确拒绝） | 上传空 JSON 的测试 .xkplic，返回业务 400；前后授权 ID、状态、期限、单位一致，未替换有效授权 |
| 学生管理接口权限 | 成功 | admin/users、admin/courses、admin/image-files、super-admin/container-templates、super-admin/license/diagnostics 均拒绝 403 |
| 普通管理员超管权限边界 | 成功 | super-admin/container-templates、super-admin/license/diagnostics 均拒绝 403 |

清理：本轮两个临时学生账号及作业测试文件已删除。授权失败审计记录保留。平台仍为 TRAINING，课堂关闭；未启动实训环境、未改源码。
浏览器控制再次检查仍返回 unsupported Codex auth method: apikey，页面交互与内嵌复测继续标为工具环境阻塞，不能当作平台失败。


## 2026-09-20 续测与上轮结果补录

- 浏览器控制重试仍报 unsupported Codex auth method: apikey；平台前端与后端 health 均 HTTP 200。以下均为接口结果，不替代逐按钮验收。
- 9 月 17 日模板新增、修改发布第二版本、版本查询、停用、删除通过；临时版本全部清理。
- 9 月 17 日 B 卷单选、多选、填空、实操题新增/修改/关键词查询、负分值拒绝通过；临时题目全部删除，原题数恢复。
- 评分详情读取通过，学生越权访问返回 403；不存在的评分/导出记录被拒绝。当前 A 卷无本轮可导出提交，导出返回 400；B 卷非当前卷被拒绝。尚未完成交卷评分导出全闭环。
- 9 月 20 日课程章节空名称拒绝、创建/回读、修改名称与工具、跨课程 PUT/DELETE 拒绝、学生管理写入 403、发布后学生可见、删除通过。两门临时课程已清理。
- 首次章节脚本错误地假定创建课程响应顶层有 courseId，产生 KeyError；随后按列表检索得到本轮 ID 完成测试及清理，非产品问题。
- 课程报告保存与 GET 回读通过。但本轮发生测试操作失误：test1 在课程 889b8ab9-27e2-45fe-99f8-8966a8f34228 的报告被写为 QA-REPORT 测试文本，未在写入前保存原内容，原内容是否为空未知，尚未恢复。不能宣称所有测试数据已清理。应先从可靠备份/审计确认原值，禁止凭猜测覆盖。
- 学习进度请求因测试参数格式不符返回 400（学习进度类型无效），不计产品失败，正确请求及回读尚待测。

状态：测试继续进行，之前“本轮结案”仅对应 9 月 16 日阶段；并非所有功能验收完成。


## 2026-09-20 报告恢复和学习进度补测

- 报告原文恢复完成：MySQL binlog.000042 的 position 1064116 存在 UPDATE_ROWS_EVENT，包含报告 71836ac2-f28f-4e77-8933-c21372d032f1 的完整修改前/后行。解析覆盖事件字段与长度，CRC32 校验通过；修改后文本哈希与当前报告一致后，使用正常报告 API 恢复修改前内容，GET 回读字节完全一致。原文未写入测试报告；临时日志副本和恢复内容文件已删除。更新时间因正常 API 保存而更新，未回写历史时间。
- 学习进度正确协议已验证：独立临时账号 PAGE 3/10 返回 IN_PROGRESS、30%；更新 10/10 返回 COMPLETED、100%；回读同资源只有一条记录。未修改 test1 的学习进度。
- 新问题 F07：删除该临时账号后，course_resource_progress 仍有该用户的一条记录；账号删除流程缺少进度清理。已按本轮 user_id=21 与唯一 progress_id 精确删除该测试行（1 行），账号删除已完成。
- 平台复核：TRAINING，24 个环境 STOPPED；三台各两个 QA 镜像标签仍存在，原多标签删除缺陷未修复。
- MariaDB 日志工具不支持 MySQL 字符集 255，改用按 MySQL ROW 格式解析目标事件并校验 CRC；此工具不兼容不是产品缺陷。

此前报告中的“误写报告尚未恢复”现已解决；其他明确未覆盖项仍未完成，不改为通过。


## 2026-09-20 续测：实训权限与草稿章节可见性

- 临时无环境学生账号的实训列表为空：通过。
- 使用该账号启动他人环境或随机不存在环境均返回业务 400；前后所有既有环境状态一致：通过。
- 随机不存在的资源预览及课程报告接口返回业务 400：通过。
- 临时账号已删除，列表确认不存在。
- 新问题 F08：草稿课程列表隐藏，但章节接口缺少同等可见性校验。复现：管理员新建未发布课程并添加章节；test1 的 GET /user/courses 不包含该课程；随后 GET /user/courses/{草稿课程ID}/chapters 返回 200 且包含章节 ID、名称和配置。应核对预期权限并统一课程发布状态校验。源码 UserCourseController.chapters 只检查用户角色后直接 selectByCourse，未检查课程 enabled。临时课程和章节均已清理。未读取非测试草稿内容。
- 本轮未重复执行 Java/Agent 全量测试，未修改源码；前一轮重跑 Vue 49 脚本均通过，不能据此消除 F08。


## 继续补测记录

- 草稿资源直接访问补测未形成新结论：测试请求使用了服务不支持的 resourceType=BOOK，课程资源添加接口正确返回 400；临时课程已删除。该次 400 是测试参数错误，不计产品缺陷。
- 已确认的 F08 仍仅针对 `/user/courses/{courseId}/chapters`：草稿课程章节可按 ID读取；其他草稿资源/报告入口不在本次错误请求中判定。


## 继续补测：授权请求与当前边界

- 授权请求创建接口成功返回 requestId/filename；正确使用前端 `/api` 入口下载了 407 字节 `.xkpreq`，内容包含 requestId。
- 非法 requestId（长度/字符边界）被拒绝；错误直连后端端口导致一次 404，归为测试地址错误。
- 更正：407/409 是首次下载内容的字节数，不是 HTTP 状态码；已观察到重复下载 HTTP 400，符合一次性下载语义。撤回“400/409 状态差异”疑点，授权请求审计记录保留。
- 当前平台仍 TRAINING、课堂 inactive、24 个环境 STOPPED。浏览器控制认证错误仍存在。


## 2026-09-22 F05 远端修复验收

三台 Agent 已逐台升级至 0.2.35，并确认 systemd active/running、二进制哈希、平台在线心跳。旧二进制及配置备份保留。

通过正常平台 API 删除此前遗留的双标签 QA 镜像，三条命令均 SUCCEEDED：
- xt-01：3fe68fd0-fd1d-46e7-9be7-8c36eed2aac7
- xt-02：4c945ab1-79d0-4018-a188-ea7b6455ded5
- xt-03：914a4dc6-bfae-4c0b-b9bd-3070c079a964

SSH 实查镜像完整 ID 与两个标签均不存在；心跳刷新后平台清单同步移除。每台删除前后全部容器 ID 和状态一致（分别 24、24、0 个）。三台同版本再次升级均拒绝。学生可读 43 门课程，前后端健康访问 200。

这验证的是正常无外部镜像写入情况下的远端完整删除，不证明外部并发 tag/load 时原子性。F02/T100 继续待定；当前 5.1.0 离线包未更新，不包含本轮新修复。
