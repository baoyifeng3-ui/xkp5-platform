# Reliable Agent Power Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a durable, authenticated management-to-Agent command channel with Wake-on-LAN and safe Ubuntu shutdown controls.

**Architecture:** The management server owns command persistence, leasing, authorization, auditing, WOL transmission, and shutdown reconciliation. The Go Agent strictly decodes the versioned command envelope, acknowledges start before direct OS execution, and reports bounded structured results without ever accepting shell text.

**Tech Stack:** Java 8, Spring Boot 2.1, MyBatis-Plus, Flyway, MySQL 8, Vue 2, Element UI, Go 1.22, JUnit 4, Go `testing`, Docker BuildKit.

---

## File Structure

Platform repository (`xkp5-platform`, branch `feature/phase-1-platform-shell`):

- `java/match-mgr/src/main/resources/db/migration/V20__processing_agent_commands.sql`: durable command table and indexes.
- `java/match-mgr/src/main/java/com/match/agent/model/AgentCommand*.java`: closed protocol and management DTOs.
- `java/match-mgr/src/main/java/com/match/agent/persistence/ProcessingAgentCommand*.java`: command record and atomic SQL operations.
- `java/match-mgr/src/main/java/com/match/agent/service/AgentCommandService.java`: create, lease, start, finish, expire, and query state transitions.
- `java/match-mgr/src/main/java/com/match/agent/service/AgentPowerService.java`: role/license-aware wake and shutdown operations.
- `java/match-mgr/src/main/java/com/match/agent/service/WakeOnLanSender.java`: magic-packet construction and UDP delivery.
- `java/match-mgr/src/main/java/com/match/agent/service/ShutdownReconciler.java`: heartbeat-loss confirmation and timeout failure.
- `java/match-mgr/src/main/java/com/match/agent/web/AgentController.java`: Agent poll/start/result endpoints.
- `java/match-mgr/src/main/java/com/match/agent/web/AdminAgentController.java`: administrator wake/shutdown/history endpoints.
- `vue/src/api/ProcessingAgents.js`: power and command-history API calls.
- `vue/src/views/operations/ProcessingAgents.vue`: super-administrator power and maintenance controls.
- `vue/src/views/management/DeviceManagement.vue`: normal-administrator power controls and command feedback.

Agent repository (`xkp-agent`, branch `main`):

- `internal/protocol/command.go`: strict version-one command envelope and acknowledgements.
- `internal/client/client.go`: start/result protocol methods.
- `internal/power/controller.go`: platform-neutral power interface.
- `internal/power/controller_linux.go`: direct `systemctl poweroff` implementation.
- `internal/power/controller_unsupported.go`: fail-closed non-Linux implementation.
- `internal/runtime/command_dispatcher.go`: allowlisted dispatch and start-before-execute ordering.
- `internal/runtime/agent.go`: invoke the dispatcher for leased commands.
- `cmd/xkp-agent/main.go`: wire the production power controller.

## Task 1: Freeze The Version-One Command Contract

**Files:**
- Create: `java/match-mgr/src/test/resources/fixtures/agent-command-shutdown-v1.json`
- Create: `java/match-mgr/src/test/java/com/match/agent/model/AgentCommandFixtureContractTest.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/model/AgentCommandEnvelope.java`
- Create: `testdata/command-shutdown-v1.json` in `xkp-agent`
- Create: `internal/protocol/command.go` in `xkp-agent`
- Create: `internal/protocol/command_test.go` in `xkp-agent`

- [ ] **Step 1: Add identical JSON fixtures to both repositories**

```json
{
  "commandId": "11111111-2222-4333-8444-555555555555",
  "type": "SHUTDOWN_SERVER",
  "version": 1,
  "leaseToken": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
  "leaseExpiresAt": "2026-08-19T12:05:00Z",
  "payload": {}
}
```

- [ ] **Step 2: Write failing Java and Go contract tests**

