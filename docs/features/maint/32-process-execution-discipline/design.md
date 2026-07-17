---
id: "MAINT-32-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-32"
folders:
  - "[[features/maint/32-process-execution-discipline/requirements|requirements]]"
---

# Technical Design: MAINT-32 — Process-Execution Discipline (LuaProcessUtil)

## 1. Architecture Overview

### Current State

The review's §2.1 prescription was "fix once at the primitive `LuaProcessUtil`, then migrate
callers." That primitive **no longer exists** — `git grep LuaProcessUtil src/` returns **zero**
hits (only `docs/review.md` still names it). The migrate-and-delete already happened: the modern
primitive is `toolchain/exec/LuaToolExecutionService` (`LuaToolExecutionService.kt:20-135`), an
`@Service(Service.Level.APP)` that **already** provides everything §2.1 asked `LuaProcessUtil` to
grow:

- **EDT / background assertion** — `ThreadingAssertions.softAssertBackgroundThread()`
  (`:45`, `:97`) logs a soft error (Logger.error semantics) when called on the EDT, verified by
  `LuaToolExecutionServiceTest.testCaptureOnEdtLogsSoftAssert` (`:88-107`).
- **Read-lock-free path** — when the caller holds the read lock but is not on the EDT, `capture`
  offloads to a pooled thread (`:47-49`).
- **Cancellable, kill-on-timeout wait** — `awaitStream` polls `indicator.isCanceled` on a 100 ms
  slice and calls `destroyProcess()` on cancel/timeout (`:111-127`); `doCapture` uses
  `runProcessWithProgressIndicator` (`:67-68`).
- **stdin** — `writeStdin` (`:78-81`).

Thirteen call sites already consume it (`git grep -l 'LuaToolExecutionService' src/main`:
`RockspecBridge`, `LuaRocksInstallExecutor`, `LuaRocksSearchService`, `SourceBuildStrategy`,
`MatrixRunner`, `LuaToolProbeImpl`, `LuaCheckInvoker`, `LuaRocksMetadataService`,
`LuaRocksInstalledService`, `PublishRockAction`, `LuaRocksInstallStrategy`, `StyluaFormattingTask`,
and the service itself). **So MAINT-32-01 is a no-op-and-verify on the primitive**; the real work
is at three *stragglers* that still spawn processes or do disk I/O outside the primitive's
discipline, and one caller (`RockspecBridge`) that IS on the primitive but is invoked from a
read-lock-holding `CachedValue.compute()`.

Re-verified defect map against `main` @ `0a99cb35`:

| §2.1 concern | Current file:line | Status now |
| :--- | :--- | :--- |
| #11 rockspec bridge under read lock | Reference **resolution** — `LuaNameReference.kt:88` / `LuaRequireReference.kt:21` → `PathConfiguration.getProjectSourcePathPatterns` (`SourcePathPattern.kt:19-23`) → `RockspecSourcePathProvider.derivedPatterns()` (`:49`) → `cache.value` → `RockspecSourcePathProvider.compute()` (`:54`) which calls its OWN `RockspecBridge.read(project, disco.rockspec)` (`:61`) | **LIVE** — resolution runs on a **non-EDT background thread holding the read lock**; the `CachedValue` guard at `RockspecSourcePathProvider.kt:24` fires only on `isDispatchThread`, so a background read-lock resolution thread falls to the `else` branch (`:39`) → `compute()` (`:54`) → `RockspecBridge.read` (`:61`) → synchronous bridge subprocess **under the read lock**, 10 s × #rockspecs. (`LuaRockspecDiscoveryService.compute():81` also calls `RockspecBridge.read`, but its `packageName` result has zero consumers — see §3.1 consumer audit — so it is not the resolution freeze path.) |
| `LuaProcessUtil.capture` no EDT guard | — | **MOOTED** — file deleted; primitive is `LuaToolExecutionService` with the guard |
| `LuaConsoleAction` EDT spawn | `LuaConsoleAction.kt:19` → `LuaConsoleRunner.initAndRun()` → `LuaConsoleRunner.createProcess()` `:22` = `commandLine.createProcess()` | **LIVE** — `actionPerformed` runs on the EDT; `initAndRun()` (platform `RunContentExecutor`) calls `createProcess()` synchronously on the calling thread |
| `LuaCoverageProgramRunner` EDT file I/O | `LuaCoverageProgramRunner.kt:54-57` (`File.exists()`/`delete()` in `doExecute`) | **LIVE** — `GenericProgramRunner.execute` dispatches `doExecute` on the EDT |
| `LuaToolHealthMonitor` prepare-phase disk I/O | `LuaToolHealthMonitor.kt:214` → `buildWatchSet()` `:196-203` → `canonicalize()` `:205-206` (`File.canonicalPath`) | **LIVE** — `HealthFileListener.prepareChange` runs `File.canonicalPath` per VFS batch |
| `LuaRocksEnvironment` transitive `File.exists()/canExecute()` | `LuaRocksEnvironment.kt:35-52` | **MOOTED** — post-BUG-375/ROCKS-16 the facade delegates to `LuaToolResolver.resolve` (`LuaToolResolver.kt:22-49`), which is **pure in-memory registry reads** + `isUsable` (a cached `health.*` field read, `LuaRegisteredTool.kt:32-33`). No `File.*` I/O on any path. See §9. |
| `WorkspaceBuildRunner.waitFor` ignores indicator + non-cancellable install | `WorkspaceBuildRunner.kt:72-76` (`handler.waitFor()` with no poll/kill) | **LIVE** — blocking `waitFor()`, no indicator poll, no kill-on-cancel |

