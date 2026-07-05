---
id: "TOOLING-03-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TOOLING-03"
folders:
  - "[[features/tooling/03-execution-and-injection/requirements|requirements]]"
---

# Technical Design: TOOLING-03 — Execution & Environment Injection

## 1. Architecture Overview

### Current State

Three subprocess idioms and three environment assemblies coexist:

**Process execution**
- `util/LuaProcessUtil.kt` — `capture(cmd, timeout = STANDARD_TIMEOUT)` with
  `STANDARD_TIMEOUT = 5_000` (`:12`) and sentinel exit codes
  `PROCESS_TIMEOUT_EXCEPTION_CODE = -1` / `PROCESS_EXECUTION_EXCEPTION_CODE = -2`
  (`:13-14`). `capture` (`:17-25`) escapes a held read lock: when
  `app.isReadAccessAllowed && !app.isDispatchThread` it offloads `doCapture` to a pooled
  thread and blocks on `.get()` (`:18-24`); `doCapture` (`:27-38`) constructs
  `CapturingProcessHandler(cmd)` *inside* the try (construction launches the process, so
  an unresolvable command throws `ExecutionException` there), maps `TimeoutException`→−1
  and `ExecutionException`→−2. `listen` (`:42-51`) wraps `OSProcessHandler` +
  `waitFor(timeout)` and **does not destroy the process on timeout** (leak).
- `lang/formatting/external/StyluaFormattingTask.kt` — raw `CapturingProcessHandler(cmd)`
  (`:39`), `runProcess(timeoutMs, true)` with `timeoutMs = 30_000` (`:50`, `:121`),
  stdin writer listener, `cancel()` via `destroyProcess()` (`:109-116`), and a sentinel
  comparison `exitCode == LuaProcessUtil.PROCESS_EXECUTION_EXCEPTION_CODE` (`:88`).
- `rocks/env/matrix/MatrixRunner.kt` — `processRunner` (`:85-98`) uses
  `ProcessHandlerFactory.getInstance().createColoredProcessHandler(command)`, an
  accumulating `ProcessListener`, `startNotify()` + unbounded `waitFor()`, and
  `handler.exitCode ?: -1`.

**Environment assembly** (three divergent copies)
- Lua run config (`run/LuaRunConfiguration.kt:272-283`): if config `sourcePath` set →
  `LUA_PATH = sourcePath` verbatim; else `union = (RockspecRunPathProvider.luaPathPrefix +
  expandSourcePath).trimEnd(';') + ";;"` and `LUA_CPATH = RockspecRunPathProvider.luaCPath`.
  **No PATH prepend at all** (no `LuaToolEnvironment` usage in the file).
- Test state (`run/test/LuaTestCommandLineState.kt:133-145`): the same LUA_PATH/LUA_CPATH
  union duplicated, *plus* `LuaToolEnvironment.prependToolDirsToPath(commandLine,
  targetProject)` (`:55`).
- Console via `command/LuaCommandLine.kt:18-30`: `LUA_PATH = expandSourcePath` only
  (`:22-25` — no rockspec prefix, no trailing `;;`), plus tool-dir prepend (`:28`).
- LuaRocks run config (`rocks/run/LuaRocksRunConfiguration.kt:181-199, 205-216`):
  executable from `LuaRocksSettings.getInstance().executablePath` (`:208`), **no PATH
  prepend and no LUA_PATH** — a bound hererocks `luarocks` can't see its own `lua`.

**PATH mechanics & caching**
- `tool/LuaToolEnvironment.kt:51-67` — the canonical prepend: join dirs with
  `File.pathSeparator` (`:54`), fold in the existing PATH from the command-line override,
  else `System.getenv("PATH")`, else `""` (`:59-61`), `newPath = prefix + sep + existing`
  (`:63`).
- `tool/LuaTerminalEnvironmentService.kt` — project service with a `@Volatile` cached dir
  list (`:37-38`), computed from `LuaToolManager.getAllValidTools(project)` (`:57-73`,
  which iterates `LuaToolType.entries`, `tool/LuaToolManager.kt:184-185`), invalidated by
  `LuaSettingsChangedListener.TOPIC` (`:40-49`; topic declared at
  `settings/LuaSettingsChangedEvent.kt:29`). **Staleness defect**:
  `LuaToolManager.registerTool` (`tool/LuaToolManager.kt:49-90`) mutates the inventory
  and fires **no** topic, so a newly registered tool is invisible to the terminal until
  an unrelated settings change.
- `tool/terminal/LuaShellExecOptionsCustomizer.kt:29-41` — terminal customizer; runs on a
  background thread without a read action (doc `:15-17`); `MutableShellExecOptions.envs`
  is read-only, so PATH is mutated via `prependEntryToPATH(dir)` in **reverse** order
  (`:33-39`) to keep the highest-priority dir first. Registered in
  `META-INF/lunar-terminal.xml` under the optional terminal dependency (`plugin.xml:29`).

### Prior Art in This Repo

