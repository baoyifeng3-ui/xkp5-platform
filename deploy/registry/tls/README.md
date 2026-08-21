# Registry TLS directory

The directory configured by `XKP_REGISTRY_TLS_DIR` must contain:

- `ca.crt`: the internal CA certificate, readable by Java and the importer;
- `tls.crt`: the Registry server certificate, including the `registry` DNS
  name (and any internal hostname used by a reverse proxy);
- `tls.key`: the Registry server private key, readable only by the Registry
  container.

Do not commit private keys or Registry write credentials. Compose mounts this
directory read-only. Agents receive only a scoped read-only credential and
the CA certificate through the later Agent deployment workflow.
