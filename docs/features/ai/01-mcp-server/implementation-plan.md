---
id: "AI-01-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "AI-01"
folders:
  - "[[features/ai/01-mcp-server/requirements|requirements]]"
---

# AI-01: Implementation Plan

## Phases

### Phase 1: Build & Declarations [Must]
- **Goal**: Enable compilation and load conditions for the MCP plugins.
- **Tasks**:
  - [ ] Add `com.intellij.mcpServer` to `platformBundledPlugins` in `gradle.properties`.
  - [ ] Add `<depends optional="true" config-file="lunar-mcp.xml">com.intellij.mcpServer</depends>` to `plugin.xml`.
  - [ ] Create `src/main/resources/META-INF/lunar-mcp.xml` registering both toolsets.
  - [ ] Create empty `LuaRocksToolset` and `LuaExecutionToolset` skeleton classes under `net.internetisalie.lunar.mcp/` (compiles but does nothing).
- **Exit criteria**: `./gradlew build` succeeds; plugin loads without error in an IDE with the MCP Server plugin.

### Phase 2: Shared Utility — `LuaRocksCli` [Must]
- **Goal**: Extract the `luarocks` subprocess-spawning logic from `LuaRocksRunConfiguration.buildCommandLine()` into a reusable suspend utility.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.command.LuaRocksCli` (design §2.3).
  - [ ] Implement `suspend fun run(project, subcommand, args, workingDirectory): ProcessOutput` using `LuaRocksSettings.executablePath`, `GeneralCommandLine`, `withContext(Dispatchers.IO)`, `LuaProcessUtil.capture()`, and `LuaToolEnvironment.prependToolDirsToPath()`.
  - [ ] Port `LuaRocksRunConfiguration.buildCommandLine()` to call `LuaRocksCli` internally (or verify it can remain independent; MCP tools call `LuaRocksCli` directly).
- **Exit criteria**: `LuaRocksCli` unit test: spawns `luarocks --version` and captures non-empty stdout.

### Phase 3: `LuaRocksToolset` Implementation [Must]
- **Goal**: Implement all five tools (design §2.1).
- **Tasks**:
  - [ ] `luarocks_search`: call `LuaRocksSearchService.search(query)`, map `LuaRockPackage` → `LuaRockPackageInfo`, return `LuaRocksSearchResult`. (design §2.4)
  - [ ] `luarocks_list`: call `LuaRocksTreeLocator.installedRocks(project)`, map `InstalledRock` → `InstalledRockInfo` (deriving `installPath` from `rockspec.parent`), return `LuaRocksListResult`.
  - [ ] `luarocks_rockspecs`: call `LuaRockspecDiscoveryService.discoverRockspecPaths()`, map to `RockspecInfo`, return `LuaRockspecsResult`.
  - [ ] `luarocks_install`: call `LuaRocksCli.run(project, "install", listOf("--local", package_name) + versionArgs)`. Return stdout. `mcpFail` on non-zero exit.
  - [ ] `luarocks_make`: resolve working directory from `rockspec_path` parent (or `project.basePath`), call `LuaRocksCli.run(project, "make", args, workingDir)`. Return stdout.
- **Exit criteria**: Unit test for each tool; `luarocks_list` returns installed rocks when a `lua_modules` tree exists.

### Phase 4: `LuaExecutionToolset` Implementation [Must]
- **Goal**: Implement safe script and code execution (design §2.2, §3.1).
- **Tasks**:
  - [ ] `run_lua_file`: resolve file path, build command string, call `checkUserConfirmationIfNeeded(...)`, build `GeneralCommandLine` via `newProjectLuaInterpreterCommandLine(project)`, add script path + args, inject PATH, capture output via `LuaProcessUtil.capture()`, `mcpFail` on error.
  - [ ] `execute_lua_in_console`: write code to temp file (`PathManager.getTempDir()/lunar_mcp_<uuid>.lua`), run via same interpreter path, capture output, delete temp file in `finally`.
- **Exit criteria**: Sandbox verification: Brave Mode disabled → confirmation dialog appears; Brave Mode enabled → script runs silently. Temp file cleanup verified.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|---|---|---|
| AI-01-01 | M | Phase 1 |
| AI-01-02 | M | Phase 2 + Phase 3 |
| AI-01-03 | M | Phase 4 |
| AI-01-04 | M | Phase 4 |
| AI-01-05 | C | Deferred |

## Verification Tasks
- [ ] Unit test: `LuaRocksCli.run()` with `luarocks --version` (Phase 2).
- [ ] Unit test: `luarocks_search` returns structured results for a known package name (Phase 3).
- [ ] Unit test: `luarocks_list` reflects installed rocks in test fixture (Phase 3).
- [ ] Unit test: `luarocks_rockspecs` discovers `.rockspec` files in test fixture (Phase 3).
- [ ] Sandbox manual: Brave Mode prompt for `run_lua_file` / `execute_lua_in_console` (Phase 4).
- [ ] Sandbox manual: temp file cleanup after `execute_lua_in_console` (Phase 4).
- [ ] Run `human-verification-checklists.md`.

## Task Summary

| Phase | Status | Priority |
|---|---|---|
| Phase 1: Build & Declarations | todo | Must |
| Phase 2: Shared Utility `LuaRocksCli` | todo | Must |
| Phase 3: `LuaRocksToolset` | todo | Must |
| Phase 4: `LuaExecutionToolset` | todo | Must |