Java must assert exact field names, command type, version, and empty payload. Go must decode with `json.Decoder.DisallowUnknownFields()`, reject trailing JSON, require UUID-shaped IDs, require version `1`, and reject a payload larger than 4 KiB.

```go
func TestDecodeCommandRejectsUnknownFields(t *testing.T) {
    _, err := protocol.DecodeCommand([]byte(`{"commandId":"11111111-2222-4333-8444-555555555555","type":"SHUTDOWN_SERVER","version":1,"leaseToken":"aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee","leaseExpiresAt":"2026-08-19T12:05:00Z","payload":{},"shell":"rm"}`))
    if err == nil { t.Fatal("expected unknown field rejection") }
}
```

- [ ] **Step 3: Run tests and verify the missing decoder/model failures**

Run in `xkp-agent`: `go test ./internal/protocol -run TestDecodeCommand -count=1`

Run from the project directory for Java:

```powershell
docker build --progress=plain -f .tmp-xkp5-java-test.Dockerfile --build-arg TEST_PATTERN=AgentCommandFixtureContractTest -t xkp5-command-contract-test .
```

Expected: both fail because the version-one protocol types do not exist.

- [ ] **Step 4: Implement the strict Go decoder and Java DTO**

The Go type is closed and contains `json.RawMessage` only for `payload`; decoding validates size before parsing. The Java DTO exposes only getters for the six fixture fields and serializes the payload as an empty object for shutdown.

- [ ] **Step 5: Run both contract tests and commit each repository**

Expected: Go package passes; Java targeted test reports `BUILD SUCCESS`.

```powershell
git add testdata internal/protocol
git commit -m "feat: define strict agent command protocol"
```

```powershell
git add java/match-mgr/src/test/resources/fixtures java/match-mgr/src/test/java/com/match/agent/model java/match-mgr/src/main/java/com/match/agent/model
git commit -m "feat: define agent command contract"
```

## Task 2: Persist And Atomically Lease Commands

**Files:**
- Create: `java/match-mgr/src/main/resources/db/migration/V20__processing_agent_commands.sql`
- Create: `java/match-mgr/src/main/java/com/match/agent/persistence/ProcessingAgentCommandRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/persistence/ProcessingAgentCommandMapper.java`
- Create: `java/match-mgr/src/test/java/com/match/agent/persistence/ProcessingAgentCommandSchemaTest.java`
- Create: `java/match-mgr/src/test/java/com/match/agent/service/AgentCommandServiceTest.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/service/AgentCommandService.java`

- [ ] **Step 1: Write the migration-shape test**

Assert that V20 defines `command_id`, `agent_id`, `command_type`, `command_version`, `payload_json`, `state`, `requester_user_id`, `requester_role`, `correlation_id`, `available_at`, `lease_token`, `lease_expires_at`, `attempt_count`, `started_at`, `completed_at`, `result_code`, `result_message`, and `result_json`; require indexes on `(agent_id,state,available_at)` and `(agent_id,requested_at)`.

- [ ] **Step 2: Run the schema test and verify it fails for missing V20**

Run: Java Docker test with `TEST_PATTERN=ProcessingAgentCommandSchemaTest`.

- [ ] **Step 3: Add V20 with bounded columns and UTC millisecond timestamps**

Use `VARCHAR(36)` UUIDs, `VARCHAR(32)` type/state, `SMALLINT UNSIGNED` version/attempt count, `JSON` payload/result, `VARCHAR(64)` result code, `VARCHAR(512)` result message, and no foreign key so soft-removed Agent history survives.

- [ ] **Step 4: Write service tests for create, duplicate shutdown, lease, lease expiry, and attempt exhaustion**

```java
@Test
public void secondShutdownReturnsExistingNonTerminalCommand() {
    AgentCommandView first = service.requestShutdown(agent, actor);
    AgentCommandView second = service.requestShutdown(agent, actor);
    assertEquals(first.getCommandId(), second.getCommandId());
    verify(mapper, times(1)).insert(any(ProcessingAgentCommandRecord.class));
}
```

