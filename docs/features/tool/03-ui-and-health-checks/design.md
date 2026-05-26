---
id: "TOOL-03-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TOOL-03"
status: "planned"
priority: "high"
folders:
  - "[[features/tool/03-ui-and-health-checks/requirements|requirements]]"
---

# Technical Design: UI/UX & Health Monitoring (`TOOL-03`)

## Overview
This document outlines the UI components and background monitoring for tools.

## Architecture

### 1. UI Components

#### `LuaToolInventoryPanel`
- Tab in Global Settings.
- Table for Registered Tools (Add, Edit, Remove, Auto-Detect).

#### `LuaProjectToolPanel`
- Section in Project Settings.
- Dropdowns for binding tools per type.

### 2. Monitoring

#### Health Checks
- **Two-Stage Verification**:
    1. **Fast Check**: Verify `File.exists()` and `File.canExecute()` via `LocalFileSystem`.
    2. **Slow Check**: Execute binary (`--version`) only if the fast check passes and the file's `mtime` (last modified) has changed.
- **Reactive Monitoring**:
    - Use `VirtualFileListener` or `AsyncFileListener` to track tool binaries.
    - Instantly invalidate tools and notify users if binaries are moved/deleted.

#### User Notifications
- **Editor Banners**: Use contextual banners for project-specific tool issues (less intrusive than balloons).
- **Settings UI**: Display specific error reasons (e.g., "Binary missing", "Permission denied") in tooltips.

## Testing Strategy
- **UI Tests**: Test the inventory table and panels.
- **Monitoring Tests**: Verify that invalidating a file on disk updates the tool state.
