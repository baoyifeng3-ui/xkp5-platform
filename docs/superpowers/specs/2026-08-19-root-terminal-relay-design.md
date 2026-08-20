# Root Terminal Relay Design

## Goal

Add a super-administrator-only browser terminal for maintaining enrolled Ubuntu
processing servers. Each session opens an interactive root `bash` through the
existing Agent trust relationship without exposing an inbound terminal port on
the processing server.

The terminal is an intentionally privileged maintenance surface. Its security
comes from narrow authorization, action-time confirmation, short-lived session
credentials, bounded lifetime, one active session per Agent, and deterministic
process cleanup. It does not attempt to restrict commands after the root shell
has started.

## Scope

This increment includes:

- one root `bash` PTY on an online Ubuntu processing Agent;
- an on-demand reverse WebSocket from the Agent to the management server;
- a browser WebSocket relayed through the management server;
- super-administrator session creation, status, connection-ticket, and close
  APIs;
- one active or connecting terminal session per Agent;
- a 10-minute idle timeout and a two-hour absolute lifetime;
- session metadata auditing without terminal-content recording;
- a full-screen terminal dialog in the processing-server operations page.

It excludes Windows Agents, non-root shells, multiple terminal tabs, session
reattachment, file upload or download, port forwarding, SSH key management,
terminal transcript storage, command filtering, and multi-node management-server
relay coordination.

## Architecture

The normal durable Agent command queue starts the terminal but never transports
terminal bytes. The management service creates a terminal session and queues one
closed `OPEN_ROOT_TERMINAL` version-one command. Its payload contains only the
session identifier, relay URL, Agent-connection deadline, idle timeout, and
absolute expiry. It never contains a connection ticket.

After strict command validation, the Agent acknowledges command start and uses
its Agent credential plus the current command lease to exchange the command for
a one-time Agent ticket over HTTPS. It then opens a root PTY running `/bin/bash`
and initiates an authenticated WSS connection back to the management server. The
browser independently obtains a one-time browser ticket and opens a WSS
connection to the same session. The management server matches the two peers and
relays frames in memory.

Processing Agents do not listen for terminal traffic. Their existing outbound
TLS trust and bearer credential remain the machine-authentication root. A
terminal ticket grants access only to one session and does not replace Agent
authentication.

The first release supports one management-server process. Active relay state is
not stored in Redis or another broker. A management-service restart terminates
all sessions and recovery marks unfinished database rows as abnormally closed.

## Session Lifecycle

The persisted state machine is:

```text
WAITING_AGENT -> WAITING_BROWSER -> ACTIVE -> CLOSED
            \-> FAILED          \-> CLOSED
```

1. A super administrator confirms the root warning and requests a session for an
   enabled, non-removed, currently online Agent.
2. In one transaction, the service enforces the per-Agent uniqueness constraint,
   creates `WAITING_AGENT`, queues the ticket-free terminal-open command, and
   writes the request audit event. The Agent must connect within 90 seconds.
3. The Agent consumes the command, acknowledges start, and requests its Agent
   ticket. The service generates the ticket, stores only its digest, and returns
   the plaintext once. A valid authenticated Agent WebSocket changes the session
   to `WAITING_BROWSER`; the browser must connect within another 60 seconds.
4. A valid browser WebSocket changes the session to `ACTIVE`. The UI must not
   claim success before both peers are attached.
5. Administrator close, either peer disconnecting, idle expiry, absolute expiry,
   relay backpressure, Agent shutdown, PTY exit, or service shutdown closes both
   sockets and cancels the PTY process group.
6. The terminal record reaches `CLOSED` for an expected end or `FAILED` when the
   session never became active. It records a stable reason code and bounded plain
   message.

Sessions are not resumed. A transient browser disconnect closes the root shell;
opening another shell requires a new confirmation and new tickets. Agent restart
or management-service restart has the same fail-closed behavior.

The idle clock advances on terminal input or output, not on WebSocket heartbeat
frames. This prevents an unattended browser tab from keeping a root shell alive.
The absolute two-hour deadline cannot be extended.

## Persistence And Concurrency

The platform adds a `processing_agent_terminal_session` table containing:

- session UUID, Agent UUID, requester user ID, and requester role;
- state and a nullable active-Agent uniqueness key;
- nullable Agent-ticket digest and browser-ticket digest with separate expiry and
  consumed timestamps;
