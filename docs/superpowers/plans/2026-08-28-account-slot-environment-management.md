# Account Slot and Environment Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the user account's processing-server slot the single source of placement, create environments by selecting accounts only, add reliable bulk account administration, environment migration, competition passwords, and large configurable per-server port pools.

**Architecture:** Extend the existing `user`, `processing_environment_slot`, and `training_environment` model instead of introducing a parallel account system. Keep account creation, slot binding, port allocation, migration state, and competition password switching behind focused transactional services; expose narrow admin APIs and update the two existing management pages to consume them.

**Tech Stack:** Java 8, Spring Boot 2, MyBatis-Plus, Flyway, MySQL 8, Vue 2, Element UI, JUnit 4, Mockito, Node contract tests.

---

## Requirement Coverage

- 账号、自定义字段、备注、单个与批量启停删除：Tasks 2, 3, and 8.
- 删除保护（存在环境时禁止删除账号）：Tasks 3 and 11.
- 账号唯一服务器槽位绑定与环境只选账号：Tasks 2, 3, 5, and 9.
- 默认、课程和比赛环境的独立端口池：Tasks 1, 4, and 10.
- 更换服务器槽位、可选数据迁移与环境重建：Task 6.
- 六位首次登录密码与八位比赛临时密码：Tasks 3 and 7.
- 比赛密码查看、重置、重新生成、导出和退出恢复：Tasks 7 and 8.

## File Map

- `java/match-mgr/src/main/resources/db/migration/V41__account_slot_environment_management.sql`: account remarks, environment-owned ports, migration jobs, port-pool configuration, and competition credentials.
- `java/match-mgr/src/main/java/com/match/account/service/AccountSlotService.java`: transactional slot bind/unbind and eligible-account query.
- `java/match-mgr/src/main/java/com/match/account/service/AccountBatchService.java`: generated usernames, six-character passwords, and bulk state/delete operations.
- `java/match-mgr/src/main/java/com/match/environment/service/EnvironmentPortPoolService.java`: per-server atomic port allocation and release.
- `java/match-mgr/src/main/java/com/match/environment/service/AccountEnvironmentMigrationService.java`: preflight, optional data transfer, cleanup, rebinding, and rebuild orchestration.
- `java/match-mgr/src/main/java/com/match/mode/service/CompetitionCredentialService.java`: eight-character competition passwords and original-password restoration.
- `vue/src/views/management/UserManagement.vue`: full account administration surface.
- `vue/src/views/management/TrainingManagement.vue`: eligible-account-only environment creation.
- `vue/src/api/AccountManagement.js`: focused account, binding, migration, and competition password APIs.
- `vue/src/api/TrainingEnvironments.js`: eligible account and account-only create requests.

### Task 1: Add Database Invariants and Persistence Records

**Files:**
- Create: `java/match-mgr/src/main/resources/db/migration/V41__account_slot_environment_management.sql`
- Create: `java/match-mgr/src/main/java/com/match/account/persistence/AccountEnvironmentMigrationRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/account/persistence/AccountEnvironmentMigrationMapper.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/persistence/CompetitionCredentialRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/persistence/CompetitionCredentialMapper.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/persistence/EnvironmentPortPoolRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/persistence/EnvironmentPortPoolMapper.java`
- Modify: `java/match-mgr/src/main/java/com/match/entity/User.java`
- Modify: `java/match-mgr/src/main/java/com/match/environment/persistence/EnvironmentPortAllocationRecord.java`

- [ ] **Step 1: Write the migration contract test**

Add `java/match-mgr/src/test/java/com/match/config/AccountSlotMigrationContractTest.java`:

```java
@Test public void migrationDefinesRequiredAccountPlacementTables() throws Exception {
    String sql = new String(Files.readAllBytes(Paths.get(
            "src/main/resources/db/migration/V41__account_slot_environment_management.sql")), "UTF-8");
    assertTrue(sql.contains("ADD COLUMN remark VARCHAR(500)"));
    assertTrue(sql.contains("environment_id VARCHAR(36)"));
    assertTrue(sql.contains("CREATE TABLE account_environment_migration"));
    assertTrue(sql.contains("CREATE TABLE competition_credential"));
    assertTrue(sql.contains("CREATE TABLE environment_port_pool"));
    assertTrue(sql.contains("UNIQUE KEY uk_environment_port_agent_host"));
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
mvn -Dtest=AccountSlotMigrationContractTest test
```

