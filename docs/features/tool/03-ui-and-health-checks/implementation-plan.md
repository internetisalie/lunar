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
- [ ] **Two-Stage Logic**: Implement optimized check (Existence check -> mtime check -> Version check).
- [ ] **Reactive Monitoring**: Register a `VirtualFileListener` to track tool binary changes in real-time.
- [ ] Periodically check if `tool.path` exists and compare `mtime` with `lastModified`.
- [ ] Re-validate via `LuaToolValidator` if `mtime` has changed.
- [ ] Update `LuaTool.isValid` flag and metadata.

## Phase 4: Notifications & Diagnostics [Should]
- [ ] Implement notification logic using **Editor Banners** for project-level tool issues.
- [ ] Use `NotificationGroupManager` for critical global tool issues.
- [ ] Implement specialized tooltips in `LuaToolInventoryPanel` to display exact failure reasons.
- [ ] Add diagnostic logging to `LuaToolManager` resolution and `LuaToolValidator` execution.
- [ ] Ensure logs include tool version, path, and any CLI errors for support troubleshooting.

## Verification Tasks
- [ ] **UI Test**: Verify table rendering and tool addition via file picker.
- [ ] **Manual Test**: Delete a tool binary from disk and verify it is marked "Invalid" in settings.
- [ ] **Manual Test**: Verify notifications appear when a project is opened with missing tools.
