---
id: "RUN-01-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "RUN-01"
folders:
  - "[[features/debug/run-01-lua-interpreter-sdk/requirements|requirements]]"
---

# Technical Design: RUN-01 — Lua Interpreter SDK

## 1. Architecture Overview

### Current State
Implemented and shipping. The SDK lives in `net.internetisalie.lunar.platform` (model +
service + combo box) with persistence in `net.internetisalie.lunar.settings` and the table UI
in the same `settings` package. This document records the as-built design.

### Prior Art in This Repo
Searched `src/main` for existing interpreter/SDK handling before designing:
- `platform/LuaInterpreter.kt:11` — `LuaInterpreter` model + `LuaInterpreterFamily` registry.
- `platform/LuaInterpreterService.kt:15` — APP service for discovery/identification.
- `platform/LuaInterpreterComponent.kt:18` — combo-box customizer + list cell renderer.
- `settings/LuaInterpretersTable.kt:35` — settings table.
- `settings/LuaApplicationSettings.kt:39` — global inventory (`State.interpreters`).
- `settings/LuaProjectSettings.kt:50` — project selection (`State.interpreter`).
- `command/LuaCommandLine.kt:32` — `newLuaInterpreterCommandLine`.

No competing/duplicate implementation exists; the TOOL epic's `LuaTool`/`LuaToolManager`
handle *external tool* binaries (luacheck/luarocks/stylua), a separate concern that this
design does **not** duplicate. This design **documents** the existing components; it neither
extends nor replaces a second implementation.

### Target State
The end-state matches current state: an APP-level inventory of `LuaInterpreter`s discovered
and identified by `LuaInterpreterService`, persisted by `LuaApplicationSettings`, selected
per-project by `LuaProjectSettings`, surfaced through `LuaInterpretersTable` (settings) and
`customizeLuaInterpreterComboBox` (run config / project settings), and consumed by
`newLuaInterpreterCommandLine`.

```
findInterpreters ─┐
                  ▼
LuaInterpreterService.identify ──> LuaInterpreter (model) ──> LuaApplicationSettings.State.interpreters
                                                                   │
                                              validInterpreters()  ▼
                LuaInterpretersTable / customizeLuaInterpreterComboBox ──> LuaProjectSettings.State.interpreter
                                                                   │
                                              newLuaInterpreterCommandLine ──> GeneralCommandLine
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.platform.LuaInterpreter`
- **Responsibility**: serializable model of one configured interpreter.
- **Threading**: pure data; no threading constraints. `executable` touches the VFS, so callers
  read it inside the same background context as identification.
- **Collaborators**: `LuaInterpreterFamily` (via `product`), `VfsUtil.findFile`.
- **Key API** (`platform/LuaInterpreter.kt:11`):
  ```kotlin
  data class LuaInterpreter(
      var path: String? = null, var banner: String? = null, var product: String? = null,
      var version: String? = null, var platform: String? = null, var languageLevel: String? = null,
  ) {
      constructor(other: LuaInterpreter)
      constructor(executable: Path)
      val family: LuaInterpreterFamily?          // FAMILIES[product] or UNKNOWN_INTERPRETER
      val familyOrUnknown: LuaInterpreterFamily
      val valid: Boolean                          // product != INVALID_PRODUCT
      val executable: VirtualFile?                // VfsUtil.findFile(Path.of(path), true)
  }
  ```

### 2.2 `net.internetisalie.lunar.platform.LuaInterpreterFamily`
- **Responsibility**: static registry of known interpreter products + per-family metadata and
  version→language-level mapping.
- **Threading**: immutable singletons (`FAMILIES`, `UNKNOWN_INTERPRETER`). Must remain static —
  do not instantiate per call.
- **Collaborators**: `LuaPlatform`, `LuaLanguageLevel`, `LuaIcons`, `SystemInfo`.
- **Key API** (`platform/LuaInterpreter.kt:65`):
  ```kotlin
  class LuaInterpreterFamily(
      val interpreterName: String, val executableName: String, val productName: String,
      val binaryType: BinaryType, val platform: LuaPlatform,
      val leveler: (String) -> LuaLanguageLevel?, val argExecCode: String?, val argLoadLib: String?,
  ) {
      enum class BinaryType { SystemBinary, JavaJar }
      val platformExecutableName: String                  // appends ".exe" on Windows SystemBinary
      fun languageLevel(productVersion: String): LuaLanguageLevel?
      companion object {
          const val UNKNOWN_PRODUCT = "unknown"; const val INVALID_PRODUCT = "invalid"
          val FAMILIES: Map<String, LuaInterpreterFamily>   // keyed by productName
          val UNKNOWN_INTERPRETER: LuaInterpreterFamily
          fun findByInterpreterName(interpreterName: String): LuaInterpreterFamily?
          fun find(productName: String, executableName: String): LuaInterpreterFamily?
      }
  }
  ```
  Registered families: **Lua** (`lua`, SystemBinary, leveler maps `5.1`→LUA51 … `5.4`→LUA54,
  else LUA50), **LuaJIT** (`luajit`, SystemBinary, always LUA51), **Tarantool** (`tarantool`,
  SystemBinary, LUA51, platform `TARANTOOL`).

