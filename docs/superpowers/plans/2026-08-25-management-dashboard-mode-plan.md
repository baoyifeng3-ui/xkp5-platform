# Management Dashboard and Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move business mode switching to the normal administrator homepage, preserve participant mode isolation, and show real per-server resource telemetry plus platform user totals.

**Architecture:** Extend the existing dashboard overview response instead of adding parallel polling APIs. Add network counters to the processing Agent heartbeat, normalize the latest heartbeat into per-agent rows in `DashboardOverviewService`, and render the confirmed dense overview layout in `ManagementHome.vue`.

**Tech Stack:** Spring Boot 2.1, MyBatis, Vue 2, Element UI, Go processing Agent.

---

### Task 1: Lock Navigation and Mode Behavior with Failing Contracts

**Files:**
- Modify: `vue/scripts/test-management-mode-contract.js`
- Modify: `vue/scripts/test-dashboard-overview-contract.js`
- Test: `java/match-mgr/src/test/java/com/match/mode/web/PlatformModeControllerTest.java`

- [ ] **Step 1: Add failing Vue assertions**

Assert that navigation does not contain `/management/competition-mode`, that the competition binding label is `比赛环境`, and that `ManagementHome.vue` contains `changePlatformMode`, `totalUsers`, `agentResources`, and a 5-second refresh interval.

```js
assert.ok(!router.includes("path: 'competition-mode'"))
assert.ok(!navigation.includes('模式与环境'))
assert.ok(navigation.includes("label: '比赛环境'"))
assert.ok(home.includes('changePlatformMode'))
assert.ok(home.includes('totalUsers'))
assert.ok(home.includes('agentResources'))
assert.ok(home.includes('5000'))
```

- [ ] **Step 2: Add a failing controller test**

Update `PlatformModeControllerTest` so an ordinary administrator may switch with only `targetMode`, while a super administrator is rejected by `requireBusinessAdmin()`.

```java
ChangePlatformModeRequest request = new ChangePlatformModeRequest();
request.setTargetMode(PlatformModeService.COMPETITION);
when(roleGuard.requireBusinessAdmin()).thenReturn(admin);
assertEquals(200, controller.change(request).getCode());
verify(platformModeService).change(PlatformModeService.COMPETITION, admin);
```

- [ ] **Step 3: Run tests and observe failures**

Run:

```powershell
cd vue
node scripts/test-management-mode-contract.js
node scripts/test-dashboard-overview-contract.js
docker run --rm -v "${PWD}/../java/match-mgr:/workspace" -v xkp5-m2-cache:/root/.m2 -w /workspace xkp5-full-java-tests mvn -q -Dtest=PlatformModeControllerTest test
```

Expected: failures mention the still-registered mode page, old menu label, missing homepage telemetry, and confirmation text validation.

### Task 2: Remove the Mode Page and Put Switching on the Homepage

**Files:**
- Delete: `vue/src/views/management/CompetitionMode.vue`
- Modify: `vue/src/router/index.js`
- Modify: `vue/src/navigation/roleNavigation.js`
- Modify: `vue/src/views/management/ManagementHome.vue`
- Modify: `java/match-mgr/src/main/java/com/match/mode/web/AdminPlatformModeController.java`
- Modify: `vue/src/api/PlatformMode.js`

- [ ] **Step 1: Remove the page import, route, and menu item**

Delete the `CompetitionMode` import and child route. Remove the `mode` item from `competitionItems`. Change only the binding menu label:

```js
{ key: 'accounts', label: '比赛环境', route: '/management/competition-accounts' }
```

- [ ] **Step 2: Simplify the mode API contract**

Remove `requireConfirmation()` from `AdminPlatformModeController`; validate only `TRAINING` or `COMPETITION` through `PlatformModeService.change`. Send only:

```js
export const changePlatformMode = targetMode => request.post(ADMIN_BASE, { targetMode })
```

- [ ] **Step 3: Add the homepage switch control**

Load `getPlatformMode()` with the dashboard snapshot. Use a normal confirmation dialog and keep the route unchanged:

```js
async togglePlatformMode () {
  const target = this.platformMode === 'COMPETITION' ? 'TRAINING' : 'COMPETITION'
  await this.$confirm(`确认${target === 'COMPETITION' ? '进入' : '退出'}比赛模式？普通用户现有登录会失效。`, '切换平台模式', { type: 'warning' })
  this.switchingMode = true
  try {
    const result = await changePlatformMode(target)
    this.platformMode = result.data.mode
    this.$message.success('平台模式已切换')
  } finally { this.switchingMode = false }
}
```

- [ ] **Step 4: Verify participant landing behavior remains unchanged**

Run `node scripts/test-role-navigation.js` and assert `landingRoute(USER, 'TRAINING')` returns `/course-platform` while competition returns `/Publicity`.

### Task 3: Add User Totals and Per-Agent Resources to the Dashboard API

**Files:**
- Modify: `java/match-mgr/src/main/java/com/match/dashboard/model/DashboardOverview.java`
- Create: `java/match-mgr/src/main/java/com/match/dashboard/model/DashboardAgentResource.java`
- Modify: `java/match-mgr/src/main/java/com/match/dashboard/persistence/DashboardOverviewMapper.java`
- Modify: `java/match-mgr/src/main/java/com/match/dashboard/service/DashboardOverviewService.java`
- Modify: `java/match-mgr/src/test/java/com/match/dashboard/service/DashboardOverviewServiceTest.java`

- [ ] **Step 1: Write failing dashboard service tests**

Build two `ProcessingAgentRecord` fixtures, one online and one offline. Assert total users include all enabled roles and each resource row preserves identity, online state, latest metrics, and last report time.

