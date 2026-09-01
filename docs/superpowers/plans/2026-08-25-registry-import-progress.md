# Registry Import Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Show real Registry import phase and layer progress from approval through immutable digest creation.

**Architecture:** Persist progress on `image_artifact`; update it from `ImageImportWorker` and `RegistryImportTool` output callbacks; render it through the existing artifact polling UI.

**Tech Stack:** Spring Boot, MyBatis, Flyway, skopeo, Vue 2.

### Task 1: Persist progress
- [ ] Add Flyway columns and record fields.
- [ ] Add mapper progress update method.

### Task 2: Emit importer progress
- [ ] Count archive manifest layers.
- [ ] Add bounded line callbacks to skopeo collectors.
- [ ] Update stage, percent and layer counts.

### Task 3: Render progress
- [ ] Poll artifacts while importing.
- [ ] Show progress bar, phase, layer counts, elapsed/stale status.
- [ ] Verify contracts and production build.
