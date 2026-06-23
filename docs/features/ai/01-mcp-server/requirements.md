---
id: "AI-01"
title: "01: MCP Server Integration"
type: "feature"
status: "todo"
priority: "high"
parent_id: "AI"
folders:
  - "[[features/ai/01-mcp-server/requirements|requirements]]"
---

# AI-01: MCP Server Integration

## Overview
Exposes Lua-specific development tools directly to the built-in JetBrains MCP server. This allows AI agents to inspect, manage, and verify Lua environments programmatically.

## Scope

### In Scope
- `LuaRocksToolset`: `luarocks_search`, `luarocks_install`, `luarocks_list`, `luarocks_make`, `luarocks_rockspecs`.
- `LuaExecutionToolset`: `run_lua_file`, `execute_lua_in_console`.
- Safe execution bounds via `checkUserConfirmationIfNeeded` (Brave Mode).
- Project-local LuaRocks tree only (`lua_modules` / `.luarocks`); `--local` forced on install.
- Shared subprocess utility `LuaRocksCli` extracted from `LuaRocksRunConfiguration.buildCommandLine()`.
- LUA_PATH injection via existing `LuaProjectSettings.expandSourcePath()`.

### Out of Scope
- Global/system LuaRocks installations (deferred: AI-01-05).
- Interactive debugger controllers (stepping/breakpoints).
- `LuaToolManager` integration for `LuaRocksSearchService` (deferred to ROCKS wave 2).

## Functional Requirements

| ID | Requirement | Priority | Description |
|---|---|---|---|
| AI-01-01 | **Optional Plugin Registration** | M | The MCP toolset extensions must only load if the `com.intellij.mcpServer` plugin is active. |
| AI-01-02 | **Project-Bound Dependency Tools** | M | Expose search, installation, listing, building, and rockspec discovery for project-bound LuaRocks instances. |
| AI-01-03 | **Execution Confirmation** | M | Script and console commands must prompt for confirmation unless Brave Mode is enabled in settings. |
| AI-01-04 | **Project Interpreter Execution** | M | Script execution must run under the project-configured Lua interpreter SDK with LUA_PATH injected. |
| AI-01-05 | **Global Rock Support** | C | Extend `luarocks_list` and install path resolution to cover system-global rocks (currently project-local only). |

## Detailed Specifications

### AI-01-01: Optional Plugin Registration
The Lunar plugin must define the extension classes compiling against the `com.intellij.mcpServer` classpath. Runtime loading of `LuaRocksToolset` and `LuaExecutionToolset` must be delegated to an optional plugin configuration file (`lunar-mcp.xml`), loaded only when `com.intellij.mcpServer` is active. The Gradle build adds `com.intellij.mcpServer` to `platformBundledPlugins`.

### AI-01-02: Project-Bound Dependency Tools

The `LuaRocksToolset` exposes five tools:

| Tool | Internal source | Return type |
|---|---|---|
| `luarocks_search(query: String)` | `LuaRocksSearchService.search(query)` (shells out to `luarocks search --porcelain`) | Structured result with name, version, arch, repo, namespace, isInstalled |
| `luarocks_install(package_name: String, version: String? = null)` | Shared `LuaRocksCli` spawning `luarocks install --local <pkg>` | `String` (stdout/stderr from subprocess) |
| `luarocks_list()` | `LuaRocksTreeLocator.installedRocks(project)` | Structured result with name, version, rockspec path (install directory derivable) |
| `luarocks_make(rockspec_path: String? = null)` | Shared `LuaRocksCli` spawning `luarocks make` in the rockspec's parent directory (or project root if absent). No `--local` flag — `make` builds, doesn't install. | `String` (stdout/stderr) |
| `luarocks_rockspecs()` | `LuaRockspecDiscoveryService.discoverRockspecPaths()` | Structured result with path, packageName |

Binary resolution: reads `LuaRocksSettings.getInstance().executablePath` directly (matches existing `LuaRocksSearchService` pattern). `LuaToolManager.getEffectiveTool()` integration deferred to ROCKS wave 2.

### AI-01-03: Execution Confirmation
`run_lua_file` and `execute_lua_in_console` must call `com.intellij.mcpserver.util.checkUserConfirmationIfNeeded(notificationText, commandString, project)` before spawning. The platform handles Brave Mode — if enabled, the call proceeds silently; if disabled, a modal dialog appears. Denial throws `McpExpectedError`.

### AI-01-04: Project Interpreter Execution
`run_lua_file` builds a `GeneralCommandLine` via `newProjectLuaInterpreterCommandLine(project)`, adds the script path and arguments, injects LUA_PATH from `LuaProjectSettings.expandSourcePath(project)`, prepends tool directories via `LuaToolEnvironment.prependToolDirsToPath()`, then captures output with `CapturingProcessHandler`. Working directory: project root (LUA_PATH handles module resolution).

`execute_lua_in_console` writes the code to a temp file in `PathManager.getTempDir()`, runs it with the same interpreter setup, captures output, and deletes the temp file in a `finally` block.

### AI-01-05: Global Rock Support (Could)
Deferred. Currently `LuaRocksTreeLocator` only discovers project-local trees. Future: merge with `luarocks list --porcelain` output or add `luarocks show --home` per package for global install discovery.

## Behavior Rules
- `luarocks_install` always appends `--local` to keep installs in the project tree.
- All subprocess calls use `withContext(Dispatchers.IO)` wrapping the blocking `LuaProcessUtil.capture()`.
- Toolsets are marked `isExperimental() = true` until validated with real LLM traffic.
- The shared `LuaRocksCli` utility uses `suspend` functions, internally dispatching blocking I/O.
- `luarocks_make` working directory: the parent of `rockspec_path` if specified, else `project.basePath`.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|---|---|---|---|
| 1 | AI-01-02 | An empty project with a standard `luarocks` executable path | Tool call `luarocks_install` with `package_name="busted"` | Returns `[success]`; `busted` installed under `lua_modules`. |
| 2 | AI-01-02 | A project with a `.rockspec` at the root | Tool call `luarocks_rockspecs` | Returns list containing that rockspec's path and parsed package name. |
| 3 | AI-01-03 | A workspace with Brave Mode **disabled** | Tool call `run_lua_file` with `file_path="main.lua"` | IDE displays confirmation dialog. Denial throws `User rejected command execution`. |
| 4 | AI-01-03 | A workspace with Brave Mode **enabled** | Tool call `run_lua_file` with `file_path="main.lua"` | Script executes immediately and returns stdout. |

## Acceptance Criteria
- [ ] No class loading failures occur when Lunar runs in an IDE lacking the MCP Server plugin (AI-01-01).
- [ ] `luarocks_list` reflects rocks in the project-local `lua_modules` tree (AI-01-02).
- [ ] `luarocks_rockspecs` returns all `.rockspec` files in the project (AI-01-02).
- [ ] Confirmation dialog triggers for all script executions under default settings (AI-01-03).

## Dependencies
- **TOOL**: `LuaToolManager`, `LuaToolEnvironment` (PATH injection), `LuaToolType.LUAROCKS`.
- **Rocks**: `LuaRocksSettings`, `LuaRocksSearchService`, `LuaRocksTreeLocator`, `LuaRockspecDiscoveryService`, `RockspecBridge`.
- **Command**: `LuaCommandLine.newProjectLuaInterpreterCommandLine()`, `LuaProcessUtil`.
- **Settings**: `LuaProjectSettings.expandSourcePath()`.
- **Platform**: `com.intellij.mcpServer` (optional dependency), `checkUserConfirmationIfNeeded`.