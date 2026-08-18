# Processing Agent Registration And Monitoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver an independently deployable Ubuntu processing Agent plus XKP5.0 enrollment, authenticated heartbeat, 30-day metrics, alerts, and role-specific monitoring interfaces.

**Architecture:** The Go Agent initiates TLS connections to the management server's fixed LAN IP. Spring Boot owns one-time enrollment, credential authentication, latest state, atomic minute aggregation, retention, and read APIs; Nginx terminates a private-CA TLS listener on port 19443. The Agent lives in the separate `xkp-agent` Git repository and never exposes Docker Engine to the LAN.

**Tech Stack:** Java 8 source compatibility, Spring Boot 2.1, MyBatis-Plus 3.2, Flyway, MySQL 8, Vue 2, Element UI, Go 1.22, gopsutil, NVIDIA go-nvml, official Docker Go client, Nginx, OpenSSL, systemd, Docker Compose.

---

## File Structure

Management-server additions live under `java/match-mgr/src/main/java/com/match/agent`:

- `model/` contains stable Agent protocol request and response DTOs.
- `persistence/` contains database records and mappers.
- `service/` contains enrollment, authentication, heartbeat aggregation, query, retention, and audit behavior.
- `web/` contains Agent, super-administrator, and normal-administrator controllers.

The new Agent repository uses:

- `cmd/xkp-agent/` for the executable entry point.
- `internal/config/` for strict configuration and root-only persistence.
- `internal/client/` for registration, heartbeat, TLS, and command polling.
- `internal/collect/` for isolated system, NVIDIA, and Docker collectors.
- `internal/runtime/` for scheduling, retry, boot identity, and sequencing.
- `deploy/` for installation, verification, removal, and systemd assets.

### Task 1: Management Persistence Contract

**Files:**
- Create: `java/match-mgr/src/main/resources/db/migration/V18__processing_agent_monitoring.sql`
- Create: `java/match-mgr/src/test/java/com/match/agent/persistence/ProcessingAgentSchemaTest.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/persistence/ProcessingAgentRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/persistence/RegistrationTokenRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/persistence/AgentMetricMinuteRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/persistence/AgentAuditRecord.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/persistence/ProcessingAgentMapper.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/persistence/RegistrationTokenMapper.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/persistence/AgentMetricMinuteMapper.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/persistence/AgentAuditMapper.java`
- Modify: `java/match-mgr/src/main/java/com/match/Application.java`

- [ ] **Step 1: Write the failing migration contract test**

```java
@Test
public void migrationDefinesAgentIdentityTokensMetricsAndAudit() throws Exception {
    String sql = read("/db/migration/V18__processing_agent_monitoring.sql");
    assertTrue(sql.contains("CREATE TABLE processing_agent"));
    assertTrue(sql.contains("CREATE TABLE processing_agent_registration_token"));
    assertTrue(sql.contains("CREATE TABLE processing_agent_metric_minute"));
    assertTrue(sql.contains("CREATE TABLE processing_agent_audit"));
    assertTrue(sql.contains("UNIQUE KEY uk_processing_agent_machine"));
    assertTrue(sql.contains("UNIQUE KEY uk_agent_metric_minute"));
}
```

- [ ] **Step 2: Run the test and verify the missing migration failure**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace xkp5-test-license-complete:latest mvn -f java/match-mgr/pom.xml -Dtest=ProcessingAgentSchemaTest test
```

Expected: FAIL because `V18__processing_agent_monitoring.sql` is missing.

- [ ] **Step 3: Add the four-table migration**

The migration defines `processing_agent` with identity, credential digest, latest metrics, boot/sequence, and timestamps; a hashed ten-minute registration token table; a unique `(agent_id, bucket_start)` minute table with sample count/sums/maxima; and an append-only audit table. All resource percentages use `DECIMAL(6,2)`, byte values use `BIGINT`, timestamps use `DATETIME(3)`, and collector errors use `JSON`.

```sql
CREATE TABLE processing_agent (
  agent_id CHAR(36) PRIMARY KEY,
  display_name VARCHAR(80) NOT NULL,
  machine_digest CHAR(64) NOT NULL,
  hostname VARCHAR(255) NOT NULL,
  primary_ip VARCHAR(45) NOT NULL,
  mac_address VARCHAR(17) NOT NULL,
  agent_version VARCHAR(40) NOT NULL,
  credential_digest CHAR(64) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  last_seen_at DATETIME(3) NULL,
  last_boot_id CHAR(36) NULL,
  last_sequence BIGINT NULL,
  latest_metrics JSON NULL,
  removed_at DATETIME(3) NULL,
  registered_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_processing_agent_machine (machine_digest),
  UNIQUE KEY uk_processing_agent_credential (credential_digest),
  INDEX idx_processing_agent_seen (enabled, last_seen_at)
);

