# Competition Mode Orchestration Design

Date: 2026-08-20

## 1. Purpose

XKP5 has two mutually exclusive participant experiences:

- `TRAINING`: course learning and training environments.
- `COMPETITION`: paper answering and, when assigned, competition annotation and
  code-editing environments.

The participant experience changes immediately at platform scope. Container
transitions converge asynchronously per processing Agent so one failed server
does not prevent all participants from answering papers.

This design adds a durable orchestration layer without refactoring the existing
training environment lifecycle. The existing Agent command lease, result, and
pair-container execution mechanisms remain the execution boundary.

GPU training submission and CUDA MPS scheduling are intentionally deferred to
later phases.

## 2. Confirmed Product Rules

1. Participant training and competition interfaces are completely isolated.
2. Every platform mode change invalidates existing `USER` sessions. Participants
   must sign in again and are routed to the interface for the current mode.
3. `ADMIN` and `SUPER_ADMIN` sessions are not invalidated by a mode change.
4. A processing server has at most four physical slots.
5. Competition mode starts containers only for slots currently bound to users.
   Unbound slots consume no competition container runtime resources.
6. A platform with no bound competition slots may still enter competition mode.
   Participants can answer papers but cannot enter a practical environment.
7. Competition containers are created before user binding. A slot cannot be
   bound unless its complete managed container pair exists and is stopped.
8. Entering competition mode records the training environments that were
   actually running. Exiting restores only that snapshot.
9. Processing-server transitions are durable, idempotent, visible, and
   retryable. A partial failure remains `DEGRADED`; completed steps are not
   automatically rolled back.
10. `ADMIN` initiates normal enter and exit operations. `SUPER_ADMIN` maintains
    infrastructure and may retry degraded transitions.

## 3. Two-Level State Model

### 3.1 Platform Mode

There is one authoritative platform mode row:

| Field | Meaning |
| --- | --- |
| `mode` | `TRAINING` or `COMPETITION` |
| `generation` | Monotonically increasing value incremented on every change |
| `changed_by` | Administrator user ID |
| `changed_at` | UTC change time |

The platform mode controls participant authentication, routing, and API access.
It does not claim that every processing server has completed its container
transition.

When an administrator confirms a change, the platform row and audit event commit
first. The participant interface changes immediately. Per-Agent transitions are
created and dispatched asynchronously after commit.

### 3.2 Processing Agent Mode

Each Agent has one durable mode state:

- `NORMAL`
- `ENTERING_COMPETITION`
- `COMPETITION`
- `EXITING_COMPETITION`
- `DEGRADED`

The row records desired mode, actual mode, active transition ID, lock version,
and timestamps. The Agent row is the concurrency boundary: only one mode
transition may be active for an Agent.

A server failure does not reverse the platform mode or block unrelated servers.
Its practical environments remain unavailable or partially available according
to their reported states, and administrators see the exact failed steps.

## 4. Persistence Model

### 4.1 Competition Environment

Add `competition_environment`, with at most one row per physical slot. It stores:

- environment, Agent, slot, and slot number identifiers;
- annotation and editor template IDs and immutable versions;
- workspace relative path;
- desired and actual pair state;
- annotation and editor container names and component states;
- configuration fingerprints;
- current operation ID, optimistic lock version, actor IDs, and timestamps.

Competition environments use `/competition/slot-{n}/` workspaces. They can exist
without a bound user. Their container pair is created and maintained by
`SUPER_ADMIN` before assignment.

### 4.2 Slot Binding

Make `processing_environment_slot.user_id` nullable. MySQL's unique key permits
multiple null values while continuing to enforce one user per Agent and one
binding per slot.

Binding is accepted only when all checks pass again inside the server
transaction:

- both managed containers exist;
- container identity labels refer to the same Agent, slot, and competition
  environment;
- stored and observed configuration fingerprints match;
- both component states are stopped;
- the slot has no other user;
- the user has no other competition slot on the same Agent;
- the platform is in `TRAINING` and the Agent has no active mode transition.

The binding view includes both container names, image/template versions,
fingerprints, ports, workspace, and observed states. If no complete competition
environment exists, the API returns a stable not-ready reason and the UI disables
binding. These checks are authoritative on the server, not only in Vue.

