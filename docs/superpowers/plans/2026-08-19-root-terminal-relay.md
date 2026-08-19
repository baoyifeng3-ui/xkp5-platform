# Root Terminal Relay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a super-administrator-only, auditable browser terminal that opens one time-bounded root `bash` on an online Ubuntu processing Agent through an outbound reverse WebSocket.

**Architecture:** The platform persists session metadata and uses the durable Agent command queue only to request terminal startup. Agent and browser peers exchange separate one-time tickets over authenticated HTTPS, attach to a bounded in-memory WebSocket relay, and stream opaque terminal bytes without persistence; the Go Agent owns the root PTY and deterministic process-group cleanup.

**Tech Stack:** Java 8, Spring Boot 2.1/Spring WebSocket 5.1, MyBatis-Plus, Flyway, MySQL 8, JUnit 4/Mockito, Go 1.22, `github.com/gorilla/websocket` v1.5.3, `github.com/creack/pty` v1.1.24, Vue 2, Element UI, xterm.js 5.3.0 with fit addon 0.7.0, Node contract tests, Docker BuildKit.

---

## Repositories And Execution Setup

- Platform worktree: `C:/Users/84502/Desktop/新建文件夹/project/xkp5-platform/.worktrees/phase-1-platform-shell`
- Agent repository: `C:/Users/84502/Desktop/新建文件夹/project/xkp-agent`
- Agent implementation worktree: `C:/Users/84502/Desktop/新建文件夹/project/xkp-agent/.worktrees/root-terminal-relay`
- Before changing the Agent, use `superpowers:using-git-worktrees` to create the Agent implementation worktree on `feature/root-terminal-relay`; do not implement Agent changes directly on its `main` branch.
- Use identical terminal command fixtures in `java/match-mgr/src/test/resources/agent/` and the Agent `testdata/` directory. Commit each repository independently.
- Platform Java tests run through `C:/Users/84502/Desktop/新建文件夹/project/.xkp5-test-runtime/java-test.Dockerfile`. Agent tests run with `go test -race ./... -count=1`.

## File Structure

Platform persistence and lifecycle:

- Create `java/match-mgr/src/main/resources/db/migration/V24__processing_agent_terminal_sessions.sql`
- Create `java/match-mgr/src/main/java/com/match/terminal/persistence/TerminalSessionRecord.java`
- Create `java/match-mgr/src/main/java/com/match/terminal/persistence/TerminalSessionMapper.java`
- Create `java/match-mgr/src/main/java/com/match/terminal/model/TerminalSessionView.java`
- Create `java/match-mgr/src/main/java/com/match/terminal/model/TerminalTicketView.java`
- Create `java/match-mgr/src/main/java/com/match/terminal/model/CreateTerminalSessionRequest.java`
- Create `java/match-mgr/src/main/java/com/match/terminal/service/TerminalSessionService.java`
- Create `java/match-mgr/src/main/java/com/match/terminal/service/TerminalSessionExpiry.java`
- Modify `java/match-mgr/src/main/java/com/match/Application.java`
- Modify `java/match-mgr/src/main/java/com/match/agent/service/AgentCommandService.java`

Platform HTTP and relay:

- Create `java/match-mgr/src/main/java/com/match/terminal/web/OperationsTerminalController.java`
- Create `java/match-mgr/src/main/java/com/match/terminal/web/AgentTerminalController.java`
- Create `java/match-mgr/src/main/java/com/match/terminal/relay/TerminalPeer.java`
- Create `java/match-mgr/src/main/java/com/match/terminal/relay/TerminalRelayCoordinator.java`
- Create `java/match-mgr/src/main/java/com/match/terminal/relay/TerminalHandshakeInterceptor.java`
- Create `java/match-mgr/src/main/java/com/match/terminal/relay/TerminalWebSocketHandler.java`
- Create `java/match-mgr/src/main/java/com/match/terminal/relay/TerminalWebSocketConfig.java`
- Modify `java/match-mgr/pom.xml`
- Modify `java/match-mgr/src/main/java/com/match/config/WebConfig.java`

Agent protocol and PTY:

- Create `internal/terminal/manager.go`
- Create `internal/terminal/manager_linux.go`
- Create `internal/terminal/manager_unsupported.go`
- Create `internal/terminal/relay.go`
- Create `internal/terminal/recovery_store.go`
- Modify `internal/protocol/command.go`
- Modify `internal/client/client.go`
- Modify `internal/runtime/command_dispatcher.go`
- Modify `cmd/xkp-agent/main.go`
- Modify `go.mod` and `go.sum`

