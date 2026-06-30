---
id: TOOL-03
title: "03: UI/UX & Health Monitoring"
type: feature
parent_id: TOOL
status: "done"
vf_icon: ✅
priority: "high"
folders:
  - "[[features/tool/requirements|requirements]]"
---

# UI/UX & Health Monitoring (`TOOL-03`)

## Scope
This feature provides the user interface for managing tools and background health monitoring.

### In Scope
- Settings UI for global and project tool management.
- Health monitoring of registered tool paths.
- User notifications for tool-related issues.
- Diagnostic reporting of tool state.

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| **TOOL-03-01** | **Settings UI** | **M** | **Not Implemented** | A dedicated "Tools" section exists in IDE preferences, mirroring the Lua interpreters UI. |
| **TOOL-03-02** | **Project Settings Overlay** | **M** | **Not Implemented** | Project properties include a tab to view and bind tool versions for that project. |
| **TOOL-03-03** | **Health Checks** | **S** | **Not Implemented** | Invalid or non-functional tool binaries are marked with an error in the inventory via background checks. |
| **TOOL-03-04** | **Notifications** | **M** | **Not Implemented** | Users receive non-intrusive notifications for missing tools, outdated versions, or invalid executables. |
| **TOOL-03-05** | **Diagnostic Reporting** | **M** | **Not Implemented** | The IDE surfaces tool version and path information in logs for troubleshooting. |

## Test Cases

### TC-TOOL-03-01: Health Check Invalidation (fast check — design §3.1)
- **Input**: A registered tool whose `path` points at a deleted file.
- **Action**: `LuaToolHealthChecker.check(tool)`.
- **Expected Output**: `HealthResult(isValid=false, version=null, reason="Binary missing")`.

### TC-TOOL-03-02: Version Extraction (slow check — design §3.1)
- **Input**: A tool whose `--version` mock stdout is `luacheck 1.1.0`.
- **Action**: `check(tool)` with the fast check passing.
- **Expected Output**: `HealthResult(isValid=true, version="1.1.0", reason="OK 1.1.0")`.

### TC-TOOL-03-03: mtime Gating (design §3.1 step 2)
- **Input**: A previously-valid tool (`isValid=true`, `version="1.1.0"`,
  `lastCheckedMtime == file.lastModified()`).
- **Action**: `check(tool)`.
- **Expected Output**: returns `OK 1.1.0` **without** spawning a `--version` process (assert
  no process invocation).

### TC-TOOL-03-04: Editor Banner (design §3.4)
- **Input**: A project with a binding to an invalid tool; a `.lua` file open.
- **Action**: `LuaToolEditorNotificationProvider.collectNotificationData(project, luaFile)`.
- **Expected Output**: a non-null `Function` producing an `EditorNotificationPanel`
  (Warning) whose text names the tool type + reason; for a non-Lua file → null.

### TC-TOOL-03-05: Diagnostic Snapshot (design §2.7)
- **Action**: `LuaToolDiagnostics.logSnapshot(project)` with two tools (one valid, one not).
- **Expected Output**: the log contains one line per tool with type, path, version, isValid,
  and reason.
