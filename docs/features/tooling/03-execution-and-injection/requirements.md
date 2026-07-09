---
id: "TOOLING-03"
title: "03: Execution & Environment Injection"
type: "feature"
status: "done"
vf_icon: ✅
priority: "high"
parent_id: "TOOLING"
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# TOOLING-03: Execution & Environment Injection

## Overview

One subprocess entry point and one environment-injection mechanism for every external Lua
binary Lunar launches. `toolchain.exec.LuaToolExecutionService` replaces the three process
idioms in the codebase today (`util/LuaProcessUtil`, raw `CapturingProcessHandler` in the
Stylua task, `ProcessHandlerFactory` in the matrix runner) with capture/stream modes,
named timeout classes, an unambiguous outcome model, cancellation, and an EDT guard.
`toolchain.exec.LuaExecutionEnvironmentBuilder` computes the `{PATH prepend dirs, LUA_PATH,
LUA_CPATH}` triple for a project exactly once — replacing the three divergent per-consumer
assemblies — with a project-scoped cache invalidated by the TOOLING-02
`LuaToolchainListener` topic. Parent epic: [[features/tooling/requirements|TOOLING]];
binding contract: [tooling-architecture.md](../tooling-architecture.md) §5, §10.

## Scope

### In Scope

- `toolchain.exec.LuaToolExecutionService` (APP service): `capture` and `stream` modes,
  timeout classes, outcome model, `ProgressIndicator` cancellation, EDT guard, the
  read-lock pooled-thread escape carried over from `LuaProcessUtil.capture`.
- `toolchain.exec.LuaExecutionEnvironmentBuilder` (PROJECT service) + the
  `LuaLaunchEnvironment` value it produces, including the exact PATH-prepend /
  LUA_PATH / LUA_CPATH assembly and application algorithms.
- Project-scoped cache of the resolver-derived PATH-prepend directories, invalidated by
  `LuaToolchainListener.TOPIC` (fixes the `registerTool`-fires-no-event staleness bug).
- `toolchain.exec.LuaInterpreterCommandLines` — the replacement for
  `command/LuaCommandLine.kt` (`newLuaInterpreterCommandLine` /
  `newProjectLuaInterpreterCommandLine`), keeping the `.jar → java -cp` special case.
- `toolchain.terminal.LuaShellExecOptionsCustomizer` — the existing terminal customizer
  moved from `tool/terminal/`, behavior unchanged, sourcing directories from the builder;
  `META-INF/lunar-terminal.xml` re-pointed to the new class.
- The documented target call pattern for every downstream consumer (run configs, test
  state, console, luacheck annotator, stylua, matrix runner, terminal) that TOOLING-05
  executes.

### Out of Scope

- **The resolver itself** — `LuaToolResolver`, binding precedence, environments, and the
  `LuaToolchainListener` topic are TOOLING-02 deliverables; this feature consumes them
  as specified in [tooling-architecture.md](../tooling-architecture.md) §3–§4.
- **Consumer file-by-file cutover** — TOOLING-05. This design defines the target API each
  consumer calls; it does not edit `LuaRunConfiguration`, `LuaRocksRunConfiguration`,
  `LuaTestCommandLineState`, `LuaConsoleRunner`, `StyluaFormattingTask`,
  `LuaCheckInvoker`, or `MatrixRunner` (except the terminal customizer move, which is
  a 1:1 relocation owned here).
- **Provisioning** — TOOLING-04 (it will *use* `capture(…, INSTALL)`).
- **Settings UI** — TOOLING-06.
- Deleting `util/LuaProcessUtil.kt`, `tool/LuaToolEnvironment.kt`,
  `tool/LuaTerminalEnvironmentService.kt`, `command/LuaCommandLine.kt` — deletion happens
  in TOOLING-05 once the last legacy caller is migrated.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TOOLING-03-01 | **Capture-mode execution** | M | `capture(cmd, timeout, indicator)` runs a process to completion and returns a `LuaExecResult` (stdout/stderr/exit code + outcome). Only subprocess capture path in the plugin post-TOOLING-05. |
