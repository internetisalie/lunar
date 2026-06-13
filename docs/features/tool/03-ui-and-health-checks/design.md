---
id: "TOOL-03-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TOOL-03"
status: "planned"
priority: "high"
folders:
  - "[[features/tool/03-ui-and-health-checks/requirements|requirements]]"
---

# Technical Design: UI/UX & Health Monitoring (`TOOL-03`)

## 1. Architecture Overview

### Current State
TOOL-03 is the third feature of the TOOL epic. It builds on TOOL-01 (data model `LuaTool`,
`LuaToolType`, `LuaToolManager`, `LuaToolValidator`, `LuaToolDiscoveryService`) and TOOL-02
(`LuaApplicationSettings.State.globalToolBindings`, `LuaProjectSettings.State
.projectToolBindings`, `LuaToolManager.getEffectiveTool(project, type)`). Those two are planned
to the bar; this design references their exact names. The plugin already ships the UI/runtime
patterns to mirror: `LuaInterpretersTable : ListTableWithButtons<LuaInterpreter>`, the
`LuaApplicationSettingsConfigurable`/`LuaProjectSettingsConfigurable`, the
`notification.group.lunar.debugger` group, `LuaProcessUtil.capture`, and the `Banner`
version-parser in `LuaInterpreterService`.

### Target State
A "Tools" settings page (global + project) with an Add/Edit/Remove/Auto-Detect table; a
background health monitor that invalidates tools reactively (VFS) and lazily (on access);
non-intrusive editor banners + balloon notifications for invalid tools; and a diagnostics log
dump.

```
Settings ▸ Lua ▸ Tools ──▶ LuaToolsTable (ListTableWithButtons<LuaTool>)  [TOOL-03-01]
Settings ▸ Lua Project  ──▶ LuaProjectToolBindingPanel (per-type combo)   [TOOL-03-02]
LuaToolHealthMonitor ─AsyncFileListener+lazy─▶ LuaToolHealthChecker (2-stage) [TOOL-03-03]
   └─invalid─▶ EditorNotifications refresh + LuaToolEditorNotificationProvider [TOOL-03-04]
   └─invalid─▶ NotificationGroup "…lunar.tools" balloon on project open       [TOOL-03-04]
LuaToolDiagnostics.logSnapshot() ─▶ idea.log                                  [TOOL-03-05]
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.tool.ui.LuaToolsTable`
- **Responsibility**: Editable table of `LuaTool`s, mirroring `LuaInterpretersTable`.
- **Threading**: EDT (Swing); Auto-Detect/validation run via background `Task.Backgroundable`.
- **Key API** (extends the existing `ListTableWithButtons<LuaTool>` base):
  ```kotlin
  class LuaToolsTable : ListTableWithButtons<LuaTool>() {
      override fun createListModel(): ListTableModel<LuaTool>   // columns below
      override fun createElement(): LuaTool                     // new blank tool
      override fun isEmpty(e: LuaTool?): Boolean = e?.path.isNullOrEmpty()
      override fun cloneElement(t: LuaTool): LuaTool
      override fun canDeleteElement(t: LuaTool): Boolean = true
      override fun createExtraToolbarActions(): Array<AnAction>  // "Auto-Detect"
  }
  ```
  Columns (via `CellModelBase`, as in `LuaInterpretersTable`):
  1. **Type** — `LuaToolType` (combo cell editor over enum values).
  2. **Path** — editable, `LocalPathCellEditor` + file chooser (single executable).
  3. **Version** — read-only (`tool.version`).
  4. **Status** — read-only; renders ✓/✗ icon + tooltip = `tool.isValid` reason (e.g.
     "Binary missing", "Permission denied", "OK 3.11.0").
  - **Auto-Detect** extra action → background task calling TOOL-01's `LuaToolDiscoveryService`
    discovery (its `PathEnvironmentVariableUtil.findInPath`-based scan, returning
    `List<LuaTool>`); results replace the table items, then revalidate.

