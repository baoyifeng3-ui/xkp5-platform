# Competition Mode Orchestration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver isolated training/competition participant modes with generation-based session invalidation, pre-created competition slot binding, and durable per-Agent container transitions.

**Architecture:** Add an authoritative platform mode for participant routing and a separate per-Agent transition state machine for asynchronous container convergence. Preserve the existing training lifecycle and Agent pair executor; add competition environment records and command types while management owns snapshots, barriers, retries, and recovery.

**Tech Stack:** Java 8, Spring Boot 2.1, MyBatis-Plus/MySQL 8/Flyway, Sa-Token, Vue 2/Element UI, Go 1.23, Docker Engine API, JUnit 5/Mockito, Node contract tests.

---

## Repository Boundaries

Platform repository: `C:\Users\84502\Desktop\新建文件夹\project\xkp5-platform`.

Agent repository: `C:\Users\84502\Desktop\新建文件夹\project\xkp-agent`.

Create isolated worktrees at execution time with `superpowers:using-git-worktrees`. Do not implement directly on `main`. Platform and Agent commits remain separate; do not push without explicit authorization.

## File Structure

### Platform backend

- `V25__competition_mode_orchestration.sql`: additive schema and nullable slot binding migration.
- `com.match.mode.persistence`: platform mode, Agent mode, transition, step, and snapshot records/mappers.
- `com.match.mode.service.PlatformModeService`: immediate platform mode and generation changes.
- `com.match.mode.service.ModeTransitionService`: per-Agent entry/exit planning and phase advancement.
- `com.match.mode.service.ModeTransitionReconciler`: Agent command result handling.
- `com.match.mode.service.ModeTransitionRecovery`: bounded startup/scheduled recovery.
- `com.match.mode.web`: ADMIN mode control and SUPER_ADMIN retry/diagnostic endpoints.
- `com.match.environment.persistence.CompetitionEnvironment*`: competition container pair persistence.
- `com.match.environment.service.CompetitionEnvironmentService`: create/inspect/restore operations.
- `com.match.environment.service.CompetitionSlotBindingService`: authoritative bind/unbind validation.
- `com.match.security.ParticipantModeGuard`: generation and endpoint mode enforcement.

### Agent

- `internal/protocol/command.go`: four competition command constants using the existing environment payload.
- `internal/runtime/command_dispatcher.go`: route competition commands through the pair executor.
- `testdata/command-*-competition-environment-v1.json`: cross-repository fixtures.

### Vue

- `src/utils/auth.js`, `src/store/modules/Match.js`, `src/router/index.js`: persist mode/generation and isolate participant routes.
- `src/api/PlatformMode.js`, `CompetitionEnvironments.js`: management and practical-readiness API clients.
- `src/views/management/CompetitionMode.vue`: global mode and per-Agent progress.
- `src/views/operations/CompetitionEnvironments.vue`: competition container creation and binding readiness.
- `src/views/competition/CompetitionPractical.vue`: bound/running practical links or an unavailable state.
- `scripts/test-competition-mode-contract.js`: deterministic frontend contract checks.

## Task 1: Add the durable orchestration schema

**Files:**
- Create: `java/match-mgr/src/main/resources/db/migration/V25__competition_mode_orchestration.sql`
- Create: `java/match-mgr/src/test/java/com/match/mode/persistence/CompetitionModeSchemaTest.java`
- Modify: `java/match-mgr/src/main/java/com/match/environment/persistence/ProcessingEnvironmentSlotRecord.java`
- Modify: `java/match-mgr/src/main/java/com/match/environment/persistence/ProcessingEnvironmentSlotMapper.java`

- [ ] **Step 1: Write the failing schema contract test**

Create a test that reads V25 and asserts the exact invariants:

```java
@Test
void migrationDefinesTwoLevelModeAndNullableBinding() throws Exception {
    String sql = new String(Files.readAllBytes(Paths.get(
            "src/main/resources/db/migration/V25__competition_mode_orchestration.sql")),
            StandardCharsets.UTF_8);
    assertTrue(sql.contains("ALTER TABLE processing_environment_slot MODIFY user_id INT NULL"));
    assertTrue(sql.contains("CREATE TABLE platform_mode"));
    assertTrue(sql.contains("generation BIGINT UNSIGNED NOT NULL"));
    assertTrue(sql.contains("CREATE TABLE competition_environment"));
    assertTrue(sql.contains("UNIQUE KEY uk_competition_environment_slot (slot_id)"));
    assertTrue(sql.contains("CREATE TABLE processing_agent_mode"));
    assertTrue(sql.contains("CREATE TABLE mode_transition"));
    assertTrue(sql.contains("CREATE TABLE mode_transition_step"));
    assertTrue(sql.contains("UNIQUE KEY uk_mode_active_transition (active_transition_key)"));
    assertTrue(sql.contains("CREATE TABLE mode_training_snapshot"));
}
```

- [ ] **Step 2: Run the test and verify RED**

Run from the platform repository:

```powershell
docker run --rm -v "${PWD}\java\match-mgr:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-8 mvn -Dtest=CompetitionModeSchemaTest test
```

Expected: FAIL because V25 does not exist.

- [ ] **Step 3: Add V25 with complete constraints**

The migration must contain these concrete structures:

