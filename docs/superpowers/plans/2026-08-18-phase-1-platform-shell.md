# Phase 1 Platform Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the current competition application into the independent XKP5.0 repository and add role-separated super-administrator, normal-administrator, and normal-user application shells without implementing processing-server features.

**Architecture:** Preserve the current Vue 2 and Spring Boot application as the compatibility baseline. Add an explicit database-backed role model, role-specific backend guards, a pure frontend navigation policy, and three route shells. Existing competition pages remain reusable inside Competition Preview, while new business modules are intentionally represented by stable first-phase landing pages.

**Tech Stack:** Vue 2.6, Vue Router 3, Vuex 3, Element UI 2, Spring Boot 2.1, MyBatis-Plus 3.2, Flyway, Sa-Token, MySQL 8, JUnit 4, Mockito, Node.js assertions

---

## File Structure

### Migrated baseline

- `java/`: existing Spring Boot backend and tests
- `vue/`: existing Vue application and assets
- `python/`: existing scoring and inference helpers
- `docker/`, `deploy/`, `compose.*.yml`: existing runtime packaging
- `download/`: existing tracked competition resources

Runtime data such as `.env`, MySQL data, FastDFS data, Maven caches,
`node_modules`, build output, and the original `.git` directory are excluded.

### New backend ownership

- `java/match-mgr/src/main/java/com/match/security/UserRole.java`: canonical roles
- `java/match-mgr/src/main/java/com/match/security/LoginSession.java`: testable Sa-Token session adapter
- `java/match-mgr/src/main/java/com/match/security/RoleGuard.java`: role authorization
- `java/match-mgr/src/main/java/com/match/controller/SuperAdminController.java`: ordinary-administrator bootstrap API
- `java/match-mgr/src/main/java/com/match/service/impl/AdministratorManagementService.java`: administrator lifecycle
- `java/match-mgr/src/main/java/com/match/dto/AdministratorRequest.java`: create/update input
- `java/match-mgr/src/main/java/com/match/dto/AdministratorView.java`: non-sensitive response
- `java/match-mgr/src/main/resources/db/migration/V15__xkp_user_roles.sql`: role migration

### New frontend ownership

- `vue/src/navigation/roleNavigation.js`: pure role/menu/landing-route policy
- `vue/src/layouts/NormalUserShell.vue`: three-option user interface
- `vue/src/layouts/ManagementShell.vue`: normal-administrator interface
- `vue/src/layouts/OperationsShell.vue`: super-administrator interface
- `vue/src/views/management/*.vue`: phase-one management landing pages
- `vue/src/views/operations/OperationsHome.vue`: super-administrator landing page
- `vue/src/views/operations/AdministratorManagement.vue`: normal-admin bootstrap page
- `vue/src/views/ChangePassword.vue`: role-neutral initial-password gate
- `vue/src/views/user/*.vue`: course, resource, and training user landing pages
- `vue/scripts/test-role-navigation.js`: dependency-free policy tests

---

### Task 1: Import The Existing Working Baseline

**Files:**
- Copy: `../competition/{java,vue,python,docker,deploy,download}`
- Copy: `../competition/{README.md,Makefile,compose.local.yml,compose.prod.yml,compose.offline.yml,.gitignore,.dockerignore,.env.local.example,.env.prod.example}`
- Preserve: `docs/specs/2026-08-18-xkp5-platform-design.md`

- [ ] **Step 1: Verify the new repository contains only the design commit**

Run:

```powershell
git status --short
git log --oneline -1
```

Expected: clean status and commit `2845d96 docs: define XKP5.0 platform architecture`.

- [ ] **Step 2: Copy the current working source without Git or runtime data**

Run from `xkp5-platform`:

```powershell
$sourceRoot = Resolve-Path '..\competition'
$directories = @('java', 'vue', 'python', 'docker', 'deploy', 'download')
$files = @(
  'README.md', 'Makefile', 'compose.local.yml', 'compose.prod.yml',
  'compose.offline.yml', '.gitignore', '.dockerignore',
  '.env.local.example', '.env.prod.example'
)
foreach ($directory in $directories) {
  Copy-Item -LiteralPath (Join-Path $sourceRoot $directory) -Destination . -Recurse -Force
}
foreach ($file in $files) {
  Copy-Item -LiteralPath (Join-Path $sourceRoot $file) -Destination . -Force
}
```

