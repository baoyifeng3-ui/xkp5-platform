# XKP5.0 Platform UI Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh the entire XKP5.0 web interface with the approved reference-inspired visual system while preserving role isolation, making competition and platform settings first-level administrator destinations, and providing an authentic read-only participant preview.

**Architecture:** Keep the existing Vue 2 routes, APIs, and role-specific page components, but introduce shared shell primitives and design tokens so every role uses one visual language. Navigation and preview policy remain centralized in `roleNavigation.js` and a new preview-policy helper; authoritative platform mode continues to come from the existing backend API, while participant login routing continues to use the existing session generation guard.

**Tech Stack:** Vue 2.6, Vue Router 3, Vuex 3, Element UI 2.15, existing CommonJS Node source-contract tests, CSS custom properties, Vue CLI production build, Playwright/browser visual verification

---

## File Structure

- `vue/src/navigation/roleNavigation.js`: canonical role menus, preview destinations, and mode landing routes.
- `vue/src/router/index.js`: role/mode route enforcement and administrator preview exception.
- `vue/src/services/participantPreview.js`: one responsibility: detect preview mode, resolve mode home, and identify mutation-disabled UI.
- `vue/src/components/PlatformShell.vue`: shared responsive sidebar/header/account shell for management, operations, training, and competition.
- `vue/src/components/ParticipantPreviewNotice.vue`: compact read-only state shown only beside disabled participant actions.
- `vue/src/layouts/ManagementShell.vue`, `OperationsShell.vue`, `NormalUserShell.vue`, `vue/src/views/Layout.vue`: thin role-specific adapters around the shared shell.
- `vue/src/views/management/CompetitionManagement.vue`: first-level competition hub, excluding devices and platform settings.
- `vue/src/views/management/PlatformSettings.vue`: first-level global settings host using the existing settings content.
- `vue/src/views/management/CompetitionPreview.vue`: mode-resolved participant iframe without administrator toolbar.
- `vue/src/views/management/ManagementHome.vue`: authoritative in-place platform-mode action.
- `vue/src/assets/style/platform-theme.css`: global colors, typography, spacing, surfaces, tables, dialogs, and responsive tokens.
- `vue/src/assets/style/platform-shell.css`, `login.css`, `layout.css`: shell-specific composition consuming the shared tokens.
- `vue/scripts/test-platform-ui-contract.js`: shared shell/theme/login source contract.
- `vue/scripts/test-participant-preview-contract.js`: preview mode, route, and mutation policy contract.
- `vue/scripts/test-management-mode-contract.js`: in-place mode switch contract.

### Task 1: Establish Navigation and Route Contracts

**Files:**
- Modify: `vue/scripts/test-role-navigation.js`
- Modify: `vue/src/navigation/roleNavigation.js`
- Modify: `vue/src/router/index.js`
- Create: `vue/src/views/management/CompetitionManagement.vue`
- Create: `vue/src/views/management/PlatformSettings.vue`

- [ ] **Step 1: Write the failing first-level navigation contract**

Replace the management/competition assertions in `vue/scripts/test-role-navigation.js` with:

```js
assert.deepStrictEqual(navigation.managementItems.map(item => item.label), [
  '主页', '竞赛管理', '课程管理', '资源管理', '实训管理',
  '用户管理', '设备管理', '平台设置', '镜像仓库'
])
const competition = navigation.managementItems.find(item => item.key === 'competition')
assert.strictEqual(competition.route, '/management/competition')
assert.strictEqual(competition.children, undefined)
assert.ok(!navigation.competitionItems.some(item => item.label === '比赛设备'))
assert.ok(!navigation.competitionItems.some(item => item.label === '平台设置'))
assert.strictEqual(
  navigation.managementItems.find(item => item.key === 'settings').route,
  '/management/platform-settings'
)
```

- [ ] **Step 2: Run the contract and verify RED**

Run: `cd vue && node scripts/test-role-navigation.js`

Expected: FAIL because competition is still a submenu and devices/settings are still competition children.

- [ ] **Step 3: Implement the canonical first-level menu**

Define these entries in `roleNavigation.js`:

