# 导航、品牌设置与课程批量导入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复侧边导航布局和展开行为，增加可持久化 Logo/英文副标题并修复背景显示，将指定目录的 43 门课程和 585 个 PDF 章节安全导入平台。

**Architecture:** 导航和品牌继续复用共享 `PlatformShell`、Vuex 设置状态与 `SystemSettingService`。图片继续走现有 FastDFS 和 `/files/**` 代理。课程导入使用独立 Node 运维脚本调用现有管理 API，先 dry-run，再幂等正式执行。

**Tech Stack:** Vue 2、Element UI、Vuex、Spring Boot、MyBatis Plus、FastDFS、Node.js 标准库。

---

### Task 1: 导航唯一展开与对齐

**Files:**
- Modify: `vue/scripts/test-platform-ui-contract.js`
- Modify: `vue/src/components/PlatformShell.vue`
- Modify: `vue/src/assets/style/platform-shell.css`

- [ ] **Step 1: 写失败契约测试**

增加断言，要求共享菜单包含 `unique-opened`，并要求子菜单项使用统一的 `.shell-submenu-item` 类。

- [ ] **Step 2: 验证测试失败**

Run: `node scripts/test-platform-ui-contract.js`

Expected: FAIL，缺少唯一展开或子菜单样式。

- [ ] **Step 3: 实现最小导航修改**

给 `<el-menu>` 增加 `unique-opened`；子菜单项增加专用类；统一一级菜单、submenu title 与子菜单的高度、边距和文字起始位置。

- [ ] **Step 4: 运行契约测试和生产构建**

Run: `node scripts/test-platform-ui-contract.js && npm run build`

Expected: PASS，构建退出码 0。

### Task 2: 后端品牌设置字段与文件代理

**Files:**
- Create: `java/match-mgr/src/main/resources/db/migration/V51__platform_brand_assets.sql`
- Modify: `java/match-mgr/src/main/java/com/match/dto/PlatformSettingsRequest.java`
- Modify: `java/match-mgr/src/main/java/com/match/service/impl/SystemSettingService.java`
- Modify: `java/match-mgr/src/main/java/com/match/controller/CompetitionController.java`
- Test: `java/match-mgr/src/test/java/com/match/service/impl/SystemSettingServiceTest.java`

- [ ] **Step 1: 写失败服务测试**

断言平台设置返回并保存 `platformLogoUrl`、`loginEnglishSubtitle`，且英文副标题长度和空值按现有登录文案规则处理。

- [ ] **Step 2: 验证测试失败**

Run: `mvn -q -Dtest=SystemSettingServiceTest test`

Expected: FAIL，字段尚不存在。

- [ ] **Step 3: 实现品牌字段和 Logo 上传接口**

沿用 `system_setting` 键值存储，增加默认 Logo 空值和默认英文副标题。新增 Logo 上传端点，限制 JPEG/PNG/WebP/SVG、2 MiB，返回 `/files/...` URL。背景上传继续返回同一代理格式。

- [ ] **Step 4: 验证服务测试和 HTTP 图片读取**

Run: `mvn -q -Dtest=SystemSettingServiceTest,FileProxyControllerTest test`

Expected: PASS；上传后的 URL 返回 HTTP 200 和正确 MIME。

### Task 3: Logo、英文副标题与背景预览前端

**Files:**
- Modify: `vue/src/store/modules/Match.js`
- Modify: `vue/src/api/Match.js`
- Modify: `vue/src/views/management/PlatformSettings.vue`
- Modify: `vue/src/components/PlatformShell.vue`
- Modify: `vue/src/layouts/ManagementShell.vue`
- Modify: `vue/src/layouts/OperationsShell.vue`
- Modify: `vue/src/layouts/NormalUserShell.vue`
- Modify: `vue/src/views/Login.vue`
- Modify: `vue/scripts/test-platform-ui-contract.js`

- [ ] **Step 1: 扩充失败契约测试**

断言设置页包含 Logo 上传和英文副标题；登录页不再硬编码英文；共享 Shell 接收 Logo URL。

- [ ] **Step 2: 验证测试失败**

Run: `node scripts/test-platform-ui-contract.js`

Expected: FAIL。

- [ ] **Step 3: 实现状态、表单和渲染**

Vuex 保存两个新字段。设置页支持 Logo 上传、预览、移除和背景图片错误兜底。登录页和共享侧栏优先显示配置 Logo；英文副标题读取配置值。

- [ ] **Step 4: 验证契约与构建**

Run: `node scripts/test-platform-ui-contract.js && npm run build`

Expected: PASS。

### Task 4: 幂等课程导入工具

**Files:**
- Create: `tools/import-courses.mjs`
- Create: `tools/import-courses.test.mjs`

- [ ] **Step 1: 写失败脚本测试**

使用临时目录验证：去除类别和课程编号、自然排序章节、仅识别 PDF、重复课程跳过且缺失章节补录。

- [ ] **Step 2: 验证测试失败**

Run: `node --test tools/import-courses.test.mjs`

Expected: FAIL，导入模块不存在。

- [ ] **Step 3: 实现 dry-run 与正式导入**

脚本参数：

```text
--root <目录> --base-url http://localhost:19243 --username admin --password admin [--execute]
```

默认只输出 9 类、43 门、585 个 PDF 的清单。`--execute` 登录后调用课程、章节、文件上传和资源绑定 API。逐文件记录结果，失败不终止整个批次。

- [ ] **Step 4: 运行脚本测试与 dry-run**

Run: `node --test tools/import-courses.test.mjs`

Run: `node tools/import-courses.mjs --root "C:\新建文件夹 (5)\文件\产品\人工智能\安装部署\MGR9000\download\电子书"`

Expected: 9 类、43 门、585 PDF、无写操作。

### Task 5: 部署、导入与抽查

**Files:**
- Runtime artifact only; no additional source file.

- [ ] **Step 1: 构建并部署后端**

用现有 Maven 构建容器生成 JAR，停止 `xkp5-current-java` 后替换 `/app/app.jar` 并启动。

- [ ] **Step 2: 正式执行课程导入**

Run: `node tools/import-courses.mjs ... --execute`

Expected: 输出成功、跳过、失败统计；失败项保留具体路径。

- [ ] **Step 3: 接口核对**

管理员课程接口应新增 43 门；普通用户接口可见已启用课程；章节和电子书资源总数与成功导入 PDF 数一致。

- [ ] **Step 4: 浏览器抽查**

检查管理首页菜单对齐与唯一展开；设置 Logo、英文副标题和背景后刷新登录页；抽查数学与统计、人工智能核心、大数据核心三类课程的章节与 PDF 预览。

- [ ] **Step 5: 最终验证**

Run: `npm run build`

Run: `mvn -q test`（若仓库既有无关长时测试阻塞，至少运行本次涉及的服务和控制器测试并明确记录）。

Expected: 本次相关测试与真实接口验证通过。