### 2.3 `net.internetisalie.lunar.platform.LuaInterpreterService`
- **Responsibility**: discover binaries on the search path and identify each via `-v`.
- **Threading**: `@Service(Service.Level.APP)`. All public methods spawn a child process, so
  callers invoke them from pooled background tasks (the table/panel/combo-box already do).
- **Collaborators**: `newLuaInterpreterCommandLine`, `LuaProcessUtil.capture`, `Banner`,
  `LuaInterpreterFamily`, `SystemInfo`, `VfsUtil`.
- **Key API** (`platform/LuaInterpreterService.kt:15`):
  ```kotlin
  @Service(Service.Level.APP)
  class LuaInterpreterService {
      fun findInterpreters(): List<LuaInterpreter>     // scan PATHS_UNIX/PATHS_WINDOWS
      fun identify(interpreter: LuaInterpreter)         // mutate in place from `<exe> -v`
      companion object {
          val PATHS_UNIX: Array<String>; val PATHS_WINDOWS: Array<String>
          val envVarPattern: Pattern                    // ".*\$\{([^\}]+)\}.*"
          fun getInstance(): LuaInterpreterService
      }
  }
  ```

### 2.4 `net.internetisalie.lunar.platform.Banner`
- **Responsibility**: parse a `-v` banner line into `(product, version, full)`.
- **Threading**: pure; called from the service's background context.
- **Key API** (`platform/LuaInterpreterService.kt:178`):
  ```kotlin
  data class Banner(val product: String, val version: String, val full: String) {
      companion object {
          val VERSION_PATTERN: Pattern                  // "^(\\S+)\\s+(\\S+).*$"
          fun create(banner: String): Banner?
          fun create(processOutput: ProcessOutput): Banner?
      }
  }
  ```

### 2.5 `net.internetisalie.lunar.settings.LuaInterpretersTable`
- **Responsibility**: settings table (add / edit-path / re-scan / delete) over the global
  inventory.
- **Threading**: EDT for Swing; identification + re-scan dispatched to background tasks
  (`newAppBackgroundTask` / `newProjectBackgroundTask`) which call back into the model and
  `refresh()`.
- **Collaborators**: `LuaInterpreterService`, `LuaBundle`, `FileChooserDescriptor`,
  `LocalPathCellEditor`.
- **Key API** (`settings/LuaInterpretersTable.kt:35`): extends
  `com.intellij.execution.util.ListTableWithButtons<LuaInterpreter>`, overriding
  `createListModel`, `createElement`, `isEmpty`, `cloneElement`, `canDeleteElement`,
  `createExtraToolbarActions` (the "Re-scan" `AnActionButton`). Columns: Executable (editable,
  path + file chooser), Product, Version, Platform, Language Level (read-only).

### 2.6 `net.internetisalie.lunar.platform.LuaInterpreterComponent` (functions)
- **Responsibility**: reusable combo box for selecting an interpreter, with background
  inspection of free-typed paths; and the list cell renderer.
- **Threading**: EDT for the combo; `newAppBackgroundTask` for inspection.
- **Key API** (`platform/LuaInterpreterComponent.kt:18`, `:59`):
  ```kotlin
  fun customizeLuaInterpreterComboBox(project: Project, interpreterField: ComboBox<LuaInterpreter>)
  class LuaInterpreterListCellRenderer : ColoredListCellRenderer<Any>()
  ```

### 2.7 `net.internetisalie.lunar.command.newLuaInterpreterCommandLine` (function)
- **Responsibility**: build a `GeneralCommandLine` from a `LuaInterpreter`.
- **Threading**: pure construction; caller executes the process off-EDT.
- **Key API** (`command/LuaCommandLine.kt:32`):
  ```kotlin
  fun newLuaInterpreterCommandLine(interpreter: LuaInterpreter): GeneralCommandLine?
  ```

## 3. Algorithms

### 3.1 Auto-discovery (`findInterpreters` / `find`)
- **Input → Output**: `()` → `List<LuaInterpreter>` (identified, possibly invalid entries excluded
  by `validate`).