```js
const competitionItems = [
  { key: 'mode', label: '模式与环境', route: '/management/competition-mode' },
  { key: 'preview', label: '参赛端预览', route: '/competition-preview' },
  { key: 'control', label: '比赛控制', route: '/Admin?tab=timer' },
  { key: 'rules', label: '赛程赛规', route: '/Admin?tab=rules' },
  { key: 'subjects', label: '试卷题目', route: '/Admin?tab=subjects' },
  { key: 'grading', label: '试卷评分', route: '/Admin?tab=grading' },
  { key: 'accounts', label: '比赛账号', route: '/Admin?tab=users' }
]

const managementItems = [
  { key: 'home', label: '主页', icon: 'el-icon-house', route: '/management' },
  { key: 'competition', label: '竞赛管理', icon: 'el-icon-trophy', route: '/management/competition' },
  { key: 'courses', label: '课程管理', icon: 'el-icon-reading', route: '/management/courses' },
  { key: 'resources', label: '资源管理', icon: 'el-icon-folder-opened', route: '/management/resources' },
  { key: 'training', label: '实训管理', icon: 'el-icon-monitor', route: '/management/training' },
  { key: 'users', label: '用户管理', icon: 'el-icon-user', route: '/management/users' },
  { key: 'devices', label: '设备管理', icon: 'el-icon-cpu', route: '/management/devices' },
  { key: 'settings', label: '平台设置', icon: 'el-icon-setting', route: '/management/platform-settings' },
  { key: 'image-registry', label: '镜像仓库', icon: 'el-icon-coin', route: '/operations/image-registry' }
]
```

Add `/management/competition` and `/management/platform-settings` children in `router/index.js`. `CompetitionManagement.vue` renders `competitionItems` as compact internal section links. `PlatformSettings.vue` hosts the existing settings panel without rendering competition admin navigation; extract the settings panel from `Admin.vue` only if direct reuse cannot keep it isolated.

- [ ] **Step 4: Run navigation contracts and build**

Run: `cd vue && node scripts/test-role-navigation.js && npm run build`

Expected: contract prints `role navigation tests passed`; build exits 0 with only existing Browserslist/bundle warnings.

- [ ] **Step 5: Commit**

```bash
git add vue/scripts/test-role-navigation.js vue/src/navigation/roleNavigation.js vue/src/router/index.js vue/src/views/management/CompetitionManagement.vue vue/src/views/management/PlatformSettings.vue
git commit -m "feat: promote competition and platform settings navigation"
```

### Task 2: Make the Home Mode Action Authoritative and In-Place

**Files:**
- Create: `vue/scripts/test-management-mode-contract.js`
- Modify: `vue/package.json`
- Modify: `vue/src/views/management/ManagementHome.vue`
- Reuse: `vue/src/api/PlatformMode.js`

- [ ] **Step 1: Write the failing mode-action contract**

```js
const assert = require('assert')
const fs = require('fs')
const source = fs.readFileSync('src/views/management/ManagementHome.vue', 'utf8')

assert.match(source, /getPlatformMode/)
assert.match(source, /changePlatformMode/)
assert.match(source, /进入比赛模式/)
assert.match(source, /退出比赛模式/)
assert.match(source, /targetMode/)
assert.doesNotMatch(source, /切换比赛模式[^\n]*route:/)
assert.doesNotMatch(source, /\$router\.(push|replace).*changePlatformMode/)
console.log('management mode contract passed')
```

Add `"test:management-mode": "node scripts/test-management-mode-contract.js"` to `package.json`.

- [ ] **Step 2: Run the contract and verify RED**

Run: `cd vue && npm run test:management-mode`

Expected: FAIL because the home action still routes to `/Admin?tab=timer`.

- [ ] **Step 3: Implement the mode state and command**

In `ManagementHome.vue`, import `getPlatformMode` and `changePlatformMode`, add `platformMode`, `modeLoading`, and `modeSwitching`, and replace the route-only action with a dedicated button:

```vue
<button type="button" class="overview-action mode-action"
  :disabled="modeLoading || modeSwitching" @click="confirmModeSwitch">
  <i class="el-icon-refresh" />
  <span>{{ platformMode === 'COMPETITION' ? '退出比赛模式' : '进入比赛模式' }}</span>
</button>
```

