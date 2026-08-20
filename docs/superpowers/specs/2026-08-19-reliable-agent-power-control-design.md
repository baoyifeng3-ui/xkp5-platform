# Reliable Agent Power Control Design

## 1. Scope

This increment adds a durable management-to-Agent command channel and the first
two processing-server power operations:

- Wake-on-LAN sent by the management server;
- authenticated shutdown executed by the processing Agent.

Normal administrators and super administrators may use power controls while the
license is active. When the license is unusable, normal administrators remain
read-only while super administrators retain power control for maintenance and
recovery. Normal users receive no power APIs.

Container create, restore, start, and stop commands are deliberately excluded.
They require the template, environment-pair, port-allocation, and workspace model
from the next increment. Remote terminal sessions are also excluded because they
need a separate streaming protocol, session limits, and explicit operator
confirmation design.

## 2. Command Safety Model

The Agent never executes an arbitrary command string. The protocol uses a closed
command type enum. This increment contains only `SHUTDOWN_SERVER`; unknown types,
unknown fields, invalid payloads, and unsupported platforms fail closed.

Delivery is at least once until the Agent acknowledges that execution has
started. A command progresses through:

```text
PENDING -> LEASED -> RUNNING -> SUCCEEDED | FAILED
                  \-> PENDING (lease expired before RUNNING)
```

Once a command reaches `RUNNING`, it is never automatically redelivered. This is
critical for shutdown: the machine may power off before sending a final result.
The management server reconciles a running shutdown as successful only when the
Agent becomes offline within the confirmation window. If it remains online after
the deadline, the command fails visibly and an administrator may retry.

Every delivery carries a random lease token. Start and result acknowledgements
must match the current token. Duplicate terminal acknowledgements are idempotent;
stale or foreign tokens return a conflict without mutating state.

## 3. Persistence

`processing_agent_command` stores:

- command UUID, Agent UUID, closed command type, and versioned JSON payload;
- state, requester user ID and role, correlation ID, request and expiry times;
- lease token, lease expiry, attempt count, delivery and start times;
- completion time, stable result code, bounded result message, and result JSON.

Indexes support polling by Agent/state/availability, management history by Agent,
and cleanup by completion time. Foreign keys are not added because Agent removal
is soft and command/audit history must remain available.

The existing Agent audit table records request, delivery, start, completion,
lease expiry, Wake-on-LAN send, and authorization failure using stable action and
reason codes. Credentials and command lease tokens are never written to audit or
ordinary logs.

## 4. Management APIs

Agent protocol endpoints:

- `GET /agent/v1/commands/poll?waitSeconds=0..25` leases at most one command;
- `POST /agent/v1/commands/{commandId}/start` records the matching lease as
  `RUNNING`;
- `POST /agent/v1/commands/{commandId}/result` records a bounded structured
  success or failure result.

Administrator endpoints:

- `POST /admin/processing-agents/{agentId}/wake` sends the magic packet;
- `POST /admin/processing-agents/{agentId}/shutdown` creates one pending shutdown
  command and returns its command view;
- `GET /admin/processing-agents/{agentId}/commands` returns recent command state.

Only one non-terminal shutdown command may exist per Agent. Repeated shutdown
requests return the existing command rather than creating duplicates.

## 5. Wake-on-LAN

The management server sends the standard six `0xFF` bytes followed by sixteen
copies of the registered six-byte MAC address over UDP port 9. The broadcast
address is configuration, defaulting to `255.255.255.255`, because deriving a
broadcast address from an IP without a subnet mask is unsafe. The platform and
processing servers are assumed to share one LAN as already approved.

Wake-on-LAN reports only that the packet was sent. It never claims the server is
online. Heartbeats remain the authoritative online signal.

## 6. Agent Execution

The Go runtime separates protocol parsing, command dispatch, and operating-system
power control:

- strict JSON decoding rejects unknown or oversized command content;
- a dispatcher accepts only supported command type/version pairs;
- Linux power control executes `systemctl poweroff` with direct argument passing,
  never a shell;
- non-Linux builds return `UNSUPPORTED_PLATFORM` and remain usable for protocol
  development tests.

The Agent posts `start` before invoking shutdown. If the process survives, it
posts the direct result. If poweroff succeeds and terminates the machine, the
management reconciler uses the loss of heartbeat as confirmation.

## 7. License And Role Rules

An active license permits normal and super administrators to wake or shut down a
processing server. An unusable license rejects normal-administrator mutations.
Super administrators retain these two power operations for maintenance; this
does not permit any environment start, create, restore, or training command.

Agent monitoring and command result reporting continue regardless of license
state. A disabled or removed Agent cannot poll or acknowledge commands.

## 8. Failure Handling

- Offline shutdown requests fail before queueing; Wake-on-LAN remains available.
- Disabled or removed Agents reject new commands.
- Lease expiry before `RUNNING` returns the command to `PENDING` with a bounded
  attempt count; exhaustion marks it failed.
- Database errors never acknowledge an uncommitted lease or result.
- Result messages are length-limited and treated as plain text.
- Wake packet errors return a stable failure code and create a failure audit.
- Management UI shows requested, delivered, running, succeeded, failed, and
  waiting-for-offline-confirmation states without optimistic success.

## 9. Verification

Java tests cover migration shape, atomic leasing, duplicate request idempotency,
lease-token conflicts, result idempotency, shutdown offline reconciliation, role
and license rules, magic packet bytes, and controller responses.

Go tests cover strict command JSON, unknown-command rejection, start-before-
execute ordering, direct Linux power invocation through a fake runner, result
reporting, cancellation, and non-Linux failure behavior. Cross-repository fixtures
lock the version-one command and acknowledgement JSON.

The integration flow queues a shutdown against a fake Agent, verifies lease and
start acknowledgement, simulates heartbeat loss, and observes management-side
success. Real Wake-on-LAN and shutdown acceptance remain pending until an Ubuntu
processing server is available.