- **Steps**:
  1. Choose `PATHS_WINDOWS` if `SystemInfo.isWindows` else `PATHS_UNIX`.
  2. For each entry, `pathFromEnvVarString` → expand `${VAR}` (§3.4) → `Path`.
  3. `directoryAsVirtualFile()`: `VfsUtil.findFile(path, true)`; skip if null / not a directory.
  4. For each family: compute `family.platformExecutableName`.
     - If the name is a glob (`isGlob`), compile `patternFromGlob` and test every
       `directory.children` name; on match call `validate(child, family)`.
     - Else `directory.findChild(exeName)` and `validate(child, family)`.
  5. Collect non-null `validate` results; `identify` each before returning.
- **Rules / edge handling**: a missing directory or child yields no entries (no error). A child
  whose identified family product ≠ the probed family product is rejected by `validate`.

### 3.2 Identification (`identify`)
- **Input → Output**: `LuaInterpreter` (mutated in place).
- **Steps**:
  1. Set `product = INVALID_PRODUCT`; clear `version`, `banner`.
  2. `newLuaInterpreterCommandLine(interpreter)` (error if null) + `addParameters("-v")`.
  3. `LuaProcessUtil.capture(cmd)` (5 s timeout).
  4. If `exitCode != 0`: `banner = stderr`; return (stays invalid).
  5. Resolve `executable` VirtualFile; return if null.
  6. `Banner.create(processOutput)` (§3.3); return if null.
  7. `family = LuaInterpreterFamily.find(banner.product, executable.name)`; return if null.
  8. Set `product = family.productName`, `version = banner.version`, `path = executable.path`,
     `languageLevel = family.languageLevel(banner.version)?.version`,
     `platform = family.platform.label`.
- **Rules / edge handling**: every early return leaves the entry at `INVALID_PRODUCT` (so
  `valid == false`), except the free-typed `UNKNOWN_PRODUCT` set by the combo box before
  inspection.

### 3.3 Banner parsing (`Banner.create`)
- **Input → Output**: `ProcessOutput` (or raw `String`) → `Banner?`.
- **Steps**:
  1. Pick `stderr` if non-empty else `stdout` (Lua prints `-v` to stderr).
  2. Trim spaces/newlines/tabs; keep only the substring before the first `\n`.
  3. Apply `VERSION_PATTERN = ^(\S+)\s+(\S+).*$`; group 1 → product, group 2 → version.
  4. No match → `null`.
- **Maps to**: `Banner.product`/`Banner.version`/`Banner.full`.

### 3.4 Env-var substitution (`substituteEnvVars`)
- **Input → Output**: `String` → `String`.
- **Steps**: while `envVarPattern` (`.*\$\{([^\}]+)\}.*`) matches, take group 1 as the var name,
  read `System.getenv(name)` (null → `""`), `replace("${name}", value)`, re-match. Returns the
  fully-substituted string.
- **Rules / edge handling**: unknown vars collapse to empty; the loop terminates because each
  pass removes one `${…}` occurrence.

