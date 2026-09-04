# Code-Server TLS and Model Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Secure code-server HTTPS with a platform CA and per-agent leaf certificates, expose only the root certificate for download, and place model configuration files in the T100 utility root.

**Architecture:** The management host owns the root CA and signs a leaf pair for each processing-agent IP. Agent runtime code mounts only its local leaf pair into EDITOR containers. A protected Java endpoint streams only the root certificate. The existing Agent model deployment command retains model files in `models/A` and places configuration files in `utils_x86`.

**Tech Stack:** Bash/OpenSSL, Java Spring Boot, Go Docker SDK, Vue 2, Node contract tests, Go tests.

**Spec:** `docs/superpowers/specs/2026-09-05-code-server-tls-design.md`

## Global Constraints

- Root CA private key remains only on the management host with mode `0600`.
- Agent hosts receive only their own leaf certificate and private key with mode `0600` for the private key.
- EDITOR containers receive the agent leaf pair by read-only bind mount only.
- Never expose or mount the root CA private key or management SSH private key in code-server.
- Model files stay in `/usr/local/zy-T100/utils_x86/models/A`; configuration files go to `/usr/local/zy-T100/utils_x86`.
- Do not delete MySQL, Registry, or FastDFS volumes during deployment.

---

### Task 1: Model Deployment Configuration Path

**Files:**
- Modify: `xkp-agent/internal/modeldeploy/manager.go`
- Modify: `xkp-agent/internal/modeldeploy/manager_test.go`
- Modify: `xkp5-platform/vue/src/components/training/ModelDeploymentDialog.vue`

**Interfaces:**
- Consumes: `protocol.ModelWorkspacePayload` with `ModelPath`, `ConfigPath`, and `Overwrite`.
- Produces: an Agent shell command copying the model to `models/A` and config to the utility root.

- [x] **Step 1: Write the failing test**

Assert the expected command includes:

```go
"'/usr/local/zy-T100/utils_x86/models/A/a model.onnx'"
"'/usr/local/zy-T100/utils_x86/a.json'"
```

- [x] **Step 2: Run test to verify it fails**

Run: `go test ./internal/modeldeploy`

Expected: FAIL because the existing command targets `models/A/a.json`.

- [x] **Step 3: Write minimal implementation**

```go
const configTargetRoot = "/usr/local/zy-T100/utils_x86"
configTarget := path.Join(configTargetRoot, path.Base(p.ConfigPath))
```

Update the dialog copy to name both directories.

- [x] **Step 4: Run test to verify it passes**

Run: `go test ./internal/modeldeploy`

Expected: PASS.

### Task 2: Platform Code-Server CA Lifecycle

**Files:**
- Create: `deploy/code-server-ca.sh`
- Create: `deploy/tests/test-code-server-ca.sh`
- Modify: `deploy/quick-install.sh`
- Modify: `deploy/offline/build-release.sh`
- Modify: `deploy/quick-offline-release.sh`

**Interfaces:**
- Consumes: management host CA directory and a processing-agent IPv4 address.
- Produces: `rootCA.pem`, `ca.key`, and `agents/<agent-id>/code-cert.pem`, `code-cert-key.pem`.

- [ ] **Step 1: Write the failing shell test**

```bash
"$repo_root/deploy/code-server-ca.sh" --ca-dir "$tmp/ca" --agent-id agent-1 --agent-ip 10.247.115.42
openssl verify -CAfile "$tmp/ca/rootCA.pem" "$tmp/ca/agents/agent-1/code-cert.pem"
openssl x509 -in "$tmp/ca/agents/agent-1/code-cert.pem" -noout -ext subjectAltName | grep -q 'IP Address:10.247.115.42'
[[ "$(stat -c '%a' "$tmp/ca/ca.key")" == 600 ]]
```

- [ ] **Step 2: Run test to verify it fails**

Run: `bash deploy/tests/test-code-server-ca.sh`

Expected: FAIL because `deploy/code-server-ca.sh` does not exist.

- [ ] **Step 3: Implement the CA script**

Generate `ca.key` and `rootCA.pem` only when absent; generate a fresh 825-day leaf pair for the supplied IP and write it under the stable agent-id directory. Validate IPv4 octets, agent-id basename safety, and file permissions.

- [ ] **Step 4: Initialize CA during platform installation**

Call the script once from `quick-install.sh` with `--ca-dir /etc/xkp/code-server-tls`; do not pass an agent until it is registered.

- [ ] **Step 5: Package the script in both release paths**

Install `deploy/code-server-ca.sh` into offline staging alongside `agent-ca.sh`.

- [ ] **Step 6: Run lifecycle verification**

Run: `bash deploy/tests/test-code-server-ca.sh`

Expected: PASS; repeated leaf generation preserves the root certificate fingerprint.

### Task 3: Agent Leaf Certificate Provisioning and Read-Only Mount

**Files:**
- Modify: `xkp-agent/internal/config/config.go`
- Modify: `xkp-agent/internal/container/docker.go`
- Modify: `xkp-agent/internal/container/docker_test.go`
- Modify: `xkp-agent/internal/container/validator.go`
- Modify: `xkp-agent/deploy/install.sh`
- Modify: `xkp5-platform/java/match-mgr/src/main/java/com/match/agent/service/AgentPackageService.java`
- Modify: `xkp5-platform/java/match-mgr/src/main/java/com/match/agent/service/RemoteAgentDeploymentService.java`

