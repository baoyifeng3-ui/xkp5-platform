# File Image And Template Flow

User-approved behavior: filename identifies uploaded archives; no user-entered tags or image versions. Docker uses an automatically assigned name and latest tag. Upload preserves old bytes until a replacement passes the existing quick header/format check. Duplicate upload and server image require explicit overwrite.

- [ ] Upload: ImageUploadService/ImageFileService and ImageUploadDialog; filename duplicate checks and replacement tests.
- [ ] Agent: DOCKER_INVENTORY_ACTION for actual inspection and safe deletion; existing durable command execution; stopped containers block image deletion.
- [ ] Backend: authenticated inventory commands; template creation checks a recent successful server inspection and stores immutable image ID. Existing environment deletion handles managed containers and database cleanup only after success.
- [ ] UI: filename list, server image template selection, command polling and deletion confirmation/errors.
- [ ] Verification: focused Java and Go tests, Vue build/behavior, schema check. Record deployment limitations in TODO.

No Docker volumes or workspace data are deleted by inventory operations. Existing template revision records remain internal to preserve environment references. Old templates remain compatible; new templates pin Docker image IDs so overwriting a name cannot silently change existing environments.
