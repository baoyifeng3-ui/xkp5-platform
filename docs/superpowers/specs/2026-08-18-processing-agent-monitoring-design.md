# Processing Agent Registration And Monitoring Design

## 1. Scope

This phase adds processing-server registration, authenticated Agent connectivity,
live infrastructure monitoring, and 30-day metric history to XKP5.0. It produces
a runnable Ubuntu Agent and management-server APIs and pages that show real Agent
data.

This phase does not implement Wake-on-LAN, shutdown, container creation, restore,
mode switching, CUDA MPS scheduling, image management, or remote terminals. It
defines an authenticated command-poll endpoint that returns no commands so later
phases can extend the protocol without changing Agent enrollment.

## 2. Repository Boundaries

- `xkp5-platform` remains the independent management-server repository.
- A new independent repository named `xkp-agent` contains the Go Agent, Ubuntu
  installation scripts, systemd unit, and Agent tests.
- The original `competition` repository remains read-only reference material.
- Windows is a development and cross-build host. The Agent production target is
  Ubuntu 22.04 amd64.

## 3. Connection Architecture

Every management server and processing server has a fixed address on the same
LAN. The Agent initiates all network connections. Processing servers do not expose
Docker Engine or an Agent management API to the LAN.

The Agent uses two authenticated HTTPS flows:

1. A five-second heartbeat posts hardware, service, and resource state.
2. A long poll asks for approved commands. During this phase it receives an empty
   command list.

Later phases add power, container, and terminal commands to the same long-poll
contract. A management request is not considered successful until its Agent result
is received; queue insertion alone is never reported as successful execution.

## 4. Internal TLS Authority

Public internet certificates and DNS are not required. Production deployment
creates an internal certificate authority and issues an Agent API server
certificate containing the management server's fixed IP address as an IP SAN.

- Nginx exposes a dedicated Agent TLS listener on port `19443` and proxies only
  `/agent/v1/` to Spring Boot on the private Compose network.
- The browser application continues to use the existing `19140` and `19141`
  behavior and never connects to the internal Agent listener.
- The Agent receives the internal CA public certificate during installation and
  verifies both its trust chain and the configured management IP.
- The internal CA remains stable while the server certificate is renewed, so
  certificate renewal does not require Agent re-registration.
- The CA private key is stored in a root-owned `0600` directory on the management
  server and is included in the operator's offline security backup. It is never
  exposed through the application or copied to processing servers.
- Local development may explicitly enable HTTP. Production configuration rejects
  insecure Agent URLs.

## 5. Enrollment And Credentials

A super administrator creates a registration token from the operations workspace.
The token is random, has at least 256 bits of entropy, expires after ten minutes,
and can be consumed once.

The Agent installer is given:

- the fixed management-server Agent URL;
- the internal CA public certificate;
- the one-time registration token;
- a requested display name.

On first startup, the Agent submits the token with its Linux machine ID digest,
hostname, primary IP, MAC address, Agent version, and hardware summary. The
management server checks the platform license, server capacity, token expiry,
token consumption state, and machine identity uniqueness in one transaction.

Successful registration returns an Agent UUID and a random 256-bit Agent
credential. The Agent stores them in `/etc/xkp-agent/agent.yml`, owned by root with
mode `0600`. The server stores only a SHA-256 credential digest. Subsequent calls
use the credential as a bearer token over the internal TLS connection.

Registration failure never writes a partial Agent credential. A disabled Agent
keeps its local identity but receives `403`; re-enrollment requires a new token.
Removing an Agent invalidates its credential while retaining audit records.

## 6. Management Data Model

New tables are separate from the legacy competition `training_server` model.

### `processing_agent`

Stores Agent identity and latest state:

- Agent UUID, display name, machine identity digest, hostname, primary IP, and MAC;
- Agent version, enabled flag, credential digest, registration and update times;
- last heartbeat time, boot ID, accepted heartbeat sequence, and online derivation;
- Docker state and version;
- latest CPU, RAM, GPU, GPU memory, system disk, and workspace disk values;
- environment count, running-container count, and collection-error JSON.

An Agent is online when enabled and its last accepted heartbeat is no more than
15 seconds old. Online state is derived from time and is not persisted as a flag.

### `processing_agent_registration_token`

Stores token digest, creator, creation time, expiry time, consumption time, and
the registered Agent UUID. Plain registration tokens are returned once and never
stored.

### `processing_agent_metric_minute`

Stores one row per Agent per UTC minute with sample count, average and maximum
CPU/RAM/GPU/GPU-memory utilization, average and maximum disk utilization, Docker
availability, and running environment/container counts. A scheduled cleanup removes
rows older than 30 days.

### `processing_agent_audit`

Stores registration-token creation, registration, disable, enable, remove, and
authentication failures with actor, target, correlation ID, result, and timestamp.
Credentials and registration tokens are never included.

## 7. Heartbeat Contract

Each heartbeat contains:

