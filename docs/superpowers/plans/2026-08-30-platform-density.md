# 平台页面比例优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 减少全平台页面空白，让数据、卡片、表格和实训工作区合理占满可用屏幕。

**Architecture:** 以 `platform-shell.css` 的公共内容比例为主，不逐页复制间距。课程卡片使用响应式网格；仅移除镜像仓库、授权诊断和平台授权的数据页面最大宽度，表单页保留受控宽度。

**Tech Stack:** Vue 2、Element UI、CSS Grid、现有 Node 契约脚本。

---

### Task 1: 公共比例契约

**Files:**
- Modify: `vue/scripts/test-platform-ui-contract.js`
- Test: `vue/scripts/test-platform-ui-contract.js`

- [ ] 增加断言：`platform-shell.css` 必须包含桌面 `padding: 14px 16px 18px`，移动端仍为 `12px`；`PlatformShell.vue` 面包屑最小高度为 `32px`。
- [ ] 增加断言：课程管理和课程平台网格包含 `repeat(auto-fit, minmax(`。
- [ ] 运行 `cd vue && node scripts/test-platform-ui-contract.js`，确认因旧比例失败。

### Task 2: 收紧公共内容比例

**Files:**
- Modify: `vue/src/assets/style/platform-shell.css`
- Modify: `vue/src/components/PlatformShell.vue`
- Test: `vue/scripts/test-platform-ui-contract.js`

- [ ] 将桌面 `.shell-main` 调整为 `padding: 14px 16px 18px`。
- [ ] 将 `.shell-breadcrumb` 调整为 `min-height: 32px; margin: -2px 0 8px`。
- [ ] 将 `.module-page` 的视口高度扣减值从 `46px` 收紧为 `32px`。
- [ ] 保持移动端 `.shell-main { padding: 12px; }` 和现有触控尺寸。
- [ ] 运行公共 UI 契约，确认公共比例断言通过。

### Task 3: 响应式卡片与数据页宽度

**Files:**
- Modify: `vue/src/views/management/CourseManagement.vue`
- Modify: `vue/src/views/user/CoursePlatform.vue`
- Modify: `vue/src/views/operations/ImageRegistryView.vue`
- Modify: `vue/src/views/operations/LicenseDiagnostics.vue`
- Modify: `vue/src/views/management/PlatformLicense.vue`
- Test: `vue/scripts/test-platform-ui-contract.js`
- Test: `vue/scripts/test-course-platform-contract.js`

- [ ] 将管理员课程网格改为 `repeat(auto-fit, minmax(280px, 1fr))`。
- [ ] 将普通用户课程网格改为 `repeat(auto-fit, minmax(280px, 1fr))`。
- [ ] 移除镜像仓库 `max-width: 1400px`、授权诊断 `max-width: 1180px` 和平台授权 `max-width: 1120px`。
- [ ] 保留平台设置 `.settings-form` 最大宽度，并由 `680px` 扩大到 `840px`。
- [ ] 运行课程契约与公共 UI 契约。

### Task 4: 多尺寸回归

**Files:**
- Runtime only.

- [ ] 运行 `cd vue && node scripts/test-platform-ui-contract.js && node scripts/test-course-platform-contract.js && npm run build`。
- [ ] 在 1440×900 检查主页、课程管理、资源管理、实训管理、镜像仓库和平台设置。
- [ ] 在 1024×768 检查卡片自动换列、表格不横向溢出。
- [ ] 在 390px 宽度检查面包屑、操作按钮和单列卡片不重叠。
- [ ] 恢复浏览器临时视口并保留 `http://localhost:19142` 测试页面。
