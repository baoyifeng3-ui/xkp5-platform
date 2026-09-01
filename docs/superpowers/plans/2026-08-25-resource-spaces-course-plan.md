# Resource Spaces and Course Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement course, public, exchange, and private homework file spaces with correct upload, directory, ownership, course association, download, container delivery, deletion, and administrative cleanup behavior.

**Architecture:** Introduce stable `platform_file` and `resource_directory` entities, with a many-to-many `course_resource_link`. Preserve existing course resource IDs during migration so learning progress and delivery records remain valid, and centralize authorization in a space-aware service used by separate administrator and participant controllers.

**Tech Stack:** Spring Boot 2.1, MyBatis, MySQL/Flyway, FastDFS, Vue 2, Element UI.

---

### Task 1: Add a Backward-Compatible Resource Space Schema

**Files:**
- Create: `java/match-mgr/src/main/resources/db/migration/V30__resource_spaces.sql`
- Create: `java/match-mgr/src/test/java/com/match/resource/persistence/ResourceSpaceSchemaTest.java`

- [ ] **Step 1: Write a failing migration contract test**

Assert V30 creates `resource_directory`, `platform_file`, `course_resource_link`, and `resource_cleanup_audit`; includes all four space values; and migrates legacy `course_resource.resource_id` into the stable file ID.

```java
assertTrue(sql.contains("create table platform_file"));
assertTrue(sql.contains("'course','public','exchange','homework'"));
assertTrue(sql.contains("insert into platform_file"));
assertTrue(sql.contains("insert into course_resource_link"));
```

- [ ] **Step 2: Create directory and file tables**

`resource_directory` has `directory_id`, `space_type`, nullable `owner_user_id`, nullable `parent_id`, name, creator, and timestamps. Add a unique key on `(space_type, owner_user_id, parent_id, name)`.

`platform_file` has stable `file_id`, `space_type`, owner, directory, name, storage key, size, MIME type, lowercase SHA-256, uploader, and timestamps.

- [ ] **Step 3: Create course links and cleanup audit**

`course_resource_link` uses `(course_id, file_id)` uniqueness and stores resource type, sort order, enabled state, and timestamps. `resource_cleanup_audit` stores only space, aggregate count/bytes, actor, and time.

- [ ] **Step 4: Migrate legacy course resources**

Insert one `COURSE` file per old `course_resource` row using the same `resource_id`, then insert its course link. Do not change `course_resource_progress.resource_id` or `course_resource_delivery.resource_id`.

- [ ] **Step 5: Run the migration test**

```powershell
docker run --rm -v "${PWD}:/workspace" -v xkp5-m2-cache:/root/.m2 -w /workspace xkp5-full-java-tests mvn -q -Dtest=ResourceSpaceSchemaTest test
```

### Task 2: Implement Space-Aware Persistence and Authorization

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/resource/persistence/ResourceDirectoryRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/resource/persistence/PlatformFileRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/resource/persistence/CourseResourceLinkRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/resource/persistence/ResourceCleanupAuditRecord.java`
- Create corresponding MyBatis mapper interfaces under the same package
- Create: `java/match-mgr/src/main/java/com/match/resource/service/ResourceAccessPolicy.java`
- Test: `java/match-mgr/src/test/java/com/match/resource/service/ResourceAccessPolicyTest.java`

- [ ] **Step 1: Write role matrix tests**

Cover administrators, two normal users, every space, read/upload/delete, and homework summary. Key assertions:

```java
assertFalse(policy.canList(HOMEWORK, admin, ownerId));
assertTrue(policy.canDelete(EXCHANGE, admin, otherUserId));
assertFalse(policy.canDelete(EXCHANGE, userOne, userTwo.getUserId()));
assertTrue(policy.canList(HOMEWORK, userOne, userOne.getUserId()));
```

- [ ] **Step 2: Implement a fail-closed permission policy**

Rules must match the approved specification. Homework administrator access exposes only aggregate cleanup methods, never file/directory methods.

- [ ] **Step 3: Add owner-scoped mapper queries**

Homework queries require `owner_user_id = #{currentUserId}` in SQL, not post-query Java filtering. Exchange user deletion requires `uploaded_by = #{currentUserId}`.

- [ ] **Step 4: Run policy tests**

Expected: all role/space matrix tests pass.

