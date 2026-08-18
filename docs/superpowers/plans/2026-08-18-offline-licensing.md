# XKP5.0 Offline Licensing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver offline, hardware-bound XKP5.0 licensing on Ubuntu, an administrator activation workflow, super-administrator diagnostics, and a separate Go license-generation executable.

**Architecture:** The management server owns installation identity, request issuance, strict license parsing, Ed25519 verification, state calculation, audit history, and a reusable enforcement guard inside a focused `com.match.licensing` package. A sibling Go repository owns private keys and signs the same canonical JSON payload; shared golden fixtures prove interoperability. Production accepts Ubuntu identities and production keys only, while the Windows development profile accepts a separate test key and development identity.

**Tech Stack:** Java 8 source level on JDK 17 runtime, Spring Boot 2.1, MyBatis-Plus, Flyway, Jackson, Bouncy Castle Ed25519, MySQL 8, Vue 2, Element UI, Node test scripts, Go 1.22 standard library.

---

## File Map

Management repository `xkp5-platform`:

- `java/match-mgr/src/main/java/com/match/licensing/model/*`: request, license, status, and diagnostics value objects.
- `java/match-mgr/src/main/java/com/match/licensing/persistence/*`: four licensing table entities and mappers.
- `java/match-mgr/src/main/java/com/match/licensing/crypto/*`: strict JSON, canonical JSON, SHA-256, and Ed25519 verification.
- `java/match-mgr/src/main/java/com/match/licensing/config/*`: environment, identity path, public-key allow-list, and monitor settings.
- `java/match-mgr/src/main/java/com/match/licensing/identity/*`: Ubuntu host identity and development identity providers.
- `java/match-mgr/src/main/java/com/match/licensing/service/*`: request, import, validation, status, clock, audit, and event behavior.
- `java/match-mgr/src/main/java/com/match/licensing/web/*`: normal-administrator and super-administrator endpoints.
- `java/match-mgr/src/main/java/com/match/licensing/guard/*`: reusable active-license and server-capacity checks.
- `java/match-mgr/src/test/java/com/match/licensing/*`: unit, controller, fixture, and integration tests.
- `java/match-mgr/src/test/resources/licensing/*`: cross-language request/license/key fixtures.
- `vue/src/api/License.js`: activation HTTP calls and browser downloads.
- `vue/src/views/management/PlatformLicense.vue`: ordinary-administrator activation page.
- `vue/src/views/operations/LicenseDiagnostics.vue`: super-administrator read-only diagnostics.
- `deploy/host-identity.sh`: Ubuntu identity-file creation with restrictive permissions.

Sibling repository `xkp-license-tool`:

- `cmd/xkp-license/main.go`: `keygen`, `issue`, and `inspect` command dispatch.
- `internal/request/request.go`: strict `.xkpreq` parsing.
- `internal/license/license.go`: validation and payload construction.
- `internal/canonicaljson/canonicaljson.go`: deterministic signing bytes.
- `internal/keys/keys.go`: Ed25519 key creation and loading.
- `testdata/*`: copied public interoperability fixtures only.

## Task 1: Lock The Cross-Language File Contract

**Files:**
- Create: `java/match-mgr/src/test/resources/licensing/request-v1.xkpreq`
- Create: `java/match-mgr/src/test/resources/licensing/license-v1.xkplic`
- Create: `java/match-mgr/src/test/java/com/match/licensing/crypto/LicenseFixtureContractTest.java`

- [ ] **Step 1: Add a failing fixture contract test**

```java
@Test
public void fixtureUsesVersionedEnvelopeAndXkp5Payload() throws Exception {
    JsonNode envelope = new ObjectMapper().readTree(resource("/licensing/license-v1.xkplic"));
    assertEquals(1, envelope.get("formatVersion").asInt());
    assertEquals("test-2026-01", envelope.get("keyId").asText());
    assertEquals("XKP5", envelope.get("payload").get("product").asText());
    assertEquals("DEVELOPMENT", envelope.get("payload").get("environment").asText());
    assertFalse(envelope.has("privateKey"));
}
```

- [ ] **Step 2: Run the test and verify the missing fixture fails**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=LicenseFixtureContractTest test`

Expected: FAIL because the fixture resources do not exist.

- [ ] **Step 3: Add fixed version-one fixture documents**

Use these exact top-level shapes; timestamps are UTC RFC 3339 strings and signatures use unpadded Base64 URL encoding:

```json
{"challenge":"2OSjPZQyUz_ZMeIVtRkF3U7lXr2SFq1CzB2Xso9h8Hw","createdAt":"2026-08-18T00:00:00Z","environment":"DEVELOPMENT","fingerprint":"sha256:fixture-fingerprint","formatVersion":1,"installationId":"00000000-0000-0000-0000-000000000001","organization":"Fixture Lab","platformVersion":"5.0.0","requestId":"00000000-0000-0000-0000-000000000002"}
```

```json
{"formatVersion":1,"keyId":"test-2026-01","payload":{"challenge":"2OSjPZQyUz_ZMeIVtRkF3U7lXr2SFq1CzB2Xso9h8Hw","environment":"DEVELOPMENT","expiresAt":"2027-08-18T00:00:00Z","fingerprint":"sha256:fixture-fingerprint","installationId":"00000000-0000-0000-0000-000000000001","issuedAt":"2026-08-18T00:00:00Z","keyId":"test-2026-01","licenseId":"00000000-0000-0000-0000-000000000003","maxProcessingServers":4,"notBefore":"2026-08-18T00:00:00Z","organization":"Fixture Lab","product":"XKP5","requestId":"00000000-0000-0000-0000-000000000002","supportedMajorVersion":5,"version":1},"signature":"AA"}
```

- [ ] **Step 4: Run the fixture contract test**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=LicenseFixtureContractTest test`

