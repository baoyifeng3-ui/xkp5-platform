# 教学实训比赛平台 UI 重设计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有业务接口、路由和权限的前提下，落地 B2 共享平台外壳与 A 参赛工作台，并完成管理首页首个迁移页面。

**Architecture:** 保留 `PlatformShell` 作为管理端、学习端和运维端的共享壳，新增只负责布局和导航的 `CompetitionShell` 给参赛端使用。颜色、字号、间距和状态规则集中在现有主题 CSS 与主题服务中，业务页面继续通过现有 API、Vuex 和事件接入。

**Tech Stack:** Vue 2、Vue Router 3、Vuex 3、Element UI、现有 Node 断言契约测试、Vue CLI 生产构建。

---

### Task 1: 锁定共享设计 token 与回归契约

**Files:**
- Modify: `vue/src/assets/style/platform-theme.css`
- Modify: `vue/src/assets/style/platform-shell.css`
- Modify: `vue/src/services/platformTheme.js`
- Test: `vue/scripts/test-platform-ui-contract.js`

- [ ] **Step 1: Write the failing contract**

在 `test-platform-ui-contract.js` 增加断言，要求主题包含 B2 的冷灰蓝背景、学院蓝默认值、4px 间距、8px 卡片圆角、固定字号变量、折叠侧栏变量和 `prefers-reduced-motion` 规则：

```js
assert.match(theme, /--ui-page:\s*#f3f6fa/)
assert.match(theme, /--ui-primary:\s*var\(--platform-theme-color,\s*#386bdc\)/)
assert.match(theme, /--ui-space-unit:\s*4px/)
assert.match(theme, /--ui-card-radius:\s*8px/)
assert.match(theme, /--ui-sidebar-collapsed-width:\s*76px/)
assert.match(shellCss, /prefers-reduced-motion\s*:\s*reduce/)
```

- [ ] **Step 2: Run the contract to verify it fails**

Run: `node scripts/test-platform-ui-contract.js`  
Expected: FAIL because the new token names and B2 default color are not present.

- [ ] **Step 3: Implement the minimum token layer**

在 `platform-theme.css` 的 `:root` 增加以下语义变量，并让现有 `--ui-*` 变量引用它们，保留 `--platform-theme-color` 的运行时覆盖：

```css
--ui-page: #f3f6fa;
--ui-primary: var(--platform-theme-color, #386bdc);
--ui-primary-strong: #2d5bb5;
--ui-primary-soft: #e8efff;
--ui-space-unit: 4px;
--ui-card-radius: 8px;
--ui-control-radius: 6px;
--ui-sidebar-collapsed-width: 76px;
--ui-type-xs: 12px;
--ui-type-sm: 14px;
--ui-type-md: 16px;
--ui-type-lg: 20px;
--ui-type-xl: 24px;
```

将 `.shell-main`、模块页面和普通表面背景改为 `var(--ui-page)`，将普通卡片圆角引用 `var(--ui-card-radius)`。在文件末尾加入：

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: .01ms !important; transition-duration: .01ms !important; }
}
```

在 `platformTheme.js` 将 `DEFAULT_THEME_COLOR` 改为 `#386bdc`，并保持 `normalizeThemeColor`、`mixHex` 和管理员自定义主题色逻辑不变。

- [ ] **Step 4: Run the contract to verify it passes**

Run: `node scripts/test-platform-ui-contract.js`  
Expected: PASS，且现有平台主题服务相关断言继续通过。

- [ ] **Step 5: Commit**

```bash
git add vue/src/assets/style/platform-theme.css vue/src/assets/style/platform-shell.css vue/src/services/platformTheme.js vue/scripts/test-platform-ui-contract.js
git commit -m "feat: establish B2 platform design tokens"
```

### Task 2: 为 PlatformShell 增加可持久化折叠侧栏

**Files:**
- Modify: `vue/src/components/PlatformShell.vue`
- Modify: `vue/src/assets/style/platform-shell.css`
- Test: `vue/scripts/test-platform-ui-contract.js`

- [ ] **Step 1: Write the failing contract**

增加以下断言：

```js
assert.match(shell, /sidebarCollapsed/)
assert.match(shell, /localStorage/)
assert.match(shell, /切换导航栏/)
assert.match(shellCss, /shell-sidebar\.is-collapsed/)
assert.match(shellCss, /--ui-sidebar-collapsed-width/)
```

- [ ] **Step 2: Run the contract to verify it fails**