| TOOLING-03-02 | **Stream-mode execution** | M | `stream(cmd, listener, timeout, colored, indicator)` feeds process output to a `ProcessListener` and returns a `LuaExecResult`; destroys the process on timeout (fixes the `LuaProcessUtil.listen` leak). |
| TOOLING-03-03 | **Timeout classes** | M | Five named classes: `PROBE`=10 s, `COMMAND`=15 s (default), `FORMAT`=30 s, `NETWORK`=120 s, `INSTALL`=600 s. No ad-hoc millisecond values in production callers. |
| TOOLING-03-04 | **Outcome model** | M | `LuaExecOutcome` ∈ {COMPLETED, TIMED_OUT, START_FAILED, CANCELLED} replaces the −1/−2 sentinel exit codes of `LuaProcessUtil`; callers branch on outcome, never on magic exit codes. |
| TOOLING-03-05 | **Threading guard** | M | `capture`/`stream` soft-assert a background thread; `capture` keeps the read-lock pooled-thread escape from `LuaProcessUtil.capture`. |
| TOOLING-03-06 | **Cancellation** | M | An explicit or ambient `ProgressIndicator` cancels a running `capture`/`stream`; the process is destroyed and the outcome is `CANCELLED`. |
| TOOLING-03-07 | **PATH prepend dirs** | M | The builder computes the deduplicated, order-stable list of parent directories of every kind's resolved tool (runtime kinds included) via `LuaToolResolver`. |
| TOOLING-03-08 | **LUA_PATH assembly** | M | LUA_PATH = rockspec-derived prefix + expanded project source path, `;`-normalized with a trailing `;;`; `null` (unset) when both parts are empty. |
| TOOLING-03-09 | **LUA_CPATH assembly** | M | LUA_CPATH taken from `RockspecRunPathProvider.luaCPath(project)`; `null` when no built-in C modules exist. |
| TOOLING-03-10 | **Application semantics** | M | `LuaLaunchEnvironment.applyTo(cmd)` prepends PATH dirs ahead of the existing PATH (override → inherited → empty, `File.pathSeparator`-joined, existing entries preserved) and sets LUA_PATH/LUA_CPATH only when computed non-null. |
| TOOLING-03-11 | **Cache + topic invalidation** | M | The PATH-prepend dir list is cached per project and invalidated by `LuaToolchainListener.TOPIC`; fixes today's stale `LuaTerminalEnvironmentService` cache (`registerTool` fires no event). |
| TOOLING-03-12 | **Terminal customizer** | M | `toolchain.terminal.LuaShellExecOptionsCustomizer` keeps the `prependEntryToPATH`-in-reverse mechanics and background-thread contract, sourcing dirs from the builder; `lunar-terminal.xml` points at the new FQN. |
| TOOLING-03-13 | **Interpreter command lines** | M | `LuaInterpreterCommandLines.forBinary(path)` / `forProject(project)` replace `newLuaInterpreterCommandLine` / `newProjectLuaInterpreterCommandLine`; the `.jar → java -cp <jar> lua` special case is kept; the project interpreter comes from the TOOLING-02 resolver (RUNTIME capability). |
| TOOLING-03-14 | **Per-run source-path override** | M | `build(sourcePathOverride)` with a non-empty override yields `luaPath = override` verbatim and `luaCPath = null`, matching today's run-config semantics. |
| TOOLING-03-15 | **Consumer target API documented** | S | Design §7.2 states the exact call each TOOLING-05 consumer makes, including the LuaRocks run-config PATH-prepend fix. |
| TOOLING-03-16 | **LUAROCKS_CONFIG injection** | M | When the project's active environment root contains `luarocks-config.lua`, `build()` sets `LUAROCKS_CONFIG=<rootDir>/luarocks-config.lua` (contract §6); absent when there is no such file; a user-set `LUAROCKS_CONFIG` is not overwritten. |
| TOOLING-03-17 | **Stdin capture** | M | `capture(cmd, timeout, stdin)` writes a non-null `stdin` string to the process input stream (UTF-8) and closes it before waiting; enables the stylua formatter (document text piped via `--stdin-filepath`) to migrate off its raw `CapturingProcessHandler`. |

## Detailed Specifications

### TOOLING-03-01/02: Execution modes

