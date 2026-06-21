---
id: "ROCKS"
title: "ROCKS: LuaRocks Integration"
type: "epic"
status: "in_progress"
priority: "high"
folders:
  - "[[features]]"
---

# LuaRocks Integration Requirements (`ROCKS`)

Lunar provides deep integration with LuaRocks for dependency management, package discovery, and project lifecycle automation.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| [`ROCKS-01`](01-project-initialization/requirements.md) | **Project Initialization & Setup** | **M** | **Full** | Scaffolding, Rockspec generation, and module resolution. |
| [`ROCKS-02`](02-package-browser/requirements.md) | **Package Browser** | **S** | **Full** | Remote search and repository exploration (split-view). |
| [`ROCKS-03`](03-dependency-resolution/requirements.md) | **Dependency Resolution** | **M** | **Full** | Hierarchical tree view and conflict detection. |
| [`ROCKS-04`](04-task-execution/requirements.md) | **Task Execution & Run Configurations** | **M** | **Full** | Target-based command execution (similar to Maven/Makefile). |
| [`ROCKS-05`](05-module-resolution/requirements.md) | **Rockspec Module Resolution** | **S** | **Not Implemented** | Derive source-path patterns from rockspec `build.modules` for require resolution, completion, and indexing. |
| `ROCKS-08` | **Publishing & Lifecycle** | **C** | **Full** | Wizard for versioning and remote uploads. |
| [`ROCKS-09`](09-workspace-discovery/requirements.md) | **Multi-Rock Workspace Discovery** | **M** | **Not Implemented** | Recursively discover all project rockspecs (replaces single-root `projectRockspec`); foundational for multi-rock resolution. |

---

## Relationship to the TOOL track

**ROCKS is independent of the TOOL track for Wave 10** — the two tracks (TOOL inventory, ROCKS
integration) are designed to be implemented and shipped in parallel. Every ROCKS feature resolves the
`luarocks` binary from its own `LuaRocksSettings.executablePath` (a `@Service(APP)` defined in ROCKS-04,
default `luarocks` on `PATH`), **not** from `LuaToolManager` / the TOOL registry. The designs were
corrected (2026-06-16 grounding audit) to remove the earlier `LuaToolManager` references; some
requirements prose still mentioned TOOL-01/02 and has been reconciled to match.

**Future integration (post-Wave-10, not a dependency):** once both tracks exist, ROCKS can consume the
project-bound `luarocks` from TOOL-01/02 (single source of truth, version enforcement) instead of its own
`executablePath`. Until then, configuring `luarocks` in both places is an accepted, deliberate trade-off
that preserves track independence.

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
