---
id: "RUN-01"
title: "RUN-01: Lua Interpreter SDK"
type: "feature"
status: "done"
priority: "medium"
parent_id: "DEBUG/RUN"
folders: ["[[features/debug/requirements|requirements]]"]
---

# RUN-01: Lua Interpreter SDK

## Overview
Lunar models, discovers, identifies, persists, and surfaces Lua interpreters so that every
downstream feature (run/debug, REPL, test runner, external tools) can resolve a concrete
executable and language level. Because Lunar plugs into host IDEs (GoLand/PyCharm/CLion) that
expose no Lua `SdkType`, the SDK is a self-contained, serializable `LuaInterpreter` model rather
than a platform `Sdk`. The model is auto-discovered on the search path, identified via the
interpreter's `-v` banner, kept in an application-level inventory, selected per project, edited
through a settings table and a reusable combo box, and turned into a `GeneralCommandLine` for
execution. This document specifies the as-built feature.

## Scope

### In Scope
- **Interpreter model**: a serializable `LuaInterpreter` with derived `valid`/`family`/`executable`.
- **Family registry**: a static registry of known products (Lua, LuaJIT, Tarantool) with per-family
  metadata, binary type (system binary vs. Java jar), platform, and a version→language-level mapping.
- **Auto-discovery**: scan the platform search path for known executables.
- **Identification**: run `<exe> -v`, parse the banner, and fill in product/version/platform/level.
- **Persistence**: an application-level inventory and a per-project selection, both in `lunar.xml`.
- **Settings UI**: a table to add/edit-path/re-scan/delete interpreters.
- **Selection UI**: a reusable combo box with background inspection of free-typed paths.
- **Command-line construction**: build a `GeneralCommandLine` from a selected interpreter,
  including the `.jar` → `java -cp <jar> lua` branch.
- **Helpers**: environment-variable substitution and glob-based executable matching.

### Out of Scope
- External *tool* binaries (luacheck/luarocks/stylua) — owned by the `TOOL` epic (`LuaTool`/
  `LuaToolManager`); this feature does not duplicate that inventory.
- Run/Debug configuration types and DBGp wiring — consumers of this SDK, specified under RUN-02+.
- Remote/SSH interpreters and containerized interpreters.
- Automatic installation of interpreters.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| RUN-01-01 | **Interpreter Model** | M | Full | Provide a serializable `net.internetisalie.lunar.platform.LuaInterpreter` data class holding `path`, `banner`, `product`, `version`, `platform`, `languageLevel`, with copy and `Path` constructors and derived `valid` (`product != INVALID_PRODUCT`), `family`, `familyOrUnknown`, and `executable` (resolved `VirtualFile`) accessors. |
| RUN-01-02 | **Family Registry** | M | Full | Provide `LuaInterpreterFamily` with a static `FAMILIES` map (Lua, LuaJIT, Tarantool) keyed by product name plus an `UNKNOWN_INTERPRETER` singleton; each family carries `interpreterName`, `executableName`, `productName`, `BinaryType`, `LuaPlatform`, a version `leveler`, and `argExecCode`/`argLoadLib`. Resolution via `find(productName, executableName)` and `findByInterpreterName`. |
| RUN-01-03 | **Auto-Discovery** | M | Full | `LuaInterpreterService.findInterpreters()` scans the platform search path (`PATHS_UNIX`/`PATHS_WINDOWS` chosen by `SystemInfo.isWindows`), probes each known family's executable name (literal or glob) in each directory, validates the candidate's identity, and returns identified `LuaInterpreter`s. Missing directories/children yield no entries (no error). |
| RUN-01-04 | **Identification via `-v` Banner** | M | Full | `LuaInterpreterService.identify(interpreter)` runs `<exe> -v` (5 s timeout) and parses the version banner via `Banner.create` (stderr-first, first line, `VERSION_PATTERN = ^(\S+)\s+(\S+).*$`). On success it sets `product`, `version`, `path`, `languageLevel`, and `platform`; on any failure (non-zero exit, missing executable, unparseable banner, unknown family) the entry stays `INVALID_PRODUCT` (`valid == false`). |
| RUN-01-05 | **Application Inventory Persistence** | M | Full | `LuaApplicationSettings` (`@State(name="LuaApplicationSettings", storages=[Storage("lunar.xml")])`, registered as `<applicationService>`) persists `State.interpreters: List<LuaInterpreter>` and exposes `validInterpreters()` and `findInterpreter(path)`; the inventory round-trips across IDE restart. |
| RUN-01-06 | **Per-Project Selection Persistence** | M | Full | `LuaProjectSettings` (`@State(name="LuaProjectSettings", storages=[Storage("lunar.xml")])`) persists the selected `State.interpreter: LuaInterpreter?`; the selection round-trips across IDE restart and is read by command-line construction. |
| RUN-01-07 | **Settings Table** | M | Full | `LuaInterpretersTable` (extends `com.intellij.execution.util.ListTableWithButtons<LuaInterpreter>`) hosts the inventory in the "Lua" `applicationConfigurable`, with an editable Executable column (path + file chooser), read-only Product/Version/Platform/Language Level columns, and a "Re-scan" toolbar action that merges `findInterpreters()` results into the table by `path` (replace matching, append new, preserve user rows). |
| RUN-01-08 | **Reusable Selection Combo Box** | S | Full | `customizeLuaInterpreterComboBox(project, field)` populates a `ComboBox<LuaInterpreter>` from `validInterpreters()`, pre-selects the project's interpreter, renders entries via `LuaInterpreterListCellRenderer`, and for free-typed paths creates an `UNKNOWN_PRODUCT` entry and queues background `identify` to fill it in. |
| RUN-01-09 | **Command-Line Construction** | M | Full | `newLuaInterpreterCommandLine(interpreter)` builds a `GeneralCommandLine` from the interpreter's resolved executable (CONSOLE parent environment, working directory = executable's parent), returning `null` when no executable resolves; for a `.jar` executable it sets `exePath = "java"` and prepends `-cp <jar> lua`. `newProjectLuaInterpreterCommandLine(project)` resolves the project's selected interpreter. |
| RUN-01-10 | **Version → Language Level Mapping** | S | Full | Each family's `leveler` maps a product version string to a `LuaLanguageLevel`: Lua maps `5.1`→LUA51, `5.2`→LUA52, `5.3`→LUA53, `5.4`→LUA54, else LUA50; LuaJIT and Tarantool always map to LUA51. Identification stores `family.languageLevel(version)?.version` (e.g. `"5.4"`) on the model. |
| RUN-01-11 | **Environment-Variable Substitution** | S | Full | Search-path entries containing `${VAR}` are expanded by `substituteEnvVars` using `envVarPattern = .*\$\{([^\}]+)\}.*` and `System.getenv`; unknown variables collapse to empty string and the loop terminates because each pass removes one `${…}` occurrence. |
| RUN-01-12 | **Platform Executable & Glob Matching** | C | Full | `LuaInterpreterFamily.platformExecutableName` appends `.exe` to `SystemBinary` executables on Windows (JavaJar names unchanged); `isGlob`/`patternFromGlob`/`matchesGlob` compile a glob executable name (`*`→`.*`, `?`→`.`, `.`→`\.`) to a `Pattern` for child matching in discovery and in `find`. |