### Prior Art in This Repo

Searched `git grep` across `src/main/kotlin`:

| Existing component | Location | This design |
| :--- | :--- | :--- |
| `LuaToolExecutionService` | `toolchain/exec/LuaToolExecutionService.kt:20-135` | **The canonical primitive — EXTEND/REUSE.** No new process-launch code is written; migrating callers route through its existing `capture`/`stream`. MAINT-32-01 verifies (does not rewrite) its guards. |
| `LuaToolExecutionService.stream` | `LuaToolExecutionService.kt:30-36`, `awaitStream:111-127` | **The kill-on-cancel precedent — REUSE for MAINT-32-04.** `WorkspaceBuildRunner.executeMake` is re-pointed at `stream(cmd, listener, INSTALL, colored=true, indicator)`; its manual `handler.waitFor()` is deleted. |
| `LunarCoroutineScopeService` | `util/LunarCoroutineScopeService.kt:19` | **The lifecycle-scope precedent — REUSE for MAINT-32-02.** The console-action off-EDT spawn and the `RockspecSourcePathProvider` bridge pre-warm launch on `getInstance(project).scope`. |
| `newProjectBackgroundTask` | `util/LuaTaskUtil.kt:15` | **REUSE.** Already used by `LuaToolHealthMonitor.revalidateAll` (`:79`); the console spawn wraps its process start in this Backgroundable. |
| `RockspecSourcePathProvider` EDT prewarm guard | `rocks/RockspecSourcePathProvider.kt:22-46` | **EXTEND (widen the guard predicate).** MAINT-22-07 already established the exact eventual-consistency contract here: guard the `CachedValue` compute, return degraded (empty) patterns, launch a background prewarm on `LunarCoroutineScopeService.scope`. The defect is the guard predicate is `isDispatchThread` only (`:24`); §3.1 widens it to `isReadAccessAllowed` (covers the background read-lock resolution thread) and makes the prewarm run `compute()`'s bridge **outside** any read action. |
| `RockspecBridge.read` | `rocks/RockspecBridge.kt:36-58` | **REUSE unchanged** as the bridge executor; only *the thread it runs on* changes — never under a read lock after §3.1. |
| `LuaRockspecDiscoveryService` | `rocks/LuaRockspecDiscoveryService.kt:38-117` | **UNCHANGED by this feature.** `discoverRockspecPaths()` (`:55`) is index-only path discovery consumed by six callers, all of which read only `DiscoveredRockspec.rockspec` (§3.1 consumer audit). Its own `RockspecBridge.read` at `:81` fills `packageName`, which has zero consumers — a latent inefficiency out of MAINT-32 scope, NOT the resolution freeze path. No split is performed. |

No new process abstraction is introduced; every change routes to an existing primitive or widens an
existing guard. `LuaProcessUtil` is confirmed already deleted and is **not** recreated.

### Target State

1. **Primitive (MAINT-32-01)** — `LuaToolExecutionService` is confirmed to already satisfy the
   §2.1 contract; a regression test locks the read-lock offload path (`§3.2`, TC-01).
2. **Bridge off the lock (MAINT-32-02)** — `RockspecSourcePathProvider` is the sole seam. Its
   `CachedValue` guard is widened from `isDispatchThread` to `isReadAccessAllowed` so a **background
   read-lock resolution thread** (the real #11 path: `LuaNameReference:88` / `LuaRequireReference:21`)
   returns **degraded (static, rockspec-free) patterns** and triggers a deduplicated background
   pre-warm on `LunarCoroutineScopeService.scope`; the pre-warm runs the `RockspecBridge.read`
   subprocess **outside** any read action, so the bridge NEVER executes under a read lock (§3.1). A
   later resolution pass, after the prewarm lands, observes the full derived patterns — the same
   eventual-consistency contract MAINT-22-07 already established for the EDT case at `:24`.
3. **Caller migration (MAINT-32-03)** — console spawn, coverage file I/O, and health-monitor
   prepare-phase I/O each move off their fast/EDT path (§3.3, §3.4, §3.5).
4. **Cancellable builds (MAINT-32-04)** — `WorkspaceBuildRunner.executeMake` and the LuaRocks
   install task migrate onto `LuaToolExecutionService.stream` and a cancellable Backgroundable
   (§3.6).

## 2. Core Components

### 2.1 net.internetisalie.lunar.rocks.RockspecSourcePathProvider (modified)
- **Responsibility**: cached derived source-root patterns across all project rockspecs. It is the
  **sole seam** for #11: its `compute()` (`:54`) is what calls `RockspecBridge.read` (`:61`) and is
  reached from reference resolution under the read lock. Widen its existing MAINT-22-07 guard so the
  bridge subprocess never runs under any read lock.
- **Threading**: the `CachedValue` compute (`:22-46`) currently guards on `isDispatchThread`
  (`:24`). It is widened to **`isReadAccessAllowed`** (which is true on both the EDT and a background
  read-lock resolution thread). Under that guard: return **degraded static patterns**
  (`PathConfiguration.getStaticSourcePathPatterns(project)`, i.e. the user `package.path` with no
  rockspec-derived roots — `SourcePathPattern.kt:32-37`) paired with an **empty** `cModuleRockspecs`
  list, and launch a deduplicated prewarm on `LunarCoroutineScopeService.getInstance(project).scope`.
  The prewarm runs `compute()`'s `RockspecBridge.read` loop **outside** any `readAction {}` (paths
  are fetched from `discoverRockspecPaths()`, whose own internal read action is a bounded index
  query, not the subprocess). The `else` branch (no read access held) computes synchronously as
  today.