### 4.3 Transition Records

Add the following tables:

- `processing_agent_mode`: current desired and actual state per Agent.
- `mode_transition`: one enter, exit, or retry request with actor, target, state,
  failure summary, and timestamps.
- `mode_transition_step`: one durable environment action with phase, ordinal,
  command ID, idempotency key, state, result code, and component result.
- `mode_training_snapshot`: training environment IDs that were actually running
  when the enter transition was accepted.

Transition states are `PENDING`, `RUNNING`, `DEGRADED`, and `SUCCEEDED`. Step
states are `PENDING`, `DISPATCHED`, `SUCCEEDED`, and `FAILED`.

The competition environment IDs selected at entry are also frozen into steps.
Binding cannot change while the platform is in competition mode or while an
Agent transition is active.

## 5. Enter Competition Flow

1. `ADMIN` requests `COMPETITION` with explicit confirmation.
2. The service verifies the license permits a new mode switch.
3. In one transaction it locks the platform mode, changes the mode, increments
   `generation`, and writes the audit event.
4. After commit, all existing participant sessions become stale by generation.
5. The coordinator creates one transition for each enabled processing Agent.
6. For each Agent it locks mode and environment state, rejects conflicting
   environment operations, records all actually running training environments,
   and freezes the currently bound and ready competition environments.
7. Phase 1 dispatches `STOP_TRAINING_ENVIRONMENT` for the snapshot. All stop
   steps must succeed before Phase 2 begins because training and competition
   slots may reuse host ports.
8. Phase 2 dispatches `START_COMPETITION_ENVIRONMENT` only for frozen bound
   slots. Unbound slots produce no start step.
9. All steps succeeding changes the Agent state to `COMPETITION`. Any failed step
   changes it to `DEGRADED` and retains all step results.

Zero frozen competition environments is valid: the Agent can reach
`COMPETITION` after its training environments stop, while no practical slot is
advertised to participants.

## 6. Exit Competition Flow

1. `ADMIN` requests `TRAINING` with explicit confirmation.
2. The platform mode changes immediately, `generation` increments, and existing
   participant sessions become stale.
3. Each Agent receives a durable exit transition.
4. Phase 1 stops the competition environments frozen by the successful or
   degraded entry transition.
5. All competition stop steps must succeed before Phase 2 begins.
6. Phase 2 restores only training environments in the entry snapshot.
7. All steps succeeding changes the Agent state to `NORMAL` and releases mode
   locks. A failure leaves it `DEGRADED` with exact retryable steps.

Newly created or previously stopped training environments are never started by
exit restoration.

## 7. Failure, Retry, and Recovery

- A failed action is never reported as a completed mode transition.
- Successful steps are retained and not automatically reversed.
- Retry continues failed and pending steps in the current phase. It never skips
  a phase barrier.
- Stable command idempotency keys prevent duplicate execution after polling,
  lease expiry, management restart, or result-report retry.
- Before dispatch, reconciliation may confirm that the observed container pair
  already matches the target and complete the step without executing it again.
- Startup recovery scans non-terminal transitions, reconciles command results,
  and resumes eligible work using bounded batches.
- `DEGRADED` permits retry and infrastructure diagnostics only. It rejects an
  opposite transition until the current target is resolved.
- License expiry blocks new mode switches and retries that would start or restore
  containers. Safety stops remain permitted.

No automatic rollback is performed. This avoids repeated container churn and
preserves a truthful record of partial progress.

## 8. Commands and Agent Boundary

Add competition command types alongside the existing training commands:

- `CREATE_COMPETITION_ENVIRONMENT`
- `START_COMPETITION_ENVIRONMENT`
- `STOP_COMPETITION_ENVIRONMENT`
- `RESTORE_COMPETITION_ENVIRONMENT`

They use the same strict, bounded environment payload and pair-container
executor. The Agent remains unaware of global platform mode; it validates and
executes one idempotent pair action at a time and reports component results.

Management owns ordering, phase barriers, snapshots, access control, and retry.
This retains fine-grained auditability and avoids an opaque compound switch
command.

