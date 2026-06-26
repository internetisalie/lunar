---
id: "BUG-360"
title: "Failed to make file writable due to container/host user UID mismatch"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-360: Failed to make file writable due to container/host user UID mismatch

## 1. Reproduction

1. Build or run the container where the prebuilt image default user (`lunar`) is built with `1001:1001`.
2. Bind-mount a host project directory (owned by the host user `1000:1000`) into the container (e.g. `/home/lunar/test`).
3. Open the project inside the containerized IDE.
4. Attempt to edit/save files (e.g. `rocks/meteor/lua/meteor/init.lua` or IDE settings files like `.idea/dataSources.xml`).
5. Since the container process runs under UID `1001` but the files are owned by UID `1000`, write operations fail with `java.nio.file.AccessDeniedException`.
6. The IDE prompts "Failed to make <file> writable" and shows a popup notification "IDE error occurred. See details and submit report".

## 2. Expected vs Actual Behavior

- **Expected**: Files within the bind-mounted project should be writable by the container process, and files created inside the container should be owned by the correct user so they are writable on the host.
- **Actual**: Write operations fail with `java.nio.file.AccessDeniedException` because of the UID/GID mismatch (`1001` inside container vs `1000` on the host).

## 3. Context / Environment

- **IDE version**: IntelliJ IDEA / GoLand 2026.1.3 Build #GO-261.25134.147
- **Relevant Files**:
  - `docker/Dockerfile`
  - `docker/docker-helper.sh`
  - `docker/docker-entrypoint.sh`
- **Other Notes**:
  - Rebuilding the image locally with `docker-helper.sh build` passes the host user's actual UID/GID to the build arguments. However, dynamic runtime mapping in the entrypoint or container setup is needed to ensure alignment when running prebuilt images.