Expected: PASS.

- [ ] **Step 5: Commit the contract skeleton**

```bash
git add java/match-mgr/src/test/resources/licensing java/match-mgr/src/test/java/com/match/licensing/crypto/LicenseFixtureContractTest.java
git commit -m "test: define offline license file contract"
```

## Task 2: Add Licensing Persistence

**Files:**
- Create: `java/match-mgr/src/main/resources/db/migration/V17__platform_licensing.sql`
- Create: `java/match-mgr/src/main/java/com/match/licensing/persistence/PlatformInstallation.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/persistence/LicenseRequestRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/persistence/PlatformLicenseRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/persistence/LicenseAuditRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/persistence/PlatformInstallationMapper.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/persistence/LicenseRequestMapper.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/persistence/PlatformLicenseMapper.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/persistence/LicenseAuditMapper.java`
- Modify: `java/match-mgr/src/main/java/com/match/Application.java`
- Create: `java/match-mgr/src/test/java/com/match/licensing/persistence/LicensingSchemaTest.java`

- [ ] **Step 1: Write a migration-content test**

```java
@Test
public void migrationCreatesAllLicensingTablesAndSingleActiveConstraint() throws Exception {
    String sql = read("/db/migration/V17__platform_licensing.sql");
    assertTrue(sql.contains("CREATE TABLE platform_installation"));
    assertTrue(sql.contains("CREATE TABLE license_request"));
    assertTrue(sql.contains("CREATE TABLE platform_license"));
    assertTrue(sql.contains("CREATE TABLE license_audit"));
    assertTrue(sql.contains("active_slot"));
}
```

- [ ] **Step 2: Run the persistence test and verify it fails**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=LicensingSchemaTest test`

Expected: FAIL because migration V17 is absent.

- [ ] **Step 3: Create V17 with retained history and one active license**

```sql
CREATE TABLE platform_installation (
  installation_key VARCHAR(32) PRIMARY KEY,
  installation_id CHAR(36) NOT NULL UNIQUE,
  max_trusted_time DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL
);
CREATE TABLE license_request (
  request_id CHAR(36) PRIMARY KEY,
  installation_id CHAR(36) NOT NULL,
  challenge_hash CHAR(64) NOT NULL,
  fingerprint_digest VARCHAR(80) NOT NULL,
  environment VARCHAR(16) NOT NULL,
  organization VARCHAR(200) NULL,
  created_by INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  consumed_at DATETIME(3) NULL,
  consumed_license_id CHAR(36) NULL,
  INDEX idx_license_request_installation (installation_id, created_at)
);
CREATE TABLE platform_license (
  license_id CHAR(36) PRIMARY KEY,
  active_slot TINYINT GENERATED ALWAYS AS (CASE WHEN active = 1 THEN 1 ELSE NULL END) STORED,
  active TINYINT(1) NOT NULL DEFAULT 0,
  request_id CHAR(36) NOT NULL,
  organization VARCHAR(200) NOT NULL,
  environment VARCHAR(16) NOT NULL,
  fingerprint_digest VARCHAR(80) NOT NULL,
  key_id VARCHAR(80) NOT NULL,
  not_before DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  max_processing_servers INT NULL,
  signed_envelope LONGTEXT NOT NULL,
  imported_by INT NOT NULL,
  imported_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_platform_license_active (active_slot),
  INDEX idx_platform_license_imported (imported_at)
);
CREATE TABLE license_audit (
  audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor_user_id INT NULL,
  action VARCHAR(64) NOT NULL,
  result VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64) NULL,
  license_id CHAR(36) NULL,
  request_id CHAR(36) NULL,
  correlation_id CHAR(36) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  INDEX idx_license_audit_created (created_at)
);
```

- [ ] **Step 4: Add one focused MyBatis entity and mapper per table**

Use `LocalDateTime` fields, explicit `@TableName`, explicit `@TableId`, and `@TableField(exist = false)` for no database-only helper data. Do not add XML mappers because CRUD is covered by `BaseMapper<T>`.

Update mapper scanning so both the legacy and licensing packages are registered:

```java
@MapperScan({"com.match.mapper", "com.match.licensing.persistence"})
```

- [ ] **Step 5: Run the persistence test**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=LicensingSchemaTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add java/match-mgr/src/main/resources/db/migration/V17__platform_licensing.sql java/match-mgr/src/main/java/com/match/licensing/persistence java/match-mgr/src/main/java/com/match/Application.java java/match-mgr/src/test/java/com/match/licensing/persistence
git commit -m "feat: add offline licensing persistence"
```

## Task 3: Implement Strict Canonical JSON And Ed25519 Verification