```sql
ALTER TABLE processing_environment_slot MODIFY user_id INT NULL;

CREATE TABLE platform_mode (
  singleton_id TINYINT UNSIGNED NOT NULL,
  mode VARCHAR(24) NOT NULL,
  generation BIGINT UNSIGNED NOT NULL,
  changed_by INT NOT NULL,
  changed_at DATETIME(3) NOT NULL,
  PRIMARY KEY (singleton_id),
  CONSTRAINT chk_platform_mode_singleton CHECK (singleton_id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO platform_mode(singleton_id, mode, generation, changed_by, changed_at)
VALUES (1, 'TRAINING', 1, 0, UTC_TIMESTAMP(3));

CREATE TABLE competition_environment (
  environment_id VARCHAR(36) NOT NULL,
  agent_id VARCHAR(36) NOT NULL,
  slot_id VARCHAR(36) NOT NULL,
  slot_number TINYINT UNSIGNED NOT NULL,
  annotation_template_id VARCHAR(36) NOT NULL,
  annotation_template_version SMALLINT UNSIGNED NOT NULL,
  editor_template_id VARCHAR(36) NOT NULL,
  editor_template_version SMALLINT UNSIGNED NOT NULL,
  workspace_relative_path VARCHAR(512) NOT NULL,
  desired_state VARCHAR(24) NOT NULL,
  actual_state VARCHAR(24) NOT NULL,
  annotation_container_name VARCHAR(128) NOT NULL,
  annotation_container_state VARCHAR(24) NOT NULL,
  annotation_config_fingerprint CHAR(64) NOT NULL,
  editor_container_name VARCHAR(128) NOT NULL,
  editor_container_state VARCHAR(24) NOT NULL,
  editor_config_fingerprint CHAR(64) NOT NULL,
  last_verified_at DATETIME(3) NULL,
  last_component_results_json JSON NULL,
  current_operation_id VARCHAR(36) NULL,
  lock_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  created_by INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by INT NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (environment_id),
  UNIQUE KEY uk_competition_environment_slot (slot_id),
  UNIQUE KEY uk_competition_annotation_name (annotation_container_name),
  UNIQUE KEY uk_competition_editor_name (editor_container_name),
  KEY idx_competition_agent_state (agent_id, actual_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE processing_agent_mode (
  agent_id VARCHAR(36) NOT NULL,
  desired_mode VARCHAR(24) NOT NULL,
  actual_mode VARCHAR(32) NOT NULL,
  active_transition_id VARCHAR(36) NULL,
  lock_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mode_transition (
  transition_id VARCHAR(36) NOT NULL,
  agent_id VARCHAR(36) NOT NULL,
  source_mode VARCHAR(24) NOT NULL,
  target_mode VARCHAR(24) NOT NULL,
  state VARCHAR(24) NOT NULL,
  active_transition_key VARCHAR(72) NULL,
  actor_user_id INT NOT NULL,
  actor_role VARCHAR(32) NOT NULL,
  requested_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3) NULL,
  failure_summary VARCHAR(512) NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (transition_id),
  UNIQUE KEY uk_mode_active_transition (active_transition_key),
  KEY idx_mode_transition_agent (agent_id, requested_at),
  KEY idx_mode_transition_state (state, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mode_transition_step (
  step_id VARCHAR(36) NOT NULL,
  transition_id VARCHAR(36) NOT NULL,
  phase_number TINYINT UNSIGNED NOT NULL,
  step_ordinal SMALLINT UNSIGNED NOT NULL,
  environment_kind VARCHAR(16) NOT NULL,
  environment_id VARCHAR(36) NOT NULL,
  action_type VARCHAR(40) NOT NULL,
  state VARCHAR(24) NOT NULL,
  command_id VARCHAR(36) NULL,
  idempotency_key VARCHAR(160) NOT NULL,
  result_code VARCHAR(64) NULL,
  result_message VARCHAR(512) NULL,
  component_results_json JSON NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (step_id),
  UNIQUE KEY uk_mode_transition_step (transition_id, phase_number, step_ordinal),
  UNIQUE KEY uk_mode_step_idempotency (idempotency_key),
  KEY idx_mode_step_command (command_id),
  KEY idx_mode_step_state (transition_id, phase_number, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mode_training_snapshot (
  transition_id VARCHAR(36) NOT NULL,
  environment_id VARCHAR(36) NOT NULL,
  captured_at DATETIME(3) NOT NULL,
  PRIMARY KEY (transition_id, environment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

Update `ProcessingEnvironmentSlotRecord.userId` to `Integer`. Add mapper methods that select a slot `FOR UPDATE`, bind only when `user_id IS NULL`, and unbind only for the exact user.

- [ ] **Step 4: Run schema tests and Flyway migration tests**

Run:

```powershell
docker run --rm -v "${PWD}\java\match-mgr:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-8 mvn -Dtest=CompetitionModeSchemaTest,TrainingEnvironmentSchemaTest test
```

Expected: all selected tests PASS.

- [ ] **Step 5: Commit**

```powershell
git add java/match-mgr/src/main/resources/db/migration/V25__competition_mode_orchestration.sql java/match-mgr/src/main/java/com/match/environment/persistence java/match-mgr/src/test/java/com/match/mode/persistence/CompetitionModeSchemaTest.java
git commit -m "feat: add competition mode schema"
```

## Task 2: Implement authoritative platform mode and participant generation

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/mode/model/PlatformModeView.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/persistence/PlatformModeRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/persistence/PlatformModeMapper.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/service/PlatformModeChangedEvent.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/service/PlatformModeService.java`
- Create: `java/match-mgr/src/test/java/com/match/mode/service/PlatformModeServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Cover idempotence, generation increments, licensing, role checks, and event publication:

```java
@Test
void changesModeAndIncrementsGenerationExactlyOnce() {
    when(mapper.selectForUpdate()).thenReturn(mode("TRAINING", 7));
    PlatformModeView changed = service.change("COMPETITION", admin());
    assertEquals("COMPETITION", changed.getMode());
    assertEquals(8L, changed.getGeneration());
    verify(mapper).change("TRAINING", 7L, "COMPETITION", 8L, 42, now);
}

