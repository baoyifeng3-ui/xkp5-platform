# Code-Server TLS Per-Agent Design

## Goal

Give each XKP platform a distinct TLS root CA. Issue one code-server leaf
certificate per processing agent IP, expose only the root certificate for user
download, and mount only the matching leaf certificate and private key into
that agent's code-server containers.

## Scope

- Keep the existing resource delivery endpoint and deploy it with the release.
- Generate and retain the platform TLS CA on the management host.
- Issue agent-specific leaf certificates with the agent `primaryIp` as an IP
  subject alternative name.
- Make the agent mount its own leaf certificate and private key read-only at
  `/root/.config/code-cert.pem` and `/root/.config/code-cert-key.pem` when it
  creates an EDITOR container.
- Let authenticated users download `rootCA.pem` from the training environment
  page.

## Security Boundaries

- The platform CA private key remains on the management host, mode `0600`.
- An agent receives only its own leaf certificate and private key, mode `0600`.
- A code-server container receives only its agent leaf certificate and private
  key through read-only mounts.
- The platform CA private key and the management SSH private key must not be
  mounted into an agent or code-server container, returned by an API, or added
  to an image.
- The downloadable artifact is the public root certificate only.

## Data Flow

1. Installation initializes the CA once at a protected host directory.
2. Agent deployment/refresh signs a leaf certificate for that agent's current
   `primaryIp` and supplies the leaf pair to the agent TLS directory.
3. The Agent uses the leaf pair only for EDITOR container mounts.
4. `DefaultEditorCommand` continues to launch code-server with the existing
   `--cert` and `--cert-key` paths.
5. The user interface requests a protected download endpoint and saves
   `rootCA.pem` locally.

## IP Changes

Changing a processing-agent IP requires reissuing only that agent leaf
certificate. The root CA remains stable, so users do not need to reinstall the
root certificate. A platform rebuild must not silently rotate the root CA.

## Verification

- Contract tests assert the root CA endpoint and user-page download action.
- Contract tests assert the agent EDITOR mount paths and reject CA/SSH private
  key mounts.
- A deployment smoke test checks all Compose services, login, and the resource
  delivery endpoint.
- For each active agent, inspect the code-server certificate SAN and confirm it
  contains the agent primary IP.
