---
id: TOOLING-05-DESIGN
title: "Technical Design"
type: design
priority: "high"
parent_id: TOOLING-05
folders:
  - "[[features/tooling/05-consumer-migration/requirements|requirements]]"
---

# Technical Design: TOOLING-05 — Consumer Migration & Legacy Removal

All names/packages follow the binding
[architecture contract](../tooling-architecture.md). Sibling designs (01–04, 06, 07) did
not exist when this was written; every new-API signature used here is either quoted from
the contract or listed in §9.1 as a **cross-check item** the reviewer must reconcile
against the sibling designs once they exist. Every *existing* symbol cited below was
verified by grep on 2026-07-05 (`file:line` against `src/main/kotlin/net/internetisalie/lunar/`
unless stated otherwise).

## 1. Architecture Overview

### Current State — four resolution patterns

1. `LuaToolManager.getEffectiveTool(project, LuaToolType)` (`tool/LuaToolManager.kt:163`)
   — stylua (`lang/formatting/external/StyluaFormattingService.kt:19,34`), busted
   (`run/test/LuaTestCommandLineState.kt:61`), luacov
   (`coverage/LuaCoverageProgramRunner.kt:29`), and the luarocks facade *first choice*
   (`rocks/LuaRocksEnvironment.kt:51-55`).
2. Dedicated settings services bypassing the registry — luacheck
   (`analysis/luacheck/LuaCheckCommandLine.kt:17,22` reading
   `LuaCheckSettings.state.executablePath`, default `/usr/local/bin/luacheck`,
   `LuaCheckSettings.kt:15`) and luarocks
   (`rocks/run/LuaRocksRunConfiguration.kt:208`,
   `rocks/browser/LuaRocksActionHandler.kt:33,57`,
   `rocks/browser/LuaRocksMetadataService.kt:30`,
   `rocks/build/WorkspaceBuildRunner.kt:29`, facade fallback
   `rocks/LuaRocksEnvironment.kt:57`, default `"luarocks"`,
   `rocks/run/LuaRocksSettings.kt:30,43`).
3. The parallel interpreter subsystem — inventory `LuaApplicationSettings.State.interpreters`
   (`settings/LuaApplicationSettings.kt:39`), project selection
   `LuaProjectSettings.State.interpreter` (`settings/LuaProjectSettings.kt:53`) with the
   ROCKS-16 mode machine (`:62-78`, `:306-359`, enum `:409`), discovery/probing in
   `platform/LuaInterpreterService.kt:21,79`, command-line building in
   `command/LuaCommandLine.kt:32`, combo UI in `platform/LuaInterpreterComponent.kt:18`.
4. The hererocks lifecycle — `rocks/env/HererocksEnvState.kt:19` (exe derivation
   `:27-34`), `HererocksEnvSet.kt:10-17`, `HererocksEnvBinder.kt:42-54`, provision/
   locate/detect (`HererocksProvisioner.kt`, `HererocksLocator.kt`,
   `HererocksEnvDetector.kt`, `HererocksDetectStartup.kt:17`), consumed by the matrix
   runner (`rocks/env/matrix/MatrixRunner.kt:47-48`, `RunMatrixAction.kt:30,36`) and the
   status-bar widget (`rocks/env/LuaEnvStatusBarWidget.kt:37,63-64,92`).

### Prior Art in This Repo

This feature *is* the replacement pass, so prior art is the entire legacy surface above;
each piece is explicitly **replaced** (§6) or **rewired** (§2). Searched: `getEffectiveTool`,
`executablePath`, `newLuaInterpreterCommandLine`, `resolveInterpreter`,
`customizeLuaInterpreterComboBox`, `HererocksEnvSet`, `LuaTerminalEnvironmentService`,
`LuaProcessUtil`, `state.interpreter` — the consumer list in §2 is the closed set of
hits. Three consumers are **not** in contract §9 and were found by grep:
`LuaRocksActionHandler.kt:33,57`, `LuaRocksMetadataService.kt:30`,
`WorkspaceBuildRunner.kt:29` (all direct `LuaRocksSettings.executablePath` reads), plus
the `rocks/init` wizard (§2.8) — see §9.2 proposed contract deviations.

### Target State

Every consumer resolves through `toolchain.resolve.LuaToolResolver` (one precedence,
contract §3), executes through `toolchain.exec` (contract §5), and the legacy packages
are deleted (contract §1). No migration shims: old persisted tags are ignored on load
(§3.7).

## 2. Core Components (per-consumer migration specs)

New-API calls reference: `LuaToolResolver.resolve(project: Project?, kindId: String):
LuaRegisteredTool?` (contract §3), `LuaRegisteredTool.isUsable` (contract §2.3),
`LuaExecutionEnvironmentBuilder.forProject(project)` → `{PATH prepend dirs, LUA_PATH,
LUA_CPATH}` (contract §5), `LuaToolExecutionService` with timeout classes PROBE/FORMAT/
INSTALL (contract §5). Signatures this design *additionally requires* are defined in
§9.1 and must be cross-checked.

### 2.1 Luacheck — `analysis/luacheck/`
- **Responsibility**: lint resolution + invocation via the unified stack.
- **Threading**: resolution EDT-safe; process runs on the annotator's background pass
  (unchanged — `LuaCheckAnnotator`/`LuaCheckInspection` registrations at
  `plugin.xml:255,266` stay).