CREATE TABLE processing_agent_registration_token (
  token_id CHAR(36) PRIMARY KEY,
  token_digest CHAR(64) NOT NULL,
  label VARCHAR(80) NULL,
  created_by INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  consumed_at DATETIME(3) NULL,
  registered_agent_id CHAR(36) NULL,
  UNIQUE KEY uk_agent_registration_digest (token_digest),
  INDEX idx_agent_registration_expiry (expires_at, consumed_at)
);

CREATE TABLE processing_agent_metric_minute (
  metric_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  agent_id CHAR(36) NOT NULL,
  bucket_start DATETIME NOT NULL,
  sample_count INT NOT NULL,
  cpu_sum DECIMAL(12,2) NULL,
  cpu_max DECIMAL(6,2) NULL,
  ram_sum DECIMAL(12,2) NULL,
  ram_max DECIMAL(6,2) NULL,
  gpu_sum DECIMAL(12,2) NULL,
  gpu_max DECIMAL(6,2) NULL,
  gpu_memory_sum DECIMAL(12,2) NULL,
  gpu_memory_max DECIMAL(6,2) NULL,
  system_disk_sum DECIMAL(12,2) NULL,
  system_disk_max DECIMAL(6,2) NULL,
  workspace_disk_sum DECIMAL(12,2) NULL,
  workspace_disk_max DECIMAL(6,2) NULL,
  running_environment_sum BIGINT NOT NULL DEFAULT 0,
  running_environment_max INT NOT NULL DEFAULT 0,
  running_container_sum BIGINT NOT NULL DEFAULT 0,
  running_container_max INT NOT NULL DEFAULT 0,
  docker_available_samples INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_agent_metric_minute (agent_id, bucket_start),
  INDEX idx_agent_metric_retention (bucket_start)
);

CREATE TABLE processing_agent_audit (
  audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor_user_id INT NULL,
  agent_id CHAR(36) NULL,
  token_id CHAR(36) NULL,
  action VARCHAR(64) NOT NULL,
  result VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64) NULL,
  correlation_id CHAR(36) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  INDEX idx_agent_audit_agent (agent_id, created_at),
  INDEX idx_agent_audit_created (created_at)
);
```

- [ ] **Step 4: Add focused records and mappers**

Records use MyBatis-Plus `@TableName` and `@TableId`; mappers extend `BaseMapper<T>`. Add `com.match.agent.persistence` to `@MapperScan` without moving existing mappers.

- [ ] **Step 5: Run schema and full Java tests**

Expected: `ProcessingAgentSchemaTest` and all existing tests pass.

- [ ] **Step 6: Commit persistence contract**

```bash
git add java/match-mgr/src/main/resources/db/migration/V18__processing_agent_monitoring.sql java/match-mgr/src/main/java/com/match/agent java/match-mgr/src/test/java/com/match/agent java/match-mgr/src/main/java/com/match/Application.java
git commit -m "feat: define processing agent persistence"
```

### Task 2: One-Time Registration Tokens

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/agent/service/AgentAuditService.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/service/RegistrationTokenService.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/model/RegistrationTokenView.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/web/SuperAdminAgentController.java`
- Create: `java/match-mgr/src/test/java/com/match/agent/service/RegistrationTokenServiceTest.java`
- Create: `java/match-mgr/src/test/java/com/match/agent/web/SuperAdminAgentControllerTest.java`

- [ ] **Step 1: Write failing token lifecycle tests**

Tests require an active license, reject capacity exhaustion, store only a SHA-256 digest, return plaintext once, set expiry to exactly ten minutes from the injected clock, and require `RoleGuard.requireSuperAdmin()` at the controller.