Run: `node scripts/test-platform-ui-contract.js`  
Expected: FAIL because `PlatformShell` has only mobile drawer state and no desktop collapse state.

- [ ] **Step 3: Implement collapse state without changing route behavior**

在 `PlatformShell.vue`：

```js
data () {
  return {
    drawerOpen: false,
    sidebarCollapsed: localStorage.getItem('platform-sidebar-collapsed') === '1',
    mobileViewport: false,
    drawerMedia: null,
    searchQuery: ''
  }
},
methods: {
  toggleSidebar () {
    if (this.mobileViewport) return this.openDrawer()
    this.sidebarCollapsed = !this.sidebarCollapsed
    localStorage.setItem('platform-sidebar-collapsed', this.sidebarCollapsed ? '1' : '0')
  }
}
```

将根节点 class 改为 `['platform-shell', { 'sidebar-collapsed': sidebarCollapsed }]`，在侧栏品牌区增加 44px 可点击按钮，`aria-label="切换导航栏"`，调用 `toggleSidebar`。按钮只在桌面显示，移动端继续使用抽屉触发器。

在 `platform-shell.css` 添加：

```css
.platform-shell.sidebar-collapsed { grid-template-columns: var(--ui-sidebar-collapsed-width) minmax(0, 1fr); }
.platform-shell.sidebar-collapsed .shell-sidebar { width: var(--ui-sidebar-collapsed-width); }
.platform-shell.sidebar-collapsed .shell-brand-copy,
.platform-shell.sidebar-collapsed .menu-text { display: none; }
.platform-shell.sidebar-collapsed .shell-brand,
.platform-shell.sidebar-collapsed .el-menu-item { justify-content: center; }
@media (max-width: 720px) {
  .platform-shell.sidebar-collapsed { display: block; }
  .platform-shell.sidebar-collapsed .shell-sidebar { width: min(86vw, 300px); }
}
```

所有图标导航项补充 `el-tooltip` 或等价可读提示，避免折叠后只能依赖图标猜含义。

- [ ] **Step 4: Run navigation and UI contracts**

Run: `node scripts/test-role-navigation.js && node scripts/test-platform-ui-contract.js`  
Expected: PASS；路由导航和移动端抽屉断言不得回归。

- [ ] **Step 5: Commit**

```bash
git add vue/src/components/PlatformShell.vue vue/src/assets/style/platform-shell.css vue/scripts/test-platform-ui-contract.js
git commit -m "feat: add persistent platform sidebar collapse"
```

### Task 3: 创建独立 CompetitionShell

**Files:**
- Create: `vue/src/components/CompetitionShell.vue`
- Modify: `vue/src/views/Layout.vue`
- Modify: `vue/src/assets/style/layout.css`
- Test: `vue/scripts/test-competition-shell-contract.js`

- [ ] **Step 1: Write the failing contract**

新建 `test-competition-shell-contract.js`：

```js
const fs = require('fs')
const assert = require('assert')
const shell = fs.readFileSync('src/components/CompetitionShell.vue', 'utf8')
const layout = fs.readFileSync('src/views/Layout.vue', 'utf8')
assert.match(shell, /competition-clock/)
assert.match(shell, /当前赛卷/)
assert.match(shell, /赛规赛程/)
assert.match(shell, /实操环境/)
assert.match(shell, /成果验证/)
assert.match(shell, /aria-live/)
assert.match(shell, /prefers-reduced-motion/)
assert.match(layout, /CompetitionShell/)
console.log('competition shell contract passed')
```

在 `package.json` 增加脚本：`"test:competition-shell": "node scripts/test-competition-shell-contract.js"`。

- [ ] **Step 2: Run the contract to verify it fails**

Run: `node scripts/test-competition-shell-contract.js`  
Expected: FAIL with missing `CompetitionShell.vue`.

- [ ] **Step 3: Implement the layout-only shell**

`CompetitionShell.vue` 只接收 `brandName`、`paperLabel`、`remainingText`、`userName`、`activeKey` 和 `tabs`，渲染：

```vue
<header class="competition-topbar">
  <div class="competition-brand"><span class="competition-mark">竞</span><strong>{{ brandName }}</strong></div>
  <div class="competition-status" aria-live="polite"><small>剩余时间</small><strong>{{ remainingText }}</strong><span>{{ paperLabel }}</span></div>
  <div class="competition-user">{{ userName }}</div>
</header>
<nav class="competition-tabs" aria-label="比赛功能">
  <button v-for="tab in tabs" :key="tab.key" type="button" :class="{ active: activeKey === tab.key }" @click="$emit('select', tab)">{{ tab.label }}</button>
</nav>
<main class="competition-content"><slot /></main>
```