### 3.5 Glob → regex (`patternFromGlob`)
- **Input → Output**: glob `String` → `java.util.regex.Pattern`.
- **Steps**: anchor with `^`/`$`; map `*`→`.*`, `?`→`.`, `.`→`\.`, `\`→`\\`, else literal.
- **Maps to**: executable-name matching in §3.1 and `LuaInterpreterFamily.find`.

### 3.6 Re-scan merge (table toolbar action)
- **Input → Output**: current table `elements` + `findInterpreters()` → merged `elements`.
- **Steps**: for each discovered interpreter, `indexOfFirst { it.path == discovered.path }`;
  `-1` → append, else replace at that index. Then push to `tableView` and `refresh()`.
- **Rules / edge handling**: user-entered rows with non-matching paths are preserved.

## 4. External Data & Parsing
The only external/unstructured input is the interpreter's `-v` banner on stdout/stderr.

### 4.1 `<lua-exe> -v` version banner
- **Format**: one line, `"<Product> <Version>  <copyright…>"`, e.g.
  `Lua 5.4.6  Copyright (C) 1994-2023 Lua.org, PUC-Rio`. Lua/LuaJIT emit this to **stderr**.
- **Parse strategy**: `Banner.create` — stderr-first selection, trim, first line, regex
  `^(\S+)\s+(\S+).*$` (§3.3).
- **Maps to**: `Banner(product, version, full)` → `LuaInterpreter.product/version/banner`.
- **Failure handling**: non-zero exit → stderr stored as banner, entry invalid; unparseable
  line → `null` → entry invalid.

## 5. Data Flow

### Example 1: First-time auto-discovery in settings
User opens Settings ▸ Languages ▸ Lua. The panel's `setData` queues
`LuaInterpreterService.identify` for any `UNKNOWN_PRODUCT` rows; the table's "Re-scan" action
runs `findInterpreters()` on a project background task, merges results by path (§3.6), and
`refresh()`es. Each row shows product/version/platform/language level from identification.

### Example 2: Project selection feeds a command line
A project settings / run-config form calls `customizeLuaInterpreterComboBox(project, field)`,
populated from `LuaApplicationSettings.validInterpreters()` and pre-selected from
`LuaProjectSettings.getInstance(project).state.interpreter`. On apply, the selected
`LuaInterpreter` persists to project state; execution later calls
`newProjectLuaInterpreterCommandLine(project)` → `newLuaInterpreterCommandLine(interpreter)`.

### Example 3: Free-typed path
User types a path into the combo box editor; `customizeLuaInterpreterComboBox` creates a
`LuaInterpreter(path, product = UNKNOWN_PRODUCT)`, selects it, and queues
`identify` in the background to fill in product/version.

## 6. Edge Cases
- **Empty/blank path** → `executable` null → no command line; rendered "No interpreter selected".
- **`.jar` interpreter** → launched via `java -cp <jar> lua` (§3.2/§2.7).
- **Windows** → `.exe` suffix on SystemBinary families (`platformExecutableName`).
- **Process timeout** → `LuaProcessUtil.capture` returns exit code `-1`; entry stays invalid.
- **Tarantool** → `argExecCode`/`argLoadLib` are null (no `-e`/`-l` injection) and platform is
  `TARANTOOL`.

## 7. Integration Points
The service is registered via the `@Service(Service.Level.APP)` annotation (no XML entry).
Persistence and the configurable that hosts the table are declared in `plugin.xml`:

```xml
<!-- src/main/resources/META-INF/plugin.xml -->
<extensions defaultExtensionNs="com.intellij">
  <!-- Global inventory persistence (LuaApplicationSettings.State.interpreters) — line 370 -->
  <applicationService
      serviceImplementation="net.internetisalie.lunar.settings.LuaApplicationSettings" />

  <!-- Settings panel hosting LuaInterpretersTable — line 393 -->
  <applicationConfigurable
      parentId="language"
      instance="net.internetisalie.lunar.settings.LuaApplicationSettingsConfigurable"
      id="net.internetisalie.lunar.settings.LuaApplicationSettingsConfigurable"
      displayName="Lua"/>
</extensions>
```
- Persistence stores: `@State(name="LuaApplicationSettings", storages=[Storage("lunar.xml")])`
  (`settings/LuaApplicationSettings.kt:30`) and `@State(name="LuaProjectSettings",
  storages=[Storage("lunar.xml")])` (`settings/LuaProjectSettings.kt:15`).
- UI strings: `LuaBundle.properties` keys `application.interpreters.{executable,product,
  version,platform,languageLevel}` (lines 73–76, 107) and `action.{inspect,locate}.interpreter`
  (lines 104–105).
- `LuaProjectSettingsConfigurable` (project settings) consumes the interpreter via the combo box;
  `LuaApplicationSettingsConfigurable` → `LuaApplicationSettingsPanel` hosts the table
  (`settings/LuaApplicationSettingsPanel.kt:54`).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| RUN-01-01 | M | §2.1 |
| RUN-01-02 | M | §2.2 |
| RUN-01-03 | M | §2.3, §3.1 |
| RUN-01-04 | M | §2.3, §2.4, §3.2, §3.3, §4.1 |
| RUN-01-05 | M | §7 (LuaApplicationSettings) |
| RUN-01-06 | M | §7 (LuaProjectSettings) |
| RUN-01-07 | M | §2.5, §3.6 |
| RUN-01-08 | S | §2.6 |
| RUN-01-09 | M | §2.7 |
| RUN-01-10 | S | §2.2 (leveler), §3.2 step 8 |
| RUN-01-11 | S | §3.4 |
| RUN-01-12 | C | §2.2 (`platformExecutableName`), §3.5 |

## 9. Alternatives Considered
- **IntelliJ `Sdk`/`SdkType` framework** vs. a plain persisted model: Lunar plugs into IDEs
  (GoLand/PyCharm/CLion) that do not expose a Lua SDK type, so a self-contained
  `LuaInterpreter` model persisted in `lunar.xml` was chosen over the platform SDK table.
- **Per-call family construction** vs. static `FAMILIES`: static singletons are required —
  `IElementType`-style registry pressure aside, identity-stable families keep equality and
  serialization simple.

## 10. Open Questions

_None — feature has cleared the planning bar (implemented and shipping)._
