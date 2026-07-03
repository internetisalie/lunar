---
id: "ROCKS-15-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "ROCKS-15"
folders:
  - "[[features/rocks/15-multi-version-development/requirements|requirements]]"
---

# Technical Design: ROCKS-15 — Multi-Version Rocks Development

> **Dependency:** every symbol prefixed *"ROCKS-14"* below (`HererocksEnvState`,
> `HererocksProvisioner`, `HererocksEnvBinder`, `HererocksLocator`, `HererocksEnvDetector`,
> the `net.internetisalie.lunar.rocks.env` package) is **introduced by ROCKS-14 (planned) and
> does not yet exist on disk.** It is reused here per ROCKS-14's design; ROCKS-15 must not start
> until ROCKS-14 is `done`. Symbols cited with `file:line` **do** exist today.

## 1. Architecture Overview

### Current State

ROCKS-14 (planned) stores exactly one env: `LuaProjectSettings.State.hererocksEnv:
HererocksEnvState?` and binds it via `HererocksEnvBinder.bind`. There is no notion of a *set* of
environments, no active-env selection, and no matrix runner. Today's project settings really has
`interpreter: LuaInterpreter?` (`settings/LuaProjectSettings.kt:48`), `projectToolBindings:
MutableMap<String,String>` (`:71`), and `setProjectToolBindingAndNotify` (`:142`).

### Prior Art in This Repo (verified)

- **Settings + notify** — `LuaProjectSettings` (`settings/LuaProjectSettings.kt:20`,
  `@Service(PROJECT)`), `State` (`:44`), `setProjectToolBindingAndNotify(typeName, toolId)`
  (`:142`) publishes `LuaSettingsChangedListener.TOPIC`. `getInstance(project)` (`:164`).
  `LuaSettingsChangedListener` + `.TOPIC` live in `settings/LuaSettingsChangedEvent.kt:22,29`.
  **Extended** with the env-list fields + `setActiveEnvAndNotify`.
- **Active-env resolver** — `LuaRocksEnvironment` (`rocks/LuaRocksEnvironment.kt`)
  `resolveExecutable(project)` (`:49`) reads the TOOL-02 `LUAROCKS` binding first. **Untouched** —
  once `bind` repoints the binding at the active env, resolution follows with no code change
  (this is the "single-valued contract unchanged" requirement, ROCKS-15-02).
- **Background CLI runner** — `WorkspaceBuildRunner` (`rocks/build/WorkspaceBuildRunner.kt:15`)
  builds a command via `LuaRocksRunConfiguration.buildCommandLine(exe)`
  (`rocks/run/LuaRocksRunConfiguration.kt:181`) and runs it through
  `ProcessHandlerFactory.getInstance().createColoredProcessHandler(cmd)` into a `ConsoleView`
  (`:57-68`). `BuildWorkspaceAction` (`rocks/build/BuildWorkspaceAction.kt:23`) wraps it in a
  `Task.Backgroundable` (`:62`). **Pattern reused** for the per-env matrix runner.
- **Rockspec discovery** — `LuaRockspecDiscoveryService.discoverRockspecPaths():
  List<DiscoveredRockspec>` (`rocks/LuaRockspecDiscoveryService.kt:55`); `DiscoveredRockspec`
  has `rockspec: Path` (`:23`). **Reused** to pick the rockspec the matrix builds/tests.
- **Notification group** — `notification.group.lunar.luarocks` (`plugin.xml:535`). **Reused**
  for matrix-complete / provisioning messages (no new group).
- **Status-bar widget** — searched `src/main/kotlin` for `StatusBarWidget*`: **none found**. The
  ROCKS-15 widget is entirely NEW.

### Target State

All new code lives in `net.internetisalie.lunar.rocks.env` (the ROCKS-14 package): a set-aware
service (`HererocksEnvSet`), a status-bar widget + factory, a matrix runner + results model, and a
batch-provision action/dialog. `State` grows a list + active id with a load-time migration. No
existing resolver is modified; ROCKS-14's `bind`/`provision`/descriptor are reused verbatim.

