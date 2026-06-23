---
id: "AI-01-DESIGN"
title: "Technical Design: AI-01"
type: "design"
status: "todo"
parent_id: "AI-01"
folders:
  - "[[features/ai/01-mcp-server/requirements|requirements]]"
---

# Technical Design: AI-01 — MCP Server Integration

## 1. Architecture Overview

### Prior Art in This Repo
- **`net.internetisalie.lunar.rocks.run.LuaRocksSettings`** — stores `executablePath` for `luarocks` binary. Used as the binary resolution source for all MCP LuaRocks tools.
- **`net.internetisalie.lunar.rocks.browser.LuaRocksSearchService`** — shells out to `luarocks search --porcelain` and parses results into `LuaRockPackage` objects. Reused directly by `luarocks_search`.
- **`net.internetisalie.lunar.rocks.LuaRocksTreeLocator`** — walks `lua_modules/lib/luarocks/rocks-*/` to enumerate `InstalledRock` entries. Reused by `luarocks_list`.
- **`net.internetisalie.lunar.rocks.LuaRockspecDiscoveryService`** — discovers `.rockspec` files via `FilenameIndex.getAllFilesByExt`, filtered by `RockspecExclusionFilter`. Reused by `luarocks_rockspecs`.
- **`net.internetisalie.lunar.command.LuaCommandLine`** — `newProjectLuaInterpreterCommandLine(project)` builds a `GeneralCommandLine` with the project interpreter, LUA_PATH, and tool PATH prepended. Reused by `run_lua_file` and `execute_lua_in_console`.
- **`net.internetisalie.lunar.tool.LuaToolEnvironment`** — `prependToolDirsToPath(cmd, project)` prepends tool binary directories to PATH. Applied to all subprocess calls.
- **`net.internetisalie.lunar.util.LuaProcessUtil`** — `capture(cmd, timeout): ProcessOutput` is the blocking subprocess runner. Wrapped in `withContext(Dispatchers.IO)` in the MCP tools.
- **`net.internetisalie.lunar.rocks.run.LuaRocksRunConfiguration.buildCommandLine()`** — the existing command-line assembly for `luarocks` subcommands. Will be extracted into the new shared `LuaRocksCli` utility.

### Target State
```
       [JetBrains MCP Server Runtime]
                   |
     +-------------+-------------+
     | (com.intellij.mcpServer.mcpToolset EP)
     v                           v
[LuaRocksToolset]       [LuaExecutionToolset]
     |                           |
     |-- luarocks_search → LuaRocksSearchService
     |-- luarocks_list   → LuaRocksTreeLocator
     |-- luarocks_rockspecs → LuaRockspecDiscoveryService
     |-- luarocks_install/  |-- run_lua_file → GeneralCommandLine
         luarocks_make      |-- execute_lua_in_console → temp file + GeneralCommandLine
         → LuaRocksCli          |
                            checkUserConfirmationIfNeeded (Brave Mode gate)
```

### Decision Log
| Question | Decision | Rationale |
|---|---|---|
| `execute_lua_in_console` impl | Fresh one-shot subprocess via temp file | Stateless, predictable, no REPL dependency |
| Shared LuaRocks utility | Extract `LuaRocksCli` in `net.internetisalie.lunar.command/` | Follows `LuaCommandLine.kt` pattern, suspend API |
| `luarocks_make` working dir | Optional `rockspec_path` param, default project root | Flexibility for monorepos/subprojects |
| `luarocks_search` data source | `LuaRocksSearchService.search()` (structured) | Already parsed, cached, battle-tested |
| Binary resolution | `LuaRocksSettings.executablePath` (for now) | `LuaToolManager` integration deferred to ROCKS wave 2 |
| `luarocks_install` scope | Always `--local` | Project-bound only, no global side effects |
| `luarocks_list` data source | `LuaRocksTreeLocator.installedRocks()` | Returns name, version, on-disk path; project-local only |
| Global rock support | Deferred (AI-01-05, Could) | TreeLocator only covers local trees |
| `run_lua_file` impl | `GeneralCommandLine` directly via `newProjectLuaInterpreterCommandLine` | Lighter than RunConfiguration framework; no producer exists |
| `execute_lua_in_console` temp file | `PathManager.getTempDir()`, delete in finally | `require` works via LUA_PATH injection |
| LUA_PATH injection | `LuaProjectSettings.expandSourcePath()` | Existing mechanism; ROCKS-05 will extend later |
| `isExperimental()` | `true` | Conservative; validate with real LLM traffic first |
| `luarocks_make --local` | Not injected | `make` builds, doesn't install |
| Tool installation timeout | None | MCP protocol cancellation handles this |

