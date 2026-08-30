# 课程内容编排与编辑实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现课程简介、大纲、目录、PPT、视频、数据资源的分栏学习，以及管理员对富文本、章节和所有资源的添加、修改、替换和删除。

**Architecture:** `course` 保存经过清洗的简介/大纲 HTML；章节和资源关联继续使用现有表。资源替换复用课程资源库上传接口，先建立新关联，再解除旧关联并尝试删除无共享引用的旧文件。前端复用现有富文本编辑器能力，不新增第三方依赖。

**Tech Stack:** Spring Boot、MyBatis Plus、Flyway、Jsoup、Vue 2、Element UI、FastDFS。

---

### Task 1: 课程富文本字段

**Files:**
- Create: `java/match-mgr/src/main/resources/db/migration/V51__course_rich_content.sql`
- Modify: `java/match-mgr/src/main/java/com/match/course/persistence/CourseRecord.java`
- Modify: `java/match-mgr/src/main/java/com/match/course/model/CourseUpsertRequest.java`
- Modify: `java/match-mgr/src/main/java/com/match/course/service/CourseAuthoringService.java`
- Test: `java/match-mgr/src/test/java/com/match/course/service/CourseAuthoringServiceTest.java`

- [ ] 写失败测试：保存简介/大纲并移除 `<script>` 和事件属性。
- [ ] 运行 `mvn -q -Dtest=CourseAuthoringServiceTest test`，确认失败。
- [ ] 增加两个 LONGTEXT 字段并用 Jsoup Safelist 清洗。
- [ ] 重跑测试，确认通过。

### Task 2: 章节和资源元数据编辑

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/course/model/CourseResourceMetadataRequest.java`
- Modify: `java/match-mgr/src/main/java/com/match/course/service/CourseAuthoringService.java`
- Modify: `java/match-mgr/src/main/java/com/match/course/web/AdminCourseController.java`
- Modify: `java/match-mgr/src/main/java/com/match/resource/persistence/PlatformFileMapper.java`
- Test: `java/match-mgr/src/test/java/com/match/course/service/CourseAuthoringServiceTest.java`

- [ ] 写失败测试：章节可重命名/排序；资源可修改名称、类型、章节、实训工具和排序；含资源章节不能删除。
- [ ] 增加 `PUT /admin/courses/{courseId}/chapters/{chapterId}`。
- [ ] 增加 `PUT /admin/courses/{courseId}/resources/{fileId}/metadata`。
- [ ] 强化章节删除保护并运行测试。

### Task 3: 课程富文本图片接口和编辑器复用

**Files:**
- Modify: `java/match-mgr/src/main/java/com/match/course/web/AdminCourseController.java`
- Modify: `vue/src/components/help/RichHelpEditor.vue`
- Modify: `vue/src/api/Courses.js`

- [ ] 增加管理员课程图片上传接口，限制图片类型和 5 MiB。
- [ ] 让 `RichHelpEditor` 接收可选图片上传函数，默认保持在线帮助现状。
- [ ] 增加课程图片、章节更新和资源元数据 API。

### Task 4: 普通用户课程页签

**Files:**
- Modify: `vue/src/views/user/CoursePlatform.vue`
- Modify: `vue/scripts/test-course-platform-contract.js`

- [ ] 写失败契约，要求页签顺序为简介、大纲、目录、PPT、视频、数据，且无“全部内容”。
- [ ] 实现富文本只读页、课程目录章节电子书页，以及三类直接资源页。
- [ ] 运行契约测试和前端构建。

### Task 5: 管理员课程内容编辑器

**Files:**
- Modify: `vue/src/views/management/CourseManagement.vue`
- Modify: `vue/src/api/ResourceSpaces.js`
- Modify: `vue/scripts/test-course-platform-contract.js`

- [ ] 增加“编辑课程内容”对话框和简介/大纲富文本页。
- [ ] 增加章节表格的新增、编辑、排序和删除操作。
- [ ] 增加资源表格的上传、元数据编辑、替换和删除操作。
- [ ] 替换时先上传新文件并绑定，再解除旧文件；旧文件无引用时调用资源库删除接口。
- [ ] 运行契约测试和前端生产构建。

### Task 6: 部署与回归

**Files:**
- Runtime artifacts only.

- [ ] 构建并部署后端 JAR，确认 Flyway V51 成功。
- [ ] 验证现有 43 门课程、585 个 PDF 仍可见。
- [ ] 使用管理员新增简介/大纲并修改一个测试章节，确认普通用户只读显示。
- [ ] 用临时文件验证资源替换顺序和共享文件保护，完成后清理测试数据。
- [ ] 浏览器验证六个页签、PDF 预览和管理员编辑器无控制台错误。