Frontend:

- Create `vue/src/api/TerminalSessions.js`
- Create `vue/src/components/agents/RootTerminalDialog.vue`
- Create `vue/src/services/rootTerminalSession.js`
- Create `vue/scripts/test-root-terminal-contract.js`
- Modify `vue/src/views/operations/ProcessingAgents.vue`
- Modify `vue/package.json` and `vue/package-lock.json`

## Task 1: Persist Terminal Sessions And Enforce One Per Agent

**Files:**
- Create: `java/match-mgr/src/main/resources/db/migration/V24__processing_agent_terminal_sessions.sql`
- Create: `java/match-mgr/src/main/java/com/match/terminal/persistence/TerminalSessionRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/terminal/persistence/TerminalSessionMapper.java`
- Create: `java/match-mgr/src/test/java/com/match/terminal/persistence/TerminalSessionSchemaTest.java`
- Modify: `java/match-mgr/src/main/java/com/match/Application.java`

- [ ] **Step 1: Write the failing migration-shape test**

Require V24 to define the session, Agent, actor, state, nullable `active_agent_id`, separate Agent/browser ticket digests and consumed timestamps, command correlation, lifecycle timestamps, two byte counters, and these exact indexes:

```java
assertUniqueIndex(sql, "uk_terminal_active_agent", "active_agent_id");
assertIndex(sql, "idx_terminal_state_expiry", "state", "absolute_expires_at");
assertIndex(sql, "idx_terminal_agent_history", "agent_id", "requested_at");
assertFalse(sql.contains("terminal_input"));
assertFalse(sql.contains("terminal_output"));
```

- [ ] **Step 2: Run the schema test and verify RED**

Run from the project parent:

```powershell
docker build --file .xkp5-test-runtime/java-test.Dockerfile --build-arg TEST_PATTERN=TerminalSessionSchemaTest xkp5-platform/.worktrees/phase-1-platform-shell
```

Expected: FAIL because `V24__processing_agent_terminal_sessions.sql` does not exist.

- [ ] **Step 3: Add the migration**

Use this table contract, retaining ticket digests but never plaintext ticket or terminal content:

```sql
CREATE TABLE processing_agent_terminal_session (
  session_id VARCHAR(36) NOT NULL,
  agent_id VARCHAR(36) NOT NULL,
  requester_user_id INT NOT NULL,
  requester_role VARCHAR(32) NOT NULL,
  state VARCHAR(24) NOT NULL,
  active_agent_id VARCHAR(36) NULL,
  agent_ticket_digest CHAR(64) NULL,
  agent_ticket_expires_at DATETIME(3) NULL,
  agent_ticket_consumed_at DATETIME(3) NULL,
  browser_ticket_digest CHAR(64) NULL,
  browser_ticket_expires_at DATETIME(3) NULL,
  browser_ticket_consumed_at DATETIME(3) NULL,
  command_id VARCHAR(36) NULL,
  requested_at DATETIME(3) NOT NULL,
  agent_connected_at DATETIME(3) NULL,
  browser_connected_at DATETIME(3) NULL,
  active_at DATETIME(3) NULL,
  last_io_at DATETIME(3) NULL,
  absolute_expires_at DATETIME(3) NOT NULL,
  ended_at DATETIME(3) NULL,
  end_reason VARCHAR(64) NULL,
  end_message VARCHAR(512) NULL,
  browser_to_agent_bytes BIGINT UNSIGNED NOT NULL DEFAULT 0,
  agent_to_browser_bytes BIGINT UNSIGNED NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (session_id),
  UNIQUE KEY uk_terminal_active_agent (active_agent_id),
  KEY idx_terminal_state_expiry (state, absolute_expires_at),
  KEY idx_terminal_agent_history (agent_id, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 4: Add record and explicit mapper operations**

The mapper must expose atomic, state-guarded methods rather than generic updates:

```java
TerminalSessionRecord selectById(String sessionId);
TerminalSessionRecord selectActiveByAgent(String agentId);
TerminalSessionRecord selectByCommandId(String commandId);
int insert(TerminalSessionRecord record);
int setCommand(String sessionId, String commandId, LocalDateTime now);
int issueAgentTicket(String sessionId, String digest, LocalDateTime expiresAt, LocalDateTime now);
int consumeAgentTicket(String sessionId, String digest, LocalDateTime now);
int issueBrowserTicket(String sessionId, String digest, LocalDateTime expiresAt, LocalDateTime now);
int consumeBrowserTicket(String sessionId, String digest, LocalDateTime now);
int markActive(String sessionId, LocalDateTime now);
int addTraffic(String sessionId, long browserToAgent, long agentToBrowser, LocalDateTime ioAt);
int close(String sessionId, String state, String reason, String message, LocalDateTime now);
List<TerminalSessionRecord> selectExpired(LocalDateTime idleBefore, LocalDateTime now, int limit);
```

- [ ] **Step 5: Re-run the schema test and commit**

Expected: `TerminalSessionSchemaTest` passes and `git diff --check` reports no errors.

```powershell
git add java/match-mgr/src/main/resources/db/migration/V24__processing_agent_terminal_sessions.sql java/match-mgr/src/main/java/com/match/terminal java/match-mgr/src/test/java/com/match/terminal java/match-mgr/src/main/java/com/match/Application.java
git commit -m "feat: persist root terminal sessions"
```

## Task 2: Create Sessions And Queue A Ticket-Free Command

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/terminal/model/CreateTerminalSessionRequest.java`
- Create: `java/match-mgr/src/main/java/com/match/terminal/model/TerminalSessionView.java`
- Create: `java/match-mgr/src/main/java/com/match/terminal/model/TerminalTicketView.java`
- Create: `java/match-mgr/src/main/java/com/match/terminal/service/TerminalSessionService.java`
- Create: `java/match-mgr/src/test/java/com/match/terminal/service/TerminalSessionServiceTest.java`
- Modify: `java/match-mgr/src/main/java/com/match/agent/service/AgentCommandService.java`
- Modify: `java/match-mgr/src/test/java/com/match/agent/service/AgentCommandServiceTest.java`