Expected: source directories exist in `xkp5-platform`; no `.env`, `.git`,
`fastdfs/*_data`, `node_modules`, or `target` directory is copied.

- [ ] **Step 3: Verify runtime data is ignored**

Run:

```powershell
git status --short
git check-ignore .env vue/node_modules java/match-mgr/target
```

Expected: imported source is untracked; all three runtime paths are ignored.

- [ ] **Step 4: Run baseline static verification**

Run:

```powershell
node vue/scripts/test-t100-paper.js
```

Expected: existing T100 paper checks pass. If Maven and compatible Node are
available, also run `mvn -f java/match-mgr/pom.xml test` and `npm --prefix vue run build`.

- [ ] **Step 5: Commit the baseline migration**

```powershell
git add java vue python docker deploy download README.md Makefile compose.local.yml compose.prod.yml compose.offline.yml .gitignore .dockerignore .env.local.example .env.prod.example
git commit -m "chore: import competition platform baseline"
```

### Task 2: Add The Database-Backed Role Model

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/security/UserRole.java`
- Create: `java/match-mgr/src/main/resources/db/migration/V15__xkp_user_roles.sql`
- Modify: `java/match-mgr/src/main/java/com/match/entity/User.java`
- Test: `java/match-mgr/src/test/java/com/match/security/UserRoleTest.java`

- [ ] **Step 1: Write the failing role-resolution test**

```java
package com.match.security;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class UserRoleTest {
    @Test
    public void resolvesStoredRolesAndLegacyFallbacks() {
        assertEquals(UserRole.SUPER_ADMIN, UserRole.resolve("SUPER_ADMIN", true, "admin"));
        assertEquals(UserRole.ADMIN, UserRole.resolve("ADMIN", true, "manager"));
        assertEquals(UserRole.USER, UserRole.resolve("USER", false, "user1"));
        assertEquals(UserRole.SUPER_ADMIN, UserRole.resolve(null, true, "admin"));
        assertEquals(UserRole.ADMIN, UserRole.resolve(null, true, "judge"));
        assertEquals(UserRole.USER, UserRole.resolve(null, false, "user1"));
    }
}
```

- [ ] **Step 2: Run the focused test and confirm failure**

Run:

```powershell
mvn -f java/match-mgr/pom.xml -Dtest=UserRoleTest test
```

Expected: compilation fails because `UserRole` does not exist.

- [ ] **Step 3: Implement the canonical role enum**

```java
package com.match.security;

public enum UserRole {
    SUPER_ADMIN,
    ADMIN,
    USER;

    public static UserRole resolve(String storedRole, Boolean legacyAdmin, String userName) {
        if (storedRole != null && !storedRole.trim().isEmpty()) {
            return UserRole.valueOf(storedRole.trim().toUpperCase());
        }
        if (Boolean.TRUE.equals(legacyAdmin)) {
            return "admin".equalsIgnoreCase(userName) ? SUPER_ADMIN : ADMIN;
        }
        return USER;
    }
}
```

Add to `User.java`:

```java
@TableField("role")
private String role;
```

Create `V15__xkp_user_roles.sql`:

```sql
ALTER TABLE user ADD COLUMN role VARCHAR(32) NULL AFTER is_admin;

UPDATE user
SET role = CASE
    WHEN LOWER(user_name) = 'admin' THEN 'SUPER_ADMIN'
    WHEN is_admin = 1 THEN 'ADMIN'
    ELSE 'USER'
END
WHERE role IS NULL;

ALTER TABLE user MODIFY COLUMN role VARCHAR(32) NOT NULL DEFAULT 'USER';
CREATE INDEX idx_user_role_enabled ON user (role, enabled);
```

- [ ] **Step 4: Run the role test**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=UserRoleTest test`

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit the role model**

