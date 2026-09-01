# 比赛管理重复界面清理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除比赛端“比赛实操”菜单和比赛管理页面的重复内层外壳，同时保留全部关键业务按钮。

**Architecture:** 从 `Layout.vue` 删除菜单项生成逻辑但保留路由；从 `Admin.vue`、`AdminGrading.vue` 删除重复模板节点，并收紧现有样式。接口、状态和业务方法不变。

**Tech Stack:** Vue 2、Element UI、Node assert 契约测试

---

### Task 1: 删除与保留契约

**Files:**
- Create: `vue/scripts/test-competition-admin-cleanup-contract.js`
- Test: `vue/scripts/test-competition-admin-cleanup-contract.js`

- [ ] **Step 1: 写入失败契约**

```js
const assert = require('assert')
const fs = require('fs')
const read = file => fs.readFileSync(file, 'utf8')
const layout = read('src/views/Layout.vue')
const admin = read('src/views/Admin.vue')
const grading = read('src/components/AdminGrading.vue')
const router = read('src/router/index.js')

assert.doesNotMatch(layout, /participantItems\.push\([^\n]*比赛实操/)
assert.match(router, /path:\s*["']\/competition-practical["']/)
assert.doesNotMatch(admin, /<header class="admin-utility">/)
assert.doesNotMatch(admin, /<h1>比赛控制<\/h1>|<h1>赛规赛程<\/h1>|<h1>试卷题目<\/h1>/)
;['loadPublicState', 'saveCompetitionContent', 'clearSubjectAnswers', 'clearAllSubjects', 'openCompetitionHelp', 'openSubjectCreateDialog'].forEach(name => assert.match(admin, new RegExp(`@click="${name}`)))
assert.doesNotMatch(grading, /<h1>试卷判分<\/h1>/)
assert.match(grading, /@click="createExport"/)
assert.match(grading, /@click="openExportHistory"/)
```

- [ ] **Step 2: 运行并确认失败**

Run: `cd vue && node scripts/test-competition-admin-cleanup-contract.js`

Expected: FAIL，首先命中“比赛实操”菜单仍存在。

### Task 2: 清理比赛菜单与管理外壳

**Files:**
- Modify: `vue/src/views/Layout.vue`
- Modify: `vue/src/views/Admin.vue`
- Modify: `vue/src/components/AdminGrading.vue`
- Test: `vue/scripts/test-competition-admin-cleanup-contract.js`

- [ ] **Step 1: 删除比赛实操菜单生成逻辑**

删除 `participantItems.push(...)` 条件块，保留 `/competition-practical` 路由。

- [ ] **Step 2: 删除重复管理外壳**

从 `Admin.vue` 删除 `admin-utility`，并删除各 `workspace-heading` 内仅包含 `h1/p` 的标题节点。设置页无关键按钮时删除空标题栏。

- [ ] **Step 3: 保留关键按钮并收紧样式**

将管理内容顶部内边距由 `90px 28px 34px` 改为 `0`；`workspace-heading` 改为靠右、底部间距 `12px`。移动端不再预留 `84px` 内层顶栏高度。

从 `AdminGrading.vue` 删除标题文字节点，保留导出按钮，并将 `grading-heading` 改为靠右紧凑操作栏。

- [ ] **Step 4: 运行契约**

Run: `cd vue && node scripts/test-competition-admin-cleanup-contract.js`

Expected: `competition admin cleanup contract passed`

### Task 3: 构建验证

**Files:**
- Verify: `vue/src/views/Layout.vue`
- Verify: `vue/src/views/Admin.vue`
- Verify: `vue/src/components/AdminGrading.vue`

- [ ] **Step 1: 运行现有导航和比赛契约**

Run: `cd vue && node scripts/test-role-navigation.js && node scripts/test-competition-mode-contract.js`

Expected: 两个脚本均通过；如旧契约仍要求比赛实操菜单，则同步为“路由保留、菜单删除”。

- [ ] **Step 2: 生产构建**

Run: `cd vue && npm run build`

Expected: BUILD SUCCESS，仅允许已有资源体积警告。

- [ ] **Step 3: 服务检查**

Run: `Invoke-WebRequest -UseBasicParsing http://localhost:19142/`

Expected: HTTP 200。