在 `Layout.vue` 保留现有倒计时、赛卷同步、权限和路由逻辑，只把 `PlatformShell` 替换为 `CompetitionShell`，并将现有 `router-view` 放进其默认 slot。比赛导航事件继续调用现有 `doRouter` / `openAdmin`，不得复制请求。

在 `layout.css` 增加三栏工作区、顶部状态栏、页签焦点态、表面和移动端单栏 token，比赛端只使用现有 `--platform-theme-color` 派生色。

- [ ] **Step 4: Run contract and build**

Run: `node scripts/test-competition-shell-contract.js && npm run build`  
Expected: contract PASS and production build complete.

- [ ] **Step 5: Commit**

```bash
git add vue/src/components/CompetitionShell.vue vue/src/views/Layout.vue vue/src/assets/style/layout.css vue/scripts/test-competition-shell-contract.js vue/package.json
git commit -m "feat: add dedicated competition shell"
```

### Task 4: 迁移管理首页到 B2 页面结构

**Files:**
- Modify: `vue/src/views/management/ManagementHome.vue`
- Modify: `vue/src/assets/style/platform-shell.css`
- Test: `vue/scripts/test-platform-ui-contract.js`

- [ ] **Step 1: Write the failing contract**

增加管理首页的结构断言：

```js
const home = read('src/views/management/ManagementHome.vue')
assert.match(home, /平台账号|账号总数/)
assert.match(home, /已发布课程/)
assert.match(home, /运行中环境/)
assert.match(home, /待处理事项/)
assert.match(home, /近七日学习活跃度/)
assert.match(home, /快捷入口/)
assert.match(home, /loading|skeleton/i)
assert.match(home, /empty|暂无/i)
```

- [ ] **Step 2: Run the contract to verify it fails**

Run: `node scripts/test-platform-ui-contract.js`  
Expected: FAIL until the home page exposes the agreed KPI and state regions.

- [ ] **Step 3: Implement the first migrated page using existing data**

保留 `ManagementHome.vue` 当前数据请求和操作方法，只调整模板为：页面标题区、四个 KPI、主趋势区、快捷入口区、最近动态区。所有加载状态保留固定高度的骨架；空数据使用图标、原因和下一步按钮；错误状态使用可重试提示。数字使用 `font-variant-numeric: tabular-nums`，卡片使用 8px 圆角和 `var(--ui-page)` 背景。

不得引入新图表依赖；已有统计接口返回什么就展示什么，没有真实数据的指标不编造百分比。

- [ ] **Step 4: Run focused checks**

Run: `node scripts/test-dashboard-overview-contract.js && node scripts/test-platform-ui-contract.js`  
Expected: PASS，管理首页现有业务操作和空/加载/错误状态均存在。

- [ ] **Step 5: Commit**

```bash
git add vue/src/views/management/ManagementHome.vue vue/src/assets/style/platform-shell.css vue/scripts/test-platform-ui-contract.js
git commit -m "feat: migrate management home to B2 system"
```

### Task 5: 接入 A 赛场控制台内容区

**Files:**
- Modify: `vue/src/components/Question.vue`
- Modify: `vue/src/views/Detect.vue`
- Modify: `vue/src/views/Match.vue`
- Modify: `vue/src/views/Matchs.vue`
- Modify: `vue/src/assets/style/layout.css`
- Test: `vue/scripts/test-competition-shell-contract.js`

- [ ] **Step 1: Write the failing contract**

增加断言：

```js
const question = read('src/components/Question.vue')
const detect = read('src/views/Detect.vue')
const layout = read('src/views/Layout.vue')
assert.match(layout, /任务|模块|完成度/)
assert.match(question, /提交试卷/)
assert.match(question, /答案自动保存|submissionState/)
assert.match(detect, /成果验证/)
assert.match(layout, /比赛结束|FINISHED/)
```

- [ ] **Step 2: Run the contract to verify it fails**

Run: `node scripts/test-competition-shell-contract.js`  
Expected: FAIL because the new shell content regions are not yet exposed.

- [ ] **Step 3: Implement the shared content frame**