## Detailed Specifications

### RUN-01-04: Identification Algorithm
`identify(interpreter)` mutates the passed model in place:
1. Set `product = INVALID_PRODUCT`; clear `version` and `banner`.
2. Build `newLuaInterpreterCommandLine(interpreter)` (error if `null`) and `addParameters("-v")`.
3. Capture the process output with a 5 s timeout (`LuaProcessUtil.capture`).
4. If `exitCode != 0`: store `stderr` as `banner` and return — the entry stays invalid.
5. Resolve the `executable` `VirtualFile`; return if `null`.
6. `Banner.create(processOutput)`; return if `null`.
7. `family = LuaInterpreterFamily.find(banner.product, executable.name)`; return if `null`.
8. Set `product = family.productName`, `version = banner.version`, `path = executable.path`,
   `languageLevel = family.languageLevel(banner.version)?.version`, `platform = family.platform.label`.

### RUN-01-04: Banner Parsing (`Banner.create`)
- The `-v` banner is one line, `"<Product> <Version>  <copyright…>"`, e.g.
  `Lua 5.4.6  Copyright (C) 1994-2023 Lua.org, PUC-Rio`. Lua/LuaJIT print it to **stderr**.
- `create(ProcessOutput)`: pick `stderr` if non-empty else `stdout`; trim spaces/newlines/tabs;
  keep the substring before the first `\n`; delegate to `create(String)`.
- `create(String)`: apply `VERSION_PATTERN = ^(\S+)\s+(\S+).*$`; group 1 → `product`,
  group 2 → `version`, whole input → `full`. No match → `null`.

### RUN-01-03: Discovery Algorithm (`findInterpreters`)
1. Choose `PATHS_WINDOWS` if `SystemInfo.isWindows`, else `PATHS_UNIX`.
2. For each path entry, expand `${VAR}` (RUN-01-11) → `Path` → `directoryAsVirtualFile()`
   (`VfsUtil.findFile(path, true)`; skip if null / not a directory).
3. For each family, compute `family.platformExecutableName`:
   - If it is a glob, compile `patternFromGlob` and test every `directory.children` name;
   - else `directory.findChild(exeName)`.