```powershell
git add java/match-mgr/src/main/java/com/match/security/UserRole.java java/match-mgr/src/main/java/com/match/entity/User.java java/match-mgr/src/main/resources/db/migration/V15__xkp_user_roles.sql java/match-mgr/src/test/java/com/match/security/UserRoleTest.java
git commit -m "feat: add explicit platform roles"
```

### Task 3: Enforce Role-Specific Backend Access

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/security/LoginSession.java`
- Create: `java/match-mgr/src/main/java/com/match/security/RoleGuard.java`
- Modify: `java/match-mgr/src/main/java/com/match/controller/UserController.java`
- Modify: `java/match-mgr/src/main/java/com/match/security/AdminGuard.java`
- Test: `java/match-mgr/src/test/java/com/match/security/RoleGuardTest.java`
- Create: `java/match-mgr/src/test/java/com/match/controller/UserControllerTest.java`

- [ ] **Step 1: Write failing guard tests**

```java
@Test
public void normalAdminCannotUseSuperAdminGuard() {
    User user = enabledUser("manager", "ADMIN");
    when(userService.getById(7)).thenReturn(user);
    when(loginSession.loginId()).thenReturn(7);
    try {
        guard.requireSuperAdmin();
        fail("normal administrator must not enter operations");
    } catch (AdminAccessException expected) {
        assertEquals("仅超级管理员可以执行此操作", expected.getMessage());
    }
}

@Test
public void superAdminCannotUseBusinessAdminGuard() {
    User user = enabledUser("admin", "SUPER_ADMIN");
    when(userService.getById(1)).thenReturn(user);
    when(loginSession.loginId()).thenReturn(1);
    try {
        guard.requireBusinessAdmin();
        fail("super administrator must not enter business management");
    } catch (AdminAccessException expected) {
        assertEquals("仅普通管理员可以执行此操作", expected.getMessage());
    }
}
```

Use these imports in the test: `import static org.junit.Assert.assertEquals;`
and `import static org.junit.Assert.fail;`.

- [ ] **Step 2: Run tests and confirm failure**

Run:

```powershell
mvn -f java/match-mgr/pom.xml -Dtest=RoleGuardTest,UserControllerTest test
```

Expected: failure because role-specific guard methods and response role are absent.

- [ ] **Step 3: Implement `RoleGuard`**

Create the session adapter:

```java
package com.match.security;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

@Component
public class LoginSession {
    public int loginId() {
        StpUtil.checkLogin();
        return StpUtil.getLoginIdAsInt();
    }
}
```

The public contract must be:

```java
public User requireSuperAdmin();
public User requireBusinessAdmin();
public User requireAnyAdmin();
public UserRole roleOf(User user);
```

Each method must reject missing or disabled users. `requireSuperAdmin` accepts
only `SUPER_ADMIN`; `requireBusinessAdmin` accepts only `ADMIN`;
`requireAnyAdmin` accepts both. Keep `AdminGuard` as a compatibility adapter that
delegates existing business controllers to `requireBusinessAdmin()`.

Add this field to `UserController.currentUserData`:

```java
data.put("role", UserRole.resolve(user.getRole(), user.getIsAdmin(), user.getUserName()).name());
```

- [ ] **Step 4: Run backend tests**

Run: `mvn -f java/match-mgr/pom.xml test`

Expected: all existing and new tests pass. Update existing fixtures to set
`role = "ADMIN"` when they represent a normal administrator.

- [ ] **Step 5: Commit backend authorization**

```powershell
git add java/match-mgr/src/main/java/com/match/security java/match-mgr/src/main/java/com/match/controller/UserController.java java/match-mgr/src/test
git commit -m "feat: enforce role-specific backend access"
```

### Task 4: Add Super-Administrator Management Of Normal Administrators

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/dto/AdministratorRequest.java`
- Create: `java/match-mgr/src/main/java/com/match/dto/AdministratorView.java`
- Create: `java/match-mgr/src/main/java/com/match/service/impl/AdministratorManagementService.java`
- Create: `java/match-mgr/src/main/java/com/match/controller/SuperAdminController.java`
- Test: `java/match-mgr/src/test/java/com/match/service/impl/AdministratorManagementServiceTest.java`
- Test: `java/match-mgr/src/test/java/com/match/controller/SuperAdminControllerTest.java`