Also assert an atomic claim updates only `PENDING` rows whose `available_at <= now`, creates a fresh lease UUID, increments attempts, and leases at most one command.

- [ ] **Step 5: Implement the record, mapper SQL, and minimal service**

Use a transaction with `SELECT ... FOR UPDATE SKIP LOCKED` on MySQL 8 followed by a conditional update. A single lease attempt returns empty when no row is claimable. Expired `LEASED` rows return to `PENDING` only below the configured maximum of five attempts; exhausted rows become `FAILED` with `DELIVERY_ATTEMPTS_EXHAUSTED`.

- [ ] **Step 6: Run targeted tests and commit**

Expected: schema and command service tests pass.

```powershell
git add java/match-mgr/src/main/resources/db/migration/V20__processing_agent_commands.sql java/match-mgr/src/main/java/com/match/agent java/match-mgr/src/test/java/com/match/agent
git commit -m "feat: persist and lease agent commands"
```

## Task 3: Enforce Start And Result State Transitions

**Files:**
- Modify: `java/match-mgr/src/main/java/com/match/agent/service/AgentCommandService.java`
- Modify: `java/match-mgr/src/main/java/com/match/agent/persistence/ProcessingAgentCommandMapper.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/model/AgentCommandStartRequest.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/model/AgentCommandResultRequest.java`
- Modify: `java/match-mgr/src/test/java/com/match/agent/service/AgentCommandServiceTest.java`

- [ ] **Step 1: Add failing transition tests**

Cover matching lease `LEASED -> RUNNING`, stale token conflict, foreign Agent conflict, `RUNNING -> SUCCEEDED|FAILED`, duplicate identical terminal result idempotency, conflicting terminal result rejection, and a 512-character result-message limit.

- [ ] **Step 2: Verify targeted tests fail**

Run Java Docker test with `TEST_PATTERN=AgentCommandServiceTest`.

- [ ] **Step 3: Implement conditional SQL transitions**

Each update includes `command_id`, authenticated `agent_id`, expected state, and `lease_token` in its predicate. Read-after-zero-update distinguishes an identical terminal retry from a conflict. Stable result codes match `^[A-Z0-9_]{1,64}$`; result JSON is optional and bounded to 16 KiB before persistence.

- [ ] **Step 4: Run tests and commit**

Expected: all command service tests pass.

```powershell
git add java/match-mgr/src/main/java/com/match/agent java/match-mgr/src/test/java/com/match/agent
git commit -m "feat: validate agent command acknowledgements"
```

## Task 4: Expose The Authenticated Agent Protocol

**Files:**
- Modify: `java/match-mgr/src/main/java/com/match/agent/web/AgentController.java`
- Modify: `java/match-mgr/src/main/java/com/match/agent/model/AgentCommandPollResponse.java`
- Modify: `java/match-mgr/src/test/java/com/match/agent/web/AgentControllerTest.java`
- Modify: `java/match-mgr/src/test/java/com/match/agent/web/AgentProtocolExceptionHandlerTest.java`

- [ ] **Step 1: Write failing controller tests**

Assert poll passes the authenticated `ProcessingAgentRecord` to the command service, returns zero or one typed command, waits no longer than the requested 0..25 seconds, start/result use the same authenticated Agent, invalid wait remains HTTP 400, and stale lease conflicts return HTTP 409 with stable codes.

- [ ] **Step 2: Run controller tests and confirm failure**

Run Java Docker test with `TEST_PATTERN=AgentControllerTest,AgentProtocolExceptionHandlerTest`.

- [ ] **Step 3: Add the service dependency and endpoints**

```java
@PostMapping("/commands/{commandId}/start")
public AgentCommandAck start(@RequestHeader("Authorization") String authorization,
                             @PathVariable String commandId,
                             @RequestBody AgentCommandStartRequest request) {
    ProcessingAgentRecord agent = credentialService.authenticate(authorization);
    return commandService.start(agent, commandId, request);
}
```