4. `validate(child, family)` rejects a child whose identified family product ≠ the probed
   family's product; collect non-null results and `identify` each before returning.

### RUN-01-07: Re-scan Merge
For each discovered interpreter, `indexOfFirst { it.path == discovered.path }`: `-1` → append,
else replace the row at that index. Then push to the `tableView` and `refresh()`. User-entered
rows whose paths do not match a discovered interpreter are preserved.

### RUN-01-09: Java-Jar Launch
When `interpreterFile.extension == "jar"`, the command line launches the bundled Lua entry point
via the JVM: `exePath = "java"` with parameters `-cp <jar-path> lua` (instead of executing the jar
directly). System-binary interpreters execute their resolved path directly.

## Behavior Rules
1. **Off-EDT process work**: every method that spawns a child process (`findInterpreters`,
   `identify`, free-typed combo inspection, table re-scan) runs from a pooled background task; the
   EDT is never blocked. Swing models are touched on the EDT.
2. **Static families**: `FAMILIES` and `UNKNOWN_INTERPRETER` are immutable singletons constructed
   once; families are never instantiated per call.
3. **Invalid is the default**: any identification failure leaves the entry at `INVALID_PRODUCT`
   so `valid == false`; the only other non-real product is the free-typed `UNKNOWN_PRODUCT` set by
   the combo box before background inspection completes.
4. **No hard platform refs**: the service is `@Service(Service.Level.APP)`; persistence uses
   `PersistentStateComponent` with `lunar.xml` storages; no hard references to `Project`/`Editor`
   are retained in the model.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | RUN-01-01 | `LuaInterpreter(product = "invalid")` and `LuaInterpreter(product = "Lua")` | `valid` is read on each | The first returns `false`; the second returns `true` (`valid == (product != INVALID_PRODUCT)`). |
| 2 | RUN-01-02 | `LuaInterpreter(product = "Lua")` | `family` / `familyOrUnknown` is read | Both resolve to `FAMILIES["Lua"]` (`interpreterName == "Lua"`, `executableName == "lua"`, `platform == STANDARD`). |
| 3 | RUN-01-02 | `productName = "Lua"`, `executableName = "lua"` | `LuaInterpreterFamily.find("Lua", "lua")` | Returns the Lua family; `find("Lua", "luajit")` returns `null` (executable name mismatch). |
| 4 | RUN-01-04 | Banner string `"Lua 5.4.6  Copyright (C) 1994-2023 Lua.org, PUC-Rio"` | `Banner.create(banner)` | Returns `Banner(product = "Lua", version = "5.4.6", full = <input>)`. |
| 5 | RUN-01-04 | A `ProcessOutput` whose stderr is `"bash: lua: command not found"` (no version pattern after trim/first-line) — actually a single non-matching token line `"garbage"` | `Banner.create(processOutput)` | Returns `null` (the single-token line fails `^(\S+)\s+(\S+).*$`), so the caller leaves the entry `INVALID_PRODUCT`. |
| 6 | RUN-01-10 | Version strings `"5.1.5"`, `"5.2.4"`, `"5.3.6"`, `"5.4.6"` against the Lua family | `family.languageLevel(version)` then `.version` | Returns `LUA51`/`LUA52`/`LUA53`/`LUA54`, whose `.version` strings are `"5.1"`/`"5.2"`/`"5.3"`/`"5.4"`. |
| 7 | RUN-01-10 | Version string `"4.0"` against the Lua family; any version against LuaJIT | `family.languageLevel(version)` | Lua family falls back to `LUA50` (`.version == "5.0"`); the LuaJIT family always returns `LUA51` (`.version == "5.1"`). |
| 8 | RUN-01-09 | A `LuaInterpreter` whose `executable` resolves to `/opt/lua/lua.jar` | `newLuaInterpreterCommandLine(interpreter)` | Returns a `GeneralCommandLine` with `exePath == "java"` and parameters `["-cp", "/opt/lua/lua.jar", "lua"]`, working directory `/opt/lua`. |
| 9 | RUN-01-11 | Search-path entry `"${HOME}/bin"` with `HOME=/home/dev` in the environment | `substituteEnvVars(entry)` | Returns `"/home/dev/bin"`; an entry referencing an unset variable collapses that `${…}` to `""` and the loop terminates. |
| 10 | RUN-01-12 | The Lua family (`SystemBinary`, `executableName = "lua"`) on Windows (`SystemInfo.isWindows == true`) | `family.platformExecutableName` is read | Returns `"lua.exe"`; on non-Windows it returns `"lua"`, and a `JavaJar` family's name is never suffixed. |
| 11 | RUN-01-05 | `LuaApplicationSettings.State.interpreters = [ LuaInterpreter(path="/usr/bin/lua", product="Lua", version="5.4.6") ]` | The state is serialized to `lunar.xml` and re-loaded (`getState`/`loadState` round-trip) | The inventory is restored with the same path/product/version; `validInterpreters()` returns the entry and `findInterpreter("/usr/bin/lua")` resolves it. |
| 12 | RUN-01-06 | `LuaProjectSettings.State.interpreter = LuaInterpreter(path="/usr/bin/lua5.4", product="Lua")` | The project state is serialized to `lunar.xml` and re-loaded | The selected interpreter is restored identically; `newProjectLuaInterpreterCommandLine(project)` builds a command line for `/usr/bin/lua5.4`. |
| 13 | RUN-01-03 | A search-path list `["/opt/lua/bin", "/no/such/dir"]` where `/opt/lua/bin` contains an executable `lua` whose `-v` prints `Lua 5.4.6  Copyright (C) …` and `/no/such/dir` does not resolve via `VfsUtil.findFile` | `LuaInterpreterService.findInterpreters()` runs | The result holds exactly one `LuaInterpreter` with `path == "/opt/lua/bin/lua"`, `valid == true`, `product == "Lua"`, `languageLevel == "5.4"`, `platform == "STANDARD"`; the missing directory contributes no entry and raises no error. |
| 14 | RUN-01-03 | A directory whose child named `lua` reports `LuaJIT 2.1.x` to `-v` (identified product ≠ the probed `Lua` family) | discovery probes the `Lua` family's `lua` in that directory and calls `validate(child, luaFamily)` | The child is rejected (identified product `LuaJIT` ≠ probed product `Lua`), so `findInterpreters()` yields no `Lua` entry for that path. |
| 15 | RUN-01-07 | A table holding user rows `A` (`path == "/usr/bin/lua"`, version `"5.3.6"`) and `B` (`path == "/custom/mylua"`, no discovery match); `findInterpreters()` returns one interpreter with `path == "/usr/bin/lua"`, version `"5.4.6"` | the "Re-scan" toolbar action merges the discovery result | `indexOfFirst { it.path == "/usr/bin/lua" } != -1`, so row `A` is replaced in place by the discovered entry (version becomes `"5.4.6"`); row `B` is preserved unchanged; final row count is 2. |
| 16 | RUN-01-07 | A table holding one user row `A` (`path == "/usr/bin/lua"`); `findInterpreters()` returns a second interpreter with `path == "/opt/luajit/bin/luajit"` (no matching row) | the "Re-scan" action merges the discovery results | `indexOfFirst { it.path == "/opt/luajit/bin/luajit" } == -1`, so the discovered interpreter is appended as a new row; row `A` is preserved; final row count is 2 and `tableView.refresh()` reflects both rows. |