- [ ] **Step 1: Write failing service tests**

Cover these exact behaviors:

```java
@Test public void createsOnlyNormalAdministrators();
@Test public void listsOnlyRoleAdminAccounts();
@Test public void preventsDisablingCurrentSuperAdministrator();
@Test public void resetsAdministratorPasswordAndRequiresChange();
@Test public void neverReturnsStoredPasswords();
```

`AdministratorView` contains only `userId`, `userName`, `enabled`,
`mustChangePassword`, and `role`.

- [ ] **Step 2: Run focused tests and confirm failure**

Run:

```powershell
mvn -f java/match-mgr/pom.xml -Dtest=AdministratorManagementServiceTest,SuperAdminControllerTest test
```

Expected: compilation failure because the administrator API is absent.

- [ ] **Step 3: Implement the administrator lifecycle**

Expose:

```text
GET    /super-admin/administrators
POST   /super-admin/administrators
PUT    /super-admin/administrators/{userId}
POST   /super-admin/administrators/{userId}/reset-password
```

Every endpoint starts with `roleGuard.requireSuperAdmin()`. New accounts always
set `role = "ADMIN"`, `isAdmin = true`, `enabled = true`, and
`mustChangePassword = true`. The API rejects attempts to create another
`SUPER_ADMIN`, edit ordinary users through this endpoint, or return password
values in list responses.

- [ ] **Step 4: Run service, controller, and full backend tests**

Run:

```powershell
mvn -f java/match-mgr/pom.xml -Dtest=AdministratorManagementServiceTest,SuperAdminControllerTest test
mvn -f java/match-mgr/pom.xml test
```

Expected: both commands report `BUILD SUCCESS`.

- [ ] **Step 5: Commit the bootstrap API**

```powershell
git add java/match-mgr/src/main/java/com/match/dto/AdministratorRequest.java java/match-mgr/src/main/java/com/match/dto/AdministratorView.java java/match-mgr/src/main/java/com/match/service/impl/AdministratorManagementService.java java/match-mgr/src/main/java/com/match/controller/SuperAdminController.java java/match-mgr/src/test
git commit -m "feat: let super admins manage administrators"
```

### Task 5: Define And Test Frontend Role Navigation

**Files:**
- Create: `vue/src/navigation/roleNavigation.js`
- Create: `vue/scripts/test-role-navigation.js`
- Modify: `vue/package.json`
- Modify: `vue/src/utils/auth.js`
- Modify: `vue/src/store/modules/Match.js`

- [ ] **Step 1: Write the failing dependency-free navigation test**

```javascript
const assert = require('assert')
const navigation = require('../src/navigation/roleNavigation')

assert.deepStrictEqual(navigation.landingRoute('SUPER_ADMIN'), { path: '/operations' })
assert.deepStrictEqual(navigation.landingRoute('ADMIN'), { path: '/management' })
assert.deepStrictEqual(navigation.landingRoute('USER'), { path: '/course-platform' })
assert.deepStrictEqual(navigation.userItems.map(item => item.label), [
  '课程平台', '资源中心', '实训环境'
])
assert.deepStrictEqual(navigation.managementItems.map(item => item.label), [
  '主页', '课程管理', '资源管理', '实训管理', '竞赛管理', '用户管理', '设备管理'
])
assert.deepStrictEqual(navigation.competitionItems.map(item => item.label), [
  '比赛预览', '比赛控制', '赛程赛规编辑', '试卷题目', '试卷评分', '比赛账号', '比赛设备', '平台设置'
])
console.log('role navigation tests passed')
```

- [ ] **Step 2: Add the script and verify it fails**

Add to `package.json`:

```json
"test:navigation": "node scripts/test-role-navigation.js"
```

