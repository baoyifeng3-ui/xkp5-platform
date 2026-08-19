# Training Environment Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Follow superpowers:test-driven-development for every behavior change. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver one complete training environment lifecycle, consisting of an annotation container and an editor container that share one host workspace and can be created, started, stopped, and restored through the existing durable Agent channel.

**Architecture:** The Spring management service owns templates, assignments, ports, state, operations, roles, licensing, and audit. It sends only strict version-one environment commands. The Go Agent validates those commands and converges local Docker state through a narrow executor interface. A fake executor provides the first end-to-end acceptance boundary; real Ubuntu validation follows.

**Tech Stack:** Java 8, Spring Boot 2.1, MyBatis-Plus, Flyway, MySQL 8, Go 1.22, Docker Engine API/CLI adapter, JUnit 4, Go `testing`, Vue 2, Element UI, Docker BuildKit.

---

## Repositories

Platform worktree:

```text
C:\Users\84502\Desktop\新建文件夹\project\xkp5-platform\.worktrees\phase-1-platform-shell
```

Agent repository:

```text
C:\Users\84502\Desktop\新建文件夹\project\xkp-agent
```

The original `competition` repository is read-only reference material and must
not be modified or receive commits.

## Task 1: Add The Training Environment Schema

**Platform files:**

- Create: `java/match-mgr/src/main/resources/db/migration/V22__training_environment_lifecycle.sql`
- Create: `java/match-mgr/src/test/java/com/match/environment/persistence/TrainingEnvironmentSchemaTest.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/persistence/ContainerTemplateRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/persistence/TrainingEnvironmentRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/persistence/EnvironmentPortAllocationRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/persistence/EnvironmentOperationRecord.java`
- Create: matching MyBatis mapper interfaces in the same package

- [ ] Write a failing schema test that requires template, environment, port-allocation, and operation tables; immutable template versions; unique Agent/slot and Agent/port constraints; environment optimistic version; and operation-to-command correlation.
- [ ] Run `TrainingEnvironmentSchemaTest` in the Java Docker test image and confirm the missing migration failure.
- [ ] Add V22 with bounded enum-like strings, UTC timestamps, JSON only for immutable component details/results, and indexes for Agent state, user/course assignment, and pending operations.
- [ ] Add records and mappers with explicit SQL for row locking and compare-and-set state updates.
- [ ] Run the targeted schema and mapper tests until green.
- [ ] Commit: `feat: add training environment persistence`

## Task 2: Implement Versioned Container Templates

**Platform files:**

- Create: `java/match-mgr/src/main/java/com/match/environment/model/ContainerTemplate*.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/service/ContainerTemplateService.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/web/SuperAdminContainerTemplateController.java`
- Create: matching service/controller tests

- [ ] Write failing tests for annotation/editor component validation, exact runtime allowlist (`sysbox-runc`, NVIDIA), image/name syntax, typed ports, mount-target allowlist, CPU/memory/GPU bounds, and role/license enforcement.
- [ ] Write failing tests proving published versions are immutable, edits create the next version, and disabled versions cannot be assigned to new environments.
- [ ] Implement strict request DTOs and a service that normalizes defaults without accepting shell fragments, arbitrary host paths, privileged mode, host networking, or arbitrary mounts.
- [ ] Expose super-admin-only create/version/disable/list/detail endpoints.
- [ ] Run targeted tests and commit: `feat: manage versioned container templates`

## Task 3: Freeze The Java/Go Environment Command Contract

**Platform files:**

- Create four JSON fixtures under `java/match-mgr/src/test/resources/fixtures/`
- Create: `java/match-mgr/src/test/java/com/match/environment/model/EnvironmentCommandContractTest.java`
- Modify: `java/match-mgr/src/main/java/com/match/agent/model/AgentCommandEnvelope.java`
- Create typed environment command payload/component DTOs

**Agent files:**

- Create matching fixtures under `testdata/`
- Modify: `internal/protocol/command.go`
- Modify: `internal/protocol/command_test.go`