- [ ] **Step 1: Write failing lifecycle and concurrency tests**

Fix the clock and assert: exact confirmation `OPEN_ROOT_TERMINAL`, Agent enabled/online within 15 seconds, state `WAITING_AGENT`, 90-second Agent deadline, two-hour absolute expiry, duplicate/concurrent create rejection, and unusable-license allowance for super-admin maintenance.

```java
TerminalSessionView created = service.create(agentId, actor, "OPEN_ROOT_TERMINAL");
assertEquals("WAITING_AGENT", created.getState());
assertEquals(now.plusSeconds(90), created.getAgentConnectionDeadline());
assertEquals(now.plusSeconds(7200), created.getAbsoluteExpiresAt());
verify(commandService).requestTerminalCommand(eq(agent), eq(created.getSessionId()),
        eq(created.getAgentConnectionDeadline()), eq(created.getAbsoluteExpiresAt()),
        eq(actor.getUserId()), eq("SUPER_ADMIN"));
```

- [ ] **Step 2: Run targeted tests and verify RED**

Run `TerminalSessionServiceTest,AgentCommandServiceTest`; expected failure is the missing terminal service/command method.

- [ ] **Step 3: Implement the ticket-free command path**

Add `OPEN_ROOT_TERMINAL` to the closed Java command set. Serialize only this payload:

```json
{
  "sessionId": "11111111-2222-4333-8444-555555555555",
  "relayUrl": "wss://management.example/terminal/v1/agent",
  "agentConnectionDeadline": "2026-08-19T13:01:30Z",
  "idleTimeoutSeconds": 600,
  "absoluteExpiresAt": "2026-08-19T15:00:00Z"
}
```

`requestTerminalCommand` must require `SUPER_ADMIN`, use dedup key
`agentId + ":OPEN_ROOT_TERMINAL"`, and reject payloads over 1 KiB. It must not
contain `ticket`, `credential`, `command`, or `shell` fields.

- [ ] **Step 4: Implement transactional session creation**

Create the session before the command, set `active_agent_id=agent_id`, then store
the returned command ID in the same transaction. Catch `DuplicateKeyException`
and return a stable `TERMINAL_SESSION_ACTIVE` conflict; do not return an existing
root session as if this request created it.

- [ ] **Step 5: Re-run tests and commit**

```powershell
git add java/match-mgr/src/main/java/com/match/terminal java/match-mgr/src/test/java/com/match/terminal java/match-mgr/src/main/java/com/match/agent/service/AgentCommandService.java java/match-mgr/src/test/java/com/match/agent/service/AgentCommandServiceTest.java
git commit -m "feat: request root terminal sessions"
```