```js
async loadPlatformMode () {
  this.modeLoading = true
  try {
    const response = await getPlatformMode()
    this.platformMode = response.data && response.data.mode === 'COMPETITION' ? 'COMPETITION' : 'TRAINING'
  } finally {
    this.modeLoading = false
  }
},
async confirmModeSwitch () {
  const targetMode = this.platformMode === 'COMPETITION' ? 'TRAINING' : 'COMPETITION'
  const phrase = targetMode === 'COMPETITION' ? 'ENTER COMPETITION' : 'EXIT COMPETITION'
  await this.$prompt(`输入 ${phrase} 确认切换。普通用户现有登录会失效。`,
    targetMode === 'COMPETITION' ? '进入比赛模式' : '退出比赛模式',
    { inputPattern: new RegExp(`^${phrase}$`), inputErrorMessage: '确认文本不匹配' })
  this.modeSwitching = true
  try {
    await changePlatformMode(targetMode)
    await this.loadPlatformMode()
    this.$message.success('平台模式已更新')
  } finally {
    this.modeSwitching = false
  }
}
```

Do not call `$router.push` or `$router.replace` after the command.

- [ ] **Step 4: Run focused and existing mode contracts**

Run: `cd vue && npm run test:management-mode && npm run test:competition-mode && npm run test:navigation`

Expected: all three scripts exit 0.

- [ ] **Step 5: Commit**

```bash
git add vue/package.json vue/scripts/test-management-mode-contract.js vue/src/views/management/ManagementHome.vue
git commit -m "feat: switch platform mode from management home"
```

### Task 3: Centralize Participant Preview Policy

**Files:**
- Create: `vue/src/services/participantPreview.js`
- Create: `vue/scripts/test-participant-preview-contract.js`
- Modify: `vue/package.json`
- Modify: `vue/src/navigation/roleNavigation.js`
- Modify: `vue/src/router/index.js`
- Modify: `vue/src/views/management/CompetitionPreview.vue`

- [ ] **Step 1: Write the failing preview policy contract**

```js
const assert = require('assert')
const fs = require('fs')
const preview = require('../src/services/participantPreview')

assert.strictEqual(preview.previewHome('TRAINING'), '/course-platform')
assert.strictEqual(preview.previewHome('COMPETITION'), '/Publicity')
assert.strictEqual(preview.isPreviewRoute({ query: { preview: '1' } }), true)
assert.strictEqual(preview.isPreviewRoute({ query: {} }), false)

const source = fs.readFileSync('src/views/management/CompetitionPreview.vue', 'utf8')
assert.doesNotMatch(source, /el-radio-group|el-radio-button|preview-toolbar|刷新预览/)
assert.match(source, /getPlatformMode/)
assert.match(source, /previewHome/)
assert.match(source, /preview=1/)
console.log('participant preview contract passed')
```

Add `"test:preview": "node scripts/test-participant-preview-contract.js"`.

- [ ] **Step 2: Run the contract and verify RED**

Run: `cd vue && npm run test:preview`

Expected: FAIL because the helper does not exist and preview still has four destination controls.

- [ ] **Step 3: Implement mode-resolved preview**

Create the CommonJS-compatible helper:

```js
function isPreviewRoute (route) {
  return Boolean(route && route.query && route.query.preview === '1')
}

function previewHome (mode) {
  return mode === 'COMPETITION' ? '/Publicity' : '/course-platform'
}

module.exports = { isPreviewRoute, previewHome }
```

Change `CompetitionPreview.vue` to load `getPlatformMode()` once, resolve `previewHome(mode)`, and render only a loading/error state plus the iframe. The iframe URL must be:

