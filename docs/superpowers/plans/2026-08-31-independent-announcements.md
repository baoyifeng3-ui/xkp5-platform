# Independent Competition Announcements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store and manage competition announcement rows independently from platform accounts.

**Architecture:** Add one announcement-row table with JSON field values keyed by existing announcement `fieldKey`. Admin endpoints own CRUD; the public homepage reads enabled fields plus independent rows.

**Tech Stack:** Spring Boot, MyBatis Plus, Flyway, Vue 2, Element UI, JUnit 4.

---

### Task 1: Add independent announcement persistence

**Files:**
- Create: `java/match-mgr/src/main/resources/db/migration/V52__competition_announcement_entries.sql`
- Create: `java/match-mgr/src/main/java/com/match/entity/CompetitionAnnouncementEntry.java`
- Create: `java/match-mgr/src/main/java/com/match/mapper/CompetitionAnnouncementEntryMapper.java`
- Test: `java/match-mgr/src/test/java/com/match/config/CompetitionAnnouncementSchemaTest.java`

- [ ] Add a failing schema contract for table `competition_announcement_entry(entry_id, values_json, sort_order, created_at, updated_at)` with no `user_id`.
- [ ] Add the Flyway migration and mapper/entity.
- [ ] Run schema tests and commit `feat: add independent announcement storage`.

### Task 2: Add CRUD service and admin API

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/service/impl/CompetitionAnnouncementService.java`
- Create: `java/match-mgr/src/main/java/com/match/controller/AdminCompetitionAnnouncementController.java`
- Create: `java/match-mgr/src/main/java/com/match/dto/CompetitionAnnouncementRequest.java`
- Test: `java/match-mgr/src/test/java/com/match/service/impl/CompetitionAnnouncementServiceTest.java`

- [ ] Test create/update/delete/list using maps keyed by enabled `fieldKey`; reject unknown fields and values over 255 characters.
- [ ] Implement endpoints `GET/POST /admin/competition-announcements`, `PUT/DELETE /admin/competition-announcements/{id}` guarded by `AdminGuard`.
- [ ] Run tests and commit `feat: manage independent competition announcements`.

### Task 3: Switch public homepage data source

**Files:**
- Modify: `java/match-mgr/src/main/java/com/match/service/impl/TeamsServiceImpl.java`
- Test: `java/match-mgr/src/test/java/com/match/service/impl/TeamsServiceImplTest.java`

- [ ] Add a failing test proving announcement rows come from `CompetitionAnnouncementService`, not `UserMapper`.
- [ ] Keep progress calculation account-based, but set homepage `rows` from independent announcements and `fields` from enabled announcement fields.
- [ ] Run tests and commit `fix: decouple announcements from accounts`.

### Task 4: Add management UI

**Files:**
- Modify: `vue/src/api/Match.js`
- Modify: `vue/src/views/Admin.vue`
- Test: `vue/scripts/test-competition-settings-placement-contract.js`

- [ ] Add API helpers for list/create/update/delete.
- [ ] Replace `participantUsers` in the announcement tab with `announcementEntries`.
- [ ] Add `新增条目`, row `编辑`, and `删除` commands; build form fields from enabled announcement fields.
- [ ] Keep account editor unavailable from this tab and remove account-count copy.
- [ ] Run contract test and `npm run build`; commit `feat: add independent announcement editor`.

### Task 5: End-to-end verification

- [ ] Create an announcement with no user accounts and verify it appears publicly.
- [ ] Create/delete/disable an account and verify announcement rows do not change.
- [ ] Disable and re-enable a field; verify its stored value hides and returns.
- [ ] Run backend targeted tests, frontend contracts, production build, and `git diff --check`.