Add the equivalent `/result` endpoint. Keep transaction scope inside each atomic lease attempt; a small `AgentCommandPoller` with an injected sleeper retries at 250 ms intervals until a command appears or the requested deadline expires, so no database transaction remains open during a long poll.

- [ ] **Step 4: Run tests and commit**

Expected: protocol controller tests pass.

```powershell
git add java/match-mgr/src/main/java/com/match/agent java/match-mgr/src/test/java/com/match/agent
git commit -m "feat: expose durable agent command API"
```

## Task 5: Add Role-Safe Wake And Shutdown Administration

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/agent/service/WakeOnLanSender.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/service/AgentPowerService.java`
- Modify: `java/match-mgr/src/main/java/com/match/agent/web/AdminAgentController.java`
- Create: `java/match-mgr/src/test/java/com/match/agent/service/WakeOnLanSenderTest.java`
- Create: `java/match-mgr/src/test/java/com/match/agent/service/AgentPowerServiceTest.java`
- Modify: `java/match-mgr/src/test/java/com/match/agent/web/AgentRoleBoundaryTest.java`

- [ ] **Step 1: Write failing magic-packet tests**

For MAC `01:23:45:67:89:ab`, assert exactly 102 bytes: six `0xff` bytes and sixteen repetitions of `01 23 45 67 89 ab`. Reject multicast, broadcast, all-zero, malformed, and missing MAC addresses before opening a socket.

- [ ] **Step 2: Implement packet construction and injectable UDP sending**

Default properties are `xkp.agent.wol.broadcast-address=255.255.255.255` and `xkp.agent.wol.port=9`. The sender enables broadcast and closes the datagram socket in all outcomes.

- [ ] **Step 3: Write failing authorization and lifecycle tests**

Assert normal and super administrators may wake/shutdown with an active license; normal administrator mutations fail with an unusable license; super administrator wake/shutdown remains allowed for maintenance; users are denied; offline shutdown does not queue; offline wake is allowed; disabled/removed Agents reject both operations.

- [ ] **Step 4: Implement power service and controller endpoints**

`RoleGuard.requireAnyAdmin()` returns the actor. `AgentPowerService` calls `LicenseGuard.requireActive()` unless `roleGuard.roleOf(actor) == SUPER_ADMIN`; that maintenance exception is local to this service. Wake records `WAKE_PACKET_SENT` or `WAKE_PACKET_FAILED`; shutdown delegates to the durable queue and records `SHUTDOWN_REQUESTED`.

- [ ] **Step 5: Run targeted tests and commit**

Expected: power, role boundary, and WOL tests pass.

```powershell
git add java/match-mgr/src/main/java/com/match/agent java/match-mgr/src/test/java/com/match/agent java/match-mgr/src/main/resources
git commit -m "feat: add processing server power controls"
```

## Task 6: Reconcile Shutdown By Heartbeat Loss

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/agent/service/ShutdownReconciler.java`
- Create: `java/match-mgr/src/test/java/com/match/agent/service/ShutdownReconcilerTest.java`
- Modify: `java/match-mgr/src/main/java/com/match/agent/persistence/ProcessingAgentCommandMapper.java`

- [ ] **Step 1: Write failing reconciliation tests**

Given a running shutdown, mark it `SUCCEEDED/OFFLINE_CONFIRMED` only when `last_seen_at` is older than the offline threshold and after `started_at`; mark it `FAILED/SHUTDOWN_NOT_CONFIRMED` when the confirmation deadline passes while the Agent remains online; leave all other commands untouched.

- [ ] **Step 2: Run the test and verify missing reconciler failure**

Run Java Docker test with `TEST_PATTERN=ShutdownReconcilerTest`.

- [ ] **Step 3: Implement bounded scheduled reconciliation**

Run every five seconds, process at most 100 running shutdowns per pass, use the existing UTC `Clock`, offline threshold 15 seconds, and confirmation deadline 90 seconds. Conditional updates prevent the scheduler from overwriting a direct terminal result.

- [ ] **Step 4: Run tests and commit**

Expected: reconciler tests pass.