**Files:**
- Modify: `java/match-mgr/pom.xml`
- Create: `java/match-mgr/src/main/java/com/match/licensing/crypto/StrictJson.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/crypto/CanonicalJson.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/crypto/Digests.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/crypto/LicenseSignatureVerifier.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/config/LicenseProperties.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/config/LicenseConfiguration.java`
- Create: `java/match-mgr/src/test/java/com/match/licensing/crypto/CanonicalJsonTest.java`
- Create: `java/match-mgr/src/test/java/com/match/licensing/crypto/LicenseSignatureVerifierTest.java`

- [ ] **Step 1: Write failing deterministic JSON and tamper-rejection tests**

```java
@Test
public void sortsEveryObjectKeyWithoutWhitespace() throws Exception {
    JsonNode value = strictJson.readTree("{\"z\":1,\"a\":{\"y\":2,\"b\":3}}");
    assertEquals("{\"a\":{\"b\":3,\"y\":2},\"z\":1}", canonicalJson.write(value));
}

@Test(expected = InvalidLicenseException.class)
public void rejectsChangedPayload() {
    verifier.verify(tamperedEnvelope, publicKeys);
}
```

- [ ] **Step 2: Run the crypto tests and verify compilation fails**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=CanonicalJsonTest,LicenseSignatureVerifierTest test`

Expected: FAIL because crypto classes are absent.

- [ ] **Step 3: Add the Java 8-compatible Ed25519 provider**

```xml
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bcprov-jdk15on</artifactId>
  <version>1.70</version>
</dependency>
```

- [ ] **Step 4: Implement strict parsing and recursive canonicalization**

Configure Jackson with `FAIL_ON_READING_DUP_TREE_KEY`, reject trailing tokens, recursively copy object fields into a `TreeMap`, preserve array order, and serialize as UTF-8 without pretty printing. Reject floating point numbers so both implementations sign only strings, booleans, null, and integral numbers.

- [ ] **Step 5: Implement signature verification**

```java
Signature signature = Signature.getInstance("Ed25519", new BouncyCastleProvider());
signature.initVerify(publicKey);
signature.update(canonicalJson.writeBytes(envelope.getPayload()));
if (!signature.verify(Base64.getUrlDecoder().decode(envelope.getSignature()))) {
    throw new InvalidLicenseException("INVALID_SIGNATURE", "授权文件签名无效");
}
```

Decode public keys from X.509 SubjectPublicKeyInfo Base64. Select the key only by an allow-listed `keyId`, and require payload `keyId` to equal envelope `keyId`.

`LicenseProperties` parses `XKP_LICENSE_PUBLIC_KEYS` as comma-separated `keyId=base64X509PublicKey` pairs, rejects duplicate or blank key IDs, and exposes an immutable map. `LicenseConfiguration` creates the strict/canonical JSON beans and verifier. Production startup fails when no production key exists. The local profile accepts only `XKP_LICENSE_TEST_PUBLIC_KEY`; it never falls back to a production key.

- [ ] **Step 6: Run the crypto tests**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=CanonicalJsonTest,LicenseSignatureVerifierTest test`

Expected: PASS, including wrong key, duplicate key, trailing content, float, and tampered payload cases.

- [ ] **Step 7: Commit**

```bash
git add java/match-mgr/pom.xml java/match-mgr/src/main/java/com/match/licensing/crypto java/match-mgr/src/main/java/com/match/licensing/config java/match-mgr/src/test/java/com/match/licensing/crypto
git commit -m "feat: verify canonical Ed25519 licenses"
```

## Task 4: Build Host Identity Providers

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/licensing/identity/HostIdentity.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/identity/HostIdentityProvider.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/identity/UbuntuHostIdentityProvider.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/identity/DevelopmentHostIdentityProvider.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/identity/HostIdentityConfiguration.java`
- Modify: `java/match-mgr/src/main/resources/application-local.yml`
- Modify: `java/match-mgr/src/main/resources/application-prod.yml`
- Create: `java/match-mgr/src/test/java/com/match/licensing/identity/HostIdentityProviderTest.java`

- [ ] **Step 1: Write failing production/development separation tests**

```java
@Test
public void productionRequiresTwoUbuntuIdentifiers() {
    try {
        ubuntuProvider.read(jsonWithOnlyMachineId());
        fail("production identity must contain at least two identifiers");
    } catch (IllegalStateException expected) {
        assertEquals("生产环境至少需要两个宿主机标识", expected.getMessage());
    }
}

