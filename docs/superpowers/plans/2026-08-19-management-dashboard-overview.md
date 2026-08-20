# Management Dashboard Overview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Replace the normal-administrator home placeholders with a tested aggregate platform overview and five-minute active-user count.

**Architecture:** Persist one digest-keyed activity row per authenticated session and expose a lightweight touch endpoint. A dashboard service performs bounded aggregate queries over existing Agent, environment, operation, command, and license state, returning one snapshot consumed by the existing Vue management home.

**Tech Stack:** Java 8, Spring Boot 2.1, Sa-Token, MyBatis-Plus, Flyway, MySQL 8, JUnit 4, Mockito, Vue 2, Element UI, Node contract tests, Docker BuildKit.

---

## Task 1: Persist Authenticated Session Activity

**Files:**
- Create: `java/match-mgr/src/main/resources/db/migration/V23__user_session_activity.sql`
- Create: `java/match-mgr/src/main/java/com/match/dashboard/persistence/UserSessionActivityRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/dashboard/persistence/UserSessionActivityMapper.java`
- Create: `java/match-mgr/src/test/java/com/match/dashboard/persistence/UserSessionActivitySchemaTest.java`

- [x] Write a schema test requiring `session_digest`, `user_id`, `login_at`, `last_activity_at`, `expires_at`, a unique digest, and indexes beginning with `expires_at` and `(user_id, expires_at)`.
- [x] Run `UserSessionActivitySchemaTest` through `.xkp5-test-runtime/java-test.Dockerfile`; verify it fails because V23 is missing.
- [x] Add V23 using `CHAR(64)` for the SHA-256 session digest and UTC `DATETIME(3)` fields.
- [x] Add a record plus mapper methods `upsertActivity`, `deleteSession`, `countDistinctActiveUsers`, and bounded `deleteExpired`.
- [x] Re-run the schema test and commit `feat: track authenticated user activity`.

The mapper contract is:

```java
int upsertActivity(String sessionDigest, int userId, LocalDateTime loginAt,
                   LocalDateTime lastActivityAt, LocalDateTime expiresAt);
int deleteSession(String sessionDigest);
int countDistinctActiveUsers(LocalDateTime cutoff);
int deleteExpired(LocalDateTime cutoff, int limit);
```

## Task 2: Record Login, Activity, And Logout

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/dashboard/service/UserActivityService.java`
- Create: `java/match-mgr/src/test/java/com/match/dashboard/service/UserActivityServiceTest.java`
- Modify: `java/match-mgr/src/main/java/com/match/controller/UserController.java`
- Modify: `java/match-mgr/src/test/java/com/match/controller/UserControllerTest.java`

- [x] Write service tests with a fixed `Clock` proving token values are SHA-256 digested, login/touch set a five-minute expiry, distinct sessions remain separate, logout deletes only the current digest, and empty tokens are rejected.
- [x] Run the tests and verify failure because the service does not exist.
- [x] Implement `recordLogin`, `touch`, `logout`, and `onlineUserCount`; never persist or log the raw token.
- [x] Write controller tests requiring successful login to record activity, `POST /user/activity` to touch only an authenticated session, and logout to clear activity before `StpUtil.logout()`.
- [x] Implement the controller integration and re-run targeted tests.
- [x] Commit `feat: record online user activity`.

The controller endpoint remains intentionally small:

```java
@PostMapping("activity")
public ResponseResult<Object> activity() {
    userActivityService.touch(StpUtil.getLoginIdAsInt(), StpUtil.getTokenValue());
    return Response.makeOKRsp("ok");
}
```

## Task 3: Build The Dashboard Snapshot

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/dashboard/model/DashboardOverview.java`
- Create: `java/match-mgr/src/main/java/com/match/dashboard/model/DashboardResourceSummary.java`
- Create: `java/match-mgr/src/main/java/com/match/dashboard/model/DashboardAlertSummary.java`
- Create: `java/match-mgr/src/main/java/com/match/dashboard/persistence/DashboardOverviewMapper.java`
- Create: `java/match-mgr/src/main/java/com/match/dashboard/service/DashboardOverviewService.java`
- Create: `java/match-mgr/src/main/java/com/match/dashboard/web/AdminDashboardController.java`
- Create: `java/match-mgr/src/test/java/com/match/dashboard/service/DashboardOverviewServiceTest.java`
- Create: `java/match-mgr/src/test/java/com/match/dashboard/web/AdminDashboardControllerTest.java`

