---
id: "TOOLING-07-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TOOLING-07"
folders:
  - "[[features/tooling/07-health-and-diagnostics/requirements|requirements]]"
---

# Technical Design: TOOLING-07 — Health Monitoring & Diagnostics

## 1. Architecture Overview

### Current State

The TOOL-03 health subsystem works and its algorithms are retained, but it is welded to
the legacy model this epic deletes:

- `tool/health/LuaToolHealthChecker.kt:33-76` — 3-stage check (fast file checks :36-41 →
  mtime gate :44-48 → `--version` slow check :51-75 via `LuaProcessUtil.capture` with a
  10 s timeout :25,55). Results are written back by mutating the `LuaTool` `var` fields
  (`applyResult`, :83-90) onto the ambiguous single `isValid` flag (`tool/LuaTool.kt:41`).
- `tool/health/LuaToolHealthMonitor.kt` — project service; `AsyncFileListener`
  registration (:44), watched-set recompute per event batch (:111-112), event-kind filter
  (:129-135), exact canonical-path match only (:137-144, misses ancestor-dir deletion),
  `revalidateAll` background task with usable→broken transition collection (:56-82) and
  EDT marshaling via `invokeLater` (:73-78). It does **not** watch hererocks env dirs.
- `tool/health/LuaToolHealthStartup.kt:12-18` — `ProjectActivity` calling
  `start()` + `revalidateAll()`.
- `tool/health/LuaToolEditorNotificationProvider.kt:30-56` — banner on Lua files (:34)
  when any `getEffectiveTool` result `!isValid` (:37-41); links the *Lua Tools* page
  (:50-54). No RUNTIME coverage (interpreters live in the parallel subsystem).
- `tool/health/LuaToolDiagnostics.kt:32-47` — per-tool log lines; no bindings, envs, or
  resolver outcomes.
- **Anti-pattern being removed**: `LuaToolManager.getTools()` mutates `isValid` during a
  read (`tool/LuaToolManager.kt:110-122`) — reads must never mutate health (arch §2.3).
- `plugin.xml:425-431` — the legacy `projectService` / `postStartupActivity` /
  `editorNotificationProvider` registrations replaced below (§7).

### Prior Art in This Repo

- `tool/health/*` (all five classes above) — **replaced** by this design (same algorithms,
  new model/package; legacy files deleted by TOOLING-05).
- `LuaCovReportNotificationProvider` (`coverage/report/LuaCovReportNotificationProvider.kt:32,48`)
  — the other `EditorNotificationProvider`; unrelated (coverage), **kept**; confirms the
  `EditorNotificationPanel` idiom used here.
- `Banner` version parser (`platform/LuaInterpreterService.kt:172-190`) — probe-output
  parsing precedent; **superseded** by TOOLING-01's `LuaToolProbe` (per-kind `ProbeSpec`
  regexes); not called from this feature.
- `util/LuaTaskUtil.kt:15-25` (`newProjectBackgroundTask`) — **reused** for the
  revalidation task. `util/LuaProcessUtil.kt:13-17` — **not used** (probe subprocesses go
  through the TOOLING-03 exec service).
- Searched for other health/monitoring components: `grep -rn "AsyncFileListener\|
  EditorNotificationProvider" src/main/kotlin` — only the files named above.

### Target State

```
LuaToolHealthStartup (ProjectActivity)
  └▶ LuaToolHealthMonitor (project service, Disposable)
       ├─ AsyncFileListener over inventory paths + env roots + env bin dirs  [§3.2]
       │    └─ MergingUpdateQueue(500ms) ─▶ revalidateAll()                 [§3.3]
       ├─ revalidateAll(): LuaToolHealthChecker.check per tool               [§3.1]
       │    ├─ LuaToolchainRegistry.updateToolCheck(…)  ──fires──▶ LuaToolchainListener.TOPIC
       │    ├─ transition balloons + env-deleted balloons (deduped)          [§3.3]
       │    └─ LuaToolDiagnostics.logSnapshot(project)                       [§4.1]
       └─ TOPIC subscription ─▶ EditorNotifications.updateAllNotifications() (banners only)
LuaToolEditorNotificationProvider ─ engaged-kind + runtime banner rules      [§3.4]
```