## 9. Authentication and API Isolation

At participant login, the response includes platform `mode` and `generation`.
The participant token session stores that generation. Every authenticated
participant request compares it with the current value.

On mismatch, the server invalidates the token and returns a stable unauthorized
response indicating that platform mode changed. The Vue client clears local
authentication and returns to login.

Server guards enforce both directions:

- competition paper and practical APIs reject participant access in `TRAINING`;
- training environment start and restore APIs reject participant access in
  `COMPETITION`;
- administrative and operational APIs retain role-based access independent of
  participant generation.

Hiding navigation items is not an authorization mechanism.

## 10. API and User Experience

`ADMIN` APIs provide:

- current platform mode and generation;
- enter and exit requests;
- Agent transition summaries and step detail;
- slot/container readiness and binding management.

`SUPER_ADMIN` APIs provide:

- competition environment create, restore, and inspection;
- degraded transition diagnostics and retry;
- exact Agent command and component results.

The management UI shows the global mode first, followed by per-server progress.
Partial failures remain prominent and actionable.

Participant routing is mode-specific:

- `TRAINING` uses the course and training layout.
- `COMPETITION` uses the paper and competition layout.

In competition mode, an unbound participant can answer papers normally. The
practical section states that no environment is assigned and exposes no editor
or annotation link. A bound participant receives links only when the assigned
competition pair is confirmed running. During exit restoration, the training UI
shows environment recovery progress rather than claiming readiness.

## 11. Concurrency and Transaction Boundaries

- Platform mode changes use row locking and optimistic generation checks.
- Agent transitions use a unique active-transition key and lock version.
- Binding uses row locks on Agent, slot, user binding, and competition
  environment records.
- Command creation and step dispatch state commit atomically.
- Command completion reconciliation locks the step and transition before
  advancing a phase.
- Post-commit dispatch never makes a database transaction depend on network I/O.
- Scheduled recovery and interactive retries use the same transition service.

Concurrent duplicate requests return the existing active transition. Conflicting
targets return a stable conflict response.

## 12. Audit and Observability

Audit events include:

- platform mode requested and changed;
- participant session invalidation generation;
- competition environment create or restore;
- slot bind and unbind;
- Agent transition started, degraded, retried, and completed;
- per-step command result and stable failure code.

Logs and audit payloads contain identifiers and bounded result summaries only.
They must not include credentials, Agent bearer tokens, terminal tickets, or
participant answer content.

Dashboard summaries distinguish platform mode from Agent convergence and show
counts for normal, entering, competition, exiting, and degraded servers.

## 13. Test Strategy

Management tests cover:

- schema uniqueness, nullable binding, and migration compatibility;
- zero-binding entry with paper access;
- one or more bound slots starting only their own container pairs;
- accurate running-training snapshots and restore selection;
- stop-before-start and stop-before-restore phase barriers;
- partial failure, degraded visibility, retry, duplicate request, and restart
  recovery;
- license, role, online Agent, and active-operation gates;
- authoritative binding readiness and concurrent uniqueness checks;
- participant generation invalidation and bidirectional API isolation;
- transaction and after-commit dispatch boundaries.

Agent tests cover all competition command types through the same strict protocol,
idempotent pair execution, duplicate delivery, component failure, and retained
result reporting.

Vue contract and component tests cover mode-specific landing routes, forced
logout on generation mismatch, zero-binding paper access, practical readiness,
transition progress, and degraded retry controls.

An end-to-end test exercises one unbound participant, one bound participant, a
running training snapshot, enter, partial Agent failure and retry, competition
paper access, practical access only for the bound slot, exit, session
invalidation, and training restoration.

## 14. Delivery Sequence

1. Platform mode, participant generation guard, and isolated routing contract.
2. Competition environment persistence, creation, inspection, and binding.
3. Durable per-Agent transition state machine and management APIs.
4. Agent competition command protocol and execution.
5. Management, operations, and participant Vue integration.
6. Recovery, dashboard, audit, and end-to-end verification.
7. `xkp-train` GPU task submission in a separate design and plan.
8. CUDA MPS capacity and memory enforcement in a separate design and plan.

