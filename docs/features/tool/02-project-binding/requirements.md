---
id: TOOL-02
title: "02: Project Binding & Environment Integration"
type: feature
parent_id: TOOL
status: "done"
priority: "high"
folders:
  - "[[features/tool/requirements|requirements]]"
---

# Project Binding & Environment Integration (`TOOL-02`)

## Scope
The Project Binding feature allows users to assign specific tools from the registry to a project and ensures those tools are available in the project's execution environment.

### In Scope
- Binding specific tool versions to projects.
- Setting global default tools.
- Injecting tool paths into the environment (PATH) for integrated terminals and build tasks.

## Syntax/Behavior

### Tool Binding
Tools can be bound at Global and Project levels.

### Path Augmentation
The directory containing the bound tool's binary is prepended to the PATH for the Terminal and Run configurations.

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| **TOOL-02-01** | **Per-Project Tool Binding** | **M** | **Full** | `LuaProjectSettings.State.projectToolBindings` / `LuaApplicationSettings.State.globalToolBindings` (keyed by `LuaToolType.name` → `LuaTool.id`); set via `LuaToolManager.setGlobalBinding` / `LuaProjectSettings.setProjectToolBindingAndNotify`. Persisted in `lunar.xml` (round-trip tested). |
| **TOOL-02-02** | **Context-Aware Invocation** | **M** | **Full** | `LuaToolManager.getEffectiveTool(project, type)` resolves the project-bound binary; ROCKS/run paths consume it via the env service + cmdline patcher. |
| **TOOL-02-03** | **PATH Augmentation** | **M** | **Full** | `LuaToolEnvironment.prependToolDirsToPath` prepends effective tool dirs to `GeneralCommandLine` PATH; wired into `newProjectLuaInterpreterCommandLine`. (No platform `EnvironmentProvider`; `RunConfigurationExtension` is in the Java module, not on this plugin's classpath — direct cmdline patch used instead.) |
| **TOOL-02-04** | **Terminal Integration** | **M** | **Full** | `LuaShellExecOptionsCustomizer` prepends the env service's tool dirs via `prependEntryToPATH`. Per-shell live-terminal matrix (TOOL-00-01) is **manual-verification only** (not headlessly testable). |
| **TOOL-02-05** | **IDE Action Integration** | **M** | **Full** | IDE actions resolve via `LuaToolManager.getEffectiveTool(project, type)` (single resolution entry point shared with run/terminal). |
| **TOOL-02-06** | **Fallback Mechanism** | **M** | **Full** | `getEffectiveTool` precedence project > global > first-valid, skipping bound ids that no longer resolve to a valid tool (stale-binding fallback tested). |

## Test Cases

### TC-TOOL-03: Project Binding Persistence
- **Input**: Bind "LuaRocks 3.7.0" to "Project A". Restart IDE.
- **Expected Output**: "Project A" still has "LuaRocks 3.7.0" bound.

### TC-TOOL-04: Terminal PATH Augmentation
- **Input**: "Project B" has `/opt/lua-tools/bin/luarocks` bound. Open Terminal.
- **Action**: Run `which luarocks`.
- **Expected Output**: Output shows `/opt/lua-tools/bin/luarocks`.