- [x] Write service tests for enabled/online/offline Agents using the existing 15-second heartbeat window; environment `RUNNING`, transitional, `DEGRADED`, and `ERROR` counts; pending/failed operations and commands; distinct online users; and license unusable/expiring categories.
- [x] Write resource tests that parse each online Agent's `latest_metrics`, average only non-null values, report sample counts, and return `null` rather than zero when no sample exists.
- [x] Run the tests and verify failure because the overview service is missing.
- [x] Implement bounded aggregate mapper queries and pure snapshot assembly using an injected `Clock`, `ObjectMapper`, `UserActivityService`, and `LicenseStatusService`.
- [x] Write controller tests proving only `ADMIN` can call `GET /admin/dashboard/overview`; `USER` and `SUPER_ADMIN` receive forbidden responses.
- [x] Implement the controller through `AdminGuard.requireAdmin()` and return `Response.makeOKRsp(overviewService.snapshot())`.
- [x] Re-run targeted dashboard tests and commit `feat: aggregate management dashboard overview`.

Resource output uses this shape:

```json
{
  "cpu": { "averagePercent": 31.4, "sampleCount": 2 },
  "gpu": { "averagePercent": null, "sampleCount": 0 },
  "memory": { "averagePercent": 54.2, "sampleCount": 2 },
  "disk": { "averagePercent": 62.1, "sampleCount": 2 }
}
```

## Task 4: Connect The Management Home

**Files:**
- Create: `vue/src/api/DashboardOverview.js`
- Create: `vue/src/services/userActivity.js`
- Create: `vue/scripts/test-dashboard-overview-contract.js`
- Modify: `vue/package.json`
- Modify: `vue/src/views/Layout.vue`
- Modify: `vue/src/views/management/ManagementHome.vue`

- [x] Add a Node contract test requiring the overview API, four metric values, fixed-size resource rows, 15-second refresh, stale-data retention, no-data rendering, and teardown of timers/listeners.
- [x] Run `npm run test:dashboard` and verify it fails because the API and page contract are absent.
- [x] Add `getDashboardOverview()` and a user-activity helper that sends at most one touch per minute while an authenticated page is visible.
- [x] Start/stop the activity helper in the authenticated layout lifecycle without extending authentication expiry.
- [x] Replace placeholder metrics with the returned snapshot, add restrained CPU/GPU/memory/disk progress rows, keep old data on refresh failure, and label first-load failures as unavailable.
- [x] Re-run `npm run test:dashboard` and the existing navigation/license/Agent/template contract tests.
- [x] Run the production Vue build and commit `feat: connect management dashboard overview`.

The refresh lifecycle must remain bounded:

```javascript
mounted () {
  this.loadOverview()
  this.refreshTimer = setInterval(this.loadOverview, 15000)
},
beforeDestroy () {
  clearInterval(this.refreshTimer)
}
```

`loadOverview` returns immediately when `loading` is already true and changes
`stale` without clearing `snapshot` when a refresh fails.

## Task 5: Full Verification And Documentation

**Files:**
- Modify: `docs/operations/processing-agent.md`
- Modify: `docs/superpowers/plans/2026-08-19-management-dashboard-overview.md`

- [x] Document the five-minute online-user definition, 15-second Agent online window, resource averaging, missing-data behavior, and initial alert categories.
- [x] Run all Java tests with `TEST_PATTERN=*Test` using the Docker test image and require `Failures: 0, Errors: 0`.
- [x] Run all frontend contract tests and `npm run build`.
- [x] Run `git diff --check` and inspect `git status --short` for unintended files.
- [x] Mark completed plan checkboxes and commit `docs: document dashboard overview operations`.

## Acceptance Boundary

The increment is complete when an ordinary administrator sees real aggregate
values, online users follow the fixed five-minute rule, missing metrics are not
displayed as zero, stale refreshes retain the last snapshot, unauthorized roles
cannot access the endpoint, and the complete Java and Vue verification suites
pass. No real processing-server SSH access is required.
