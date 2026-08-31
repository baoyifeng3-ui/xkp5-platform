# Class Resource Access and Login Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow resource-center access during one-key class sessions and make custom login backgrounds visible without losing text contrast.

**Architecture:** Extend the existing frontend class-policy whitelist in both navigation and router guards. Adjust the existing CSS overlay opacity without changing background storage or rendering.

**Tech Stack:** Vue 2, Vue Router, CSS, Node contract tests.

---

### Task 1: Add resource center to active-class whitelist

**Files:**
- Modify: `vue/src/layouts/NormalUserShell.vue`
- Modify: `vue/src/router/index.js`
- Test: `vue/scripts/test-course-platform-contract.js`

- [ ] Add failing contract checks for `/resource-center` in active class navigation and routing.
- [ ] Update `navigate` and `classRouteAllowed` to allow `/resource-center`.
- [ ] Update router active-class guard to allow `/resource-center` before redirecting.
- [ ] Run `node scripts/test-course-platform-contract.js`.

### Task 2: Reduce login background overlay

**Files:**
- Modify: `vue/src/assets/style/login.css`
- Test: `vue/scripts/test-platform-ui-contract.js`

- [ ] Add a failing assertion for `rgba(26, 34, 55, .35)`.
- [ ] Replace the existing `.7` overlay opacity with `.35`.
- [ ] Run `node scripts/test-platform-ui-contract.js`.

### Task 3: Verify

- [ ] Run both contract tests.
- [ ] Run `npm run build`.
- [ ] Confirm `http://localhost:19142/` returns HTTP 200.
