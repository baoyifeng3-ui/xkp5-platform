# XKP5.0 Platform Design

## 1. Objective

Build XKP5.0 as an independent platform repository based on the existing Vue 2
and Spring Boot competition system. The original `competition` repository is a
read-only reference for migration and must not receive XKP5.0 commits.

The platform is split into a management server and one or more processing
servers. Processing servers run training and competition containers. The
management server owns identity, business configuration, orchestration,
monitoring, licensing, and audit records.

## 2. Repository And Delivery

- Repository directory: `xkp5-platform`
- Default branch: `main`
- The existing `competition` repository remains separate.
- The management application reuses the current Vue 2 and Spring Boot code.
- The processing-server Agent is a Go binary managed by `systemd`.
- Processing servers target Ubuntu 22.04 on amd64 with Docker, NVIDIA drivers,
  NVIDIA Container Toolkit, and an RTX 2080 GPU.

## 3. System Architecture

### 3.1 Management Server

- Vue 2 management and user interfaces
- Spring Boot API and orchestration service
- MySQL business, monitoring, license, and audit data
- LAN-private Docker image registry
- Wake-on-LAN sender
- Agent registration and authenticated command channel
- Web terminal gateway for super administrators

### 3.2 Processing Server

- Go Agent installed as a system service
- Docker and container runtime integration
- NVIDIA NVML monitoring and CUDA MPS control
- Local environment workspaces
- Training and competition container pairs
- Encrypted, authenticated connection to the management server

The Docker API is never exposed directly over the LAN. The Agent is the only
component allowed to translate approved management commands into local Docker
operations.

## 4. Roles And Interfaces

### 4.1 Super Administrator

The super administrator uses a separate operations workspace and is not used
for daily business administration. It provides:

- Add, edit, disable, and remove processing servers
- Create one-time Agent registration tokens
- Container and environment-template management
- Image and private-registry management
- Browser-based remote terminal sessions
- Platform license import and status
- Destructive maintenance operations and audit inspection

The existing `admin` account becomes the first super administrator.

### 4.2 Normal Administrator

The normal administrator manages daily business operations:

- Full dashboard shortcuts and platform overview
- Course, resource, training, competition, and user management
- View processing-server and container status
- Wake processing servers and request controlled shutdown
- Restore containers while preserving workspace data
- Switch normal and competition modes

Normal administrators cannot add, edit, or remove processing servers and cannot
change low-level environment templates or platform licenses.

### 4.3 Normal User

The normal user has a separate interface with only:

1. Course Platform
2. Resource Center
3. Training Environment

Normal users consume assigned environments and do not receive infrastructure
or container-management controls.

## 5. Management Navigation

The normal-administrator navigation is:

1. Home
2. Course Management
3. Resource Management
4. Training Management
5. Competition Management
6. User Management
7. Device Management

Competition Management contains:

1. Competition Preview
2. Competition Control
3. Schedule and Rules Editor
4. Paper Questions
5. Paper Grading
6. Competition Accounts
7. Competition Devices
8. Platform Settings

Competition Preview reuses the current participant-facing Home, Schedule and
Rules, Current Paper, and Result Verification without changing their behavior.
Competition Devices becomes status-only for normal administrators; server and
port configuration moves to the super-administrator operations workspace.

## 6. Dashboard

The normal-administrator dashboard includes:

- One-click class startup
- Competition-mode switch
- Course-platform entry
- Training-environment entry
- Platform overview
- Online processing-server count
- Running environment count
- Online user count
- Alerts and pending work
- CPU, GPU, memory, and disk utilization summaries

## 7. Agent Registration And Monitoring

- A super administrator generates a one-time registration token.
- The Agent submits the token and hardware identity on first startup.
- The management server issues a per-Agent credential after registration.
- Each Agent reports live status every five seconds.
- One-minute metric aggregates are retained for 30 days.
- Metrics include online state, CPU, GPU, GPU memory, RAM, disk, Docker state,
  environment state, and container state.
- Offline Agents are explicitly shown as offline; commands never report success
  before the Agent confirms execution.

## 8. Power And Remote Access

- Management and processing servers are on the same LAN and subnet.
- Wake-on-LAN uses the registered MAC and broadcast address.
- Shutdown is an authenticated Agent command.
- Normal administrators may wake and shut down processing servers.
- Super administrators retain all power controls.
- Remote access is a super-administrator-only browser terminal created through
  the Agent, with action-time confirmation, idle timeout, one active session per
  server, and session metadata auditing.
- Terminal password contents are not recorded.

## 9. Environment Model

Each processing server provides at most four user slots. Every environment is a
pair:

1. Image annotation container
2. Code editing container

Each normal user owns one competition environment and may own multiple course
training environments. Only one training environment per user is active at a
time.

### 9.1 Normal Mode

