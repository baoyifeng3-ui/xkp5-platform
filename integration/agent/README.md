# Processing Agent integration flow

These scripts exercise the management-server Agent protocol without requiring an
Ubuntu GPU host. They never print the one-time registration token or persisted
Agent credential.

Run against the local development stack with a one-time token created in the
super-administrator processing-server page:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\integration\agent\test-agent-flow.ps1 `
  -RegistrationToken '<one-time-token>' `
  -AdminToken '<administrator-session-token>' `
  -SuperAdminToken '<super-administrator-session-token>'
```

When `RegistrationToken` is omitted, `SuperAdminToken` is used to issue one. The
admin token enables the 15-second offline and reconnect assertions. The super-admin
token additionally verifies disable behavior. For a production TLS endpoint, set
`AgentBaseUrl` to the fixed management IP on port `19443` and trust the internal CA
in Windows before running the flow.

Run the script contract check independently:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\integration\agent\test-script-contract.ps1
```

`test-terminal-relay-flow.ps1` is a destructive acceptance flow for a disposable
loopback TLS stack. It imports the configured test license, enrolls a temporary
Agent, and uses a non-privileged deterministic echo adapter instead of starting a
host shell. Required environment variables and the container prerequisites are
listed in `docs/operations/processing-agent.md`. The flow fails if ticket replay
or a concurrent session is accepted, cleanup or injected idle expiry is
unfinished, or its random terminal sentinel appears in MySQL metadata or
management logs.

The short idle case requires the development-only management clock offset
`MATCH_TERMINAL_TEST_CLOCK_OFFSET_SECONDS=600+XKP_TEST_TERMINAL_IDLE_SECONDS`;
the script does not rewrite terminal session timestamps.
