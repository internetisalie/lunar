---
id: TOOL-02-DESIGN
title: "Technical Design"
type: design
parent_id: TOOL-02
status: "planned"
priority: "high"
folders:
  - "[[features/tool/02-project-binding/requirements|requirements]]"
---

# Technical Design: Project Binding & Environment Integration (`TOOL-02`)

## Overview
This document outlines how tools are bound to projects and integrated into the execution environment.

## Architecture

### 1. Storage

#### Global Defaults (`LuaApplicationSettings`)
Global tool preferences used when no project override is present.
```kotlin
class State {
    var globalToolBindings: MutableMap<LuaToolType, String?> = mutableMapOf()
}
```

#### Project Bindings (`LuaProjectSettings`)
Project-specific tool overrides. Stored in `.idea/lunar.xml`, allowing bindings to be shared via Version Control (VCS) across teams (Enterprise Management).
```kotlin
class State {
    var projectToolBindings: MutableMap<LuaToolType, String?> = mutableMapOf()
}
```

### 2. Services

#### `LuaToolManager` (Resolution)
- `getEffectiveTool(project: Project?, type: LuaToolType): LuaTool?`: Resolves Project override > Global default.
- Fallback logic: If a project-bound tool ID is invalid or missing, it automatically attempts to return the Global default for that type.

#### `LuaTerminalEnvironmentService` (Project-level Service)
- **Scoping**: Defined as a Project-level Service to avoid memory leaks and ensure automatic cleanup.
- **Caching**: Caches the calculated `PATH` modification for the project.
- **Invalidation**: Subscribes to the IntelliJ **Message Bus** to invalidate the cache immediately when tool settings change.

### 3. Integration Points

#### IDE Actions (Formatting/Linting)
- Features like `LuaFormattingModelBuilder` or `LuaCheckAnnotator` will call `LuaToolManager.getEffectiveTool()` to locate the binary.
- This ensures consistency between manual terminal use and IDE-automated actions.

#### Language Server & Debugger Integration
- If the plugin spawns a Language Server (LSP) or a debug adapter that depends on external tools (e.g., for completion or static analysis), the `LuaToolEnvironmentProvider` must be used to patch the environment of these server processes.
- This addresses the "auto-complete injection" requirement by ensuring the server sees the project-bound tool versions.

## Implementation Details

### PATH Augmentation
- `LuaToolEnvironmentProvider`: Implements `EnvironmentProvider` to prepend tool directories.
- Terminal integration: Use `LocalTerminalDirectRunner` extension to inject `PATH`.

## Testing Strategy
- **Integration Tests**: Verify binding retrieval and resolution logic.
- **Environment Tests**: Verify `PATH` in spawned processes and terminal.
