# Private Image Registry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a built-in TLS-protected Registry workflow for resumable Docker `save` uploads, approval, immutable image releases, and per-Agent image deployment.

**Architecture:** Keep the Java management service responsible for metadata, authorization, upload sessions, review, releases, and deployment commands. A separate importer worker consumes approved files from persistent staging storage and writes to the Registry without exposing the host Docker socket to Java. Agent deployment is represented by idempotent command contracts and verified through fake-Agent integration scripts; the Agent repository can consume the contract independently.

**Tech Stack:** Spring Boot 2.1, MyBatis-Plus/MySQL/Flyway, Vue 2, Docker Compose Registry v2, Java 8, PowerShell and shell contract tests.

---

### Task 1: Registry deployment foundation

**Files:**
- Create: `compose.registry.yml`
- Modify: `compose.prod.yml`, `compose.offline.yml`, `.env.prod.example`, `.env.local.example`
- Create: `deploy/registry/README.md`, `deploy/registry/tls/README.md`
- Test: `deploy/tests/test-registry-static.sh`

- [ ] **Step 1: Write the failing static deployment tests**

Assert that production and offline Compose files define a Registry service with a persistent data volume, internal TLS certificate mount, no public write port, and separate importer configuration. Assert no license private key or Docker socket is mounted into the Java service.

- [ ] **Step 2: Run the static test to verify it fails**

Run `bash deploy/tests/test-registry-static.sh`.
Expected: FAIL because the Registry service and required environment variables do not exist.

- [ ] **Step 3: Add the Registry Compose service and configuration contract**

Expose Registry only on the configured LAN/internal endpoint, mount persistent
Registry data, mount Registry certificates and keys read-only into the
Registry, and mount only the CA certificate read-only into Java and the import
worker. Add configurable staging/import/size settings. Keep Registry write
credentials in deployment secrets, not source-controlled Compose values.

- [ ] **Step 4: Run the static tests and Compose validation**

Run `bash deploy/tests/test-registry-static.sh` and `docker compose -f compose.prod.yml config` when Docker is available.
Expected: PASS; Compose config has no socket mount on `java` and Registry storage is persistent.

- [ ] **Step 5: Commit**

Run `git add compose.registry.yml compose.prod.yml compose.offline.yml .env*.example deploy/registry deploy/tests/test-registry-static.sh && git commit -m "feat: add private registry deployment foundation"`.

### Task 2: Image catalog, upload sessions, and schema

**Files:**
- Create: `java/match-mgr/src/main/resources/db/migration/V26__image_registry_catalog.sql`
- Create: `java/match-mgr/src/main/java/com/match/registry/model/ImageGroupType.java`
- Create: `java/match-mgr/src/main/java/com/match/registry/model/ImageComponentType.java`
- Create: `java/match-mgr/src/main/java/com/match/registry/persistence/ImageGroupRecord.java`, `ImageArtifactRecord.java`, `ImageUploadRecord.java`, `ImageReleaseRecord.java`, `ImageDeploymentRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/registry/persistence/*Mapper.java`
- Create: `java/match-mgr/src/test/java/com/match/registry/persistence/ImageRegistrySchemaTest.java`

- [ ] **Step 1: Write schema and mapper contract tests**

Cover group types, component types, upload state, review/import state, immutable digest fields, resumable chunk uniqueness, one active deployment per Agent/component, nullable failure metadata, and absence of foreign keys where the existing migrations avoid them.

- [ ] **Step 2: Run the focused test to verify it fails**

Run `mvn -f java/match-mgr/pom.xml '-Dtest=ImageRegistrySchemaTest' test`.
Expected: test compilation fails because V26 and registry persistence classes are missing.

- [ ] **Step 3: Add V26 and MyBatis records/mappers**

Use the existing MySQL 8 migration conventions. Store SHA-256 and Registry digests as fixed lowercase text with unique constraints. Store upload byte counts and chunk indexes as checked non-negative values. Add `SELECT ... FOR UPDATE` methods for upload completion, review transitions, and deployment uniqueness.

- [ ] **Step 4: Run schema tests**

Run the focused Maven command again.
Expected: all schema tests pass.

- [ ] **Step 5: Commit**

Run `git add java/match-mgr/src/main/resources/db/migration/V26__image_registry_catalog.sql java/match-mgr/src/main/java/com/match/registry java/match-mgr/src/test/java/com/match/registry && git commit -m "feat: add image registry catalog schema"`.