- **Collaborators**: `LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths()`
  (`:56`, existing, path-only), `RockspecBridge.read` (`RockspecBridge.kt:36`),
  `PathConfiguration.getStaticSourcePathPatterns` (`SourcePathPattern.kt:32`),
  `CachedValuesManager` / `PsiModificationTracker` + the existing `forceRefreshTracker`
  `SimpleModificationTracker` (`:19`), `LunarCoroutineScopeService.getInstance(project).scope`.
- **Key API** (signatures unchanged; only the compute body changes):
  ```kotlin
  fun derivedPatterns(): List<SourcePathPattern>   // = cache.value.first (unchanged)
  fun cModuleRockspecs(): List<CModuleRock>         // = cache.value.second (unchanged)
  // compute() body: split degraded-return + prewarm from the synchronous full path (§3.1)
  private fun compute(): Pair<List<SourcePathPattern>, List<CModuleRock>>   // unchanged signature
  private fun prewarm()   // dedup on cache.hasUpToDateValue(); launch off-read-lock on scope
  ```
- **Prewarm dedup**: a single `@Volatile private var prewarmInFlight = false` (CAS-style set/clear
  around the launch) plus `if (cache.hasUpToDateValue()) return` guarantees N unresolved references
  in one pass schedule **at most one** prewarm job, not N (§3.1 step 4).

### 2.2 net.internetisalie.lunar.rocks.build.BuildWorkspaceAction (modified)
- **Responsibility**: enable/disable the "Build Workspace" action; must not spawn a subprocess in
  `update()`.
- **Threading**: `update()` — already `ActionUpdateThread.BGT` (`:109`); it calls
  `discoverRockspecPaths().size` (`:34`), which is index-only (no subprocess) and unchanged by this
  feature.
- **Collaborators**: `LuaRockspecDiscoveryService.discoverRockspecPaths` (existing, path-only).
- **No code change**: this component is listed only to record that the `update()` gate remains
  subprocess-free; §3.1 does not touch it.

### 2.3 net.internetisalie.lunar.run.console.LuaConsoleAction (modified)
- **Responsibility**: open the REPL; move the interpreter spawn off the EDT.
- **Threading**: `actionPerformed` stays on EDT for UI console creation; the process **start**
  moves to `newProjectBackgroundTask`, marshalling the console attach back with
  `invokeLater`/`Dispatchers.EDT`.
- **Collaborators**: `LuaConsoleRunner` (`run/console/LuaConsoleRunner.kt:17`),
  `newProjectBackgroundTask` (`util/LuaTaskUtil.kt:15`).
- **Key API**: unchanged signature; body wraps `LuaConsoleRunner(project).initAndRun()` per §3.3.

### 2.4 net.internetisalie.lunar.coverage.LuaCoverageProgramRunner (modified)
- **Responsibility**: run tests under coverage; move the stats-file cleanup off the EDT.
- **Threading**: `doExecute` is EDT (platform contract for `GenericProgramRunner`); the
  `luacov.stats.out` existence-check/delete moves into the process launch path, off the EDT.
- **Collaborators**: `File` (`java.io.File`), existing `state.execute` (`:60`).
- **Key API**: `doExecute` unchanged signature; the `File(workDir, "luacov.stats.out")` I/O
  (`:54-57`) is extracted to a helper invoked off the EDT per §3.4.

### 2.5 net.internetisalie.lunar.toolchain.health.LuaToolHealthMonitor (modified)
- **Responsibility**: reactive tool-health watcher; stop doing `File.canonicalPath` in the
  `AsyncFileListener` prepare phase.
- **Threading**: `prepareChange` must be allocation-cheap and I/O-free (platform contract);
  `buildWatchSet()`/`canonicalize()` I/O moves to a cached, invalidation-driven snapshot recomputed
  off the listener path.
- **Collaborators**: `LuaToolchainRegistry.tools()`, `LuaToolchainProjectSettings.environments()`
  (existing), `LuaToolchainListener.TOPIC` (existing, already subscribed `:64-71`).
- **Key API**:
  ```kotlin
  @Volatile private var watchSet: LuaHealthWatchSet = LuaHealthWatchSet.EMPTY
  private fun rebuildWatchSet()   // off the listener path; called on toolchainChanged + start()
  ```

### 2.6 net.internetisalie.lunar.rocks.build.WorkspaceBuildRunner (modified)
- **Responsibility**: run `luarocks make` per rock in topo order; make the wait cancellable and
  kill the process on cancel.
- **Threading**: called from a `Task.Backgroundable` (`BuildWorkspaceAction.runBuildTask:62`),
  already off-EDT with an `indicator`.
- **Collaborators**: `LuaToolExecutionService.stream` (`toolchain/exec/LuaToolExecutionService.kt:30`),
  the existing `config.buildCommandLine(exe)` (`:64`), a `ProcessListener` writing to the `ConsoleView`.