@Test
void sameTargetIsIdempotent() {
    when(mapper.selectForUpdate()).thenReturn(mode("COMPETITION", 8));
    assertEquals(8L, service.change("COMPETITION", admin()).getGeneration());
    verify(mapper, never()).change(anyString(), anyLong(), anyString(), anyLong(), anyInt(), any());
}
```

- [ ] **Step 2: Run and verify RED**

Run `mvn -Dtest=PlatformModeServiceTest test` through the Java Docker command from Task 1.

Expected: test compilation fails because the mode classes do not exist.

- [ ] **Step 3: Implement the mode service**

Use exact constants and a transaction:

```java
@Service
public class PlatformModeService {
    public static final String TRAINING = "TRAINING";
    public static final String COMPETITION = "COMPETITION";

    @Transactional
    public PlatformModeView change(String target, User actor) {
        roleGuard.requireAdmin(actor);
        requireTarget(target);
        PlatformModeRecord current = mapper.selectForUpdate();
        if (target.equals(current.getMode())) return view(current);
        licenseGuard.requireOperationAllowed("MODE_SWITCH");
        long nextGeneration = Math.addExact(current.getGeneration(), 1L);
        if (mapper.change(current.getMode(), current.getGeneration(), target,
                nextGeneration, actor.getUserId(), utcNow()) != 1) {
            throw new ModeConflictException("平台模式已被其他操作修改");
        }
        audit.record(actor, "PLATFORM_MODE_CHANGED", target, nextGeneration);
        eventPublisher.publishEvent(new PlatformModeChangedEvent(
                current.getMode(), target, nextGeneration, actor.getUserId(), actorRole(actor)));
        return new PlatformModeView(target, nextGeneration, utcNow());
    }
}
```

`current()` performs a read-only lookup. `requireTarget` accepts only exact uppercase constants. Task 7 consumes `PlatformModeChangedEvent` with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`, so Agent transition creation never runs before the platform row commits.

- [ ] **Step 4: Run focused tests**

Expected: `PlatformModeServiceTest` PASS with tests for overflow, stale compare-and-set, invalid target, and post-commit dispatch.

- [ ] **Step 5: Commit**

```powershell
git add java/match-mgr/src/main/java/com/match/mode java/match-mgr/src/test/java/com/match/mode/service/PlatformModeServiceTest.java
git commit -m "feat: manage authoritative platform mode"
```

## Task 3: Enforce generation and bidirectional participant API isolation

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/security/ParticipantModeGuard.java`
- Create: `java/match-mgr/src/main/java/com/match/security/ParticipantModeException.java`
- Create: `java/match-mgr/src/test/java/com/match/security/ParticipantModeGuardTest.java`
- Modify: `java/match-mgr/src/main/java/com/match/controller/UserController.java`
- Modify: `java/match-mgr/src/main/java/com/match/config/WebConfig.java`
- Modify: `java/match-mgr/src/main/java/com/match/config/GlobalExceptionHandler.java`
- Modify: `java/match-mgr/src/test/java/com/match/controller/UserControllerTest.java`
- Modify: `java/match-mgr/src/test/java/com/match/config/WebConfigAgentBoundaryTest.java`

- [ ] **Step 1: Write failing login and guard tests**

```java
@Test
void participantLoginReturnsAndStoresModeGeneration() {
    when(modeService.current()).thenReturn(new PlatformModeView("COMPETITION", 12, now));
    ResponseResult<Object> response = controller.login(credentials("user", "secret"));
    Map<?, ?> data = (Map<?, ?>) response.getData();
    assertEquals("COMPETITION", data.get("platformMode"));
    assertEquals(12L, data.get("modeGeneration"));
    verify(sessionGeneration).bindCurrentToken(12L);
}

@Test
void staleParticipantGenerationLogsOutAndRejects() {
    when(modeService.current()).thenReturn(new PlatformModeView("TRAINING", 13, now));
    when(sessionGeneration.currentTokenGeneration()).thenReturn(12L);
    assertThrows(ParticipantModeException.class, guard::requireCurrentGeneration);
    verify(loginSession).logout();
}
```

- [ ] **Step 2: Run and verify RED**

Run focused `ParticipantModeGuardTest,UserControllerTest,WebConfigAgentBoundaryTest`.

Expected: compilation fails for `ParticipantModeGuard` and login mode fields.

- [ ] **Step 3: Implement server-side isolation**

Store the generation in the Sa-Token token session under `participantModeGeneration`. The guard must bypass non-`USER` roles and expose these exact methods:

```java
public void bindCurrentParticipantSession(long generation);
public void requireCurrentGeneration();
public void requireTrainingMode();
public void requireCompetitionMode();
public void requireAdministrativeTrainingMode();
```

Add `platformMode` and `modeGeneration` to `currentUserData` for every role, but bind the token generation only for `USER`. In `WebConfig` apply:

```java
SaRouter.match("/**")
    .notMatch(PUBLIC_AND_AGENT_PATHS)
    .check(participantModeGuard::requireCurrentGeneration);
SaRouter.match("/user/training-environments/**")
    .check(participantModeGuard::requireTrainingMode);
SaRouter.match("/testPaper/**", "/score/**", "/train-url/**")
    .check(participantModeGuard::requireCompetitionMode);
```

Map generation mismatch to HTTP 401 with message `平台模式已切换，请重新登录` and data `{ "reasonCode": "PLATFORM_MODE_CHANGED" }`; map wrong-mode access to HTTP 403. Preserve the exact Agent and terminal exclusions asserted by existing boundary tests.

- [ ] **Step 4: Run focused tests and the existing security suite**

Run:

```powershell
docker run --rm -v "${PWD}\java\match-mgr:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-8 mvn -Dtest=ParticipantModeGuardTest,UserControllerTest,WebConfigAgentBoundaryTest,RoleGuardTest,ParticipantLoginGateTest test
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```powershell
git add java/match-mgr/src/main/java/com/match/controller/UserController.java java/match-mgr/src/main/java/com/match/security java/match-mgr/src/main/java/com/match/config java/match-mgr/src/test/java/com/match
git commit -m "feat: isolate participant sessions by platform mode"
```