Expected: FAIL because `V41__account_slot_environment_management.sql` does not exist.

- [ ] **Step 3: Add the schema migration**

Use these concrete columns and constraints:

```sql
ALTER TABLE user ADD COLUMN remark VARCHAR(500) NULL AFTER password;
ALTER TABLE environment_port_allocation
    ADD COLUMN environment_id VARCHAR(36) NULL AFTER allocation_id,
    ADD KEY idx_environment_port_environment (environment_id),
    ADD UNIQUE KEY uk_environment_port_agent_host (agent_id, host_port, protocol);

CREATE TABLE environment_port_pool (
    pool_id VARCHAR(36) PRIMARY KEY,
    agent_id VARCHAR(36) NOT NULL,
    environment_type VARCHAR(16) NOT NULL,
    service_type VARCHAR(24) NOT NULL,
    range_start INT UNSIGNED NOT NULL,
    range_end INT UNSIGNED NOT NULL,
    updated_by INT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_port_pool_agent_kind (agent_id, environment_type, service_type)
);

CREATE TABLE account_environment_migration (
    migration_id VARCHAR(36) PRIMARY KEY,
    user_id INT NOT NULL,
    source_agent_id VARCHAR(36) NOT NULL,
    source_slot_id VARCHAR(36) NOT NULL,
    target_agent_id VARCHAR(36) NOT NULL,
    target_slot_id VARCHAR(36) NOT NULL,
    migrate_data TINYINT(1) NOT NULL DEFAULT 0,
    state VARCHAR(32) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(512) NULL,
    requested_by INT NOT NULL,
    requested_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    KEY idx_account_migration_user_state (user_id, state)
);

CREATE TABLE competition_credential (
    credential_id VARCHAR(36) PRIMARY KEY,
    user_id INT NOT NULL,
    mode_generation BIGINT NOT NULL,
    original_password_value VARCHAR(255) NOT NULL,
    competition_password_value VARCHAR(255) NOT NULL,
    encrypted_plain_password TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_competition_credential_generation_user (mode_generation, user_id)
);
```

Seed eight default pools per enabled agent with the approved ranges `20000-23999`. Map fields with `@TableName`/`@TableId`, add `remark` to `User`, and add `environmentId` to `EnvironmentPortAllocationRecord`.

- [ ] **Step 4: Run migration contract and backend package**

Run:

```powershell
mvn -Dtest=AccountSlotMigrationContractTest test
mvn -Dmaven.test.skip=true package
```

Expected: PASS and package success.

- [ ] **Step 5: Commit**

```bash
git add java/match-mgr/src/main/resources/db/migration/V41__account_slot_environment_management.sql java/match-mgr/src/main/java/com/match/account/persistence java/match-mgr/src/main/java/com/match/mode/persistence/CompetitionCredential* java/match-mgr/src/main/java/com/match/environment/persistence/EnvironmentPortPool* java/match-mgr/src/main/java/com/match/entity/User.java java/match-mgr/src/main/java/com/match/environment/persistence/EnvironmentPortAllocationRecord.java java/match-mgr/src/test/java/com/match/config/AccountSlotMigrationContractTest.java
git commit -m "feat: add account placement persistence"
```

### Task 2: Implement Transactional Account-to-Slot Binding

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/account/model/EligibleAccountView.java`
- Create: `java/match-mgr/src/main/java/com/match/account/service/AccountSlotService.java`
- Modify: `java/match-mgr/src/main/java/com/match/environment/persistence/ProcessingEnvironmentSlotMapper.java`
- Test: `java/match-mgr/src/test/java/com/match/account/service/AccountSlotServiceTest.java`

- [ ] **Step 1: Write failing slot binding tests**

Cover successful bind, occupied slot rejection, second binding rejection, and eligible filtering:

```java
@Test public void bindRejectsSlotOwnedByAnotherAccount() {
    ProcessingEnvironmentSlotRecord slot = slot("slot-1", "agent-1", 1, 7);
    when(slots.selectForUpdate("slot-1")).thenReturn(slot);
    expectFailure(() -> service.bind(8, "slot-1", 1), "槽位已绑定其他账号");
}

