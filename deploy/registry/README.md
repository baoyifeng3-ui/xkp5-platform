# Built-in private Registry

The production and offline Compose files include a TLS-protected Docker
Registry and a separate importer worker. The Registry is reachable only on
the Compose network (`https://registry:5000`); it does not publish a host
write port.

## Setup

1. Create an internal CA and a Registry server certificate for the `registry`
   service. Keep `tls.key` and any Registry write credentials outside Git.
2. Place `ca.crt`, `tls.crt`, and `tls.key` in `XKP_REGISTRY_TLS_DIR`.
3. Set the staging/import directories and capacity variables in `.env`.
4. Start the stack with `compose.prod.yml` (or `compose.offline.yml`).

The `registry` service owns the persistent `registry_data` volume. The
`registry-importer` service is the only image-import boundary and receives the
staging/import directories plus the CA certificate. The Java service never
mounts the Docker socket or a Registry private key.

The importer reads its write credential from files named `username` and
`password` under `XKP_REGISTRY_IMPORTER_SECRETS_DIR` (default
`/etc/xkp/registry-secrets`). Create that directory as a root-owned, mode 0700
deployment secret; Compose mounts it read-only. Do not put those files or their
values in `.env`, Compose source, or Git.

## Storage policy

`XKP_REGISTRY_MAX_FILE_BYTES` defaults to 20 GiB. Staging capacity and cleanup
are enforced by the upload/import workflow; Registry history is retained
until a super administrator explicitly removes a version.

See [tls/README.md](tls/README.md) for certificate requirements.