## Task 3: Issue One-Time Tickets And Expose Role-Safe REST APIs

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/terminal/web/OperationsTerminalController.java`
- Create: `java/match-mgr/src/main/java/com/match/terminal/web/AgentTerminalController.java`
- Create: `java/match-mgr/src/test/java/com/match/terminal/web/OperationsTerminalControllerTest.java`
- Create: `java/match-mgr/src/test/java/com/match/terminal/web/AgentTerminalControllerTest.java`
- Modify: `java/match-mgr/src/main/java/com/match/terminal/service/TerminalSessionService.java`

- [ ] **Step 1: Write failing ticket tests**

Assert 32 secure random bytes encoded Base64URL without padding, SHA-256 digest-only persistence, 60-second expiry, atomic one-use consumption, browser-ticket rotation before activation, Agent/session/command/lease matching, and indistinguishable replay failure.

```java
TerminalTicketView ticket = service.issueAgentTicket(agent, sessionId, commandId, leaseToken);
assertEquals(43, ticket.getTicket().length());
assertFalse(mapper.selectById(sessionId).getAgentTicketDigest().contains(ticket.getTicket()));
assertTrue(service.consumeAgentTicket(agent, sessionId, ticket.getTicket()));
assertFalse(service.consumeAgentTicket(agent, sessionId, ticket.getTicket()));
```

- [ ] **Step 2: Write controller role-boundary tests**

Require exact endpoints and prove `ADMIN` and `USER` cannot create, view, ticket,
or close sessions. `SUPER_ADMIN` must pass the exact confirmation. Agent ticket
exchange must authenticate the normal Agent bearer credential and matching
running lease.

- [ ] **Step 3: Run controller/service tests and verify RED**

Expected: missing controllers and ticket methods.

- [ ] **Step 4: Implement REST contracts**

```java
@PostMapping("/operations/processing-agents/{agentId}/terminal-sessions")
public ResponseResult<Object> create(@PathVariable String agentId,
        @RequestBody CreateTerminalSessionRequest request) {
    User actor = roleGuard.requireSuperAdmin();
    return Response.makeOKRsp(service.create(agentId, actor, request.getConfirmation()));
}

