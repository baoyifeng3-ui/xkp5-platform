# XKP5 Processing Agent

This is the first deployable Agent runtime for registration, authenticated heartbeats, command polling, and command receipts.

On Ubuntu, copy this directory to the processing server and run as root:

```bash
MANAGEMENT_URL='https://management.example/agent/v1' \
REGISTRATION_TOKEN='one-time-token' \
AGENT_DISPLAY_NAME='GPU Server 01' \
CA_FILE='/etc/xkp-agent/ca.crt' \
./install.sh
```

The Agent stores its credential in `/etc/xkp-agent/identity.json` with mode `0600`. Container lifecycle and course resource handlers are added on top of this runtime in the next phase.