```java
RegistrationTokenView issued = service.create(7, "机房 A");
assertEquals(now.plus(Duration.ofMinutes(10)), issued.getExpiresAt());
verify(licenseGuard).requireProcessingServerCapacity(3);
assertFalse(saved.getTokenDigest().contains(issued.getToken()));
```

- [ ] **Step 2: Run tests and verify missing-service failures**

Expected: FAIL because the token service and API do not exist.

- [ ] **Step 3: Implement secure token issuance**

Use `SecureRandom` to generate 32 bytes, Base64 URL encoding without padding for the returned token, and the existing SHA-256 utility for storage. Count enabled Agents before calling `LicenseGuard.requireProcessingServerCapacity(count + 1)`. Store creator, optional label, creation time, expiry, and correlation ID in the audit record.

- [ ] **Step 4: Expose super-administrator token endpoints**

```java
@PostMapping("registration-tokens")
public ResponseResult<Object> createToken(@RequestBody RegistrationTokenRequest request) {
    User actor = roleGuard.requireSuperAdmin();
    return Response.makeOKRsp(tokens.create(actor.getUserId(), request.getLabel()));
}
```

The list endpoint returns metadata only and never returns token plaintext or digest.

- [ ] **Step 5: Run focused and full Java tests**

Expected: token lifecycle, controller authorization, licensing, and all existing tests pass.

- [ ] **Step 6: Commit registration tokens**

```bash
git add java/match-mgr/src/main/java/com/match/agent java/match-mgr/src/test/java/com/match/agent
git commit -m "feat: issue processing agent registration tokens"
```

### Task 3: Agent Enrollment And Authentication

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/agent/model/AgentRegistrationRequest.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/model/AgentRegistrationResponse.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/service/AgentCredentialService.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/service/AgentRegistrationService.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/web/AgentController.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/web/AgentProtocolException.java`
- Create: `java/match-mgr/src/test/java/com/match/agent/service/AgentRegistrationServiceTest.java`
- Create: `java/match-mgr/src/test/java/com/match/agent/service/AgentCredentialServiceTest.java`
- Create: `java/match-mgr/src/test/java/com/match/agent/web/AgentControllerTest.java`

- [ ] **Step 1: Write failing transactional enrollment tests**

Cover valid registration, expired/consumed/unknown token, duplicate machine digest, licensed capacity, rollback when Agent insertion fails, credential digest storage, `401` invalid bearer, and `403` disabled Agent.

```java
AgentRegistrationResponse result = service.register(validRequest());
assertNotNull(UUID.fromString(result.getAgentId()));
assertTrue(result.getCredential().length() >= 43);
assertEquals(now, token.getConsumedAt().toInstant(ZoneOffset.UTC));
```

- [ ] **Step 2: Verify tests fail because enrollment is absent**

- [ ] **Step 3: Implement registration in one transaction**

Lock the token row, compare SHA-256 digests in constant time, validate expiry and consumption, enforce capacity, insert the Agent, consume the token, and append an audit. Machine identity is a lowercase 64-character SHA-256 value; IP and MAC are validated strictly.

- [ ] **Step 4: Implement bearer authentication**

`AgentCredentialService.authenticate(String authorization)` requires exactly `Bearer <credential>`, hashes the credential, loads the Agent by digest, checks enabled state, and never logs the header.

- [ ] **Step 5: Expose stable Agent protocol responses**

```java
@PostMapping("register")
public AgentRegistrationResponse register(@RequestBody AgentRegistrationRequest request) {
    return registration.register(request);
}
```

Map protocol failures to JSON `{ "code": "TOKEN_EXPIRED", "message": "..." }` with HTTP `400`, authentication failures to `401`, and disabled Agent to `403`.

- [ ] **Step 6: Run focused and full Java tests, then commit**

```bash
git add java/match-mgr/src/main/java/com/match/agent java/match-mgr/src/test/java/com/match/agent
git commit -m "feat: enroll and authenticate processing agents"
```

### Task 4: Heartbeat Sequencing And Minute Aggregation

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/agent/model/AgentHeartbeatRequest.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/model/AgentHeartbeatAck.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/model/AgentMetricSnapshot.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/service/AgentHeartbeatService.java`
- Modify: `java/match-mgr/src/main/java/com/match/agent/persistence/ProcessingAgentMapper.java`
- Modify: `java/match-mgr/src/main/java/com/match/agent/persistence/AgentMetricMinuteMapper.java`
- Modify: `java/match-mgr/src/main/java/com/match/agent/web/AgentController.java`
- Create: `java/match-mgr/src/test/java/com/match/agent/service/AgentHeartbeatServiceTest.java`

