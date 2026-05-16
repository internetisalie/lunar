---
folders:
  - "[[features/tool/03-ui-and-health-checks/requirements|requirements]]"
title: Implementation Plan
---

# Implementation Plan: UI/UX & Health Monitoring (`TOOL-03`)

This plan covers the user interface and background maintenance of the tool inventory.

## Phase 1: Global Inventory UI [Must]
- [ ] Create `LuaToolInventoryPanel` using `ToolbarDecorator` and `JBTable`.
- [ ] Implement columns: Name, Type, Version, Path, Status.
- [ ] Implement Add (File Picker), Edit, and Remove actions.
- [ ] Add an "Auto-Detect" button that triggers `LuaToolDiscoveryService`.
- [ ] Register the panel in `LuaApplicationSettingsConfigurable`.

## Phase 2: Project Binding UI [Must]
- [ ] Create `LuaProjectToolPanel` with `ComboBox` for each `LuaToolType`.
- [ ] Populate dropdowns with registered tools from `LuaApplicationSettings`.
- [ ] Include an "Inherit Global" option.
- [ ] Register the panel in `LuaProjectSettingsConfigurable`.

## Phase 3: Health Monitoring [Should]
- [ ] Create `net.internetisalie.lunar.util.LuaToolHealthCheckActivity`.
- [ ] Implement a `ProjectActivity` (or `BackgroundPostStartupActivity`) to verify bound tools.
- [ ] Periodically check if `tool.path` exists and compare `mtime` with `lastModified`.
- [ ] Re-validate via `LuaToolValidator` if `mtime` has changed.
- [ ] Update `LuaTool.isValid` flag and metadata.

## Phase 4: Notifications & Diagnostics [Should]
- [ ] Implement notification logic for:
    - Missing tools (path not found).
    - Outdated tools (below minimum version).
    - Invalid executables.
- [ ] Use `NotificationGroupManager` for standardized IDE alerts.
- [ ] Add diagnostic logging to `LuaToolManager` resolution and `LuaToolValidator` execution.
- [ ] Ensure logs include tool version, path, and any CLI errors for support troubleshooting.

## Verification Tasks
- [ ] **UI Test**: Verify table rendering and tool addition via file picker.
- [ ] **Manual Test**: Delete a tool binary from disk and verify it is marked "Invalid" in settings.
- [ ] **Manual Test**: Verify notifications appear when a project is opened with missing tools.
