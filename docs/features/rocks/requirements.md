---
folders:
  - "[[features]]"
title: "ROCKS: LuaRocks Integration"
priority: high
status: planned
---

# LuaRocks Integration Requirements (`ROCKS`)

Lunar provides deep integration with LuaRocks for dependency management, package discovery, and project lifecycle automation.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| [`ROCKS-01`](01-project-initialization/requirements.md) | **Project Initialization & Setup** | **M** | **Not Implemented** | Scaffolding, Rockspec generation, and module resolution. |
| [`ROCKS-02`](02-package-browser/requirements.md) | **Package Browser** | **S** | **Not Implemented** | Remote search and repository exploration (split-view). |
| [`ROCKS-03`](03-dependency-resolution/requirements.md) | **Dependency Resolution** | **M** | **Not Implemented** | Hierarchical tree view and conflict detection. |
| [`ROCKS-04`](04-task-execution/requirements.md) | **Task Execution & Run Configurations** | **M** | **Not Implemented** | Target-based command execution (similar to Maven/Makefile). |
| `ROCKS-08` | **Publishing & Lifecycle** | **C** | **Not Implemented** | Wizard for versioning and remote uploads. |

---

## Motivation
Managing Lua dependencies manually is error-prone and lacks IDE visibility. Integrating LuaRocks directly into the workflow provides a standardized ecosystem for package management, reducing context switching between the terminal and the editor.

## Benefits
- **Efficiency**: Faster dependency management through parallel operations and IDE-native UI.
- **Reliability**: Semantic versioning validation and conflict detection.
- **Traceability**: Full visibility into package origins and transitive dependencies.
- **Workflow Integration**: Automatic path configuration and rockspec maintenance.

## Detailed Implementation Status

### ROCKS-01: Project Initialization & Setup
| ID | Requirement | Implementation |
| :--- | :--- | :--- |
| `ROCKS-01-01` | Rockspec Generation | `LuaRockspecGenerator` |
| `ROCKS-01-02` | Project Scaffolding | `LuaRocksProjectTemplates` |
| `ROCKS-01-03` | Module Resolution | `LuaRocksPathResolver` |

### ROCKS-04: Task Execution & Run Configurations
| ID | Requirement | Implementation |
| :--- | :--- | :--- |
| `ROCKS-04-01` | Command Execution | `LuaRocksCommandLine` |
| `ROCKS-04-02` | Run Configuration | `LuaRocksRunConfiguration` |