```js
frameUrl () {
  return `${window.location.origin}${window.location.pathname}#${this.homePath}?preview=1`
}
```

Expand `previewDestinations` to include both participant trees:

```js
const previewDestinations = [
  '/course-platform', '/resource-center', '/training-environment',
  '/Publicity', '/Home', '/Question', '/Detect', '/competition-practical'
]
```

Keep the router exception exact: only role `ADMIN`, query `preview=1`, and a listed participant destination bypasses role/mode metadata.

- [ ] **Step 4: Run preview and navigation contracts**

Run: `cd vue && npm run test:preview && npm run test:navigation && npm run build`

Expected: scripts and build exit 0.

- [ ] **Step 5: Commit**

```bash
git add vue/package.json vue/scripts/test-participant-preview-contract.js vue/src/services/participantPreview.js vue/src/navigation/roleNavigation.js vue/src/router/index.js vue/src/views/management/CompetitionPreview.vue
git commit -m "feat: preview the current participant experience"
```

### Task 4: Enforce Read-Only Participant Preview

**Files:**
- Create: `vue/src/components/ParticipantPreviewNotice.vue`
- Modify: `vue/scripts/test-participant-preview-contract.js`
- Modify: `vue/src/views/user/TrainingEnvironment.vue`
- Modify: `vue/src/views/competition/CompetitionPractical.vue`
- Modify: `vue/src/views/Detect.vue`
- Modify: `vue/src/components/Question.vue`
- Modify: `vue/src/views/Home.vue`
- Modify: `vue/src/views/Publicity.vue`

- [ ] **Step 1: Extend the failing source contract for every mutation surface**

Add explicit file assertions rather than a blanket CSS-only check:

```js
const mutationViews = [
  'src/views/user/TrainingEnvironment.vue',
  'src/views/competition/CompetitionPractical.vue',
  'src/views/Detect.vue',
  'src/components/Question.vue'
]
mutationViews.forEach(file => {
  const text = fs.readFileSync(file, 'utf8')
  assert.match(text, /isPreviewRoute/, `${file} must detect preview mode`)
  assert.match(text, /previewOnly/, `${file} must expose preview-only UI state`)
})
```

- [ ] **Step 2: Run and verify RED**

Run: `cd vue && npm run test:preview`

Expected: FAIL naming each mutation-capable view that lacks the centralized guard.

- [ ] **Step 3: Disable mutations in component logic, not only visually**

For each listed view, add:

```js
const { isPreviewRoute } = require('@/services/participantPreview')

computed: {
  previewOnly () { return isPreviewRoute(this.$route) }
}
```

Mutation methods must return before API calls:

```js
if (this.previewOnly) {
  this.$message.info('预览模式不会提交或修改数据')
  return
}
```

Buttons use `:disabled="previewOnly || existingDisabled"`. Hide answers, score rules, correct-answer fields, internal identifiers, administrator controls, and configuration values entirely in preview. Add `ParticipantPreviewNotice` adjacent only to disabled practical, submit, or detection areas:

```vue
<ParticipantPreviewNotice v-if="previewOnly" />
```

The component text is exactly `当前为只读预览，操作不会提交。` and contains no instructional feature list.

- [ ] **Step 4: Run preview, paper, and full source contracts**

Run:

```bash
cd vue
npm run test:preview
node scripts/test-t100-paper.js
Get-ChildItem scripts/test-*.js | ForEach-Object { node $_.FullName; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }
```

Expected: every script exits 0.

- [ ] **Step 5: Commit**

```bash
git add vue/scripts/test-participant-preview-contract.js vue/src/components/ParticipantPreviewNotice.vue vue/src/views/user/TrainingEnvironment.vue vue/src/views/competition/CompetitionPractical.vue vue/src/views/Detect.vue vue/src/components/Question.vue
git add vue/src/views vue/src/components
git commit -m "feat: make participant preview read only"
```

### Task 5: Build the Shared Responsive Platform Shell

**Files:**
- Create: `vue/src/components/PlatformShell.vue`
- Create: `vue/scripts/test-platform-ui-contract.js`
- Modify: `vue/package.json`
- Modify: `vue/src/layouts/ManagementShell.vue`
- Modify: `vue/src/layouts/OperationsShell.vue`
- Modify: `vue/src/layouts/NormalUserShell.vue`
- Modify: `vue/src/views/Layout.vue`
- Create: `vue/src/assets/style/platform-theme.css`
- Modify: `vue/src/assets/style/platform-shell.css`
- Modify: `vue/src/assets/style/layout.css`

- [ ] **Step 1: Write the failing shared-shell contract**

```js
const assert = require('assert')
const fs = require('fs')
const shell = fs.readFileSync('src/components/PlatformShell.vue', 'utf8')
const theme = fs.readFileSync('src/assets/style/platform-theme.css', 'utf8')