- **Key API**: `executeMake` returns `Int` (unchanged) but delegates the wait to `stream`.

### 2.7 net.internetisalie.lunar.rocks.browser.LuaRocksInstallExecutor (modified, MAINT-32-04)
- **Responsibility**: the LuaRocks install task the review flags as "constructed non-cancellable."
- **Threading**: background install task; the CLI capture already runs on the task background thread
  but discards the indicator.
- **Collaborators**: `LuaToolExecutionService.capture(cmd, timeout, stdin, indicator)`
  (`LuaToolExecutionService.kt:23-28`, already used), `Task.Backgroundable`.
- **Key API — explicit signature change**:
  ```kotlin
  // :53  Task.Backgroundable(project, job.title, false)  ->  (project, job.title, true)   // canBeCancelled flip
  // :54  override fun run(indicator) = execute(job)       ->  execute(job, indicator)      // capture the indicator
  private fun execute(job: Job)                            // OLD signature
  private fun execute(job: Job, indicator: ProgressIndicator)   // NEW: threads indicator to capture
  // :62  capture(command, LuaExecTimeout.INSTALL)         ->  capture(command, LuaExecTimeout.INSTALL, indicator = indicator)
  ```
  (`indicator` is not counted against the §3 3-arg cap — it is a platform threading/lifecycle
  handle passed alongside the `Job` context object; see §3.6, TC-08.)

## 3. Algorithms

### 3.1 Fencing the rockspec bridge out of the read lock at RockspecSourcePathProvider (MAINT-32-02)

**The real freeze path (each anchor verified):**
`LuaNameReference.kt:88` and `LuaRequireReference.kt:21` call
`PathConfiguration.getProjectSourcePathPatterns(project)` (`SourcePathPattern.kt:19-23`) **during
reference resolution, on a non-EDT background thread that holds the read lock**. That calls
`RockspecSourcePathProvider.derivedPatterns()` (`:49`) → `cache.value` → the `CachedValue` compute
(`:22-46`). The existing guard at `:24` is `app.isDispatchThread && !app.isUnitTestMode` — a
background read-lock thread is **not** the dispatch thread, so it falls to the `else` branch (`:39`)
→ `compute()` (`:54`) → `RockspecBridge.read(project, disco.rockspec)` (`:61`) → a synchronous
`lua` subprocess (≤ `LuaExecTimeout.PROBE`, 10 s) **under the read lock**, ×#rockspecs.

- **Input → Output**: `getProjectSourcePathPatterns` invoked under any read lock (EDT or background
  resolution) → **degraded static patterns returned immediately + one prewarm scheduled**, no
  bridge under the lock. After the prewarm lands, a later resolution pass returns the full derived
  patterns.
- **Steps** (all edits are inside `RockspecSourcePathProvider.kt`; `LuaRockspecDiscoveryService` is
  untouched):
  1. **Widen the guard predicate.** Change the `if` at `:24` from
     `app.isDispatchThread && !app.isUnitTestMode` to
     **`app.isReadAccessAllowed && !app.isUnitTestMode`**. `isReadAccessAllowed` is `true` on the EDT
     *and* on a background read-lock resolution thread — it is the exact predicate
     `LuaToolExecutionService.captureWithMillis` already uses to detect a read-lock caller
     (`LuaToolExecutionService.kt:47`). (Unit-test mode still bypasses so the existing off-pooled-thread
     tests drive `compute()` synchronously.)
  2. **Degraded return under the guard.** In the guarded branch, return
     `CachedValueProvider.Result.create(<degraded>, PsiModificationTracker.getInstance(project), forceRefreshTracker)`
     where `<degraded>` is
     `PathConfiguration.getStaticSourcePathPatterns(project) to emptyList<CModuleRock>()`.
     `getStaticSourcePathPatterns` (`SourcePathPattern.kt:32-37`) is a pure settings read (no index,
     no subprocess) — the user `package.path` with no rockspec-derived roots. This is a superset-safe
     degradation: resolution still finds stdlib/user-path modules; only rockspec-derived roots are
     briefly absent until the prewarm lands. **The degraded result is NOT cached against
     `PsiModificationTracker` alone** — because it is keyed on the same `forceRefreshTracker` the
     prewarm bumps, the next `cache.value` after `prewarm()` completes recomputes (identical to the
     existing MAINT-22-07 pattern at `:29`/`:37`).
  3. **Schedule the prewarm** (not a synchronous compute): call `prewarm()` inside the guarded
     branch, then return the degraded result.
  4. **`prewarm()` — dedup + off-read-lock bridge**:
     ```
     if (cache.hasUpToDateValue()) return          // already have real patterns → nothing to do
     if (!prewarmInFlight.compareAndSet(false, true)) return   // one job for N unresolved refs
     LunarCoroutineScopeService.getInstance(project).scope.launch {
         try {
             val paths = readAction { LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths() }
             val full = computePatternsFromPaths(paths)   // RockspecBridge.read loop, NO readAction around it
             cachedFull.set(full)                          // provider-local identity cache (step 6)
             forceRefreshTracker.incModificationCount()    // invalidate → next cache.value recomputes to full
         } finally { prewarmInFlight.set(false) }
     }
     ```
     `prewarmInFlight` is an `AtomicBoolean`. The `readAction { }` wraps **only** the bounded
     index-backed path enumeration; `computePatternsFromPaths` runs `RockspecBridge.read` per path
     **with no surrounding read action**, so the bridge subprocess never executes under a read lock
     — the fix's whole point. The `launch` uses the scope's default dispatcher (the same bare
     `scope.launch { readAction { … } }` shape already at `:27`).
  5. **`compute()` restructure.** The current `compute()` (`:54-73`) becomes `computePatternsFromPaths(paths)`
     taking the already-enumerated paths and running the `RockspecBridge.read` → `RockspecModuleDerivation.derive`
     loop (`:60-72`) unchanged. The synchronous `else` branch (`:39-45`, no read access held — e.g.
     the existing pooled-thread run-config setup and the unit tests) calls
     `readAction { discoverRockspecPaths() }` then `computePatternsFromPaths(...)`, preserving today's
     behavior for non-read-lock callers.
  6. **Provider-local prewarmed cache** (resolving the old `discoverRockspecData()` question):
     the prewarmed full result lives in a provider-local `@Volatile private var cachedFull: Pair<...>?`
     (or an `AtomicReference`), read back by `computePatternsFromPaths`'s fast path *or* simply
     surfaced by the `forceRefreshTracker` invalidation causing `cache.value` to recompute the full
     result on the next off-lock pass. **Its single consumer is `RockspecSourcePathProvider.cache`
     itself** — no cross-service `discoverRockspecData()` method is introduced (see step below).