## Task 4: Add competition container lifecycle and inspection

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/environment/persistence/CompetitionEnvironmentRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/persistence/CompetitionEnvironmentMapper.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/model/CreateCompetitionEnvironmentRequest.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/model/CompetitionEnvironmentView.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/service/CompetitionEnvironmentService.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/web/SuperAdminCompetitionEnvironmentController.java`
- Create: `java/match-mgr/src/test/java/com/match/environment/service/CompetitionEnvironmentServiceTest.java`
- Modify: `java/match-mgr/src/main/java/com/match/environment/service/EnvironmentCommandFactory.java`
- Modify: `java/match-mgr/src/main/java/com/match/environment/service/EnvironmentOperationReconciler.java`
- Modify: `java/match-mgr/src/test/java/com/match/environment/service/EnvironmentOperationReconcilerTest.java`

- [ ] **Step 1: Write failing lifecycle tests**

Tests must prove one environment per slot, exact `/competition/slot-{n}` workspace, immutable template versions/fingerprints, two stopped components before binding, and role/license checks.

```java
@Test
void createsUnboundCompetitionPairForExactSlot() {
    CompetitionEnvironmentView view = service.create(request(slotId, 2), superAdmin());
    assertNull(view.getUserId());
    assertEquals("competition/slot-2", view.getWorkspaceRelativePath());
    assertEquals("CREATE_COMPETITION_ENVIRONMENT", capturedCommand.getCommandType());
    assertEquals("STOPPED", capturedRecord.getDesiredState());
}
```

- [ ] **Step 2: Run and verify RED**

Expected: test compilation fails for competition environment classes.

- [ ] **Step 3: Implement lifecycle using existing allocation and command services**

Add an overloaded factory method with a private shared projection:

```java
public String createPayloadJson(CompetitionEnvironmentRecord environment, String operationId) {
    return createPayloadJson(environment.getEnvironmentId(), operationId,
            environment.getWorkspaceRelativePath(), environment.getSlotId(),
            environment.getAnnotationTemplateId(), environment.getAnnotationTemplateVersion(),
            environment.getAnnotationContainerName(), environment.getEditorTemplateId(),
            environment.getEditorTemplateVersion(), environment.getEditorContainerName());
}
```

Use stable container names `xkp-comp-{agentShort}-s{slot}-annotation` and `xkp-comp-{agentShort}-s{slot}-editor`. Creation dispatches a competition command but leaves desired mode stopped after successful creation. Inspection returns stored and last Agent-verified component states; no binding behavior belongs in this service.

Generalize `EnvironmentOperationReconciler` without changing its public method: after locking the operation, resolve the environment ID against exactly one of `training_environment` and `competition_environment`, then update only that mapper. A successful competition create or restore stores the bounded component result and `last_verified_at`; failed or missing results clear readiness. Existing training reconciliation remains byte-for-byte equivalent in behavior.

- [ ] **Step 4: Run lifecycle, command factory, and schema tests**

Expected: all focused tests PASS and existing training command payload tests remain unchanged.

- [ ] **Step 5: Commit**

```powershell
git add java/match-mgr/src/main/java/com/match/environment java/match-mgr/src/test/java/com/match/environment
git commit -m "feat: manage competition container pairs"
```

## Task 5: Enforce competition slot binding readiness

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/environment/model/CompetitionSlotView.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/model/BindCompetitionSlotRequest.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/service/CompetitionSlotBindingService.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/web/AdminCompetitionSlotController.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/web/UserCompetitionEnvironmentController.java`
- Create: `java/match-mgr/src/test/java/com/match/environment/service/CompetitionSlotBindingServiceTest.java`
- Create: `java/match-mgr/src/test/java/com/match/environment/web/CompetitionSlotControllerTest.java`

- [ ] **Step 1: Write failing readiness and concurrency tests**

Cover missing pair, one missing component, running component, absent successful Agent verification, fingerprint mismatch in the verified result, duplicate user, duplicate slot, competition mode, active transition, and valid stopped pair.

```java
@Test
void refusesBindingWhenCompetitionPairIsMissing() {
    when(environmentMapper.selectBySlotForUpdate(slotId)).thenReturn(null);
    ModeConflictException error = assertThrows(ModeConflictException.class,
            () -> service.bind(slotId, userId, admin()));
    assertEquals("COMPETITION_ENVIRONMENT_NOT_CREATED", error.getCode());
    verify(slotMapper, never()).bind(anyString(), anyInt(), any());
}

@Test
void bindsOnlyCompleteStoppedExactPair() {
    stubReadyStoppedPair();
    CompetitionSlotView result = service.bind(slotId, userId, admin());
    assertEquals(userId, result.getUserId());
    verify(slotMapper).bindIfUnbound(slotId, userId, now);
}
```

- [ ] **Step 2: Run and verify RED**

Expected: test compilation fails for binding service and request/view classes.

- [ ] **Step 3: Implement transactional binding**

The bind method must execute in this order:

```java
roleGuard.requireAdmin(actor);
participantModeGuard.requireAdministrativeTrainingMode();
ProcessingEnvironmentSlotRecord slot = slotMapper.selectForUpdate(slotId);
agentModeGuard.requireIdleForBinding(slot.getAgentId());
CompetitionEnvironmentRecord environment = environmentMapper.selectBySlotForUpdate(slotId);
readiness.requireCompleteStoppedExactPair(environment);
requireUserEnabledParticipant(userId);
requireNoExistingBinding(slot.getAgentId(), userId);
if (slotMapper.bindIfUnbound(slotId, userId, now()) != 1) throw conflict();
audit.recordSlotBinding(actor, slot, userId, environment);
```

Unbind uses the exact slot/user compare-and-set and is allowed only in platform `TRAINING`, with stopped competition containers and no active transition. Administrative list responses always include container information and a stable readiness code.