## 2. Core Components

### 2.1 `settings.LuaProjectSettings.State` (extended)

```kotlin
// added to State (settings/LuaProjectSettings.kt:44), defaulted for the XML serializer:
var hererocksEnvs: MutableList<HererocksEnvState> = mutableListOf()  // ROCKS-15-01
var activeEnvId: String = ""                                        // ROCKS-15-01; "" = none
@Deprecated("ROCKS-15: migrated into hererocksEnvs on load; kept for back-compat read")
var hererocksEnv: HererocksEnvState? = null                         // ROCKS-14 legacy field
```

`loadState(state)` override runs the §3.1 migration after `XmlSerializerUtil.copyBean`.

### 2.2 `settings.LuaProjectSettings` — set API (extended)

```kotlin
fun resolveAllEnvs(): List<HererocksEnvState> = state.hererocksEnvs.toList()          // ROCKS-15-01
fun activeEnv(): HererocksEnvState? =
    state.hererocksEnvs.firstOrNull { it.id == state.activeEnvId }                    // ROCKS-15-01
/** ROCKS-15-02. Bind [envId]'s env (ROCKS-14 binder) + set active + notify. No-op on unknown id. */
fun setActiveEnvAndNotify(project: Project, envId: String)                            // §3.2
fun addEnv(spec: HererocksEnvState)  // append if id not present; used by batch provisioning §2.7
```

`setActiveEnvAndNotify` is a `@Service(PROJECT)` method (existing service); threading: EDT
(cheap state write + `bind`, which itself dispatches VFS/settings per ROCKS-14 §2.5).

### 2.3 `rocks.env.HererocksEnvSet` (thin static facade, optional)

Static accessor so non-settings callers (widget, matrix action) do not reach into `State`:

```kotlin
object HererocksEnvSet {
    fun all(project: Project): List<HererocksEnvState> =
        LuaProjectSettings.getInstance(project).resolveAllEnvs()       // ROCKS-15-01 / resolveAllEnvs
    fun active(project: Project): HererocksEnvState? =
        LuaProjectSettings.getInstance(project).activeEnv()
    fun switch(project: Project, envId: String) =
        LuaProjectSettings.getInstance(project).setActiveEnvAndNotify(project, envId)  // ROCKS-15-02
}
```

### 2.4 `rocks.env.LuaEnvStatusBarWidget` (`StatusBarWidget`, `StatusBarWidget.TextPresentation`)

- **Responsibility**: show the active env label; on click, popup the env set + "Add environment…".
- **Threading**: EDT (all `StatusBarWidget` callbacks are EDT).
- **Collaborators**: `HererocksEnvSet`, `com.intellij.openapi.ui.popup.JBPopupFactory`,
  ROCKS-14 `CreateHererocksEnvAction`.
- **Key API**:
  ```kotlin
  class LuaEnvStatusBarWidget(private val project: Project) :
      StatusBarWidget, StatusBarWidget.TextPresentation {
      override fun ID() = "Lunar.LuaEnvWidget"
      override fun getText(): String =
          HererocksEnvSet.active(project)?.displayLabel() ?: "No Lua env"   // §3.4
      override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { showPopup() }
      private fun showPopup()  // §3.4
  }
  ```
  Subscribes to `LuaSettingsChangedListener.TOPIC` in `install(statusBar)` and calls
  `statusBar.updateWidget(ID())` on `onSettingsChanged()` so switching refreshes the label.

### 2.5 `rocks.env.LuaEnvStatusBarWidgetFactory` (`StatusBarWidgetFactory`)

```kotlin
class LuaEnvStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = "Lunar.LuaEnvWidget"
    override fun getDisplayName() = "Lua Environment"
    override fun createWidget(project: Project): StatusBarWidget = LuaEnvStatusBarWidget(project)
    override fun isAvailable(project: Project) = true
}
```
Registered under `<statusBarWidgetFactory>` (§7).