```java
when(mapper.countEnabledUsers()).thenReturn(128);
when(mapper.selectDashboardAgents()).thenReturn(Arrays.asList(onlineAgent, offlineAgent));
DashboardOverview result = service.snapshot();
assertEquals(128, result.getTotalUsers());
assertEquals(2, result.getAgentResources().size());
assertTrue(result.getAgentResources().get(0).isOnline());
assertFalse(result.getAgentResources().get(1).isOnline());
```

- [ ] **Step 2: Add mapper queries**

```java
@Select("SELECT COUNT(*) FROM user WHERE enabled = 1")
int countEnabledUsers();

@Select("SELECT agent_id, display_name, hostname, primary_ip, enabled, last_seen_at, latest_metrics "
      + "FROM processing_agent WHERE removed_at IS NULL ORDER BY display_name, agent_id")
List<ProcessingAgentRecord> selectDashboardAgents();
```

- [ ] **Step 3: Add the response model**

`DashboardAgentResource` exposes `agentId`, `displayName`, `primaryIp`, `online`, `lastSeenAt`, CPU/GPU/RAM/disk percentages, network receive/send bytes per second, and running container count. Nullable metrics remain `null` so Vue renders `--`.

- [ ] **Step 4: Map the snapshot atomically**

In `DashboardOverviewService.snapshot()`, set `totalUsers`, `onlineUsers`, current platform mode, and `agentResources`. Online means enabled and `lastSeenAt >= snapshotAt - 15 seconds`.

- [ ] **Step 5: Run focused Java tests**

```powershell
docker run --rm -v "${PWD}:/workspace" -v xkp5-m2-cache:/root/.m2 -w /workspace xkp5-full-java-tests mvn -q -Dtest=DashboardOverviewServiceTest test
```

Expected: all dashboard service tests pass.

### Task 4: Collect Network Rates in the Processing Agent

**Files:**
- Modify: `../xkp-agent/internal/collect/collector.go`
- Create: `../xkp-agent/internal/collect/network.go`
- Create: `../xkp-agent/internal/collect/network_test.go`

- [ ] **Step 1: Write failing parser and rate tests**

Test `/proc/net/dev` parsing excludes loopback and converts two cumulative samples into bytes per second.

```go
func TestNetworkRateExcludesLoopback(t *testing.T) {
  previous := netCounters{received: 1000, sent: 2000, at: time.Unix(0, 0)}
  current := netCounters{received: 5000, sent: 8000, at: time.Unix(2, 0)}
  got := rate(previous, current)
  assert.Equal(t, int64(2000), got.receivedPerSecond)
  assert.Equal(t, int64(3000), got.sentPerSecond)
}
```

- [ ] **Step 2: Add heartbeat fields**

```go
NetworkReceiveBytesPerSecond *int64 `json:"networkReceiveBytesPerSecond"`
NetworkSendBytesPerSecond    *int64 `json:"networkSendBytesPerSecond"`
```

- [ ] **Step 3: Implement sampling**

Read `/proc/net/dev`, sum non-loopback interfaces, retain the previous sample in the collector, and emit rates only after two valid samples. Counter reset yields `nil`, never a negative rate.

- [ ] **Step 4: Extend the Java metric model**

Add matching nullable `Long` fields to `AgentMetricSnapshot` and confirm Jackson parses an old heartbeat with fields absent.

- [ ] **Step 5: Run Agent and Java tests**

```powershell
cd ..\xkp-agent
go test ./internal/collect ./internal/runtime
cd ..\xkp5-platform\java\match-mgr
docker run --rm -v "${PWD}:/workspace" -v xkp5-m2-cache:/root/.m2 -w /workspace xkp5-full-java-tests mvn -q -Dtest=DashboardOverviewServiceTest test
```

### Task 5: Render the Confirmed Dense Homepage Layout

**Files:**
- Modify: `vue/src/views/management/ManagementHome.vue`
- Modify: `vue/scripts/test-dashboard-overview-contract.js`

- [ ] **Step 1: Replace the contact panel with user statistics**

Render total, online, and offline users. Keep the existing course/resource category shortcuts and one-click class dialog.

- [ ] **Step 2: Add the server resource table**

Columns: server, state, CPU, GPU, memory, disk, receive, send, containers, last report. Use compact progress bars for percentages and `formatRate()` for network values.

- [ ] **Step 3: Poll every five seconds without overlap**

Use one dashboard request guarded by `refreshing`; schedule the next refresh only after the current call completes or keep a single interval with an in-flight guard.

- [ ] **Step 4: Run contracts and build**

```powershell
cd vue
node scripts/test-management-mode-contract.js
node scripts/test-dashboard-overview-contract.js
npm run build
```

Expected: contracts pass; production build completes with only existing asset-size warnings.

### Task 6: Deploy and Verify Both Roles and Modes

**Files:**
- Modify only if needed: `docs/PROJECT_HANDOVER.md`

- [ ] **Step 1: Build and deploy the Java service and Agent package**

Build the tested JAR, run Flyway if any migration is introduced by adjacent resource work, rebuild the Agent Linux binary, and refresh the downloadable deployment package.

- [ ] **Step 2: Verify APIs**

Log in as normal administrator, call `/admin/dashboard/overview`, and assert `totalUsers`, `onlineUsers`, `platformMode`, and `agentResources` exist. Verify a super administrator receives 403 from `POST /admin/platform-mode`.

- [ ] **Step 3: Verify browser behavior**

At desktop and mobile widths, verify no overlap, switch mode using the homepage confirmation, and log in as a normal user in each mode to confirm the correct landing page.