## 2. Core Components

### 2.1 `net.internetisalie.lunar.mcp.LuaRocksToolset`
- **Responsibility**: Exposes project-bound LuaRocks operations to the AI client.
- **Threading**: Suspending; dispatches blocking I/O to `withContext(Dispatchers.IO)`.
- **Collaborators**: `LuaRocksSearchService`, `LuaRocksTreeLocator`, `LuaRockspecDiscoveryService`, `LuaRocksCli`, `LuaRocksSettings`.
- **Key API**:
  ```kotlin
  class LuaRocksToolset : McpToolset {
      override fun isExperimental(): Boolean = true

      @McpTool
      @McpDescription("Searches the online LuaRocks database. Returns structured package info.")
      suspend fun luarocks_search(
          @McpDescription("Package name or keyword") query: String
      ): LuaRocksSearchResult

      @McpTool
      @McpDescription("Installs a package in the project workspace (always --local).")
      suspend fun luarocks_install(
          @McpDescription("Package name to install") package_name: String,
          @McpDescription("Optional version constraint") version: String? = null
      ): String

      @McpTool
      @McpDescription("Lists rocks installed in the project-local tree (lua_modules/.luarocks).")
      suspend fun luarocks_list(): LuaRocksListResult

      @McpTool
      @McpDescription("Builds from a rockspec. Defaults to project root if no path given.")
      suspend fun luarocks_make(
          @McpDescription("Optional project-relative path to a .rockspec file")
          rockspec_path: String? = null
      ): String

      @McpTool
      @McpDescription("Discovers all .rockspec files in the project.")
      suspend fun luarocks_rockspecs(): LuaRockspecsResult
  }
  ```

### 2.2 `net.internetisalie.lunar.mcp.LuaExecutionToolset`
- **Responsibility**: Safe script execution under project SDK.
- **Threading**: EDT for confirmation dialog; `Dispatchers.IO` for subprocess.
- **Collaborators**: `checkUserConfirmationIfNeeded`, `newProjectLuaInterpreterCommandLine`, `LuaProcessUtil`, `LuaToolEnvironment`.
- **Key API**:
  ```kotlin
  class LuaExecutionToolset : McpToolset {
      override fun isExperimental(): Boolean = true

      @McpTool
      @McpDescription("Runs a Lua script using the project's configured interpreter.")
      suspend fun run_lua_file(
          @McpDescription("Project-relative path to the Lua file") file_path: String,
          @McpDescription("Arguments to pass to the script") args: List<String> = emptyList()
      ): String

      @McpTool
      @McpDescription("Evaluates a Lua code snippet in a one-shot interpreter process.")
      suspend fun execute_lua_in_console(
          @McpDescription("Lua code to evaluate") code: String
      ): String
  }
  ```

### 2.3 `net.internetisalie.lunar.command.LuaRocksCli`
- **Responsibility**: Shared suspend utility for spawning `luarocks` subprocesses. Extracted from `LuaRocksRunConfiguration.buildCommandLine()`.
- **Threading**: `withContext(Dispatchers.IO)` wrapping `LuaProcessUtil.capture()`.
- **Collaborators**: `LuaRocksSettings`, `LuaToolEnvironment`.
- **Key API**:
  ```kotlin
  object LuaRocksCli {
      suspend fun run(
          project: Project,
          subcommand: String,
          args: List<String> = emptyList(),
          workingDirectory: String? = null,
      ) : ProcessOutput
  }
  ```