Capture wraps `CapturingProcessHandler` (constructed inside the try, since construction
launches the process and throws `ExecutionException` for an unresolvable command — the
behavior `util/LuaProcessUtil.kt:27-38` established). Stream wraps `OSProcessHandler` (or
`ProcessHandlerFactory.getInstance().createColoredProcessHandler` when `colored = true`,
absorbing `MatrixRunner.processRunner`, `rocks/env/matrix/MatrixRunner.kt:85-98`). Both
return `LuaExecResult`; stream's `ProcessOutput` carries the exit code with empty
stdout/stderr (the listener consumed the text).

### TOOLING-03-03: Timeout classes — legacy mapping

Every timeout constant in the codebase today maps to a class (TOOLING-05 uses this table):

| Class | ms | Legacy call sites absorbed |
|-------|----|---------------------------|
| `PROBE` | 10 000 | `LuaToolValidator.VERSION_TIMEOUT_MS` = 10 000 (`tool/LuaToolValidator.kt:22`); `HererocksLocator.PROBE_TIMEOUT_MS` = 10 000 (`rocks/env/HererocksLocator.kt:20`); `RockspecBridge.TIMEOUT_MS` = 10 000 (`rocks/RockspecBridge.kt:30`); interpreter banner probe, today 5 s default (`platform/LuaInterpreterService.kt:89`) |
| `COMMAND` | 15 000 | `LuaRocksSearchService.SEARCH_TIMEOUT_MS` = 15 000 (`rocks/browser/LuaRocksSearchService.kt:34`); `LuaRocksMetadataService.SHOW_TIMEOUT_MS` = 15 000 (`rocks/browser/LuaRocksMetadataService.kt:23`); luacheck run, today 5 s default (`analysis/luacheck/LuaCheckInvoker.kt:24`); supersedes `LuaProcessUtil.STANDARD_TIMEOUT` = 5 000 (`util/LuaProcessUtil.kt:12`) |
| `FORMAT` | 30 000 | `StyluaFormattingTask.timeoutMs` = 30 000 (`lang/formatting/external/StyluaFormattingTask.kt:121`) |
| `NETWORK` | 120 000 | `LuaRocksActionHandler.INSTALL_TIMEOUT_MS` = 120 000 (`rocks/browser/LuaRocksActionHandler.kt:23`); `PublishRockAction.UPLOAD_TIMEOUT_MS` = 120 000 (`rocks/publish/PublishRockAction.kt:86`) |
| `INSTALL` | 600 000 | `HererocksProvisioner.PROVISION_TIMEOUT_MS` = 600 000 (`rocks/env/HererocksProvisioner.kt:97`); matrix rows, today unbounded `waitFor()` (`rocks/env/matrix/MatrixRunner.kt:97`) — 600 s cap is a deliberate improvement |

### TOOLING-03-04: Outcome model

`LuaProcessUtil` encodes failure into fake exit codes (−1 timeout, −2 start failure,
`util/LuaProcessUtil.kt:13-14`) that callers then compare against
(`StyluaFormattingTask.kt:88`, `LuaToolValidator.kt:144-151`). This is **replaced, not
kept**: real processes can legitimately exit −1/255, so sentinels are ambiguous. The
result carries an explicit `outcome` enum; the sentinel constants die with
`LuaProcessUtil` in TOOLING-05.

### TOOLING-03-07: PATH prepend dirs — ordering

Kinds are enumerated in `LuaToolKindRegistry` declaration order (the TOOLING-01 built-in
kind list — deterministic, mirroring today's `LuaToolType.entries` iteration at
`tool/LuaToolManager.kt:184-185`); each kind resolves through
`LuaToolResolver.resolve(project, kind.id)`; the resolved binary's parent directory is
taken; duplicates are removed keeping the **first** occurrence. RUNTIME kinds are
included — an improvement over today, putting the project's `lua` on the terminal PATH.

### TOOLING-03-08: LUA_PATH assembly

Grounded in the current run-config fallback branch (`run/LuaRunConfiguration.kt:276-282`,
duplicated at `run/test/LuaTestCommandLineState.kt:137-144`):

```
prefix      = RockspecRunPathProvider.luaPathPrefix(project)        // "…?;…;" or ""
projectPath = LuaProjectSettings.getInstance(project).state.expandSourcePath(project)
union       = (prefix + projectPath).trimEnd(';') + ";;"
luaPath     = if (union == ";;") null else union
```

The trailing `;;` keeps Lua's default search paths appended. The console path today sets
only `expandSourcePath` with no prefix and no `;;` (`command/LuaCommandLine.kt:22-25`);
it unifies onto this formula (a strict superset — documented behavior change).

