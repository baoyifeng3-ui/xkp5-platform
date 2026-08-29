# Classroom Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce one-click-class restrictions, restore course visibility, and provide a compact resizable/fullscreen embedded training workspace.

**Architecture:** `ActiveClassSessionService` remains the classroom policy source. A small user policy endpoint feeds both router guards and the training page. Existing course and environment APIs remain authoritative; the frontend only composes their results.

**Tech Stack:** Spring Boot 2.1, MyBatis Plus, Vue 2, Element UI, existing contract scripts.

---

### Task 1: Classroom access policy

**Files:**
- Modify: `java/match-mgr/src/main/java/com/match/environment/service/ActiveClassSessionService.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/web/UserClassPolicyController.java`
- Test: `java/match-mgr/src/test/java/com/match/environment/service/ActiveClassSessionServiceTest.java`

- [ ] Add a failing test asserting policy returns `active`, `courseId`, `environmentName`, `editorTool`, and allowed routes.
- [ ] Run `mvn -q -Dtest=ActiveClassSessionServiceTest test`; expect the new assertion to fail.
- [ ] Add `userPolicy(userId)` and `GET /user/class-policy`; return allowed route keys `training` and `validation` while active.
- [ ] Add backend guards to course/resource user controllers so active classroom requests return `当前上课中，禁止使用该功能` except the selected course content consumed inside training.
- [ ] Re-run the test and controller tests; expect PASS.

### Task 2: Router and navigation enforcement

**Files:**
- Create: `vue/src/api/ClassPolicy.js`
- Modify: `vue/src/router/index.js`
- Modify: `vue/src/layouts/NormalUserShell.vue`
- Modify: `vue/src/navigation/roleNavigation.js`
- Test: `vue/scripts/test-role-navigation.js`

- [ ] Add a failing contract asserting active class blocks course/resources but permits training/validation.
- [ ] Cache class policy for one navigation cycle and show the agreed warning instead of routing.
- [ ] Hide or disable blocked menu items while active without affecting administrators.
- [ ] Run `node scripts/test-role-navigation.js`; expect PASS.

### Task 3: Course visibility and classroom chapter data

**Files:**
- Modify: `java/match-mgr/src/main/java/com/match/course/web/UserCourseController.java`
- Modify: `java/match-mgr/src/main/java/com/match/course/service/CourseLearningService.java`
- Modify: `vue/src/views/user/CoursePlatform.vue`
- Test: `java/match-mgr/src/test/java/com/match/course/service/CourseLearningServiceTest.java`
- Test: `vue/scripts/test-course-platform-contract.js`

- [ ] Add tests for published courses with chapters/resources and for selected-course classroom access.
- [ ] Make the API return an explicit empty reason rather than silently swallowing request errors.
- [ ] Remove `loadSafely` suppression for course list failures and render the backend reason.
- [ ] Verify ordinary user and admin demo both see the current published course and its two resources.

### Task 4: Compact and fullscreen workspace

**Files:**
- Modify: `vue/src/views/user/TrainingEnvironment.vue`
- Modify: `vue/src/assets/style/platform-shell.css`
- Test: `vue/scripts/test-course-platform-contract.js`

- [ ] Add failing contract assertions for one-line controls, fullscreen toggle, resizable split, tool filtering, and clipboard permissions.
- [ ] Implement the approved compact header and viewport-height iframe.
- [ ] Use the Fullscreen API on the workspace element; fullscreen keeps only shortcut bar and iframe.
- [ ] For course class, render chapter/resources beside iframe with a native CSS `resize`/drag divider; for independent class open the selected tool directly.
- [ ] Respect `editorTool`: expose only VS Code or Jupyter selected by the administrator; show annotation only when present.
- [ ] Keep report drawer resizable and available in either mode.
- [ ] Run frontend contract and `npm run build`; expect PASS.

### Task 5: Real verification

- [ ] Deploy backend by stopping the Java container before replacing the JAR, then restart and wait for `/competition`.
- [ ] Verify inactive class permits course/training.
- [ ] Verify independent class opens only selected tools.
- [ ] Verify course class opens chapter directory and bound environment.
- [ ] Verify fullscreen, half-screen resize, report drawer, CVAT auth, VS Code clipboard, and both processing servers.

