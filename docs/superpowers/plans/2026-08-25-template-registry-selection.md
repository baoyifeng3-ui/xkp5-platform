# Registry-backed Template Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make container template publishing select only READY image releases and persist the immutable Registry digest.

**Architecture:** The Vue template form loads published releases and filters by component. The API sends the selected release identity with the template request. Spring validates the release is READY and component-compatible before storing the digest in the template image reference/fingerprint.

**Tech Stack:** Vue 2, Axios, Spring Boot, MyBatis-Plus, MySQL.

### Task 1: Map existing release and template contracts

**Files:** `vue/src/services/imageRegistry.js`, `java/match-mgr/src/main/java/com/match/registry`, `java/match-mgr/src/main/java/com/match/environment`.

- [ ] Confirm release list fields and template request fields.
- [ ] Add focused contract assertions for release filtering and digest submission.

### Task 2: Replace manual image input in the template form

**Files:** `vue/src/views/operations/ContainerTemplates.vue`, `vue/src/services/imageRegistry.js`, `vue/scripts/test-container-template-view-contract.js`.

- [ ] Load published releases when the dialog opens.
- [ ] Filter `ANNOTATION` and `EDITOR` options independently.
- [ ] Replace free-text image input with a release selector showing group, version, repository and digest.
- [ ] Disable publish when no compatible release is selected.

### Task 3: Validate release selection on the backend

**Files:** `java/match-mgr/src/main/java/com/match/environment/model/ContainerTemplateRequest.java`, `ContainerTemplateService.java`, registry mapper/service.

- [ ] Accept `releaseId` in the request.
- [ ] Require the release to be READY, published, and component-compatible.
- [ ] Store the immutable digest in the template configuration and reject mutable-only references.

### Task 4: Verify

- [ ] Run `npm run test:templates` and `node scripts/test-image-registry-contract.js`.
- [ ] Run `npm run build`.
- [ ] Run the focused Java template service tests.
- [ ] Manually verify a template can be published only after a READY release exists.
