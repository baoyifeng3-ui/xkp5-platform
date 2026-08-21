# Private Image Registry And Deployment Design

## Scope

The platform will provide a built-in private Docker Registry for approved
Docker `save` archives. The same reusable image group may be used by training,
competition, and future custom courses. Mode-specific container names,
workspaces, and runtime behavior remain template concerns.

This phase does not implement vulnerability scanning or runtime support for
arbitrary custom multi-container groups.

## Image Groups And Versions

An image group has one of two types:

- `STANDARD_PAIR`: one image for annotation and one image for code editing.
- `CUSTOM`: an arbitrary set of approved images, registered for future use but
  not bindable to a runtime template in this phase.

Each image version is identified by an immutable Registry digest. Tags and
labels are metadata only and are never used as the execution reference.
Training, competition, and custom-course templates may reference the same
standard pair.

The platform supports independent annotation and editor upgrades. Registry
history is retained until a super administrator explicitly deletes it.

## Upload And Review

Only super administrators may upload, review, import, publish, delete, or
rollback images. Uploads use resumable fixed-size chunks into a staging
directory. A single archive defaults to a 20 GiB limit and staging defaults to
100 GiB; both are configurable by environment variables. Incomplete uploads
older than 24 hours are eligible for cleanup.

The accepted format is Docker `save` output in `.tar` or `.tar.gz` form. After
the final chunk, the server computes SHA-256, validates the size, and streams a
Docker archive parse. It does not perform vulnerability scanning, architecture
validation, or entrypoint validation in this phase.

The state flow is:

`UPLOADING -> PENDING_REVIEW -> APPROVED -> IMPORTING -> READY`

Failures retain their reason and can be retried. A rejected or failed staging
file is not removed automatically unless it reaches the cleanup policy or an
administrator explicitly deletes it.

An asynchronous Registry import worker reads approved staging files, imports
them through standard Registry tooling, records the resulting digest, and
never blocks a web request. The Java management service does not mount the
host Docker socket. Staging, import, and Registry data use separate persistent
directories.

## Registry Security

The built-in Registry uses an internal CA and TLS. Every Agent receives an
independent read-only credential that can be revoked without changing other
Agents. The platform-side import credential has write access and is never
distributed to Agents. Registry secrets, signing material, and full image
contents are excluded from ordinary logs.

## Data Model

The implementation will add records for:

- `ImageGroup`: group type, name, description, and lifecycle state.
- `ImageArtifact`: component type, source filename, size, SHA-256, Registry
  digest, version, and review/import state.
- `ImageRelease`: exact annotation/editor digests, creator, and release time.
- `ImageUpload`: resumable session, chunk counts, received bytes, checksum,
  state, and expiry.
- `ImageDeployment`: target Agent, component, desired/current digest, update
  policy, progress, failure reason, and rollback metadata.

## Deployment And Upgrade

Publishing is a per-Agent canary workflow. A release task contains the target
Agent, component, immutable digest, policy, and idempotency key. An Agent pulls
and verifies the digest before reporting `PULLED`.

The default policy is `UPDATE_CONTAINERS`: after a successful pull, the Agent
records a container snapshot, stops and recreates affected containers, and
reports `RUNNING` only after health checks pass. On success it removes old local
images that are not referenced by another environment.

The alternative policy is `IMAGE_ONLY`: the Agent pulls and caches the new
image without changing running containers; future creation or restore uses the
new digest.

Failures preserve the old container and old image. A failed recreation may be
rolled back to the previous digest. Each Agent permits only one deployment for
the same component at a time. Tasks support reconnect, progress polling,
idempotent retry, and audit records that omit credentials and image contents.

## Permissions

- `SUPER_ADMIN`: all image, release, deployment, deletion, and rollback actions.
- `ADMIN`: read-only image, release, and Agent deployment status.
- Agent: pull-only access using its own credential and only the digest assigned
  by the platform.
- Participant users: no Registry or deployment access.

## Acceptance Criteria

- Interrupted chunk uploads resume without restarting from zero.
- Invalid archives and checksum mismatches never enter the Registry.
- Approval and import are asynchronous and retryable.
- Agent credentials are isolated and read-only.
- Independent annotation/editor upgrades are supported.
- Both `UPDATE_CONTAINERS` and `IMAGE_ONLY` policies are observable and
  idempotent.
- Failed upgrades do not delete the working old container or image.
- Registry history remains available for explicit administrator rollback.