## Acceptance Criteria
- [ ] A `LuaInterpreter` reports `valid`/`family`/`executable` correctly for valid, invalid, and unknown products.
- [ ] The static `FAMILIES` registry resolves Lua/LuaJIT/Tarantool by product and executable name.
- [ ] `findInterpreters()` discovers known interpreters on the platform search path without blocking the EDT.
- [ ] `identify()` parses a real `-v` banner and fills product/version/platform/level, and leaves the entry invalid on any failure.
- [ ] The application inventory and the per-project selection both persist across IDE restart.
- [ ] Settings ▸ Languages ▸ Lua lets the user add, edit a path, re-scan (merge by path), and delete interpreters.
- [ ] The reusable combo box lists valid interpreters, renders invalid/unknown distinctly, and inspects free-typed paths in the background.
- [ ] `newLuaInterpreterCommandLine` produces a correct command line for system binaries and for `.jar` interpreters.
- [ ] Version→language-level mapping is correct for 5.1–5.4 with the documented fallback.
- [ ] `${VAR}` search-path entries expand against the environment; Windows `.exe` suffixing and glob matching work.

## Non-Functional Requirements
- **Threading**: all process spawning (`findInterpreters`, `identify`, combo inspection, re-scan)
  runs off the EDT; Swing model mutation runs on the EDT.
- **Robustness**: a 5 s identification timeout; failures degrade to an invalid entry rather than an
  exception; missing directories/children during discovery are skipped silently.
- **Stability**: family singletons are static and identity-stable for equality/serialization.

## Dependencies
- `LuaLanguageLevel` (`lang/LuaLanguageLevel.kt`) for the version→level mapping.
- `LuaPlatform` (`platform/LuaPlatform.kt`) for the family platform label.
- `LuaProcessUtil.capture` for bounded process execution during identification.
- `LuaBundle.properties` for the settings table column and action labels.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks & Gaps: [risks-and-gaps.md](risks-and-gaps.md)