### 2.2 `net.internetisalie.lunar.tool.ui.LuaToolsConfigurable`
- **Responsibility**: The global "Tools" settings page (TOOL-03-01).
- **Key API**: implements `com.intellij.openapi.options.Configurable`
  (`getDisplayName()="Tools"`, `createComponent()` hosts `LuaToolsTable.component` in a
  titled panel, `isModified()/apply()/reset()` against `LuaToolManager`/`LuaApplicationSettings`).
- **Registration**: child of the existing Lua application settings group (§7).

### 2.3 `net.internetisalie.lunar.tool.ui.LuaProjectToolBindingPanel`
- **Responsibility**: Per-type binding combos for the project (TOOL-03-02); added into
  `LuaProjectSettingsConfigurable`'s component.
- **Key API**:
  ```kotlin
  class LuaProjectToolBindingPanel(project: Project) {
      val panel: JComponent                 // one ComboBox<LuaTool?> per LuaToolType
      fun reset(state: LuaProjectSettings.State)   // load projectToolBindings
      fun isModified(state: …): Boolean
      fun apply(state: …)                   // write projectToolBindings
  }
  ```
  Each combo lists the registered tools of that `LuaToolType` plus a "(use global default)"
  entry, bound to `state.projectToolBindings[type]`.

### 2.4 `net.internetisalie.lunar.tool.health.LuaToolHealthChecker`
- **Responsibility**: The two-stage validation of one tool (pure-ish; CLI via capture).
- **Threading**: background (never EDT).
- **Key API**:
  ```kotlin
  data class HealthResult(val isValid: Boolean, val version: String?, val reason: String)
  object LuaToolHealthChecker {
      fun check(tool: LuaTool): HealthResult   // §3.1
  }
  ```

### 2.5 `net.internetisalie.lunar.tool.health.LuaToolHealthMonitor`
- **Responsibility**: Schedule/trigger health checks (reactive + eager), update tools, refresh
  banners, notify.
- **Threading**: `@Service(Service.Level.PROJECT)`; uses `serviceCoroutineScope`/background.
- **Key API**:
  ```kotlin
  @Service(Service.Level.PROJECT)
  class LuaToolHealthMonitor(private val project: Project) {
      fun start()                       // register the AsyncFileListener (§3.2); call from startup
      fun revalidateAll()               // background Task over all tools (§3.3)
      companion object { fun getInstance(project: Project): LuaToolHealthMonitor = project.service() }
  }
  ```
  Triggered by a `ProjectActivity` (startup) → `start()` + `revalidateAll()`.

### 2.6 `net.internetisalie.lunar.tool.health.LuaToolEditorNotificationProvider`
- **Responsibility**: Editor banner when a tool bound to the current project is invalid.
- **Key API** (real platform interface):
  ```kotlin
  class LuaToolEditorNotificationProvider : EditorNotificationProvider {
      override fun collectNotificationData(project: Project, file: VirtualFile):
          Function<in FileEditor, out JComponent?>? {
          if (file.fileType !is LuaFileType) return null
          val invalid = LuaToolManager invalid bindings for project  // see §3.4
          if (invalid.isEmpty()) return null
          return Function { fileEditor ->
              EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning).apply {
                  text = "Lua tool '${invalid.first().type}' is unavailable: ${invalid.first().reason}"
                  createActionLabel("Configure tools") {
                      ShowSettingsUtil.getInstance().showSettingsDialog(project, "Tools") }
              }
          }
      }
  }
  ```

### 2.7 `net.internetisalie.lunar.tool.health.LuaToolDiagnostics`
- **Responsibility**: TOOL-03-05 — write tool inventory to the log.
  ```kotlin
  object LuaToolDiagnostics {
      private val log = logger<LuaToolDiagnostics>()
      fun logSnapshot(project: Project?)   // log.info one line per tool: type, path, version, isValid, reason
  }
  ```
  Exposed via an action "Lua: Report Tool Status" (writes to `idea.log`) and called after
  `revalidateAll()`.