@Test public void eligibleAccountsRequireEnabledOnlineBoundSlot() {
    when(slots.selectEligibleAccounts()).thenReturn(Arrays.asList(
            eligible(3, true, true), eligible(4, true, false)));
    assertEquals(Collections.singletonList(3), service.eligible().stream()
            .map(EligibleAccountView::getUserId).collect(Collectors.toList()));
}
```

- [ ] **Step 2: Run RED**

Run `mvn -Dtest=AccountSlotServiceTest test`.

Expected: compilation failure because `AccountSlotService` and `selectEligibleAccounts` do not exist.

- [ ] **Step 3: Implement minimal binding service**

Add mapper queries that lock by `slot_id`, lock all slots for a user, and return only enabled users bound to enabled online agents. Implement:

```java
@Transactional
public ProcessingEnvironmentSlotRecord bind(int userId, String slotId, int actorId) {
    ProcessingEnvironmentSlotRecord target = slots.selectForUpdate(slotId);
    if (target == null) throw new IllegalArgumentException("服务器槽位不存在");
    if (target.getUserId() != null && target.getUserId() != userId)
        throw new IllegalArgumentException("槽位已绑定其他账号");
    List<ProcessingEnvironmentSlotRecord> current = slots.selectByUserForUpdate(userId);
    if (!current.isEmpty() && !slotId.equals(current.get(0).getSlotId()))
        throw new IllegalArgumentException("账号已绑定其他服务器槽位");
    if (target.getUserId() == null && slots.bindIfUnbound(slotId, userId, LocalDateTime.now()) != 1)
        throw new IllegalArgumentException("槽位绑定状态已变化");
    return slots.selectForUpdate(slotId);
}
```

- [ ] **Step 4: Run GREEN**

Run `mvn -Dtest=AccountSlotServiceTest test`.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add java/match-mgr/src/main/java/com/match/account java/match-mgr/src/main/java/com/match/environment/persistence/ProcessingEnvironmentSlotMapper.java java/match-mgr/src/test/java/com/match/account/service/AccountSlotServiceTest.java
git commit -m "feat: bind accounts to processing slots"
```

### Task 3: Extend Single and Batch Account Management

**Files:**
- Modify: `java/match-mgr/src/main/java/com/match/dto/AdminUserRequest.java`
- Modify: `java/match-mgr/src/main/java/com/match/dto/AdminUserBatchRequest.java`
- Modify: `java/match-mgr/src/main/java/com/match/dto/AdminUserView.java`
- Modify: `java/match-mgr/src/main/java/com/match/service/impl/AdminUserManagementService.java`
- Modify: `java/match-mgr/src/main/java/com/match/controller/AdminUserController.java`
- Test: `java/match-mgr/src/test/java/com/match/service/impl/AdminUserManagementServiceTest.java`

- [ ] **Step 1: Add failing account tests**

Add tests asserting single create requires slot, batch allocates selected servers in order, generated passwords are six characters, `mustChangePassword=true`, and deletion is blocked when environments exist:

```java
@Test public void batchCreatesSixCharacterFirstLoginPasswordsAndBindsSlots() {
    when(slots.lockFreeSlots(Arrays.asList("agent-1"))).thenReturn(Arrays.asList(slot("s1"), slot("s2")));
    List<AdminUserView> result = service.createBatch(batch(2, Arrays.asList("agent-1")));
    assertEquals(2, result.size());
    assertTrue(result.stream().allMatch(v -> v.getInitialPassword().length() == 6));
    assertTrue(result.stream().allMatch(AdminUserView::getMustChangePassword));
    verify(accountSlots, times(2)).bind(anyInt(), anyString(), anyInt());
}
```

- [ ] **Step 2: Run RED**

Run `mvn -Dtest=AdminUserManagementServiceTest test`.

Expected: FAIL because request/view binding fields and bulk operations are missing.

- [ ] **Step 3: Extend DTOs and service**

Add request fields `remark`, `slotId`, `customFields`; batch fields `count`, `agentIds`, `remark`, `customFields`; view fields `remark`, `agentId`, `agentName`, `primaryIp`, `slotId`, `slotNumber`, `environmentCount`, `placementReady`, `initialPassword`.

Change batch generation to letters/digits excluding ambiguous characters:

```java
private static final char[] INITIAL_PASSWORD =
        "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

String generateInitialPassword() {
    StringBuilder value = new StringBuilder(6);
    while (value.length() < 6) value.append(INITIAL_PASSWORD[secureRandom.nextInt(INITIAL_PASSWORD.length)]);
    return value.toString();
}
```

