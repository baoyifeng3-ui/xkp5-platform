# Training Environment Lifecycle Design

Date: 2026-08-19
Status: Approved for implementation
Parent design: `docs/specs/2026-08-18-xkp5-platform-design.md`

## 1. Goal

Deliver the first usable vertical slice for one training environment. A training
environment always consists of an image-annotation container and a code-editor
container on the same processing server. Both containers use one host workspace
for data exchange.

The slice covers template configuration and environment create, start, stop,
and restore operations from the management API through the durable Agent command
channel to a Docker executor. A fake executor is the initial acceptance target;
real Ubuntu and NVIDIA validation follows after this slice passes.

## 2. Scope

Included:

- Reusable, versioned annotation and editor container templates
- One training environment assigned to one user, course, server, and slot
- Deterministic container names, host ports, and shared workspace path
- Create, start, stop, restore, status, and command-history operations
- Role, license, server-state, port, path, and resource validation
- Idempotent Agent execution with component-level results
- Audit records for every requested operation
- Backend, Agent, contract, migration, and fake-executor integration tests

Deferred:

- Competition mode and four competition pairs
- CUDA MPS scheduling and real RTX 2080 workload validation
- Private registry user interface and image distribution
- Permanent workspace deletion
- Production UI redesign

## 3. Chosen Approach

The implementation extends the current durable Agent command channel with
strict, versioned environment commands. The management server remains the
source of truth; the Agent remains the only component that talks to Docker.

This vertical approach is preferred over completing all management APIs first
or exposing a generic Docker command API. It validates the cross-process
contract early and prevents arbitrary shell or Docker access from becoming a
platform feature.

## 4. Ownership And Security Boundary

### Management server

- Stores templates, environments, allocated ports, desired state, actual state,
  operations, and audit history.
- Resolves template versions into an immutable command payload.
- Enforces actor permissions, active license, Agent eligibility, one-active-
  training-environment-per-user, and port/resource constraints.
- Never sends shell text or an unrestricted Docker request.

### Processing Agent

- Accepts only known command types and version `1` payloads.
- Validates names, paths, runtime, images, mounts, ports, restart policy, and
  resource bounds again before calling the executor.
- Derives the effective workspace under its configured workspace root and
  rejects path traversal or arbitrary host mounts.
- Executes Docker operations through a narrow adapter that is replaceable by a
  fake in tests.
- Reports one overall result plus annotation/editor component results.

The Docker API is local to the processing server and is never exposed over the
LAN.

## 5. Data Model

### `container_template`

One row describes one component template:

- `template_id`, `name`, `component_type` (`ANNOTATION` or `EDITOR`)
- `version`, `enabled`, `image`, `runtime`, `restart_policy`
- typed container ports and mount target
- optional command and working directory
- CPU, memory, GPU, and GPU-memory limits
- creation/update actor and timestamps

Published versions are immutable. Editing a published template creates a new
version so existing environments remain reproducible.

### `training_environment`

- `environment_id`, `user_id`, `course_id`, `agent_id`, `slot_number`
- annotation and editor template-version references
- workspace relative path
- desired state and actual state
- component container names and last known states
- current operation identifier and optimistic-lock version
- creation/update actor and timestamps

The database prevents duplicate `(agent_id, slot_number)` assignments and
duplicate environment identity for a user/course assignment.

### `environment_port_allocation`

- environment, component, container port, host port, and protocol
- unique `(agent_id, host_port, protocol)` allocation

### `environment_operation`

- operation identifier, environment, operation type, actor, role
- command identifier, state, correlation identifier
- requested/start/finish timestamps
- overall and per-component result codes/messages

The existing Agent command table remains the transport ledger. Environment
operations reference it instead of duplicating leasing and retry logic.

## 6. Naming, Paths, And Defaults

Container names are deterministic and ASCII-only:

```text
xkp-train-{environment-id-short}-annotation
xkp-train-{environment-id-short}-editor
```

The management server stores only a relative workspace path:

```text
training/{user-id}/{course-id}
```

The Agent resolves it below its configured root, initially:

```text
/home/zyadmin/xkp-data/training/{user-id}/{course-id}
```

Both components mount that same host directory. The annotation target is
`/root/data`; the editor target is `/home/student/data`.

Initial component defaults preserve the supplied deployment behavior:

- Annotation: image `zy-anno`, runtime `sysbox-runc`, port `8080`
- Editor: image `zy-contestv2`, NVIDIA runtime, ports `9090`, `8887`, `5000`,
  memory `6g`, command `/bin/bash`

Host ports are template inputs selected by a super administrator. The platform
reserves them transactionally and rejects conflicts before creating a command.

## 7. Roles

- Super administrator: create/version/disable templates, assign environments,
  set ports and resource limits, and perform all lifecycle operations.
- Normal administrator: view environments and request start, stop, or restore.
  Cannot change templates, server assignments, ports, or resource limits.
- Normal user: view assigned environments and start the selected course through
  One-click Class. Cannot directly create, restore, or reconfigure containers.

## 8. State Model

Environment actual states:

