# 课程学习工作区实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将普通用户课程详情页改造成支持完整资料预览、同页可调分屏实训、工具切换、全屏和浮动实训报告的学习工作区。

**Architecture:** 新增一个只负责布局、分隔线和全屏的 `CourseLearningWorkspace` 组件；课程资源、环境启动和 URL 选择仍由现有 `CoursePlatform` 负责。实训报告在现有 `CourseReportEditor` 上增加可选浮动模式，独立实训页面继续使用原抽屉模式。

**Tech Stack:** Vue 2、Element UI、浏览器 Fullscreen API、现有课程与实训环境 REST API。

---

### Task 1: 锁定课程工作区契约

**Files:**
- Modify: `vue/scripts/test-course-platform-contract.js`
- Test: `vue/scripts/test-course-platform-contract.js`

- [ ] **Step 1: 写失败契约**

在现有课程平台契约中增加：

```js
assert.match(coursePage, /CourseLearningWorkspace/)
assert.doesNotMatch(coursePage, /\$router\.push\([\s\S]*training-environment/)
assert.match(coursePage, /workspaceVisible/)
assert.match(coursePage, /trainingPaneVisible/)
assert.match(coursePage, /openWorkspaceTool/)
assert.match(coursePage, /floating/)

const workspace = fs.readFileSync('src/components/course/CourseLearningWorkspace.vue', 'utf8')
assert.match(workspace, /splitRatio/)
assert.match(workspace, /requestFullscreen/)
assert.match(workspace, /图像标注/)
assert.match(workspace, /VS Code/)
assert.match(workspace, /Jupyter/)
assert.match(workspace, /role="separator"/)
```

- [ ] **Step 2: 运行契约并确认失败**

Run: `cd vue && node scripts/test-course-platform-contract.js`

Expected: FAIL，提示缺少 `CourseLearningWorkspace`。

- [ ] **Step 3: 提交测试**

```bash
git add vue/scripts/test-course-platform-contract.js
git commit -m "test: define course workspace contract"
```

### Task 2: 新增可调分屏工作区组件

**Files:**
- Create: `vue/src/components/course/CourseLearningWorkspace.vue`
- Test: `vue/scripts/test-course-platform-contract.js`

- [ ] **Step 1: 创建组件公开接口**

组件接收以下属性：

```js
props: {
  visible: Boolean,
  trainingVisible: Boolean,
  environments: { type: Array, default: () => [] },
  environmentId: { type: String, default: '' },
  embeddedUrl: { type: String, default: '' },
  embeddedTitle: { type: String, default: '' },
  embeddedKey: { type: Number, default: 0 },
  busy: Boolean,
}
```

组件发出 `update:environmentId`、`tool`、`refresh`、`report` 和 `close` 事件。课程资料通过默认插槽传入，组件不请求接口。

- [ ] **Step 2: 实现工具栏**

工具栏包括环境下拉、图像标注、VS Code、Jupyter、刷新、实训报告、全屏和关闭工作区。工具可用性从当前环境 URL 判断：

```js
currentEnvironment() {
  return this.environments.find(item => item.environmentId === this.environmentId) || null
},
toolEnabled() {
  const item = this.currentEnvironment || {}
  return {
    ANNOTATION: Boolean(item.annotationUrl),
    VSCODE: Boolean(item.editorUrl),
    JUPYTER: Boolean(item.jupyterUrl),
  }
}
```

- [ ] **Step 3: 实现拖动分隔线**

使用原生鼠标事件，组件保存 `splitRatio`，桌面端限制在 22% 到 72%，窄屏限制在 25% 到 70%。分隔线使用：

```html
<div
  class="workspace-divider"
  role="separator"
  aria-label="调整课程资料和实训环境宽度"
  @mousedown="beginResize"
/>
```

在 `beforeDestroy` 移除 `mousemove` 和 `mouseup` 监听。

- [ ] **Step 4: 实现工作区全屏**

```js
async toggleFullscreen() {
  if (document.fullscreenElement) await document.exitFullscreen()
  else await this.$refs.workspace.requestFullscreen()
}
```