Set `mustChangePassword=true` for batch users, bind slots transactionally, add `DELETE /admin/users/{userId}`, `POST /admin/users/bulk-enable`, `POST /admin/users/bulk-disable`, and `POST /admin/users/bulk-delete`. Reject deletion if any `training_environment` row exists.

- [ ] **Step 4: Run GREEN and existing user tests**

Run:

```powershell
mvn -Dtest=AdminUserManagementServiceTest,ParticipantLoginGateTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add java/match-mgr/src/main/java/com/match/dto/AdminUser* java/match-mgr/src/main/java/com/match/service/impl/AdminUserManagementService.java java/match-mgr/src/main/java/com/match/controller/AdminUserController.java java/match-mgr/src/test/java/com/match/service/impl/AdminUserManagementServiceTest.java
git commit -m "feat: add bound account batch management"
```

### Task 4: Add Configurable Environment-Owned Port Pools

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/environment/service/EnvironmentPortPoolService.java`
- Create: `java/match-mgr/src/main/java/com/match/environment/web/EnvironmentPortPoolController.java`
- Modify: `java/match-mgr/src/main/java/com/match/environment/persistence/EnvironmentPortAllocationMapper.java`
- Modify: `java/match-mgr/src/main/java/com/match/environment/service/TrainingEnvironmentService.java`
- Test: `java/match-mgr/src/test/java/com/match/environment/service/EnvironmentPortPoolServiceTest.java`

- [ ] **Step 1: Write failing allocation tests**

```java
@Test public void allocationUsesFirstFreePortAndKeepsItUntilEnvironmentDeletion() {
    when(pools.lockPool("agent-1", "COURSE", "ANNOTATION")).thenReturn(pool(20000, 20499));
    when(allocations.selectUsedPortsForUpdate("agent-1", 20000, 20499)).thenReturn(new HashSet<>(Arrays.asList(20000)));
    assertEquals(20001, service.allocate("env-1", "agent-1", "COURSE", "ANNOTATION", 8080, "tcp").getHostPort());
}

@Test public void exhaustedPoolReturnsSpecificError() {
    expectFailure(() -> service.allocate("env-1", "agent-1", "COURSE", "T100", 5000, "tcp"),
            "T100 端口池已用尽");
}
```

- [ ] **Step 2: Run RED**

Run `mvn -Dtest=EnvironmentPortPoolServiceTest test`.

Expected: FAIL because the pool service does not exist.

- [ ] **Step 3: Implement atomic allocation and release**

Implement `allocate(environmentId, agentId, environmentType, serviceType, containerPort, protocol)` by locking the pool row, locking used ports, choosing the first gap, and inserting an allocation with `environment_id`. Implement `releaseEnvironment(environmentId)` only from environment deletion. Add super-admin endpoints to list/update per-agent ranges and reject overlapping ranges.

Replace slot-derived port calculations in `TrainingEnvironmentService` with one annotation allocation or three editor allocations.

- [ ] **Step 4: Run GREEN**

Run `mvn -Dtest=EnvironmentPortPoolServiceTest,TrainingEnvironmentServiceTest test`.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add java/match-mgr/src/main/java/com/match/environment java/match-mgr/src/test/java/com/match/environment/service/EnvironmentPortPoolServiceTest.java java/match-mgr/src/test/java/com/match/environment/service/TrainingEnvironmentServiceTest.java
git commit -m "feat: allocate environment ports from pools"
```

