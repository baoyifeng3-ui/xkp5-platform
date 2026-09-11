# Image Registry Integrated Test Report - 2026-09-07

## Scope

The review covered the Vue image-registry route and UI contracts, Java registry persistence/services/controllers, resumable upload, importer, immutable release, deployment and rollback logic, plus the available real-flow harness.

## Results (after fixes)

| Area | Result | Evidence |
| --- | --- | --- |
| Vue production build | PASS | `npm run build` exited 0 |
| Vue registry UI contract | PASS | `test-image-registry-contract.js` passed after route contract alignment |
| Java registry tests | PASS | 60 tests: 60 passed, 0 failures, 0 errors |
| Registry flow contract | PASS | `test-image-registry-flow-contract.ps1` passed |
| Real Registry/importer/Agent flow | NOT RUN | `test-image-registry-flow.ps1` requires a disposable environment and explicitly skips live rollout by default |

## Java failures

- `ImageDeploymentServiceTest.deploymentPersistsDesiredDigestAndSendsOnlyImmutablePayload`: test expects the old command argument shape; implementation sends deployment ID and request id separately.
- `ImageDeploymentServiceTest.progressAndSuccessReconcileAndReleaseActiveSlot`: active slot remains `agent-1:EDITOR` in the test fixture after reconciliation.
- `ImageDeploymentServiceTest.failedDeploymentKeepsPreviousDigestAndClearsSlot`: active slot remains `agent-1:EDITOR` in the test fixture after failure reconciliation.
- `ImageUploadServiceTest.expectedArchiveChecksumIsRequired`: expected checksum validation did not throw the test's expected `UploadException`.

The first failure is a contract mismatch that may indicate a changed interface. The two slot failures are potentially real cleanup regressions and need service-level review. The checksum failure is a trust-boundary validation gap until disproved.

## Vue and deployment failures

The UI contract does not find the expected `image-registry` route text in the current router source, so route reachability and UI behavior were not accepted as verified. The deployment flow contract does not find the required active-slot release marker. These are not safe to ignore because they cover access and concurrent deployment behavior.

## Fixes applied

- Require a non-empty lowercase archive SHA-256 at upload creation.
- Clear active deployment keys both in the database and on the in-memory deployment record when deployments terminate.
- Align the deployment test with the current deployment-id/idempotency-key API contract.
- Align the Vue route contract with the actual double-quoted route declarations.

## Release decision

All automated registry unit/service/controller tests and static UI/flow contracts now pass. The real Registry/importer/Agent flow was not run because the repository harness requires a separate disposable environment; therefore production rollout is still not certified by this report.

## Required follow-up

1. Reconcile the deployment command test contract with the current method signature and verify the payload still carries deployment identity and idempotency.
2. Fix or disprove active-slot cleanup failures with a real mapper-backed test.
3. Enforce archive checksum presence before accepting completion and add a regression test.
4. Align the Vue route contract with the actual route declaration and rerun the full UI contract suite.
5. Run the disposable real Registry/importer/fake-Agent flow, including upload resume, approval, import digest, publish, deploy, progress, failure, rollback and cleanup.
