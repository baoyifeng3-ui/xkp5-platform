# Stable Candidate and Versioned Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabilize the current platform as a tested candidate, then assign `5.0.2` to the exact tested Java, Vue, Agent, database, template, installer, and offline-package artifacts.

**Architecture:** Preserve the current `5.0.1` runtime as rollback state while a candidate stack runs under a unique Compose project and network. Freeze only reviewed source changes, build every application component from that source, verify platform and Agent compatibility, and promote the unchanged candidate image digests to `5.0.2` only after all gates pass.

**Tech Stack:** Git, Bash, PowerShell, Docker Engine/Compose, Spring Boot, Vue 2, Go, MySQL, SHA256

**Spec:** `docs/superpowers/specs/2026-09-07-versioned-release-baseline-design.md`

## Global Constraints

- Failed candidates use `candidate-<platform-short-sha>-<UTC timestamp>` and never consume a SemVer.
- The first passing candidate becomes `5.0.2`; later fixes increment PATCH.
- Formal releases must rebuild Java, Vue, and Agent and must not use `--reuse-images`.
- Do not delete the `5.0.1` images or upgrade backup until `5.0.2` passes rollback verification.
- Do not commit JARs, logs, `dist`, local certificates, temporary Dockerfiles, or image archives.
- Do not expose passwords, tokens, private keys, or connection strings in manifests or logs.

---

### Task 1: Freeze a reviewable candidate source snapshot

**Files:**
- Modify: `.gitignore`
- Create: `scripts/check-release-source.ps1`
- Test: `scripts/test-release-source.ps1`

**Interfaces:**
- Consumes: the current `ui-redesign-live` working tree and sibling `xkp-agent` repository
- Produces: a clean release-source check and two immutable Git commits used by later builds

- [ ] **Step 1: Write the failing source-hygiene test**

```powershell
$script = Join-Path $PSScriptRoot 'check-release-source.ps1'
if (-not (Test-Path $script)) { throw 'release source checker is missing' }
& $script -PlatformRoot (Split-Path $PSScriptRoot) -AgentRoot (Resolve-Path (Join-Path $PSScriptRoot '..\..\xkp-agent'))
if ($LASTEXITCODE -ne 0) { throw 'release source checker rejected the candidate' }
```

- [ ] **Step 2: Run the test and confirm it fails because the checker does not exist**

Run: `pwsh -File scripts/test-release-source.ps1`

Expected: non-zero exit with `release source checker is missing`.

- [ ] **Step 3: Implement the minimum checker**

Create `scripts/check-release-source.ps1` so it rejects tracked or untracked paths matching:

```text
*.jar
*.log
dist/
runtime/
*.tar
*.tar.gz
*.pem
*.key
Dockerfile.overlay
```

It must also reject unresolved merge entries and print only offending paths, never file contents.

- [ ] **Step 4: Extend `.gitignore` only for confirmed generated artifacts**

Add the exact generated patterns found by the checker. Do not ignore source, migrations, tests, deployment scripts, or documentation.

- [ ] **Step 5: Review and stage candidate source by subsystem**

Run `git status --short`, then stage Java/Vue source, migrations, deployment files, direct tests, and current documentation explicitly. Leave unexplained files unstaged. Run `git diff --cached --check` and inspect `git diff --cached --stat` before committing.

- [ ] **Step 6: Commit platform and Agent snapshots**

Commit the platform candidate on `release/5.0.2-candidate` and confirm the Agent repository is clean at commit `9567027` or commit its reviewed changes separately. Record both full SHAs for Task 2.

- [ ] **Step 7: Re-run the source-hygiene test**

Run: `pwsh -File scripts/test-release-source.ps1`

Expected: exit 0 and no offending paths.

---

### Task 2: Add one version manifest consumed by every component

