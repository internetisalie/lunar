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
| **TOOL-02-01** | **Per-Project Tool Binding** | **M** | **Not Implemented** | Users can bind a registered tool to a specific project or set it as global. |
| **TOOL-02-02** | **Context-Aware Invocation** | **M** | **Not Implemented** | When running ROCKS feature operations, the IDE uses the project-bound tool binary. |
| **TOOL-02-03** | **PATH Augmentation** | **M** | **Not Implemented** | Project-bound tool binary directories are prepended to PATH for subprocesses. |
| **TOOL-02-04** | **Terminal Integration** | **M** | **Not Implemented** | Integrated terminals automatically have the project-bound tool binary directories prepended to PATH. |
| **TOOL-02-05** | **IDE Action Integration** | **M** | **Not Implemented** | IDE actions (formatting, linting) resolve and use the bound tool. |
| **TOOL-02-06** | **Fallback Mechanism** | **M** | **Not Implemented** | Fall back to global tools if project tools are missing/invalid. |

## Test Cases

### TC-TOOL-03: Project Binding Persistence
- **Input**: Bind "LuaRocks 3.7.0" to "Project A". Restart IDE.
- **Expected Output**: "Project A" still has "LuaRocks 3.7.0" bound.

### TC-TOOL-04: Terminal PATH Augmentation
- **Input**: "Project B" has `/opt/lua-tools/bin/luarocks` bound. Open Terminal.
- **Action**: Run `which luarocks`.
- **Expected Output**: Output shows `/opt/lua-tools/bin/luarocks`.