| Component | Location | Disposition |
|---|---|---|
| `LuaProcessUtil` (capture/listen, sentinels, read-lock escape) | `util/LuaProcessUtil.kt` | **Replaced** by §2.3; deleted in TOOLING-05 |
| Raw `CapturingProcessHandler` in Stylua | `lang/formatting/external/StyluaFormattingTask.kt:39,50` | **Replaced** by `capture(…, FORMAT)`; cutover in TOOLING-05 |
| `MatrixRunner.processRunner` | `rocks/env/matrix/MatrixRunner.kt:85-98` | **Replaced** by `stream(…, colored = true)`; cutover in TOOLING-05 |
| `LuaToolEnvironment.prependToolDirsToPath` | `tool/LuaToolEnvironment.kt:40-67` | **Replaced** by `LuaLaunchEnvironment.applyTo` (§3.5 — same algorithm, verified by existing `tool/LuaToolEnvironmentTest.kt`) |
| `LuaTerminalEnvironmentService` (@Volatile cache) | `tool/LuaTerminalEnvironmentService.kt:37-78` | **Replaced** by the builder cache (§2.5, §3.7) |
| `tool/terminal/LuaShellExecOptionsCustomizer` | `tool/terminal/LuaShellExecOptionsCustomizer.kt` | **Moved/evolved** to `toolchain.terminal` (§2.7); mechanics unchanged |
| `command/LuaCommandLine.kt` (3 factory functions) | `command/LuaCommandLine.kt:11-45` | **Replaced** by §2.6; `newLuaDefaultInterpreterCommandLine` (`:11-16`) has no callers (grep-verified) and is dropped without replacement |
| LUA_PATH union logic | `run/LuaRunConfiguration.kt:272-283` and `run/test/LuaTestCommandLineState.kt:133-145` | **Absorbed** into §3.4 (single copy) |
| `RockspecRunPathProvider` | `rocks/RockspecRunPathProvider.kt:8-22` | **Retained**, consumed by the builder |
| `RockspecSourcePathProvider` (CachedValue, PSI-tracked) | `rocks/RockspecSourcePathProvider.kt:22-46` | **Retained** — the reason the builder does *not* cache LUA_PATH (§3.7) |
| `LuaToolValidator` / `LuaToolHealthChecker` / rocks services timeouts | see requirements §TOOLING-03-03 table | **Mapped** to timeout classes; call-site migration in TOOLING-05 (probe engine itself is TOOLING-01) |

### Target State

```
                        ┌────────────────────────────────────────┐
 consumers (05)         │ toolchain.exec                         │
 run cfg / rocks cfg    │  LuaToolExecutionService   (APP svc)   │──▶ CapturingProcessHandler
 test / console         │   capture(cmd, timeout, indicator)     │    OSProcessHandler /
 luacheck / stylua      │   stream(cmd, listener, …)             │    ColoredProcessHandler
 matrix / provisioner ─▶│  LuaExecutionEnvironmentBuilder (PROJ) │
 terminal customizer ──▶│   pathPrependDirs() [cached]           │◀── LuaToolResolver (02)
                        │   build(sourcePathOverride)            │◀── RockspecRunPathProvider
                        │   LuaLaunchEnvironment.applyTo(cmd)    │◀── LuaProjectSettings.expandSourcePath
                        │  LuaInterpreterCommandLines            │
                        └────────────▲───────────────────────────┘
                                     │ invalidate on LuaToolchainListener.TOPIC (02)
```