Expose `GET /user/competition-environment` for the authenticated participant. It returns exactly one of `UNBOUND`, `STARTING`, `DEGRADED`, or `RUNNING`. Only `RUNNING` includes server-generated annotation and editor URLs. `UNBOUND` is a successful response and does not block paper APIs.

- [ ] **Step 4: Run service and controller tests**

Expected: all focused tests PASS, including real `RoleGuard` ADMIN/SUPER_ADMIN/USER boundaries.

- [ ] **Step 5: Commit**

```powershell
git add java/match-mgr/src/main/java/com/match/environment java/match-mgr/src/test/java/com/match/environment
git commit -m "feat: bind ready competition slots"
```

## Task 6: Extend the Agent competition command contract

**Files (Agent repository):**
- Modify: `internal/protocol/command.go`
- Modify: `internal/protocol/command_test.go`
- Modify: `internal/runtime/command_dispatcher.go`
- Modify: `internal/runtime/command_dispatcher_test.go`
- Modify: `internal/runtime/fixture_test.go`
- Create: `testdata/command-create-competition-environment-v1.json`
- Create: `testdata/command-start-competition-environment-v1.json`
- Create: `testdata/command-stop-competition-environment-v1.json`
- Create: `testdata/command-restore-competition-environment-v1.json`
- Create matching platform fixtures under `java/match-mgr/src/test/resources/fixtures/` named `agent-command-*-competition-environment-v1.json`
- Modify: `java/match-mgr/src/test/java/com/match/agent/model/AgentCommandFixtureContractTest.java`

- [ ] **Step 1: Add fixtures and failing Go protocol tests**

Each fixture uses the existing strict environment payload and only changes command type. Add exact constants:

```go
const (
    CreateCompetitionEnvironment  CommandType = "CREATE_COMPETITION_ENVIRONMENT"
    StartCompetitionEnvironment   CommandType = "START_COMPETITION_ENVIRONMENT"
    StopCompetitionEnvironment    CommandType = "STOP_COMPETITION_ENVIRONMENT"
    RestoreCompetitionEnvironment CommandType = "RESTORE_COMPETITION_ENVIRONMENT"
)
```

Tests assert all four decode and duplicate/unknown/trailing/oversize rules remain enforced.

- [ ] **Step 2: Run and verify RED**

Run in the Agent repository:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace golang:1.23-bookworm go test -race ./internal/protocol ./internal/runtime -count=1
```

Expected: FAIL because command constants and dispatcher routes are absent.

- [ ] **Step 3: Route competition commands through the pair executor**

Extend both switches without weakening the safety stop grant rule:

```go
func isEnvironmentCommand(t protocol.CommandType) bool {
    switch t {
    case protocol.CreateTrainingEnvironment, protocol.StartTrainingEnvironment,
        protocol.StopTrainingEnvironment, protocol.RestoreTrainingEnvironment,
        protocol.CreateCompetitionEnvironment, protocol.StartCompetitionEnvironment,
        protocol.StopCompetitionEnvironment, protocol.RestoreCompetitionEnvironment:
        return true
    default:
        return false
    }
}

func isEnvironmentStop(t protocol.CommandType) bool {
    return t == protocol.StopTrainingEnvironment || t == protocol.StopCompetitionEnvironment
}
```

Map create/start/stop/restore competition commands to the same executor methods and stable result codes. Persist-before-execute and report retry behavior remain unchanged.

- [ ] **Step 4: Run Go race tests and cross-repository fixture test**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace golang:1.23-bookworm go test -race ./internal/protocol ./internal/runtime ./internal/container -count=1
```

Then run the Java `AgentCommandFixtureContractTest`. Expected: both repositories PASS and fixture SHA-256 values match byte-for-byte.

- [ ] **Step 5: Commit both repositories separately**

Agent:

```powershell
git add internal testdata
git commit -m "feat: execute competition environment commands"
```

Platform:

```powershell
git add java/match-mgr/src/test/resources/agent java/match-mgr/src/test/java/com/match/agent/model/AgentCommandFixtureContractTest.java
git commit -m "test: lock competition command fixtures"
```