## Behavior Rules

- **Env application order**: consumers apply user-specified run-config environment
  variables first, then `applyTo` — so the computed LUA_PATH/LUA_CPATH win, matching
  today's ordering in `LuaRunConfiguration.startProcess` (`:257` then `:272-283`), and
  PATH is merged (prepended), never clobbered.
- **No parsing of tool output** happens in this feature; the service returns raw
  stdout/stderr and consumers keep their own parsers.
- **DBGp debug variables** (`LUA_INIT`, `LUNAR_LUA_PATH_TEMPLATE`,
  `LUNAR_DEBUGGER_PACKAGE`, `run/LuaRunConfiguration.kt:260-270`) remain caller-owned;
  the builder never touches them.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | TOOLING-03-01 | `GeneralCommandLine("/bin/sh","-c","echo out; echo err 1>&2; exit 3")` | `capture(cmd)` on a pooled thread | `outcome == COMPLETED`, `exitCode == 3`, `stdout == "out\n"`, `stderr == "err\n"`, `isSuccess == false` |
| 2 | TOOLING-03-01 | same command exiting 0 | `capture(cmd)` | `outcome == COMPLETED`, `isSuccess == true` |
| 3 | TOOLING-03-04 | `GeneralCommandLine("/nonexistent/binary-xyz")` | `capture(cmd)` | `outcome == START_FAILED`; no exception propagates; `isSuccess == false` |
| 4 | TOOLING-03-03 | — | read `LuaExecTimeout.*.millis` | `PROBE=10_000, COMMAND=15_000, FORMAT=30_000, NETWORK=120_000, INSTALL=600_000` |
| 5 | TOOLING-03-01/03 | `sh -c "sleep 5"` | `@TestOnly captureWithMillis(cmd, 200)` | returns in ≪5 s with `outcome == TIMED_OUT`; process destroyed (no orphan) |
| 6 | TOOLING-03-06 | `sh -c "sleep 5"`, an `EmptyProgressIndicator` cancelled from another thread after ~200 ms | `capture(cmd, COMMAND, indicator)` | returns promptly with `outcome == CANCELLED` (`output.isCancelled == true`) |
| 7 | TOOLING-03-05 | unit test on the EDT | `capture(cmd)` | `ThreadingAssertions.softAssertBackgroundThread` error is logged (assert via `LoggedErrorProcessor`) |
| 8 | TOOLING-03-02 | `sh -c "printf a; printf b 1>&2; exit 2"` + recording listener | `stream(cmd, listener)` | `outcome == COMPLETED`, `exitCode == 2`, listener received both `a` and `b` |
| 9 | TOOLING-03-02 | `sh -c "sleep 5"` | `@TestOnly streamWithMillis(cmd, listener, 200)` | `outcome == TIMED_OUT` and the process has been destroyed |
| 10 | TOOLING-03-07 | resolver stub: kind `lua` → `/usr/bin/lua`, `luacheck` → `/usr/bin/luacheck`, `stylua` → `/opt/x/stylua`; kind declaration order `lua, …, luacheck, stylua` | `pathPrependDirs()` | `[/usr/bin, /opt/x]` — deduped, first-occurrence order kept |
| 11 | TOOLING-03-08 | rockspec prefix `"/p/lib/?.lua;"`, source path expanding to `"/p/src/?.lua"` | `build().luaPath` | `"/p/lib/?.lua;/p/src/?.lua;;"` |
| 12 | TOOLING-03-08 | prefix `""`, source path `""` | `build().luaPath` | `null`; `applyTo` leaves LUA_PATH untouched |
| 13 | TOOLING-03-09 | project with a builtin-C rockspec, tree root `/p/lua_modules`, language level 5.4 | `build().luaCPath` | `"/p/lua_modules/lib/lua/5.4/?.so;;"` |
| 14 | TOOLING-03-10 | cmd with `environment["PATH"] = "A:B"` (POSIX), dirs `[/opt/x, /usr/bin]` | `applyTo(cmd)` | `environment["PATH"] == "/opt/x:/usr/bin:A:B"` |
| 15 | TOOLING-03-10 | cmd with no PATH override, dirs `[/opt/x]` | `applyTo(cmd)` | `environment["PATH"] == "/opt/x" + File.pathSeparator + System.getenv("PATH")` |
| 16 | TOOLING-03-11 | `pathPrependDirs()` computed once; then a new tool registered in the TOOLING-02 registry (which fires `LuaToolchainListener.TOPIC`) | `pathPrependDirs()` again | second call recomputes and contains the new tool's dir (no stale cache — the defect of `LuaToolManager.registerTool`, `tool/LuaToolManager.kt:49-90`, which fires no event) |
| 17 | TOOLING-03-12 | builder dirs `[d1, d2]`, recording fake `MutableShellExecOptions` | `customizeExecOptions(project, options)` | `prependEntryToPATH` called with `d2` then `d1`, so final PATH order is `d1, d2, …` |
| 18 | TOOLING-03-13 | `forBinary(Path.of("/env/bin/lua"))` | build | `exePath == "/env/bin/lua"`, working dir `/env/bin`, parent env type `CONSOLE` |
| 19 | TOOLING-03-13 | `forBinary(Path.of("/opt/luaj.jar"))` | build | `exePath == "java"`, parameters `["-cp", "/opt/luaj.jar", "lua"]` |
| 20 | TOOLING-03-14 | `build(sourcePathOverride = "/x/?.lua")` in a project that *also* has rockspec-derived paths | read env | `luaPath == "/x/?.lua"` exactly (no prefix, no `;;`), `luaCPath == null` |
| 21 | TOOLING-03-16 | Active env rooted at `/env` containing `/env/luarocks-config.lua` | `build()` then `applyTo(cmd)` | `cmd.environment["LUAROCKS_CONFIG"] == "/env/luarocks-config.lua"` |
| 22 | TOOLING-03-16 | No active environment (or root without `luarocks-config.lua`) | `build()` then `applyTo(cmd)` | `"LUAROCKS_CONFIG" !in cmd.environment` |
| 23 | TOOLING-03-17 | A command echoing stdin (e.g. `cat`), `stdin = "return 1\n"` | `capture(cmd, COMMAND, stdin = "return 1\n")` | `result.output.stdout == "return 1\n"`, outcome `COMPLETED` |