全屏样式固定工作区高度为 `100vh`，保留工具栏，资料区和 iframe 填满剩余空间。

- [ ] **Step 5: 运行契约并提交**

Run: `cd vue && node scripts/test-course-platform-contract.js`

Expected: 仍因课程页未接入而失败，但工作区组件断言通过。

```bash
git add vue/src/components/course/CourseLearningWorkspace.vue
git commit -m "feat: add resizable course workspace"
```

### Task 3: 课程页接入完整预览与同页实训

**Files:**
- Modify: `vue/src/views/user/CoursePlatform.vue`
- Test: `vue/scripts/test-course-platform-contract.js`

- [ ] **Step 1: 增加工作区状态与组件引用**

导入 `CourseLearningWorkspace`、`startUserTrainingEnvironment` 和 `startAdminTrainingEnvironment`，增加：

```js
workspaceVisible: false,
trainingPaneVisible: false,
workspaceEnvironmentId: '',
workspaceEmbeddedUrl: '',
workspaceEmbeddedTitle: '',
workspaceEmbeddedKey: 0,
workspaceBusy: false,
```

- [ ] **Step 2: 用完整工作区替换预览弹窗**

`openResource(resource)` 获取预览 URL 后设置 `workspaceVisible=true`，不再设置 `preview=true`。工作区资料插槽按类型渲染：

```html
<video v-if="activeResource.resourceType === 'VIDEO'" :src="previewUrl" controls />
<iframe v-else :src="previewUrl" title="课程资料预览" />
```

保留进度条和“完成本资源学习”按钮，并删除原 820px `el-dialog`。

- [ ] **Step 3: 把进入实训改为同页打开**

`openTraining()` 不调用路由，改为：

```js
async openTraining() {
  this.workspaceVisible = true
  this.trainingPaneVisible = true
  const environment = this.courseEnvironments[0]
  if (!environment) return this.$message.warning('当前课程未分配实训环境')
  this.workspaceEnvironmentId = environment.environmentId
}
```

如果没有活动资料，资料插槽显示当前课程章节和资源列表，点击资源后在左侧打开。

- [ ] **Step 4: 实现环境与工具切换**

```js
async openWorkspaceTool(tool) {
  const environment = this.courseEnvironments.find(item => item.environmentId === this.workspaceEnvironmentId)
  if (!environment) return
  if (!['RUNNING', 'STARTING'].includes(environment.actualState)) {
    this.workspaceBusy = true
    await (this.adminDemo
      ? startAdminTrainingEnvironment(environment.environmentId)
      : startUserTrainingEnvironment(environment.environmentId))
    await this.reloadEnvironments()
    this.workspaceBusy = false
  }
  const current = this.courseEnvironments.find(item => item.environmentId === this.workspaceEnvironmentId) || environment
  this.workspaceEmbeddedUrl = tool === 'ANNOTATION' ? current.annotationUrl : tool === 'JUPYTER' ? current.jupyterUrl : current.editorUrl
  this.workspaceEmbeddedTitle = tool === 'ANNOTATION' ? '图像标注' : tool === 'JUPYTER' ? 'Jupyter Notebook' : 'VS Code'
  this.workspaceEmbeddedKey += 1
}
```

重复点击启动中的环境不得再次发送启动命令。

- [ ] **Step 5: 接入模板**

在课程详情内容区优先渲染 `CourseLearningWorkspace`；关闭后恢复六个课程页签。环境选择使用 `.sync`，报告事件打开浮动报告：

```html
<CourseLearningWorkspace
  v-if="workspaceVisible"
  :visible="workspaceVisible"
  :training-visible="trainingPaneVisible"
  :environments="courseEnvironments"
  :environment-id.sync="workspaceEnvironmentId"
  :embedded-url="workspaceEmbeddedUrl"
  :embedded-title="workspaceEmbeddedTitle"
  :embedded-key="workspaceEmbeddedKey"
  :busy="workspaceBusy"
  @tool="openWorkspaceTool"
  @refresh="workspaceEmbeddedKey += 1"
  @report="reportVisible = true"
  @close="closeWorkspace"
>
  <!-- 当前课程资料 -->
</CourseLearningWorkspace>
```

