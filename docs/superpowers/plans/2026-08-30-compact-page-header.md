# 全局紧凑页面顶部栏 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将所有平台页面的面包屑和页面级操作合并为约 40px 的紧凑顶部栏，消除隐藏标题留下的空白。

**Architecture:** 只修改公共 `PlatformShell`，将页面第一个标题栏定位到面包屑右侧；标题文字继续隐藏，操作按钮保留。移动端恢复普通文档流并允许换行，不修改页面业务组件。

**Tech Stack:** Vue 2、CSS Grid、Node assert 契约测试

---

### Task 1: 公共顶部栏契约

**Files:**
- Modify: `vue/scripts/test-platform-ui-contract.js`
- Test: `vue/scripts/test-platform-ui-contract.js`

- [ ] **Step 1: 写入失败断言**

断言 `.shell-main` 提供定位上下文、面包屑高度为 40px、首个页面标题栏固定在右上方，以及移动端恢复普通定位：

```js
assert.match(shell, /\.shell-main\s*\{[^}]*position:\s*relative;/s)
assert.match(shell, /\.shell-breadcrumb\s*\{[^}]*min-height:\s*40px;/s)
assert.match(shell, /\.shell-main\s*>\s*\.module-page\s*>\s*\.module-heading[^}]*position:\s*absolute;/s)
assert.match(shell, /@media\s*\(max-width:\s*720px\)[\s\S]*?position:\s*static;/s)
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd vue && node scripts/test-platform-ui-contract.js`

Expected: FAIL，提示公共壳层尚未使用紧凑顶部网格。

### Task 2: 公共顶部栏实现

**Files:**
- Modify: `vue/src/components/PlatformShell.vue`
- Test: `vue/scripts/test-platform-ui-contract.js`

- [ ] **Step 1: 将页面操作栏合并到面包屑行**

在 `PlatformShell.vue` 的全局样式中：

```css
.shell-main { position: relative; }
.shell-breadcrumb { min-height: 40px; margin: 0; padding-right: 140px; }
.shell-main > .module-page > .module-heading,
.shell-main > .module-composed-page > .module-heading {
  position: absolute;
  top: 0;
  right: 0;
  min-height: 40px;
  margin: 0;
}
```

标题栏脱离普通文档流并与面包屑同排；页面主体紧接在 40px 顶部栏之后。页面没有操作按钮时只显示面包屑。

- [ ] **Step 2: 增加移动端回退**

```css
@media (max-width: 720px) {
  .shell-breadcrumb { min-height: 36px; }
  .shell-main > .module-page > .module-heading,
  .shell-main > .module-composed-page > .module-heading {
    position: static;
    min-height: 0;
    margin-bottom: 8px;
    justify-self: stretch;
  }
}
```

- [ ] **Step 3: 运行契约和生产构建**

Run: `cd vue && node scripts/test-platform-ui-contract.js`

Expected: 本次新增顶部栏断言通过；如脚本停在既有实训表格包裹断言，单独的顶部栏断言不得失败。

Run: `cd vue && npm run build`

Expected: BUILD SUCCESS，仅允许既有资源体积警告。

- [ ] **Step 4: 检查开发服务**

Run: `Invoke-WebRequest -UseBasicParsing http://localhost:19142/`

Expected: HTTP 200；实训页顶部只占一行，面包屑左对齐、模型验证按钮右对齐。