## Task 7: Plan and execute entry transitions with phase barriers

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/mode/persistence/ProcessingAgentModeRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/persistence/ProcessingAgentModeMapper.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/persistence/ModeTransitionRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/persistence/ModeTransitionMapper.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/persistence/ModeTransitionStepRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/persistence/ModeTransitionStepMapper.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/persistence/ModeTrainingSnapshotMapper.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/service/ModeTransitionService.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/service/PlatformModeTransitionListener.java`
- Create: `java/match-mgr/src/test/java/com/match/mode/service/ModeTransitionEntryTest.java`

- [ ] **Step 1: Write failing entry state-machine tests**

Cover zero bindings, one binding, snapshot of actual `RUNNING` only, all training stops before any competition start, duplicate request idempotence, offline Agent degradation, conflicting operation degradation, and partial command failure.

```java
@Test
void entryStopsSnapshotBeforeStartingOnlyBoundCompetitionSlots() {
    stubRunningTraining("train-a", "train-b");
    stubBoundCompetition("comp-slot-2");
    ModeTransitionView transition = service.planEntry(agentId, admin());
    assertEquals(Arrays.asList("train-a", "train-b"), snapshotIds());
    assertSteps(transition, 1, "STOP_TRAINING", "train-a", "train-b");
    assertSteps(transition, 2, "START_COMPETITION", "comp-slot-2");
    assertFalse(dispatcher.wasDispatched("comp-slot-2"));
}
```

- [ ] **Step 2: Run and verify RED**

Expected: compilation fails for transition persistence and service classes.

- [ ] **Step 3: Implement entry planning and advancement**

`planEntry` is transactional and must:

```java
lockAgentMode(agentId);
returnExistingSameTargetOrRejectOpposite();
insertTransition("NORMAL", "COMPETITION", "RUNNING");
recordOfflineOrConflictingOperationAsDurableDegradedPreflight();
snapshotActuallyRunningTraining();
insertPhaseOneStopSteps();
insertPhaseTwoStartStepsForBoundReadyCompetitionOnly();
setAgentMode("COMPETITION", "ENTERING_COMPETITION", transitionId);
afterCommit(() -> dispatchReadyPhase(transitionId));
```

Offline Agents and Agents with active environment operations retain a transition with a stable preflight failure instead of disappearing from progress. A later SUPER_ADMIN retry reruns preflight before dispatch. `dispatchReadyPhase` dispatches only the lowest phase with non-terminal steps. Phase 2 is ineligible until every Phase 1 step is `SUCCEEDED`. Zero-step phases complete immediately. Command idempotency is `mode:{transitionId}:{phase}:{environmentId}:{action}`.

`PlatformModeTransitionListener` handles `PlatformModeChangedEvent` only after commit and calls `convergeAll(targetMode, actorIdentity)`. It catches failures per Agent so one server cannot prevent transition creation for later servers.

- [ ] **Step 4: Run entry and existing environment tests**

Expected: entry tests PASS; existing `EnvironmentOperationServiceTest` and `EnvironmentOperationReconcilerTest` remain green.

- [ ] **Step 5: Commit**

```powershell
git add java/match-mgr/src/main/java/com/match/mode java/match-mgr/src/test/java/com/match/mode
git commit -m "feat: orchestrate competition mode entry"
```

## Task 8: Add result reconciliation, exit, retry, and recovery

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/mode/service/ModeTransitionReconciler.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/service/ModeTransitionRecovery.java`
- Create: `java/match-mgr/src/test/java/com/match/mode/service/ModeTransitionExitTest.java`
- Create: `java/match-mgr/src/test/java/com/match/mode/service/ModeTransitionReconcilerTest.java`
- Create: `java/match-mgr/src/test/java/com/match/mode/service/ModeTransitionRecoveryTest.java`
- Modify: `java/match-mgr/src/main/java/com/match/agent/service/AgentCommandService.java`
- Modify: `java/match-mgr/src/test/java/com/match/agent/service/AgentCommandServiceTest.java`

- [ ] **Step 1: Write failing exit and recovery tests**

Cover frozen competition IDs, competition stop barrier, snapshot-only restore, failed step to `DEGRADED`, successful-step preservation, retry current target only, opposite-target rejection, management restart, and bounded scans.

```java
@Test
void exitStopsFrozenCompetitionBeforeRestoringSnapshot() {
    ModeTransitionView exit = service.planExit(agentId, admin());
    assertSteps(exit, 1, "STOP_COMPETITION", "comp-slot-2");
    assertSteps(exit, 2, "RESTORE_TRAINING", "train-a", "train-b");
    reconciler.reconcile(commandFor("comp-slot-2"), true, "ENVIRONMENT_STOPPED", null, stoppedPair());
    assertTrue(dispatcher.wasDispatched("train-a"));
}

@Test
void failedStepLeavesCompletedStepsAndBecomesDegraded() {
    reconcileSuccess(firstStop);
    reconcileFailure(secondStop, "DOCKER_TIMEOUT");
    assertEquals("SUCCEEDED", step(firstStop).getState());
    assertEquals("FAILED", step(secondStop).getState());
    assertEquals("DEGRADED", transition().getState());
    assertEquals("DEGRADED", agentMode().getActualMode());
}
```

- [ ] **Step 2: Run and verify RED**

Expected: compilation fails for reconciliation and recovery services.

- [ ] **Step 3: Implement exact reconciliation semantics**

`AgentCommandService` first asks the mode reconciler to handle the exact command ID. Only when it returns `false` does it invoke the existing environment operation reconciler. This prevents one command result from terminalizing two workflows. The mode method is:

```java
@Transactional
public boolean reconcileIfPresent(String commandId, boolean success, String code,
                                  String message, String componentJson) {
    ModeTransitionStepRecord step = stepMapper.selectByCommandForUpdate(commandId);
    if (step == null) return false;
    if (terminal(step.getState())) return true;
    stepMapper.markTerminal(step.getStepId(), success ? "SUCCEEDED" : "FAILED",
            code, bounded(message), componentJson, now());
    competitionOrTrainingState.reconcile(step, success, componentJson);
    if (!success) {
        transitionMapper.markDegraded(step.getTransitionId(), bounded(message), now());
        agentModeMapper.markDegraded(step.getTransitionId(), now());
        return true;
    }
    advanceIfPhaseComplete(step.getTransitionId());
    return true;
}
```

Retry resets only `FAILED` steps in the current incomplete phase to `PENDING`, keeps `SUCCEEDED`, clears the failure summary, and redispatches after commit. Recovery selects at most 100 active transitions ordered by request time and uses the same advancement method; one failure is caught per transition so later rows progress.

- [ ] **Step 4: Run all transition tests**

Expected: entry, exit, reconcile, and recovery tests PASS with no duplicate dispatch.

- [ ] **Step 5: Commit**

```powershell
git add java/match-mgr/src/main/java/com/match/mode java/match-mgr/src/test/java/com/match/mode java/match-mgr/src/main/java/com/match/agent
git commit -m "feat: recover competition mode transitions"
```