## 3. Algorithms

### 3.1 Two-stage health check (`LuaToolHealthChecker.check`) — TOOL-03-03
- **Input → Output**: `LuaTool` → `HealthResult`.
- **Steps**:
  1. **Fast check**: `val f = File(tool.path)`; if `!f.exists()` → `HealthResult(false, null,
     "Binary missing")`. If `!f.canExecute()` → `HealthResult(false, null, "Permission denied")`.
  2. **mtime gate**: `val mtime = f.lastModified()`. If `tool.isValid && tool.version != null &&
     mtime == tool.lastCheckedMtime` → return `HealthResult(true, tool.version, "OK ${tool.version}")`
     **without** re-running the binary (avoids needless `--version` calls).
  3. **Slow check**: run `LuaProcessUtil.capture(GeneralCommandLine(tool.path, "--version"),
     10_000)`. If `exitCode != 0` → `HealthResult(false, null, out.stderr.takeFirstLine()
     .ifEmpty{"Not executable"})`. Else parse via the existing `Banner.create(ProcessOutput)`
     (regex `^(\S+)\s+(\S+)` on the first non-empty line, taking **stderr first then stdout** —
     `stderr.ifEmpty { stdout }`, matching `LuaInterpreterService.kt`) → `version`; return
     `HealthResult(true, version, "OK $version")`.
  - **Write-back**: the caller stores the result onto the `LuaTool` — `lastCheckedMtime = mtime`,
    `version`, `isValid`, **and** `lastCheckReason = result.reason`. The
    table/banner/diagnostics read `lastCheckReason` from this cached state (no CLI).
- **Bounds**: 10 s timeout per tool; runs only when the fast check passes and the mtime changed.

### 3.2 Reactive monitoring (`LuaToolHealthMonitor.start`) — TOOL-03-03/04
- **Steps**:
  1. Build `watched = LuaToolManager` tool paths (canonicalised).
  2. Register a backgroundable async listener:
     `VirtualFileManager.getInstance().addAsyncFileListenerBackgroundable(listener, project)`.
  3. In `prepareChange(events)`: if no event's `file?.path` is in `watched` → return null; else
     return a `ChangeApplier` whose `afterVfsChange()` marks the affected tools for recheck,
     calls `revalidateAll()` (or just the affected tools), then on the EDT
     `EditorNotifications.getInstance(project).updateAllNotifications()`.
- **Rule**: deletion/move/rename/content-change of a watched path triggers revalidation; other
  events ignored. No periodic polling.

### 3.3 Eager revalidation (`revalidateAll`) — TOOL-03-03/05
- **Steps** (inside `Task.Backgroundable`): for each registered `LuaTool`, run §3.1, write the
  result back; collect newly-invalid tools; on the EDT refresh editor notifications; if any
  tool became invalid, show one balloon via the `notification.group.lunar.tools` group; call
  `LuaToolDiagnostics.logSnapshot(project)`.
- **Triggers**: project startup (`ProjectActivity`), settings-page open, after Auto-Detect, and
  from the AsyncFileListener (§3.2). **No timer.**

### 3.4 Banner invalid-set (`LuaToolEditorNotificationProvider`) — TOOL-03-04
- **Steps**: for the current `project`, for each `LuaToolType` that has a project binding, call
  `LuaToolManager.getEffectiveTool(project, type)`; collect those whose `isValid == false`
  (with their `reason`). Non-empty ⇒ render the warning panel (§2.6). Read-lock safe (only
  reads cached `LuaTool` state, no CLI).

## 4. External Data & Parsing
The only external input is `<tool> --version` stdout/stderr, parsed by the existing `Banner`
regex `^(\S+)\s+(\S+)` on the first non-empty line, **taking stderr first then stdout**
(`stderr.ifEmpty { stdout }`, per `LuaInterpreterService`). No other external format is
consumed. Reuses `net.internetisalie.lunar.platform.…Banner`.

