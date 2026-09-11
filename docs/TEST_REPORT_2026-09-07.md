# Test Report - 2026-09-07

## Scope

This report covers the platform repository, the sibling `xkp-agent` repository, Vue contract checks, Java tests, Python tests, and deployment checks available in the current Windows environment.

## Results

| Area | Result | Evidence |
| --- | --- | --- |
| Vue production build | PASS | `npm run build` exited 0; webpack emitted asset-size warnings |
| Vue contract tests | FAIL | 15 scripts failed; see below |
| Python tests | PASS | `5 passed` |
| Java tests | NOT RUN | Maven is not installed (`mvn` command not found) |
| Agent tests | FAIL | package build failures plus 5 failing tests |
| Deployment shell tests | NOT RUN | WSL could not start `/bin/bash` |
| Git whitespace check | PASS | `git diff --check` exited 0 |

### Vue failures

`test-account-environment-creation-contract.js`, `test-agent-navigation.js`, `test-agent-view-contract.js`, `test-competition-admin-cleanup-contract.js`, `test-competition-environment-access-contract.js`, `test-competition-mode-contract.js`, `test-dashboard-overview-contract.js`, `test-image-batch-deployment-contract.js`, `test-image-registry-contract.js`, `test-license-navigation.js`, `test-participant-preview-contract.js`, `test-platform-ui-contract.js`, `test-resource-spaces-contract.js`, `test-role-navigation.js`, and `test-root-terminal-contract.js`.

The failures are contract mismatches in the current source, not transient test infrastructure errors.

### Agent failures

- `cmd/xkp-agent` cannot build because `internal/upgrade` has no files for the current Windows build constraints.
- `internal/power` tests reference missing `NewLinuxController`.
- `internal/collect` tests reference missing `collect` and `parseNvidiaSMI` symbols.
- Credential-file permission test observed mode `666` instead of owner-only permissions.
- Four container tests fail because the expected TLS files do not exist on this Windows host.

## Release decision

Formal archival and a `5.0.2` tag are blocked. The release plan requires all component and deployment gates to pass; the evidence above does not satisfy that gate. No release tag or archive was created.

## Next required checks

1. Install Maven and a Linux shell/WSL environment, then rerun Java and deployment suites.
2. Resolve the 15 Vue contract mismatches and rerun every Vue script.
3. Repair Agent build/test compatibility and rerun `go test ./...` in its supported target environment.
4. Build and verify a clean candidate from reviewed commits before assigning a SemVer and creating the offline archive.
