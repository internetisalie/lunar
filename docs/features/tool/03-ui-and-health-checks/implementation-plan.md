---
id: TOOL-03-PLAN
title: "Implementation Plan"
type: plan
parent_id: TOOL-03
status: "planned"
priority: "high"
folders:
  - "[[features/tool/03-ui-and-health-checks/requirements|requirements]]"
---

# Implementation Plan: UI/UX & Health Monitoring (`TOOL-03`)

Implements `design.md`, mirroring the existing `LuaInterpretersTable`/settings patterns.
Depends on TOOL-01/02 (LuaTool, LuaToolType, LuaToolManager, bindings). Phases map to req IDs.

## Phase 1: Global Inventory UI [Must] — TOOL-03-01
- [ ] `LuaToolsTable : ListTableWithButtons<LuaTool>` (§2.1): Type/Path/Version/Status columns
      (`CellModelBase`, `LocalPathCellEditor`), Add/Edit/Remove + "Auto-Detect" extra action.
- [ ] `LuaToolsConfigurable` (§2.2) + `<applicationConfigurable parentId=…Lua… displayName="Tools">`.
- [ ] UI test: table renders, add via file picker.

## Phase 2: Project Binding UI [Must] — TOOL-03-02
- [ ] `LuaProjectToolBindingPanel` (§2.3): one combo per `LuaToolType` + "(use global default)";
      bind to `LuaProjectSettings.State.projectToolBindings`.
- [ ] Add the panel into `LuaProjectSettingsConfigurable.createComponent()`.

## Phase 3: Health Monitoring [Should] — TOOL-03-03
- [ ] Add `lastCheckedMtime: Long` and `lastCheckReason: String` to the TOOL-01 `LuaTool`
      model (mtime gate + cached reason for table/banner/diagnostics).
- [ ] `LuaToolHealthChecker.check` (§3.1): fast (exists/canExecute) → mtime gate → slow
      (`--version` via `LuaProcessUtil.capture`, `Banner` parse).
- [ ] `LuaToolHealthMonitor` (`@Service(PROJECT)`): `start()` registers
      `addAsyncFileListenerBackgroundable` (§3.2); `revalidateAll()` background task (§3.3).
- [ ] `LuaToolHealthStartup : ProjectActivity` (`<postStartupActivity>`) → start + revalidate.
- [ ] Unit tests: TC-TOOL-03-01 (fast invalid), TC-TOOL-03-02 (version), TC-TOOL-03-03 (mtime
      gate — assert no process spawned).

## Phase 4: Notifications & Diagnostics [Must] — TOOL-03-04/05
- [ ] `LuaToolEditorNotificationProvider` (§2.6) + `<editorNotificationProvider>`; refresh via
      `EditorNotifications.updateAllNotifications()`.
- [ ] `<notificationGroup id="notification.group.lunar.tools">`; balloon on project open when a
      tool became invalid.
- [ ] `LuaToolDiagnostics.logSnapshot` (§2.7) + a "Report Tool Status" action.
- [ ] Tests: TC-TOOL-03-04 (banner data), TC-TOOL-03-05 (log snapshot).

## Verification Tasks
- Unit: health checker (fast/slow/mtime), banner data, diagnostics.
- Manual: delete a binary → table shows Invalid + banner + balloon; open project with a
  missing bound tool → notification.