- **`discoverRockspecData()` is DROPPED.** The old design added
  `LuaRockspecDiscoveryService.discoverRockspecData(): List<DiscoveredRockspec>` and re-pointed
  `RockspecSourcePathProvider.compute` at it. That is removed for three reasons proven by the consumer
  audit: (a) all six `discoverRockspecPaths()` consumers read only `DiscoveredRockspec.rockspec`, so
  a data method would have **zero real consumers**; (b) `.packageName` is read only inside
  `LuaRockspecDiscoveryService.compute():81` itself; (c) re-pointing `compute()` at a method that
  asserts `assertNoReadAccess()` would **throw on every cold reference resolution**, since `compute()`
  is already reached under the resolution read lock. The prewarmed identity has a home in the
  provider-local `cachedFull` (step 6), whose only consumer is the provider's own `cache`.
- **Rules / edge handling**:
  - No interpreter configured → `RockspecBridge.read` returns `null` (existing,
    `RockspecBridge.kt:39-45`) → that rockspec contributes no derived patterns; degraded static
    patterns still apply. Unchanged.
  - `DumbService.isDumb` early-return is preserved inside `LuaRockspecDiscoveryService.discoverRockspecPaths()`
    (`LuaRockspecDiscoveryService.kt:58`) — untouched.
  - No `ThreadingAssertions.assertNoReadAccess()` is added anywhere. The fence is structural: the
    `isReadAccessAllowed` guard diverts every read-lock caller to the degraded+prewarm path, and the
    prewarm runs the bridge outside `readAction`. There is no method a read-lock caller can reach
    that synchronously runs the bridge (proven by the consumer table below).
- **Cache invalidation**: unchanged model — `PsiModificationTracker` (PSI edits) + `forceRefreshTracker`
  (bumped by the prewarm on completion, `:29` today). A rockspec edit ticks `PsiModificationTracker`
  → both degraded and full results invalidate; the next resolution re-enters the guard and re-prewarms.

**Complete `derivedPatterns()` / `cModuleRockspecs()` consumer set (verified) — corrects the old
"two callers, run-config only" claim and Risk 1.1:**

| Consumer | file:line | Thread model | Pre-prewarm observes | Post-prewarm observes |
| :--- | :--- | :--- | :--- | :--- |
| `LuaNameReference` external resolution | `LuaNameReference.kt:88` | **read-lock, background** (reference resolution) | degraded static patterns (rockspec roots absent) | full derived patterns |
| `LuaRequireReference.resolve` | `LuaRequireReference.kt:21` | **read-lock, background** (reference resolution) | degraded static patterns | full derived patterns |
| `LuaCrossFileCompletionProvider.processCrossFileSymbols` | `LuaCrossFileCompletionProvider.kt:56` | completion (BGT, read-lock during PSI reads) | degraded static patterns | full derived patterns |
| `LuaModulePathResolver.resolve` | `LuaModulePathResolver.kt:21` | called from resolution/type paths (read-lock) | degraded static patterns | full derived patterns |
| `LuaTypeManagerImpl.doResolveModule` | `LuaTypeManagerImpl.kt:108` | type resolution (read-lock) | degraded static patterns | full derived patterns |
| `SourcePathPattern`→`PathConfiguration.getProjectSourcePathPatterns` (the shared entry) | `SourcePathPattern.kt:21` | inherits caller's thread | (as caller) | (as caller) |
| `LuaRockSourceRootDecorator` | `LuaRockSourceRootDecorator.kt:24` | project-view decorator (BGT) | degraded static patterns | full derived patterns |
| `RockspecRunPathProvider.luaPathPrefix` / `luaCPath` | `RockspecRunPathProvider.kt:10,16` | **run-config setup**, MAINT-22 launched `readAction {}` — read-lock | degraded static patterns | full derived patterns |