### Task 3: Implement Directory, Upload, Download, and Delete Services

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/resource/service/ResourceSpaceService.java`
- Create: `java/match-mgr/src/main/java/com/match/resource/service/ResourceStorageService.java`
- Modify: `java/match-mgr/src/main/java/com/match/util/dfs/FastDFSClient.java`
- Test: `java/match-mgr/src/test/java/com/match/resource/service/ResourceSpaceServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Test nested directory creation, duplicate names, upload metadata, orphan cleanup, non-empty directory rejection, referenced course file rejection, exchange ownership, and homework isolation.

- [ ] **Step 2: Wrap FastDFS operations behind an injectable service**

`ResourceStorageService` exposes `store(MultipartFile)`, `download(storageKey)`, `accessUrl(storageKey)`, and `delete(storageKey)`. This makes tests deterministic and prevents direct static calls throughout controllers.

- [ ] **Step 3: Implement directory operations**

Validate that parent and child are in the same space and owner scope. Reject empty, slash-containing, dot, and dot-dot names. Reject deletion of non-empty directories.

- [ ] **Step 4: Implement upload atomically**

Stream SHA-256 while receiving the upload, persist size and MIME type, and delete the stored object if metadata persistence fails. `COURSE` upload requires at least one valid course ID.

- [ ] **Step 5: Implement download and delete**

Return an attachment response only after permission checks. Delete the FastDFS object only after verifying no course links; if storage deletion fails, keep metadata and report a storage error.

- [ ] **Step 6: Run service tests**

Expected: upload, directory, permission, and cleanup tests pass.

