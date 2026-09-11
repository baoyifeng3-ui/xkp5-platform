# XKP5 5.0.1 Offline Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a verified Ubuntu 20.04 amd64 offline installer archive for XKP5 version 5.0.1.

**Architecture:** Reuse the repository's quick offline release pipeline in portable empty-data mode. Build from committed platform and Agent sources, reuse the exact Java/Vue images currently deployed and verified, bundle Docker packages, then validate both inner and outer checksums.

**Tech Stack:** Bash, Docker Engine/Compose, Git archive, SHA256, gzip/tar.

**Spec:** `deploy/offline/README.md`

## Global Constraints

- Release version is `5.0.1`.
- Target is Ubuntu 20.04 amd64.
- Package uses `PORTABLE_SEED` with empty runtime data and does not include production users, credentials, certificates, volumes, or business data.
- Java, Vue, Registry importer, MySQL, FastDFS, Registry, Agent, installation scripts, and offline Docker packages are included.
- Existing production services and data volumes are not removed.

---

### Task 1: Assemble Release Builder

**Files:**
- Source: committed `xkp5-platform` and `xkp-agent` repositories
- Create: `/opt/xkp5-release-builder-5.0.1/`

**Interfaces:**
- Consumes: platform commit `96d7cec` or its direct successor containing only this plan; Agent commit `9567027`
- Produces: Ubuntu builder tree with `xkp5-platform/` and sibling `xkp-agent/`

- [ ] Export both Git repositories without untracked build artifacts.
- [ ] Upload and extract them on the Ubuntu 20.04 management server.
- [ ] Install the current Agent binary under the sibling Agent `dist/` directory.
- [ ] Confirm required runtime images exist before release generation.

### Task 2: Build Version 5.0.1

**Files:**
- Create: `/opt/xkp5-release-builder-5.0.1/xkp5-platform/dist/xkp5-offline-5.0.1.tar.gz`
- Create: `/opt/xkp5-release-builder-5.0.1/xkp5-platform/dist/install-xkp5-offline.sh`

**Interfaces:**
- Consumes: `deploy/quick-offline-release.sh --empty --reuse-images`
- Produces: two-file offline installer delivery

- [ ] Preserve existing image tags before assigning `latest` to the deployed Java/Vue/Registry importer images.
- [ ] Run the release builder with `--version 5.0.1 --ubuntu-version 20.04 --empty --reuse-images`.
- [ ] Confirm production Compose services remain running after the build.

### Task 3: Verify and Deliver

**Files:**
- Verify: generated archive, installer, `release.env`, `SHA256SUMS`
- Deliver: local `backups/xkp5-offline-5.0.1/`

**Interfaces:**
- Consumes: generated release files
- Produces: locally accessible verified package and checksum report

- [ ] Verify the outer archive with `tar -tzf`.
- [ ] Extract to a temporary directory and run `sha256sum -c SHA256SUMS`.
- [ ] Confirm release metadata reports version `5.0.1`, Ubuntu `20.04`, amd64, and empty portable data.
- [ ] Confirm installer `expected_archive_sha256` equals the archive's actual SHA256.
- [ ] Download both release files and write `SHA256SUMS` beside them.