## Task 9: Expose role-correct management and operations APIs

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/mode/model/ChangePlatformModeRequest.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/model/ModeTransitionView.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/web/AdminPlatformModeController.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/web/SuperAdminModeOperationsController.java`
- Create: `java/match-mgr/src/test/java/com/match/mode/web/PlatformModeControllerTest.java`
- Modify: `java/match-mgr/src/main/java/com/match/dashboard/service/DashboardOverviewService.java`
- Modify: `java/match-mgr/src/test/java/com/match/dashboard/service/DashboardOverviewServiceTest.java`

- [ ] **Step 1: Write failing controller boundary tests**

Use real `RoleGuard` behavior. Prove ADMIN can read/change global mode but cannot use operational retry; SUPER_ADMIN can inspect/retry but cannot impersonate ADMIN's normal switch endpoint; USER gets 403 everywhere.

```java
@Test
void adminChangesModeWithExactConfirmation() {
    ChangePlatformModeRequest request = new ChangePlatformModeRequest();
    request.setTargetMode("COMPETITION");
    request.setConfirmation("ENTER COMPETITION");
    assertEquals(200, controller.change(request).getCode());
}
```

- [ ] **Step 2: Run and verify RED**

Expected: compilation fails because controllers and views are absent.

- [ ] **Step 3: Implement bounded APIs**

Expose:

```text
GET  /admin/platform-mode
POST /admin/platform-mode
GET  /admin/platform-mode/transitions
GET  /admin/platform-mode/transitions/{id}
GET  /operations/mode-transitions/{id}
POST /operations/mode-transitions/{id}/retry
```

Require exact confirmations `ENTER COMPETITION` and `EXIT COMPETITION`. List endpoints cap `limit` at 100 and never return command lease tokens or payload secrets. Add dashboard counts for each Agent mode state without changing the ADMIN/SUPER_ADMIN dashboard role separation.

- [ ] **Step 4: Run controller, role, dashboard, and audit tests**

Expected: all focused tests PASS.

- [ ] **Step 5: Commit**

```powershell
git add java/match-mgr/src/main/java/com/match/mode java/match-mgr/src/main/java/com/match/dashboard java/match-mgr/src/test/java/com/match/mode java/match-mgr/src/test/java/com/match/dashboard
git commit -m "feat: expose competition mode controls"
```

## Task 10: Isolate Vue participant routes and forced re-login

**Files:**
- Modify: `vue/src/utils/auth.js`
- Modify: `vue/src/utils/request.js`
- Modify: `vue/src/store/modules/Match.js`
- Modify: `vue/src/navigation/roleNavigation.js`
- Modify: `vue/src/router/index.js`
- Modify: `vue/src/views/Login.vue`
- Create: `vue/src/views/competition/CompetitionPractical.vue`
- Create: `vue/scripts/test-competition-mode-contract.js`
- Modify: `vue/package.json`

- [ ] **Step 1: Write a failing frontend contract test**

The Node test must load source modules and assert:

```js
assert.deepStrictEqual(landingRoute('USER', 'TRAINING'), { path: '/course-platform' })
assert.deepStrictEqual(landingRoute('USER', 'COMPETITION'), { path: '/Publicity' })
assert.ok(routerSource.includes("meta: { roles: ['USER'], modes: ['TRAINING'] }"))
assert.ok(routerSource.includes("meta: { roles: ['USER'], modes: ['COMPETITION'] }"))
assert.ok(requestSource.includes('PLATFORM_MODE_CHANGED'))
assert.ok(requestSource.includes('clearSession()'))
```

- [ ] **Step 2: Run and verify RED**

Run:

```powershell
cd vue
npm run test:competition-mode
```

Expected: FAIL because the script and mode-aware routing do not exist.

- [ ] **Step 3: Implement mode-aware authentication and routing**

Persist exact fields from login:

```js
export function getPlatformMode () {
  const value = getUserInfo().platformMode
  return value === 'COMPETITION' ? 'COMPETITION' : 'TRAINING'
}

function landingRoute (role, mode) {
  if (role === SUPER_ADMIN) return { path: '/operations' }
  if (role === ADMIN) return { path: '/management' }
  return mode === 'COMPETITION' ? { path: '/Publicity' } : { path: '/course-platform' }
}
```

Mark training routes with `modes: ['TRAINING']` and the existing participant competition layout with `roles: ['USER'], modes: ['COMPETITION']`. The router redirects a logged-in USER away from the wrong layout. The response interceptor handles HTTP 401 whose response data contains `reasonCode === 'PLATFORM_MODE_CHANGED'`, clears auth, shows the server message once, and routes to `/login`.

Add `/competition-practical` under the competition layout. It must render practical readiness from the API; it must not synthesize container links from local state.

- [ ] **Step 4: Run navigation and production build**

Run:

```powershell
npm run test:competition-mode
npm run test:navigation
npm run build
```

Expected: PASS; only existing Browserslist and bundle-size warnings are allowed.

- [ ] **Step 5: Commit**

```powershell
git add vue
git commit -m "feat: isolate participant mode interfaces"
```

## Task 11: Build management and operations mode views

**Files:**
- Create: `vue/src/api/PlatformMode.js`
- Create: `vue/src/api/CompetitionEnvironments.js`
- Create: `vue/src/views/management/CompetitionMode.vue`
- Create: `vue/src/views/operations/CompetitionEnvironments.vue`
- Modify: `vue/src/views/competition/CompetitionPractical.vue`
- Modify: `vue/src/router/index.js`
- Modify: `vue/src/navigation/roleNavigation.js`
- Modify: `vue/scripts/test-competition-mode-contract.js`

- [ ] **Step 1: Extend the failing contract test**

Assert exact API endpoints, confirmation strings, bounded readiness states, retry role placement, and no practical links for unbound users:

```js
assert.ok(apiSource.includes("'/admin/platform-mode'"))
assert.ok(apiSource.includes("`/operations/mode-transitions/${id}/retry`"))
assert.ok(managementSource.includes('ENTER COMPETITION'))
assert.ok(managementSource.includes('EXIT COMPETITION'))
assert.ok(practicalSource.includes('未分配比赛环境'))
assert.ok(practicalSource.includes("readiness === 'RUNNING'"))
```

- [ ] **Step 2: Run and verify RED**

Expected: FAIL because API modules and views do not exist.

- [ ] **Step 3: Implement complete work surfaces**

`CompetitionMode.vue` shows the global mode, generation, changed time, enter/exit confirmation, and a dense Agent table with actual/desired state, current phase, completed/total steps, failure summary, and detail drawer.

`CompetitionEnvironments.vue` shows four stable slot rows per selected Agent. Each row shows binding, annotation/editor container names, image versions, fingerprints, ports, workspace, component states, and readiness reason. Create/restore uses SUPER_ADMIN endpoints. Bind/unbind controls are rendered only in the ADMIN management view; retry is rendered only in operations.

`CompetitionPractical.vue` has three complete states:

```text
UNBOUND: paper access remains; show "未分配比赛环境" and no links.
STARTING/DEGRADED: show bounded status and no links.
RUNNING: show annotation and editor links returned by the server.
```

Do not nest cards or add feature-explanation copy. Use existing Element UI tables, status tags, dialogs, icons, spacing, and 8px-or-less radii.

- [ ] **Step 4: Run all Vue contracts and build**

Run all existing `test:*` scripts plus `npm run build`. Expected: all PASS with no route regression.

- [ ] **Step 5: Commit**

```powershell
git add vue
git commit -m "feat: add competition mode work surfaces"
```

## Task 12: Full verification, recovery smoke, and documentation

**Files:**
- Create: `scripts/test-competition-mode-flow.ps1`
- Create: `scripts/test-competition-mode-flow-contract.ps1`
- Modify: `docs/agents/operator-guide.md`
- Modify: `docs/agents/administrator-guide.md`
- Modify: `README.md`

- [ ] **Step 1: Write the failing PowerShell contract test**

Require the flow script to cover exact stages without embedding credentials or tickets:

```powershell
$source = Get-Content -Raw $FlowScript
@('TRAINING','COMPETITION','modeGeneration','DEGRADED','retry',
  '未分配比赛环境','STOP_TRAINING_ENVIRONMENT',
  'START_COMPETITION_ENVIRONMENT','RESTORE_TRAINING_ENVIRONMENT') |
  ForEach-Object { if (-not $source.Contains($_)) { throw "Missing flow stage: $_" } }