- [ ] **Step 1: Write failing heartbeat behavior tests**

Test a first heartbeat, increasing sequence, duplicate sequence, lower sequence, new boot ID, null GPU metrics, collector errors, database failure, and atomic minute-bucket inputs.

```java
AgentHeartbeatAck first = service.accept(agent, heartbeat("boot-1", 1));
AgentHeartbeatAck duplicate = service.accept(agent, heartbeat("boot-1", 1));
assertTrue(first.isAccepted());
assertFalse(duplicate.isAccepted());
verify(metricMapper, times(1)).accumulate(any(AgentMetricMinuteRecord.class));
```

- [ ] **Step 2: Verify the tests fail before implementation**

- [ ] **Step 3: Add strict metric validation and sequencing**

Percent values are nullable or between 0 and 100; byte counts are non-negative; boot ID is UUID; sequence is positive. Server time, not Agent time, sets `last_seen_at` and the UTC minute bucket.

- [ ] **Step 4: Add atomic latest-state and aggregate mapper statements**

The latest-state update succeeds only for a new boot ID or larger sequence. The minute upsert increments sample count, adds sums, and keeps `GREATEST` maxima in one MySQL statement. Duplicate requests return an acknowledgement without aggregate writes.

- [ ] **Step 5: Add heartbeat and empty command-poll endpoints**

`POST /agent/v1/heartbeat` authenticates the bearer and returns accepted sequence and server time. `GET /agent/v1/commands/poll?waitSeconds=25` authenticates, caps wait at 25 seconds, and returns `{ "commands": [] }`.

- [ ] **Step 6: Run tests and commit**

```bash
git add java/match-mgr/src/main/java/com/match/agent java/match-mgr/src/test/java/com/match/agent
git commit -m "feat: ingest processing agent heartbeats"
```