- command correlation ID;
- requested, Agent-connected, browser-connected, active, last-I/O, and ended
  timestamps;
- absolute expiry, stable end reason, bounded end message, and inbound/outbound
  byte counters.

The active-Agent uniqueness key contains the Agent ID while a row is connecting
or active and becomes null only in a terminal state. A unique index makes the
one-session rule hold across concurrent requests and service instances even
though relay traffic itself is single-node.

Tickets use cryptographically secure random bytes. Only SHA-256 digests are
persisted. Each ticket expires after 60 seconds and is atomically consumed once;
reuse fails without revealing whether the original ticket was valid. Ticket
values, Agent bearer credentials, WebSocket authentication headers, and terminal
frames are excluded from application and access logs. Tickets never appear in a
URL or durable Agent command payload.

## Authentication And Authorization

Only `SUPER_ADMIN` may create, inspect, ticket, connect to, or close a terminal
session. Normal administrators and normal users receive forbidden responses even
if they know an Agent or session identifier. Route visibility is not treated as
authorization.

The browser cannot reliably set a bearer header during a WebSocket upgrade, so
it exchanges its authenticated Sa-Token session for a one-time terminal ticket
over HTTPS. The WebSocket endpoint accepts that ticket for exactly one session,
checks the configured management Origin, and consumes it during the upgrade.
The browser supplies the ticket through the WebSocket subprotocol field rather
than a URL query parameter.

The Agent upgrade requires both its normal bearer credential and its one-time
terminal ticket. The session Agent ID must match the authenticated Agent. A
disabled, removed, different, or offline Agent cannot attach.

Opening a terminal is allowed for super-administrator maintenance even when the
platform license is unusable, consistent with licensing recovery operations.
Terminal access does not create or start training environments and does not
weaken Agent operation-grant enforcement.

## Relay Protocol And Resource Limits

Terminal data uses binary WebSocket frames. UTF-8 interpretation and terminal
escape handling remain browser-terminal concerns; the relay treats output as
opaque bytes. Text control frames use a closed JSON schema with only:

- `resize`, carrying bounded positive columns and rows;
- `ping` and `pong`, carrying no terminal content;
- `close`, carrying a stable reason code.

Unknown fields, unknown message types, dimensions outside 20..500 columns or
5..200 rows, fragmented oversized messages, and trailing JSON close the session.
Each frame is limited to 64 KiB. Each peer has a 1 MiB send queue; sustained
backpressure closes the session instead of allowing unbounded memory growth. A
token bucket permits 2 MiB per second in each direction with an 8 MiB burst.
Control frames are excluded from the inactivity clock.

The relay is a dedicated component separate from REST controllers and terminal
persistence. It owns peer attachment, frame forwarding, deadlines, counters,
and idempotent close. Database updates occur on lifecycle boundaries and bounded
counter flushes, never once per keystroke.

## Agent PTY Execution

The Go Agent adds a terminal-session client separate from its durable command
dispatcher. The command dispatcher still strictly decodes the closed
`OPEN_ROOT_TERMINAL` payload, acknowledges start before execution, and hands the
validated request to the terminal manager.

The terminal-open command is asynchronous after acknowledgement. The terminal
manager owns its eventual command result and a separate minimal recovery record,
so a two-hour terminal does not block heartbeats or normal command polling. The
recovery record contains command, lease, and session identifiers plus deadlines,
but no tickets or terminal content. After an Agent restart it reports the old
terminal command as failed and never recreates the shell.

On Linux, the terminal manager starts `/bin/bash --noprofile --norc` in `/root`,
attached to a PTY with a new process group and an explicit environment containing
only safe fixed values such as `HOME`, `PATH`, `TERM`, `SHELL`, `USER`, `LOGNAME`,
and `LANG`. The systemd Agent is expected to run as root, so the child shell has
root privileges as approved. The Agent does not invoke `sh -c`, interpolate
command text, accept a requested executable, or expose an arbitrary environment
in the command payload.

Resize control frames update the PTY dimensions. PTY bytes and WebSocket bytes
are copied with bounded buffers. Any cancellation or peer failure sends a
graceful termination signal to the process group, waits for a short fixed grace
period, then force-kills the group and waits for child reaping. This cleanup also
runs during Agent shutdown.