**Files:**
- Create: `release/version.env`
- Create: `deploy/tests/test-version-manifest.sh`
- Modify: `docker/java/Dockerfile`
- Modify: `docker/vue/Dockerfile`
- Modify: `deploy/quick-offline-release.sh`
- Modify: `deploy/offline/verify.sh`

**Interfaces:**
- Consumes: `XKP_BUILD_VERSION`, platform SHA, Agent SHA, and UTC build time
- Produces: `/app/version.env` in Java, `/usr/share/nginx/html/version.env` in Vue, and matching offline release metadata

- [ ] **Step 1: Write the failing manifest contract**

The shell test must assert that `release/version.env` contains exactly these public keys:

```text
XKP_BUILD_VERSION
XKP_PLATFORM_GIT_COMMIT
XKP_AGENT_GIT_COMMIT
XKP_BUILD_CREATED_AT
XKP_DATABASE_SCHEMA_VERSION
```

It must also assert both Dockerfiles copy the manifest and that the offline verifier compares the installed manifest with both running image labels.

- [ ] **Step 2: Run the test and verify the missing manifest assertions fail**

Run: `bash deploy/tests/test-version-manifest.sh`

Expected: non-zero exit naming `release/version.env`.

- [ ] **Step 3: Add candidate metadata and Docker labels**

Use build arguments in both Dockerfiles:

```dockerfile
ARG XKP_BUILD_VERSION
ARG XKP_PLATFORM_GIT_COMMIT
ARG XKP_AGENT_GIT_COMMIT
ARG XKP_BUILD_CREATED_AT
LABEL com.xkp.version=$XKP_BUILD_VERSION \
      com.xkp.platform-commit=$XKP_PLATFORM_GIT_COMMIT \
      com.xkp.agent-commit=$XKP_AGENT_GIT_COMMIT \
      com.xkp.created-at=$XKP_BUILD_CREATED_AT
```

Generate `release/version.env` from the build command; never place secrets in it.

- [ ] **Step 4: Make formal releases reject image reuse**

Change `deploy/quick-offline-release.sh` so `--reuse-images` is accepted only when the version begins with `candidate-`. A numeric SemVer combined with `--reuse-images` must exit non-zero before any build or service change.

- [ ] **Step 5: Verify the manifest contract**

Run: `bash deploy/tests/test-version-manifest.sh` and `bash deploy/tests/test-quick-deployment-static.sh`

Expected: both exit 0.

---

### Task 3: Fail fast on incompatible Agent and TLS host state

**Files:**
- Modify: sibling `xkp-agent/internal/container/docker.go`
- Modify: sibling `xkp-agent/internal/container/docker_test.go`
- Modify: sibling `xkp-agent/deploy/verify.sh`
- Modify: `java/match-mgr/src/main/java/com/match/environment/service/TrainingEnvironmentService.java`
- Test: `java/match-mgr/src/test/java/com/match/environment/service/TrainingEnvironmentServiceTest.java`

**Interfaces:**
- Consumes: Agent version, code-server TLS directory, template image digest, and environment command
- Produces: deterministic preflight failures before Docker creates a broken model container

- [ ] **Step 1: Write the failing Agent TLS preflight test**

Add a Go test where `code-cert-key.pem` is a directory. Calling the real Docker configuration preflight must return an error containing `code-cert-key.pem must be a regular file` and must not return container mount arguments.

- [ ] **Step 2: Run the focused Go test and verify it fails for the missing check**

Run: `go test ./internal/container -run TestPreflightRejectsTLSDirectory -count=1`

Expected: FAIL because the current code accepts the directory path.

- [ ] **Step 3: Implement regular-file checks**

Before constructing mounts, use `os.Stat` for `code-cert.pem` and `code-cert-key.pem`; require `Mode().IsRegular()`. Preserve clear error messages and do not delete or replace host paths automatically.

- [ ] **Step 4: Write the failing platform compatibility test**

Add a Java service test proving environment creation rejects an online Agent below the template's minimum Agent version and returns `处理服务器 Agent 版本不兼容，请先升级` before inserting an operation.