@PostMapping("/agent/v1/terminal-sessions/{sessionId}/agent-ticket")
public TerminalTicketView agentTicket(@RequestHeader("Authorization") String authorization,
        @PathVariable String sessionId, @RequestBody AgentTerminalTicketRequest request) {
    ProcessingAgentRecord agent = credentialService.authenticate(authorization);
    return service.issueAgentTicket(agent, sessionId, request.getCommandId(), request.getLeaseToken());
}
```

Also add GET status, POST browser-ticket, and idempotent DELETE close under
`/operations/terminal-sessions/{sessionId}`.

- [ ] **Step 5: Update HTTP interception and commit**

Exclude only `/agent/v1/**` and `/terminal/v1/**` from Sa-Token interception;
controllers and WebSocket handshake interceptors perform their own exact auth.

```powershell
git add java/match-mgr/src/main/java/com/match/terminal java/match-mgr/src/test/java/com/match/terminal java/match-mgr/src/main/java/com/match/config/WebConfig.java
git commit -m "feat: issue one-time terminal tickets"
```

## Task 4: Pair WebSocket Peers In A Bounded In-Memory Relay

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/terminal/relay/TerminalPeer.java`
- Create: `java/match-mgr/src/main/java/com/match/terminal/relay/TerminalRelayCoordinator.java`
- Create: `java/match-mgr/src/main/java/com/match/terminal/relay/TerminalHandshakeInterceptor.java`
- Create: `java/match-mgr/src/main/java/com/match/terminal/relay/TerminalWebSocketHandler.java`
- Create: `java/match-mgr/src/main/java/com/match/terminal/relay/TerminalWebSocketConfig.java`
- Create: `java/match-mgr/src/test/java/com/match/terminal/relay/TerminalRelayCoordinatorTest.java`
- Create: `java/match-mgr/src/test/java/com/match/terminal/relay/TerminalHandshakeInterceptorTest.java`
- Modify: `java/match-mgr/pom.xml`

- [ ] **Step 1: Add the WebSocket dependency and failing handshake tests**

Add `spring-boot-starter-websocket`. Test two upgrade paths, configured Origin,
browser subprotocol ticket parsing, Agent bearer plus ticket, identity matching,
atomic ticket consumption, and rejection after terminal state.

```java
assertEquals(TerminalPeer.BROWSER, attributes.get("terminalPeer"));
assertEquals(sessionId, attributes.get("terminalSessionId"));
verify(ticketService).consumeBrowserTicket(sessionId, ticket);
```

- [ ] **Step 2: Write failing relay tests**

Use fake `WebSocketSession` peers to prove: state becomes active only when both
attach, binary bytes forward unchanged, closed peer closes the other, duplicate
close is harmless, invalid text controls close both, and terminal bytes are
never passed to persistence.

- [ ] **Step 3: Implement handshake and WebSocket registration**

Register `/terminal/v1/browser/{sessionId}` and `/terminal/v1/agent/{sessionId}`
with separate handshake interceptors. Configure 64 KiB binary and 4 KiB text
buffers. Never allow `*` origins.

```java
registry.addHandler(browserHandler, "/terminal/v1/browser/{sessionId}")
        .addInterceptors(browserHandshake).setAllowedOrigins(allowedOrigins);
registry.addHandler(agentHandler, "/terminal/v1/agent/{sessionId}")
        .addInterceptors(agentHandshake).setAllowedOrigins(allowedOrigins);
```

- [ ] **Step 4: Implement the relay coordinator**

Use one relay object per session, a 1 MiB bounded queue per peer, one serialized
writer per WebSocket, 64 KiB maximum frames, token buckets at 2 MiB/s with 8 MiB
burst, and resize bounds of 20..500 columns and 5..200 rows. Only these text
controls are valid:

```json
{"type":"resize","columns":120,"rows":36}
{"type":"ping"}
{"type":"pong"}
{"type":"close","reason":"OPERATOR_CLOSED"}
```

- [ ] **Step 5: Run relay tests and commit**

Expected: pairing, forwarding, policy closure, and ticket replay tests pass.

```powershell
git add java/match-mgr/pom.xml java/match-mgr/src/main/java/com/match/terminal/relay java/match-mgr/src/test/java/com/match/terminal/relay
git commit -m "feat: relay terminal websocket peers"
```

## Task 5: Expire, Recover, Audit, And Reconcile Sessions

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/terminal/service/TerminalSessionExpiry.java`
- Create: `java/match-mgr/src/main/java/com/match/terminal/service/TerminalCommandResultListener.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/model/AgentCommandFinishedEvent.java`
- Create: `java/match-mgr/src/test/java/com/match/terminal/service/TerminalSessionExpiryTest.java`
- Create: `java/match-mgr/src/test/java/com/match/terminal/service/TerminalCommandResultListenerTest.java`
- Modify: `java/match-mgr/src/main/java/com/match/terminal/service/TerminalSessionService.java`
- Modify: `java/match-mgr/src/main/java/com/match/agent/service/AgentCommandService.java`
- Modify: `java/match-mgr/src/main/java/com/match/agent/service/AgentAuditService.java`

- [ ] **Step 1: Write failing timeout and recovery tests**

Cover the 90-second Agent deadline, 60-second browser deadline after Agent
attachment, 10 minutes without terminal I/O, absolute two-hour expiry, batches of
at most 100, service startup recovery, and release of `active_agent_id`.

- [ ] **Step 2: Write command-result reconciliation tests**

An Agent failure before WebSocket attachment must fail the session immediately.
A normal terminal close reports command success once. Replayed identical results
remain idempotent and stale results cannot overwrite a newer terminal state.

Use a post-commit event to avoid a circular service dependency:

```java
public final class AgentCommandFinishedEvent {
    private final String commandId;
    private final String agentId;
    private final String commandType;
    private final boolean success;
    private final String resultCode;
    private final String resultMessage;
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onFinished(AgentCommandFinishedEvent event) {
    if ("OPEN_ROOT_TERMINAL".equals(event.getCommandType())) {
        terminalSessionService.reconcileCommand(event);
    }
}
```

- [ ] **Step 3: Implement bounded lifecycle closure**

```java
@Scheduled(fixedDelay = 5000L)
public void expire() {
    LocalDateTime now = utcNow();
    for (TerminalSessionRecord row : mapper.selectExpired(now.minusMinutes(10), now, 100)) {
        coordinator.close(row.getSessionId(), expiryReason(row, now));
    }
}
```

Startup recovery changes every non-terminal row to `FAILED` with
`MANAGEMENT_RESTARTED` before accepting WebSocket peers.

Publish `AgentCommandFinishedEvent` only after `markTerminal` succeeds. The
listener finds the session by `command_id`; failure closes a waiting/active
session, while a success result closes only a still non-terminal session and
cannot rewrite an already recorded operator/timeout reason.

- [ ] **Step 4: Add metadata-only audit events**

Record request, ticket issue/consume, Agent attach, browser attach, active, close,
timeout, PTY failure, and restart recovery. Audit details may contain only IDs,
role, timestamps, stable reason, duration, and byte totals.

- [ ] **Step 5: Run tests and commit**

```powershell
git add java/match-mgr/src/main/java/com/match/terminal java/match-mgr/src/test/java/com/match/terminal java/match-mgr/src/main/java/com/match/agent/service java/match-mgr/src/test/java/com/match/agent/service
git commit -m "feat: enforce terminal session lifecycle"
```

## Task 6: Freeze The Java/Go Terminal Command Contract

**Files:**
- Create: `java/match-mgr/src/test/resources/agent/command-open-root-terminal-v1.json`
- Modify: `java/match-mgr/src/test/java/com/match/agent/model/AgentCommandFixtureContractTest.java`
- Create in Agent worktree: `testdata/command-open-root-terminal-v1.json`
- Modify in Agent worktree: `internal/protocol/command.go`
- Modify in Agent worktree: `internal/protocol/command_test.go`

- [ ] **Step 1: Add identical ticket-free fixtures**

Use the JSON shape from Task 2. The fixture must contain no ticket, credential,
shell, executable, environment, or arbitrary command field.

- [ ] **Step 2: Write failing Java serialization and Go strict-decoding tests**

Go must reject unknown fields, wrong version/type, invalid UUID, non-WSS relay in
production, idle timeout other than 600, an expired Agent-connection deadline,
an absolute deadline no later than the connection deadline or more than two
hours after it, oversized input, and trailing JSON.

```go
if command.Type != OpenRootTerminal || command.Terminal == nil ||
    command.Terminal.IdleTimeoutSeconds != 600 {
    t.Fatalf("terminal command = %#v", command)
}
```

- [ ] **Step 3: Run both tests and verify RED**

Expected: Java does not serialize the type yet or Go rejects it as unsupported.

- [ ] **Step 4: Implement the strict Go payload**

```go
type TerminalPayload struct {
    SessionID              string    `json:"sessionId"`
    RelayURL               string    `json:"relayUrl"`
    AgentConnectionDeadline time.Time `json:"agentConnectionDeadline"`
    IdleTimeoutSeconds     int       `json:"idleTimeoutSeconds"`
    AbsoluteExpiresAt      time.Time `json:"absoluteExpiresAt"`
}
```

Set `Command.Terminal` only for `OPEN_ROOT_TERMINAL`; all existing command
validation remains unchanged.

- [ ] **Step 5: Run contract tests and commit both repositories**

```powershell
git commit -m "feat: define root terminal command contract"
```

## Task 7: Add Agent Ticket Exchange And Asynchronous Terminal Ownership

**Files:**
- Modify in Agent worktree: `internal/client/client.go`
- Modify in Agent worktree: `internal/client/client_test.go`
- Create in Agent worktree: `internal/terminal/manager.go`
- Create in Agent worktree: `internal/terminal/recovery_store.go`
- Create in Agent worktree: `internal/terminal/manager_test.go`
- Modify in Agent worktree: `internal/runtime/command_dispatcher.go`
- Modify in Agent worktree: `internal/runtime/command_dispatcher_test.go`

- [ ] **Step 1: Write failing authenticated ticket-client tests**

Require POST `/agent/v1/terminal-sessions/{sessionId}/agent-ticket` with bearer,
command ID, and lease token; reject incomplete responses and do not include body,
ticket, or credential text in errors.

- [ ] **Step 2: Write failing asynchronous-dispatch tests**

Prove the dispatcher acknowledges start, saves a minimal recovery row, starts the
terminal manager, and returns so polling continues. A second open command fails
with `TERMINAL_ALREADY_ACTIVE`. Restart recovery reports
`TERMINAL_INTERRUPTED_BY_AGENT_RESTART` without recreating a shell.

```go
type TerminalManager interface {
    Start(context.Context, protocol.Command) error
    Active() bool
    Close(context.Context, string) error
}
```

- [ ] **Step 3: Implement ticket exchange and separate recovery storage**

The recovery JSON contains only session ID, command ID, lease token, and
deadlines; create it with mode 0600 and atomic rename. Never store the Agent or
browser ticket.

- [ ] **Step 4: Add a dedicated terminal dispatch branch**

Handle `OPEN_ROOT_TERMINAL` before the synchronous power/environment path. The
terminal manager sends the eventual command result; the ordinary one-command
result store remains available for environment operations.

- [ ] **Step 5: Run Agent tests and commit**

```powershell
go test -race ./internal/client ./internal/protocol ./internal/runtime ./internal/terminal -count=1
git add internal/client internal/protocol internal/runtime internal/terminal
git commit -m "feat: own asynchronous terminal sessions"
```

## Task 8: Run A Root PTY Through The Reverse WebSocket

**Files:**
- Create in Agent worktree: `internal/terminal/manager_linux.go`
- Create in Agent worktree: `internal/terminal/manager_unsupported.go`
- Create in Agent worktree: `internal/terminal/relay.go`
- Create in Agent worktree: `internal/terminal/manager_linux_test.go`
- Create in Agent worktree: `internal/terminal/relay_test.go`
- Modify in Agent worktree: `cmd/xkp-agent/main.go`
- Modify in Agent worktree: `go.mod`
- Modify in Agent worktree: `go.sum`

- [ ] **Step 1: Add pinned dependencies**

```powershell
go get github.com/gorilla/websocket@v1.5.3
go get github.com/creack/pty@v1.1.24
```

- [ ] **Step 2: Write failing fake-PTY and fake-WebSocket tests**

Assert exact executable `/bin/bash`, args `--noprofile --norc`, directory `/root`,
fixed safe environment, a new session/controlling terminal, binary forwarding, resize bounds,
64 KiB frames, disconnect cancellation, graceful-then-force process-group kill,
child wait, and no content in error messages.

- [ ] **Step 3: Implement Linux PTY startup and unsupported-platform failure**

Use `pty.StartWithAttrs` around `exec.Command` with
`SysProcAttr{Setsid:true, Setctty:true}` and the fixed environment. The session
leader PID is also the process-group ID used for group termination; do not set
`Setpgid` in addition to `Setsid`. The non-Linux implementation returns
`TERMINAL_UNSUPPORTED_PLATFORM` without opening a process.

- [ ] **Step 4: Implement authenticated WSS relay**

Use the existing TLS root pool and Agent bearer header. Supply the one-time
ticket in `Sec-WebSocket-Protocol`, never in the URL. Enforce deadlines, one
reader/one writer, bounded channels, ping/pong, resize controls, and cancellation.

- [ ] **Step 5: Wire shutdown and command results**

Agent cancellation closes the socket and PTY. Normal end reports
`TERMINAL_SESSION_CLOSED`; startup/relay failures report stable bounded codes.
Remove the recovery file only after management acknowledges the result.

- [ ] **Step 6: Run the full race suite and commit**

```powershell
go test -race ./... -count=1
git add .
git commit -m "feat: relay root terminal pty"
```

## Task 9: Add The Super-Administrator Terminal Surface

**Files:**
- Create: `vue/src/api/TerminalSessions.js`
- Create: `vue/src/services/rootTerminalSession.js`
- Create: `vue/src/components/agents/RootTerminalDialog.vue`
- Create: `vue/scripts/test-root-terminal-contract.js`
- Modify: `vue/src/views/operations/ProcessingAgents.vue`
- Modify: `vue/package.json`
- Modify: `vue/package-lock.json`

- [ ] **Step 1: Add dependencies and the failing contract test**

Pin `xterm` 5.3.0 and `xterm-addon-fit` 0.7.0. Add `test:terminal` and require:
super-admin operations page only, root confirmation, online/enabled gating,
waiting/active/closing/closed states, binary WebSocket, subprotocol ticket,
bounded resize, countdowns, explicit close, and teardown close.

- [ ] **Step 2: Run `npm run test:terminal` and verify RED**

Expected: missing API, service, component, and terminal action.

- [ ] **Step 3: Implement API and WebSocket state service**

```javascript
export const createTerminalSession = (agentId) => request.post(
  `/operations/processing-agents/${agentId}/terminal-sessions`,
  { confirmation: 'OPEN_ROOT_TERMINAL' })
export const issueBrowserTicket = (sessionId) => request.post(
  `/operations/terminal-sessions/${sessionId}/browser-ticket`)
export const closeTerminalSession = (sessionId) => request.delete(
  `/operations/terminal-sessions/${sessionId}`)
```

`rootTerminalSession` converts HTTP(S) origin to WS(S), passes
`['xkp-terminal.v1', 'ticket.' + ticket]`, sets `binaryType='arraybuffer'`, and
never logs frames or tickets.

- [ ] **Step 4: Implement the full-screen terminal dialog**

Use xterm with fit addon, stable full-viewport dimensions, a compact header with
server name/root badge/deadlines/close icon, and no nested cards. Send resize only
after fit returns columns 20..500 and rows 5..200. `beforeDestroy` and dialog
close must call DELETE and close the socket.

- [ ] **Step 5: Add the terminal action to processing Agents**

Show an icon button only for selected online/enabled Agents. Confirmation text
must name the server and explicitly state root authority. Do not add the action
to normal-administrator routes or APIs.

- [ ] **Step 6: Run frontend verification and commit**

```powershell
npm run test:terminal
npm run test:navigation
npm run test:license
npm run test:agents
npm run test:templates
npm run test:dashboard
npm run build
git add vue
git commit -m "feat: add root terminal workspace"
```

## Task 10: End-To-End Fake Relay, Deployment, And Documentation

**Files:**
- Create: `integration/agent/test-terminal-relay-flow.ps1`
- Modify: `integration/agent/test-script-contract.ps1`
- Modify: `docs/operations/processing-agent.md`
- Modify Agent worktree: `README.md`
- Modify Agent worktree: deployment systemd/config examples under `deploy/`

- [ ] **Step 1: Write the failing integration-script contract**

Require a non-privileged fake PTY, one Agent peer, one browser peer, binary echo,
resize, ticket replay rejection, second-session conflict, disconnect cleanup,
idle expiry with an injected short test clock, and database assertions proving a
sentinel terminal string is absent.

- [ ] **Step 2: Implement the fake relay flow**

The script must never start `/bin/bash` as root. It uses a disposable license,
enrolled fake Agent, loopback TLS, and a deterministic echo PTY adapter. It exits
non-zero on leaked content, unfinished sessions, or replay acceptance.

- [ ] **Step 3: Document production prerequisites**

Document HTTPS/WSS proxy upgrade headers, allowed Origin, Agent systemd root
identity, `/bin/bash`, PTY support, firewall rule requiring outbound management
access only, 10-minute idle/two-hour maximum, one session per Agent, lack of
reattachment, and the isolated-host real-root acceptance procedure.

- [ ] **Step 4: Run full verification**

```powershell
docker build --file .xkp5-test-runtime/java-test.Dockerfile --build-arg TEST_PATTERN=*Test xkp5-platform/.worktrees/phase-1-platform-shell
Set-Location 'C:/Users/84502/Desktop/新建文件夹/project/xkp-agent/.worktrees/root-terminal-relay'
go test -race ./... -count=1
Set-Location xkp5-platform/.worktrees/phase-1-platform-shell/vue
npm run test:navigation
npm run test:license
npm run test:agents
npm run test:templates
npm run test:dashboard
npm run test:terminal
npm run build
```

Run the V24 migration and fake relay against disposable MySQL/management
containers. Require zero Java failures/errors, a clean Go race run, successful
Vue build, replay rejection, and no sentinel content in DB or logs.

- [ ] **Step 5: Request code review and resolve findings**

Use `superpowers:requesting-code-review`. Re-run every affected targeted suite,
then repeat the full verification commands above.

- [ ] **Step 6: Commit documentation and acceptance harness**

```powershell
git add integration docs java vue
git commit -m "test: verify root terminal relay"
```

In the Agent worktree:

```powershell
git add README.md deploy
git commit -m "docs: document root terminal prerequisites"
```

## Acceptance Boundary

This increment is complete when only a super administrator can confirm and open
one root terminal per online enabled Ubuntu Agent; Agent and browser connect with
separate one-use 60-second tickets; terminal bytes remain in memory; resize and
binary data relay correctly; disconnect, 10-minute idle, two-hour absolute
expiry, Agent restart, management restart, and backpressure all reap the PTY and
release the session; ticket replay and concurrent sessions fail closed; full
Java, Go race, frontend, migration, and fake-relay suites pass; and real-root
acceptance instructions are documented without running a privileged shell in CI.
