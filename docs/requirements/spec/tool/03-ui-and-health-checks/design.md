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
- `ProjectActivity`: Periodically verify tool paths.
- Update `LuaTool.isValid` flag.

## Implementation Details

### Security Considerations
- All tool executions (for health checks/versioning) must use `GeneralCommandLine` with timeouts.

## Testing Strategy
- **UI Tests**: Test the inventory table and panels.
- **Monitoring Tests**: Verify that invalidating a file on disk updates the tool state.