- [ ] **Step 5: Run the focused Java test and verify it fails**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=TrainingEnvironmentServiceTest test`

Expected: FAIL because no minimum-version guard exists.

- [ ] **Step 6: Implement the minimum compatibility guard**

Compare normalized three-part numeric versions without adding a dependency. Reject missing or older versions before allocating slots, ports, or commands.

- [ ] **Step 7: Run focused Agent and Java tests**

Run: `go test ./internal/container -count=1` in the Agent repository, then `mvn -f java/match-mgr/pom.xml -Dtest=TrainingEnvironmentServiceTest test`.

Expected: both exit 0.

---

### Task 4: Reconcile stuck environment operations safely

**Files:**
- Modify: `java/match-mgr/src/main/java/com/match/environment/service/EnvironmentOperationReconciler.java`
- Modify: `java/match-mgr/src/main/java/com/match/environment/persistence/EnvironmentOperationMapper.java`
- Modify: `java/match-mgr/src/main/java/com/match/environment/persistence/TrainingEnvironmentMapper.java`
- Test: `java/match-mgr/src/test/java/com/match/environment/service/EnvironmentOperationReconcilerTest.java`

**Interfaces:**
- Consumes: active operations, command terminal state, Agent heartbeat age, and operation update time
- Produces: terminal `DEGRADED` state with a reason code for stale operations

- [ ] **Step 1: Write a failing stale-operation test**

Create a real service test where an environment remains `STARTING`, its command is terminal `FAILED`, and its operation is older than the configured timeout. One reconciliation pass must clear `current_operation_id`, clear the active operation key, set `actual_state=DEGRADED`, and retain `desired_state=RUNNING`.

- [ ] **Step 2: Run the test and verify the environment remains stuck**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=EnvironmentOperationReconcilerTest test`

Expected: FAIL on the expected `DEGRADED` state.

- [ ] **Step 3: Implement the scheduled reconciliation branch**

Reuse existing command-result reconciliation. Add only the missing query for stale active operations and compare-and-set update so concurrent Agent completion wins safely. Do not automatically delete user data or containers.

- [ ] **Step 4: Add a second test for a late successful Agent result**

Prove a successful completion received before the compare-and-set update keeps the environment `RUNNING` and is not overwritten by the stale-operation pass.

- [ ] **Step 5: Run the reconciler tests**

Run: `mvn -f java/match-mgr/pom.xml -Dtest=EnvironmentOperationReconcilerTest test`

Expected: exit 0.

---

### Task 5: Build and start an isolated candidate stack

**Files:**
- Create: `deploy/build-candidate.ps1`
- Create: `deploy/tests/test-build-candidate.ps1`
- Modify: `compose.prod.yml`

**Interfaces:**
- Consumes: clean platform and Agent commits plus existing non-secret local environment file
- Produces: candidate-tagged Java/Vue images and one isolated `xkp5-candidate` Compose stack

- [ ] **Step 1: Write the failing build-script contract**

The PowerShell test must assert the script rejects a dirty release source, generates a candidate ID from Git SHA plus UTC time, passes all version build arguments, uses `--project-name xkp5-candidate`, and never tags `latest`.

- [ ] **Step 2: Run the test and verify it fails because the script is absent**

Run: `pwsh -File deploy/tests/test-build-candidate.ps1`

Expected: non-zero exit naming `deploy/build-candidate.ps1`.

- [ ] **Step 3: Implement the candidate builder**

The script must call the Task 1 source checker, back up MySQL, build Java/Vue without cache reuse, build the Agent binary from its recorded commit, and write `dist/candidate/current/MANIFEST.txt` plus `dist/candidate/current/candidate.env` containing public SHAs, image names and image digests.

- [ ] **Step 4: Add explicit image variables to Compose**

Use `${MATCH_JAVA_IMAGE:?Set MATCH_JAVA_IMAGE}` and `${MATCH_VUE_IMAGE:?Set MATCH_VUE_IMAGE}` for candidate and production deployment. Remove implicit application `latest` selection from the release path.

