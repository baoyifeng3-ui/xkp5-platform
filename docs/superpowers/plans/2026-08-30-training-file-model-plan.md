# Training File And Model Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add temporary dataset transfer, permanent multi-file resource uploads, and audited model deployment from `/home/student`.

**Architecture:** Reuse `platform_file`, FastDFS, the Agent command queue, and existing delivery result listeners. Temporary transfers get a separate table and 24-hour cleanup; model browsing/copying is implemented as typed Agent commands, never arbitrary shell input.

**Tech Stack:** Spring Boot, MySQL/Flyway, FastDFS, Go Agent, Vue 2, XLSX-independent browser uploads.

---

### Task 1: Temporary transfer persistence and download

**Files:**
- Create: `V49__temporary_training_transfers.sql`
- Create package: `com.match.transfer`
- Test: `TemporaryTransferServiceTest.java`

- [ ] Test ownership, 24-hour expiry, one-time download ticket, success cleanup, and retryable failure.
- [ ] Implement upload/status/download/complete endpoints with role/environment ownership checks.
- [ ] Add scheduled cleanup for expired failed artifacts.

### Task 2: Agent transfer command

**Files:**
- Modify: `xkp-agent/internal/protocol/command.go`
- Modify: `xkp-agent/internal/runtime/command_dispatcher.go`
- Create: `xkp-agent/internal/transfer/transfer.go`
- Test: matching Go tests.

- [ ] Define a typed download command containing URL, SHA-256, environment container, and relative target.
- [ ] Reject traversal and targets outside `/home/student/data`.
- [ ] Download to a temporary name, verify digest, atomically rename, and report bytes/progress.
- [ ] Run `go test ./internal/protocol ./internal/runtime ./internal/transfer`.

### Task 3: In-page dataset upload

**Files:**
- Create: `vue/src/components/training/DatasetUploadDialog.vue`
- Create: `vue/src/api/TrainingTransfers.js`
- Modify: `TrainingEnvironment.vue`

- [ ] Add multi-select, per-file progress, cancel, retry, and restored backend status.
- [ ] Upload datasets to `/home/student/data/datasets` and close only after tasks are queued.

### Task 4: Permanent resource multi-upload

**Files:**
- Modify: `ResourceUploadDialog.vue`
- Modify: resource upload controllers to accept repeated multipart files.
- Test: resource service tests and frontend resource contract.

- [ ] Process files independently and return one result per file.
- [ ] Preserve course bindings and directory selection for every uploaded file.

### Task 5: Typed model browser and deployment

**Files:**
- Create Flyway table `model_deployment_task`.
- Create backend package `com.match.modeldeployment`.
- Extend Agent protocol with list-files and deploy-model commands.
- Create `vue/src/components/training/ModelDeploymentDialog.vue`.

- [ ] List only `/home/student` descendants, excluding symlinks and traversal.
- [ ] Require one model and one config file.
- [ ] Detect target conflicts and require explicit `overwrite=true`.
- [ ] Copy atomically to `/usr/local/zy-T100/utils_x86/models/A` and record audit/result.
- [ ] Verify ordinary-user ownership and admin-demo selected environment permissions.

### Task 6: Real verification

- [ ] Upgrade both Agents and verify versions.
- [ ] Upload two temporary datasets and confirm no resource-space records remain.
- [ ] Upload multiple permanent resources and verify all appear.
- [ ] Browse `/home/student`, deploy model/config, verify target files and overwrite confirmation.

