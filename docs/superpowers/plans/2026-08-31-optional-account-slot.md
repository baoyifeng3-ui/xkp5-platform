# Optional Account Slot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow theoretical-competition accounts to exist without server slots while preserving slot checks for practical environments.

**Architecture:** Account persistence becomes independent from placement persistence. Creation binds a slot only when one is supplied; environment creation and practical access retain the existing placement guards.

**Tech Stack:** Spring Boot, MyBatis Plus, Vue 2, Element UI, JUnit 4, Node contract tests.

---

### Task 1: Make single-account placement optional

**Files:**
- Modify: `java/match-mgr/src/main/java/com/match/service/impl/AdminUserManagementService.java`
- Test: `java/match-mgr/src/test/java/com/match/service/impl/AdminUserManagementServiceTest.java`

- [ ] Add a failing test that calls `create()` with `slotId=null`, expects a created user, and verifies `accountSlotService.bind` is never called.
- [ ] Run `mvn -o -Dtest=AdminUserManagementServiceTest test`; expect failure `请选择服务器槽位`.
- [ ] Replace the unconditional slot validation/bind with:

```java
if (request.getSlotId() != null && !request.getSlotId().trim().isEmpty()) {
    accountSlotService.bind(user.getUserId(), request.getSlotId(), actorId);
}
```

- [ ] Re-run the test and commit `feat: allow accounts without slots`.

### Task 2: Make batch placement optional

**Files:**
- Modify: `java/match-mgr/src/main/java/com/match/service/impl/AdminUserManagementService.java`
- Test: `java/match-mgr/src/test/java/com/match/service/impl/AdminUserManagementServiceTest.java`

- [ ] Add a failing test with empty `agentIds`, expecting all requested accounts and no slot binding.
- [ ] Run the targeted test; expect `请选择处理服务器`.
- [ ] Set `freeSlots` to an empty list when `agentIds` is empty; only validate capacity and bind when servers were selected.
- [ ] Populate slot fields in `AdminUserView` only for bound rows.
- [ ] Re-run tests and commit `feat: support theory-only batch accounts`.

### Task 3: Remove frontend slot requirements

**Files:**
- Modify: `vue/src/views/management/UserManagement.vue`
- Test: `vue/scripts/test-account-management-contract.js`

- [ ] Add contract assertions that the warning text is `用户名和密码不能为空`, and batch creation does not reject empty `agentIds`.
- [ ] Run `node scripts/test-account-management-contract.js`; expect failure.
- [ ] Remove `!this.form.slotId` from single save validation and the empty-server early return from batch save.
- [ ] Change slot label to `服务器槽位（可选）` and batch server label to `处理服务器（可选）`.
- [ ] Run the contract test and `npm run build`; commit `feat: make account placement optional in UI`.

### Task 4: Verify practical gating and regression

**Files:**
- Test: `java/match-mgr/src/test/java/com/match/account/service/AccountSlotServiceTest.java`
- Test: `java/match-mgr/src/test/java/com/match/environment/service/AccountEnvironmentCreationServiceTest.java`

- [ ] Assert `requireReadyPlacement` still rejects an unbound account with `账号未绑定可用的处理服务器槽位`.
- [ ] Run account, environment, login, submission and frontend account tests.
- [ ] Verify manually: create unbound account, login, answer and submit theory paper; practical endpoint rejects it; bind a slot and environment creation succeeds.