@Test
public void developmentFingerprintIsStableAndMarkedDevelopment() {
    HostIdentity first = developmentProvider.load();
    assertEquals("DEVELOPMENT", first.getEnvironment());
    assertEquals(first.getFingerprint(), developmentProvider.load().getFingerprint());
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=HostIdentityProviderTest test`

Expected: FAIL because identity providers are absent.

- [ ] **Step 3: Implement normalized hashing**

Normalize by trimming, lower-casing UUID values, sorting keys, and hashing `key=value\n` lines with SHA-256. Return only `sha256:<hex>` outside the provider. Production reads `${XKP_HOST_IDENTITY_FILE:/etc/xkp/host-identity.json}` and rejects non-Linux hosts or fewer than two nonblank identifiers.

- [ ] **Step 4: Configure mutually exclusive profiles**

```yaml
# application-local.yml
xkp:
  licensing:
    environment: DEVELOPMENT
    development-identity: ${XKP_DEVELOPMENT_IDENTITY:windows-docker-development}
```

```yaml
# application-prod.yml
xkp:
  licensing:
    environment: PRODUCTION
    host-identity-file: ${XKP_HOST_IDENTITY_FILE:/etc/xkp/host-identity.json}
```

- [ ] **Step 5: Run identity tests and commit**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=HostIdentityProviderTest test`

Expected: PASS.

```bash
git add java/match-mgr/src/main/java/com/match/licensing/identity java/match-mgr/src/main/resources/application-local.yml java/match-mgr/src/main/resources/application-prod.yml java/match-mgr/src/test/java/com/match/licensing/identity
git commit -m "feat: bind licenses to management host identity"
```

## Task 5: Implement Request Export

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/licensing/model/PlatformRequest.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/service/InstallationService.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/service/LicenseRequestService.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/service/LicenseAuditService.java`
- Create: `java/match-mgr/src/test/java/com/match/licensing/service/LicenseRequestServiceTest.java`

- [ ] **Step 1: Write failing request-generation tests**

```java
@Test
public void createsFreshChallengeWithoutExportingRawHardware() {
    PlatformRequest first = service.create("Fixture Lab", 7);
    PlatformRequest second = service.create("Fixture Lab", 7);
    assertNotEquals(first.getRequestId(), second.getRequestId());
    assertNotEquals(first.getChallenge(), second.getChallenge());
    assertTrue(first.getFingerprint().startsWith("sha256:"));
    assertFalse(json.write(first).contains("machineId"));
}
```

- [ ] **Step 2: Run and observe the missing service failure**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=LicenseRequestServiceTest test`

Expected: FAIL.

- [ ] **Step 3: Implement stable installation identity and fresh requests**

`InstallationService` creates the singleton key `PRIMARY` with a random UUID in a transaction. `LicenseRequestService` creates a UUID request ID, 32 random bytes from `SecureRandom`, stores only `sha256(challenge)`, persists actor/time/fingerprint/environment, and returns canonical `.xkpreq` bytes. Organization is optional, trimmed, and limited to 200 characters.

`LicenseAuditService.recordFailure(...)` uses `@Transactional(propagation = Propagation.REQUIRES_NEW)` so rejected imports remain auditable after the import transaction throws. Store only safe reason codes; do not store signed contents or exception stack data in audit fields.

- [ ] **Step 4: Test request bytes and audit records**

Assert exact UTF-8 content type, filename `xkp-platform-<requestId>.xkpreq`, no credentials or raw identity values, and one `REQUEST_CREATED/SUCCESS` audit row.

- [ ] **Step 5: Run tests and commit**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=LicenseRequestServiceTest test`

Expected: PASS.

```bash
git add java/match-mgr/src/main/java/com/match/licensing/model/PlatformRequest.java java/match-mgr/src/main/java/com/match/licensing/service java/match-mgr/src/test/java/com/match/licensing/service/LicenseRequestServiceTest.java
git commit -m "feat: export offline platform requests"
```

## Task 6: Implement License Import, State, Clock, Events, And Guard

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/licensing/model/LicenseEnvelope.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/model/LicensePayload.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/model/LicenseState.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/model/LicenseStatus.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/service/LicenseValidationService.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/service/LicenseImportService.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/service/LicenseStatusService.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/service/LicenseStateChangedEvent.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/service/LicenseStateMonitor.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/guard/LicenseGuard.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/guard/LicenseAccessException.java`
- Create: `java/match-mgr/src/test/java/com/match/licensing/service/LicenseImportServiceTest.java`
- Create: `java/match-mgr/src/test/java/com/match/licensing/service/LicenseStatusServiceTest.java`
- Create: `java/match-mgr/src/test/java/com/match/licensing/service/LicenseStateMonitorTest.java`
- Create: `java/match-mgr/src/test/java/com/match/licensing/guard/LicenseGuardTest.java`
- Modify: `java/match-mgr/src/main/java/com/match/Application.java`

- [ ] **Step 1: Write failing import atomicity tests**

```java
@Test
public void invalidReplacementKeepsCurrentLicenseActive() {
    when(licenseMapper.selectActive()).thenReturn(existingActive);
    try {
        service.importLicense(tamperedBytes, 7);
        fail("tampered license must be rejected");
    } catch (InvalidLicenseException expected) {
        assertEquals("INVALID_SIGNATURE", expected.getReasonCode());
    }
    verify(licenseMapper, never()).deactivateAll();
    verify(licenseMapper, never()).insert(any());
}

@Test
public void successfulImportConsumesRequestAndPublishesActiveEvent() {
    PlatformLicenseRecord result = service.importLicense(validBytes, 7);
    assertTrue(result.getActive());
    verify(requestMapper).updateById(argThat(r -> r.getConsumedAt() != null));
    verify(events).publishEvent(any(LicenseStateChangedEvent.class));
}
```

- [ ] **Step 2: Write failing state and rollback tests**

Cover `NOT_ACTIVATED`, `ACTIVE`, `EXPIRING` at 30 days, `EXPIRED`, `INVALID`, and `CLOCK_ROLLBACK` when current time is more than five minutes before `maxTrustedTime`.

Add a monitor test that starts from `ACTIVE`, advances the injected clock beyond expiry, invokes one monitor tick, and verifies exactly one active-to-expired event and audit row. A second tick in the same state must not duplicate either.

- [ ] **Step 3: Run service tests and verify failure**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=LicenseImportServiceTest,LicenseStatusServiceTest,LicenseGuardTest test`

Expected: FAIL.

- [ ] **Step 4: Implement strict validation in this order**

1. Enforce 256 KiB file limit and strict JSON shape.
2. Resolve allow-listed key and verify Ed25519 signature.
3. Require product `XKP5`, version `1`, supported major `5`, and matching key IDs.
4. Require current environment, installation ID, and fingerprint.
5. Load the unconsumed request and compare `sha256(challenge)` plus stored fingerprint.
6. Require `notBefore <= now < expiresAt` and `expiresAt > notBefore`.
7. In one transaction, deactivate the old license, insert the new active license, consume the request, and audit success.

- [ ] **Step 5: Implement state and trusted-clock updates**

Inject `java.time.Clock` for deterministic tests. On status evaluation, reject rollback first; otherwise update `maxTrustedTime` only when time moves forward. Do not expose the envelope, signature, challenge, or raw fingerprint in `LicenseStatus`.

Add `@EnableScheduling` to `Application.java`. `LicenseStateMonitor` runs once per minute with `@Scheduled(fixedDelayString = "${xkp.licensing.monitor-delay-ms:60000}")`, keeps the last calculated state, and publishes/audits only state transitions. This detects expiry even when no user is making requests; Agent integration subscribes to the same event.

- [ ] **Step 6: Implement the reusable guard**

```java
public LicenseStatus requireActive() {
    LicenseStatus status = statusService.currentStatus();
    if (!status.isUsable()) {
        throw new LicenseAccessException(status.getState().name(), "平台授权未生效，无法执行此操作");
    }
    return status;
}

public void requireProcessingServerCapacity(int resultingServerCount) {
    LicenseStatus status = requireActive();
    Integer limit = status.getMaxProcessingServers();
    if (limit != null && resultingServerCount > limit) {
        throw new LicenseAccessException("SERVER_LIMIT_EXCEEDED", "处理服务器数量超过授权上限");
    }
}
```

- [ ] **Step 7: Run all licensing service tests and commit**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=LicenseImportServiceTest,LicenseStatusServiceTest,LicenseStateMonitorTest,LicenseGuardTest test`

Expected: PASS.

```bash
git add java/match-mgr/src/main/java/com/match/licensing java/match-mgr/src/test/java/com/match/licensing java/match-mgr/src/main/java/com/match/Application.java
git commit -m "feat: validate and enforce offline licenses"
```

## Task 7: Expose Role-Safe Licensing APIs

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/licensing/web/AdminLicenseController.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/web/SuperAdminLicenseController.java`
- Create: `java/match-mgr/src/main/java/com/match/licensing/web/LicenseImportException.java`
- Modify: `java/match-mgr/src/main/java/com/match/config/GlobalExceptionHandler.java`
- Create: `java/match-mgr/src/test/java/com/match/licensing/web/AdminLicenseControllerTest.java`
- Create: `java/match-mgr/src/test/java/com/match/licensing/web/SuperAdminLicenseControllerTest.java`

- [ ] **Step 1: Write failing permission and response tests**

```java
@Test
public void normalAdminCanExportAndImport() {
    controller.createRequest(new LicenseRequestInput("Fixture Lab"));
    controller.importLicense(mockMultipartFile);
    verify(roleGuard, times(2)).requireBusinessAdmin();
}

@Test
public void diagnosticsRequireSuperAdmin() {
    controller.diagnostics();
    verify(roleGuard).requireSuperAdmin();
}
```

- [ ] **Step 2: Run controller tests and verify failure**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=AdminLicenseControllerTest,SuperAdminLicenseControllerTest test`

Expected: FAIL.

- [ ] **Step 3: Implement exact endpoints**

- `GET /admin/license/status`
- `POST /admin/license/requests`
- `GET /admin/license/requests/{requestId}/download`
- `POST /admin/license/import` with multipart field `file`
- `GET /super-admin/license/diagnostics`
- `GET /super-admin/license/audits?page=1&size=50`, maximum size 100
- `POST /super-admin/license/revalidate`

Normal-admin endpoints call `requireBusinessAdmin`; diagnostics call `requireSuperAdmin`. Download uses attachment filename `xkp-platform-<requestId>.xkpreq`. The controller fetches the current actor from the `User` returned by the guard, never from request input.

- [ ] **Step 4: Add stable error codes**

Map malformed/import validation errors to HTTP 400, inactive guard errors to HTTP 423, unauthenticated to 401, and wrong role to 403. Response bodies contain `code`, Chinese `msg`, and a safe `data.reasonCode`; they never contain signed file contents.

- [ ] **Step 5: Run controller and regression tests**

Run: `mvn -f java/match-mgr/pom.xml test`

Expected: all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add java/match-mgr/src/main/java/com/match/licensing/web java/match-mgr/src/main/java/com/match/config/GlobalExceptionHandler.java java/match-mgr/src/test/java/com/match/licensing/web
git commit -m "feat: expose offline activation APIs"
```

## Task 8: Add Normal-Administrator Activation UI

**Files:**
- Create: `vue/src/api/License.js`
- Create: `vue/src/views/management/PlatformLicense.vue`
- Modify: `vue/src/views/management/ManagementHome.vue`
- Modify: `vue/src/router/index.js`
- Create: `vue/scripts/test-license-navigation.js`
- Modify: `vue/package.json`

- [ ] **Step 1: Add a failing route/API contract test**

```js
const assert = require('assert')
const source = require('fs').readFileSync('src/router/index.js', 'utf8')
assert.ok(source.includes("path: 'license'"))
assert.ok(source.includes("name: 'PlatformLicense'"))
console.log('license navigation tests passed')
```

- [ ] **Step 2: Run the test and verify failure**

Run: `npm --prefix vue run test:license`

Expected: FAIL because the script and route are not registered.

- [ ] **Step 3: Implement the API module**

```js
export const getLicenseStatus = () => request.get('admin/license/status')
export const createLicenseRequest = data => request.post('admin/license/requests', data, json)
export const downloadLicenseRequest = id => request.get(`admin/license/requests/${id}/download`, { responseType: 'blob' })
export const importLicense = file => {
  const body = new FormData()
  body.append('file', file)
  return request.post('admin/license/import', body)
}
```

- [ ] **Step 4: Build the activation page**

Show one status band with state, organization, license ID, expiry, remaining days, and processing-server limit. Provide `导出平台信息` and `导入授权文件` commands. Export asks only for an optional organization label; import accepts `.xkplic`, enforces 256 KiB client-side, requires confirmation, reloads status after success, and displays backend reason messages.

- [ ] **Step 5: Link from the management home without changing the approved first-level menu**

Add `/management/license` as a child route and a license-status panel on `ManagementHome.vue`. The panel is visible to ordinary administrators and routes to the activation page. Do not add another first-level sidebar item.

- [ ] **Step 6: Run frontend tests and production build**

Run: `npm --prefix vue run test:navigation`

Expected: PASS with the existing approved menu labels unchanged.

Run: `npm --prefix vue run test:license`

Expected: PASS.

Run: `npm --prefix vue run build`

Expected: build completes successfully; existing bundle-size warnings are acceptable.

- [ ] **Step 7: Commit**

```bash
git add vue/src/api/License.js vue/src/views/management/PlatformLicense.vue vue/src/views/management/ManagementHome.vue vue/src/router/index.js vue/scripts/test-license-navigation.js vue/package.json
git commit -m "feat: add administrator platform activation"
```

## Task 9: Add Super-Administrator Diagnostics UI

**Files:**
- Modify: `vue/src/api/SuperAdmin.js`
- Create: `vue/src/views/operations/LicenseDiagnostics.vue`
- Modify: `vue/src/navigation/roleNavigation.js`
- Modify: `vue/src/router/index.js`
- Modify: `vue/scripts/test-role-navigation.js`

- [ ] **Step 1: Extend the failing operations navigation assertion**

```js
assert.deepStrictEqual(navigation.operationsItems.map(item => item.label), [
  '运维主页', '管理员账号', '授权诊断'
])
```

- [ ] **Step 2: Run and verify the assertion fails**

Run: `npm --prefix vue run test:navigation`

Expected: FAIL with actual operations menu lacking `授权诊断`.

- [ ] **Step 3: Add diagnostics API calls and page**

```js
export const getLicenseDiagnostics = () => request.get('super-admin/license/diagnostics')
export const listLicenseAudits = params => request.get('super-admin/license/audits', { params })
export const revalidateLicense = () => request.post('super-admin/license/revalidate')
```

Show safe fingerprint digest, environment, configured key IDs, installation ID, current state, trusted-clock status, and paginated audit records. Provide only `重新校验`; do not provide request export, license generation, private-key entry, or raw envelope display.

- [ ] **Step 4: Register `/operations/license` and rerun tests/build**

Run: `npm --prefix vue run test:navigation`

Expected: PASS.

Run: `npm --prefix vue run build`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add vue/src/api/SuperAdmin.js vue/src/views/operations/LicenseDiagnostics.vue vue/src/navigation/roleNavigation.js vue/src/router/index.js vue/scripts/test-role-navigation.js
git commit -m "feat: add license maintenance diagnostics"
```

## Task 10: Create The Independent Go License Tool

**Files:**
- Create sibling repository: `../xkp-license-tool/.git`
- Create: `../xkp-license-tool/go.mod`
- Create: `../xkp-license-tool/.gitignore`
- Create: `../xkp-license-tool/cmd/xkp-license/main.go`
- Create: `../xkp-license-tool/internal/canonicaljson/canonicaljson.go`
- Create: `../xkp-license-tool/internal/canonicaljson/canonicaljson_test.go`
- Create: `../xkp-license-tool/internal/request/request.go`
- Create: `../xkp-license-tool/internal/request/request_test.go`
- Create: `../xkp-license-tool/internal/keys/keys.go`
- Create: `../xkp-license-tool/internal/license/license.go`
- Create: `../xkp-license-tool/internal/license/license_test.go`
- Create: `../xkp-license-tool/README.md`

- [ ] **Step 1: Initialize a genuinely separate repository**

Run from `C:\Users\84502\Desktop\新建文件夹\project`:

```powershell
New-Item -ItemType Directory -Path xkp-license-tool
git -C xkp-license-tool init
git -C xkp-license-tool config user.name baoyifeng3
git -C xkp-license-tool config user.email baoyifeng3@gmail.com
```

Expected: `git -C xkp-license-tool rev-parse --show-toplevel` ends in `xkp-license-tool`, not `xkp5-platform`.

- [ ] **Step 2: Write failing canonicalization and issue tests**

```go
func TestCanonicalMarshalSortsNestedObjectKeys(t *testing.T) {
    got, err := canonicaljson.Marshal(map[string]any{"z": 1, "a": map[string]any{"y": 2, "b": 3}})
    if err != nil { t.Fatal(err) }
    if string(got) != `{"a":{"b":3,"y":2},"z":1}` { t.Fatalf("got %s", got) }
}

func TestIssuedLicenseVerifiesAndReferencesRequest(t *testing.T) {
    envelope := Issue(fixtureRequest, fixturePrivateKey, Options{Organization: "Fixture Lab", ValidDays: 365, MaxProcessingServers: 4})
    if envelope.Payload.RequestID != fixtureRequest.RequestID { t.Fatal("request mismatch") }
    if !ed25519.Verify(fixturePublicKey, envelope.CanonicalPayload(), envelope.SignatureBytes()) { t.Fatal("invalid signature") }
}
```

- [ ] **Step 3: Run tests and verify failure**

Run: `go test ./...`

Expected: FAIL because packages are absent.

- [ ] **Step 4: Implement strict request parsing and canonical JSON**

Use `json.Decoder.DisallowUnknownFields`, require EOF after the first object, reject duplicate keys with a token pre-scan, use `json.Number`, reject decimals/exponents, recursively sort map keys, and preserve array order. Enforce `.xkpreq` size at 256 KiB.

- [ ] **Step 5: Implement private-key handling**

`keygen` writes PKCS#8 private key Base64 with Windows ACL guidance and X.509 public key Base64. Refuse to overwrite existing keys unless `--force` is supplied. `.gitignore` must contain:

```gitignore
keys/
issued/
requests/
*.key
*.xkpreq
*.xkplic
```

- [ ] **Step 6: Implement exact commands**

```text
xkp-license keygen --key-id prod-2026-01 --out-dir keys
xkp-license inspect --request customer.xkpreq
xkp-license issue --request customer.xkpreq --private-key keys/prod-2026-01-private.key --key-id prod-2026-01 --organization "Customer" --not-before 2026-08-18T00:00:00Z --expires-at 2027-08-18T00:00:00Z --max-processing-servers 4 --out issued/customer.xkplic
```

Require explicit organization and expiry, require `expires-at > not-before`, copy request environment/install/fingerprint/challenge exactly, generate UUIDv4 license ID locally, and print only output path plus safe metadata.

- [ ] **Step 7: Run tests and build both operator binaries**

Run: `go test ./...`

Expected: PASS.

Run: `$env:GOOS='windows'; $env:GOARCH='amd64'; go build -o dist/xkp-license-tool.exe ./cmd/xkp-license`

Expected: `dist/xkp-license-tool.exe` exists.

Run: `$env:GOOS='linux'; $env:GOARCH='amd64'; go build -o dist/xkp-license-tool ./cmd/xkp-license`

Expected: `dist/xkp-license-tool` exists.

- [ ] **Step 8: Commit in the independent repository**

```bash
git add .gitignore go.mod cmd internal README.md
git commit -m "feat: generate signed XKP5 licenses offline"
```

## Task 11: Replace Golden Fixtures With A Real Cross-Language Signature

**Files:**
- Modify: `java/match-mgr/src/test/resources/licensing/license-v1.xkplic`
- Modify: `java/match-mgr/src/test/resources/licensing/test-public-key.b64`
- Modify: `java/match-mgr/src/test/resources/licensing/license-v1-payload.canonical.json`
- Create: `../xkp-license-tool/testdata/request-v1.xkpreq`
- Create: `../xkp-license-tool/testdata/license-v1.xkplic`
- Create: `../xkp-license-tool/testdata/test-public-key.b64`

- [ ] **Step 1: Generate a disposable fixture key outside either Git index**

Use the Go tool `keygen` in a temporary directory, issue the fixed development request, and copy only the public key, canonical payload, request, and signed license into both fixture directories. Never copy the fixture private key into either repository.

- [ ] **Step 2: Make Java verify the Go-produced fixture**

```java
@Test
public void verifiesLicenseProducedByGoTool() throws Exception {
    LicenseEnvelope envelope = strictJson.read(resource("/licensing/license-v1.xkplic"), LicenseEnvelope.class);
    verifier.verify(envelope, singletonMap("test-2026-01", readPublicKey()));
    assertArrayEquals(readBytes("/licensing/license-v1-payload.canonical.json"), canonicalJson.writeBytes(envelope.getPayload()));
}
```

- [ ] **Step 3: Make Go verify the checked-in Java fixture copy**

Read `testdata/license-v1.xkplic`, canonicalize payload, decode the checked-in public key, and assert `ed25519.Verify` returns true.

- [ ] **Step 4: Run both suites**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=LicenseFixtureContractTest,LicenseSignatureVerifierTest test`

Expected: PASS.

Run from `xkp-license-tool`: `go test ./...`

Expected: PASS.

- [ ] **Step 5: Commit fixtures in both repositories**

```bash
git add java/match-mgr/src/test/resources/licensing java/match-mgr/src/test/java/com/match/licensing/crypto
git commit -m "test: verify Go generated license fixtures"
```

```bash
git add testdata internal
git commit -m "test: add cross-language license fixtures"
```

## Task 12: Wire Ubuntu Identity And Production Key Configuration

**Files:**
- Create: `deploy/host-identity.sh`
- Modify: `compose.prod.yml`
- Modify: `.env.prod.example`
- Modify: `deploy/offline/build-release.sh`
- Modify: `deploy/offline/install.sh`
- Modify: `deploy/offline/README.md`
- Modify: `deploy/offline/tests/test-static.sh`
- Create: `deploy/tests/test-host-identity.sh`

- [ ] **Step 1: Add failing deployment assertions**

Assert production Compose mounts `/etc/xkp/host-identity.json:ro`, sets `XKP_LICENSE_PUBLIC_KEYS`, and does not set development identity or test key values. Assert the release package includes `host-identity.sh`.

- [ ] **Step 2: Run deployment tests and verify failure**

Run: `bash deploy/offline/tests/test-static.sh`

Expected: FAIL with missing identity/key wiring.

- [ ] **Step 3: Implement host identity creation**

The script must require root, read `/etc/machine-id`, `/sys/class/dmi/id/product_uuid` when readable, and `findmnt -no UUID /`. It must require at least two values, write JSON atomically through `mktemp`, install it as `/etc/xkp/host-identity.json` with owner `root:root` and mode `0444`, and print no identifier values.

- [ ] **Step 4: Wire only public keys into production**

```yaml
environment:
  XKP_LICENSE_PUBLIC_KEYS: ${XKP_LICENSE_PUBLIC_KEYS:?Set production license public keys}
  XKP_HOST_IDENTITY_FILE: /etc/xkp/host-identity.json
volumes:
  - /etc/xkp/host-identity.json:/etc/xkp/host-identity.json:ro
```

Use the environment format `keyId=base64X509PublicKey`, comma-separated for rotation. Do not add any private-key variable.

- [ ] **Step 5: Run static, host-identity, and Compose validation**

Run: `bash deploy/tests/test-host-identity.sh`

Expected: PASS using a temporary output path and fixture identifier files.

Run: `bash deploy/offline/tests/test-static.sh`

Expected: PASS.

Run: `docker compose --env-file .env -f compose.prod.yml config --quiet`

Expected: exit code 0 with production public key configured in local `.env`.

- [ ] **Step 6: Commit**

```bash
git add deploy/host-identity.sh compose.prod.yml .env.prod.example deploy/offline
git commit -m "feat: configure Ubuntu production licensing"
```

## Task 13: End-To-End Verification And Documentation

**Files:**
- Modify: `README.md`
- Create: `docs/licensing/operator-guide.md`
- Create: `docs/licensing/administrator-guide.md`

- [ ] **Step 1: Document the operator workflow without secrets**

Describe key generation, public-key deployment, `.xkpreq` inspection, `.xkplic` issuance, renewal, key rotation, and safe offline storage. State that private keys must never be sent to a customer or copied to the management server.

- [ ] **Step 2: Document the administrator workflow**

Describe status meanings, platform-information export, sending the request to the operator, importing the returned file, expiry renewal, and safe responses to wrong-machine, wrong-environment, invalid-signature, and clock-rollback messages.

- [ ] **Step 3: Run all repository checks**

Run: `mvn -f java/match-mgr/pom.xml test`

Expected: PASS.

Run: `npm --prefix vue run test:navigation`

Expected: PASS.

Run: `npm --prefix vue run test:license`

Expected: PASS.

Run: `npm --prefix vue run build`

Expected: PASS.

Run: `bash deploy/offline/tests/test-static.sh`

Expected: PASS.

Run from `xkp-license-tool`: `go test ./...`

Expected: PASS.

- [ ] **Step 4: Exercise the offline workflow on Windows development profile**

1. Start the existing MySQL, FastDFS, Java, and Vue Compose services.
2. Log in as a normal administrator.
3. Export a `.xkpreq` from `/management/license`.
4. Issue a development `.xkplic` with the test key.
5. Import it and verify `ACTIVE` plus the expected organization/expiry/limit.
6. Tamper with one payload byte and verify import returns `INVALID_SIGNATURE` while the valid license remains active.
7. Verify the super-administrator diagnostics page contains no signature, challenge, raw identifier, or envelope.

- [ ] **Step 5: Exercise the formal workflow on Ubuntu before release**

Run `deploy/host-identity.sh`, deploy the production public key, export a production request, issue with the offline production key, import it, restart Java, and verify the license remains `ACTIVE`. Confirm the same production license is rejected by the Windows development profile.

- [ ] **Step 6: Final commit**

```bash
git add README.md docs/licensing
git commit -m "docs: add offline licensing operations guides"
```

## Completion Criteria

- A normal administrator can export platform information and import a valid signed file without internet access.
- The management server never contains a private signing key.
- Production licensing activates Ubuntu management servers only.
- Windows works only under the development profile with a separate test key.
- Invalid replacement files never deactivate a valid current license.
- Expiry, invalidity, and clock rollback make `LicenseGuard.requireActive()` fail closed.
- License status events are available for the processing-server Agent phase to stop containers.
- Super administrators can diagnose and audit licensing but cannot generate licenses.
- Both repositories pass their complete test suites and share a verified Ed25519 golden fixture.