All classes live in `net.internetisalie.lunar.toolchain.health` (arch §1), keeping the
proven TOOL-03 simple names.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.toolchain.health.LuaToolHealthChecker`
- **Responsibility**: pure 3-stage health evaluation of one inventory entry; never writes.
- **Threading**: background only (the probe path enforces this via the exec service's EDT
  guard, arch §5).
- **Collaborators**: `LuaToolProbe` (TOOLING-01, arch §1 `toolchain.probe`) injected for
  hermetic tests; `LuaToolKind.probe: ProbeSpec` (arch §2.1).
- **Key API**:
  ```kotlin
  data class LuaToolCheckResult(
      val health: LuaToolHealth,
      val version: String?,          // probed version, null unless probeOk == true
      val luaVersion: String?,       // linked-Lua hint (LuaRocks); passed to updateToolCheck so re-probe refreshes it
      val runtime: LuaRuntimeInfo?,  // RUNTIME kinds only, from the probe
  )

  object LuaToolHealthChecker {
      fun check(
          tool: LuaRegisteredTool,
          kind: LuaToolKind,
          probe: LuaToolProbe = LuaToolProbe.getInstance(),
      ): LuaToolCheckResult    // §3.1
  }
  ```
- Cross-feature interface consumed (TOOLING-01, per arch §1/§2.1):
  ```kotlin
  // toolchain.probe — single probe engine; runs via LuaToolExecutionService,
  // capture mode with stdout+stderr merged for parsing, LuaExecTimeout.PROBE (10 s).
  data class LuaToolProbeResult(              // shape per arch §10.3
      val ok: Boolean, val version: String?, val luaVersion: String?,
      val runtime: LuaRuntimeInfo?,
      val failure: String?,   // "Timeout" | "Not executable" | first non-blank output line
  )
  interface LuaToolProbe {                    // interface (injectable fake in tests), NOT object
      fun probe(kind: LuaToolKind, binaryPath: Path): LuaToolProbeResult   // (kind, Path)
      companion object { fun getInstance(): LuaToolProbe = service() }
  }
  ```

### 2.2 `net.internetisalie.lunar.toolchain.health.LuaToolHealthMonitor`
- **Responsibility**: VFS watching, revalidation batching, registry write-back,
  transition/env notifications, banner-refresh marshaling, runtime-banner dismissal flag.
- **Threading**: `@Service(Service.Level.PROJECT)`, implements `Disposable` (queue
  parent). VFS `prepareChange` on a background VFS thread; revalidation in
  `Task.Backgroundable` (`newProjectBackgroundTask`, `util/LuaTaskUtil.kt:15`); UI via
  `ApplicationManager.getApplication().invokeLater` (as today,
  `tool/health/LuaToolHealthMonitor.kt:73-78`).
- **Collaborators**: `LuaToolchainRegistry` + `LuaToolchainListener.TOPIC` (TOOLING-02,
  arch §4), project toolchain state (TOOLING-02, arch §7), `LuaToolHealthChecker`,
  `LuaToolDiagnostics`, `com.intellij.util.ui.update.MergingUpdateQueue` (verified:
  `intellij-community/platform/ide-core/src/com/intellij/util/ui/update/MergingUpdateQueue.kt`),
  `EditorNotifications`, `NotificationGroupManager`.
- **Key API**:
  ```kotlin
  @Service(Service.Level.PROJECT)
  class LuaToolHealthMonitor(private val project: Project) : Disposable {
      fun start()                    // register AsyncFileListener + TOPIC subscription (§3.2)
      fun scheduleRevalidation()     // queue into MergingUpdateQueue (500 ms merge)
      fun revalidateAll()            // immediate background pass (§3.3); startup/settings use this
      fun dismissRuntimeBanner()     // session-scoped suppression (§3.4 rule 1)
      val runtimeBannerDismissed: Boolean
      override fun dispose()
      companion object { fun getInstance(project: Project): LuaToolHealthMonitor = project.service() }
  }
  ```
- **Session state** (in-memory only): `@Volatile runtimeBannerDismissed: Boolean`,
  `notifiedDeletedEnvIds: MutableSet<String>` (synchronized).
- Cross-feature interface consumed (TOOLING-02, per arch §2.2/§4/§7):
  ```kotlin
  // toolchain.registry.LuaToolchainRegistry (APP service) — pure reads + mutators that fire TOPIC
  fun tools(): List<LuaRegisteredTool>
  fun toolsOfKind(kindId: String): List<LuaRegisteredTool>
  fun globalBindings(): Map<String, String>                       // kindId -> toolId
  fun updateToolCheck(toolId: String, health: LuaToolHealth,
                      version: String?, runtime: LuaRuntimeInfo?) // no-op (no event) if unchanged
  // toolchain.registry.LuaToolchainProjectSettings (PROJECT service, a
  // PersistentStateComponent<LuaToolchainProjectState>; persisted per arch §7/§10)
  val bindings: Map<String, String>
  val environments: List<LuaEnvironmentState>
  val activeEnvironmentId: String?
  ```

### 2.3 `net.internetisalie.lunar.toolchain.health.LuaToolHealthStartup`
- **Responsibility**: project-open bootstrap, exactly as today
  (`tool/health/LuaToolHealthStartup.kt:12-18`).
- **Key API**:
  ```kotlin
  class LuaToolHealthStartup : ProjectActivity {
      override suspend fun execute(project: Project) {
          val monitor = LuaToolHealthMonitor.getInstance(project)
          monitor.start(); monitor.revalidateAll()
      }
  }
  ```

### 2.4 `net.internetisalie.lunar.toolchain.health.LuaToolEditorNotificationProvider`
- **Responsibility**: at most one banner per Lua editor per §3.4; extends today's UX
  (`tool/health/LuaToolEditorNotificationProvider.kt:30-56`) to RUNTIME kinds.
- **Threading**: `collectNotificationData` on a read-compatible thread; reads cached
  registry state + inspection profile only — no I/O, no subprocess (as today, :25-27).
- **Collaborators**: `LuaToolKindRegistry` (TOOLING-01, arch §2.1), `LuaToolResolver`
  (arch §3), registry/project-state reads (§2.2), `LuaCheckInspection.SHORT_NAME`
  (`analysis/luacheck/LuaCheckInspection.kt:12`), platform
  `InspectionProjectProfileManager.getInstance(project).currentProfile
  .isToolEnabled(HighlightDisplayKey.find(...))` (verified:
  `intellij-community/platform/analysis-api/src/com/intellij/codeInspection/InspectionProfile.java:62-65`,
  `analysis-impl/src/com/intellij/profile/codeInspection/InspectionProjectProfileManager.java:16`),
  `LuaFileType` gate (as today, :34).
- **Key API**:
  ```kotlin
  class LuaToolEditorNotificationProvider : EditorNotificationProvider, DumbAware {
      override fun collectNotificationData(project: Project, file: VirtualFile):
          Function<in FileEditor, out JComponent?>?          // §3.4
  }
  ```
  Panels are `EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning)`
  with `createActionLabel("Configure toolchain") { ShowSettingsUtil.getInstance()
  .showSettingsDialog(project, LuaToolchainConfigurable::class.java) }` (TOOLING-06 page,
  id `net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable` per arch §1/§8;
  replaces today's `LuaToolsConfigurable` link, :50-54); the runtime banner adds
  `createActionLabel("Dismiss") { monitor.dismissRuntimeBanner();
  EditorNotifications.getInstance(project).updateAllNotifications() }`.

### 2.5 `net.internetisalie.lunar.toolchain.health.LuaToolDiagnostics`
- **Responsibility**: full-toolchain snapshot to the IDE log (format §4.1); replaces
  `tool/health/LuaToolDiagnostics.kt:32-47` (inventory-only).
- **Threading**: background (called from the revalidation task and the §2.6 action).
- **Key API**:
  ```kotlin
  object LuaToolDiagnostics {
      fun logSnapshot(project: Project?)   // null project ⇒ kinds/inventory/global bindings only
  }
  ```

### 2.6 `net.internetisalie.lunar.toolchain.health.LuaToolchainDiagnosticsAction` *(Could)*
- **Responsibility**: on-demand snapshot from the Tools menu (TOOLING-07-07).
- **Key API**: `class LuaToolchainDiagnosticsAction : AnAction(), DumbAware` —
  `actionPerformed` runs `logSnapshot(e.project)` inside `newProjectBackgroundTask`, then
  balloons "Toolchain diagnostics written to idea.log" (INFO,
  `notification.group.lunar.tools`). Menu precedent: `Lua.Console`
  (`plugin.xml:608-612`, `add-to-group group-id="ToolsMenu"`).

## 3. Algorithms

### 3.1 Three-stage health check (`LuaToolHealthChecker.check`) — TOOLING-07-01
- **Input → Output**: `(LuaRegisteredTool, LuaToolKind, LuaToolProbe)` →
  `LuaToolCheckResult`.
- **Steps** (evolves `tool/health/LuaToolHealthChecker.kt:33-76` onto arch §2.3):
  1. **Stage 1 — fast checks** (no subprocess): `val f = File(tool.path)`.
     - `!f.exists()` → `LuaToolHealth(fileExists=false, executable=false, probeOk=null,
       probedAtMtime=null, reason="Binary missing")`; result version/runtime `null`.
     - `!f.canExecute()` → `LuaToolHealth(fileExists=true, executable=false, probeOk=null,
       probedAtMtime=null, reason="Permission denied")`; version/runtime `null`.
     (`probeOk=null` = "never probed" per arch §2.3 — the probe did not run; `isUsable`
     is already false via the file flags.)
  2. **Stage 2 — mtime gate** (short-circuit; kept from TOOL-03, :44-48):
     `val mtime = f.lastModified()`. If `tool.health.probeOk == true &&
     tool.health.probedAtMtime == mtime && tool.version != null` → return
     `LuaToolCheckResult(tool.health, tool.version, tool.luaVersion, tool.runtime)`
     **without invoking the probe**. The gate applies only to previously *successful* probes: a failed probe is
     retried on every revalidation (revalidation is event-driven, so this is bounded).
  3. **Stage 3 — probe**: `val r = probe.probe(kind, tool.path.toNioPath())` (stream-merged
     capture via the TOOLING-03 exec service, `LuaExecTimeout.PROBE` = 10 s, arch §5).
     - `r.ok` → `LuaToolHealth(true, true, probeOk=true, probedAtMtime=mtime,
       reason="OK ${r.version}")`; result `(health, r.version, r.luaVersion, r.runtime)`.
     - `!r.ok` → `LuaToolHealth(true, true, probeOk=false, probedAtMtime=mtime,
       reason=r.failure ?: "Not executable")`; version/luaVersion/runtime `null`. Failure strings
       come from the probe: `"Timeout"` (timeout sentinel), `"Not executable"` (start
       failure), else the first non-blank line of merged output (unrecognized banner) —
       the same taxonomy as today (:57-75).
- **Rules / edge handling**: no write-back — the caller persists via the registry (§3.3
  step 4; removes `applyResult`, :83-90). `probedAtMtime` is recorded on both probe
  outcomes; only `probeOk == true` arms the gate.
- **Bounds**: ≤ 1 subprocess per call; 10 s cap.

### 3.2 VFS watching & event filtering (`LuaToolHealthMonitor.start`) — TOOLING-07-03
- **Registration**: `VirtualFileManager.getInstance().addAsyncFileListener(listener,
  this)` (disposable-parented; today parented to `project`,
  `tool/health/LuaToolHealthMonitor.kt:44`) plus
  `project.messageBus.connect(this).subscribe(LuaToolchainListener.TOPIC, …)` whose
  handler **only** marshals `EditorNotifications.getInstance(project)
  .updateAllNotifications()` to the EDT — it never schedules checks (loop guard).
- **Watch sets** — recomputed inside `prepareChange` per event batch (cheap, thread-safe
  registry reads per arch §10; same recompute-per-batch strategy as today, :111-112):
  - `exactPaths` = canonical `File(tool.path).canonicalPath` for every inventory entry
    (canonicalize with fallback to the raw path, as today :137-144);
  - `envRoots` = `environments.map { it.rootDir }` (project toolchain state, arch §2.4/§7);
  - `binDirs` = `envRoots.map { "$it/bin" }` (**new**: provisioned env bin dirs watched).
- **Match predicate** (per `VFileEvent`, using `event.file?.canonicalPath ?: event.path`
  = `p`):
  - `VFileDeleteEvent` / `VFileMoveEvent`: match iff `p ∈ exactPaths ∪ envRoots ∪ binDirs`
    **or** any watched path `w` satisfies `w.startsWith("$p/")` (ancestor deleted/moved —
    fixes the exact-match-only gap in today's :137-144).
  - `VFileContentChangeEvent` / `VFilePropertyChangeEvent`: match iff `p ∈ exactPaths` or
    `p`'s parent dir ∈ `binDirs` (a binary inside an env changed).
  - All other event types: ignore (same kind filter as today, :129-135).
- **On match**: return a `ChangeApplier` whose `afterVfsChange()` calls
  `scheduleRevalidation()`.
- **Batching**: `MergingUpdateQueue("lunar.toolchain.health", 500, true, null, this, null,
  Alarm.ThreadToUse.POOLED_THREAD)`; `scheduleRevalidation()` queues
  `Update.create("revalidate") { revalidateAll() }` — an `rm -rf` burst over an env dir
  collapses to one pass. No polling, no timer.

### 3.3 Revalidation pass (`revalidateAll`) — TOOLING-07-01/02/05
Inside `newProjectBackgroundTask("Validating Lua toolchain", project) { indicator -> … }`
(util cited §2.2; indeterminate indicator, `text2 = tool.path` per tool as today :57-63):
1. Snapshot pure reads: `tools = registry.tools()`, `envs = projectState.environments()`.
2. `deadRoots = envs.filterNot { File(it.rootDir).isDirectory }`.
3. For each `tool` in `tools`:
   a. `kind = LuaToolKindRegistry.getInstance().findById(tool.kindId)`; unknown kind → skip (log.warn).
   b. `result = LuaToolHealthChecker.check(tool, kind)` (§3.1).
   c. **Env-reason override**: if `tool.environmentId != null`, its env ∈ `deadRoots`,
      and `!result.health.fileExists` → replace reason with
      `"Environment root missing: <rootDir>"` (checker stays env-agnostic).
   d. `previousUsable = tool.isUsable` (arch §2.3 extension, computed on the pre-write
      snapshot).
   e. `registry.updateToolCheck(tool.id, result.health, result.version, result.luaVersion,
      result.runtime)` — the **only** write path (arch §10.1); fires the topic; no-ops when
      health+version are unchanged (event dedup, TOOLING-07-02).
   f. If `previousUsable && !newUsable` → add to `newlyBroken`.
4. **Env-deleted notifications**: for env in `deadRoots` with `env.id ∉
   notifiedDeletedEnvIds` → add to `deadEnvsToNotify`, record id. For envs whose root
   exists → remove id from the set (re-arms if the root is recreated).
5. `invokeLater` (EDT, as today :73-78): `EditorNotifications.getInstance(project)
   .updateAllNotifications()`; if `newlyBroken` non-empty → **one** WARNING balloon on
   `notification.group.lunar.tools`: `"Lua tool(s) became unavailable: <kind display
   names, comma-joined>. Check Settings > Languages & Frameworks > Lua > Toolchain."`;
   for each env in `deadEnvsToNotify` → one WARNING balloon `"Lua environment
   '<name>' was deleted from disk (<rootDir>). Its tools are unavailable."`.