- **Changes**:
  - `LuaCheckCommandLine.kt:16-22` — replace the `LuaCheckSettings` read with:
    ```kotlin
    val tool = LuaToolResolver.getInstance().resolve(project, "luacheck") ?: return null
    val cmd = GeneralCommandLine(tool.path).withWorkDirectory(workDirectory.path)
    ```
    (`isUsable` filtering is inside the resolver per contract §3; `null` → return `null`,
    matching today's empty-path behavior at `:18-20`.)
  - `LuaCheckCommandLine.kt:26-30` — `settings.state.arguments` → the TOOLING-02
    kind-scoped luacheck-arguments option read via
    `LuaToolchainProjectSettings.getInstance(project).kindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS)`
    (contract §10.5/§10.8; project-scoped, falling back to the app default
    `LuaToolchainRegistry.getInstance().kindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS)`
    per contract §7). Target-`--std` logic (`:33-38`) unchanged.
  - `LuaCheckInvoker.kt:24` — `LuaProcessUtil.listen(cmd, listener)` →
    `LuaToolExecutionService.capture(cmd, LuaExecTimeout.FORMAT)`; parse
    `output.stdout` line-by-line with the existing regex
    (`LuaCheckInvoker.kt:35`: `(.+?):(\d+):(\d+)-(\d+):(.+)` — drop the trailing `\n`,
    apply per line via `lineSequence()`); the ANSI-strip at `:45` and the
    `Problem` mapping (`:48-59`) are unchanged. Non-zero exit or exception → empty list
    (today's behavior, `:25`).
  - `LuaCheckSettings.kt` — **deleted** (whole service; `executablePath` default
    `/usr/local/bin/luacheck` at `:15` dies with it).
  - `LuaCheckSettingsPanel.kt` — interim slimming (TOOLING-06 owns the page): delete the
    executable row (`:18-23`) and the dead download link (`:24-26`); rebind the
    arguments row (`:27-32`) to the TOOLING-02 option accessor. Page removal is 06's.

### 2.2 Stylua — `lang/formatting/external/`
- **Changes**:
  - `StyluaFormattingService.kt:19,21` and `:34-35` —
    `LuaToolManager.getInstance().getEffectiveTool(project, LuaToolType.STYLUA)` + `tool.isValid`
    → `LuaToolResolver.getInstance().resolve(project, "stylua")` (resolver already
    returns only usable tools; drop the separate validity check).
  - `StyluaFormattingTask.kt:33,38-56` — replace the raw `CapturingProcessHandler`
    (+ manual stdin writer `:66-79`) with one exec-service call. Per contract §10.6 the
    command line carries executable/args/workdir and the timeout is a separate arg:
    ```kotlin
    val cmd = GeneralCommandLine(config.styluaPath, "--stdin-filepath", config.fileName)
        .withWorkDirectory(config.workingDirectory)
    LuaToolExecutionService.getInstance()
        .capture(cmd, LuaExecTimeout.FORMAT, stdin = request.documentText)
    ```
    The document text is fed on **stdin** via the `stdin` parameter of `capture`
    (contract §10.6 / TOOLING-03-17 — the exec service writes it to the process input
    stream and closes it), replacing stylua's manual stdin writer. Output handling
    (`handleProcessOutput`), error notifications,
    and `StyluaExecutionConfig` (`:19-23`) stay; the `@Volatile processHandler` cancel
    hook is replaced by the exec service's cancellation (contract §5).

### 2.3 Busted & LuaCov — `run/test/`, `coverage/`
- **Changes**:
  - `LuaTestCommandLineState.kt:61-64` — busted via
    `resolve(targetProject, "busted")`; `null` → `ExecutionException("Busted is not
    configured. Register/bind it under Settings → Lua → Toolchain (or install via
    LuaRocks).")`.
  - `LuaTestCommandLineState.kt:55` — `LuaToolEnvironment.prependToolDirsToPath(...)` →
    `LuaExecutionEnvironmentBuilder.forProject(targetProject).applyTo(commandLine)`.
  - `LuaTestCommandLineState.kt:133-145` (`configureLuaPath`) — the project-default
    LUA_PATH/LUA_CPATH union (`RockspecRunPathProvider` prefix + expanded source path)
    moves into the env builder (TOOLING-03 absorbs it; cross-check §9.1-d). The
    run-config override precedence stays local: `config.sourcePath` non-empty → set
    `LUA_PATH` to it verbatim, skip the builder's LUA_PATH (PATH prepend still applies).
  - `LuaCoverageProgramRunner.kt:29-30` — `resolve(project, "luacov")`; the
    install-hint notification block (`:31-44`) is kept as the null/unusable branch
    (its `LuaRocksActionHandler.install` call is itself migrated per §2.4).
  - Lunity branch `LuaTestCommandLineState.kt:109-110` migrates with the runtime
    consumers (§2.5).

### 2.4 LuaRocks — `rocks/`
- **`LuaRocksEnvironment` is KEPT** (same object, `rocks/LuaRocksEnvironment.kt`) as the
  luarocks-kind facade; this is `withServer`'s new home — unchanged at `:68-69`.
  - `resolveExecutable(project)` (`:49-58`) is rewritten to return `String?`:
    ```kotlin
    fun resolveExecutable(project: Project?): String? =
        LuaToolResolver.getInstance().resolve(project, "luarocks")?.path
    ```
    The `LuaRocksSettings.executablePath` fallback (`:57`) and the `"luarocks"` default
    are deleted (contract §3 step 5: no hardcoded defaults).
  - `resolveServer(project)` (`:33-40`): project override
    `LuaProjectSettings.State.rocksServerUrl` (kept field,
    `settings/LuaProjectSettings.kt:109`) → app default now read via
    `LuaToolchainRegistry.getInstance().kindOption(LuaKindOptionKeys.LUAROCKS_SERVER_URL)`
    (contract §10.1/§10.8; replaces `LuaRocksSettings.serverUrl`,
    `rocks/run/LuaRocksSettings.kt:31`) → `null`.
- **Call-site cutovers** (all get the shared null-handling of §3.3):
  - `rocks/run/LuaRocksRunConfiguration.kt:208` —
    `LuaRocksSettings.getInstance().executablePath` →
    `LuaRocksEnvironment.resolveExecutable(project) ?: throw ExecutionException(...)`;
    after `buildCommandLine(...)` (`:209`), add
    `LuaExecutionEnvironmentBuilder.forProject(project).applyTo(commandLine)` — this
    fixes the missing PATH prepend called out in contract §9. `buildCommandLine`
    (`:181-199`) itself is unchanged (still unit-testable with an explicit path).
  - `rocks/browser/LuaRocksActionHandler.kt:33,57` — direct settings reads (bypassing
    even the ROCKS-06 facade) → `LuaRocksEnvironment.resolveExecutable(project)`; `null`
    → error notification (existing `notify(...)` helper, `:82-87`) + `onDone(false)`.
    `LuaProcessUtil.capture(…, INSTALL_TIMEOUT_MS)` (`:38,58`) →
    `LuaToolExecutionService.capture(…, LuaExecTimeout.INSTALL)`.
  - `rocks/browser/LuaRocksMetadataService.kt:30` — same facade cutover.
  - `rocks/build/WorkspaceBuildRunner.kt:29` — same facade cutover.
  - `rocks/browser/LuaRocksSearchService.kt:48,74` and
    `rocks/publish/PublishRockAction.kt:63` already call the facade — only add the
    `null` branch (skip launch + configure-hint notification).
    `rocks/publish/RockUploadCommand.kt:31` (`withServer` args-only) unchanged.
- `rocks/run/LuaRocksSettings.kt` — **deleted**. `LuaRocksSettingsConfigurable`
  (`rocks/run/LuaRocksSettingsConfigurable.kt:26` binds `settings::executablePath`) is
  interim-slimmed like §2.1: executable row deleted, server row rebound to the
  TOOLING-02 option; page deletion is 06's.

### 2.5 Lua runtime consumers — `run/`, `command/`, `platform/`
The project “interpreter” becomes the resolved RUNTIME tool (contract §7).

- **`toolchain.ui.LuaRuntimeComboBox`** (new, created by this feature):
  `fun customize(project: Project, field: ComboBox<LuaRegisteredTool>)` — replaces
  `customizeLuaInterpreterComboBox` (`platform/LuaInterpreterComponent.kt:18-74`) with
  identical semantics, grounded in the ROCKS-16 follow-up (commits a47161e6, 29d1b636,
  b6c1a193). Model-building algorithm: §3.1. Renderer: port of
  `LuaInterpreterListCellRenderer` (`LuaInterpreterComponent.kt:76-110`) rendering
  `tool.path` bold + `runtime.product runtime.version` gray (unusable → “Invalid” +
  error icon; never-probed → “Unknown”).
- **`run/LuaRunConfiguration.kt`**:
  - Options: the stored `interpreter` **string path option is kept**
    (`LuaRunConfigurationOptions:69-71,100-104`) — run configs live in workspace/`.run`
    files, not `lunar.xml`, so existing configs keep working across the clean break.
  - `interpreter` property (`:169-175`): retyped `LuaRegisteredTool?` — get = §3.2 step 1
    lookup of the stored path; set = stores `tool?.path`.
  - `resolveInterpreter()` (`:187-188`): algorithm §3.2 (explicit path → registry-by-path
    → ad-hoc; else `resolveRuntime(project)`), preserving the documented “unset config
    tracks the project default dynamically” behavior.
  - `getState().startProcess()` (`:238-292`): `newLuaInterpreterCommandLine(interpreter)`
    (`:241`) → the TOOLING-03 runtime command-line factory (jar handling preserved —
    §9.1-b); the LUA_PATH fallback block (`:272-283`) → env builder with the same
    sourcePath-override rule as §2.3; PATH prepend added (today absent here). The
    debugger env block (`:260-270`) is unchanged (contract §9: debugger unchanged).
  - Editor (`:305,318,343,353`): `ComboBox<LuaInterpreter>` → `ComboBox<LuaRegisteredTool>`
    + `LuaRuntimeComboBox.customize`.
- **`run/test/LuaTestRunConfiguration.kt`**: mirror changes — `interpreter` property
  (`:165-174`), `resolveInterpreter()` (`:182-183`), combo at `:278`;
  `LuaTestCommandLineState.buildLunityCommandLine` (`:108-131`) uses the resolved
  runtime + TOOLING-03 factory (the `-lluacov` coverage flag `:119-121` unchanged).
- **`run/test/LuaTestRunConfigurationProducer.kt:53-55,74-76`**: preseeding from
  `LuaProjectSettings.state.interpreter` → `LuaToolResolver.resolveRuntime(project)`
  (skip preseeding when `null` — same as today's null check).
- **`run/console/LuaConsoleRunner.kt:37`**: `newProjectLuaInterpreterCommandLine(project)`
  → resolveRuntime + factory + env builder (parity note: the deleted helper,
  `command/LuaCommandLine.kt:18-30`, did LUA_PATH + tool-dir PATH prepend — both are
  supplied by the builder). `null` → existing `ExecutionException("No project Lua
  interpreter configured")` message updated to name the Toolchain settings.
- **`rocks/RockspecBridge.kt:35-36`**: `state.interpreter?.path … ?: DEFAULT_INTERPRETER`
  → `resolveRuntime(project)?.path`; `null` → `log.warn` + return `null` (the bridge
  already returns `null` on failure, `:44-46`; the bare-`"lua"` fallback is removed per
  no-hardcoded-defaults — PATH-discovered interpreters are auto-registered by
  TOOLING-01 discovery, so the practical behavior is preserved).
- **`command/LuaCommandLine.kt`** — deleted after cutover:
  `newLuaDefaultInterpreterCommandLine` (`:11-16`) has **no callers** (dead — verified);
  `newProjectLuaInterpreterCommandLine` (`:18-30`) → console cutover above;
  `newLuaInterpreterCommandLine` (`:32-45`) → TOOLING-03 factory (the `.jar` →
  `java -cp <jar> lua` branch at `:39-42` must be preserved there — §9.1-b).
- **`platform/LuaInterpreterService.kt`, `platform/LuaInterpreter.kt`,
  `platform/LuaInterpreterComponent.kt`** — **deleted** (decision: delete, not shrink);
  survival map for their data: §3.5.

### 2.6 Matrix runner — `rocks/env/matrix/` → `rocks/matrix/`
- Package moves out of the deleted `rocks/env/`; classes keep their names.
- `MatrixRow.env`/`MatrixRunner.Request.envs`/`RowRunner`
  (`rocks/env/matrix/MatrixRunner.kt:12,40,44`) retype `HererocksEnvState` →
  `toolchain.model.LuaEnvironmentState` (contract §2.4).
- `commandLineFor` (`:47-48`) — `env.luarocksExe()` → per-environment resolver overload
  `LuaToolResolver.resolveIn(env, "luarocks")?.path` (§9.1-a); `null` → the row is not
  spawned: `safeRun` (`:76-82`) returns
  `RowOutcome(-1, "luarocks is not provisioned in ${env.name}")` (row FAIL, others
  unaffected).
- `ProcessHandlerFactory` in `processRunner` (`:85-98`) →
  `LuaToolExecutionService` stream mode (contract §5 absorbs it).
- `RunMatrixAction.kt:30,36` — `HererocksEnvSet.all(project)` → the TOOLING-02
  environment accessor `LuaToolchainProjectSettings.getInstance(project).environments()`
  (contract §10.5, §9.1-f). Row-labeling
  `env.displayLabel()` → `LuaEnvironmentState.name`.
- `MatrixResultsToolWindow` unchanged except package; tool-window id `Lunar.LuaMatrix`
  kept (`plugin.xml:602-603` re-pointed, §7).
- `BatchProvisionAction`/`BatchProvisionDialog` (`rocks/env/matrix/`) — **deleted**;
  multi-version provisioning is TOOLING-04's UI (§9.1-g).

### 2.7 Env status-bar widget — `rocks/env/` → `toolchain.ui`
`LuaEnvStatusBarWidget`/`LuaEnvStatusBarWidgetFactory` are **rewired, not deleted**
(they are consumers of env state); all via
`LuaToolchainProjectSettings.getInstance(project)` (contract §10.5): label from the
TOOLING-02 active environment `.activeEnvironment()`
(replacing `HererocksEnvSet.active`, `LuaEnvStatusBarWidget.kt:37`), popup list from
`.environments()` (`:63-64`), switching via `.activateEnvironment(environmentId)`
(replacing `HererocksEnvSet.switch`, `:92`); refresh subscription moves from
`LuaSettingsChangedListener.TOPIC` to `LuaToolchainListener.TOPIC`. Widget-factory id
`Lunar.LuaEnvWidget` kept (`plugin.xml:600-601` re-pointed).

### 2.8 New Project wizard — `rocks/init/`
- `LuaRocksProjectSettings.kt:20,27-28` — `flavor: HererocksFlavor` → the TOOLING-04
  version-spec type (`kindId` `"lua"|"luajit"` + version string; §9.1-g);
  `provisionHererocks` renamed `provisionEnvironment` (wizard-local state, not
  persisted settings — safe rename).
- `LuaRocksInterpreterInitializer.kt:31-48` — `state.setTarget(HererocksEnvState(…)
  .toTarget())` → build the `Target` directly from the chosen platform/version via
  `PlatformVersionRegistry` (the existing `HererocksEnvState.toTarget` logic,
  `rocks/env/HererocksEnvState.kt:45-56`, is inlined here before that class dies);
  the `InterpreterMode` writes (`:35-40`) are deleted; the explicit-interpreter path
  sets a project RUNTIME **binding** for the chosen registered tool via
  `LuaToolchainProjectSettings.getInstance(project).setBinding("lua", tool.id)`
  (contract §10.5) instead of `state.interpreter`; the provisioning path activates the
  provisioned env via `upsertEnvironmentAndActivate(env)` (contract §10.5, TOOLING-04
  populates the env); `scheduleProvision` queues a TOOLING-04
  `LuaToolProvisioner.provision(project, request)` post-open instead of
  `HererocksProvisioner`.
- `LuaRocksGeneratorPeer.kt:49-51,141` — `ComboBox<LuaInterpreter>` →
  `ComboBox<LuaRegisteredTool>` via `LuaRuntimeComboBox.customize`;
  `interpreterPath` stays a path string.

### 2.9 Terminal — verification only
TOOLING-03 replaces `tool/terminal/LuaShellExecOptionsCustomizer` (registered in
`src/main/resources/META-INF/lunar-terminal.xml`) and
`tool/LuaTerminalEnvironmentService`. This feature's job is the **residual-consumer
gate**: the only current consumers are `tool/LuaToolEnvironment.kt:42` and the
customizer itself (`tool/terminal/LuaShellExecOptionsCustomizer.kt:32`) — both die in
the deletion inventory. Gate: `grep -rn "LuaTerminalEnvironmentService" src/main` = 0
matches after Phase 6.

## 3. Algorithms

### 3.1 Runtime combo-box model (ports `LuaInterpreterComponent.kt:29-73`)
- **Input → Output**: `(project, typed: LuaRegisteredTool?)` → `DefaultComboBoxModel<LuaRegisteredTool>`.
- **Steps**:
  1. `default = LuaToolResolver.resolveRuntime(project)` (may be an active-environment
     tool not globally bound — the ROCKS-16 “managed env must stay listed” case).
  2. Build `LinkedHashMap<String /*path*/, LuaRegisteredTool>`; insert in order:
     `typed` (if non-null), `default` (if non-null), then every registry inventory entry
     whose kind has `Capability.RUNTIME` and `isUsable`, each via `putIfAbsent(path, tool)`.
  3. Model = insertion-ordered values; initial selection = `default`.
  4. `DocumentListener` on the editable editor (port of `:46-73`): text empty →
     `selectedItem = null`; text equals an existing model entry's path → select it;
     otherwise create an **ad-hoc** `LuaRegisteredTool(id = random, kindId = "lua",
     path = text, origin = MANUAL, health = never-probed)`, rebuild the model with it as
     `typed` (step 2 keeps `default` present — the load-bearing ROCKS-16 subtlety), and
     probe it in the background via `LuaToolProbe` (renderer upgrades from “Unknown”).
     Ad-hoc entries are **not** auto-registered into the inventory (matches today).
- **Edge handling**: de-dup strictly by path; `default == null` and empty registry →
  empty model with `null` selection rendered as “No interpreter selected” (error
  attributes), as today (`LuaInterpreterComponent.kt:85-87`).

### 3.2 Run-config runtime resolution (replaces `LuaRunConfiguration.kt:169-188`)
- **Input → Output**: `LuaRunConfiguration`/`LuaTestRunConfiguration` → `LuaRegisteredTool?`.
- **Steps**:
  1. `p = options.interpreter`; if `p` is non-null/non-empty:
     `LuaToolchainRegistry.findByPath(p)` (§9.1-a) → hit: return it; miss: return an
     ad-hoc RUNTIME tool with `path = p` (explicit user choice always wins — port of
     `LuaApplicationSettings.findInterpreter(path) ?: LuaInterpreter(path, UNKNOWN)`,
     `LuaRunConfiguration.kt:173-174`).
  2. Else return `LuaToolResolver.resolveRuntime(project)` — capability-level
     resolution with the contract-§3 precedence restricted to RUNTIME kinds: active
     environment's RUNTIME tool → project RUNTIME binding → global RUNTIME binding →
     first usable RUNTIME inventory entry → `null`.
  3. `null` at execution time → `ExecutionException("No Lua runtime is configured.
     Add one under Settings → Languages & Frameworks → Lua → Toolchain.")`.

### 3.3 Null-resolution surfacing (shared rule)
- **Interactive launch paths** (run configs, console, busted, publish, browser
  install/remove, workspace build): fail fast — `ExecutionException` or an error
  notification in the existing notification group of that feature; message pattern
  `"<Tool> is not configured. Add or bind it under Settings → … → Lua → Toolchain."`.
- **Passive/background paths** (luacheck annotator, rockspec bridge, search/metadata
  refresh, matrix `update()` enablement): degrade silently (empty result / disabled
  action / debug log) — TOOLING-07 owns proactive surfacing.

### 3.4 Luacheck output parsing (relocation, not redesign)
Regex `(.+?):(\d+):(\d+)-(\d+):(.+)` applied per line of captured stdout
(`Regex.find` per `lineSequence()` element); groups 2–5 → `Problem(lineStart/lineEnd =
g2-1, columnStart = g3-1, columnEnd = g4-1, message = g5 with `\[[;\d]*m`
stripped)` — byte-for-byte the mapping at `LuaCheckInvoker.kt:35-59`.

### 3.5 `LuaInterpreterFamily` survival map (delete-vs-shrink decision: **delete**)
`platform/LuaInterpreterService.kt` + `platform/LuaInterpreter.kt` are deleted whole;
`platform/target/Target.kt`/`PlatformVersionRegistry` do **not** reference them
(verified — `Target` is built from `TargetState{platform, versionLabel}`,
`settings/LuaProjectSettings.kt:30-48`). The following **data must survive** into the
TOOLING-01 built-in kind list (`LuaToolKindRegistry`) — parity checklist for the
reviewer against TOOLING-01's design:

| Legacy datum (file:line) | Survives as |
|---|---|
| Families `Lua`/`LuaJIT`/`Tarantool`: executable names `lua`/`luajit`/`tarantool` (`LuaInterpreter.kt:100-140`) | `LuaToolKind.binaryNames` of kinds `lua`, `luajit`, `tarantool` |
| Version→level levelers: Lua `5.1…5.5 → LUA51…LUA55` else `LUA50` (`:109-118`); LuaJIT → `LUA51` (`:128`); Tarantool → `LUA51` (`:138`) | the kind's runtime mapping feeding `LuaRuntimeInfo.languageLevel` |
| Family→`LuaPlatform`: Lua/LuaJIT → `STANDARD`, Tarantool → `TARANTOOL` (`:108,127,137`) | `LuaRuntimeInfo.platform` per kind |
| `argExecCode="-e"` / `argLoadLib="-l"` (`:106-107,125-126`; Tarantool `null`) | runtime kind metadata consumed by the TOOLING-03 command factory (console `-e` bootstrap, `-lluacov`) |
| `BinaryType.JavaJar` + `java -cp <jar> lua` (`LuaInterpreter.kt:75-78`; `command/LuaCommandLine.kt:39-42`) | TOOLING-03 factory jar branch (§9.1-b) |
| Probe: `lua -v`, banner on **stderr first** then stdout, first line only, `^(\S+)\s+(\S+).*$` (`LuaInterpreterService.kt:86-87`, `Banner:178,190-198`) | runtime kinds' `ProbeSpec` (args `["-v"]`, banner regex, stderr-first rule) |
| Well-known dirs `PATHS_UNIX` (`:133-147`), `PATHS_WINDOWS` glob dirs (`:149-152`), env-var substitution + glob expansion (`:117-128,205-243`) | `LuaToolDiscovery` well-known-dir scan data/mechanics (TOOLING-01) |

### 3.6 Per-environment resolution (matrix rows)
`resolveIn(env: LuaEnvironmentState, kindId): LuaRegisteredTool?` = the first tool `t`
in registry inventory with `t.id ∈ env.toolIds ∧ t.kindId == kindId ∧ t.isUsable`;
no fallback to bindings (a matrix row must use *that* env's tool or fail). Signature
owned by TOOLING-02 (§9.1-a).

### 3.7 Clean-break load tolerance (mechanism)
`PersistentStateComponent` deserialization ignores XML elements that no longer bind to
a property: `com.intellij.util.xmlb.BeanBinding.deserializeInto` iterates the element's
children and dispatches only those for which some accessor binding answers
`isBoundTo(child)`; unmatched children fall through the loop and are skipped (verified
in the local platform source,
`~/Documents/src/lua/intellij-community/platform/util/src/com/intellij/util/xmlb/BeanBinding.kt:310-327`).
Hence stale `<option name="interpreters|toolInventory|globalToolBindings|interpreter|
interpreterMode|…"/>` tags load as no-ops and disappear on next save. Whole deleted
*components* (`LuaCheckSettings`, `LuaRocksSettings` `<component>` blocks) are simply
never requested. The TOOLING-00-06 spike is the empirical gate for this claim; TC 14
adds a permanent regression test.

## 4. External Data & Parsing

No **new** external formats are introduced: luacheck's output parsing is relocated
verbatim (§3.4); the interpreter `-v` banner format moves into TOOLING-01 `ProbeSpec`
data (§3.5); luarocks `--porcelain` parsing (`LuaRocksSearchService`) is untouched.

## 5. Data Flow

### Example 1: LuaCheck annotator run (after cutover)
`LuaCheckAnnotator` → `LuaCheckInvoker.invoke` → `newLuaCheckCommandLine` →
`LuaToolResolver.resolve(project, "luacheck")` (active env → project binding → global →
first usable → null⇒skip) → args from TOOLING-02 `luacheckArguments` + target `--std` →
`LuaToolExecutionService.capture(FORMAT)` → §3.4 line parse → `Problem`s.

### Example 2: Run config with no explicit interpreter
`startProcess` → `resolveInterpreter()` §3.2 → project RUNTIME binding B →
TOOLING-03 factory builds `GeneralCommandLine(B.path)` (jar branch if `.jar`) →
`LuaExecutionEnvironmentBuilder.forProject` applies PATH prepend + LUA_PATH/LUA_CPATH →
debugger env block unchanged → process.

### Example 3: Matrix run
`RunMatrixAction` → `environments(project)` (TOOLING-02) → one background task per row →
`MatrixRunner.commandLineFor` uses `resolveIn(env, "luarocks")` → exec-service stream →
rows aggregate into `MatrixResultsPanel` (unchanged).

## 6. Deletion Inventory (exhaustive; every entry grep-verified)

### 6.1 Classes/files

| # | File (under `src/main/kotlin/net/internetisalie/lunar/`) | Replaced by |
|---|---|---|
| 1 | `tool/LuaTool.kt` (`:15`; ambiguous `isValid` `:41`) | `toolchain.model.LuaRegisteredTool` + `LuaToolHealth` [01] |
| 2 | `tool/LuaToolDescriptor.kt` (`LuaToolType` enum `:19`, descriptor `:27`) | `toolchain.model.LuaToolKind` + `LuaToolKindRegistry` [01] |
| 3 | `tool/LuaToolDiscoveryService.kt` (`:19,53`) | `toolchain.discovery.LuaToolDiscovery` [01] |
| 4 | `tool/LuaToolValidator.kt` (`:17`; `SemanticVersion` `:184`; dead `meetsMinimumVersion` `:119`) | `toolchain.probe.LuaToolProbe`; `SemanticVersion` → `toolchain.model` [01] |
| 5 | `tool/LuaToolManager.kt` (`:28`; mutating `getTools()` `:110-122`; `getEffectiveTool` `:163`) | `toolchain.registry.LuaToolchainRegistry` [01] + `toolchain.resolve.LuaToolResolver` [02] |
| 6 | `tool/LuaToolEnvironment.kt` (`:30,40,51`) | `toolchain.exec.LuaExecutionEnvironmentBuilder` [03] |
| 7 | `tool/LuaTerminalEnvironmentService.kt` (`:33`; stale-cache defect fixed by topic) | env builder + `LuaToolchainListener.TOPIC` [03/02] |
| 8 | `tool/terminal/LuaShellExecOptionsCustomizer.kt` | `toolchain.terminal.LuaShellExecOptionsCustomizer` [03] |
| 9 | `tool/ui/LuaToolsConfigurable.kt` | Toolchain page [06] |
| 10 | `tool/health/LuaToolHealthChecker.kt`, `LuaToolHealthMonitor.kt`, `LuaToolHealthStartup.kt`, `LuaToolEditorNotificationProvider.kt`, `LuaToolDiagnostics.kt` | `toolchain.health.*` [07] (accepted gap between Phase 6 and 07) |
| 11 | `platform/LuaInterpreterService.kt` (whole file incl. `Banner`, glob helpers `:205-269`) | discovery + probe [01] per §3.5 |
| 12 | `platform/LuaInterpreter.kt` (incl. `LuaInterpreterFamily`) | `LuaRegisteredTool`/`LuaRuntimeInfo` + kind data [01] per §3.5 |
| 13 | `platform/LuaInterpreterComponent.kt` (combo `:18`, renderer `:76`) | `toolchain.ui.LuaRuntimeComboBox` [**05**, §2.5/§3.1] |
| 14 | `command/LuaCommandLine.kt` (`:11` dead, `:18`, `:32`) | TOOLING-03 runtime command factory; console cutover §2.5 |
| 15 | `util/LuaProcessUtil.kt` (`capture :17`, `listen :42`) | `LuaToolExecutionService` [03]; last callers cut in Phases 1–4 (LuaCheckInvoker, StyluaFormattingTask, LuaRocksActionHandler, LuaRocksSearchService, LuaRocksMetadataService, PublishRockAction, RockspecBridge, HererocksLocator†, HererocksProvisioner†; † die with #17) |
| 16 | `settings/LuaInterpretersTable.kt` | Toolchain inventory table [06] |
| 17 | `rocks/env/HererocksEnvState.kt`, `HererocksEnvSet.kt`, `HererocksEnvBinder.kt`, `HererocksProvisioner.kt`, `HererocksLocator.kt`, `HererocksEnvDetector.kt`, `HererocksDetectStartup.kt`, `HererocksEnvActions.kt` (4 action classes `:10,21,37,58`), `CreateHererocksEnvDialog.kt` | `toolchain.model.LuaEnvironmentState` + TOOLING-02 env state/activation + `toolchain.provision.LuaToolProvisioner` and its UI [02/04]. Hererocks *detection* is dropped without replacement (envs are plugin-provisioned; foreign trees enter as MANUAL/DISCOVERED tools) |
| 18 | `rocks/env/matrix/BatchProvisionAction.kt`, `BatchProvisionDialog.kt` | TOOLING-04 multi-version provisioning UI (§9.1-g) |
| 19 | `analysis/luacheck/LuaCheckSettings.kt` | resolver + TOOLING-02 `luacheckArguments` option |
| 20 | `rocks/run/LuaRocksSettings.kt` | resolver + TOOLING-02 `rocksServerUrl` app option |

**Moved (not deleted)**: `rocks/env/matrix/{MatrixRunner,RunMatrixAction,MatrixResultsToolWindow}.kt`
→ `rocks/matrix/` (§2.6); `rocks/env/{LuaEnvStatusBarWidget,LuaEnvStatusBarWidgetFactory}.kt`
→ `toolchain.ui` (§2.7). **Kept**: `rocks/LuaRocksEnvironment.kt` (rewritten, §2.4).

### 6.2 State fields — `settings/LuaApplicationSettings.kt`
Deleted: `State.interpreters` (`:39`), `State.toolInventory` (`:45`),
`State.globalToolBindings` (`:53`), companion `findInterpreter` (`:71-73`),
`validInterpreters` (`:75-77`), `getTool` (`:80-81`), and the now-unused
`platform.LuaInterpreter`/`tool.LuaTool` imports (`:20-21`). Kept:
`includeAllFieldsInCompletions`, `enableTypeInference`. (TOOLING-01/02 own the *new*
app persistence — `toolInventory` new shape + `globalBindings`, contract §7 — in their
own component, not this class; cross-check §9.1-h.)

### 6.3 State fields — `settings/LuaProjectSettings.kt`
Deleted fields: `interpreter` (`:53`), `interpreterMode` (`:62`),
`interpreterModeMigrated` (`:69`), `explicitInterpreter` (`:76`), `explicitTarget`
(`:77-78`), `hererocksEnv` (`:116-117`), `hererocksEnvs` (`:124`), `activeEnvId`
(`:130`), `projectToolBindings` (`:101`).
Deleted functions/members: `migrateInterpreterMode` (`:185-191`), `migrateLegacyEnv`
(`:199-207`), `resolveAllEnvs` (`:210`), `activeEnv` (`:213-214`), `addEnv` (`:217-219`),
`removeEnv` (`:222-225`), `upsertAndActivate` (`:236-252`), `setActiveEnvAndNotify`
(`:260-264`), `setProjectToolBindingAndNotify` (`:283-290`), `setInterpreterAndNotify`
(`:297-300`), `interpreterMode` val (`:306-307`), `setInterpreterModeAndNotify`
(`:323-346`), `restoreExplicitOverlay` (`:353-359`), enum `InterpreterMode` (`:409-412`),
and the `loadState` migration calls (`:172-176` reduces to `myState = state`).
Kept: `languageLevel`, `target`/`TargetState`, `sourcePath`, globals options,
`rockspecInclude/ExcludeGlobs`, auto-import options, `rocksServerUrl` (`:109`),
`getTarget/setTarget/setTargetAndNotify`, `expandSourcePath`.
(TOOLING-02 adds `bindings`/`environments`/`activeEnvironmentId` per contract §7.)

Panels (interim slimming here; pages are 06's): `settings/LuaApplicationSettingsPanel.kt`
— interpreters table section removed (`:40,52-59,74-80,94,100`);
`settings/LuaProjectSettingsPanel.kt` — interpreter combo + hererocks-managed checkbox
+ mode apply/reset logic removed (`:28,43-49,85,114-122,126-156,162-199` interpreter/
mode parts; platform/version/level combos and source path stay until 06).

### 6.4 Test migration inventory (from grep over `src/test`; integration tests touch none of this)

| Test file (under `src/test/kotlin/net/internetisalie/lunar/`) | Action |
|---|---|
| `tool/LuaToolManagerTest.kt` | Delete → coverage by TOOLING-01 registry tests |
| `tool/LuaToolBindingResolutionTest.kt` | Delete → TOOLING-02 resolver-precedence tests |
| `tool/LuaToolDiscoveryServiceTest.kt` | Delete → TOOLING-01 discovery tests |
| `tool/LuaToolValidatorTest.kt` | Delete → TOOLING-01 probe/SemanticVersion tests |
| `tool/LuaToolEnvironmentTest.kt` | Delete → TOOLING-03 env-builder tests |
| `tool/health/LuaToolHealthCheckerTest.kt` | Delete → TOOLING-07 health tests |
| `rocks/env/EnvSettingsTestCase.kt` (shared base) | Delete with the suite below |
| `rocks/env/{HererocksEnvActionsTest,HererocksEnvBinderTest,HererocksEnvDetectorTest,HererocksEnvStateTest,HererocksEnvSetStateTest,HererocksEnvSwitchTest,HererocksLocatorTest,HererocksProvisionerTest}.kt` | Delete → TOOLING-02 environment-state/activation tests + TOOLING-04 provisioning tests |
| `rocks/env/LuaEnvStatusBarWidgetTest.kt` | Rewrite (here) onto TOOLING-02 env state (TC: active env name shown; switch updates activeEnvironmentId) |
| `rocks/env/matrix/{BatchProvisionTest,BatchProvisionSetMembershipTest}.kt` | Delete → TOOLING-04 multi-provision tests |
| `rocks/env/matrix/MatrixRunnerTest.kt` | Rewrite (here): `LuaEnvironmentState` rows + `resolveIn` stub — covers TC 12 |
| `rocks/LuaRocksEnvironmentTest.kt` | Rewrite: resolver-backed `resolveExecutable` precedence + nullable contract — TC 7/8 |
| `rocks/run/TestLuaRocksRunConfiguration.kt` | Rewrite: startProcess resolution + PATH prepend — TC 6 |
| `rocks/build/WorkspaceBuildRunnerTest.kt` | Rewrite: exe from facade, null branch |
| `rocks/init/LuaRocksGeneratorPeerTest.kt`, `rocks/init/LuaRocksInterpreterInitializerTest.kt` | Rewrite onto TOOLING-04 request + RUNTIME binding — TC 13 |
| `run/TestLuaRunConfiguration.kt` | Rewrite: seed registry RUNTIME tool + binding instead of `state.interpreter` — TC 10/11 |
| `run/test/LuaTestRunConfigurationTest.kt`, `run/test/LuaTestRunnerTest.kt` | Rewrite: inventory/binding seeding via registry (busted tool) — TC 4/5 |
| `command/LuaCommandLineTest.kt` | Split: jar-branch + command tests move to TOOLING-03 factory tests; project-command tests → resolver-based run-config tests |
| `platform/LuaInterpreterSearchPathGlobTest.kt` | Delete → TOOLING-01 discovery glob-expansion tests (same fixtures) |
| `settings/LuaSettingsSerializationTest.kt` | Rewrite: drop deleted-field round-trips; **add** the stale-tag tolerance test (TC 14) |
| `lang/formatting/external/StyluaFormattingServiceTest.kt` | Rewrite setup: `toolInventory`/`globalToolBindings` seeding → registry + bindings — TC 3 |
| `lang/parser/TestLuaAttributesParser.kt:16`, `lang/syntax/TestLuaNumeralAnnotator.kt:15` | Trivial edit: delete the `LuaCheckSettings…executablePath = ""` disable line (empty registry now disables luacheck by default) |
| `rocks/LuaRocksGeneratorPeerTest.kt` flavor refs | covered by the rocks/init rewrite row |

## 7. Integration Points

### 7.1 plugin.xml — removals (all in `src/main/resources/META-INF/plugin.xml`)

```xml
<!-- DELETE (line refs @ b6c1a193): -->
<applicationService serviceImplementation="net.internetisalie.lunar.tool.LuaToolManager"/>            <!-- :421-423 -->
<projectService serviceImplementation="net.internetisalie.lunar.tool.health.LuaToolHealthMonitor"/>   <!-- :426-427 -->
<postStartupActivity implementation="net.internetisalie.lunar.tool.health.LuaToolHealthStartup"/>     <!-- :428-429 -->
<editorNotificationProvider implementation="net.internetisalie.lunar.tool.health.LuaToolEditorNotificationProvider"/> <!-- :430-431 -->
<postStartupActivity implementation="net.internetisalie.lunar.rocks.env.HererocksDetectStartup"/>     <!-- :433-435 -->
<applicationConfigurable … instance="net.internetisalie.lunar.tool.ui.LuaToolsConfigurable"
    id="net.internetisalie.lunar.tool.ui.LuaToolsConfigurable" displayName="Lua Tools"/>              <!-- :436-439 -->
<applicationService serviceImplementation="net.internetisalie.lunar.rocks.run.LuaRocksSettings"/>     <!-- :501-502 -->
<applicationService serviceImplementation="net.internetisalie.lunar.analysis.luacheck.LuaCheckSettings"/> <!-- :518 -->
<!-- DELETE the whole actions group :643-668 -->
<group id="net.internetisalie.lunar.rocks.env.HererocksEnvGroup" …>   <!-- incl. actions
  Lunar.Hererocks.{Create,Upgrade,Recreate,Remove,BatchProvision} (replaced by TOOLING-04)
  and Lunar.Hererocks.RunMatrix (re-registered below) -->
```

Kept-until-06 (slimmed by this feature, deleted by TOOLING-06):
`applicationConfigurable … LuaRocksSettingsConfigurable` (`:507-510`) and
`… LuaCheckSettingsPanel` (`:512-515`).

### 7.2 plugin.xml — re-registrations owned by this feature

```xml
<!-- class-path updates, same IDs: -->
<statusBarWidgetFactory id="Lunar.LuaEnvWidget"
    implementation="net.internetisalie.lunar.toolchain.ui.LuaEnvStatusBarWidgetFactory"/>   <!-- was :600-601 -->
<toolWindow id="Lunar.LuaMatrix" anchor="bottom" secondary="true" canCloseContents="true"
    factoryClass="net.internetisalie.lunar.rocks.matrix.MatrixResultsToolWindow"/>          <!-- was :602-603 -->
<!-- RunMatrix moves into the TOOLING-04 environment group (group id per 04's design): -->
<action id="Lunar.Toolchain.RunMatrix"
    class="net.internetisalie.lunar.rocks.matrix.RunMatrixAction"
    text="Run Test Matrix…"
    description="Run the rockspec test command against every provisioned environment"/>
```

### 7.3 Registrations added by siblings (context, not owned here)
Per contract: TOOLING-01/02/03 services use `@Service` annotations (no plugin.xml
entries — the current `LuaToolManager` double-registers via both `@Service`
(`tool/LuaToolManager.kt:27`) *and* plugin.xml; the new code must not repeat that);
TOOLING-03 re-points `lunar-terminal.xml`'s `shellExecOptionsCustomizer` to
`toolchain.terminal.LuaShellExecOptionsCustomizer`; TOOLING-04 registers the
provisioning action group; TOOLING-06 registers the Toolchain configurable under the
Lua tree (contract §8); TOOLING-07 re-adds `postStartupActivity` +
`editorNotificationProvider` under `toolchain.health`. Cross-check at review (§9.1).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| TOOLING-05-01 | M | §2.1, §3.3, §3.4 |
| TOOLING-05-02 | M | §2.2 |
| TOOLING-05-03 | M | §2.3, §3.3 |
| TOOLING-05-04 | M | §2.4, §3.3 |
| TOOLING-05-05 | M | §2.5, §3.1, §3.2 |
| TOOLING-05-06 | M | §2.6, §2.7, §3.6 |
| TOOLING-05-07 | M | §2.8 |
| TOOLING-05-08 | M | §6.1, §3.5 |
| TOOLING-05-09 | M | §6.2, §6.3, §3.7 |
| TOOLING-05-10 | M | §7.1, §7.2 |
| TOOLING-05-11 | M | §6.4 |
| TOOLING-05-12 | S | §2.9, §6 (grep gates), plan Phase 6 |

## 9. Alternatives Considered

- **Shrink `LuaInterpreterService` instead of deleting** — rejected: nothing outside
  the deleted surface consumes it (`Target` is independent, §3.5); keeping a husk
  preserves the parallel-subsystem smell the epic exists to remove. The family data
  moves to kind descriptors instead.
- **Migrate persisted fields** — rejected by user decision (2026-07-05); no install
  base justifies the shim complexity. Tolerated-stale-tags (§3.7) is sufficient.
- **Delete `LuaRocksEnvironment` and call the resolver directly at each site** —
  rejected: `withServer`/server precedence needs one home, and the facade keeps the
  six luarocks call sites one-line migrations.
- **Retype the run-config `interpreter` option to a tool UUID** — rejected: UUIDs die
  with the clean break and are not portable across machines via `.run` files; a path
  string is stable and preserves existing run configs for free.
- **Big-bang single commit** — rejected: the per-consumer phase order (plan) keeps
  every commit green and bisectable; old/new systems coexist until Phase 6.

### 9.1 Cross-check items (sibling designs did not exist at time of writing)
Reviewer must reconcile these names/signatures against TOOLING-01…04/06 designs when
they land (contract sanctions the *capability*, the *symbol* is the sibling's call):
- **a)** `LuaToolResolver.resolveRuntime(project): LuaRegisteredTool?` (contract §3/§9
  “resolver(RUNTIME)”), `LuaToolResolver.resolveIn(env: LuaEnvironmentState, kindId):
  LuaRegisteredTool?` (contract §9 “resolver overload”), and
  `LuaToolchainRegistry.findByPath(path: String): LuaRegisteredTool?` [02/01].
- **b)** TOOLING-03 runtime command-line factory preserving the jar branch
  (`java -cp <jar> lua`, from `command/LuaCommandLine.kt:39-42`) and the `-e`/`-l`
  runtime arg metadata (§3.5).
- **c)** Contract §10.6 pins `capture(cmd: GeneralCommandLine, timeout: LuaExecTimeout,
  stdin: String? = null, indicator?)` and `stream(cmd, listener, timeout, colored,
  indicator?)`, with env/workdir on the `GeneralCommandLine` (no `execute(env, workDir)`
  facade). §2.6 matrix rows + LuaRocks handlers use the pinned `stream`/`capture` directly.
  **Resolved:** stylua's process **stdin** (`request.documentText`) is fed through the
  `stdin` parameter of `capture` (contract §10.6 / TOOLING-03-17) — the earlier gap was
  closed by adding the stdin channel to the exec service, so §2.2 uses `capture(cmd, FORMAT,
  stdin = request.documentText)` with no bespoke handler.
- **d)** `LuaExecutionEnvironmentBuilder.forProject(project)` exposes
  `applyTo(GeneralCommandLine)` and computes the LUA_PATH union currently in
  `LuaTestCommandLineState.kt:133-145` / `LuaRunConfiguration.kt:272-283`
  (`RockspecRunPathProvider` prefix + expanded project source path + `;;`).
- **e)** Kind-scoped option accessors are **pinned by contract §10.5/§10.8** (no longer
  open): `kindOption(key)`/`setKindOption(key, value)` on
  `LuaToolchainProjectSettings.getInstance(project)` (project scope) and on
  `LuaToolchainRegistry.getInstance()` (app-default scope), keyed by
  `LuaKindOptionKeys.LUACHECK_ARGUMENTS = "luacheck.arguments"` and
  `LUAROCKS_SERVER_URL = "luarocks.serverUrl"`. Precedence: project option → app default
  (contract §7). Cross-check is only that TOOLING-02 ships these constants + accessors.
- **f)** TOOLING-02 environment accessors on
  `LuaToolchainProjectSettings.getInstance(project)` (contract §10.5, all instance
  methods, no project arg): `environments(): List<LuaEnvironmentState>`,
  `activeEnvironment(): LuaEnvironmentState?`, `activateEnvironment(environmentId:
  String): Boolean`, `setBinding(kindId, toolId)`,
  `upsertEnvironmentAndActivate(env): LuaEnvironmentState` — used by §2.6/§2.7/§2.8.
- **g)** TOOLING-04: provisioning request type (kindId + versionSpec set), the
  environment action group id, and multi-version (batch) provisioning coverage
  replacing `BatchProvisionAction`.
- **h)** TOOLING-01/02 own the new persistence components (app `toolInventory` new
  shape + `globalBindings`; project `bindings`/`environments`/`activeEnvironmentId`);
  this feature only deletes the legacy fields (§6.2/§6.3).

### 9.2 Proposed contract deviations (NOT applied — for the supervisor)
1. **§9 consumer table additions**: `rocks/browser/LuaRocksActionHandler.kt:33,57`,
   `rocks/browser/LuaRocksMetadataService.kt:30`, `rocks/build/WorkspaceBuildRunner.kt:29`
   (direct `LuaRocksSettings.executablePath` reads) and the `rocks/init` New Project
   wizard (§2.8) are real consumers missing from the contract's checklist.
2. **§1 note**: `util/LuaProcessUtil` deletion is implied by §5 (“absorbs”) but absent
   from the §1 legacy-deletions list — add it.
3. **Package homes for survivors**: matrix classes → `rocks/matrix`, env status-bar
   widget → `toolchain.ui` (contract is silent; stated here for the record).

## 10. Open Questions

_None — the planning bar is cleared. Serialization tolerance is gated by the TOOLING-00-06 spike (§3.7); sibling-API name alignment is tracked as review cross-checks in §9.1, not implementer decisions._