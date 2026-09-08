# Simple Image Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the review/import/publish image workflow with canonical TAR files that become enabled after upload and deploy directly to Agents, while showing each Agent's Docker images and containers.

**Architecture:** Keep upload sessions only for chunk transfer. On completion, move the assembled TAR to a canonical path keyed by repository and filename and upsert one `image_file` row. Direct deployment references that row and sends its image name to the Agent; inventory rides in the existing heartbeat JSON.

**Tech Stack:** Spring Boot 2.1, MyBatis Plus, Flyway, Vue 2/Element UI, Go Agent, Docker API.

**Spec:** `docs/superpowers/specs/2026-09-08-simple-image-delivery-design.md`

## Global Constraints

- Accept only `.tar`; do not calculate or validate archive SHA-256.
- Preserve authorization, path safety, and the 50 GiB limit.
- Keep legacy registry tables for historical compatibility but remove them from the active UI.
- `docker load` is the final archive validity check.

---

### Task 1: Canonical image files

**Files:**
- Create: `java/match-mgr/src/main/resources/db/migration/V54__simple_image_files.sql`
- Create: `java/match-mgr/src/main/java/com/match/registry/persistence/ImageFileRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/registry/persistence/ImageFileMapper.java`
- Test: `java/match-mgr/src/test/java/com/match/registry/service/ImageFileServiceTest.java`
- Create: `java/match-mgr/src/main/java/com/match/registry/service/ImageFileService.java`
- Create: `java/match-mgr/src/main/java/com/match/registry/web/ImageFileController.java`
- Modify: `java/match-mgr/src/main/java/com/match/registry/service/ImageUploadService.java`

**Interfaces:**
- Produces: `ImageFileService.finishUpload(ImageArtifactRecord, ImageUploadRecord, Path)` and `/admin/image-files` list/delete endpoints.

- [ ] Write a failing test proving a completed `.tar` is moved to `files/<repository>/<filename>` and upserted as enabled.
- [ ] Run `mvn -Dtest=ImageFileServiceTest test` and confirm failure because the service is absent.
- [ ] Add `image_file(file_id, image_repository, original_filename, image_tag, archive_path, size_bytes, enabled, created_by, created_at, updated_at)` with a unique repository/filename key.
- [ ] Implement `finishUpload` with path validation, a TAR header/manifest reference check, atomic replace, and mapper upsert.
- [ ] Change upload creation to accept only `.tar`, omit expected SHA handling, and call `finishUpload` after assembly instead of entering review/import.
- [ ] Run the focused Java test and registry tests.

### Task 2: Direct deployment

**Files:**
- Modify: `java/match-mgr/src/main/resources/db/migration/V54__simple_image_files.sql`
- Modify: `java/match-mgr/src/main/java/com/match/registry/persistence/ImageDeploymentRecord.java`
- Modify: `java/match-mgr/src/main/java/com/match/registry/service/ImageDeploymentService.java`
- Modify: `java/match-mgr/src/main/java/com/match/registry/service/AgentImageArchiveService.java`
- Modify: `java/match-mgr/src/main/java/com/match/agent/service/AgentCommandService.java`
- Modify: `java/match-mgr/src/main/java/com/match/registry/web/ImageFileController.java`
- Test: `java/match-mgr/src/test/java/com/match/registry/service/SimpleImageDeploymentTest.java`
- Modify: `../xkp-agent/internal/protocol/command.go`
- Modify: `../xkp-agent/internal/imagedeploy/manager.go`
- Test: `../xkp-agent/internal/imagedeploy/manager_test.go`

**Interfaces:**
- Produces: `POST /admin/image-files/{fileId}/deploy`, payload fields `imageName` and `overwrite`, and archive resolution by `file_id`.

- [ ] Write failing Java and Go tests for direct file deployment and `docker load` failure propagation.
- [ ] Extend legacy `image_deployment` with nullable `file_id` and `target_image`; retain legacy release columns for old rows.
- [ ] Create deployment records directly from enabled `image_file` rows and emit `{deploymentId,imageName,overwrite}`.
- [ ] Resolve archive downloads from `image_file.archive_path`, independent of upload/artifact/release IDs.
- [ ] Make Agent compare `repository:tag`; return `IMAGE_ALREADY_PRESENT` unless overwrite was confirmed, otherwise download and run `docker load`.
- [ ] Map load failures to `镜像文件无效，请更换有效的 Docker 镜像归档：<docker error>`.
- [ ] Run focused Java and Go tests.

### Task 3: Agent Docker inventory

**Files:**
- Modify: `../xkp-agent/internal/collect/collector.go`
- Modify: `../xkp-agent/internal/collect/docker.go`
- Test: `../xkp-agent/internal/collect/docker_test.go`
- Modify: `java/match-mgr/src/main/java/com/match/agent/model/AgentMetricSnapshot.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/model/AgentDockerImageView.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/model/AgentDockerContainerView.java`

**Interfaces:**
- Produces: heartbeat `metrics.images[]` and `metrics.containers[]`, exposed by existing processing-agent list APIs.

- [ ] Write a failing Go test for image/container projection and Java JSON parsing.
- [ ] Collect Docker images and all containers using the existing Docker client.
- [ ] Add typed Java fields so heartbeat inventory survives serialization and appears in `latestMetrics`.
- [ ] Run focused Agent and Java tests.

### Task 4: Simplified interface and deployment

**Files:**
- Modify: `vue/src/services/imageRegistry.js`
- Rewrite: `vue/src/views/operations/ImageRegistryView.vue`
- Modify: `vue/src/components/operations/ImageUploadDialog.vue`
- Modify: `vue/src/components/operations/ImageDeploymentDialog.vue`
- Test: `vue/scripts/test-image-registry-contract.js`
- Modify: `TODO.md`

**Interfaces:**
- Consumes: `/admin/image-files`, direct deploy endpoint, and processing-agent inventories.

- [ ] Update the contract test to require only “镜像文件、推送进度、服务器清单” and reject review/import/publish controls.
- [ ] Run the contract test and confirm it fails against the old page.
- [ ] Remove component/version/SHA controls; handle `FILE_EXISTS` with one overwrite confirmation.
- [ ] Show enabled files with direct push action, deployment progress, and per-server image/container tables.
- [ ] Build Vue and run the contract test.
- [ ] Build Java and Linux Agent, deploy Java/Vue, upgrade the three Agents, then verify heartbeats, inventory, upload, and a direct deployment.
- [ ] Update `TODO.md` with verified results and remaining external limitations.