### 2.6 `rocks.env.matrix.MatrixRunner` + `MatrixResult`

```kotlin
data class MatrixRow(val env: HererocksEnvState, var status: Status, var exitCode: Int?, var output: String)
enum class Status { PENDING, RUNNING, PASS, FAIL }
data class MatrixResult(val rows: List<MatrixRow>) { val allPassed: Boolean get() = rows.all { it.status == Status.PASS } }

object MatrixRunner {
    /** ROCKS-15-04. One Task.Backgroundable per env; each runs `<env bin>/luarocks <command> <rockspec>`. */
    fun run(project: Project, command: String, rockspec: Path, envs: List<HererocksEnvState>): MatrixResult
}
```
- **Threading**: each env runs on its own `Task.Backgroundable`; per-row process via
  `ProcessHandlerFactory.getInstance().createColoredProcessHandler(cmd)` + `handler.waitFor()`
  (the `WorkspaceBuildRunner` idiom). Rows update the shared `MatrixResult` under EDT via
  `invokeLater` for the tool-window view.
- **Command line (§3.3)**: `GeneralCommandLine(env.luarocksExe(), command, rockspec.toString())`.

### 2.7 `rocks.env.matrix.RunMatrixAction` / `MatrixResultsToolWindow` / `BatchProvisionAction`

- `RunMatrixAction : AnAction` (ROCKS-15-04) — `update()` disabled when `resolveAllEnvs` empty or no
  rockspec discovered; on perform, picks command (`make`/`test`/`build` combo) + rockspec (first of
  `LuaRockspecDiscoveryService.discoverRockspecPaths()`), calls `MatrixRunner.run`, and shows results
  in `MatrixResultsToolWindow`.
- `MatrixResultsToolWindow` — a `ToolWindowFactory` (id `Lunar.LuaMatrix`) rendering a `JBTable` of
  `MatrixRow` (columns: Env label, Status, Exit). Selecting a row shows its captured `output` in an
  attached `ConsoleView`.
- `BatchProvisionAction : AnAction` (ROCKS-15-05) — opens `BatchProvisionDialog` (base-dir field +
  editable list of {flavor combo, luaVersion combo} rows), builds one `HererocksEnvState` per row
  (§3.5), calls `HererocksProvisioner.getInstance(project).provision(spec, CREATE)` per row;
  ROCKS-14's `bind` on success also appends via `addEnv`.

## 3. Algorithms

### 3.1 Load-time migration (ROCKS-15-01)

- **Input → Output**: `State` (possibly with legacy `hererocksEnv`) → `State` with populated
  `hererocksEnvs`/`activeEnvId`.
- **Steps** (in `loadState`, after `XmlSerializerUtil.copyBean(state, this.state)`):
  1. `val legacy = this.state.hererocksEnv`
  2. if `legacy != null && this.state.hererocksEnvs.none { it.id == legacy.id }`:
     - `if (legacy.id.isBlank()) legacy.id = UUID.randomUUID().toString()`
     - `this.state.hererocksEnvs.add(legacy)`
     - `if (this.state.activeEnvId.isBlank()) this.state.activeEnvId = legacy.id`
     - `this.state.hererocksEnv = null` (consumed)
- **Edge handling**: idempotent (guarded by the `none { id }` check); empty legacy ⇒ no change;
  blank `activeEnvId` with a non-empty list is left blank (no auto-select) — the empty/no-active
  state is valid.

### 3.2 `setActiveEnvAndNotify(project, envId)` (ROCKS-15-02)