Every consumer is either under a read lock (→ degraded + prewarm, safe) or off-lock (→ synchronous
full compute). None can reach `RockspecBridge.read` under a read lock after step 1. The eventual
consistency is acceptable everywhere: resolution/completion re-runs on the next pass (already the
platform's model for a `CachedValue` that invalidates), and run-config setup re-reads after the
prewarm.

- **Complexity / bounds**: path enumeration is O(#rockspec files) index scan (bounded read action);
  identity resolution is O(#rockspecs) subprocess launches, each ≤ `LuaExecTimeout.PROBE` (10 s), now
  entirely off the lock on `LunarCoroutineScopeService.scope`.

### 3.2 Read-lock offload regression lock (MAINT-32-01)
- **Input → Output**: a `capture` invoked while `application.isReadAccessAllowed && !isDispatchThread`
  → process runs on a pooled thread (not the caller's read-lock thread).
- **Steps** (this is a *verification* of existing `:47-49`, not new logic):
  1. Test seeds a read action off the EDT (`ReadAction.run` on a pooled thread).
  2. Inside it, call `service.capture(sh("echo x"))`.
  3. Capture the executing thread name via the command (`echo $$` / a `ProcessListener`) or assert
     the call returns `COMPLETED` without a `softAssertBackgroundThread` soft-error being logged
     (the read-lock branch is the sanctioned path).
- **Rules / edge handling**: on the EDT (`isDispatchThread`), `softAssertBackgroundThread` logs a
  soft error — already covered by `testCaptureOnEdtLogsSoftAssert` (`:88`). No production behavior
  change; MAINT-32-01 = "the primitive already satisfies §2.1; assert it and forbid regressions."

### 3.3 Console REPL spawn off the EDT (MAINT-32-03)
- **Input → Output**: `actionPerformed(event)` on EDT → REPL launched with the process **started**
  off the EDT.
- **Steps**:
  1. Resolve `project` (existing `:17`).
  2. `newProjectBackgroundTask("Starting Lua Console", project) { runCatching { LuaConsoleRunner(project).initAndRun() }.onFailure { … } }.queue()`.
  3. `LuaConsoleRunner.createProcess()` (`:22`, `commandLine.createProcess()`) now executes on the
     Backgroundable thread, not the EDT.
  4. On `ExecutionException`, marshal `notifyFailure(project, message)` back via
     `ApplicationManager.getApplication().invokeLater` (the notification API is EDT-safe but the
     catch runs on the background thread).
- **Rules / edge handling**: `AbstractConsoleRunnerWithHistory.initAndRun` internally schedules its
  UI (console view) creation to the EDT via the platform; only the process construction is heavy and
  is what moves off-EDT. The command-line build (`LuaConsoleRunner.buildCommandLine`, `:36-47`) is
  in-memory settings reads and is fine either way. Empty/None: no interpreter → `ExecutionException`
  → notification (existing `:20-22`).

### 3.4 Coverage stats-file I/O off the EDT (MAINT-32-03)
- **Input → Output**: `doExecute` (EDT) → stale `luacov.stats.out` removed before the run, off EDT.
- **Steps**:
  1. Extract lines `:52-58` into `private fun clearStaleStats(workDir: String)` that does
     `File(workDir, "luacov.stats.out").takeIf { it.exists() }?.delete()`.
  2. `state.execute(...)` (`:60`) already returns an `ExecutionResult` whose process runs off-EDT;
     move the `clearStaleStats` call into the run path by wrapping the pre-run cleanup in the
     `RunProfileState.execute` command line preparation, OR schedule it on a pooled thread with
     `ApplicationManager.getApplication().executeOnPooledThread { clearStaleStats(workDir) }.get()`
     **before** `state.execute` (the cleanup must complete before the coverage run starts, so the
     `.get()` join is required; it is bounded single-file I/O, ≤ a few ms, and the join is the
     minimal off-EDT hop the review asks for).
- **Rules / edge handling**: `workDir` may be `null` (existing `:53` guard) → skip cleanup.
  Delete failure (locked file) is ignored (best-effort, matching current `:56`).

### 3.5 Health-monitor watch-set off the prepare phase (MAINT-32-03)
- **Input → Output**: an `AsyncFileListener.prepareChange(events)` → cheap, I/O-free membership
  test against a **pre-computed** watch set.
- **Steps**:
  1. Add `@Volatile private var watchSet: LuaHealthWatchSet = LuaHealthWatchSet.EMPTY`.
  2. `rebuildWatchSet()` runs the current `buildWatchSet()` body (`:196-203`, incl. `canonicalize`
     `File.canonicalPath` I/O) and assigns the field. Call it from `start()` (`:62`, off the
     listener) and from the existing `toolchainChanged` callback (`:67`) — both fire when the
     inventory/environments actually change, which is exactly when the canonical paths change.
  3. `HealthFileListener.prepareChange` (`:213`) reads the volatile `watchSet` field instead of
     calling `buildWatchSet()` — no `File.canonicalPath` on the listener path.
- **Rules / edge handling**: first VFS event before `start()` completes sees `EMPTY` → no match →
  no revalidation (benign; `start()` runs at project open before user edits). `LuaHealthWatchSet`
  needs an `EMPTY` companion constant (three empty sets) — additive, `LuaHealthWatchSet.kt`.
  `canonicalize` remains best-effort (`runCatching`, `:206`).

### 3.6 Cancellable build + install (MAINT-32-04)
- **Input → Output**: `executeMake` under a cancellable indicator → process killed on cancel;
  install task honors cancel.
- **Steps (build)**:
  1. In `WorkspaceBuildRunner.executeMake` (`:51-77`) build the same `cmd` (`config.buildCommandLine(exe)`,
     `:64`).
  2. Replace `createColoredProcessHandler` + `startNotify` + `handler.waitFor()` (`:65-74`) with
     `LuaToolExecutionService.getInstance().stream(cmd, consoleListener, LuaExecTimeout.INSTALL, colored = true, indicator = indicator)`,
     where `consoleListener` is a `ProcessListener` whose `onTextAvailable` writes to `console`
     (mirrors the current `console.attachToProcess(handler)`).
  3. Map the result: `result.exitCode` on `COMPLETED`; on `CANCELLED`/`TIMED_OUT` return the
     result's `exitCode` (`-1`) so `run` (`:38-49`) reports failure and stops the topo loop.
     `stream`'s `awaitStream` (`:111-127`) already polls the indicator and calls `destroyProcess()`.
  4. `run` (`:38-49`) already calls `ProgressManager.checkCanceled()` per rock (`:39`) — the
     per-process kill closes the remaining gap.
- **Steps (install)**: in `LuaRocksInstallExecutor.runInBackground` (`:52-56`), flip the
  `Task.Backgroundable` cancellable flag `false` → `true` at `:53`
  (`Task.Backgroundable(project, job.title, true)`), and change the `run(indicator)` body at `:54`
  from `execute(job)` to `execute(job, indicator)`. Change the `execute` signature (`:58`) from
  `private fun execute(job: Job)` to `private fun execute(job: Job, indicator: ProgressIndicator)`,
  and thread that indicator into the capture at `:62`:
  `capture(command, LuaExecTimeout.INSTALL, indicator = indicator)`. `capture` already accepts a
  trailing `indicator: ProgressIndicator?` (`LuaToolExecutionService.kt:23-28`); `captureWithMillis`
  polls it and kills the process on cancel via the same `awaitStream`-style slice used by `stream`.
- **Rules / edge handling**: `LuaExecTimeout.INSTALL` = 600 000 ms (`LuaExecTimeout.kt`) is the
  outer cap; user cancel returns promptly via the 100 ms poll slice (`SLICE_MILLIS`,
  `LuaToolExecutionService.kt:130`).

## 4. External Data & Parsing

No new external-data parsing is introduced. The rockspec bridge JSON is parsed by the existing
`RockspecBridge.parse` (`RockspecBridge.kt:76-117`, unchanged): the bridge `print`s one JSON object
(`package`, `version`, `build.type`, `build.modules`, `dependencies`), parsed via
`com.google.gson.JsonParser`. This design only changes **what thread** `RockspecBridge.read` runs
on, not how its output is parsed.

## 5. Data Flow

### Example 1: typing in a rockspec-heavy workspace (MAINT-32-02)
1. A highlight/resolution pass on a background read-lock thread resolves a `require`/name →
   `LuaRequireReference.resolve` (`:21`) / `LuaNameReference` (`:88`) →
   `getProjectSourcePathPatterns` → `RockspecSourcePathProvider.derivedPatterns()` → `cache.value`.
2. The widened guard sees `isReadAccessAllowed == true`: it returns **degraded static patterns**
   immediately and calls `prewarm()` — **no 10 s × #rockspecs bridge stall under the read lock**.
   The first unresolved reference wins the `prewarmInFlight` CAS; the other N-1 references in the
   same pass see `hasUpToDateValue()==false` but `prewarmInFlight==true` → no duplicate jobs.
3. `prewarm()` runs the `lua` bridge per rockspec on `LunarCoroutineScopeService.scope`, **outside
   any read action**; on completion it bumps `forceRefreshTracker`. The next resolution pass re-enters
   `cache.value`, now `hasUpToDateValue()` (or recomputes to the full patterns), and observes the
   rockspec-derived roots.

### Example 2: cancel a workspace build mid-flight (MAINT-32-04)
1. User clicks Build Workspace → `BuildWorkspaceAction.runBuildTask` (`:61`) starts a cancellable
   `Task.Backgroundable`.
2. `WorkspaceBuildRunner.executeMake` runs `luarocks make` via `stream(..., indicator)`.
3. User cancels → `indicator.isCanceled` true → `awaitStream` (`:114`) calls `destroyProcess()` →
   `luarocks make` is killed; `run` returns a failure `BuildOutcome`; the topo loop stops.

### Example 3: opening the Lua REPL (MAINT-32-03)
1. `LuaConsoleAction.actionPerformed` (EDT) queues `newProjectBackgroundTask`.
2. The Backgroundable calls `LuaConsoleRunner(project).initAndRun()`; `createProcess()` spawns the
   interpreter off the EDT.
3. The console view attaches (platform marshals its UI to the EDT); no `SlowOperationsException`.

## 6. Edge Cases

- **Bridge under the read lock**: structurally impossible after §3.1 step 1 — every read-lock caller
  hits the `isReadAccessAllowed` guard and is diverted to the degraded+prewarm path; the prewarm runs
  the bridge outside any `readAction`. The consumer table in §3.1 enumerates all eight
  `derivedPatterns()`/`cModuleRockspecs()` reachers and confirms none reaches `RockspecBridge.read`
  synchronously under a lock. (No `assertNoReadAccess()` fence is added; it would be unreachable given
  the guard, and would have crashed on cold resolution if placed on `compute()` — the defect the old
  design would have introduced.)
- **No interpreter configured**: `RockspecBridge.read` returns `null` → identities `null`; `update()`
  path uses `discoverRockspecPaths()` (never needs identity), so the action still enables on
  `count >= 2` (`BuildWorkspaceAction.kt:36`).
- **Console spawn failure off-EDT**: `ExecutionException` caught on the background thread → error
  notification marshalled to EDT via `invokeLater`.
- **Health watch-set race**: a VFS event arriving between `start()` and the first `rebuildWatchSet()`
  sees `EMPTY` and is dropped; the subsequent `toolchainChanged`/`start()` rebuild covers steady
  state. Missing a single pre-open event is benign (no user file edits yet).
- **Coverage cleanup join on EDT**: the `.get()` join in §3.4 is bounded single-file I/O; if it ever
  exceeds the slow-ops threshold the delete is instead scheduled fire-and-forget before
  `state.execute` returns its handler (DR-01 evaluates which).

## 7. Integration Points

No new `plugin.xml` extension points are added; all four affected components are **already
registered**, and this feature changes their bodies only:

```xml
<!-- plugin.xml (existing registrations — UNCHANGED by this feature) -->
<!-- :607  <programRunner implementation="net.internetisalie.lunar.coverage.LuaCoverageProgramRunner"/> -->
<!-- :629  <postStartupActivity implementation="net.internetisalie.lunar.toolchain.health.LuaToolHealthStartup"/> -->
<!-- :756  <action class="net.internetisalie.lunar.run.console.LuaConsoleAction" .../> -->
<!-- :774  <action class="net.internetisalie.lunar.rocks.build.BuildWorkspaceAction" .../> -->
```

`LuaRockspecDiscoveryService`, `WorkspaceBuildRunner`, `RockspecBridge`, and `LuaRocksInstallExecutor`
are plain services/objects reached programmatically (no EP). `LunarCoroutineScopeService` is the
existing MAINT-22 `@Service(Service.Level.PROJECT)` (registered implicitly by the platform's
light-service mechanism; no `plugin.xml` entry needed).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-32-01 | M | §1 (primitive already satisfies §2.1), §3.2 (regression lock) |
| MAINT-32-02 | M | §2.1, §3.1, §5-Ex1 |
| MAINT-32-03 | S | §2.3/§3.3 (console), §2.4/§3.4 (coverage), §2.5/§3.5 (health) |
| MAINT-32-04 | S | §2.6/§3.6 (build), §2.7/§3.6 (install), §5-Ex2 |

## 9. Alternatives Considered

- **Re-create `LuaProcessUtil` and "fix at the primitive" literally.** Rejected: the primitive was
  already consolidated into `LuaToolExecutionService` (13 callers) — recreating a second primitive
  would fork the discipline the review wanted unified. Verdict: **consolidate-not-harden**;
  MAINT-32-01 is a verify-and-fence on the surviving primitive, not a new class.
- **Run the rockspec bridge synchronously but inside a `ReadAction.nonBlocking` cancel-on-write.**
  Rejected: the bridge is a 10 s-per-rockspec subprocess; even a cancellable read action holds no
  guarantee it runs off the lock, and it still blocks the consuming pass. Widening the
  `RockspecSourcePathProvider` guard to `isReadAccessAllowed` + off-read-lock prewarm (§3.1) removes
  the subprocess from the lock entirely.
- **Split `LuaRockspecDiscoveryService` into path/data caches and re-point `RockspecSourcePathProvider`
  at a fenced `discoverRockspecData()`.** Rejected (this was the FAILed prior design): (a) it leaves
  the real freeze unfixed — `RockspecSourcePathProvider.compute():61` calls its OWN
  `RockspecBridge.read`, not the discovery service's; (b) `discoverRockspecData()` would have zero
  real consumers (all six discovery callers read only `.rockspec`; the sole `.packageName` reader is
  the map inside `LuaRockspecDiscoveryService.compute():81` itself); (c) re-pointing `compute()` at a
  method asserting `assertNoReadAccess()` crashes on every cold reference resolution, which arrives
  holding the read lock. The chosen fix guards at the actual seam and never adds an unreachable
  assertion.
- **Coroutine `withBackgroundProgress` for the console spawn instead of `newProjectBackgroundTask`.**
  Both satisfy the bar; `newProjectBackgroundTask` is chosen for symmetry with the existing
  health-monitor usage (`LuaToolHealthMonitor.kt:79`) and to avoid introducing a suspend boundary in
  a platform `AbstractConsoleRunnerWithHistory` call. DR-02 may revisit.
- **Keep `File.canonicalPath` in `prepareChange` but memoize per event.** Rejected: `prepareChange`
  must be I/O-free by platform contract; a per-event memo still does I/O on the listener path. A
  change-driven `@Volatile` snapshot (§3.5) removes it.
- **`LuaRocksEnvironment` I/O fix.** Dropped from scope: re-verified mooted — the facade is pure
  registry reads post-BUG-375/ROCKS-16 (`LuaToolResolver.resolve` has no `File.*` I/O). No design
  action; recorded here so the requirements-stub row is explicitly resolved, not silently skipped.

## 10. Open Questions

None.