### Task 4: Implement Administrator and Participant APIs

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/resource/web/AdminResourceSpaceController.java`
- Create: `java/match-mgr/src/main/java/com/match/resource/web/UserResourceSpaceController.java`
- Create request/view classes under `java/match-mgr/src/main/java/com/match/resource/model/`
- Test: `java/match-mgr/src/test/java/com/match/resource/web/ResourceSpaceControllerTest.java`

- [ ] **Step 1: Define administrator endpoints**

```text
GET    /admin/resource-spaces/{space}/entries?parentId=
POST   /admin/resource-spaces/{space}/directories
POST   /admin/resource-spaces/{space}/files
GET    /admin/resource-spaces/{space}/files/{fileId}/download
DELETE /admin/resource-spaces/{space}/files/{fileId}
DELETE /admin/resource-spaces/{space}/directories/{directoryId}
GET    /admin/resource-spaces/homework/summary
POST   /admin/resource-spaces/{space}/clear
```

- [ ] **Step 2: Define participant endpoints**

```text
GET    /user/resource-spaces/{space}/entries?parentId=
POST   /user/resource-spaces/{space}/directories
POST   /user/resource-spaces/{space}/files
GET    /user/resource-spaces/{space}/files/{fileId}/download
DELETE /user/resource-spaces/{space}/files/{fileId}
DELETE /user/resource-spaces/{space}/directories/{directoryId}
POST   /user/resource-spaces/public/files/{fileId}/deliver?environmentId=
```

Allow only `PUBLIC`, `EXCHANGE`, and `HOMEWORK` on participant routes; reject `COURSE` before service invocation.

- [ ] **Step 3: Add explicit HTTP error mapping**

Map not found to 404, permission denial to 403, course reference or non-empty directory conflict to 409, and storage outage to 503 with concise Chinese messages.

- [ ] **Step 4: Run controller tests**

Use administrator and two user fixtures to prove no cross-user homework access and no deletion of another user's exchange file.

### Task 5: Switch Course Services to Stable Files and Many-to-Many Links

**Files:**
- Modify: `java/match-mgr/src/main/java/com/match/course/service/CourseAuthoringService.java`
- Modify: `java/match-mgr/src/main/java/com/match/course/service/CourseLearningService.java`
- Modify: `java/match-mgr/src/main/java/com/match/course/service/CourseDeliveryService.java`
- Modify: `java/match-mgr/src/main/java/com/match/course/web/AdminCourseController.java`
- Create: `java/match-mgr/src/test/java/com/match/course/service/CourseResourceLinkServiceTest.java`

- [ ] **Step 1: Write failing course link tests**

Assert one course file links to two courses without copying `storage_key`, duplicate linking is rejected, unlink preserves the file, visible courses use enabled links, and delivery resolves the linked file.

- [ ] **Step 2: Replace direct `course_resource` reads**

Course list and learning queries join `course_resource_link` to `platform_file`. Keep resource IDs stable in `ResourceView`, progress, preview, and delivery APIs.

- [ ] **Step 3: Add link and unlink endpoints**

```text
POST   /admin/courses/{courseId}/resources/{fileId}
DELETE /admin/courses/{courseId}/resources/{fileId}
GET    /admin/course-resource-library/files
```

- [ ] **Step 4: Preserve participant rules**

Only enabled courses and enabled links appear. `ARCHIVE` remains non-previewable and deliverable to the user's own environment. Course resources never gain a participant download endpoint.

- [ ] **Step 5: Run course tests**

Run focused authoring, learning, progress, and delivery suites plus the new link suite.

### Task 6: Build a Shared Resource Browser UI

**Files:**
- Create: `vue/src/components/resources/ResourceBrowser.vue`
- Create: `vue/src/components/resources/ResourceUploadDialog.vue`
- Create: `vue/src/api/ResourceSpaces.js`
- Modify: `vue/src/views/management/ResourceManagement.vue`
- Modify: `vue/src/views/user/ResourceCenter.vue`
- Test: `vue/scripts/test-resource-spaces-contract.js`

- [ ] **Step 1: Write failing UI contracts**

Assert the management view has tabs for 课程资源库, 公共资源库, 交换空间, and 作业空间汇总; participant view has 公共资源库, 交换空间, and 作业空间 but not 课程资源库; and upload progress, directory creation, download, delete, and clear controls exist.

- [ ] **Step 2: Implement reusable directory browsing**

`ResourceBrowser` renders breadcrumbs, directories before files, loading/empty/error states, and permission-driven commands. Stable grid/list dimensions prevent layout shifts.

- [ ] **Step 3: Implement upload progress**

Use Axios `onUploadProgress`; show selected file, bytes, percent, target directory, and error message. Prevent duplicate submission while active.

- [ ] **Step 4: Implement administrator spaces**

Course resources: upload with one or more course selections, download, unlink context, and protected delete. Public and exchange: directory/file operations. Homework: only aggregate count/bytes and clear button, never entries.

- [ ] **Step 5: Implement participant spaces**

Public: browse/download/deliver. Exchange: browse/upload/download and delete own. Homework: own directories and files only.

- [ ] **Step 6: Implement clear confirmation**

Administrator chooses exchange or homework in a dialog, sees aggregate count/bytes, and confirms once more before calling the clear endpoint.

### Task 7: Repair Course Management Resource Workflows

**Files:**
- Modify: `vue/src/views/management/CourseManagement.vue`
- Modify: `vue/src/api/Courses.js`
- Test: `vue/scripts/test-course-platform-contract.js`

- [ ] **Step 1: Replace “添加资源” with “添加内容”**

Dialog tabs:

```text
上传新文件 | 从课程资源库选择
```

Uploading includes resource type and auto-links the saved file to the selected course. Existing selection supports multi-select and links all selected files.

- [ ] **Step 2: Show linked resources and unlink action**

Each course card lists resource type/name and provides an unlink icon with confirmation. Unlink does not delete the library file.

- [ ] **Step 3: Fix cover preview**

Keep cover upload separate, store its stable access key, and resolve it to a usable `/files/` URL instead of placing a raw storage key directly in `<img src>`.

- [ ] **Step 4: Run Vue contracts and production build**

```powershell
cd vue
node scripts/test-course-platform-contract.js
node scripts/test-resource-spaces-contract.js
npm run build
```

### Task 8: Integration, Privacy, and Destructive-Operation Verification

**Files:**
- Modify if necessary: `docs/PROJECT_HANDOVER.md`

- [ ] **Step 1: Run Java resource and course suites**

Run schema, policy, service, controller, authoring, learning, progress, and delivery tests in the cached Maven container.

- [ ] **Step 2: Run API integration scenarios**

Use administrator and two normal users. Verify public download, exchange ownership, homework cross-user 403, administrator homework entry 403, homework summary success, and aggregate clear success.

- [ ] **Step 3: Verify physical cleanup**

Upload test files, unlink a course resource, verify file remains, delete the last unreferenced file, and verify FastDFS no longer serves it. Clear exchange and homework and verify audit totals without homework detail leakage.

- [ ] **Step 4: Verify browser layouts**

At desktop and mobile widths, check resource tabs, breadcrumbs, file names, action menus, upload progress, course selection, and clear dialogs for overflow or overlap.

- [ ] **Step 5: Deploy and smoke test**

Apply V30 through Flyway, deploy the tested backend/frontend, ensure `http://localhost:19244` and login APIs return 200, and retain the page for user review.