Run: `npm --prefix vue run test:navigation`

Expected: failure because `roleNavigation` does not exist.

- [ ] **Step 3: Implement the pure CommonJS navigation policy**

`roleNavigation.js` exports:

```javascript
const USER = 'USER'
const ADMIN = 'ADMIN'
const SUPER_ADMIN = 'SUPER_ADMIN'

const userItems = [
  { key: 'course', label: '课程平台', icon: 'el-icon-reading', route: '/course-platform' },
  { key: 'resources', label: '资源中心', icon: 'el-icon-folder-opened', route: '/resource-center' },
  { key: 'training', label: '实训环境', icon: 'el-icon-monitor', route: '/training-environment' }
]

const competitionItems = [
  { key: 'preview', label: '比赛预览', route: '/competition-preview' },
  { key: 'control', label: '比赛控制', route: '/Admin?tab=timer' },
  { key: 'rules', label: '赛程赛规编辑', route: '/Admin?tab=rules' },
  { key: 'subjects', label: '试卷题目', route: '/Admin?tab=subjects' },
  { key: 'grading', label: '试卷评分', route: '/Admin?tab=grading' },
  { key: 'accounts', label: '比赛账号', route: '/Admin?tab=users' },
  { key: 'devices', label: '比赛设备', route: '/Admin?tab=training' },
  { key: 'settings', label: '平台设置', route: '/Admin?tab=settings' }
]

const managementItems = [
  { key: 'home', label: '主页', route: '/management' },
  { key: 'courses', label: '课程管理', route: '/management/courses' },
  { key: 'resources', label: '资源管理', route: '/management/resources' },
  { key: 'training', label: '实训管理', route: '/management/training' },
  { key: 'competition', label: '竞赛管理', children: competitionItems },
  { key: 'users', label: '用户管理', route: '/management/users' },
  { key: 'devices', label: '设备管理', route: '/management/devices' }
]

function landingRoute (role) {
  if (role === SUPER_ADMIN) return { path: '/operations' }
  if (role === ADMIN) return { path: '/management' }
  return { path: '/course-platform' }
}

module.exports = { USER, ADMIN, SUPER_ADMIN, userItems, competitionItems, managementItems, landingRoute }
```

Extend `auth.js` with `getRole()`, `hasRole(role)`, and `landingRoute()` based on
the stored user info. Keep `isAdmin()` temporarily as `ADMIN || SUPER_ADMIN` for
legacy components, but use exact role checks in new route guards.

- [ ] **Step 4: Run the policy test**

Run: `npm --prefix vue run test:navigation`

Expected: `role navigation tests passed`.

- [ ] **Step 5: Commit the frontend policy**

```powershell
git add vue/src/navigation/roleNavigation.js vue/scripts/test-role-navigation.js vue/package.json vue/src/utils/auth.js vue/src/store/modules/Match.js
git commit -m "feat: define role-based navigation policy"
```

### Task 6: Add Role-Specific Route Shells

**Files:**
- Create: `vue/src/layouts/NormalUserShell.vue`
- Create: `vue/src/layouts/ManagementShell.vue`
- Create: `vue/src/layouts/OperationsShell.vue`
- Create: `vue/src/views/user/CoursePlatform.vue`
- Create: `vue/src/views/user/ResourceCenter.vue`
- Create: `vue/src/views/user/TrainingEnvironment.vue`
- Create: `vue/src/views/management/ManagementHome.vue`
- Create: `vue/src/views/management/CourseManagement.vue`
- Create: `vue/src/views/management/ResourceManagement.vue`
- Create: `vue/src/views/management/TrainingManagement.vue`
- Create: `vue/src/views/management/UserManagement.vue`
- Create: `vue/src/views/management/DeviceManagement.vue`
- Create: `vue/src/views/operations/OperationsHome.vue`
- Create: `vue/src/views/operations/AdministratorManagement.vue`
- Create: `vue/src/views/ChangePassword.vue`
- Modify: `vue/src/router/index.js`