Non-Linux builds reject terminal-open commands with a stable unsupported-platform
result. A second local terminal request is rejected while one PTY is connecting
or active, providing defense in depth in addition to the platform constraint.

## Management APIs And UI

The management service exposes:

- `POST /operations/processing-agents/{agentId}/terminal-sessions` to create a
  confirmed session;
- `GET /operations/terminal-sessions/{sessionId}` to read its state and deadlines;
- `POST /operations/terminal-sessions/{sessionId}/browser-ticket` to issue or
  rotate the one-time browser connection ticket before activation;
- `DELETE /operations/terminal-sessions/{sessionId}` to close it idempotently;
- `POST /agent/v1/terminal-sessions/{sessionId}/agent-ticket` for the authenticated
  Agent to exchange the matching running command lease for its one-time ticket;
- dedicated Agent and browser WebSocket upgrade paths under `/terminal/v1`.

The create request contains an exact confirmation value, not a client-controlled
shell command. The response returns session metadata and never returns the Agent
ticket. The browser ticket endpoint returns the plaintext ticket only in its
one successful HTTPS response.

The existing super-administrator processing-server page gains a terminal icon
for online, enabled Agents. Activating it opens a confirmation dialog that names
the server and states that the shell has root authority. After confirmation, a
full-screen terminal dialog shows server identity, connection state, root badge,
idle deadline, absolute deadline, and a close control.

The frontend uses a maintained terminal emulator library and its fit behavior.
It sends resize controls only after stable layout measurement. Connecting,
waiting for Agent, active, closing, closed, timeout, and failure states are
explicit. Leaving the dialog closes the session rather than leaving a detached
root process.

## Audit And Privacy

Audit events cover request accepted or denied, command delivery, Agent attach,
browser attach, activation, close request, timeout, connection loss, PTY exit,
and abnormal service recovery. Events contain actor, role, Agent, session,
timestamps, correlation ID, result code, and byte totals where available.

Terminal input, output, commands, prompts, passwords, environment contents, and
ticket material are never persisted or emitted to ordinary logs. Error messages
are stable and bounded so that library or shell errors cannot leak terminal
content. Operational metrics may report only active-session counts, durations,
byte totals, and close-reason counts.

## Failure Handling

- An offline, disabled, or removed Agent rejects session creation before a
  command is queued.
- A command lease or Agent-ticket expiry fails the waiting session and releases
  the Agent uniqueness key.
- Agent PTY or WSS startup failure produces a stable command result and closes
  the session.
- Browser-ticket expiry leaves the Agent peer alive only until the session
  connection deadline, then terminates the PTY.
- Duplicate close and repeated terminal Agent results are idempotent.
- A stale socket cannot attach after a session reaches a terminal state.
- Relay send-queue overflow, invalid frames, or excessive traffic closes both
  peers and records a stable policy reason.
- Startup recovery closes every persisted non-terminal session before accepting
  new terminal requests.

## Verification

Java tests cover migration shape, concurrent per-Agent uniqueness, ticket
digests and atomic consumption, ticket expiry and replay, role enforcement,
Agent identity matching, state transitions, restart recovery, timeout handling,
relay pairing, frame validation, backpressure, idempotent close, audit metadata,
and proof that terminal content is absent from persistence and logs.

Go tests cover strict terminal command decoding, unsupported platforms, single
local session, root `bash` argument construction, PTY resize, bidirectional relay,
idle and absolute cancellation, disconnect cleanup, process-group termination,
and bounded buffers. Tests use fake PTYs and WebSockets; no test opens an actual
privileged shell.

Frontend tests cover super-administrator-only visibility, confirmation, waiting
and active states, binary terminal transport, resize behavior, timeout display,
explicit close, disconnect handling, and dialog teardown. Final verification
runs the complete Java suite, Go race-enabled suite, frontend contract tests and
production build, a disposable-database migration, and a non-privileged fake-PTY
end-to-end relay flow.

Real root-shell acceptance is a deliberate Ubuntu deployment check. It requires
an enrolled test Agent on an isolated maintenance host and verifies root identity,
resize, idle close, absolute close, disconnect cleanup, and absence of terminal
content from database and service logs.