## 5. Data Flow

### Example: deleting a registered binary (TC-TOOL-03-01)
User deletes `/usr/bin/luacheck`. The AsyncFileListener's `prepareChange` sees the path in
`watched` → `afterVfsChange` runs §3.1 fast check → `exists()==false` → tool `isValid=false`,
reason "Binary missing" → table Status column shows ✗ with tooltip; editor banner appears on
open Lua files; a balloon notifies once. `logSnapshot` records it.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Tool prints version to stderr | `Banner.create` already falls back stdout→stderr. |
| Binary replaced (same path, new mtime) | mtime gate (§3.1 step 2) forces a fresh slow check. |
| No tools registered | Monitor watches nothing; provider returns null (no banner). |
| Settings page open while a check runs | Table updates on the EDT after the background task; `isModified` compares against persisted state. |
| Tool valid but slow `--version` (network mount) | 10 s timeout → treated invalid with reason "timeout"; user can retry via Auto-Detect. |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<applicationConfigurable
    parentId="net.internetisalie.lunar.settings.LuaApplicationSettingsConfigurable"
    id="net.internetisalie.lunar.tool.ui.LuaToolsConfigurable"
    instance="net.internetisalie.lunar.tool.ui.LuaToolsConfigurable"
    displayName="Tools"/>
<editorNotificationProvider
    implementation="net.internetisalie.lunar.tool.health.LuaToolEditorNotificationProvider"/>
<postStartupActivity
    implementation="net.internetisalie.lunar.tool.health.LuaToolHealthStartup"/>
<notificationGroup id="notification.group.lunar.tools" displayType="BALLOON"
    isLogByDefault="true" bundle="net.internetisalie.lunar.LuaBundle"
    key="notification.group.lua.tools"/>
```
- `LuaToolHealthStartup : ProjectActivity` calls `LuaToolHealthMonitor.getInstance(project)
  .start()` + `revalidateAll()`.
- Project binding UI (TOOL-03-02) is added into the existing `LuaProjectSettingsConfigurable`
  component (no new EP) — `LuaProjectToolBindingPanel`.
- Depends on TOOL-01 (`LuaTool`, `LuaToolType`, `LuaToolManager`, `LuaToolDiscoveryService`)
  and TOOL-02 (`projectToolBindings`, `getEffectiveTool`); add **two** fields to the TOOL-01
  `LuaTool` data model: `lastCheckedMtime: Long = 0` (the §3.1 mtime gate) and
  `lastCheckReason: String = ""` (so the Status tooltip, banner, and diagnostics read the
  failure/OK reason from cached state without a CLI call).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| TOOL-03-01 Settings UI | M | §2.1, §2.2, §7 |
| TOOL-03-02 Project Settings Overlay | M | §2.3, §7 |
| TOOL-03-03 Health Checks | S | §2.4, §2.5, §3.1, §3.2, §3.3 |
| TOOL-03-04 Notifications | M | §2.6, §3.2, §3.3 (banner + balloon) |
| TOOL-03-05 Diagnostic Reporting | M | §2.7, §3.3 |

## 9. Alternatives Considered

- **`EditorNotificationProvider` (banner) vs balloon-only**: banners are contextual and
  non-intrusive for per-file tool issues (requirement preference); balloons used once on
  project open for global state.
- **AsyncFileListener vs periodic polling**: reactive VFS + lazy-on-access + eager-on-open
  avoids a timer thread and matches TOOL-01's lazy re-validation. Polling rejected.
- **Separate Tools page vs section in the Lua page**: a child `applicationConfigurable` gives
  a "dedicated Tools section" (requirement TOOL-03-01) while staying under the Lua group.

## 10. Open Questions

_None — feature has cleared the planning bar._