```
val env = state.hererocksEnvs.firstOrNull { it.id == envId } ?: return   // unknown id ⇒ no-op (TC-4)
HererocksEnvBinder.bind(project, env)         // ROCKS-14 §2.5: registers luarocks tool, binds
                                              // interpreter, fires TOPIC (TC-3)
state.activeEnvId = envId
```
`bind` already publishes `TOPIC`; no second publish. Never re-provisions (bind only wires the
already-built dir). Resolution correctness (TC-5) is inherited from ROCKS-14: after bind the
`LUAROCKS` project binding points at `env.luarocksExe()`, so `LuaRocksEnvironment.resolveExecutable`
(`rocks/LuaRocksEnvironment.kt:49`) returns it unchanged.

### 3.3 Matrix command construction + aggregation (ROCKS-15-04)

- **Per-env command**: `GeneralCommandLine(env.luarocksExe(), command, rockspec.toString())`
  (`env.luarocksExe()` is ROCKS-14 §2.1 — `<bin>/luarocks[.bat]`). Example (TC-7, `test`):
  `["/p/envs/PUC-5.3/bin/luarocks","test","/p/foo-1.0-1.rockspec"]`.
- **Per-row**: run in `Task.Backgroundable`; `handler.waitFor()`; `row.exitCode = handler.exitCode`;
  `row.status = if (exitCode == 0) PASS else FAIL`; `row.output` = accumulated process text.
- **Aggregate**: `result.allPassed = rows.all { it.status == PASS }` (TC-7 ⇒ FAIL since B exits 1).
- **Empty set**: `RunMatrixAction.update()` disables the action when `envs.isEmpty()`; if invoked
  programmatically with no envs, `run` returns `MatrixResult(emptyList())` and shows "no
  environments" (TC-8) — no process spawned.

### 3.4 Widget label + popup (ROCKS-15-03)

- **Label**: `active(project)?.displayLabel() ?: "No Lua env"` (TC-6).
- **Popup**: build a `JBPopupFactory.getInstance().createListPopup(...)` whose items are
  `resolveAllEnvs()` (rendered as `displayLabel()`, active row prefixed with a check icon) plus a
  final `"Add environment…"` item. On select: env item → `HererocksEnvSet.switch(project, env.id)`;
  add item → `ActionManager.getInstance().getAction("Lunar.Hererocks.Create")` (ROCKS-14 action id)
  executed via an `AnActionEvent` synthesized from the widget's `DataContext`.

### 3.5 Batch spec derivation (ROCKS-15-05)

```
for row in dialog.rows:                       // row = {flavor, luaVersion}
    dir = "$baseDir/${row.flavor.name}-${row.luaVersion}"     // e.g. /p/envs/PUC-5.3 (TC-9)
    spec = HererocksEnvState(id = UUID.randomUUID().toString(), directory = dir,
                             flavor = row.flavor, luaVersion = row.luaVersion,
                             luarocksVersion = "latest")
    HererocksProvisioner.getInstance(project).provision(spec, CREATE)   // ROCKS-14 §2.4, guarded
```
ROCKS-14 `provision` on success calls `bind`, and we hook `addEnv(spec)` so the env joins the set.

## 4. External Data & Parsing

The matrix runner consumes `luarocks` **exit codes only** (0 = PASS, non-zero = FAIL), exactly as
`WorkspaceBuildRunner` does — no stdout parsing. Captured output is shown verbatim in the row's
`ConsoleView`. No format spec required. hererocks provisioning output is handled by ROCKS-14.

## 5. Data Flow

### Example 1: Switch active env (ROCKS-15-02/03)
User clicks the status-bar widget → popup lists envs → selects "PUC 5.3" →
`HererocksEnvSet.switch(project, id)` → `LuaProjectSettings.setActiveEnvAndNotify` →
`HererocksEnvBinder.bind` repoints `LUAROCKS` + interpreter and fires `TOPIC` → indexing/resolution
rebind; widget's `onSettingsChanged` calls `statusBar.updateWidget(ID())` → label now "PUC 5.3".