```text
NEW -> CREATING -> STOPPED -> STARTING -> RUNNING
                    ^           |           |
                    |           v           v
                 RESTORING <- STOPPING <- DEGRADED
```

`DEGRADED` means the two components disagree or an operation partially failed.
It is visible and retryable. `ERROR` is reserved for invalid or unrecoverable
state that requires super-administrator action.

Only one mutating operation may own an environment at a time. The service uses
an optimistic version plus a database lock when switching a user's active
training environment.

## 9. Lifecycle Semantics

### Create

1. Validate role, active license, Agent, slot, template versions, ports, path,
   and resources.
2. Persist the environment and allocations in `CREATING` state.
3. Enqueue `CREATE_TRAINING_ENVIRONMENT` with both immutable component specs.
4. The Agent creates the workspace and both stopped containers.
5. Success produces `STOPPED`; partial success produces `DEGRADED`.

Creation does not start the containers.

### Start

1. Validate role, active license, enabled/online Agent, and environment state.
2. Lock all training environments for the user.
3. Stop the previously active environment if it differs from the selected one.
4. Enqueue `START_TRAINING_ENVIRONMENT` for the selected pair.
5. Mark `RUNNING` only after both containers are reported running.

Repeated start requests converge on `RUNNING` and do not create duplicates.

### Stop

Stop is permitted even when the platform license is expired, because it reduces
resource use and is required for safe license enforcement. The Agent stops both
components and reports `STOPPED` only when both are stopped. Repeated requests
are successful no-ops.

### Restore

1. Validate normal-admin or super-admin role and active license.
2. Stop both components.
3. Remove both containers without deleting the host workspace.
4. Recreate both containers from the environment's pinned template versions.
5. Leave the environment `STOPPED` after success.

Restore never deletes or clears the shared directory. Permanent clearing is a
separate future super-administrator operation with explicit confirmation.

## 10. Command Contract

Four new allowlisted command types use version `1`:

- `CREATE_TRAINING_ENVIRONMENT`
- `START_TRAINING_ENVIRONMENT`
- `STOP_TRAINING_ENVIRONMENT`
- `RESTORE_TRAINING_ENVIRONMENT`

Every payload contains `environmentId`, `operationId`, deterministic component
identities, desired state, and the fields required by that operation. Create and
restore include full immutable component specs. Start and stop contain identity
and expected configuration fingerprints, preventing an unrelated container
with the same name from being controlled.

The Agent stores operation results locally until the management server accepts
them. Re-delivery of the same operation identifier returns the stored result or
continues convergence; it never creates a second environment.

## 11. License Enforcement

Create, start, and restore require an active license at request time and again
when the command is leased. Stop always remains available.

The Agent also refuses create, start, and restore unless its most recent
authenticated management heartbeat grants operations and is fresh. This closes
the gap where an expired or disconnected management server could leave an old
start command executable. Stop bypasses this gate.

When the license expires, management enqueues stop operations for every active
training or competition environment. Renewal does not auto-start anything.

## 12. Failure And Recovery

- Offline Agent: operation remains queued and visible; it is not reported as
  successful.
- Port conflict discovered by Agent: fail before creating either component.
- First component succeeds, second fails: compensate by stopping/removing the
  newly created first component when safe, then report component details.
- Result delivery fails: retain and retry the same terminal result.
- Management restart: recover from durable operation and Agent command rows.
- Agent restart: recover idempotency records and inspect actual Docker state.
- Stale container fingerprint: reject the operation and require reconciliation.

Messages returned to normal users are concise. Full component diagnostics are
available to administrators and audit records, without credentials or secrets.

## 13. API Surface

Initial endpoints are grouped by responsibility:

- Super-admin template CRUD/version/enable endpoints
- Super-admin environment assignment/create endpoint
- Admin and super-admin environment list/detail/status endpoints
- Admin and super-admin start/stop/restore endpoints
- Normal-user assigned-environment list and one-click start endpoint
- Existing Agent poll/start/result endpoints carry the new command types

Mutating endpoints return an accepted operation view. Clients poll the operation
or environment status; they do not infer success from HTTP acceptance.

## 14. Verification And Acceptance

Implementation follows test-driven development. Required automated coverage:

- Flyway schema and mapper contract tests
- Template immutability and validation tests
- Role and license boundary tests for every operation
- Port allocation and one-active-environment concurrency tests
- Command JSON contract fixtures shared by Java and Go
- Go payload validation, path safety, idempotency, and fake-Docker tests
- Partial component failure and compensation tests
- Management restart and Agent result retry tests
- End-to-end fake-Agent create/start/stop/restore flow

This slice is accepted when one assigned environment can be created, started,
stopped, and restored end to end; both components share the same preserved host
workspace; all terminal states are confirmed by the Agent; and the complete
Java and Go test suites remain green.

Real-server acceptance is the next checkpoint and requires Ubuntu 22.04,
Docker, `sysbox-runc`, NVIDIA runtime, and the target images. It verifies the
actual mounts, ports, restart behavior, GPU access, and restore semantics.
