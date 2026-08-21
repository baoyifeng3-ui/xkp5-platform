# XKP5.0 Platform UI Refresh Design

## Goal

Refresh the complete XKP5.0 web interface using the approved reference-inspired
visual direction while preserving the existing backend behavior and role
boundaries. The refresh covers login, super-administrator operations, ordinary
administrator management, participant training, participant competition, and
administrator preview surfaces.

## Visual Direction

Use the approved "reference fusion" direction:

- white, compact left navigation;
- a light gray application workspace;
- blue-violet as the primary action and selected-navigation color;
- white content surfaces with restrained shadows and 6-8px card radii;
- circular colored icon signals for categories and status summaries;
- a compact top bar with page context, optional search, and account controls;
- dense tables and operational panels inside the visual system rather than
  decorative nested cards.

The design language is shared across all roles. Competition and training
participant experiences remain behaviorally and navigationally isolated even
though they use the same tokens and component styling.

## Role Navigation

### Ordinary administrator

The first-level navigation is:

1. Home
2. Competition Management
3. Course Management
4. Resource Management
5. Training Management
6. User Management
7. Device Management
8. Platform Settings

Competition Management is a first-level destination with its own internal
sections. It must not contain a competition-device section. All server and
device ownership is managed from Device Management.

Platform Settings is a first-level destination. Changes made there apply to
the complete platform rather than to only competition pages.

### Super administrator

Keep system-level operations separate: processing servers, container
templates, image registry, competition containers, administrator accounts,
license diagnostics, and platform-wide settings. Ordinary administrators keep
read-only image-registry access where already authorized.

### Participant

Training mode shows only training navigation and pages. Competition mode shows
only competition navigation and pages. No navigation item from the inactive
mode is rendered.

## Platform Mode Behavior

The administrator home page owns one mode action:

- in training mode it reads `Enter Competition Mode`;
- in competition mode it reads `Exit Competition Mode`.

Clicking the action changes authoritative backend mode without navigating away
or replacing the administrator interface. The current page refreshes its mode
state and button label after success.

Participant routing is resolved at login from authoritative platform mode:

- `COMPETITION` routes to the competition participant home;
- `TRAINING` routes to the training participant home.

After a mode change, an already authenticated participant must re-authenticate
under the existing generation guard before entering the other mode.

An unbound participant may enter competition pages and answer the paper. Image
annotation and code-editor actions remain visible but disabled with a concise
unavailable state because no competition slot is bound.

## Competition Management

Competition Management contains mode/environment readiness, participant-view
preview, competition control, rules, paper questions, grading, and competition
accounts. Device selection is removed. Container and device state is linked or
displayed from the platform Device Management ownership boundary.

## Participant-View Preview

The administrator preview destination renders the same shell, navigation, and
home page that an ordinary participant would see for the current platform
mode. It is not a separate administrator-styled preview screen.

The preview page has no top destination buttons and no refresh toolbar. It
opens directly on the participant home for the current mode. Navigation inside
the embedded participant shell remains available so the administrator can
inspect the participant journey.

Preview is strictly read-only:

- no administrator navigation or administrator-only content;
- no answers, scoring rules, internal identifiers, or configuration fields;
- no editing, submission, detection, grading, environment mutation, or other
  state-changing actions;
- read-only notices are shown only where necessary to explain a disabled
  action, not as persistent feature instructions.

## Page Composition

### Login

Use a full-height brand surface with the actual login form as the primary
experience. Apply the shared blue-violet visual language, preserve responsive
behavior, and keep validation/error messages near their fields.

### Home and dashboards

Use a compact overview band followed by summary metrics, actionable alerts,
and resource/device status. The platform-mode action stays in the overview
band. Avoid automatic navigation after commands.

### Management pages

Use full-width sections with compact toolbars, searchable/filterable tables,
clear empty/loading/error states, and focused dialogs. Do not nest page cards.

### Participant pages

Use the shared shell style at a lower information density. Competition paper
and practical actions remain the primary content; training focuses on courses,
resources, and environments.

## Responsive Behavior

- Desktop uses the full text sidebar.
- Tablet uses a narrower sidebar while preserving labels when space permits.
- Mobile collapses navigation into a controlled drawer.
- Tables may scroll horizontally; controls and labels must not overlap.
- Fixed toolbars, counters, and status controls use stable dimensions.

## Testing

Add or update source contract tests before production edits to cover:

- first-level ordinary administrator navigation;
- removal of competition devices and promotion of platform settings;
- mode action changes backend state without route changes and changes label;
- role- and mode-isolated participant navigation;
- preview has no destination toolbar and resolves to participant mode home;
- preview hides administrator content and mutation actions;
- the shared UI tokens and shell apply to login, administrator, operations,
  competition, and participant views;
- desktop and mobile production builds remain valid.

Run the complete Vue contract suite and production build after each major
navigation or shell change. Perform browser screenshots at desktop and mobile
viewports before completion.

