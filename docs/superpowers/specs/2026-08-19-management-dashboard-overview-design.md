# Management Dashboard Overview Design

## Goal

Replace the normal-administrator home page placeholders with one reliable
platform overview while preserving the existing role-specific shells and
navigation. The overview reports processing-server availability, environment
state, user activity, actionable problems, and aggregate resource use.

## Scope

This increment includes:

- one authenticated normal-administrator dashboard summary endpoint;
- five-minute active-user tracking;
- processing-server and environment counts from existing persisted state;
- a bounded first set of alert and pending-work counters;
- aggregate CPU, GPU, memory, and disk utilization;
- periodic refresh and stale/error presentation on the existing management
  home page.

It does not redesign the UI, add a general alert rules engine, create new Agent
metrics, change competition-mode behavior, or expose dashboard data to normal
users or super administrators.

## Roles And Navigation

The existing normal-administrator home route remains the only consumer of the
new overview. Its four shortcuts remain:

1. One-click class
2. Competition-mode switch
3. Course platform
4. Training environment

Normal users retain their three-item interface. Super administrators retain the
maintenance workspace and do not receive the normal-administrator dashboard.
The backend endpoint independently enforces the administrator role rather than
relying on route visibility.

## Active User Semantics

A user is online when their most recent authenticated activity occurred during
the preceding five minutes. Login records activity immediately. While a user is
signed in, the frontend sends a lightweight activity update at most once per
minute. Logout removes the current session's activity immediately when possible;
otherwise the record expires naturally after five minutes.

Activity is stored per authenticated session, not as a permanent online flag on
the user row. The dashboard counts distinct user IDs with at least one active
session. Anonymous requests, failed authentication, Agent protocol calls, and
expired sessions never count as online users.

## Dashboard Summary API

The management service exposes one endpoint returning a single consistent
snapshot. The response contains:

- snapshot time and freshness state;
- online, total, and offline processing-server counts;
- running, transitioning, degraded, and failed environment counts;
- distinct online-user count;
- alert and pending-work totals with a small category breakdown;
- CPU, GPU, GPU-memory, system-memory, and disk utilization summaries.

Resource values use the most recent valid metric for each online Agent and are
averaged only across Agents that reported that metric. A missing metric remains
unknown; it is never converted to zero. Each resource field includes a sample
count so the frontend can distinguish no data from genuine zero utilization.

The endpoint reads existing Agent, metric, environment, operation, command, and
license state. It does not contact Agents synchronously, so dashboard requests
remain fast when processing servers are offline.

## Alerts And Pending Work

The first release uses deterministic conditions already represented by current
state:

- offline or stale enabled processing servers;
- environments in `DEGRADED` or `ERROR`;
- failed environment operations or server commands that have not been cleared
  by a later successful operation;
- operations still pending or running;
- unusable, expired, or near-expiry platform authorization.

The response returns counts and stable category codes. It does not duplicate
full records or introduce acknowledgement workflows. The existing detail pages
remain the source for investigation and remediation.

## Frontend Behavior

The normal-administrator home page loads the summary on entry and refreshes it
every 15 seconds while visible. Only one request may be in flight. A successful
refresh atomically replaces the snapshot and records its update time.

If refresh fails after a prior success, the page keeps the previous snapshot and
marks it stale. On the first-load failure, values display as unavailable rather
than zero. Resource bars have fixed dimensions and show an explicit no-data
state when sample count is zero. Pending and alert counts link to the closest
existing management detail page.

The existing license status panel remains visible. Dashboard authorization
problems are presented as an overview warning without exposing license payloads
or signing details.

## Data And Performance

Session activity uses a dedicated table with a unique session identifier, user
ID, login time, last-activity time, and expiry time. Indexes support expiry
cleanup and distinct active-user counting. Activity updates use an upsert and
are rate-limited by the client; the server remains idempotent.

The summary service issues bounded aggregate queries and never loads full metric
history. Agent resource aggregation uses only the latest accepted metric row per
Agent. Old activity rows are deleted opportunistically in bounded batches and
do not affect the online count once expired.

## Error Handling And Safety

- Unauthorized roles receive a forbidden response.
- A missing metrics sample produces `null` plus sample count zero.
- A partial database failure fails the whole snapshot rather than mixing data
  from different moments and claiming it is current.
- Activity updates never extend the authentication token or bypass normal
  session expiry.
- Dashboard reads and activity updates do not write secrets or Agent credentials
  to logs.

## Verification

Backend tests cover the five-minute boundary, distinct-session counting,
anonymous and Agent exclusion, role enforcement, aggregate counts, missing
metrics, stale Agents, alert categories, and license states. Service tests fix
the clock so time boundaries are deterministic.

Frontend tests cover successful load, 15-second refresh, one in-flight request,
stale-data retention, first-load failure, no-metric display, links, and role
visibility. Final verification runs targeted Java tests, the complete Java test
suite, and the production Vue build in the existing Docker-based toolchain.