- Competition containers remain stopped.
- A user selects a course through One-click Class.
- The previously active training pair is stopped.
- The selected course's training pair is started.
- Workspace data remains on the processing server.

### 9.2 Competition Mode

- The system records which training environments were running.
- All training containers are stopped and locked from restart.
- The four competition environment pairs are started.
- Exiting competition mode stops competition containers and restores the
  previously running training environments.

Mode transitions are idempotent and report per-server failures. Partial
transitions remain visible and retryable instead of being reported as complete.

## 10. Container Templates

The initial annotation template is based on:

```text
image: zy-anno
runtime: sysbox-runc
container port: 8080
restart policy: always
workspace mount: host workspace -> /root/data
```

The initial code-editor template is based on:

```text
image: zy-contestv2
runtime: nvidia
container ports: 9090, 8887, 5000
restart policy: always
workspace mount: host workspace -> /home/student/data
command: /bin/bash
```

The super administrator sets CPU, memory, GPU policy, GPU compute limit, GPU
memory limit, host ports, workspace root, restart policy, image version, and
container name when creating or editing a template. Defaults may be saved for
reuse. Resource and port conflicts prevent container creation.

Each environment pair mounts the same host directory at its respective
container path. Default layout:

```text
/home/zyadmin/xkp-data/
  competition/slot-{1..4}/
  training/{user-id}/{course-id}/
```

Container restore recreates the two containers but preserves the host
workspace. A separate super-administrator-only clear operation removes the
containers and workspace after explicit confirmation. The platform provides no
central workspace storage, backup, or migration feature.

## 11. Ports And Slots

Default slot ports are generated from template bases:

| Slot | Annotation | Editor | Auxiliary | T100 |
| --- | ---: | ---: | ---: | ---: |
| 1 | 8081 | 9091 | 8881 | 5001 |
| 2 | 8082 | 9092 | 8882 | 5002 |
| 3 | 8083 | 9093 | 8883 | 5003 |
| 4 | 8084 | 9094 | 8884 | 5004 |

Training and competition environments may reuse a slot's ports because the two
modes cannot run simultaneously.

## 12. GPU Policy

RTX 2080 does not support MIG. GPU sharing uses NVIDIA CUDA MPS under Linux.
`xkp-train` submits training commands without requiring changes to training
source code and applies the selected execution policy:

- Exclusive: one task at 100 percent
- Balanced: two tasks at up to 50 percent each; default
- Four-user: four tasks at up to 25 percent each after workload validation

Templates declare expected GPU memory. MPS applies compute and device-memory
limits. A task starts immediately when its policy fits current capacity and
otherwise waits. Exceeding a configured memory limit produces a clear CUDA
out-of-memory result with retained logs. Code editing and annotation remain
available while a GPU task waits.

## 13. Private Registry

The management server provides a private Docker registry on the LAN. Super
administrators manage approved annotation, editor, training, and competition
image versions. Agents pull only approved template images and report pull and
verification progress.

## 14. Licensing

The platform imports an offline, digitally signed license containing customer,
platform, validity, capacity, module, version, issuer, and signature data.
Only a super administrator may import a license.

On expiry:

- All training and competition containers are stopped.
- New container start, create, restore, training, and mode-switch operations are
  blocked.
- Agents continue monitoring and remain manageable for license recovery.
- Normal administrators may sign in read-only and inspect history and status.
- Normal users see a license-expired state.
- Super administrators can import a replacement license.
- Renewing a license never auto-starts containers; an administrator restores
  service deliberately.

License checks are enforced by both the management server and Agent command
validation so that an expired management API cannot start containers indirectly.

## 15. Audit And Safety

Audit records cover actor, role, target, command, request time, confirmation,
Agent result, failure reason, and correlation identifier. High-impact operations
require explicit confirmation. Secrets, license signing keys, terminal input,
and Agent credentials are never written to ordinary application logs.

## 16. Delivery Phases

1. Independent repository, migrated baseline, roles, and navigation
2. Go Agent registration, monitoring, and metric history
3. Device power, restore operations, and remote terminal
4. Private registry, templates, slots, containers, and workspaces
5. Training and competition mode orchestration plus `xkp-train` and MPS
6. Signed offline licensing and expiry enforcement

Each phase receives its own implementation plan, tests, deployment verification,
and acceptance before the next phase starts.

## 17. Verification Strategy

- Unit tests for roles, license state, port allocation, mode transitions, Agent
  authentication, idempotency, and GPU scheduling
- Integration tests against disposable Docker containers and a fake Agent
- Agent tests for metrics, Docker operations, command authorization, and failure
  reporting
- End-to-end tests for all three role-specific interfaces
- Failure tests for offline Agents, partial mode transitions, port conflicts,
  resource exhaustion, expired licenses, and interrupted terminal sessions
- Desktop and mobile visual checks for navigation, dashboards, status tables,
  dialogs, and error states