- [ ] Add identical create/start/stop/restore version-one fixtures to both repositories. Include deterministic names, relative workspace, component fingerprints, ports, limits, mount targets, and an operation ID.
- [ ] Write failing Java serialization tests and strict Go decoding tests. Go must reject unknown fields, trailing JSON, oversized payloads, invalid names, absolute/traversing workspaces, duplicate host ports, unapproved runtime/mount targets, and unknown command versions.
- [ ] Extend the closed command type set; do not add a generic command or shell field.
- [ ] Run Java and Go targeted contract tests until both accept the same fixtures.
- [ ] Commit Agent: `feat: define environment command protocol`
- [ ] Commit platform: `feat: define environment command contract`

## Task 4: Create And Assign A Training Environment

**Platform files:**

- Create: `java/match-mgr/src/main/java/com/match/environment/service/TrainingEnvironmentService.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/service/EnvironmentCommandFactory.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/web/SuperAdminTrainingEnvironmentController.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/model/TrainingEnvironment*.java`
- Create matching tests
- Modify: `java/match-mgr/src/main/java/com/match/agent/service/AgentCommandService.java`

- [ ] Write failing tests for super-admin assignment, active-license check, enabled Agent, slot uniqueness, template pinning, relative workspace generation, deterministic names, transactional port reservation, and duplicate request idempotency.
- [ ] Write a failure test proving a port conflict leaves no environment, allocation, operation, or command row behind.
- [ ] Implement one transaction that persists `CREATING`, allocations, operation, audit, and a `CREATE_TRAINING_ENVIRONMENT` command.
- [ ] Return an accepted operation view rather than optimistic success.
- [ ] Run targeted tests and commit: `feat: create assigned training environments`

## Task 5: Orchestrate Start, Stop, And Restore

**Platform files:**

- Modify: `TrainingEnvironmentService.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/web/AdminTrainingEnvironmentController.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/web/UserTrainingEnvironmentController.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/service/EnvironmentOperationService.java`
- Create matching tests

- [ ] Write failing role-boundary tests: admin may start/stop/restore; user may list assignments and one-click start only; super admin may perform all operations.
- [ ] Write license tests proving create/start/restore are rejected when expired while stop remains available.
- [ ] Write concurrency tests that lock a user's assignments, stop the previously active environment, then start the selected environment without allowing two `RUNNING` desired states.
- [ ] Write idempotency and illegal-state tests for repeated start/stop/restore and concurrent mutating requests.
- [ ] Implement operation creation using the existing durable Agent command lease/result transport.
- [ ] Keep restore semantics fixed: stop, remove both containers, recreate from pinned templates, preserve workspace, finish in `STOPPED`.
- [ ] Run targeted tests and commit: `feat: orchestrate training environment lifecycle`

## Task 6: Add A Narrow Docker Executor To The Agent

**Agent files:**

- Create: `internal/container/executor.go`
- Create: `internal/container/docker.go`
- Create: `internal/container/docker_test.go`
- Create: `internal/container/fake.go`
- Create: `internal/container/validator.go`
- Create: `internal/container/validator_test.go`
- Modify: `internal/config/config.go`
- Modify: `internal/config/config_test.go`

- [ ] Define an executor interface with inspect, create pair, start pair, stop pair, and restore pair operations. It receives validated typed specs, never command strings.
- [ ] Write failing validator tests for workspace-root containment, symlink/path traversal, container fingerprints, name/image/runtime/port/resource bounds, and the two allowed mount targets.
- [ ] Write fake-executor state tests for pair atomicity and component-level results.
- [ ] Implement production Docker calls with exact argument construction or a typed Engine client. Capture bounded stdout/stderr and never log credentials.
- [ ] Create the shared host workspace once and pass the same absolute source to both component mounts.
- [ ] Run `go test -race ./internal/container -count=1` and commit: `feat: add validated container executor`

## Task 7: Dispatch Environment Commands Idempotently

**Agent files:**

- Modify: `internal/runtime/command_dispatcher.go`
- Modify: `internal/runtime/command_dispatcher_test.go`
- Modify: `internal/runtime/command_store.go`
- Modify: `internal/runtime/agent.go`
- Modify: `cmd/xkp-agent/main.go`