assert.match(shell, /shell-drawer/)
assert.match(shell, /el-icon-search/)
assert.match(shell, /aria-label="打开导航"/)
assert.match(theme, /--ui-primary:\s*#6f7ff7/)
assert.match(theme, /--ui-workspace:\s*#f3f5f9/)
assert.match(theme, /--ui-radius:\s*8px/)
;['ManagementShell', 'OperationsShell', 'NormalUserShell'].forEach(name => {
  assert.match(fs.readFileSync(`src/layouts/${name}.vue`, 'utf8'), /PlatformShell/)
})
assert.match(fs.readFileSync('src/views/Layout.vue', 'utf8'), /PlatformShell/)
console.log('platform UI contract passed')
```

Add `"test:platform-ui": "node scripts/test-platform-ui-contract.js"`.

- [ ] **Step 2: Run and verify RED**

Run: `cd vue && npm run test:platform-ui`

Expected: FAIL because the shared component and token file do not exist.

- [ ] **Step 3: Add shared tokens and component API**

Define these core tokens in `platform-theme.css`:

```css
:root {
  --ui-primary: #6f7ff7;
  --ui-primary-strong: #5368ed;
  --ui-workspace: #f3f5f9;
  --ui-surface: #ffffff;
  --ui-text: #1f2940;
  --ui-muted: #7d879c;
  --ui-border: #e6e9f1;
  --ui-success: #31c7a1;
  --ui-warning: #f4b84a;
  --ui-danger: #f46f78;
  --ui-info: #4eb8ee;
  --ui-radius: 8px;
  --ui-shadow: 0 12px 30px rgba(46, 58, 98, .08);
}
```

`PlatformShell.vue` accepts `items`, `brandCaption`, `workspaceTitle`, `workspaceCaption`, and `userName`; it owns the desktop sidebar, mobile drawer, compact search affordance, account/logout slot, and `<router-view />` slot. Icon buttons use Element UI icons with tooltips/ARIA labels. The four existing shells become thin adapters that provide navigation and activity/logout behavior.

For competition `Layout.vue`, pass only participant competition items when `showAdminNavigation` is false; keep countdown synchronization and pre-start behavior inside `Layout.vue`, but move visual navigation/header markup into `PlatformShell` slots.

- [ ] **Step 4: Implement responsive rules**

In `platform-shell.css`, keep a 220px desktop sidebar, a 76px compact tablet sidebar, and a mobile drawer below 720px. Do not scale fonts with viewport width. Use stable 40px icon buttons, `minmax(0, 1fr)` grid tracks, horizontal table overflow, and no nested section cards.

- [ ] **Step 5: Run contracts and production build**

Run: `cd vue && npm run test:platform-ui && npm run test:navigation && npm run build`

Expected: all commands exit 0; existing bundle-size warning may remain.

- [ ] **Step 6: Commit**

```bash
git add vue/package.json vue/scripts/test-platform-ui-contract.js vue/src/components/PlatformShell.vue vue/src/layouts vue/src/views/Layout.vue vue/src/assets/style/platform-theme.css vue/src/assets/style/platform-shell.css vue/src/assets/style/layout.css
git commit -m "feat: unify role shells with responsive platform chrome"
```

### Task 6: Restyle Login and Global Operational Surfaces

**Files:**
- Modify: `vue/scripts/test-platform-ui-contract.js`
- Modify: `vue/src/views/Login.vue`
- Modify: `vue/src/assets/style/login.css`
- Modify: `vue/src/App.vue` or `vue/src/main.js` (whichever is the current global stylesheet entry)
- Modify: `vue/src/assets/style/platform-theme.css`

- [ ] **Step 1: Extend the failing contract**

```js
const login = fs.readFileSync('src/views/Login.vue', 'utf8')
assert.match(login, /platform-theme\.css/)
assert.match(login, /login-status/)
assert.match(login, /autocomplete="username"/)
assert.match(login, /autocomplete="current-password"/)
assert.doesNotMatch(login, /gradient|orb|bokeh/i)
```

- [ ] **Step 2: Run and verify RED**

Run: `cd vue && npm run test:platform-ui`

Expected: FAIL because login does not import the shared token layer or expose the approved compact status treatment.

- [ ] **Step 3: Apply the approved login composition**

Keep the actual login form in the first viewport. Use the configured bitmap background only when present, with a legible solid overlay; otherwise use the light workspace color. Place brand/name as a strong first signal, use a white login surface with 8px radius, retain inline Element validation, and use blue-violet for the primary action. Do not add marketing feature cards, decorative SVGs, gradients, or oversized headings.

Apply global Element UI overrides under `.platform-app`/`.platform-shell` only: buttons, inputs, tables, tags, dialogs, tabs, pagination, empty/loading states. Do not override third-party xterm internals.

- [ ] **Step 4: Run login/navigation contracts and build**

Run: `cd vue && npm run test:platform-ui && npm run test:navigation && npm run build`

Expected: all commands exit 0.

- [ ] **Step 5: Commit**

```bash
git add vue/scripts/test-platform-ui-contract.js vue/src/views/Login.vue vue/src/assets/style/login.css vue/src/assets/style/platform-theme.css vue/src/App.vue vue/src/main.js
git commit -m "feat: apply shared visual system to login and controls"
```

### Task 7: Refresh Administrator and Participant Page Composition

**Files:**
- Modify: `vue/src/views/management/ManagementHome.vue`
- Modify: `vue/src/views/management/CompetitionManagement.vue`
- Modify: `vue/src/views/management/CompetitionMode.vue`
- Modify: `vue/src/views/management/CourseManagement.vue`
- Modify: `vue/src/views/management/ResourceManagement.vue`
- Modify: `vue/src/views/management/TrainingManagement.vue`
- Modify: `vue/src/views/management/UserManagement.vue`
- Modify: `vue/src/views/management/DeviceManagement.vue`
- Modify: `vue/src/views/operations/AdministratorManagement.vue`
- Modify: `vue/src/views/operations/CompetitionEnvironments.vue`
- Modify: `vue/src/views/operations/ContainerTemplates.vue`
- Modify: `vue/src/views/operations/ImageRegistryView.vue`
- Modify: `vue/src/views/operations/LicenseDiagnostics.vue`
- Modify: `vue/src/views/operations/OperationsHome.vue`
- Modify: `vue/src/views/operations/ProcessingAgents.vue`
- Modify: `vue/src/views/user/CoursePlatform.vue`
- Modify: `vue/src/views/user/ResourceCenter.vue`
- Modify: `vue/src/views/user/TrainingEnvironment.vue`
- Modify: `vue/src/views/Home.vue`
- Modify: `vue/src/views/Publicity.vue`
- Modify: `vue/src/components/Question.vue`
- Modify: `vue/src/views/Detect.vue`
- Modify: `vue/src/views/competition/CompetitionPractical.vue`
- Modify: `vue/scripts/test-platform-ui-contract.js`

- [ ] **Step 1: Add page-composition source assertions**

Assert every named top-level view contains `module-page` and `module-heading`, no page heading uses an inline font size above 32px, and no management page contains `.card .card`/nested `el-card` structures. Assert Competition Management contains the seven required internal labels and excludes `比赛设备` and `平台设置`.

```js
const competitionHub = fs.readFileSync('src/views/management/CompetitionManagement.vue', 'utf8')
;['模式与环境', '参赛端预览', '比赛控制', '赛程赛规', '试卷题目', '试卷评分', '比赛账号']
  .forEach(label => assert.ok(competitionHub.includes(label)))
assert.ok(!competitionHub.includes('比赛设备'))
assert.ok(!competitionHub.includes('平台设置'))
```

- [ ] **Step 2: Run and verify RED**

Run: `cd vue && npm run test:platform-ui`

Expected: FAIL listing legacy pages that have not adopted the shared structure.

- [ ] **Step 3: Convert pages without changing domain behavior**

For administrators, use unframed page sections, compact action toolbars, dense tables, clear empty/loading/error states, and dialogs only for focused actions. Keep cards only for repeated summary items. Device state is linked to `/management/devices`; do not recreate device selection in competition.

For participants, lower the information density but keep training and competition navigation isolated. Competition paper/practical remain primary; training course/resource/environment remain primary. An unbound competition user may still open and answer the paper, while annotation/editor buttons render disabled with the existing binding-unavailable reason.

Do not alter API requests, response mapping, permissions, timers, xterm lifecycle, or domain state transitions during this styling task.

- [ ] **Step 4: Run all frontend contracts**

Run:

```powershell
cd vue
Get-ChildItem scripts/test-*.js | Sort-Object Name | ForEach-Object {
  node $_.FullName
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
```

Expected: all contract scripts exit 0.

- [ ] **Step 5: Run production build**

Run: `cd vue && npm run build`

Expected: exit 0; only pre-existing Browserslist/bundle-size warnings permitted.

- [ ] **Step 6: Commit**

```bash
git add vue/src/views vue/src/components/Question.vue vue/scripts/test-platform-ui-contract.js
git commit -m "feat: refresh administrator and participant workspaces"
```

### Task 8: Browser Verification and Regression Closure

**Files:**
- Modify: `docs/superpowers/specs/2026-08-21-platform-ui-refresh-design.md` only if verified implementation behavior requires clarification
- Create: `docs/verification/2026-08-21-platform-ui-refresh.md`

- [ ] **Step 1: Start the production-like frontend**

Run: `cd vue && npm run serve -- --host 0.0.0.0 --port 19142`

Expected: dev server reports `http://localhost:19142/` without compile errors. If the port is occupied, use the next free port and record it.

- [ ] **Step 2: Verify role and mode workflows in browser**

Using the configured local accounts, verify:

```text
SUPER_ADMIN: login -> /operations -> operations shell and system menu only
ADMIN: login -> /management -> first-level Competition Management and Platform Settings
ADMIN: home mode action -> confirmation -> same /management route -> toggled button label
ADMIN: participant preview -> no toolbar -> current-mode participant home -> navigation works -> mutations disabled
USER/TRAINING: login -> /course-platform -> no competition navigation
USER/COMPETITION: login -> /Publicity -> no training navigation -> paper remains available when unbound -> practical actions disabled
```

- [ ] **Step 3: Capture and inspect desktop screenshots**

Capture login, management home, competition management, operations, training participant home, competition participant home, and preview at `1440x900`. Verify no text overlap, horizontal clipping, nested cards, admin leakage, gradients/orbs, or one-note color dominance.

- [ ] **Step 4: Capture and inspect mobile screenshots**

Capture the same critical shells at `390x844`. Verify the navigation drawer opens/closes, labels fit, tables scroll, fixed controls remain stable, preview iframe is usable, and content does not overlap.

- [ ] **Step 5: Run final automated verification**

```powershell
cd vue
Get-ChildItem scripts/test-*.js | Sort-Object Name | ForEach-Object {
  node $_.FullName
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
npm run build
git diff --check
git status --short
```

Expected: every contract passes, build exits 0, `git diff --check` has no output, and status contains only the intended verification document before commit.

- [ ] **Step 6: Record evidence and commit**

Write `docs/verification/2026-08-21-platform-ui-refresh.md` with tested URLs, viewports, role/mode results, command totals, warnings, and screenshot paths. Do not include account passwords or secrets.

```bash
git add docs/verification/2026-08-21-platform-ui-refresh.md docs/superpowers/specs/2026-08-21-platform-ui-refresh-design.md
git commit -m "test: verify platform UI refresh"
```

## Self-Review Checklist

- [ ] Ordinary administrator navigation is exactly first-level, and Competition Management contains no device or platform-settings section.
- [ ] Platform Settings is first-level and its copy/behavior is platform-wide.
- [ ] Home mode switch calls the existing backend command, remains on the current admin page, and toggles its label.
- [ ] Login continues to route USER by authoritative session mode, and generation invalidation remains unchanged.
- [ ] Preview opens the current-mode participant home with the real participant shell and no destination/refresh toolbar.
- [ ] Preview hides administrator-only data, answers, scoring, identifiers, and configuration, and blocks every mutation before API invocation.
- [ ] Training and competition participant navigation remain mutually exclusive.
- [ ] Unbound competition users can answer papers but cannot start annotation/editor practical operations.
- [ ] Shared visual tokens apply to login, management, operations, training, competition, and preview.
- [ ] Desktop/tablet/mobile layouts have stable controls, no overlap, and no nested page cards.
- [ ] Every implementation step names its concrete file, command, expected result, and defined helper/function signature.
