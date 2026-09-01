# 模板级 MPS GPU 调度 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with checkpoints.

**Goal:** 让 MPS 成为容器模板级可选 GPU 调度限制，同时保持未启用 MPS 的现有容器完全兼容。

**Architecture:** 管理端保存并传递 `mpsEnabled` 与比例；Agent 在 Docker 适配层按开关调用 MPS 管理器。MPS 失败只影响声明启用 MPS 的环境，并通过命令结果回传管理端。

**Tech Stack:** Spring Boot/MyBatis、Vue 2、Go、Docker Engine/NVIDIA MPS。

---

### Task 1: 管理端模板契约

**Files:**
- Modify: `java/match-mgr/src/main/java/com/match/environment/persistence/ContainerTemplateRecord.java`
- Modify: `java/match-mgr/src/main/java/com/match/environment/model/ContainerTemplateRequest.java`
- Modify: `java/match-mgr/src/main/java/com/match/environment/service/ContainerTemplateService.java`
- Create: `java/match-mgr/src/main/resources/db/migration/V46__container_template_mps.sql`
- Test: `java/match-mgr/src/test/java/com/match/environment/service/ContainerTemplateServiceTest.java`

- [ ] Add nullable `mps_enabled` with false default and Java `Boolean mpsEnabled`.
- [ ] Validate MPS only for EDITOR + GPU; require GPU percentage 1-100 when enabled.
- [ ] Add a failing test for invalid combinations, then implement and run the service test.

### Task 2: Agent protocol and Docker adapter

**Files:**
- Modify: `xkp-agent/internal/protocol/command.go`
- Modify: `xkp-agent/internal/container/docker.go`
- Create: `xkp-agent/internal/container/mps.go`
- Test: `xkp-agent/internal/container/docker_test.go`

- [ ] Add `MPSEnabled bool` and encode it in environment component payloads.
- [ ] Add an MPS manager interface with start/configure/stop methods.
- [ ] Make DockerExecutor call it only when `MPSEnabled` is true; preserve existing config when false.
- [ ] Test both branches and error propagation.

### Task 3: Template UI

**Files:**
- Modify: `vue/src/views/operations/ContainerTemplates.vue`
- Modify: `vue/src/api/ContainerTemplates.js`
- Test: `vue/scripts/test-container-template-view-contract.js`

- [ ] Add an MPS switch visible only for EDITOR + GPU.
- [ ] Rename the percentage label to “MPS GPU 比例”; hide and clear it when MPS is off.
- [ ] Add contract assertions for valid combinations and backward-compatible defaults.

### Task 4: Integration verification

**Files:**
- Modify: `xkp-agent/deploy/install.sh`
- Modify: `xkp-agent/deploy/one-click-install.sh`
- Test: `xkp-agent/internal/runtime/*_test.go`

- [ ] Detect MPS prerequisites during Agent startup without requiring them for non-MPS environments.
- [ ] Run Go tests and build the Linux Agent.
- [ ] Run Maven targeted tests and Vue build/contracts.
- [ ] Verify one MPS-enabled and one non-MPS environment on a processing server.
