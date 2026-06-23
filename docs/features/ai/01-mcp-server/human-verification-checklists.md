---
id: "AI-01-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "todo"
parent_id: "AI-01"
folders:
  - "[[features/ai/01-mcp-server/requirements|requirements]]"
---

# Verification Checklists: AI-01 — MCP Server Integration

## 1. Execution Security (Brave Mode)

### Scenario 1.1: Default Sandbox Prompt
- **Setup**: Open the plugin sandbox, configure standard Lua interpreter SDK, disable Brave Mode in MCP Server settings.
- **Steps**:
  1. Trigger `run_lua_file` with `file_path="main.lua"` (any valid Lua file).
  2. When the confirmation dialog appears, click **Deny**.
- **Expected**: Execution terminates immediately; the tool returns an error: `User rejected command execution`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Brave Mode Skip Prompt
- **Setup**: Enable Brave Mode in MCP Server settings.
- **Steps**:
  1. Trigger `run_lua_file` with `file_path="main.lua"`.
- **Expected**: Script runs instantly without prompting, returning stdout/stderr. No dialog appears.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: `execute_lua_in_console` Confirmation
- **Setup**: Brave Mode disabled. A valid Lua interpreter configured.
- **Steps**:
  1. Trigger `execute_lua_in_console` with `code="print('hello')"`.
  2. Click **Allow** in the confirmation dialog.
- **Expected**: Returns `hello\n`. No temp file remains in the temp directory.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. LuaRocks Tools

### Scenario 2.1: Install a Rock
- **Setup**: A project with `luarocks` on PATH, no prior installs.
- **Steps**:
  1. Trigger `luarocks_install` with `package_name="say"`.
- **Expected**: Subprocess completes; `say` is installed in `lua_modules/`. `luarocks_list` shows `say` with the correct version and install path.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: Discover Rockspecs
- **Setup**: A project with at least one `.rockspec` file.
- **Steps**:
  1. Trigger `luarocks_rockspecs`.
- **Expected**: Returns a list containing the rockspec's relative path and parsed package name. Non-rockspec files are excluded.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.3: `luarocks_make` with Rockspec Path
- **Setup**: A project with a `.rockspec` file in a subdirectory (`sub/project-scm-1.rockspec`).
- **Steps**:
  1. Trigger `luarocks_make` with `rockspec_path="sub/project-scm-1.rockspec"`.
- **Expected**: `luarocks make` runs in `sub/` (the rockspec's parent directory). No `--local` flag in the command.
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Optional Plugin Loading

### Scenario 3.1: No MCP Server Plugin Present
- **Setup**: A GoLand instance WITHOUT the `com.intellij.mcpServer` plugin (or the plugin disabled).
- **Steps**:
  1. Install Lunar.
  2. Open any Lua project.
- **Expected**: Lunar loads and functions normally. No `NoClassDefFoundError`. The MCP tools are simply not registered.
- **Result**: ⬜ Pass / ⬜ Fail