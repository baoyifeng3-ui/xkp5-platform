# Background And Online Help Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make login backgrounds reliably display and add role-scoped rich online help.

**Architecture:** Background URLs are normalized to platform-origin `/files/...` paths. Help metadata/content uses MySQL; help images and uploaded documents reuse FastDFS. Backend role checks are authoritative.

**Tech Stack:** Spring Boot, Flyway, FastDFS, Vue 2, Element UI, browser-native contenteditable/preview.

---

### Task 1: Background URL root cause fix

**Files:**
- Modify: `CompetitionController.java`
- Modify: `SystemSettingService.java`
- Modify: `PlatformSettings.vue`
- Test: `SystemSettingServiceTest.java`, frontend platform settings contract.

- [ ] Test uploaded relative `/files/...` URL remains browser-resolvable and invalid schemes/paths are rejected.
- [ ] Normalize stored URL to `/files/...`; do not call server-side HEAD on relative browser URLs.
- [ ] Add image `load/error` validation in settings preview before save.
- [ ] Verify current stored `/files/group1/...jpg` returns 200 through frontend dev proxy and renders on login.

### Task 2: Help persistence and authorization

**Files:**
- Create: `V50__online_help.sql`
- Create package: `com.match.help`
- Test: `HelpDocumentServiceTest.java`, `HelpDocumentControllerTest.java`.

- [ ] Store title, audience (`USER`/`ADMIN`), content type, HTML/storage key, publish state, author, timestamps.
- [ ] Super admin can CRUD/publish/disable.
- [ ] Admin reads both audiences; user reads only published `USER` documents.
- [ ] Reject direct access to disallowed document IDs.

### Task 3: Rich editor and document upload

**Files:**
- Create: `vue/src/views/operations/HelpManagement.vue`
- Create: `vue/src/views/shared/OnlineHelp.vue`
- Create: `vue/src/components/help/RichHelpEditor.vue`
- Create: `vue/src/api/OnlineHelp.js`

- [ ] Implement toolbar with native `contenteditable` commands for headings, size, bold, italic, color, lists, quote, link, table, and image.
- [ ] Upload inserted images and save only returned URLs.
- [ ] Preview PDF in iframe; render HTML; offer DOC/DOCX download.
- [ ] Add first-level help navigation for all roles with role-filtered content.

### Task 4: Page fill behavior

**Files:**
- Modify: `platform-shell.css`, `platform-theme.css`, and bounded page-specific wrappers.

- [ ] Replace unnecessary fixed max widths/heights with `minmax(0,1fr)`, flex/grid growth, and viewport-relative minimum heights.
- [ ] Preserve fixed dimensions for boards/toolbars; check desktop and mobile for overflow.

### Task 5: Real verification

- [ ] Upload/save/reload background and inspect login rendering.
- [ ] Create one user and one admin help document with formatted text/image.
- [ ] Upload PDF and DOCX.
- [ ] Verify super admin edit, admin dual visibility, user user-only visibility, and forbidden direct API access.
- [ ] Run frontend build and backend focused tests.
