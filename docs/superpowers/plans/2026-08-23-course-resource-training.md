# Course Resource Training Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build administrator course/resource authoring and ordinary-user learning workflows with independent ebook/video/PPT/archive progress, private archive delivery into training containers, and T100 image/URL validation.

**Architecture:** Add normalized course, resource, progress, and delivery records with MyBatis persistence and service-layer role/ownership enforcement. Reuse existing training environment operations, Agent command persistence, private file storage, and terminal/T100 execution boundaries; extend Vue pages only after backend contracts are stable.

**Tech Stack:** Spring Boot 2.1, MyBatis-Plus, Flyway/MySQL 8, Vue 2, Element UI, existing Agent command protocol, PowerShell/static and Maven Docker tests

---

### Task 1: Course and resource schema

**Files:**
- Create: `java/match-mgr/src/main/resources/db/migration/V28__course_resource_platform.sql`
- Create: `java/match-mgr/src/main/java/com/match/course/persistence/CourseRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/course/persistence/CourseResourceRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/course/persistence/CourseProgressRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/course/persistence/CourseDeliveryRecord.java`
- Create: matching `*Mapper.java` files under `com.match.course.persistence`
- Create: `java/match-mgr/src/test/java/com/match/course/persistence/CourseResourceSchemaTest.java`
- Modify: `java/match-mgr/src/main/java/com/match/Application.java` mapper scan

- [ ] **Step 1: Write failing schema/mapper tests**

Test that V28 defines the four tables, enum/state checks, unique `(user_id, resource_id)` progress key, unique delivery idempotency key, byte/checksum fields, and mapper methods named `selectForUpdate`, `selectVisible`, `upsertProgress`, and `selectDeliveryForUpdate`.

- [ ] **Step 2: Run RED**

Run: `docker run --rm -v "${PWD}/java/match-mgr:/workspace" -w /workspace maven:3.9-eclipse-temurin-17 mvn '-Dtest=CourseResourceSchemaTest' test`

Expected: test compilation fails because V28 and course persistence classes do not exist.

- [ ] **Step 3: Implement V28 and records/mappers**

Use MySQL 8 `DATETIME(3)`, `utf8mb4`, no foreign keys, binary lowercase digest checks, bounded resource types (`EBOOK`, `VIDEO`, `PPT`, `ARCHIVE`), progress kinds (`PAGE`, `TIME`, `SLIDE`, `DELIVERY`), and delivery states (`PENDING`, `DISPATCHED`, `RUNNING`, `SUCCEEDED`, `FAILED`). Add `com.match.course.persistence` to `@MapperScan`.

- [ ] **Step 4: Run GREEN and commit**

Run the focused test and `git diff --check`.

```bash
git add java/match-mgr/src/main java/match-mgr/src/test
git commit -m "feat: add course resource progress schema"
```