### Task 5: Create Environments from Accounts Only

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/environment/model/CreateAccountsEnvironmentRequest.java`
- Modify: `java/match-mgr/src/main/java/com/match/environment/web/AdminTrainingEnvironmentController.java`
- Modify: `java/match-mgr/src/main/java/com/match/environment/service/TrainingEnvironmentService.java`
- Test: `java/match-mgr/src/test/java/com/match/environment/service/AccountEnvironmentCreationTest.java`

- [ ] **Step 1: Write failing placement-resolution test**

```java
@Test public void createResolvesAgentAndSlotFromAccountBinding() {
    when(accountSlots.requireReadyPlacement(3)).thenReturn(placement(3, "agent-1", "slot-2", 2));
    service.createForAccounts(request(Arrays.asList(3)), 1, "ADMIN");
    ArgumentCaptor<TrainingEnvironmentRecord> record = ArgumentCaptor.forClass(TrainingEnvironmentRecord.class);
    verify(environments).insert(record.capture());
    assertEquals("agent-1", record.getValue().getAgentId());
    assertEquals("slot-2", record.getValue().getSlotId());
}
```

- [ ] **Step 2: Run RED**

Run `mvn -Dtest=AccountEnvironmentCreationTest test`.

Expected: FAIL because account-only create API is missing.

- [ ] **Step 3: Implement account-only creation**

Define request fields `environmentType`, `environmentName`, `remark`, `userIds`, optional `courseId`, annotation/editor template IDs and versions. Do not accept `agentId`, `slotNumber`, or host ports.

For each account independently:

```java
for (Integer userId : request.getUserIds()) {
    try {
        AccountPlacement placement = accountSlots.requireReadyPlacement(userId);
        results.add(createOne(request, userId, placement, actorId, actorRole));
    } catch (RuntimeException error) {
        results.add(AccountEnvironmentResult.failure(userId, error.getMessage()));
    }
}
```

Add `GET /admin/training-environments/eligible-accounts` and return only enabled, bound, online, non-migrating accounts.

- [ ] **Step 4: Run GREEN**

Run `mvn -Dtest=AccountEnvironmentCreationTest,TrainingEnvironmentServiceTest test`.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add java/match-mgr/src/main/java/com/match/environment java/match-mgr/src/test/java/com/match/environment/service/AccountEnvironmentCreationTest.java
git commit -m "feat: create environments from account placement"
```

### Task 6: Add Account Environment Migration

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/account/model/AccountMigrationRequest.java`
- Create: `java/match-mgr/src/main/java/com/match/account/service/AccountEnvironmentMigrationService.java`
- Create: `java/match-mgr/src/main/java/com/match/account/web/AccountMigrationController.java`
- Modify: `java/match-mgr/src/main/java/com/match/agent/service/AgentCommandService.java`
- Modify: `xkp-agent/internal/protocol/command.go`
- Modify: `xkp-agent/internal/runtime/command_dispatcher.go`
- Test: `java/match-mgr/src/test/java/com/match/account/service/AccountEnvironmentMigrationServiceTest.java`
- Test: `xkp-agent/internal/runtime/command_dispatcher_test.go`

- [ ] **Step 1: Write migration preflight and rollback tests**

Test that missing target digest stops before old-container deletion, `migrateData=false` skips copy, `migrateData=true` dispatches copy/checksum, and rebuild failure leaves `FAILED_RECOVERABLE`.

```java
@Test public void missingTargetImageStopsBeforeCleanup() {
    when(images.hasDigest("agent-new", "sha256:abc")).thenReturn(false);
    expectFailure(() -> service.migrate(request(false), 1), "请先向目标服务器推送镜像");
    verify(operations, never()).deleteContainers(anyList());
}
```

- [ ] **Step 2: Run RED**

Run `mvn -Dtest=AccountEnvironmentMigrationServiceTest test` and `go test ./internal/runtime`.

Expected: FAIL because migration service and Agent commands are missing.

- [ ] **Step 3: Implement staged migration**

Implement states `PREFLIGHT`, `COPYING_DATA`, `CLEANING_SOURCE`, `REBINDING`, `REBUILDING`, `SUCCEEDED`, `FAILED_RECOVERABLE`. Preflight checks all environment template digests using latest successful per-agent deployments. Add Agent commands `COPY_ACCOUNT_WORKSPACE`, `DELETE_ACCOUNT_CONTAINERS`, and reuse environment-create commands for rebuild.

Only switch slot after preflight and optional copy succeed. Store original binding in the migration row so `restoreSource(migrationId)` can recreate on the source server.

- [ ] **Step 4: Run GREEN**

Run:

```powershell
mvn -Dtest=AccountEnvironmentMigrationServiceTest test
go test ./internal/runtime ./internal/protocol
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add java/match-mgr/src/main/java/com/match/account java/match-mgr/src/main/java/com/match/agent/service/AgentCommandService.java java/match-mgr/src/test/java/com/match/account xkp-agent/internal/protocol xkp-agent/internal/runtime
git commit -m "feat: migrate account environments between slots"
```

### Task 7: Implement Competition Password Lifecycle

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/mode/service/CompetitionPasswordCipher.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/service/CompetitionCredentialService.java`
- Create: `java/match-mgr/src/main/java/com/match/mode/web/CompetitionCredentialController.java`
- Modify: `java/match-mgr/src/main/java/com/match/mode/service/PlatformModeService.java`
- Modify: `java/match-mgr/src/main/resources/application-local.yml`
- Modify: `java/match-mgr/src/main/resources/application-prod.yml`
- Test: `java/match-mgr/src/test/java/com/match/mode/service/CompetitionCredentialServiceTest.java`