- [ ] **Step 5: Build candidate images**

Run: `pwsh -File deploy/build-candidate.ps1 -EnvFile .env.local`

Expected: Java/Vue builds exit 0, Agent tests exit 0, and the manifest records exact digests.

- [ ] **Step 6: Start the isolated stack and validate configuration**

Run `docker compose --project-name xkp5-candidate --env-file dist/candidate/current/candidate.env -f compose.prod.yml config`, then run the same command with `up -d`. Confirm only the candidate Vue exposes the candidate test port and that `java` resolves uniquely inside its network.

---

### Task 6: Run the release gate and promote unchanged artifacts

**Files:**
- Create: `deploy/verify-candidate.ps1`
- Create: `deploy/tests/test-verify-candidate.ps1`
- Modify: `deploy/offline/verify.sh`
- Modify: `TODO.md`
- Append: `docs/CHANGELOG.md`

**Interfaces:**
- Consumes: candidate manifest and running isolated stack
- Produces: pass/fail report; on success only, `5.0.2` tags and offline release inputs

- [ ] **Step 1: Write the failing release-gate contract**

The PowerShell test must assert the verifier checks health, image labels, CORS, login, role navigation, license request/download/import endpoints with test credentials, Agent heartbeat/version, TLS preflight, and the complete temporary competition-environment lifecycle. It must assert promotion is unreachable after any failed check.

- [ ] **Step 2: Run the test and verify it fails because the verifier is absent**

Run: `pwsh -File deploy/tests/test-verify-candidate.ps1`

Expected: non-zero exit naming `deploy/verify-candidate.ps1`.

- [ ] **Step 3: Implement the verifier using existing scripts and APIs**

Reuse Vue contract scripts, Maven tests, Go tests, `deploy/offline/verify.sh`, and existing fake-Agent flows. Add one real temporary environment flow against a dedicated test account and test Agent; always stop and delete that temporary environment in a `finally` block.

- [ ] **Step 4: Run all static and component checks**

Run all `vue/package.json` test scripts, `npm run build`, focused Maven suites plus `mvn test`, `go test ./...`, deployment shell tests, and candidate configuration validation.

Expected: every command exits 0. Any failure returns to the relevant earlier task without assigning `5.0.2`.

- [ ] **Step 5: Run the real candidate verification**

Run: `pwsh -File deploy/verify-candidate.ps1 -Manifest dist/candidate/current/MANIFEST.txt`

Expected: one report with every gate marked PASS, including create/start/access/stop/restore/delete for the temporary competition environment.

- [ ] **Step 6: Promote digests without rebuilding**

Tag the exact passing candidate Java and Vue digests as `5.0.2`. Build the versioned Agent binary from the already tested commit and verify its SHA256 matches the candidate manifest. Only then move `latest` to the same image digests.

- [ ] **Step 7: Build and verify the offline package**

Add `--candidate-manifest dist/candidate/current/MANIFEST.txt` to the offline release builder. This mode may bundle only the image digests recorded by the passing verifier; it must reject missing, changed, or unverified digests and must not rebuild or select `latest`. Run it for `5.0.2`, inspect `release.env`, run inner `sha256sum -c SHA256SUMS`, verify the installer outer SHA256, and confirm all component SHAs match the promoted manifest.

- [ ] **Step 8: Verify rollback before switching the main ports**

Start the retained `5.0.1` stack against the upgrade backup, confirm health, stop it, then switch `19140`, `19141`, and `19443` to `5.0.2`. Re-run the full candidate verifier against the final ports.

- [ ] **Step 9: Record the release**

Update `TODO.md` with the running version and remaining work. Append `5.0.2` to `docs/CHANGELOG.md` with platform commit, Agent commit, image digests, migration version, offline archive SHA256, verification timestamp, and rollback backup path.