if ($source -match 'admin123|tokenValue\s*=\s*["'']') { throw 'Embedded credential detected' }
```

- [ ] **Step 2: Run and verify RED**

Run `powershell -File scripts/test-competition-mode-flow-contract.ps1`.

Expected: FAIL because the flow script is missing.

- [ ] **Step 3: Implement the environment-driven smoke script**

The script accepts management URL, ADMIN credentials via secure environment variables, Agent ID, bound and unbound participant credentials, and timeout. It must:

1. assert initial `TRAINING` mode;
2. log in both participants and record generation;
3. enter `COMPETITION`;
4. prove both old participant tokens return the stable mode-change 401;
5. prove unbound participant can read paper data but receives `UNBOUND` practical state;
6. prove only the bound slot gets a start step;
7. inject one deterministic fake Agent failure, assert `DEGRADED`, retry, and await success;
8. exit to `TRAINING` and prove tokens invalidate again;
9. assert only the captured training snapshot is restored;
10. emit a sanitized pass/fail summary and clean up only resources it created.

- [ ] **Step 4: Run complete repository verification**

Platform Java:

```powershell
docker run --rm -v "${PWD}\java\match-mgr:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-8 mvn test
```

Agent:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace golang:1.23-bookworm go test -race ./... -count=1
```

Vue:

```powershell
cd vue
npm run test:competition-mode
npm run test:navigation
npm run test:license
npm run test:agents
npm run test:templates
npm run test:dashboard
npm run test:terminal
npm run build
```

PowerShell:

```powershell
powershell -File scripts/test-competition-mode-flow-contract.ps1
[void][scriptblock]::Create((Get-Content -Raw scripts/test-competition-mode-flow.ps1))
```

Expected: every executable suite PASS. If a real Agent/MySQL stack is unavailable, report the smoke script as not run; do not claim end-to-end success from contract tests.

- [ ] **Step 5: Update operator documentation**

Document prerequisites, pre-creating competition pairs, binding readiness, mode confirmations, USER forced re-login, zero-binding paper behavior, degraded diagnosis, retry, and exit restoration. Do not document `xkp-train` or MPS as delivered in this phase.

- [ ] **Step 6: Run final diff and secret checks**

Run in both repositories:

```powershell
git diff --check
rg -n "admin123|Feng113147|xkp-terminal-ticket\.[A-Za-z0-9_-]{20,}|Authorization: Bearer [A-Za-z0-9]" .
git status --short
```

Expected: diff check clean, no committed secrets, and only intended files modified.

- [ ] **Step 7: Commit documentation and verification**

```powershell
git add scripts docs README.md
git commit -m "test: verify competition mode orchestration"
```

## Final Review Checklist

- [ ] Platform mode changes immediately and increments generation exactly once.
- [ ] Old USER sessions fail on the next request; ADMIN and SUPER_ADMIN remain valid.
- [ ] Training and competition participant APIs reject the opposite mode.
- [ ] Zero bindings permit paper answering and expose no practical link.
- [ ] Only bound, complete, stopped competition pairs can be assigned and started.
- [ ] Every Agent obeys stop-before-start and stop-before-restore barriers.
- [ ] Exit restores only the captured actually-running training environments.
- [ ] Partial failure is truthful, durable, degraded, and retryable without rollback.
- [ ] Management restart and duplicate Agent delivery remain idempotent.
- [ ] No network or Docker call runs inside a database transaction.
- [ ] License, role, audit, capacity, pagination, and secret-redaction boundaries pass.
- [ ] Java full tests, Go full race tests, Vue contracts/build, and PowerShell syntax pass.
- [ ] Real end-to-end status is reported separately from static/contract verification.