- [ ] **Step 1: Extend navigation tests with route-role expectations**

Add assertions that `routeRoles` maps `/operations` to `SUPER_ADMIN`,
`/management` to `ADMIN`, and `/course-platform` to `USER`.

- [ ] **Step 2: Run the test and confirm failure**

Run: `npm --prefix vue run test:navigation`

Expected: failure because `routeRoles` is absent.

- [ ] **Step 3: Implement shells and route metadata**

Use `el-container`, `el-aside`, and `el-menu` in each shell. The shells render
only their role's items from `roleNavigation.js`. Add router records with exact
metadata:

```javascript
{ path: '/operations', component: OperationsShell, meta: { roles: ['SUPER_ADMIN'] }, children: [...] }
{ path: '/management', component: ManagementShell, meta: { roles: ['ADMIN'] }, children: [...] }
{ path: '/course-platform', component: NormalUserShell, meta: { roles: ['USER'] }, children: [...] }
```

The global guard reads `getRole()`. A role mismatch redirects to that role's
landing route instead of showing a blank page. After login, route directly to
`landingRoute(response.data.role)`.

Phase-one landing pages must contain a concise title, current-role context, and
stable empty/loading states. They must not claim that Agent, course, resource,
training, or device features already work.

Add a role-neutral authenticated route:

```javascript
{ path: '/change-password', name: 'ChangePassword', component: ChangePassword }
```

`ChangePassword.vue` reuses `changePasswordApi`, validates a minimum of six
characters, rejects reuse of the current password, and redirects through
`landingRoute(getRole())` after success. The global guard redirects every logged
in account with `mustChangePassword()` to `/change-password`, regardless of
role. This keeps the initial `admin/admin` bootstrap compatible with the new
super-administrator shell.

- [ ] **Step 4: Run navigation tests and production build**

Run:

```powershell
npm --prefix vue run test:navigation
npm --prefix vue run build
```

Expected: navigation test passes and Vue production build completes without errors.

- [ ] **Step 5: Commit role shells**

```powershell
git add vue/src/layouts vue/src/views/user vue/src/views/management vue/src/views/operations vue/src/views/ChangePassword.vue vue/src/router/index.js vue/src/navigation/roleNavigation.js vue/scripts/test-role-navigation.js
git commit -m "feat: add role-specific application shells"
```

### Task 7: Integrate Existing Competition Management And Preview

**Files:**
- Modify: `vue/src/views/Layout.vue`
- Modify: `vue/src/views/Admin.vue`
- Modify: `vue/src/assets/style/layout.css`
- Create: `vue/src/views/management/CompetitionPreview.vue`
- Modify: `vue/src/router/index.js`
- Test: `vue/scripts/test-role-navigation.js`

- [ ] **Step 1: Add failing competition-route assertions**

Assert that all eight competition children resolve to a route and that preview
defines four reused destinations: `/Publicity`, `/Home`, `/Question`, `/Detect`.

- [ ] **Step 2: Run the test and confirm failure**

Run: `npm --prefix vue run test:navigation`

Expected: failure because preview destinations are absent.

- [ ] **Step 3: Implement competition grouping and preview**

- Render Competition Management as one expandable `el-submenu` in
  `ManagementShell.vue`.
- Keep existing `Admin.vue` tab implementations and map labels to Competition
  Control, Schedule and Rules Editor, Paper Questions, Paper Grading,
  Competition Accounts, Competition Devices, and Platform Settings.
- Remove server creation and port-allocation controls from the normal-admin
  Competition Devices view by guarding those controls behind `SUPER_ADMIN`;
  the normal-admin view remains status-only until Phase 2 supplies Agent data.
- `CompetitionPreview.vue` provides four local tabs and renders the existing
  participant routes without duplicating their business logic.
- Preserve the participant competition routes for the later competition-mode
  switch, but do not show them in the normal user's three-item shell in Phase 1.

- [ ] **Step 4: Verify tests and responsive build**

Run:

```powershell
npm --prefix vue run test:navigation
npm --prefix vue run build
```