在 `Layout.vue` 的比赛 shell slot 外层增加 `competition-workspace`，提供左侧任务进度、中央 `<router-view>` 和右侧状态槽位。右侧状态只读取现有 Vuex/组件状态；不新增第二套提交或计时请求。`Question.vue` 保留现有提交确认和锁定逻辑，在现有题目底部补充保存状态；`Detect.vue` 保留检测 API，只补充“未同步/检测中/成功/失败”可见状态。

实操页沿用现有 `/competition-practical`，通过页签进入，不改变其组件和路由名称。所有提交按钮在未满足条件时显示原因，禁止只用 disabled 状态。

- [ ] **Step 4: Run competition regression checks**

Run: `node scripts/test-competition-mode-contract.js && node scripts/test-paper-selection-contract.js && node scripts/test-grading-refresh-contract.js && node scripts/test-competition-shell-contract.js`  
Expected: all PASS，比赛计时、赛卷选择、提交和判分刷新逻辑不回归。

- [ ] **Step 5: Commit**

```bash
git add vue/src/views/Question.vue vue/src/views/Detect.vue vue/src/views/Match.vue vue/src/views/Matchs.vue vue/src/views/Layout.vue vue/src/assets/style/layout.css vue/scripts/test-competition-shell-contract.js
git commit -m "feat: organize competition workspace content"
```

### Task 6: 响应式、无障碍和最终验收

**Files:**
- Modify: `vue/src/assets/style/platform-shell.css`
- Modify: `vue/src/assets/style/layout.css`
- Modify: `vue/src/components/PlatformShell.vue`
- Modify: `vue/src/components/CompetitionShell.vue`
- Test: `vue/scripts/test-platform-ui-contract.js`
- Test: `vue/scripts/test-competition-shell-contract.js`

- [ ] **Step 1: Write the failing responsive/a11y contract**

增加断言：

```js
assert.match(shellCss, /overflow-x:\s*clip/)
assert.match(shellCss, /focus-visible/)
assert.match(shellCss, /min-width:\s*44px|min-height:\s*44px/)
assert.match(layoutCss, /prefers-reduced-motion\s*:\s*reduce/)
```

- [ ] **Step 2: Run the contracts to verify missing gates**

Run: `node scripts/test-platform-ui-contract.js && node scripts/test-competition-shell-contract.js`  
Expected: FAIL for any missing responsive or focus rule.

- [ ] **Step 3: Add the smallest responsive and accessibility fixes**

在共享样式中：

- `html, body` 使用 `overflow-x: clip`，不使用 `overflow-x: hidden`。
- 所有按钮和页签最小高度 44px。
- 所有自定义按钮加入 `:focus-visible` 描边。
- 桌面三栏在 1024px 以下收缩；在 720px 以下改为单栏，底部操作固定但不遮挡内容。
- 比赛倒计时不逐秒触发 `aria-live` 播报，只有状态变化使用 `aria-live="polite"`。
- `prefers-reduced-motion` 下关闭抽屉和页签过渡。

- [ ] **Step 4: Run full frontend verification**

Run:

```bash
npm run test:platform-ui
npm run test:competition-shell
npm run test:navigation
npm run test:competition-mode
npm run test:course-platform
npm run build
```

Expected: all commands exit 0. Build warnings about existing asset size may remain, but no new compile error or runtime import error may appear.

- [ ] **Step 5: Check the dev server and viewport matrix**

Run: `npm run serve -- --port 19142`  
Check `http://localhost:19142/` at 1440px, 1920px, 1024px, 768px, 414px and 375px. Verify no page-level horizontal scrollbar, sidebar collapse, competition timer stability, keyboard focus, empty/loading/error states and submit confirmation.

- [ ] **Step 6: Commit final integration**

```bash
git add vue/src/assets/style/platform-shell.css vue/src/assets/style/layout.css vue/src/components/PlatformShell.vue vue/src/components/CompetitionShell.vue vue/scripts/test-platform-ui-contract.js vue/scripts/test-competition-shell-contract.js
git commit -m "test: verify redesigned platform and competition UI"
```

## Self-review

- 设计规范的 B2 颜色、固定字号、折叠侧栏、共享组件、比赛 A 外壳、状态覆盖、响应式和验证要求均有对应任务。
- 第一阶段不包含暗色模式、后端改造、菜单信息架构变更或装饰性视觉引擎。
- 每个修改任务均有失败契约、最小实现、通过验证和提交步骤。
- 业务请求继续留在现有页面/服务中，外壳只负责布局、导航和状态展示。