- [ ] **Step 6: 运行契约并提交**

Run: `cd vue && node scripts/test-course-platform-contract.js`

Expected: PASS。

```bash
git add vue/src/views/user/CoursePlatform.vue vue/scripts/test-course-platform-contract.js
git commit -m "feat: embed training in course learning"
```

### Task 4: 实训报告浮动编辑窗

**Files:**
- Modify: `vue/src/components/course/CourseReportEditor.vue`
- Modify: `vue/src/views/user/CoursePlatform.vue`
- Test: `vue/scripts/test-course-platform-contract.js`

- [ ] **Step 1: 写浮动模式失败断言**

```js
const report = fs.readFileSync('src/components/course/CourseReportEditor.vue', 'utf8')
assert.match(report, /floating/)
assert.match(report, /beginDrag/)
assert.match(report, /resize: both/)
assert.match(report, /最小化/)
```

运行契约，预期因缺少 `beginDrag` 失败。

- [ ] **Step 2: 增加浮动模式属性和状态**

```js
props: {
  floating: Boolean,
  visible: Boolean,
  courseId: { type: String, default: '' },
  userName: { type: String, default: '用户' },
},
data: () => ({ left: 80, top: 90, minimized: false, dragging: false })
```

`floating=false` 继续渲染原 `el-drawer`；`floating=true` 渲染固定定位面板。

- [ ] **Step 3: 实现拖动、缩放与最小化**

标题栏 `mousedown` 调用 `beginDrag`，窗口移动时限制在浏览器可视范围。编辑区域保留 `resize: both`，面板设置最小宽高。最小化只显示标题栏和重新打开按钮，不卸载编辑器内容。

- [ ] **Step 4: 保留现有报告能力**

浮动和抽屉模式共用同一编辑器、自动保存、图片粘贴、导出文档、上传交换空间和上传作业空间方法，不新增接口。

课程页使用：

```html
<CourseReportEditor
  floating
  :visible.sync="reportVisible"
  :course-id="selectedCourse && selectedCourse.course.courseId"
  :user-name="reportUserName"
/>
```

- [ ] **Step 5: 运行契约与构建并提交**

Run: `cd vue && node scripts/test-course-platform-contract.js && npm run build`

Expected: 契约 PASS；生产构建完成，仅允许现有资源体积警告。

```bash
git add vue/src/components/course/CourseReportEditor.vue vue/src/views/user/CoursePlatform.vue vue/scripts/test-course-platform-contract.js
git commit -m "feat: add floating training report"
```

### Task 5: 浏览器与回归验证

**Files:**
- Runtime artifacts only.

- [ ] **Step 1: 验证完整资料预览**

以普通用户进入已有 PDF 课程，点击“在线浏览”，确认不出现小弹窗，PDF 占满课程工作区左侧或全宽区域。

- [ ] **Step 2: 验证同页实训**

点击“进入实训”，确认 URL 不跳转到 `/training-environment`，右侧显示环境；拖动分隔线后左右区域尺寸同步变化。

- [ ] **Step 3: 验证工具和全屏**

切换图像标注、VS Code、Jupyter，确认 iframe URL 与当前环境对应；进入全屏后平台侧栏隐藏，工具栏仍可操作；退出全屏恢复页面。

- [ ] **Step 4: 验证浮动报告**

打开报告，确认窗口可拖动、缩放、最小化；输入文字并刷新后内容恢复；验证导出和空间上传按钮仍存在。

- [ ] **Step 5: 运行范围回归**

Run:

```bash
cd vue
node scripts/test-course-platform-contract.js
node scripts/test-training-environment-creation-contract.js
npm run build
```

Expected: 两个契约 PASS；生产构建成功。

- [ ] **Step 6: 保留测试页面并报告结果**

保持 `http://localhost:19142` 运行，向用户说明验证账号、页面入口和仍存在的非本功能测试缺口。
