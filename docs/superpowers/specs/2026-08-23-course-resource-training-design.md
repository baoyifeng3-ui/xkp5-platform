# Course Platform, Resources, and Training Design

## Goal

Build a learning-oriented course platform for ordinary users, with administrator
course/resource authoring, per-resource progress tracking, private delivery of
archives into a user's training container, and a side-by-side learning/training
workflow.

## Scope

This feature is split into three independently testable slices:

1. Course and resource authoring for administrators.
2. Course discovery and per-resource learning progress for ordinary users.
3. Training environment launch and archive delivery from a course/resource page.

Competition mode, paper answering, competition practical sessions, and existing
image registry deployment remain separate domains. The existing training
environment lifecycle and Agent command protocol are reused rather than copied.

## Roles and Permissions

### Ordinary user

- Can browse only enabled courses assigned or made visible to the user.
- Can search by course name, custom course type, learning state, and resource type.
- Can preview ebook, video, and PPT resources in the browser.
- Cannot download any course resource to the local computer.
- Can send an archive only to the user's own ready training environment.
- Can start/enter the user's own training environment and use model validation.
- Cannot create, edit, publish, enable, disable, delete, or assign courses/resources.

### Administrator

- Can create and edit courses and custom course types.
- Can upload ebook, video, PPT, and archive resources to a course.
- Can enable/disable a course.
- Can create and delete training environments within the existing administrator
  boundary. Environment deletion must use the existing lifecycle safety checks.
- Can assign courses/resources and send a resource to all eligible users' training
  environments.
- Can inspect progress summaries but cannot read private answer data from training
  sessions.

### Super administrator

- Retains all administrator capabilities.
- May perform operations already reserved for `SUPER_ADMIN`, including environment
  creation when the existing backend endpoint requires it and platform-wide
  resource administration.

## Data Model

Use normalized records instead of one JSON blob so filtering and progress queries
remain bounded and indexable.

### `course`

- `course_id` UUID/string primary key.
- `name` required, bounded to 120 characters.
- `course_type` required, administrator-defined, bounded to 48 characters.
- `description` nullable, bounded to 2000 characters.
- `cover_resource_id` nullable reference to a resource record, resolved by service
  rather than a database foreign key to match repository migration conventions.
- `enabled` boolean.
- `created_by`, `created_at`, `updated_at`.

### `course_resource`

- `resource_id` UUID/string primary key.
- `course_id` logical course identifier.
- `resource_type` enum: `EBOOK`, `VIDEO`, `PPT`, `ARCHIVE`.
- `name` required, bounded to 160 characters.
- `storage_key` private object/filesystem key; never returned as a public download URL.
- `content_length`, `sha256`, `mime_type`.
- `sort_order` integer.
- `enabled` boolean.
- `created_by`, `created_at`, `updated_at`.

The API returns preview tokens or streaming endpoints for ebook/video/PPT only.
Archive resources never expose a browser download endpoint.

### `course_resource_progress`

One row per `user_id + resource_id`:

- `progress_id` UUID/string primary key.
- `user_id`, `resource_id` logical identifiers.
- `progress_kind`: `PAGE`, `TIME`, `SLIDE`, or `DELIVERY`.
- `current_value`, `total_value`, `percent` decimal/integer.
- `last_position` bounded string for ebook location or video timestamp metadata.
- `state`: `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`.
- `completed_at`, `updated_at`.

The server clamps values to `[0, total_value]`, calculates percent itself, and
marks `COMPLETED` only when the resource reaches exactly 100%. Client values are
never trusted for completion without server-side bounds validation.

### `course_resource_delivery`

One row per archive delivery request and target environment:

- `delivery_id` UUID/string primary key.
- `resource_id`, `user_id`, `environment_id`.
- `state`: `PENDING`, `DISPATCHED`, `RUNNING`, `SUCCEEDED`, `FAILED`.
- `operation_id`, `command_id`, `failure_code`, `failure_message`.
- `requested_at`, `completed_at`, `updated_at`.