- [ ] Write failing dispatcher tests for all four commands, start-before-execute ordering, stored terminal-result replay, operation ID reuse, partial component failure, compensation, and restart recovery.
- [ ] Require expected fingerprints before start/stop so unrelated same-name containers are never controlled.
- [ ] Implement create as two stopped containers, start/stop as convergent pair operations, and restore as stop/remove/recreate while retaining the workspace.
- [ ] Persist terminal result details until server acknowledgement. Redelivery of the same operation returns or continues the same result.
- [ ] Wire the production executor and configurable workspace root.
- [ ] Run `go test -race ./... -count=1` and commit: `feat: execute training environment commands`

## Task 8: Reconcile Results And License State

**Platform files:**

- Create: `java/match-mgr/src/main/java/com/match/environment/service/EnvironmentOperationReconciler.java`
- Create matching tests
- Modify Agent heartbeat acknowledgement model/service
- Modify environment query DTOs

**Agent files:**

- Modify heartbeat response model and runtime state
- Add freshness/license-operation tests

- [ ] Write failing platform tests mapping terminal command results to `STOPPED`, `RUNNING`, `DEGRADED`, or `ERROR` only after Agent confirmation.
- [ ] Write tests preserving per-component code/message details and preventing stale operations from overwriting newer state.
- [ ] Add an authenticated heartbeat grant containing operation permission and grant expiry. The Agent rejects create/start/restore with a stale or denied grant; stop remains allowed.
- [ ] Add expiry enforcement that queues stop for active environments without auto-starting after renewal.
- [ ] Run Java targeted tests and full Go race tests.
- [ ] Commit Agent: `feat: enforce environment operation grants`
- [ ] Commit platform: `feat: reconcile training environment operations`

## Task 9: Add The First Usable Management Surface

**Platform files:**

- Create: `vue/src/api/TrainingEnvironments.js`
- Create: `vue/src/views/operations/ContainerTemplates.vue`
- Create: `vue/src/views/operations/TrainingEnvironments.vue`
- Modify administrator training-management view
- Modify normal-user training-environment view
- Add unit tests where the existing Vue harness supports them

- [ ] Add API methods for template/version operations, assignment/create, list/detail, and lifecycle operation status.
- [ ] Test role visibility, destructive restore confirmation, in-flight action disabling, status polling, partial component failure display, and absence of super-admin controls from admin/user views.
- [ ] Implement compact status tables and action controls consistent with the existing shell. Do not perform the deferred visual redesign.
- [ ] Ensure accepted operations display `等待处理/执行中` until confirmed, never immediate success.
- [ ] Run the production frontend build and commit: `feat: add training environment controls`

## Task 10: End-To-End Fake Agent Verification

**Platform files:**

- Create: `deploy/tests/fake-agent-training-environment-flow.ps1`
- Modify: `docs/operations/processing-agent.md`

**Agent files:**

- Modify: `README.md`

- [ ] Build a non-destructive fake-Agent flow that imports an active test license, registers an Agent, publishes two templates, assigns one environment, and exercises create/start/stop/restore.
- [ ] Assert both components receive the same workspace source, restore preserves a sentinel file, repeated operations are idempotent, and partial failure reaches `DEGRADED` with component details.
- [ ] Run all Java tests, `go test -race ./... -count=1`, frontend production build, Flyway migration against disposable MySQL, and the fake-Agent flow.
- [ ] Document real Ubuntu prerequisites: Docker, `sysbox-runc`, NVIDIA driver/runtime, images, workspace ownership, ports, and Agent service permissions.
- [ ] Request code review, resolve findings, and repeat the affected plus full verification suites.
- [ ] Commit platform docs/harness: `test: verify training environment lifecycle`
- [ ] Commit Agent docs: `docs: document container execution prerequisites`

## Acceptance Boundary

The slice is complete only when a single assigned annotation/editor pair can be
created, started, stopped, and restored end to end through the durable Agent
channel; both components use the same preserved host workspace; role, license,
port, path, and idempotency tests pass; and the complete Java and Go suites are
green.

Real Docker, `sysbox-runc`, NVIDIA, target-image, and RTX 2080 acceptance remains
explicitly pending until an Ubuntu processing server is connected.