### Task 3: Resumable upload and review service

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/registry/service/ImageUploadService.java`
- Create: `java/match-mgr/src/main/java/com/match/registry/web/ImageUploadController.java`
- Create: `java/match-mgr/src/main/java/com/match/registry/web/ImageReviewController.java`
- Create: `java/match-mgr/src/main/java/com/match/registry/dto/ImageUploadCreateRequest.java`, `ImageUploadChunkView.java`, `ImageReviewRequest.java`
- Create: `java/match-mgr/src/test/java/com/match/registry/service/ImageUploadServiceTest.java`
- Create: `java/match-mgr/src/test/java/com/match/registry/web/ImageRegistryControllerTest.java`

- [ ] **Step 1: Write failing service tests**

Test exact SUPER_ADMIN authorization, configurable 20 GiB/100 GiB limits, chunk retry with identical checksum, conflicting duplicate rejection, out-of-order chunks, cancellation, 24-hour expiry, final SHA-256, Docker archive parse failure, and review transition idempotency.

- [ ] **Step 2: Run tests to verify the expected RED state**

Run `mvn -f java/match-mgr/pom.xml '-Dtest=ImageUploadServiceTest,ImageRegistryControllerTest' test`.
Expected: test compilation fails because upload service, DTOs, and controllers are missing.

- [ ] **Step 3: Implement streaming filesystem upload**

Write each chunk to a session-scoped staging directory using an exclusive file name, enforce the configured byte and total staging limits before writing, and never assemble the entire archive in memory. Completion must stream the assembled file through SHA-256 and Docker archive parsing before entering `PENDING_REVIEW`.

- [ ] **Step 4: Implement review endpoints and stable errors**

Expose create/chunk/complete/cancel/status and approve/reject operations under an ADMIN-protected route, with the service itself enforcing SUPER_ADMIN. Return stable reason codes for size, checksum, archive, state, and authorization failures.

- [ ] **Step 5: Run focused tests and commit**

Run the focused Maven command and `git diff --check`.
Expected: all upload/controller tests pass.
Commit with `git add java/match-mgr/src/main/java/com/match/registry java/match-mgr/src/test/java/com/match/registry && git commit -m "feat: support resumable image uploads and review"`.

### Task 4: Asynchronous Registry import worker

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/registry/service/ImageImportWorker.java`
- Create: `java/match-mgr/src/main/java/com/match/registry/service/RegistryImportTool.java`
- Create: `java/match-mgr/src/test/java/com/match/registry/service/ImageImportWorkerTest.java`
- Create: `deploy/registry/import-worker.Dockerfile`
- Create: `scripts/test-image-import-contract.ps1`

- [ ] **Step 1: Write failing import tests**

Cover approved-only input, one import attempt at a time per artifact, retry after a tool failure, digest persistence only after successful import, idempotent reprocessing, and staging-file retention on failure.

- [ ] **Step 2: Run the tests to verify RED**

Run `mvn -f java/match-mgr/pom.xml '-Dtest=ImageImportWorkerTest' test`.
Expected: test compilation fails because the worker and tool boundary are missing.

- [ ] **Step 3: Implement the worker boundary**

Keep tool execution behind `RegistryImportTool` so tests never invoke Docker. The production worker invokes standard Docker archive-to-Registry tooling inside the dedicated importer container, captures only bounded stdout/stderr, validates the returned digest format, and updates the artifact state transactionally.

- [ ] **Step 4: Add the contract script**

The PowerShell script must check state ordering, no Java Docker socket mount, bounded output, retry behavior, and no deletion before digest persistence. It must exit nonzero on any violation.

- [ ] **Step 5: Run tests and commit**

Run focused Maven tests, `powershell -NoProfile -File scripts/test-image-import-contract.ps1`, and `git diff --check`. Commit `feat: import approved images asynchronously`.