```powershell
git add java/match-mgr/src/main/java/com/match/agent java/match-mgr/src/test/java/com/match/agent
git commit -m "feat: reconcile shutdown command outcomes"
```

## Task 7: Execute Shutdown Safely In The Go Agent

**Files:**
- Modify: `internal/client/client.go` in `xkp-agent`
- Modify: `internal/client/client_test.go` in `xkp-agent`
- Create: `internal/power/controller.go` in `xkp-agent`
- Create: `internal/power/controller_linux.go` in `xkp-agent`
- Create: `internal/power/controller_unsupported.go` in `xkp-agent`
- Create: `internal/power/controller_test.go` in `xkp-agent`
- Create: `internal/runtime/command_dispatcher.go` in `xkp-agent`
- Create: `internal/runtime/command_dispatcher_test.go` in `xkp-agent`
- Modify: `internal/runtime/agent.go` in `xkp-agent`
- Modify: `internal/runtime/agent_test.go` in `xkp-agent`
- Modify: `cmd/xkp-agent/main.go` in `xkp-agent`

- [ ] **Step 1: Write failing client tests for start and result**

Assert bearer authentication, exact paths, start body `{ "leaseToken": "..." }`, result body with `success`, `code`, `message`, and optional object `details`, plus rejection of non-2xx responses.

- [ ] **Step 2: Implement `StartCommand` and `FinishCommand`**

Extend the runtime `Transport` interface with typed methods and reuse `authenticatedJSON`; never log lease tokens or response bodies.

- [ ] **Step 3: Write failing dispatcher ordering tests**

```go
func TestDispatcherAcknowledgesStartBeforePoweroff(t *testing.T) {
    order := []string{}
    transport := &fakeTransport{onStart: func() { order = append(order, "start") }}
    power := &fakePower{onShutdown: func() { order = append(order, "poweroff") }}
    err := NewCommandDispatcher(transport, power).Dispatch(context.Background(), shutdownCommand())
    if err != nil { t.Fatal(err) }
    want := []string{"start", "poweroff", "result"}
    if !reflect.DeepEqual(want, order) { t.Fatalf("order = %v, want %v", order, want) }
}
```

Also assert unknown type/version never calls start or power, cancellation is reported as `COMMAND_CANCELLED`, and power errors use `SHUTDOWN_FAILED` with a bounded plain-text message.

- [ ] **Step 4: Implement allowlisted dispatch**

The dispatcher switches on `SHUTDOWN_SERVER` version `1`, posts start, calls `PowerController.Shutdown(ctx)`, then posts a direct result only if the process survives. No payload field can influence executable or arguments.

- [ ] **Step 5: Write and implement OS-specific power tests**

Linux uses an injected runner with exact executable `systemctl` and args `poweroff`; the production runner calls `exec.CommandContext` directly. `//go:build !linux` returns `UNSUPPORTED_PLATFORM` without process execution.

- [ ] **Step 6: Wire the dispatcher into the poll loop and main**

Poll one command at a time. A command decode failure is reported only when its safe envelope identifiers were validated; otherwise it is discarded with a bounded local error. After handling a command, poll immediately; on transport errors use jittered backoff.

- [ ] **Step 7: Run race-enabled Go tests and commit**

Run: `go test -race ./... -count=1`

Expected: all Go packages pass.

```powershell
git add cmd internal testdata
git commit -m "feat: execute allowlisted shutdown commands"
```

## Task 8: Add Management UI Controls And History

**Files:**
- Modify: `vue/src/api/ProcessingAgents.js`
- Modify: `vue/src/views/operations/ProcessingAgents.vue`
- Modify: `vue/src/views/management/DeviceManagement.vue`
- Create: `vue/tests/unit/ProcessingAgents.spec.js` if the repository test harness supports unit tests
- Create: `vue/tests/unit/DeviceManagement.spec.js` if the repository test harness supports unit tests

- [ ] **Step 1: Add API functions**