### Example 2: Run test matrix (ROCKS-15-04)
Tools ▸ Run Test Matrix → `RunMatrixAction` picks `test` + first discovered rockspec →
`MatrixRunner.run` spawns one `Task.Backgroundable` per env, each running
`<env>/bin/luarocks test <rockspec>` → rows update PASS/FAIL → `MatrixResultsToolWindow` table shows
per-env results; aggregate FAIL if any row failed.

## 6. Edge Cases

- **Active id not in list** (env removed via ROCKS-14 Remove) → `activeEnv()` returns null, widget
  shows "No Lua env"; `LuaRocksEnvironment` falls back to app settings (existing behavior).
- **Duplicate env ids** → `addEnv` is a no-op when id already present.
- **Batch dir collision** (an env dir already exists) → handled by ROCKS-14 `provision`
  (hererocks rebuilds in place); ROCKS-15 adds no new handling.
- **Matrix during provisioning** → per-env process is independent of provisioning; the ROCKS-14
  concurrency guard still protects a directory from concurrent *provision*, not from a matrix
  *read*-only `luarocks test`.
- **Empty env set** → widget "No Lua env"; `RunMatrixAction`/`BatchProvisionAction` update()-gated.

## 7. Integration Points (`plugin.xml`)

```xml
<extensions defaultExtensionPointName="com.intellij">
  <statusBarWidgetFactory id="Lunar.LuaEnvWidget"
      implementation="net.internetisalie.lunar.rocks.env.LuaEnvStatusBarWidgetFactory"/>
  <toolWindow id="Lunar.LuaMatrix" anchor="bottom" secondary="true" canCloseContents="true"
      factoryClass="net.internetisalie.lunar.rocks.env.matrix.MatrixResultsToolWindow"/>
</extensions>

<actions>
  <!-- add into the existing ROCKS-14 group id="net.internetisalie.lunar.rocks.env.HererocksEnvGroup" -->
  <action id="Lunar.Hererocks.RunMatrix"
      class="net.internetisalie.lunar.rocks.env.matrix.RunMatrixAction"
      text="Run Test Matrix…">
    <add-to-group group-id="net.internetisalie.lunar.rocks.env.HererocksEnvGroup" anchor="last"/>
  </action>
  <action id="Lunar.Hererocks.BatchProvision"
      class="net.internetisalie.lunar.rocks.env.matrix.BatchProvisionAction"
      text="Provision Version Matrix…">
    <add-to-group group-id="net.internetisalie.lunar.rocks.env.HererocksEnvGroup" anchor="last"/>
  </action>
</actions>
```
`LuaProjectSettings` needs no new registration (existing `@Service(PROJECT)`); the added `State`
fields serialize with the existing `Storage("lunar.xml")`. The ROCKS-14 group
`HererocksEnvGroup` and action id `Lunar.Hererocks.Create` are the reuse points (ROCKS-14 §5).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| ROCKS-15-01 | M | §2.1, §2.2, §3.1 |
| ROCKS-15-02 | M | §2.2, §2.3, §3.2 |
| ROCKS-15-03 | M | §2.4, §2.5, §3.4 |
| ROCKS-15-04 | S | §2.6, §2.7, §3.3 |
| ROCKS-15-05 | C | §2.7, §3.5 |

## 9. Alternatives Considered

- **Interpreter-combo switcher instead of a status-bar widget** — rejected; the interpreter combo is
  owned by `LuaProjectSettingsPanel` and is modal/settings-scoped. A status-bar widget is always
  visible and one click, matching the "quick version switch" use case.
- **N run-console tabs for the matrix** — rejected in favor of one tool-window table + a shared
  console: an env matrix of 5 tabs is noisy; a table gives an at-a-glance pass/fail grid.
- **Per-env registries/credentials** — out of scope (ROCKS-06 owns registry handling); envs share
  the project registry.
- **Auto-select an active env on migration when none set** — rejected; leaving `activeEnvId` blank
  preserves ROCKS-14's "no env ⇒ app fallback" behavior deterministically.

## 10. Open Questions

_None — the feature has cleared the planning bar._