- Agent UUID, boot ID, monotonically increasing sequence, timestamp, and version;
- CPU utilization;
- total, used, and utilization values for RAM;
- GPU model, utilization, temperature, total/used memory, and collector state;
- system-disk and configured-workspace-disk totals and utilization;
- Docker daemon state and version;
- environment and container state summaries;
- individual collector failures using stable error codes.

The management server accepts a heartbeat only when its boot ID is new or its
sequence is greater than the last accepted sequence for the same boot ID. Duplicate
and out-of-order requests return the current acknowledgement without changing
metrics. The latest state update and minute-bucket aggregation are transactional.

Raw five-second samples are not retained. Minute rows are updated atomically with
running averages and maxima, preventing concurrent heartbeats from losing samples.

## 8. Agent Collectors And Runtime

The Agent uses focused collector interfaces so unavailable subsystems do not stop
the process:

- `gopsutil` provides CPU, RAM, and filesystem metrics.
- NVIDIA NVML provides RTX 2080 GPU and GPU-memory metrics.
- The official Docker Go client provides daemon and container state.
- A configurable workspace path selects the data-exchange filesystem to monitor.

Missing NVIDIA drivers, an unavailable Docker daemon, or a missing workspace mount
produces a collector-specific unavailable state. The heartbeat still succeeds and
the management UI raises an alert for that subsystem.

The Agent runs under systemd with automatic restart, a dedicated unprivileged
service identity where possible, and only the Docker/NVML/filesystem permissions
needed by its collectors. It logs structured operational events without credentials.

When the management server cannot be reached, heartbeat and command polling use
capped exponential backoff with jitter. A successful request resets the backoff.

## 9. License Behavior

- Creating or consuming a new registration token requires an active license.
- Registration rejects a processing-server count above the licensed maximum.
- Existing Agents continue heartbeat monitoring when the platform license is
  expired or invalid so operators can diagnose and recover the platform.
- An unusable license causes the command poll to return no start/create/restore
  work. This phase returns no operational commands in every license state.
- Renewing a license never starts any environment automatically.

## 10. Management APIs And Roles

The Agent API under `/agent/v1` provides registration, heartbeat, and command poll.
Registration uses a one-time token; all other Agent endpoints use the Agent
credential.

The super-administrator API provides:

- list and inspect Agents;
- create and list registration-token metadata;
- enable, disable, and remove Agents;
- inspect latest metrics, history, collection errors, and Agent audit events.

The normal-administrator API provides read-only Agent lists, latest status,
metric history, and alerts. Normal administrators cannot create tokens or mutate
Agent records. Normal users receive no Agent APIs.

All timestamps are UTC over the wire and converted for display by the browser.

## 11. Management Interface

The existing operations workspace gains a processing-server page for super
administrators. It includes registration-token creation, server state, Agent
version, identity, latest resource values, collector errors, history, and
enable/disable/remove actions with confirmation.

The normal administrator's top-level Device Management page uses the same latest
status and history data in read-only form. The dashboard derives online-server
count, utilization summaries, running-environment count, and Agent alerts from the
same APIs.

This phase follows the existing XKP5.0 shell and prioritizes stable loading, empty,
offline, unavailable, and error states. The broader visual redesign remains a
separate later activity after the infrastructure workflows are connected.

## 12. Failure Handling

- Expired, consumed, or invalid registration tokens return stable error codes and
  do not reveal whether another machine consumed a token.
- Invalid Agent credentials return `401`; disabled Agents return `403`.
- Duplicate machine identity registration is rejected and points the operator to
  the existing Agent record.
- Individual collector failures preserve Agent online state and mark only the
  affected metric unavailable.
- Database failure rejects the heartbeat rather than acknowledging uncommitted
  state; the Agent retries with the same sequence.
- Clock values from an Agent are diagnostic only. Online and retention decisions
  use management-server time.
- Metric cleanup failure is logged and retried without affecting heartbeat writes.

## 13. Verification And Acceptance

Go tests cover configuration permissions, enrollment persistence, TLS trust,
heartbeat sequencing, retry behavior, collectors, Docker/NVML degradation, and
redaction. The Agent must cross-compile to Linux amd64 and Windows amd64; production
acceptance runs the Linux binary on Ubuntu.

Java tests cover migrations, token lifecycle, license capacity, credential
authentication, replay rejection, transactional aggregation, retention, offline
derivation, role authorization, and stable error responses.

Integration tests run a fake Agent against the management API and verify initial
registration, five-second heartbeats, one-minute aggregation, duplicate requests,
disconnect/offline transition, reconnect, disable, and expired-license monitoring.

Frontend tests cover role-specific navigation and the loading, empty, online,
offline, partial-metric, and error states. Production builds and existing platform
tests remain green.

Ubuntu deployment acceptance verifies internal CA generation, IP SAN validation,
Nginx Agent TLS routing, root-only Agent configuration, systemd startup/restart,
Docker and RTX 2080 metric collection, and reconnection after a management-server
restart.

The phase is accepted when a newly installed Ubuntu Agent registers once, appears
online within five seconds, records minute history, becomes offline within 15
seconds after stopping, returns online without re-registration, and is correctly
restricted by both role and license rules.