**Interfaces:**
- Consumes: agent-local `/etc/xkp-agent/code-server-tls/code-cert.pem` and `code-cert-key.pem`.
- Produces: EDITOR Docker binds ending in `:ro` at `/root/.config/code-cert.pem` and `/root/.config/code-cert-key.pem`.

- [ ] **Step 1: Write the failing Docker config test**

```go
assertBind(t, hostConfig.Binds, "/etc/xkp-agent/code-server-tls/code-cert.pem:/root/.config/code-cert.pem:ro")
assertBind(t, hostConfig.Binds, "/etc/xkp-agent/code-server-tls/code-cert-key.pem:/root/.config/code-cert-key.pem:ro")
assertNotContains(t, hostConfig.Binds, "ca.key")
assertNotContains(t, hostConfig.Binds, "id_ed25519")
```

- [ ] **Step 2: Run test to verify it fails**

Run: `go test ./internal/container`

Expected: FAIL because EDITOR has only its workspace bind.

- [ ] **Step 3: Implement agent-local TLS configuration**

Add a validated absolute `CodeServerTLSDir` configuration field, defaulting to `/etc/xkp-agent/code-server-tls`; require only the leaf pair for EDITOR creation. Append the two explicit read-only binds only for `component.ComponentType == "EDITOR"`.

- [ ] **Step 4: Implement secure leaf delivery**

Extend the management-side Agent deployment package to generate a leaf for `serverIp`, include only `code-cert.pem` and `code-cert-key.pem`, and have `install.sh` create the agent TLS directory with `0700`, copy the files, and apply `0644`/`0600` modes. Do not package `ca.key` or `rootCA.pem`.

- [ ] **Step 5: Run Agent tests**

Run: `go test ./internal/container ./internal/config ./internal/modeldeploy`

Expected: PASS.

### Task 4: Root Certificate Download Endpoint and Training UI

**Files:**
- Create: `java/match-mgr/src/main/java/com/match/environment/web/CodeServerCertificateController.java`
- Create: `vue/scripts/test-code-server-ca-download-contract.js`
- Modify: `vue/src/views/user/TrainingEnvironment.vue`
- Modify: `vue/src/views/user/CoursePlatform.vue`

**Interfaces:**
- Produces: `GET /user/code-server/root-ca.pem`, requiring `roleGuard.requireUser()`, returning `application/x-pem-file` attachment `rootCA.pem`.
- Consumes: the endpoint through a browser download link or Blob download helper.

- [ ] **Step 1: Write the failing contract test**

```js
assert.match(controller, /@GetMapping\("\/root-ca\.pem"\)/)
assert.match(controller, /requireUser\(\)/)
assert.match(controller, /attachment; filename="rootCA\.pem"/)
assert.match(training, /download.*root.*ca/i)
assert.match(course, /download.*root.*ca/i)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node vue/scripts/test-code-server-ca-download-contract.js`

Expected: FAIL because no endpoint or UI action exists.

- [ ] **Step 3: Implement the protected download**

Read only `/etc/xkp/code-server-tls/rootCA.pem` through an injected property, reject missing/non-regular files, and stream it as a download. Add a small shared Vue method that creates an authenticated Axios Blob and an object URL. Render the button in both course workspace and standalone training pages.

- [ ] **Step 4: Run contract test and Vue build**

Run: `node vue/scripts/test-code-server-ca-download-contract.js && npm --prefix vue run build`

Expected: PASS; build may retain existing bundle-size warnings but no compilation error.

### Task 5: Release and Production Verification

**Files:**
- Modify: `compose.offline.yml`
- Modify: `compose.prod.yml`
- Modify: `deploy/offline/verify.sh`

**Interfaces:**
- Java receives only `/etc/xkp/code-server-tls/rootCA.pem:ro` for downloads; it never receives `ca.key`.

- [ ] **Step 1: Write static deployment checks**

```bash
assert_java_mounts_root_ca_only
assert_no_compose_value_matches 'code-server-tls/ca.key|id_ed25519|id_rsa'
```

- [ ] **Step 2: Run checks to verify they fail**

Run: `bash deploy/offline/tests/test-static.sh`

Expected: FAIL until the root certificate mount is explicit and private-key mounts are rejected.

- [ ] **Step 3: Implement Compose and verification changes**

Bind-mount only `rootCA.pem` read-only into Java. Keep CA private key host-only. Add `verify.sh` checks for the root certificate and require active-agent certificate SAN inspection through the Agent host.

- [ ] **Step 4: Build and deploy**

Build Agent binary and Java/Vue images with a unique release label. Before replacement, tag the current Java and Vue images with a rollback label. Transfer release artifacts, update only `java`, `vue`, and the Agent package, and never run a volume deletion command.

- [ ] **Step 5: Verify production**

Run:

```bash
docker compose -f /opt/xkp5-platform/compose.offline.yml ps
curl -fsS http://127.0.0.1:19140/
curl -fsS -o /dev/null -w '%{http_code}' http://127.0.0.1:19141/login
openssl s_client -connect AGENT_IP:EDITOR_PORT -servername AGENT_IP </dev/null 2>/dev/null | openssl x509 -noout -ext subjectAltName
```

Expected: all seven services healthy, front-end HTTP 200, login HTTP 200, and each code-server certificate SAN contains its agent IP.