6. `LuaToolDiagnostics.logSnapshot(project)` (§4.1).

**Notification dedup (specified)**: the usable→unusable comparison in step 3d/3f *is* the
dedup — persisted health carries the pre-restart state, so a tool that broke offline
notifies exactly once at startup (same semantics as today's `wasValid` check, :64-69),
a persistently-broken tool never re-notifies, and a recover→break cycle notifies again.
Env dedup is the session-scoped id set of step 4. Provisioning/registration failures are
TOOLING-04's notifications — not fired here.

### 3.4 Banner conditions (`collectNotificationData`) — TOOLING-07-04
- **Input → Output**: `(Project, VirtualFile)` → panel function or `null`.
- **Steps**:
  1. `file.fileType !is LuaFileType` → `null` (as today, :34).
  2. **Rule 1 — runtime banner**: if `LuaToolResolver.getInstance().resolveRuntime(project)
     == null` (the resolver returns only a usable RUNTIME-capability tool, arch §3/§10.4)
     **and** `!monitor.runtimeBannerDismissed` → Warning panel
     `"No usable Lua runtime for this project."` + *Configure toolchain* + *Dismiss*.
  3. **Rule 2 — broken engaged tool**: iterate kinds ordered RUNTIME-capability kinds
     first, then the rest, each group by `kindId` ascending (deterministic). For each:
     - `engaged(kind)` (below) — else continue.
     - `intended = intendedTool(project, kind.id)` (below); if `intended != null &&
       !intended.isUsable` → Warning panel `"Lua tool '<kind.displayName>' is
       unavailable: <intended.health.reason ?: "unavailable">"` + *Configure toolchain*.
       Return the first hit (at most one banner).
  4. Otherwise `null`.
- **`engaged(kind)`** (the DECIDED rule):
  - `kind.id == "luacheck"` → `InspectionProjectProfileManager.getInstance(project)
    .currentProfile.isToolEnabled(HighlightDisplayKey.find(LuaCheckInspection.SHORT_NAME))`
    (**or** explicitly selected). The inspection (`shortName="LuaCheck"`,
    `plugin.xml:257-266`; paired annotator `plugin.xml:253-255`,
    `LuaCheckAnnotator.kt:22`) survives this epic — only its command line migrates
    (TOOLING-05), so the profile query stays valid.
  - every other kind (incl. individual RUNTIME kinds) → *explicitly selected*:
    `kind.id ∈ projectState.bindings().keys ∪ registry.globalBindings().keys`, or the
    active environment (`projectState.activeEnvironment()`) contains a tool of that kind. Rationale: stylua/busted/luacov/luarocks are on-demand
    features with no cheap "enabled" flag — an *explicit selection* that is broken is
    exactly what the user must be told about; an unbound missing tool never nags
    (parity with today's effective-tool-only banner, :37-41).
- **`intendedTool(project, kindId)`** — resolver precedence **without** the usability
  filter and **without** the inventory fallback (arch §3 steps 1-3 only):
  1. active env: first registry entry whose id ∈ `activeEnv.toolIds` and kind matches;
  2. else project binding `bindings[kindId]` → registry entry (dangling id → continue);
  3. else global binding likewise;
  4. else `null`.
  This intentionally differs from `LuaToolResolver.resolve` (which skips unusable entries
  and falls back to the inventory): the banner reports the tool the user *chose*, even
  when resolution has silently fallen through to another copy.

## 4. External Data & Parsing

The probe subprocess output is parsed by TOOLING-01's `LuaToolProbe` against each kind's
`ProbeSpec` regexes (arch §2.1) — this feature consumes only the structured
`LuaToolProbeResult` (§2.1) and defines no parser of its own. Its single *produced*
external format is the diagnostics log:

### 4.1 Diagnostics snapshot format (`LuaToolDiagnostics.logSnapshot`) — TOOLING-07-06
One `log.info` per line, prefix `[TOOLCHAIN-DIAG]`, `<id8>` = first 8 chars of the UUID,
absent optionals rendered `-`, reasons always double-quoted (greppable, replaces the
`[TOOL-DIAG]` format of `tool/health/LuaToolDiagnostics.kt:39-45`):

```
[TOOLCHAIN-DIAG] snapshot project='demo' kinds=7 tools=4 envs=1
[TOOLCHAIN-DIAG] tool id=1a2b3c4d kind=lua path=/usr/bin/lua5.4 origin=DISCOVERED version=5.4.6 env=- health=[exists=true exec=true probe=true mtime=1751712000000 reason="OK 5.4.6"]
[TOOLCHAIN-DIAG] tool id=9f8e7d6c kind=luacheck path=/usr/bin/luacheck origin=MANUAL version=- env=- health=[exists=false exec=false probe=- mtime=- reason="Binary missing"]
[TOOLCHAIN-DIAG] binding scope=global kind=stylua toolId=5c4d3e2f
[TOOLCHAIN-DIAG] binding scope=project kind=luacheck toolId=9f8e7d6c
[TOOLCHAIN-DIAG] env id=0a1b2c3d name='lua54' root=/home/u/proj/.lua active=true tools=[1a2b3c4d,7e6f5a4b]
[TOOLCHAIN-DIAG] resolve kind=lua -> id=1a2b3c4d path=/usr/bin/lua5.4
[TOOLCHAIN-DIAG] resolve kind=busted -> none
```

Emission order: `snapshot` header → `tool` lines (inventory order) → `binding` lines
(global then project, kindId ascending) → `env` lines → one `resolve` line per kind in
`LuaToolKindRegistry` (kindId ascending). With `project == null`: header (`project='-'`),
tool lines, global bindings only — no project bindings/envs/resolve lines. Empty
inventory → header line only (replaces the "No tools registered" special case, :34-37).

## 5. Data Flow

### Example 1: bound luacheck binary deleted (TC-04/06)
`rm /usr/bin/luacheck` → VFS `VFileDeleteEvent` → §3.2 predicate matches `exactPaths` →
`scheduleRevalidation()` → 500 ms merge → §3.3 pass: stage 1 yields
`health(fileExists=false, reason="Binary missing")`; `updateToolCheck` fires the topic;
usable→broken transition → EDT: banners refresh (provider: `luacheck` engaged via
inspection profile + project binding; intended tool unusable → banner "Lua tool
'luacheck' is unavailable: Binary missing") + one balloon. Snapshot logged. A second pass
(any trigger) writes nothing (unchanged health → no event) and re-notifies nothing.

### Example 2: provisioned environment removed (TC-07)
`rm -rf /home/u/proj/.lua` → burst of delete events; ancestor rule matches `envRoots` /
`binDirs` → one merged revalidation → member tools fail stage 1, reason overridden to
`"Environment root missing: /home/u/proj/.lua"`; env id enters `notifiedDeletedEnvIds` →
one env balloon; runtime banner appears if no other RUNTIME kind resolves.

### Example 3: fresh project, no Lua installed (TC-05)
Startup pass finds an empty inventory → provider rule 1: no RUNTIME kind resolves →
"No usable Lua runtime for this project." banner with *Configure toolchain* / *Dismiss*.
User provisions via TOOLING-04 → registration fires the topic → monitor's subscription
refreshes notifications → banner gone.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Binary replaced in place (same path, new mtime) | mtime gate misses (§3.1 stage 2) → fresh probe; version updated via registry write. |
| Probe fails persistently (broken binary kept on disk) | Re-probed per revalidation (event-driven, bounded); balloon fired once (transition dedup §3.3). |
| Slow `--version` (network mount) | PROBE timeout (10 s) → `probeOk=false, reason="Timeout"`; tool unusable until a later pass succeeds. |
| Unknown `kindId` in inventory (stale persisted entry) | Skipped by §3.3 3a with `log.warn`; no crash, no write. |
| Dangling binding id (bound tool unregistered) | `intendedTool` yields `null` → no broken-tool banner; resolver likewise skips it (TOOLING-02). |
| Two kinds broken at once | One banner (first per §3.4 rule-2 order); the balloon names all newly-broken kinds in one message. |
| Env root deleted then recreated (re-provision) | Id removed from `notifiedDeletedEnvIds` on a pass that sees the root (§3.3 step 4) → future deletion notifies again. |
| VFS events for non-watched files (bulk git operations) | Predicate rejects in `prepareChange`; no `ChangeApplier` allocated (as today, :118). |
| Project closed mid-pass | Task and queue are disposable-parented to the monitor service (`Disposable`); queue suppressed on dispose. |
| No tools and no envs registered | Watch sets empty → listener matches nothing; provider shows only the runtime banner (rule 1). |

## 7. Integration Points

New registrations (the TOOLING-07 replacement block; the monitor needs **no**
`projectService` entry — `@Service(Service.Level.PROJECT)` self-registers, unlike the
legacy explicit entry at `plugin.xml:426-427`):

```xml
<!-- META-INF/plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<!-- Toolchain health & diagnostics (TOOLING-07) -->
<postStartupActivity
    implementation="net.internetisalie.lunar.toolchain.health.LuaToolHealthStartup" />
<editorNotificationProvider
    implementation="net.internetisalie.lunar.toolchain.health.LuaToolEditorNotificationProvider" />
```

Optional (TOOLING-07-07, Could), inside `<actions>` (precedent `plugin.xml:608-612`):

```xml
<action id="Lua.ToolchainDiagnostics"
        class="net.internetisalie.lunar.toolchain.health.LuaToolchainDiagnosticsAction"
        text="Lua: Toolchain Diagnostics"
        description="Write a Lua toolchain state snapshot to the IDE log">
  <add-to-group group-id="ToolsMenu" anchor="last"/>
</action>
```

Retained: `<notificationGroup id="notification.group.lunar.tools" displayType="BALLOON"
isLogByDefault="true"/>` (`plugin.xml:543-545`) — reused unchanged.

Removed (enumerated here; **TOOLING-05 owns the deletion commit**), `plugin.xml:425-431`:

```xml
<!-- Tool health monitoring & UI (TOOL-03) -->
<projectService
    serviceImplementation="net.internetisalie.lunar.tool.health.LuaToolHealthMonitor" />
<postStartupActivity
    implementation="net.internetisalie.lunar.tool.health.LuaToolHealthStartup" />
<editorNotificationProvider
    implementation="net.internetisalie.lunar.tool.health.LuaToolEditorNotificationProvider" />
```

(TOOLING-05 also deletes the five `tool/health/*.kt` files and
`src/test/kotlin/net/internetisalie/lunar/tool/health/LuaToolHealthCheckerTest.kt`,
superseded by this feature's tests.)

Cross-feature interfaces consumed (signatures in §2.1/§2.2; all names per the arch
contract §§1-5): TOOLING-01 `LuaToolKindRegistry`/`LuaToolProbe`, TOOLING-02
`LuaToolchainRegistry.updateToolCheck` + `LuaToolchainListener.TOPIC` +
`LuaToolchainProjectSettings`, TOOLING-03 `LuaExecTimeout.PROBE`, TOOLING-06
`LuaToolchainConfigurable` (id `net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable`).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| TOOLING-07-01 Three-stage health check | M | §2.1, §3.1 |
| TOOLING-07-02 Registry-mediated health writes | M | §2.2, §3.3 (steps 3e, dedup), §3.1 (no write-back) |
| TOOLING-07-03 Reactive VFS monitoring | M | §2.2, §3.2 |
| TOOLING-07-04 Editor banner | M | §2.4, §3.4, §7 |
| TOOLING-07-05 State-transition notifications | M | §2.2, §3.3 (steps 3f/4/5) |
| TOOLING-07-06 Diagnostics snapshot | S | §2.5, §4.1 |
| TOOLING-07-07 Diagnostics action | C | §2.6, §7 |

## 9. Alternatives Considered

- **Gate failed probes by mtime too** (never re-probe an unchanged broken binary):
  rejected — a probe can fail transiently (locked file, PATH-dependent shim); TOOL-03's
  retry-on-revalidation behavior is proven and bounded by event-driven triggering.
- **Feature-enablement queries for every kind** (e.g. "is the formatter configured to run
  on save?"): rejected — only luacheck has a queryable on/off switch (its inspection
  profile entry); the others gain a uniform, predictable *explicit selection* rule instead
  of N bespoke lookups.
- **Banner when an engaged kind has no tool at all** (beyond the runtime case): rejected —
  `LuaCheck` is `enabledByDefault="true"` (`plugin.xml:263`), so every fresh project would
  nag about a missing optional linter; parity with TOOL-03 kept (banner only for a broken
  *chosen* tool). The mandated runtime banner covers the truly blocking case and is
  dismissible.
- **Cache the VFS watch set, invalidate on topic events**: rejected — registry reads are
  cheap/thread-safe (arch §10) and per-batch recompute (today's strategy, :111-112) cannot
  go stale.
- **Per-tool topic payloads for banner refresh**: rejected — `updateAllNotifications()` is
  already coalesced by the platform; payload plumbing buys nothing here.

## 10. Open Questions

_None — feature has cleared the planning bar._