### Task 5: Monitoring Queries, Retention, And Role Boundaries

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/agent/model/ProcessingAgentView.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/model/AgentMetricPoint.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/service/AgentQueryService.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/service/AgentMetricRetention.java`
- Create: `java/match-mgr/src/main/java/com/match/agent/web/AdminAgentController.java`
- Modify: `java/match-mgr/src/main/java/com/match/agent/web/SuperAdminAgentController.java`
- Create: `java/match-mgr/src/test/java/com/match/agent/service/AgentQueryServiceTest.java`
- Create: `java/match-mgr/src/test/java/com/match/agent/service/AgentMetricRetentionTest.java`
- Create: `java/match-mgr/src/test/java/com/match/agent/web/AgentRoleBoundaryTest.java`

- [ ] **Step 1: Write failing online, history, retention, and role tests**

At exactly 15 seconds an enabled Agent is online; after 15 seconds it is offline. Normal administrators can list/detail/history but cannot mutate. Super administrators can enable, disable, and remove. Retention deletes buckets strictly older than 30 days.

- [ ] **Step 2: Verify red tests**

- [ ] **Step 3: Implement read models and server-time online derivation**

Views expose no credential or token digest. Latest metrics preserve nullable unavailable values and stable collector error codes. History accepts a bounded `from/to` range of at most 30 days.

- [ ] **Step 4: Implement scheduled retention and mutations**

Run cleanup once daily using an injected `Clock`. Disable and remove invalidate credentials and write audits. Remove performs a soft removal so metric and audit history remain available.

- [ ] **Step 5: Expose role-specific APIs and run full Java tests**

Use `/admin/processing-agents` for read-only normal-admin routes and `/super-admin/processing-agents` for operations routes.

- [ ] **Step 6: Commit monitoring APIs**

```bash
git add java/match-mgr/src/main/java/com/match/agent java/match-mgr/src/test/java/com/match/agent
git commit -m "feat: expose processing agent monitoring"
```

### Task 6: Role-Specific Monitoring Interfaces

**Files:**
- Create: `vue/src/api/ProcessingAgents.js`
- Create: `vue/src/components/agents/AgentStatusTable.vue`
- Create: `vue/src/components/agents/AgentMetricSummary.vue`
- Create: `vue/src/components/agents/AgentHistoryChart.vue`
- Modify: `vue/src/views/management/DeviceManagement.vue`
- Create: `vue/src/views/operations/ProcessingAgents.vue`
- Modify: `vue/src/views/operations/OperationsHome.vue`
- Modify: `vue/src/navigation/roleNavigation.js`
- Modify: `vue/src/router/index.js`
- Modify: `vue/src/api/SuperAdmin.js`
- Create: `vue/scripts/test-agent-navigation.js`
- Create: `vue/scripts/test-agent-view-contract.js`
- Modify: `vue/package.json`

- [ ] **Step 1: Write failing navigation and view contract tests**

```javascript
assert.ok(operationsItems.some(item => item.route === '/operations/processing-agents'))
assert.ok(deviceSource.includes('AgentStatusTable'))
assert.ok(operationsSource.includes('createRegistrationToken'))
assert.ok(operationsSource.includes('disableProcessingAgent'))
```

- [ ] **Step 2: Run both scripts and verify failures**

Run `node scripts/test-agent-navigation.js` and `node scripts/test-agent-view-contract.js`.

- [ ] **Step 3: Implement shared status, metric, and history components**

Use stable table height, skeleton loading, explicit empty state, online/offline tags, `--` for unavailable metrics, collector-error callouts, and ECharts history with CPU/GPU/RAM/disk series. Do not add decorative cards or explanatory feature text.

- [ ] **Step 4: Implement normal-admin read-only device page**

The page lists processing servers and opens latest/history detail. It contains no registration, enable, disable, remove, or editing actions.

- [ ] **Step 5: Implement super-administrator operations page**

Add one-time token generation with a single copy opportunity, token metadata, enable/disable/remove confirmations, identity detail, latest metrics, history, and audit result states.

- [ ] **Step 6: Run frontend scripts and production build**

Expected: navigation and Agent contract scripts pass; `node scripts/vue-cli.js build` exits 0 with only known bundle-size warnings.

- [ ] **Step 7: Commit monitoring UI**

```bash
git add vue/src vue/scripts vue/package.json
git commit -m "feat: add processing server monitoring views"
```

### Task 7: Internal CA And Agent TLS Listener

**Files:**
- Create: `deploy/agent-ca.sh`
- Create: `deploy/tests/test-agent-ca.sh`
- Modify: `docker/vue/nginx.conf`
- Modify: `docker/vue/Dockerfile`
- Modify: `compose.prod.yml`
- Modify: `compose.offline.yml`
- Modify: `.env.prod.example`
- Modify: `deploy/offline/build-release.sh`
- Modify: `deploy/offline/tests/test-static.sh`

- [ ] **Step 1: Write failing static and certificate tests**

The test generates a CA and server certificate in a temporary directory, runs `openssl verify`, verifies the configured fixed IP appears as an IP SAN, checks private files are `0600`, and asserts Compose exposes only Nginx port 19443 for Agent traffic.

- [ ] **Step 2: Run the shell test in an Ubuntu/OpenSSL container and verify red**

- [ ] **Step 3: Implement idempotent CA/server certificate generation**

`sudo ./deploy/agent-ca.sh --management-ip 192.168.1.10 --output /etc/xkp/agent-tls` creates a long-lived internal CA once and renews the server leaf without replacing the CA. It refuses invalid/non-local IP input and never prints private-key contents.

- [ ] **Step 4: Add Nginx 19443 TLS server**

```nginx
server {
    listen 19443 ssl;
    ssl_certificate /etc/xkp/agent-tls/server.crt;
    ssl_certificate_key /etc/xkp/agent-tls/server.key;
    location /agent/v1/ {
        proxy_pass http://java:19141/agent/v1/;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

- [ ] **Step 5: Mount certificates and package the CA script**

Production and offline Compose expose `${XKP_AGENT_PORT:-19443}:19443`; local browser ports remain unchanged. The Vue container receives read-only server certificate and key mounts.

- [ ] **Step 6: Run TLS, Compose, and offline static tests, then commit**

```bash
git add deploy docker/vue compose.prod.yml compose.offline.yml .env.prod.example
git commit -m "feat: secure processing agent API with internal CA"
```

### Task 8: Independent Go Agent Configuration And Enrollment Client

**Files:**
- Create repository: `C:/Users/84502/Desktop/新建文件夹/project/xkp-agent`
- Create: `go.mod`
- Create: `.gitignore`
- Create: `cmd/xkp-agent/main.go`
- Create: `internal/config/config.go`
- Create: `internal/config/config_test.go`
- Create: `internal/client/client.go`
- Create: `internal/client/client_test.go`
- Create: `internal/identity/identity.go`
- Create: `internal/identity/identity_test.go`

- [ ] **Step 1: Initialize the independent repository**

Use Git identity `baoyifeng3 <baoyifeng3@gmail.com>`, default branch `main`, module name `xkp-agent`, and ignore `dist/`, local config, credentials, and test certificates.

- [ ] **Step 2: Write failing strict-config and TLS enrollment tests**

Tests reject unknown YAML fields, insecure production URLs, missing CA, world-readable credential files, malformed IP/MAC/machine identity, and enrollment responses without both Agent ID and credential. `httptest.NewTLSServer` verifies the supplied CA is required.

- [ ] **Step 3: Implement configuration and atomic `0600` persistence**

```go
type Config struct {
    ManagementURL string `yaml:"managementUrl"`
    CACertificate string `yaml:"caCertificate"`
    WorkspacePath string `yaml:"workspacePath"`
    AgentID        string `yaml:"agentId"`
    Credential     string `yaml:"credential"`
    Development    bool   `yaml:"development"`
}
```

Write credentials through a restricted temporary file, `fsync`, and atomic rename. Production requires `https` and a readable CA certificate.

- [ ] **Step 4: Implement Linux identity and enrollment client**

Hash `/etc/machine-id`; discover the primary route IP/MAC; send version and hardware summary; persist credentials only after a complete successful response. Redact Authorization and registration token values from all errors.

- [ ] **Step 5: Run `go test ./...`, cross-build, and commit**

```bash
git add .
git commit -m "feat: bootstrap secure processing agent enrollment"
```

### Task 9: Go Metrics Collectors And Heartbeat Runtime

**Files:**
- Create: `internal/collect/collector.go`
- Create: `internal/collect/system.go`
- Create: `internal/collect/system_test.go`
- Create: `internal/collect/nvidia.go`
- Create: `internal/collect/nvidia_test.go`
- Create: `internal/collect/docker.go`
- Create: `internal/collect/docker_test.go`
- Create: `internal/runtime/agent.go`
- Create: `internal/runtime/agent_test.go`
- Modify: `internal/client/client.go`
- Modify: `cmd/xkp-agent/main.go`

- [ ] **Step 1: Write collector and runtime tests with fakes**

Verify system and workspace disks are distinct, missing GPU/Docker become stable collector errors, one collector failure does not suppress others, sequence increments, boot ID changes per process, successful heartbeats reset backoff, duplicate acknowledgements do not lose sequence, and cancellation stops heartbeat and long poll promptly.

- [ ] **Step 2: Run tests and verify red**

- [ ] **Step 3: Implement collector interfaces using proven libraries**

Use `gopsutil/v4`, `github.com/NVIDIA/go-nvml`, and the official Docker client. Normalize percentages to `[0,100]`, keep byte counts as unsigned values validated before JSON encoding, and never shell out to parse human-readable commands.

- [ ] **Step 4: Implement five-second heartbeat and capped jittered retry**

The scheduler collects concurrently with per-collector timeouts, posts one combined snapshot, and uses management acknowledgements to advance. Network failures retry with capped exponential backoff and jitter; collector errors remain data, not transport failures.

- [ ] **Step 5: Implement authenticated empty command polling**

Run a separate cancellable loop with a 25-second server wait and the same credential. Reject any non-empty command in this phase with a safe unsupported-command result rather than executing it.

- [ ] **Step 6: Run race-enabled tests, Linux build, and commit**

```bash
go test -race ./...
GOOS=linux GOARCH=amd64 go build -o dist/xkp-agent-linux-amd64 ./cmd/xkp-agent
git add .
git commit -m "feat: report processing server metrics"
```

### Task 10: Ubuntu systemd Installation

**Files:**
- Create: `deploy/xkp-agent.service`
- Create: `deploy/install.sh`
- Create: `deploy/verify.sh`
- Create: `deploy/uninstall.sh`
- Create: `deploy/tests/test-static.sh`
- Create: `README.md`

- [ ] **Step 1: Write failing deployment static tests**

Assert the unit restarts on failure, uses `/etc/xkp-agent/agent.yml`, enables filesystem hardening compatible with Docker/NVML access, never embeds a token, creates config mode `0600`, and refuses non-Ubuntu/non-amd64 production installation.

- [ ] **Step 2: Run deployment tests and verify red**

- [ ] **Step 3: Implement idempotent install and verification**

The installer takes explicit management URL, CA path, registration token, display name, and workspace path. It installs the binary, writes bootstrap config without echoing secrets, starts systemd, waits for enrollment, and removes the registration token from persisted state after success.

- [ ] **Step 4: Implement conservative uninstall**

Stop and disable the service, remove the binary/unit, and preserve `/etc/xkp-agent/agent.yml` unless `--purge-identity` is explicitly supplied and confirmed.

- [ ] **Step 5: Run static tests, build release artifacts, and commit**

```bash
git add deploy README.md
git commit -m "feat: install processing agent as system service"
```

### Task 11: Cross-Repository Integration Contract

**Files:**
- Create: `xkp5-platform/integration/agent/fake-agent.ps1`
- Create: `xkp5-platform/integration/agent/test-agent-flow.ps1`
- Create: `xkp5-platform/integration/agent/README.md`
- Create: `xkp-agent/testdata/heartbeat-v1.json`
- Create: `xkp5-platform/java/match-mgr/src/test/resources/agent/heartbeat-v1.json`
- Create: `xkp5-platform/java/match-mgr/src/test/java/com/match/agent/model/AgentFixtureContractTest.java`

- [ ] **Step 1: Add a shared heartbeat fixture and failing Java contract test**

The fixture includes full metrics, nullable GPU, collector errors, boot ID, sequence, and version. Java strict deserialization must accept the shared fixture and reject unknown fields.

- [ ] **Step 2: Run contract test red, implement exact JSON compatibility, then green**

- [ ] **Step 3: Add the fake-Agent flow**

The PowerShell flow requests or accepts a one-time token, registers, sends heartbeats, repeats a sequence, advances sequence, waits for offline derivation, reconnects, and verifies disable behavior without requiring an Ubuntu GPU host.

- [ ] **Step 4: Run the flow against the local Docker management stack**

Expected: registration succeeds once, duplicate token fails, duplicate heartbeat does not add a sample, online/offline/reconnect states match 15-second policy, and disabled Agent receives `403`.

- [ ] **Step 5: Commit both repository sides**

Platform commit: `test: verify processing agent integration flow`.
Agent commit: `test: publish processing agent protocol fixtures`.

### Task 12: Documentation And Final Verification

**Files:**
- Modify: `xkp5-platform/README.md`
- Create: `xkp5-platform/docs/agents/operator-guide.md`
- Create: `xkp5-platform/docs/agents/administrator-guide.md`
- Modify: `xkp-agent/README.md`

- [ ] **Step 1: Document internal CA, fixed IP, enrollment, monitoring, disable, recovery, and certificate renewal**

Instructions distinguish Windows development from Ubuntu production and never advise exposing Docker TCP or storing plaintext Agent credentials on the management server.

- [ ] **Step 2: Run complete platform verification**

Run all Java tests, all frontend navigation/Agent/license tests, Vue production build, Compose production/offline config validation, CA tests, and offline static tests. Record exact test counts and existing non-blocking warnings.

- [ ] **Step 3: Run complete Agent verification**

Run `go test -race ./...`, Linux amd64 build, Windows amd64 cross-build, deployment static tests, `go vet ./...`, and verify release hashes.

- [ ] **Step 4: Perform Ubuntu acceptance when a processing server is available**

Verify systemd restart, TLS IP SAN validation, Docker metrics, RTX 2080 NVML metrics, five-second online appearance, 15-second offline transition, reconnect, and root-only configuration. If no Ubuntu server is connected, report this acceptance item as pending rather than simulating success.

- [ ] **Step 5: Commit documentation separately in both repositories**

Platform commit: `docs: add processing agent operations guide`.
Agent commit: `docs: add processing agent deployment guide`.

- [ ] **Step 6: Request code review and resolve all Critical and Important findings**

Review both repository diffs against the approved design, rerun affected tests after fixes, and preserve the platform worktree for the next phase unless the user explicitly chooses integration.
