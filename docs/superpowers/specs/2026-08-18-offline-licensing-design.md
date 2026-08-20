# XKP5.0 Offline Licensing Design

## 1. Scope

This phase delivers offline platform activation before processing-server Agent
development. It includes:

- platform information request export;
- a separate offline Go license-generator program;
- signed license import and validation;
- license status, audit history, and clock-rollback detection;
- backend guards that future training, competition, and container operations
  must call;
- ordinary-administrator activation UI and super-administrator diagnostics.

It does not yet stop remote containers because the Agent command channel is a
later phase. The license service exposes the transition event required by that
future integration.

## 2. Repository Boundary

The management-server implementation remains in `xkp5-platform`.

The generator is created as a separate sibling repository named
`xkp-license-tool`. Its private signing key, generated licenses, and customer
request files are ignored by Git. The private key is never copied into the
management repository, Docker image, release archive, application logs, or
database.

## 3. Cryptographic Model

Licenses use Ed25519 signatures:

- the generator owns the private signing key;
- the management server contains only the public verification key;
- the request and license payloads use canonical JSON before signing;
- the license envelope contains the payload, signature, format version, and
  key identifier;
- readable license fields are not encrypted because they contain no secret.

Signature verification uses a Java-compatible Ed25519 provider. The Go
generator uses the Go standard library. Key rotation is supported through a
configured map of key identifiers to public keys.

## 4. Platform Request File

An ordinary administrator downloads a `.xkpreq` file containing:

- request format version;
- installation ID;
- platform version;
- hardware fingerprint SHA-256 digest;
- random request ID and challenge;
- request creation time;
- optional organization label entered by the administrator.

No password, database credential, IP address, user record, file path, or raw
hardware identifier is exported.

The installation ID is generated once and stored in the management database.
Each export creates a fresh request ID and challenge. A license must reference
the exact installation ID, fingerprint, request ID, and challenge.

## 5. Hardware Fingerprint

Formal licensing targets Ubuntu 22.04 amd64 management servers only. The host
installation script creates `/etc/xkp/host-identity.json` from:

- `/etc/machine-id`;
- DMI product UUID when available;
- root filesystem UUID when available.

The file is mounted read-only into the Java container. The application
normalizes and hashes the values and never stores or exports the raw values.
At least two host identifiers must be present in production.

Windows is a development and integration-test host only. A development build
may use an explicitly marked development identity together with a test key and
a license carrying `environment: DEVELOPMENT`. Production builds do not contain
the test public key and reject Windows as an activation target. Development
licenses cannot activate an Ubuntu production installation, and production
licenses cannot activate a Windows development installation.

## 6. License Payload

The `.xkplic` payload contains:

- license format version and license ID;
- referenced request ID and challenge;
- installation ID and hardware fingerprint digest;
- organization name;
- environment (`PRODUCTION` or `DEVELOPMENT`);
- issue time, not-before time, and expiry time;
- optional maximum processing-server count;
- product code `XKP5` and supported major version;
- key identifier.

The initial commercial enforcement surface is expiry plus optional processing
server count. Feature-specific limits are not added until a real requirement
exists.

## 7. Administrative Workflow

Normal administrators can:

- view the current activation status;
- download a platform request file;
- import a signed license file;
- view expiry, organization, license ID, and server limit.

Super administrators can additionally:

- inspect fingerprint and validation diagnostics;
- view import and validation audit history;
- replace a license during repair or renewal;
- verify configured public-key identifiers.

No role can generate a license in the management platform.

Import is transactional. The server validates file structure, signature,
product, environment, fingerprint, request challenge, validity period, and key
identifier before replacing the active license. Failed imports do not alter the
current license.

## 8. Runtime State And Enforcement

The canonical states are:

- `NOT_ACTIVATED`;
- `ACTIVE`;
- `EXPIRING` (30 days or fewer remain);
- `EXPIRED`;
- `INVALID`;
- `CLOCK_ROLLBACK`.

Every protected backend operation calls `LicenseGuard.requireActive()`.
Read-only status, login, password change, request export, license import, and
super-administrator diagnostics remain available when inactive.

When the state changes from active to inactive, the service writes an audit
event and publishes a local license-state event. The future Agent orchestration
phase consumes this event to stop all training and competition containers and
reject subsequent start commands at both management-server and Agent layers.

## 9. Clock Handling

The platform stores the greatest trusted wall-clock time observed while a
license was valid. A system time earlier than that value beyond a five-minute
tolerance produces `CLOCK_ROLLBACK`. Importing a valid renewal does not erase
the audit trail.

This detects ordinary clock rollback but cannot defend against a machine owner
who can modify the application, database, and signing configuration. That
threat is outside the offline-license boundary.

## 10. Data Model

The management database adds:

- `platform_installation`: installation ID and maximum trusted time;
- `license_request`: request ID, challenge hash, fingerprint digest, creation
  time, creator, and consumed state;
- `platform_license`: signed envelope, parsed non-secret metadata, import time,
  importer, and active flag;
- `license_audit`: actor, action, result, reason, license ID, request ID,
  correlation ID, and timestamp.

Only one license is active. Historical licenses and audits are retained.

## 11. APIs

Normal-administrator endpoints:

- `GET /admin/license/status`;
- `POST /admin/license/requests`;
- `GET /admin/license/requests/{requestId}/download`;
- `POST /admin/license/import`.

Super-administrator endpoints:

- `GET /super-admin/license/diagnostics`;
- `GET /super-admin/license/audits`;
- `POST /super-admin/license/revalidate`.

Downloads and imports have strict size limits. Parsing rejects unknown format
versions, duplicate JSON keys, missing fields, oversized values, and invalid
timestamps.

## 12. Error Handling

User-facing errors distinguish invalid signature, wrong machine, wrong
environment, expired license, consumed request, unsupported version, malformed
file, and clock rollback. Logs and API responses never include signatures,
challenges, raw fingerprints, private keys, or full license envelopes.

## 13. Testing And Acceptance

Management-server tests cover:

- canonical payload and signature verification;
- wrong-key, tampered-payload, wrong-machine, and expired-file rejection;
- atomic replacement and preservation of a valid existing license;
- role permissions;
- request challenge consumption;
- clock rollback;
- `LicenseGuard` on protected operations;
- inactive-to-active and active-to-inactive events.

Generator tests cover deterministic canonical JSON, key creation, request
parsing, license generation, and verification against shared fixtures.

Windows integration acceptance runs only in the development profile: it uses a
development identity to export a request, signs it with the test key, imports
it through the normal-administrator UI, and verifies the resulting state
without internet access. Release acceptance repeats the workflow on Ubuntu
with the production key before packaging.