```javascript
export const wakeProcessingAgent = id => request.post(`${ADMIN_BASE}/${id}/wake`)
export const shutdownProcessingAgent = id => request.post(`${ADMIN_BASE}/${id}/shutdown`)
export const listProcessingAgentCommands = id => request.get(`${ADMIN_BASE}/${id}/commands`)
```

- [ ] **Step 2: Add explicit confirmation and disabled-state tests**

Verify shutdown requires a destructive confirmation, wake never claims the server is online, disabled/removed rows expose no controls, an in-flight action cannot be submitted twice, and command state labels do not optimistically show success. Verify normal administrators can access the controls in `DeviceManagement`, while registration tokens and enable/disable/remove remain absent there.

- [ ] **Step 3: Implement compact power controls and command history**

Use icon buttons with tooltips in both selected-Agent operation bars. Show `已发送唤醒包`, `等待 Agent 接收`, `执行中`, `等待离线确认`, `已完成`, or the backend failure code/message. Keep registration token and enable/disable/remove controls exclusively in the existing `/operations/processing-agents` super-administrator route; do not duplicate them in normal administrator `DeviceManagement`.

- [ ] **Step 4: Build and commit**

Run: `npm run build` from `vue` (or the repository Docker frontend build when Node is unavailable).

Expected: production build succeeds without lint or template errors.

```powershell
git add vue/src/api/ProcessingAgents.js vue/src/views/operations/ProcessingAgents.vue vue/src/views/management/DeviceManagement.vue vue/tests
git commit -m "feat: add processing server power UI"
```

## Task 9: Cross-Repository Integration And Documentation

**Files:**
- Create: `deploy/tests/fake-agent-power-flow.ps1` in `xkp5-platform`
- Modify: `docs/operations/processing-agent.md` in `xkp5-platform`
- Modify: `README.md` in `xkp-agent`

- [ ] **Step 1: Add a non-destructive fake-Agent integration script**

The script registers a fake Agent, sends a heartbeat, queues shutdown, polls and starts the command, does not invoke host poweroff, waits past the test offline threshold, and asserts command history reaches `SUCCEEDED/OFFLINE_CONFIRMED`. It must use test-stack credentials from environment variables and never embed secrets.

- [ ] **Step 2: Document production prerequisites and safety boundaries**

Document Ubuntu systemd, Agent service privilege for `systemctl poweroff`, BIOS/UEFI WOL, NIC WOL, fixed LAN addresses, UDP broadcast configuration, TLS CA installation, and the fact that an HTTP success for wake means packet sent rather than machine online.

- [ ] **Step 3: Run full verification**

Platform Java: full Docker test image with `TEST_PATTERN=*Test`, expected current baseline plus new tests, zero failures/errors.

Agent: `go test -race ./... -count=1`, `GOOS=linux GOARCH=amd64 go build ./cmd/xkp-agent`, and `GOOS=windows GOARCH=amd64 go build ./cmd/xkp-agent`.

Frontend: production build succeeds.

Integration: fake-Agent flow reaches offline-confirmed success; duplicate shutdown returns the same command ID; stale lease receives HTTP 409; disabled Agent receives HTTP 403.

- [ ] **Step 4: Request code review and fix findings**

Review security boundaries, atomic state transitions, license maintenance exception, cross-repository JSON compatibility, build tags, and UI role visibility. Re-run the affected targeted test after every correction, then re-run all verification.

- [ ] **Step 5: Commit documentation and integration harness**

```powershell
git add deploy/tests/fake-agent-power-flow.ps1 docs/operations/processing-agent.md
git commit -m "test: verify processing server power flow"
```

```powershell
git add README.md
git commit -m "docs: document agent shutdown execution"
```

## Acceptance Boundary

This plan is complete when durable command delivery, WOL transmission, Ubuntu shutdown dispatch, role/license enforcement, management feedback, and the fake-Agent integration flow all pass. Real physical WOL and real shutdown remain explicitly unverified until an Ubuntu processing server with BIOS/NIC WOL enabled is available; the software must not claim those hardware acceptance checks passed.