### 2.4 Serializable Result Types
```kotlin
@Serializable
data class LuaRocksSearchResult(
    val packages: List<LuaRockPackageInfo>
)
@Serializable
data class LuaRockPackageInfo(
    val name: String,
    val version: String,
    val arch: String,
    val repo: String,
    val namespace: String,
    val isInstalled: Boolean,
)

@Serializable
data class LuaRocksListResult(
    val installed: List<InstalledRockInfo>
)
@Serializable
data class InstalledRockInfo(
    val packageName: String,
    val version: String,
    val installPath: String,   // derived from rockspec.parent
)

@Serializable
data class LuaRockspecsResult(
    val rockspecs: List<RockspecInfo>
)
@Serializable
data class RockspecInfo(
    val path: String,
    val packageName: String?,
)
```

## 3. Algorithms

### 3.1 Process Execution with Brave Mode Validation
- **Input**: `(project, file_path, args)` or `(project, code)`.
- **Output**: Captured stdout/stderr as `String`.
- **Steps**:
  1. `currentCoroutineContext().project` → `targetProject`.
  2. For `run_lua_file`: resolve file path via `project.resolveInProject(file_path)`. Build command-line string representation for the confirmation dialog.
  3. Call `checkUserConfirmationIfNeeded(notificationText, commandString, targetProject)`.
  4. If accepted / Brave Mode: build `GeneralCommandLine` via `newProjectLuaInterpreterCommandLine(targetProject)`.
  5. For `run_lua_file`: add script path + args as parameters.
  6. For `execute_lua_in_console`: write code to temp file (`PathManager.getTempDir()/lunar_mcp_<uuid>.lua`), add as parameter.
  7. Inject PATH via `LuaToolEnvironment.prependToolDirsToPath(cmd, targetProject)`.
  8. Execute via `withContext(Dispatchers.IO) { LuaProcessUtil.capture(cmd) }`.
  9. For `execute_lua_in_console`: in `finally`, delete the temp file.
  10. On non-zero exit: `mcpFail("luarocks/lua exited ${exitCode}: ${stderr}")`.
  11. On success: return `stdout`.

### 3.2 `luarocks_list` — Tree Walking
- **Input**: `Project`.
- **Output**: `LuaRocksListResult`.
- **Steps**:
  1. Call `LuaRocksTreeLocator.installedRocks(project)`.
  2. Map each `InstalledRock` to `InstalledRockInfo(name, version, installPath=rockspec.parent)`.
  3. Return serialized `LuaRocksListResult`.
- **Edge case**: returns empty list if `LuaRocksTreeLocator.treeRoot(project)` is null (no `lua_modules` / `.luarocks`).

### 3.3 `luarocks_rockspecs` — Discovery
- **Input**: `Project`.
- **Output**: `LuaRockspecsResult`.
- **Steps**:
  1. Call `LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths()`.
  2. Map each `DiscoveredRockspec` to `RockspecInfo(path=relative, packageName)`.
  3. Return serialized `LuaRockspecsResult`.
- **Edge case**: returns empty list when dumb mode (`DumbService.isDumb`) — discovery is deferred.

## 4. Integration Points

Add to `src/main/resources/META-INF/plugin.xml`:
```xml
<depends optional="true" config-file="lunar-mcp.xml">com.intellij.mcpServer</depends>
```

Create `src/main/resources/META-INF/lunar-mcp.xml`:
```xml
<idea-plugin>
  <extensions defaultExtensionNs="com.intellij.mcpServer">
    <mcpToolset implementation="net.internetisalie.lunar.mcp.LuaRocksToolset"/>
    <mcpToolset implementation="net.internetisalie.lunar.mcp.LuaExecutionToolset"/>
  </extensions>
</idea-plugin>
```

Update `gradle.properties`:
```properties
platformBundledPlugins = org.jetbrains.plugins.terminal, com.intellij.mcpServer
```

## 5. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|---|---|---|
| AI-01-01 | M | §4 (optional dependency, lunar-mcp.xml) |
| AI-01-02 | M | §2.1 (LuaRocksToolset), §2.3 (LuaRocksCli) |
| AI-01-03 | M | §2.2, §3.1 (checkUserConfirmationIfNeeded) |
| AI-01-04 | M | §2.2, §3.1 (newProjectLuaInterpreterCommandLine) |
| AI-01-05 | C | Deferred; no design section |

## 6. Open Questions
_None — feature has cleared the planning bar._