TOOLING-02 names (`LuaToolResolver`, `LuaToolchainListener.TOPIC`, `LuaRegisteredTool`,
`LuaToolKindRegistry`, `Capability.RUNTIME`) are fixed by the architecture contract
([../tooling-architecture.md](../tooling-architecture.md) §2–§4) and are build-order
prerequisites, not open questions.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.toolchain.exec.LuaExecTimeout`

- **Responsibility**: the closed set of process timeout budgets (TOOLING-03-03).
- **Key API**:
  ```kotlin
  enum class LuaExecTimeout(val millis: Int) {
      PROBE(10_000),    // version/banner probes (was LuaToolValidator.VERSION_TIMEOUT_MS)
      COMMAND(15_000),  // default; short tool commands (was 5-15s scattered)
      FORMAT(30_000),   // stylua (was StyluaFormattingTask.timeoutMs)
      NETWORK(120_000), // luarocks install/upload from the browser (was 120_000 twice)
      INSTALL(600_000), // provisioning / matrix rows (was PROVISION_TIMEOUT_MS)
  }
  ```

### 2.2 `net.internetisalie.lunar.toolchain.exec.LuaExecResult`

- **Responsibility**: unambiguous process outcome (TOOLING-03-04); replaces −1/−2
  sentinels (`util/LuaProcessUtil.kt:13-14`).
- **Key API**:
  ```kotlin
  enum class LuaExecOutcome { COMPLETED, TIMED_OUT, START_FAILED, CANCELLED }

  data class LuaExecResult(
      val output: ProcessOutput,          // com.intellij.execution.process.ProcessOutput
      val outcome: LuaExecOutcome,
  ) {
      val exitCode: Int get() = output.exitCode
      val stdout: String get() = output.stdout
      val stderr: String get() = output.stderr
      val isSuccess: Boolean get() = outcome == LuaExecOutcome.COMPLETED && output.exitCode == 0
  }
  ```
- On `START_FAILED` the output is `ProcessOutput("", exceptionMessage, -1, false, false)`
  — the exit code is a placeholder; `outcome` is authoritative and callers must never
  branch on magic exit codes.

### 2.3 `net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService`

- **Responsibility**: the only subprocess entry point (contract §5).
- **Threading**: background threads only — `ThreadingAssertions.softAssertBackgroundThread()`
  on entry (logs an error in production, fails tests; verified in intellij-community
  `platform/core-api/src/com/intellij/util/concurrency/ThreadingAssertions.java:111-113`;
  the method is annotated `@Obsolete` upstream but its behavior is stable and it remains the
  idiomatic non-throwing background-thread assertion — implementer note, not a blocker);
  `capture` keeps the read-lock pooled-thread escape (§3.1 step 2). No `Project` refs
  (contract §10).
- **Collaborators**: `CapturingProcessHandler` (incl.
  `runProcessWithProgressIndicator(indicator, timeoutMs, destroyOnTimeout)`, verified in
  intellij-community `platform/platform-util-io/src/com/intellij/execution/process/
  CapturingProcessHandler.java:70-79`), `OSProcessHandler`,
  `ProcessHandlerFactory.getInstance().createColoredProcessHandler` (idiom from
  `rocks/env/matrix/MatrixRunner.kt:86`), `ProgressManager`.
- **Key API**:
  ```kotlin
  @Service(Service.Level.APP)
  class LuaToolExecutionService {
      /** TOOLING-03-01/03/04/05/06/17. Runs [cmd] to completion, capturing output.
       *  [stdin], if non-null, is written to the process stdin and the stream closed
       *  (stylua pipes the document text — TOOLING-05 migrates it here). */
      fun capture(
          cmd: GeneralCommandLine,
          timeout: LuaExecTimeout = LuaExecTimeout.COMMAND,
          stdin: String? = null,
          indicator: ProgressIndicator? = null,
      ): LuaExecResult

      /** TOOLING-03-02. Streams output to [listener]; destroys the process on timeout. */
      fun stream(
          cmd: GeneralCommandLine,
          listener: ProcessListener,
          timeout: LuaExecTimeout = LuaExecTimeout.COMMAND,
          colored: Boolean = false,
          indicator: ProgressIndicator? = null,
      ): LuaExecResult

      @TestOnly internal fun captureWithMillis(cmd, millis, indicator = null): LuaExecResult
      @TestOnly internal fun streamWithMillis(cmd, listener, millis, colored = false, indicator = null): LuaExecResult

      companion object { fun getInstance(): LuaToolExecutionService }
  }
  ```
  The public methods delegate to the `*WithMillis` internals with `timeout.millis`, so
  unit tests can exercise timeout paths in milliseconds (TC 5, 9).
- **Environment & working directory** ride on the `GeneralCommandLine` itself
  (`withEnvironment(...)` / `withWorkDirectory(...)`), per contract §10.6 — there is no
  `execute(env, workDir)` facade. Callers that need injected env/workdir (notably
  TOOLING-04 provisioning build steps, and run configs after `LuaLaunchEnvironment.applyTo`)
  construct the command line with both set, then call `capture`/`stream`.

### 2.4 `net.internetisalie.lunar.toolchain.exec.LuaLaunchEnvironment`

- **Responsibility**: the computed injection triple + its application to a command line
  (TOOLING-03-10).
- **Key API**:
  ```kotlin
  data class LuaLaunchEnvironment(
      val pathPrependDirs: List<Path>,  // highest priority first
      val luaPath: String?,             // null = leave LUA_PATH untouched
      val luaCPath: String?,            // null = leave LUA_CPATH untouched
      val luarocksConfig: String?,      // null = leave LUAROCKS_CONFIG untouched (§3.6)
  ) {
      /** Applies §3.5 to [commandLine]; returns it for chaining. */
      fun applyTo(commandLine: GeneralCommandLine): GeneralCommandLine
  }
  ```

### 2.5 `net.internetisalie.lunar.toolchain.exec.LuaExecutionEnvironmentBuilder`

- **Responsibility**: computes the `LuaLaunchEnvironment` for a project; owns the
  project-scoped PATH-dir cache (TOOLING-03-07/08/09/11/14). Replaces
  `LuaTerminalEnvironmentService` + `LuaToolEnvironment`.
- **Threading**: `pathPrependDirs()` safe on any thread, no PSI/VFS (terminal contract,
  as today `tool/LuaTerminalEnvironmentService.kt:28-30`); `build()` may reach PSI only
  through `RockspecSourcePathProvider`, which is EDT-safe by construction
  (`rocks/RockspecSourcePathProvider.kt:22-46`).
- **Collaborators**: `LuaToolResolver` + `LuaToolKindRegistry` (TOOLING-02/01, contract
  §2–§3), `LuaToolchainProjectSettings.activeEnvironment()` (TOOLING-02, contract §10.5 —
  for the LUAROCKS_CONFIG derivation, §3.4 step 7), `RockspecRunPathProvider`
  (`rocks/RockspecRunPathProvider.kt:8-22`), `LuaProjectSettings.State.expandSourcePath`
  (`settings/LuaProjectSettings.kt:132-134`), `LuaToolchainListener.TOPIC` (contract §4).
- **Key API**:
  ```kotlin
  @Service(Service.Level.PROJECT)
  class LuaExecutionEnvironmentBuilder(private val project: Project) : Disposable {
      /** TOOLING-03-07/11. Cached resolver-derived PATH prepend dirs. */
      fun pathPrependDirs(): List<Path>

      /** TOOLING-03-08/09/14. Full environment; [sourcePathOverride] = run-config sourcePath. */
      fun build(sourcePathOverride: String? = null): LuaLaunchEnvironment

      /** Drops the cached dir list (also used by tests). */
      fun invalidate()

      override fun dispose() {}

      companion object {
          fun getInstance(project: Project): LuaExecutionEnvironmentBuilder = project.service()
          /** Contract-§5 facade: LuaExecutionEnvironmentBuilder.forProject(project). */
          fun forProject(project: Project): LuaLaunchEnvironment = getInstance(project).build()
      }
  }
  ```
- Cache field: `@Volatile private var cachedPathPrependDirs: List<Path>? = null`
  (pattern from `tool/LuaTerminalEnvironmentService.kt:37-38`). Subscription in `init`:
  `ApplicationManager.getApplication().messageBus.connect(this)
  .subscribe(LuaToolchainListener.TOPIC, …)` → `invalidate()` — app-level bus because
  the TOOLING-02 topic is app-level (contract §4); the connection is disposed with the
  service.

### 2.6 `net.internetisalie.lunar.toolchain.exec.LuaInterpreterCommandLines`

- **Responsibility**: interpreter `GeneralCommandLine` construction (TOOLING-03-13);
  replaces `command/LuaCommandLine.kt`.
- **Threading**: `forProject` resolves tools and builds the environment — background
  thread (callers are `startProcess`/console builders, already off-EDT).
- **Key API**:
  ```kotlin
  object LuaInterpreterCommandLines {
      /** Replaces newLuaInterpreterCommandLine (command/LuaCommandLine.kt:32-45). */
      fun forBinary(executable: Path): GeneralCommandLine

      /** Replaces newProjectLuaInterpreterCommandLine (command/LuaCommandLine.kt:18-30):
       *  resolver(RUNTIME) + full environment applied. Null when no runtime resolves. */
      fun forProject(project: Project): GeneralCommandLine?
  }
  ```
- The interpreter executable comes from the TOOLING-02 resolver's RUNTIME resolution
  (`LuaToolResolver.resolveRuntime(project)` — the capability-based overload the run-config
  dropdown also needs, contract §2.1/§3); run configs with an explicit interpreter
  override call `forBinary(Path.of(tool.path))` directly (TOOLING-05).
- `newLuaDefaultInterpreterCommandLine` (`command/LuaCommandLine.kt:11-16`) is dead code
  (no callers, grep-verified) — dropped without replacement.

### 2.7 `net.internetisalie.lunar.toolchain.terminal.LuaShellExecOptionsCustomizer`

- **Responsibility**: terminal PATH injection (TOOLING-03-12). A move of
  `tool/terminal/LuaShellExecOptionsCustomizer.kt` with one change: the directory source
  becomes the builder.
- **Threading**: `customizeExecOptions` runs on a background thread without a read
  action (`@RequiresBackgroundThread`/`@RequiresReadLockAbsence` on the EP, documented at
  `tool/terminal/LuaShellExecOptionsCustomizer.kt:15-17`) — satisfied because
  `pathPrependDirs()` touches no PSI/VFS.
- **Key API** (unchanged shape):
  ```kotlin
  class LuaShellExecOptionsCustomizer : ShellExecOptionsCustomizer {
      override fun customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions) {
          val dirs = LuaExecutionEnvironmentBuilder.getInstance(project).pathPrependDirs()
          for (dir in dirs.asReversed()) shellExecOptions.prependEntryToPATH(dir)
      }
  }
  ```
- `MutableShellExecOptions.envs` is read-only; `prependEntryToPATH(Path)` inserts first,
  joins with the *remote* path separator, and translates local→remote paths — hence the
  reverse iteration to keep the highest-priority dir first (mechanics verbatim from
  `tool/terminal/LuaShellExecOptionsCustomizer.kt:18-24,33-39`).

## 3. Algorithms

### 3.1 `capture` (TOOLING-03-01/03/04/05/06/17)

- **Input → Output**: `(GeneralCommandLine, LuaExecTimeout, stdin: String?, ProgressIndicator?) → LuaExecResult`
- **Steps** (public method delegates to `captureWithMillis(cmd, timeout.millis, stdin, indicator)`):
  1. `ThreadingAssertions.softAssertBackgroundThread()`.
  1b. **stdin** (TOOLING-03-17): if `stdin != null`, after the handler starts, write it to
     `handler.processInput` (UTF-8) and close the stream — before the process wait. This
     replaces stylua's raw-handler stdin pipe (`StyluaFormattingTask.kt` feeds the document
     text via `--stdin-filepath`); a `null` stdin leaves the child's stdin untouched.
  2. **Read-lock escape** (verbatim semantics of `util/LuaProcessUtil.kt:18-24`): if
     `application.isReadAccessAllowed && !application.isDispatchThread`, run steps 3–6 via
     `application.executeOnPooledThread(Callable { … }).get()` so the process wait does
     not sit inside a read action.
  3. `val handler = try { CapturingProcessHandler(cmd) } catch (e: ExecutionException) {
     return LuaExecResult(ProcessOutput("", e.message ?: "", -1, false, false), START_FAILED) }`
     — construction inside the try because it launches the process
     (`util/LuaProcessUtil.kt:29-31`).
  4. `val effIndicator = indicator ?: ProgressManager.getInstance().progressIndicator`.
  5. `val output = if (effIndicator != null)
     handler.runProcessWithProgressIndicator(effIndicator, millis, /*destroyOnTimeout=*/true)
     else handler.runProcess(millis, /*destroyOnTimeout=*/true)`; a defensive
     `catch (TimeoutException)` maps to `TIMED_OUT` (parity with `LuaProcessUtil.kt:33-34`).
  6. Outcome: `output.isCancelled → CANCELLED`; else `output.isTimeout → TIMED_OUT`;
     else `COMPLETED` (flags verified on `ProcessOutput.java:118,126`).
- **Rules / edge handling**: `millis < 0` is not supported (no unbounded capture —
  `INSTALL` is the ceiling); empty stdout/stderr are legal; the caller owns stderr-tail
  presentation.

### 3.2 `stream` (TOOLING-03-02/03/06)

- **Input → Output**: `(GeneralCommandLine, ProcessListener, LuaExecTimeout, Boolean, ProgressIndicator?) → LuaExecResult`
- **Steps**:
  1. `ThreadingAssertions.softAssertBackgroundThread()`.
  2. `val handler = try { if (colored)
     ProcessHandlerFactory.getInstance().createColoredProcessHandler(cmd) else
     OSProcessHandler(cmd) } catch (e: ExecutionException) { return … START_FAILED }`.
  3. `handler.addProcessListener(listener); handler.startNotify()`.
  4. Wait loop in 100 ms slices: `while (!handler.waitFor(100)) { if
     (indicator?.isCanceled == true) { handler.destroyProcess(); handler.waitFor(); return
     CANCELLED }; if (elapsed >= millis) { handler.destroyProcess(); handler.waitFor();
     return TIMED_OUT } }` — destroying on timeout fixes the `LuaProcessUtil.listen` leak
     (`util/LuaProcessUtil.kt:42-51` never destroys).
  5. Terminated: `LuaExecResult(ProcessOutput("", "", handler.exitCode ?: -1, false,
     false), COMPLETED)` — stdout/stderr empty because the listener consumed them (the
     `MatrixRunner.processRunner` accumulation pattern, `rocks/env/matrix/MatrixRunner.kt:87-97`,
     moves to the caller's listener).
- **Rules / edge handling**: `exitCode == null` after termination (theoretical) maps to
  −1 with `COMPLETED`; listener exceptions are not caught (caller bug, surfaced loudly).

### 3.3 `pathPrependDirs` (TOOLING-03-07/11)

- **Input → Output**: `() → List<Path>` (highest priority first).
- **Steps**:
  1. `cachedPathPrependDirs?.let { return it }`.
  2. `dirs = LuaToolKindRegistry.allKinds()` — **declaration order** of the built-in kind
     list (deterministic; the successor of today's `LuaToolType.entries` iteration in
     `getAllValidTools`, `tool/LuaToolManager.kt:184-185`) —
     `.mapNotNull { LuaToolResolver.resolve(project, it.id) }`
     `.mapNotNull { tool -> tool.path.takeIf { it.isNotBlank() } }`
     `.mapNotNull { runCatching { Path.of(it).parent }.getOrNull() }`
     `.distinct()` (first occurrence wins).
  3. `cachedPathPrependDirs = dirs; return dirs`.
- **Rules / edge handling**: RUNTIME kinds are **included** (behavior improvement: the
  project's `lua` lands on the terminal/subprocess PATH; today interpreters are outside
  the tool inventory and never prepended). Unresolvable kinds contribute nothing. Empty
  result is legal (no tools configured). Blank/`.` parent paths are skipped by the
  `runCatching`/`parent` step — same hardening as
  `tool/LuaTerminalEnvironmentService.kt:62-64`.

### 3.4 LUA_PATH / LUA_CPATH / LUAROCKS_CONFIG computation (TOOLING-03-08/09/14/16)

- **Input → Output**: `(project, sourcePathOverride: String?) → (luaPath: String?, luaCPath: String?, luarocksConfig: String?)`
- **Steps** (single source of truth for the logic currently duplicated at
  `run/LuaRunConfiguration.kt:272-283` and `run/test/LuaTestCommandLineState.kt:133-145`):
  1. If `sourcePathOverride?.isNotEmpty() == true` → `luaPath = sourcePathOverride`
     verbatim, `luaCPath = null`, **stop** (exact current run-config behavior: the
     override branch sets only LUA_PATH, `run/LuaRunConfiguration.kt:272-275`).
  2. `prefix = RockspecRunPathProvider.luaPathPrefix(project)` — '?'-expanded local
     roots each ending `;` (`rocks/RockspecRunPathProvider.kt:8-11`).
  3. `projectPath = LuaProjectSettings.getInstance(project).state.expandSourcePath(project)`
     (`settings/LuaProjectSettings.kt:132-134`).
  4. `union = (prefix + projectPath).trimEnd(';') + ";;"`.
  5. `luaPath = if (union == ";;") null else union` (trailing `;;` keeps Lua's default
     search paths).
  6. `luaCPath = RockspecRunPathProvider.luaCPath(project)` — already
     `"<treeRoot>/lib/lua/<X.Y>/?.so;;"` or `null` (`rocks/RockspecRunPathProvider.kt:14-22`).
  7. **LUAROCKS_CONFIG** (TOOLING-03-16, contract §6): let
     `env = LuaToolchainProjectSettings.getInstance(project).activeEnvironment()`; if
     `env != null` and `File(env.rootDir, "luarocks-config.lua").isFile` →
     `luarocksConfig = "<env.rootDir>/luarocks-config.lua"`, else `null`. This makes the
     Windows standalone `luarocks.exe` find its tree config; harmless for POSIX
     source-built trees (their wrappers carry baked config). It is a pure settings +
     `File.isFile` read (no PSI/VFS), so it is **not** cached with `pathPrependDirs` but
     recomputed per `build()` — cheap, and correct when a provision writes the file.
- **Rules / edge handling**: the console path today omits the prefix and `;;`
  (`command/LuaCommandLine.kt:22-25`); it unifies onto this formula — an intentional,
  documented behavior change (console gains rockspec-derived paths and keeps default
  paths via `;;`).

### 3.5 `LuaLaunchEnvironment.applyTo` (TOOLING-03-10)

- **Input → Output**: `GeneralCommandLine → GeneralCommandLine` (mutated in place).
- **Steps** (PATH mechanics verbatim from `tool/LuaToolEnvironment.kt:51-67`):
  1. If `pathPrependDirs` non-empty:
     a. `prefix = pathPrependDirs.joinToString(File.pathSeparator) { it.toString() }`.
     b. `existing = commandLine.environment["PATH"] ?: System.getenv("PATH") ?: ""` —
        `GeneralCommandLine.environment` overrides the parent env for matching keys, so
        the existing PATH must be folded in manually.
     c. `commandLine.environment["PATH"] = if (existing.isBlank()) prefix else
        "$prefix${File.pathSeparator}$existing"` — existing entries always preserved,
        prepend dirs first.
  2. `luaPath?.let { commandLine.environment["LUA_PATH"] = it }`.
  3. `luaCPath?.let { commandLine.environment["LUA_CPATH"] = it }`.
  4. `luarocksConfig?.let { if ("LUAROCKS_CONFIG" !in commandLine.environment)
     commandLine.environment["LUAROCKS_CONFIG"] = it }` — a user-set `LUAROCKS_CONFIG`
     (applied before `applyTo`) wins; the derived default only fills an absent key.
- **Rules / edge handling**: last-write-wins for LUA_PATH/LUA_CPATH — consumers apply
  user run-config env vars *before* `applyTo`, so the computed values win, matching
  today's ordering (`run/LuaRunConfiguration.kt:257` then `:272-283`). PATH is merged,
  never clobbered. No local→remote translation (these command lines run locally; the
  terminal path uses `prependEntryToPATH` for that, §2.7 — distinction documented at
  `tool/LuaToolEnvironment.kt:26-28`).

### 3.6 `LuaInterpreterCommandLines` (TOOLING-03-13)

- **`forBinary(executable: Path)`** (from `command/LuaCommandLine.kt:32-45`):
  1. `cmd = GeneralCommandLine(executable.toString())
     .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
     .withWorkingDirectory(executable.parent)` (`:35-37`).
  2. **Jar special case kept** (`:39-42`): if
     `executable.fileName.toString().endsWith(".jar", ignoreCase = true)` →
     `cmd.exePath = "java"; cmd.addParameters("-cp", executable.toString(), "lua")`.
  3. Return `cmd`. (Unlike the legacy function it never returns null: it takes a real
     `Path`, not a `VirtualFile` that may be missing from the VFS snapshot.)
- **`forProject(project)`**:
  1. `tool = LuaToolResolver.resolveRuntime(project) ?: return null` (TOOLING-02).
  2. `cmd = forBinary(Path.of(tool.path))`.
  3. `LuaExecutionEnvironmentBuilder.getInstance(project).build().applyTo(cmd)`.
  4. Return `cmd`.

### 3.7 Cache & invalidation (TOOLING-03-11)

- **What is cached**: only `pathPrependDirs` (resolver-derived, changes exactly when the
  toolchain changes). `luaPath`/`luaCPath` are **not** cached here — they depend on
  rockspec PSI, and `RockspecSourcePathProvider` already caches them under
  `PsiModificationTracker` (`rocks/RockspecSourcePathProvider.kt:21-46`); double-caching
  would reintroduce invalidation bugs.
- **Invalidation**: `LuaToolchainListener.TOPIC` (app-level, fired by *every*
  registry/binding/environment mutation — contract §4) → `cachedPathPrependDirs = null`.
- **Defect fixed**: today `LuaToolManager.registerTool` (`tool/LuaToolManager.kt:49-90`)
  fires no event while `LuaTerminalEnvironmentService` only listens to
  `LuaSettingsChangedListener.TOPIC` (`tool/LuaTerminalEnvironmentService.kt:40-49`), so
  registering a tool leaves the terminal PATH stale. Under TOOLING-02 every mutation
  fires the topic, and this builder subscribes to it.

## 4. External Data & Parsing

None. The execution service returns raw stdout/stderr and an outcome; all parsing
(version regexes, luacheck problem lines, luarocks tables) stays in the consuming
components. The environment builder consumes only in-process APIs
(`RockspecRunPathProvider`, `LuaProjectSettings`, the resolver).

## 5. Data Flow

### Example 1: Run a Lua script (post-TOOLING-05 target flow)

1. `LuaRunConfiguration.startProcess` resolves the interpreter (config override →
   `forBinary`; else `LuaInterpreterCommandLines` path via `resolveRuntime`).
2. Adds interpreter/program args and user env vars (as today,
   `run/LuaRunConfiguration.kt:244-257`).
3. `LuaExecutionEnvironmentBuilder.getInstance(project).build(config.sourcePath)
   .applyTo(commandLine)` — PATH prepend (new for this run config: today it has none),
   LUA_PATH union or verbatim override, LUA_CPATH.
4. Hands the command line to `ProcessHandlerFactory` (run configs keep platform process
   handlers; the exec service is for plugin-internal one-shot processes).

### Example 2: Stylua format

1. `StyluaFormattingTask.run` builds the command line (unchanged) and calls
   `LuaToolExecutionService.getInstance().capture(cmd, FORMAT)` after attaching its
   stdin-writer listener — replaced idiom: raw handler + `runProcess(30_000, true)`
   (`StyluaFormattingTask.kt:39,50`).
2. Branches on `result.outcome`: `TIMED_OUT` → timeout message; `START_FAILED` →
   "could not execute"; `COMPLETED` + `exitCode != 0` → first stderr line — replacing
   the sentinel comparison at `StyluaFormattingTask.kt:88`.

### Example 3: Terminal opens

1. Terminal plugin calls `customizeExecOptions(project, options)` on a background thread.
2. Customizer reads `pathPrependDirs()` — volatile cache hit after first call.
3. `prependEntryToPATH` per dir in reverse; shell starts with bound tools and the
   project's `lua` on PATH.
4. User binds a new stylua in settings → TOOLING-02 registry fires
   `LuaToolchainListener.TOPIC` → builder cache invalidated → next terminal reflects it.

## 6. Edge Cases

| Case | Handling |
|---|---|
| No tools/kinds resolve | `pathPrependDirs() == emptyList()`; `applyTo` skips PATH entirely |
| Empty source path + no rockspecs | `luaPath == null`; LUA_PATH untouched (TC 12) |
| `sourcePathOverride` set + rockspecs present | override wins verbatim, no `;;`, `luaCPath = null` (TC 20; today's semantics) |
| Command not on PATH / not executable | `START_FAILED`, no exception to caller (TC 3) |
| Process exits −1 legitimately | `COMPLETED` with `exitCode == -1` — distinguishable from timeout/failure via `outcome` (impossible with the legacy sentinels) |
| `capture` under a read action | pooled-thread escape (§3.1 step 2) — wait happens outside the read lock |
| `capture` on the EDT | soft assert logs an error; call still completes (production-safe, test-fatal) |
| Stream timeout | process destroyed + awaited before returning (fixes leak) |
| Interpreter is a `.jar` | `java -cp <jar> lua` (TC 19; `command/LuaCommandLine.kt:39-42` kept) |
| Windows separators | `File.pathSeparator` (`;`) for PATH joins; LUA_PATH/LUA_CPATH use Lua's `;` templates unchanged (they already normalize `\\`→`/`, `rocks/RockspecRunPathProvider.kt:21`) |
| Topic fires during `pathPrependDirs()` compute | benign race: worst case one recompute; `@Volatile` write of `null` after the compute's write is a full invalidation (same tolerance as today's service) |

## 7. Integration Points

### 7.1 Registrations

Both services are annotation-registered (`@Service`) — **no `plugin.xml` entries**, the
same pattern as `LuaTerminalEnvironmentService` / `HererocksProvisioner` today. The only
XML change is re-pointing the terminal customizer in `META-INF/lunar-terminal.xml`
(loaded via `plugin.xml:29`
`<depends optional="true" config-file="lunar-terminal.xml">org.jetbrains.plugins.terminal</depends>`):

```xml
<!-- META-INF/lunar-terminal.xml (full file; only the implementation FQN changes) -->
<idea-plugin>
  <extensions defaultExtensionNs="org.jetbrains.plugins.terminal">
    <shellExecOptionsCustomizer
        implementation="net.internetisalie.lunar.toolchain.terminal.LuaShellExecOptionsCustomizer"/>
  </extensions>