## Acceptance Criteria

- [ ] TOOLING-03-01…06, 17: `LuaToolExecutionService` passes TCs 1–9, 23 (incl. stdin) and
      is the only new subprocess API (legacy paths untouched until TOOLING-05).
- [ ] TOOLING-03-07…11, 14, 16: `LuaExecutionEnvironmentBuilder` passes TCs 10–16, 20–22.
- [ ] TOOLING-03-12: terminal PATH injection works live (VNC: `which stylua` in the IDE
      terminal resolves to the bound tool) with the customizer under `toolchain.terminal`.
- [ ] TOOLING-03-13: `LuaInterpreterCommandLines` passes TCs 18–19.
- [ ] Full unit suite stays green; `ktlintFormat ktlintCheck` clean on new files.

## Non-Functional Requirements

- **Threading**: `capture`/`stream` never run on the EDT (soft assert); the builder's
  `pathPrependDirs()` is safe on any thread with no PSI/VFS access (terminal contract);
  `build()` may touch PSI only through `RockspecSourcePathProvider`, which already
  handles EDT/read-action safety internally (`rocks/RockspecSourcePathProvider.kt:22-46`).
- **Memory**: the APP-level execution service holds no `Project` refs (contract §10);
  the PROJECT-level builder holds only its own project (platform-managed lifetime).
- **Performance**: `pathPrependDirs()` after the first call is a volatile field read;
  registry reads are cheap and thread-safe per contract §10.

## Dependencies

- **TOOLING-02** (`LuaToolResolver`, `LuaToolchainListener.TOPIC`, kind registry from
  TOOLING-01) — names fixed by [tooling-architecture.md](../tooling-architecture.md)
  §2–§4; this feature cannot land before them.
- Retained collaborators: `rocks/RockspecRunPathProvider.kt` (`luaPathPrefix:8-11`,
  `luaCPath:14-22`), `settings/LuaProjectSettings.kt` (`expandSourcePath:132-134`).
- **TOOLING-05** consumes the API defined here; **TOOLING-04** uses `capture(…, INSTALL)`.

## See Also

- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Epic risks: [../00-de-risking (TOOLING-00)](../requirements.md)