- [ ] **Step 1: Write failing lifecycle tests**

Cover eight-character non-ambiguous generation, original password restoration, regeneration, manual replacement, and transition rollback:

```java
@Test public void exitRestoresOriginalPasswordAndDeletesTemporaryCredential() {
    service.activate(12L, 1);
    String original = credentials.select(12L, 3).getOriginalPasswordValue();
    service.restore(12L, 1);
    assertEquals(original, users.selectById(3).getPassword());
    assertNull(credentials.select(12L, 3));
}
```

- [ ] **Step 2: Run RED**

Run `mvn -Dtest=CompetitionCredentialServiceTest test`.

Expected: FAIL because credential services do not exist.

- [ ] **Step 3: Implement encrypted temporary credentials**

Use AES-GCM with a base64 256-bit key from `MATCH_COMPETITION_PASSWORD_KEY`. Generate passwords from:

```java
private static final char[] COMPETITION_PASSWORD =
        "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
```

Generate exactly eight characters, encode the login password with `PasswordCodec`, encrypt the plaintext for controlled admin display, and store the exact previous `user.password` value. Integrate `activate(generation)` into successful entry and `restore(generation)` into exit and transition rollback. Reject account delete/migration while mode is competition.

- [ ] **Step 4: Run GREEN and mode tests**

Run:

```powershell
mvn -Dtest=CompetitionCredentialServiceTest,PlatformModeControllerTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add java/match-mgr/src/main/java/com/match/mode java/match-mgr/src/main/resources/application-*.yml java/match-mgr/src/test/java/com/match/mode
git commit -m "feat: rotate and restore competition passwords"
```

### Task 8: Build the Account Management UI

**Files:**
- Create: `vue/src/api/AccountManagement.js`
- Rewrite: `vue/src/views/management/UserManagement.vue`
- Modify: `vue/src/navigation/roleNavigation.js`
- Create: `vue/scripts/test-account-management-contract.js`
- Modify: `vue/package.json`

- [ ] **Step 1: Write failing frontend contract**

Assert the page includes create, batch create, bulk enable/disable/delete, remark/custom fields, server/slot selectors, migration data checkbox defaulting false, and competition password controls.

```js
mustContain(source, '批量创建账号')
mustContain(source, '批量启用')
mustContain(source, '迁移用户数据')
mustContain(source, 'value: false')
mustContain(source, '重新生成比赛密码')
```

- [ ] **Step 2: Run RED**

Run `node vue/scripts/test-account-management-contract.js`.

Expected: FAIL on the first missing control.

- [ ] **Step 3: Implement the page**

Use a selectable `el-table`, summary strip, filter toolbar, account editor, batch dialog, migration confirmation, and competition password dialog. Build custom fields as editable key/value rows. Show initial passwords only in the batch-result dialog and CSV export. Disable delete/migrate controls in competition mode.

- [ ] **Step 4: Run GREEN and frontend build**

Run:

```powershell
node vue/scripts/test-account-management-contract.js
npm --prefix vue run build
```

Expected: contract PASS and production build success.

- [ ] **Step 5: Commit**

```bash
git add vue/src/api/AccountManagement.js vue/src/views/management/UserManagement.vue vue/src/navigation/roleNavigation.js vue/scripts/test-account-management-contract.js vue/package.json
git commit -m "feat: add bound account management UI"
```

### Task 9: Simplify the Environment Creation UI

**Files:**
- Rewrite: `vue/src/views/management/TrainingManagement.vue`
- Modify: `vue/src/api/TrainingEnvironments.js`
- Create: `vue/scripts/test-account-environment-creation-contract.js`
- Modify: `vue/package.json`

- [ ] **Step 1: Write failing UI contract**

Assert the create form has eligible accounts and select-all but no editable server, slot, or host-port controls:

```js
mustContain(source, '全选当前可用账号')
mustContain(source, 'eligibleAccounts')
mustNotContain(createDialog, 'v-model="form.agentId"')
mustNotContain(createDialog, 'v-model="form.slotNumbers"')
mustNotContain(createDialog, 'HostPort')
```

- [ ] **Step 2: Run RED**

Run `node vue/scripts/test-account-environment-creation-contract.js`.

Expected: FAIL because server and slot controls still exist.

- [ ] **Step 3: Implement eligible-account selection**

Load `/admin/training-environments/eligible-accounts`, group options by server for display, and submit only `userIds` plus environment/template/course fields. Add a checkbox that selects all currently filtered eligible accounts. Display per-account creation results without hiding successful rows when another account fails.

- [ ] **Step 4: Run GREEN and build**

Run:

```powershell
node vue/scripts/test-account-environment-creation-contract.js
npm --prefix vue run build
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add vue/src/views/management/TrainingManagement.vue vue/src/api/TrainingEnvironments.js vue/scripts/test-account-environment-creation-contract.js vue/package.json
git commit -m "feat: create environments by account selection"
```

### Task 10: Add Port-Pool Capacity UI

**Files:**
- Modify: `vue/src/views/operations/ProcessingAgents.vue`
- Modify: `vue/src/api/ProcessingAgents.js`
- Create: `vue/src/components/agents/PortPoolDialog.vue`
- Create: `vue/scripts/test-port-pool-contract.js`

- [ ] **Step 1: Write failing contract**

Assert each server has a port-pool action and the dialog displays total, used, remaining, start and end.

- [ ] **Step 2: Run RED**

Run `node vue/scripts/test-port-pool-contract.js`.

Expected: FAIL because no port-pool dialog exists.

- [ ] **Step 3: Implement capacity and range editing**

Add an icon button with tooltip “端口池”, an eight-row dialog, overlap validation, and save through the super-admin port-pool endpoint. Display `总量 / 已用 / 剩余` and warn when remaining capacity is below 10%.

- [ ] **Step 4: Run GREEN and build**

Run `node vue/scripts/test-port-pool-contract.js` and `npm --prefix vue run build`.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add vue/src/views/operations/ProcessingAgents.vue vue/src/api/ProcessingAgents.js vue/src/components/agents/PortPoolDialog.vue vue/scripts/test-port-pool-contract.js
git commit -m "feat: manage processing server port pools"
```

### Task 11: End-to-End Verification and Deployment

**Files:**
- Modify: `deploy/dev/smoke-test.ps1`
- Modify: `docs/PROJECT_HANDOFF.md`

- [ ] **Step 1: Add smoke assertions**

Extend the smoke script to create a bound account, verify it appears in eligible accounts, create an environment without server fields, verify port ownership, enter/exit competition mode, and verify the original password works again.

- [ ] **Step 2: Run focused backend and Agent suites**

Run:

```powershell
mvn -Dtest=AccountSlotServiceTest,AdminUserManagementServiceTest,EnvironmentPortPoolServiceTest,AccountEnvironmentCreationTest,AccountEnvironmentMigrationServiceTest,CompetitionCredentialServiceTest,PlatformModeControllerTest test
go test ./internal/runtime ./internal/protocol ./internal/container
```

Expected: PASS.

- [ ] **Step 3: Run all frontend contracts and build**

Run:

```powershell
npm --prefix vue run test:contracts
npm --prefix vue run build
```

Expected: PASS with only existing asset-size warnings.

- [ ] **Step 4: Deploy backend and Agent in a safe order**

Apply Flyway migration through backend startup, verify schema version 41, deploy frontend, then upgrade idle Agents. Do not upgrade an Agent with an active image deployment or environment operation.

- [ ] **Step 5: Execute local smoke and record evidence**

Run `powershell -File deploy/dev/smoke-test.ps1`.

Expected: all account placement, environment creation, competition password restoration, and port-pool checks print `PASS`.

- [ ] **Step 6: Update handoff and commit**

Document migration 41, required `MATCH_COMPETITION_PASSWORD_KEY`, new APIs, recovery procedures, and the rule that account deletion requires prior environment deletion.

```bash
git add deploy/dev/smoke-test.ps1 docs/PROJECT_HANDOFF.md
git commit -m "docs: verify account placement workflows"
```