</idea-plugin>
```

The old `net.internetisalie.lunar.tool.terminal.LuaShellExecOptionsCustomizer` class
loses its registration in this feature and is deleted with the rest of `tool/` in
TOOLING-05.

### 7.2 Consumer target API (executed by TOOLING-05; normative here)

| Consumer | Today (cite) | Target call |
|---|---|---|
| Lua run config | LUA_PATH/LUA_CPATH union inline, **no PATH prepend** (`run/LuaRunConfiguration.kt:272-283`) | `build(options.sourcePath).applyTo(cmd)`; interpreter via `forBinary`/resolver |
| LuaRocks run config | `LuaRocksSettings.executablePath`, **no prepend, no LUA_PATH** (`rocks/run/LuaRocksRunConfiguration.kt:208`, `181-199`) | resolver(`luarocks`) + `build().applyTo(cmd)` — **fixes the missing PATH prepend** |
| Test state | duplicated union + `prependToolDirsToPath` (`run/test/LuaTestCommandLineState.kt:55,133-145`) | `build(config.sourcePath).applyTo(cmd)` |
| Console | `newProjectLuaInterpreterCommandLine` (`run/console/LuaConsoleRunner.kt:36-44`) | `LuaInterpreterCommandLines.forProject(project)` |
| Luacheck annotator | `LuaProcessUtil.listen`, 5 s (`analysis/luacheck/LuaCheckInvoker.kt:24`) | `stream(cmd, listener, COMMAND)` + `build().applyTo(cmd)` |
| Stylua | raw handler, 30 s (`StyluaFormattingTask.kt:39,50`) | `capture(cmd, FORMAT)` |
| Matrix runner | `ProcessHandlerFactory` + unbounded wait (`MatrixRunner.kt:85-98`) | `stream(cmd, listener, INSTALL, colored = true)` |
| Terminal | `LuaTerminalEnvironmentService.getToolDirectories()` | `pathPrependDirs()` (done in this feature) |
| Probe/validator engines | `LuaProcessUtil.capture(cmd, 10_000)` (`tool/LuaToolValidator.kt:143`) | `capture(cmd, PROBE)` (TOOLING-01's probe engine) |
| Provisioner | `capture(…, 600_000)` (`rocks/env/HererocksProvisioner.kt:66,97`) | `capture(cmd, INSTALL, indicator)` (TOOLING-04) |

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| TOOLING-03-01 | M | §2.3, §3.1 |
| TOOLING-03-02 | M | §2.3, §3.2 |
| TOOLING-03-03 | M | §2.1 (+ requirements mapping table) |
| TOOLING-03-04 | M | §2.2, §3.1 step 6, §3.2 |
| TOOLING-03-05 | M | §2.3 threading, §3.1 steps 1–2, §3.2 step 1 |
| TOOLING-03-06 | M | §3.1 steps 4–6, §3.2 step 4 |
| TOOLING-03-07 | M | §2.5, §3.3 |
| TOOLING-03-08 | M | §2.5, §3.4 steps 2–5 |
| TOOLING-03-09 | M | §2.5, §3.4 step 6 |
| TOOLING-03-10 | M | §2.4, §3.5 |
| TOOLING-03-11 | M | §2.5 cache field, §3.7 |
| TOOLING-03-12 | M | §2.7, §7.1 |
| TOOLING-03-13 | M | §2.6, §3.6 |
| TOOLING-03-14 | M | §3.4 step 1 |
| TOOLING-03-15 | S | §7.2 |
| TOOLING-03-16 | M | §2.4, §3.4 step 7, §3.5 step 4 |
| TOOLING-03-17 | M | §2.3 (capture signature), §3.1 step 1b |

## 9. Alternatives Considered

- **Keep −1/−2 sentinel exit codes** (as `LuaProcessUtil` today) — rejected: collides
  with legitimate negative/255 exit codes and forces every caller into magic-number
  comparisons (`StyluaFormattingTask.kt:88`); an explicit outcome enum is
  self-documenting and type-safe.
- **Hard EDT assert (`assertBackgroundThread`)** — rejected for v1: legacy callers still
  reach `capture` from the EDT during the TOOLING-05 transition (e.g. settings-panel
  probes); the soft assert fails tests without breaking production, and TOOLING-05
  tightens callers.
- **Cache the full `LuaLaunchEnvironment`** — rejected: LUA_PATH depends on rockspec PSI;
  `RockspecSourcePathProvider` already owns that caching under `PsiModificationTracker`
  (`rocks/RockspecSourcePathProvider.kt:21-46`); caching it again here would need PSI
  tracking in a service that must stay PSI-free for the terminal thread contract.
- **Set-if-absent semantics for LUA_PATH (user env wins)** — deferred: today the computed
  union overwrites a user-supplied LUA_PATH env var (`run/LuaRunConfiguration.kt:257` vs
  `:272-283`); changing precedence is a user-visible behavior change belonging to a
  follow-up decision, not this refactor. `applyTo` keeps last-write-wins.
- **Unbounded stream mode for matrix rows** — rejected: today's unbounded `waitFor()`
  (`MatrixRunner.kt:97`) can hang a row forever; `INSTALL` (600 s) is a generous, safe
  ceiling consistent with provisioning.
- **`RunConfigurationExtension`-based env injection** — rejected previously and still:
  the interface lives in the Java `execution-impl` module, not on this plugin's platform
  classpath (documented at `tool/LuaToolEnvironment.kt:16-20`); explicit `applyTo` at
  command-line construction stays.

## 10. Open Questions

_None — feature has cleared the planning bar._