Expected: pass. Then verify at widths 1440, 1024, and 390 pixels that menu text
does not overlap and Competition Management expands without changing page width.

- [ ] **Step 5: Commit competition integration**

```powershell
git add vue/src/views/Layout.vue vue/src/views/Admin.vue vue/src/views/management/CompetitionPreview.vue vue/src/assets/style/layout.css vue/src/router/index.js vue/src/navigation/roleNavigation.js vue/scripts/test-role-navigation.js
git commit -m "feat: group existing competition management"
```

### Task 8: Add The Super-Administrator Bootstrap UI

**Files:**
- Create: `vue/src/api/SuperAdmin.js`
- Modify: `vue/src/views/operations/AdministratorManagement.vue`
- Modify: `vue/src/layouts/OperationsShell.vue`
- Test: `vue/scripts/test-role-navigation.js`

- [ ] **Step 1: Add operations-menu assertions**

Assert the Phase 1 operations menu contains exactly `运维主页` and `管理员账号`.
Server, container, terminal, and license items are reserved for later phases and
must not appear as functional controls yet.

- [ ] **Step 2: Run test and confirm failure**

Run: `npm --prefix vue run test:navigation`

Expected: failure because operations menu is absent.

- [ ] **Step 3: Implement the API client and page**

`SuperAdmin.js` exposes:

```javascript
export const listAdministrators = () => request.get('super-admin/administrators')
export const createAdministrator = data => request.post('super-admin/administrators', data)
export const updateAdministrator = (id, data) => request.put(`super-admin/administrators/${id}`, data)
export const resetAdministratorPassword = (id, data) => request.post(`super-admin/administrators/${id}/reset-password`, data)
```

The page supports list, create, enable/disable, and reset password. It never
renders stored passwords. Reset shows the newly supplied temporary password only
in the confirmation form and clears it after the request completes.

- [ ] **Step 4: Run frontend and backend verification**

Run:

```powershell
npm --prefix vue run test:navigation
npm --prefix vue run build
mvn -f java/match-mgr/pom.xml test
```

Expected: all commands pass.

- [ ] **Step 5: Commit the bootstrap UI**

```powershell
git add vue/src/api/SuperAdmin.js vue/src/views/operations/AdministratorManagement.vue vue/src/layouts/OperationsShell.vue vue/src/navigation/roleNavigation.js vue/scripts/test-role-navigation.js
git commit -m "feat: add administrator bootstrap workspace"
```

### Task 9: Final Phase 1 Verification And Documentation

**Files:**
- Modify: `README.md`
- Create: `docs/phase-1-acceptance.md`

- [ ] **Step 1: Run the complete verification set**

```powershell
mvn -f java/match-mgr/pom.xml test
npm --prefix vue run test:navigation
npm --prefix vue run build
node vue/scripts/test-t100-paper.js
```

Expected: all commands pass with exit code 0.

- [ ] **Step 2: Run the Docker build and health checks when base images are available**

```powershell
docker compose --env-file .env -f compose.prod.yml build java vue
docker compose --env-file .env -f compose.prod.yml up -d
curl.exe -f http://localhost:19141/health
curl.exe -f http://localhost:19140/
```

Expected: both HTTP checks return success.

- [ ] **Step 3: Execute the role acceptance matrix**

Record evidence in `docs/phase-1-acceptance.md`:

```text
SUPER_ADMIN -> /operations; can manage ADMIN accounts; cannot enter /management
ADMIN       -> /management; sees seven first-level items and eight competition children
USER        -> /course-platform; sees only Course Platform, Resource Center, Training Environment
```

Also verify disabled accounts cannot log in and each role is redirected away
from another role's URL.

- [ ] **Step 4: Update README startup and repository-boundary instructions**

Document that `competition` is reference-only, XKP5.0 work occurs in this
repository, the default branch is `main`, and runtime `.env` files are never committed.

- [ ] **Step 5: Commit Phase 1 acceptance**

```powershell
git add README.md docs/phase-1-acceptance.md
git commit -m "docs: record phase one acceptance"
```