The delivery is idempotent for the same archive digest, user, and environment.
Successful delivery sets the archive progress to 100%; failed delivery leaves the
resource incomplete and preserves a stable failure reason.

## Course and Resource Flow

1. Administrator creates a course with a custom type and disabled initial state.
2. Administrator uploads one or more resources of the four supported types.
3. Uploads are streamed, size/checksum validated, and stored privately.
4. Administrator enables the course after required resources are present.
5. Ordinary users see only enabled/eligible courses.
6. The course page lists resources by type and displays independent progress.
7. Browser preview endpoints stream ebook/video/PPT content without a download
   disposition. Archive content is represented by a “send to training environment”
   action only.

## Training Integration

The course page provides a “快速开启实训环境” action:

- If the user has a ready environment, open it in the existing training view.
- If no environment exists, show the existing unavailable state and do not create
  an environment implicitly for an ordinary user.
- If the environment is stopped, call the existing start operation and show the
  asynchronous pending state until Agent confirmation.
- Keep the course preview and training terminal/environment panel in a split,
  responsive layout. On narrow screens they become vertically stacked sections.

Archive delivery uses the existing environment operation/Agent command boundary:

- ordinary user target is always the current user's own environment;
- administrator broadcast expands to eligible users server-side, not in the browser;
- each target gets an idempotent delivery row and command correlation;
- container destination is a server-generated workspace path, never caller input;
- success/failure is updated from Agent terminal results.

## Model Validation

Training model validation reuses the competition result-validation contract but
adds two input forms:

- image upload: stream the image to the user's training environment validation
  endpoint, enforcing configured size/type limits;
- URL detection: validate the URL server-side, then dispatch the same T100
  validation command with a bounded URL payload.

Both forms return a stable operation state (`PENDING`, `RUNNING`, `SUCCEEDED`,
`FAILED`) and sanitized result data. Credentials, container names, internal URLs,
and raw command payloads are never rendered to ordinary users.

## API Boundaries

Administrator routes:

- `GET/POST/PUT /admin/courses`
- `POST /admin/courses/{courseId}/resources`
- `PUT /admin/courses/{courseId}/enabled`
- `GET /admin/course-progress`
- `POST /admin/training-environments` and existing lifecycle routes
- `POST /admin/course-resources/{resourceId}/broadcast`

Ordinary user routes:

- `GET /user/courses`
- `GET /user/courses/{courseId}`
- `GET /user/course-resources/{resourceId}/preview`
- `PUT /user/course-resources/{resourceId}/progress`
- `POST /user/course-resources/{resourceId}/deliver`
- existing `/user/training-environments` lifecycle routes
- `POST /user/training-environments/{environmentId}/validate-image`
- `POST /user/training-environments/{environmentId}/validate-url`

All mutations enforce the role and ownership checks in the service layer, not
only in route metadata.

## Error and State Rules

- Missing/disabled course: stable `COURSE_UNAVAILABLE`.
- Unsupported resource type: `RESOURCE_TYPE_INVALID`.
- Local download attempt for a course resource: no download endpoint is exposed;
  return `RESOURCE_PREVIEW_ONLY` if a direct request is attempted.
- No own training environment: `TRAINING_ENVIRONMENT_UNAVAILABLE`.
- Archive delivery conflict: `RESOURCE_DELIVERY_CONFLICT`.
- T100 image/URL validation errors: stable bounded code and user-safe message.
- Agent/operation failures preserve the previous environment/container state and
  never report success before terminal confirmation.

## Testing Strategy

- Schema tests for course/resource/progress/delivery constraints and indexes.
- Service tests for role boundaries, course enablement, private preview, progress
  clamping, exact-100 completion, archive delivery idempotency, and broadcast
  target expansion.
- Controller tests for ordinary-user ownership and administrator actions.
- Agent command fixture tests for archive delivery and T100 image/URL validation.
- Vue contracts for four resource tabs/types, filters, progress display, no local
  download links, quick training action, and responsive split layout.
- Docker smoke test reads course/resource endpoints and verifies no archive URL is
  returned; it does not mutate mode or create environments.