### Task 5: Releases, Agent deployment commands, and rollback

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/registry/service/ImageReleaseService.java`
- Create: `java/match-mgr/src/main/java/com/match/registry/service/ImageDeploymentService.java`
- Create: `java/match-mgr/src/main/java/com/match/registry/web/ImageReleaseController.java`
- Create: `java/match-mgr/src/test/java/com/match/registry/service/ImageDeploymentServiceTest.java`
- Modify: `java/match-mgr/src/main/java/com/match/agent/service/AgentCommandService.java`
- Create: `scripts/test-image-deployment-contract.ps1`

- [ ] **Step 1: Write failing command and deployment tests**

Cover exact digest-only payloads, independent annotation/editor releases, `UPDATE_CONTAINERS` default, `IMAGE_ONLY`, one active deployment per Agent/component, idempotency, progress/reconnect, failed pull/rebuild preservation, health-gated old-image cleanup, and rollback to previous digest.

- [ ] **Step 2: Run tests to verify RED**

Run `mvn -f java/match-mgr/pom.xml '-Dtest=ImageDeploymentServiceTest,AgentCommandServiceTest' test`.
Expected: new deployment tests fail because the release/deployment service and command types do not exist.

- [ ] **Step 3: Implement immutable release and deployment state machines**

Create commands only with Registry digests, target Agent, component, policy, and idempotency key. Persist desired state before dispatch; accept Agent progress/result through the existing command result path; clean old local images only after a successful health-gated result. Preserve the last working digest for rollback.

- [ ] **Step 4: Add release/deployment controllers and authorization**

Expose SUPER_ADMIN write operations and ADMIN read-only status. Clamp list limits, hide credentials and raw command payloads, and require explicit confirmation for `UPDATE_CONTAINERS` and rollback.

- [ ] **Step 5: Run focused tests and contract script**

Run focused Maven tests, `powershell -NoProfile -File scripts/test-image-deployment-contract.ps1`, and commit `feat: deploy approved images to agents`.

### Task 6: Management UI and deployment documentation

**Files:**
- Create: `vue/src/services/imageRegistry.js`
- Create: `vue/src/views/operations/ImageRegistryView.vue`
- Create: `vue/src/components/operations/ImageUploadDialog.vue`
- Create: `vue/src/components/operations/ImageDeploymentDialog.vue`
- Modify: `vue/src/router/index.js`, operations navigation components
- Create: `vue/scripts/test-image-registry-contract.js`
- Modify: `docs/agents/administrator-guide.md`, `docs/agents/operator-guide.md`, `README.md`

- [ ] **Step 1: Write failing UI contract tests**

Assert route authorization, resumable upload status, review actions, digest visibility, independent component release, target-Agent selection, default container-update warning, image-only option, progress/error/rollback states, and no credential rendering.

- [ ] **Step 2: Run the contract test to verify RED**

Run `node vue/scripts/test-image-registry-contract.js`.
Expected: FAIL because the Registry service, route, and views do not exist.

- [ ] **Step 3: Implement the management UI**

Keep the page operations-focused: searchable image groups/artifacts, upload/review actions, release history, per-Agent deployment table, and explicit update-policy confirmation. Use the existing icon and table conventions; do not expose participant routes.

- [ ] **Step 4: Update operational documentation**

Document internal CA setup, staging capacity variables, upload/review flow, per-Agent rollout, image-only behavior, rollback, and manual Registry history cleanup. Do not claim a real end-to-end rollout without a Registry, fake Agent, and dedicated test account.

- [ ] **Step 5: Run UI verification and commit**

Run `node vue/scripts/test-image-registry-contract.js`, the existing Vue contract suite, and `npm run build`. Commit `feat: add image registry management UI`.

### Task 7: Integrated verification

**Files:**
- Modify: `scripts/test-image-registry-flow.ps1`
- Create: `scripts/test-image-registry-flow-contract.ps1`
- Modify: `deploy/tests/test-static.sh`

- [ ] **Step 1: Add a fake Registry/importer/Agent flow contract**

Verify upload resume, review, import digest, release creation, per-Agent `PULLED`/health result, default container update, image-only deployment, failure preservation, and rollback without embedding credentials.

- [ ] **Step 2: Run all available verification**

Run the Java registry suite, the existing Java suite excluding the known Windows-only `ScoreServiceImplTest`, the Vue contract/build suite, deployment static tests, and both Registry scripts. Use the real flow script only when a disposable MySQL, TLS Registry, importer, fake Agent, and dedicated accounts are available.

- [ ] **Step 3: Review scope and commit acceptance**

Run `git diff --check`, scan diffs for credentials and Docker socket mounts, record unavailable integration prerequisites, and commit `test: verify private image registry flow`.