### Task 2: Administrator course/resource authoring

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/course/model/*Request.java`
- Create: `java/match-mgr/src/main/java/com/match/course/model/*View.java`
- Create: `java/match-mgr/src/main/java/com/match/course/service/CourseAuthoringService.java`
- Create: `java/match-mgr/src/main/java/com/match/course/web/AdminCourseController.java`
- Create: `java/match-mgr/src/test/java/com/match/course/service/CourseAuthoringServiceTest.java`
- Create: `java/match-mgr/src/test/java/com/match/course/web/AdminCourseControllerTest.java`

- [ ] **Step 1: Write failing service tests**

Cover: ADMIN/SUPER_ADMIN can list/read; only allowed administrator roles can create/update/enable; resource type validation; disabled courses excluded from ordinary queries; archive preview never returns `storageKey` or a download URL; duplicate resource digest is rejected.

- [ ] **Step 2: Run RED**

Run the two focused classes; expect missing service/controller symbols.

- [ ] **Step 3: Implement service and endpoints**

Implement `POST/PUT/GET /admin/courses`, `POST /admin/courses/{courseId}/resources`, `PUT /admin/courses/{courseId}/enabled`, and private streamed preview metadata. Enforce role in the service even if route guards are bypassed. Store files under a server-owned key and return only bounded preview metadata.

- [ ] **Step 4: Run GREEN and commit**

Run focused service/controller tests and commit `feat: author courses and resources`.

### Task 3: Ordinary course platform and progress

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/course/service/CourseLearningService.java`
- Create: `java/match-mgr/src/main/java/com/match/course/web/UserCourseController.java`
- Create: `java/match-mgr/src/test/java/com/match/course/service/CourseLearningServiceTest.java`
- Create: `java/match-mgr/src/test/java/com/match/course/web/UserCourseControllerTest.java`
- Modify: `vue/src/api/Courses.js`
- Modify: `vue/src/views/user/CoursePlatform.vue`
- Modify: `vue/src/views/user/ResourceCenter.vue`
- Create: `vue/scripts/test-course-platform-contract.js`

- [ ] **Step 1: Write failing learning tests and Vue contract**

Assert progress values are clamped, completion requires exactly 100%, each resource type keeps its own row, disabled courses are hidden, USER ownership is enforced, and Vue exposes filters for name/type/learning state without any archive download link.

- [ ] **Step 2: Run RED**

Run focused Maven tests and `node vue/scripts/test-course-platform-contract.js`; expect missing APIs/components.

- [ ] **Step 3: Implement learning API and UI**

Implement `GET /user/courses`, `GET /user/courses/{courseId}`, `GET /user/course-resources/{resourceId}/preview`, and `PUT /user/course-resources/{resourceId}/progress`. Store `currentValue`, `totalValue`, `percent`, `lastPosition`, and state server-side. Build a course list with type/status filters and a resource viewer that sends debounced progress updates; archives show only “发送到实训环境”.

- [ ] **Step 4: Run GREEN and commit**

Run all Vue contracts plus focused Maven classes and commit `feat: add course learning progress`.

### Task 4: Training environment creation and course launch

**Files:**
- Modify: `java/match-mgr/src/main/java/com/match/environment/web/AdminTrainingEnvironmentController.java`
- Modify: `java/match-mgr/src/main/java/com/match/environment/service/TrainingEnvironmentService.java` only where required by the existing create contract
- Create: `java/match-mgr/src/main/java/com/match/course/service/CourseTrainingService.java`
- Create: `java/match-mgr/src/test/java/com/match/course/service/CourseTrainingServiceTest.java`
- Modify: `vue/src/api/TrainingEnvironments.js`
- Modify: `vue/src/views/management/TrainingManagement.vue`
- Modify: `vue/src/views/user/TrainingEnvironment.vue`

- [ ] **Step 1: Write failing lifecycle tests**

Cover administrator create/delete ownership, no implicit USER creation, stopped environment start, ready-environment launch from course page, and stable `TRAINING_ENVIRONMENT_UNAVAILABLE` when no own environment exists.

- [ ] **Step 2: Run RED**

Run the focused service/controller tests and expect missing create/delete/course-launch contracts.

- [ ] **Step 3: Implement bounded lifecycle wiring**

Expose administrator creation/deletion through the existing `TrainingEnvironmentService` safety checks. Add course launch response containing only environment state and safe display data. Keep all operations asynchronous and report pending until Agent confirmation.

- [ ] **Step 4: Run GREEN and commit**

Run focused Java/Vue tests and commit `feat: create and launch training environments`.

### Task 5: Archive delivery and T100 validation

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/course/service/CourseDeliveryService.java`
- Create: `java/match-mgr/src/main/java/com/match/course/web/UserCourseDeliveryController.java`
- Create: `java/match-mgr/src/test/java/com/match/course/service/CourseDeliveryServiceTest.java`
- Create/update byte fixtures under `java/match-mgr/src/test/resources/fixtures/course/`
- Modify: existing environment command factory/dispatcher only through a new explicit course-delivery command type
- Modify: `vue/src/views/user/TrainingEnvironment.vue`
- Create: `vue/src/scripts/test-training-validation-contract.js`

- [ ] **Step 1: Write failing delivery/validation tests**

Cover own-user delivery, administrator broadcast expansion, idempotency by digest/user/environment, no local archive URL, image upload size/type validation, URL allowlist validation, T100 command payload fixture, and terminal result mapping.

- [ ] **Step 2: Run RED**

Run focused Java tests and the Vue contract; expect missing delivery/validation symbols.

- [ ] **Step 3: Implement command and controller boundaries**

Add `POST /user/course-resources/{resourceId}/deliver`, `POST /admin/course-resources/{resourceId}/broadcast`, `POST /user/training-environments/{environmentId}/validate-image`, and `POST /user/training-environments/{environmentId}/validate-url`. Generate destination paths server-side, persist delivery before dispatch, and update only from terminal results. Return stable sanitized states and never expose raw payloads, container names, or internal URLs.

- [ ] **Step 4: Run GREEN and commit**

Run focused Java/Vue tests, fixture digest checks, and commit `feat: deliver course archives and validate models`.

### Task 6: Integrated verification and documentation

**Files:**
- Modify: `deploy/dev/smoke-test.ps1`
- Modify: `deploy/dev/test-smoke-static.ps1`
- Create: `deploy/tests/test-course-platform-contract.ps1`
- Modify: `docs/verification/2026-08-21-platform-ui-refresh.md`

- [ ] **Step 1: Add non-mutating smoke checks**

Check authenticated course list, resource list, progress reads, training environment list, and archive URL absence. Do not call mode mutation, create, delete, upload, approval, broadcast, or delivery endpoints.

- [ ] **Step 2: Run full verification**

Run all Java focused/full tests available in Docker, all Vue contracts/build, PowerShell static contracts, and `git diff --check`.

- [ ] **Step 3: Run physical-server prerequisites only after approval**

Verify Agent registration, TLS, Docker runtime, T100 service, container mounts, and actual archive delivery with a disposable test course. Preserve existing containers and data; use a dedicated test account/environment.

- [ ] **Step 4: Commit verification evidence**

Record exact test counts, endpoints, viewport checks, Docker image IDs, and any environment limitations without storing passwords or tokens.

