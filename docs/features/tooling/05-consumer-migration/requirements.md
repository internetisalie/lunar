---
id: TOOLING-05
title: "05: Consumer Migration & Legacy Removal"
type: feature
status: "done"
priority: "high"
parent_id: TOOLING
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# TOOLING-05: Consumer Migration & Legacy Removal

## Overview

The clean-break cutover of the [TOOLING epic](../requirements.md): every consumer of an
external Lua binary switches from its current resolution path (four different patterns
today) onto the unified `toolchain.*` subsystem delivered by TOOLING-01…04, and the
entire legacy surface — the `tool/` package, the `platform/` interpreter subsystem, the
`rocks/env/` hererocks lifecycle, the per-tool `executablePath` settings services, and
the legacy persisted state fields — is **deleted, not migrated** (user decision
2026-07-05; see [tooling-product-requirements.md](../tooling-product-requirements.md)
Resolved Decision 2). The binding contract is
[tooling-architecture.md](../tooling-architecture.md), especially §9 (consumer
inventory) and §1/§7 (deletions / clean-break persistence).

## Scope

### In Scope
- Per-consumer migration of **every** call site that resolves or executes an external
  Lua binary: luacheck, stylua, busted, luacov, luarocks (run config, browser, search,
  metadata, publish, workspace build), the Lua runtime consumers (run config, console,
  test runner, run-config producer, rockspec bridge, interpreter combo box), the matrix
  runner, the env status-bar widget, and the New Project wizard (`rocks/init`).
- The full **deletion inventory**: `tool/*` (all classes), `platform/LuaInterpreter*`,
  `settings/LuaInterpretersTable` + the interpreter parts of the settings panels,
  `rocks/env/*` hererocks classes, `command/LuaCommandLine.kt`, `util/LuaProcessUtil.kt`,
  `LuaCheckSettings`, `LuaRocksSettings`, and all legacy `State` fields on
  `LuaApplicationSettings`/`LuaProjectSettings` (including the ROCKS-16 mode state
  machine and both migration functions).
- The `plugin.xml` delta: every legacy registration removed; re-registration of the
  classes this feature moves (matrix tool window/action, env status-bar widget).
- Interim slimming of the two per-tool settings panels (path fields removed, remaining
  options rebound) so the build stays green until TOOLING-06 folds the pages.
- The test-suite migration inventory (rewrite vs delete-with-replacement per file).
- Clean-break load tolerance: stale XML tags in `lunar.xml` must be ignored on load.

### Out of Scope
- The new subsystems themselves: model/registry/discovery/probe (TOOLING-01),
  resolver/bindings/environments/events (TOOLING-02), execution service/environment
  builder/terminal customizer (TOOLING-03), native provisioning (TOOLING-04).
- The consolidated settings tree and page deletion/replacement (TOOLING-06). This
  feature only deletes state/services and excises now-compile-broken UI parts;
  TOOLING-06 owns the pages.
- Health monitoring banners/notifications (TOOLING-07). Deleting `tool/health/*` here
  creates a deliberate, temporary health-surface gap until TOOLING-07 lands.
- Any transparent migration of persisted settings (explicit non-goal — clean break).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TOOLING-05-01 | **Luacheck via resolver** | M | The luacheck annotator/inspection resolves the binary via `LuaToolResolver.resolve(project, "luacheck")`; `LuaCheckSettings` (incl. the `/usr/local/bin/luacheck` default) is deleted; arguments come from the TOOLING-02 kind-scoped option. No binary resolved → annotator produces no problems (no error). |
| TOOLING-05-02 | **Stylua via resolver + exec service** | M | `StyluaFormattingService`/`StyluaFormattingTask` resolve via `resolve(project, "stylua")` and execute through `LuaToolExecutionService` (FORMAT timeout class, stdin supported) instead of a raw `CapturingProcessHandler`. |
| TOOLING-05-03 | **Test & coverage tools via resolver** | M | Busted (`LuaTestCommandLineState`) and luacov (`LuaCoverageProgramRunner`) resolve via `resolve(project, "busted"/"luacov")`; the busted command line gets its PATH/LUA_PATH/LUA_CPATH from `LuaExecutionEnvironmentBuilder`. |
| TOOLING-05-04 | **LuaRocks consumers via resolver** | M | `LuaRocksEnvironment.resolveExecutable` is rewritten onto `resolve(project, "luarocks")` (returns `String?`, no `"luarocks"` fallback); `withServer`/server precedence is retained in `LuaRocksEnvironment`. All five bypassing call sites (run config `:208`, action handler, metadata service, workspace build, plus the already-facaded search/publish) go through it. The LuaRocks run config additionally gains the env-builder PATH prepend. |
| TOOLING-05-05 | **Lua runtime consumers via resolver** | M | `LuaRunConfiguration`/`LuaTestRunConfiguration` `resolveInterpreter`, `LuaConsoleRunner`, `LuaTestRunConfigurationProducer`, and `RockspecBridge` resolve the runtime via `LuaToolResolver.resolveRuntime(project)` (explicit run-config path wins). The run-config combo box lists RUNTIME-kind registered tools plus the project-resolved default, preserving the ROCKS-16 follow-up semantics (commits a47161e6/29d1b636/b6c1a193). Command lines are built by the TOOLING-03 factory (jar-interpreter handling preserved) with env-builder injection. |
| TOOLING-05-06 | **Matrix runner & env widget on TOOLING-02 environments** | M | `MatrixRunner`/`RunMatrixAction`/`MatrixResultsToolWindow` operate on `LuaEnvironmentState` from the TOOLING-02 project state (replacing `HererocksEnvSet.all`), resolving each row's luarocks via the per-environment resolver overload. `LuaEnvStatusBarWidget` reads/switches the TOOLING-02 active environment. |
| TOOLING-05-07 | **New Project wizard migrated** | M | `rocks/init` (`LuaRocksGeneratorPeer`, `LuaRocksProjectSettings`, `LuaRocksInterpreterInitializer`) provisions via `LuaToolProvisioner` and binds via TOOLING-02 instead of `HererocksProvisioner`/`InterpreterMode`. |
| TOOLING-05-08 | **Legacy code deleted** | M | Every class/file in the design §6 deletion inventory is removed: `tool/*`, `platform/LuaInterpreter.kt`, `platform/LuaInterpreterService.kt`, `platform/LuaInterpreterComponent.kt`, `settings/LuaInterpretersTable.kt`, `rocks/env/*` (hererocks lifecycle), `command/LuaCommandLine.kt`, `util/LuaProcessUtil.kt`, `analysis/luacheck/LuaCheckSettings.kt`, `rocks/run/LuaRocksSettings.kt`. The `LuaInterpreterFamily` banner/level/platform data survives inside the TOOLING-01 built-in kinds (design §3.5 parity checklist). |
| TOOLING-05-09 | **Legacy state fields deleted; stale XML tolerated** | M | The legacy fields on `LuaApplicationSettings.State` (`interpreters`, `toolInventory`, `globalToolBindings`) and `LuaProjectSettings.State` (`interpreter`, `interpreterMode`, `interpreterModeMigrated`, `explicitInterpreter`, `explicitTarget`, `hererocksEnv`, `hererocksEnvs`, `activeEnvId`, `projectToolBindings`) are deleted, along with `migrateLegacyEnv`/`migrateInterpreterMode` and the whole `InterpreterMode` machinery. Loading a `lunar.xml` that still contains those tags must succeed silently (XmlSerializer ignores unbound elements — design §3.7; empirically verified by TOOLING-00-06). |
| TOOLING-05-10 | **plugin.xml legacy registrations removed** | M | All legacy registrations (design §7 removal table) are deleted; the moved matrix/widget classes are re-registered under their new packages with their existing IDs. |
| TOOLING-05-11 | **Test suite migrated** | M | Every test file in the design §6.4 inventory is rewritten onto the new APIs or deleted with named replacement coverage; the full unit suite is green. |
| TOOLING-05-12 | **Zero residual legacy references** | S | After the final deletion commit, `grep -r` over `src/main` finds no references to `net.internetisalie.lunar.tool.`, `Hererocks`, `LuaInterpreter`, `LuaCheckSettings`, `LuaRocksSettings.getInstance`, `executablePath`, `InterpreterMode`, or `LuaProcessUtil` (success metric: “0 direct `executablePath`-style fields left in code”, PRD). |

## Detailed Specifications

Per-consumer current→target call specifications, with `file:line` evidence, live in
[design.md](design.md) §2 (consumers) and §6 (deletion inventory). The requirement rows
above are intentionally thin; the design is the normative spec.

### TOOLING-05-01: Luacheck
Current: `analysis/luacheck/LuaCheckCommandLine.kt:17,22` reads
`LuaCheckSettings.state.executablePath` (default `/usr/local/bin/luacheck`,
`LuaCheckSettings.kt:15`); `:27` reads `state.arguments`. Target: design §2.1. Empty
resolution returns `null` exactly as the empty-path case does today
(`LuaCheckCommandLine.kt:18-20`), so the annotator degrades to “no findings”.

### TOOLING-05-04: LuaRocks
The five direct/bypassing executable reads are `rocks/run/LuaRocksRunConfiguration.kt:208`,
`rocks/browser/LuaRocksActionHandler.kt:33,57`, `rocks/browser/LuaRocksMetadataService.kt:30`,
`rocks/build/WorkspaceBuildRunner.kt:29`, and the facade fallback
`rocks/LuaRocksEnvironment.kt:57`. All become `LuaRocksEnvironment.resolveExecutable(project)`
(resolver-backed, nullable). Server precedence (project `rocksServerUrl` → app default →
none) and `withServer` argument injection stay in `LuaRocksEnvironment` (design §2.4).

### TOOLING-05-05: Runtime resolution precedence
For a run configuration: stored `interpreter` path (non-empty) → registry entry with that
path, else an ad-hoc unregistered runtime; empty → `resolveRuntime(project)` (active
environment → project RUNTIME binding → global → first usable RUNTIME entry → `null` ⇒
`ExecutionException` with a “configure a Lua runtime” hint). Full algorithm: design §3.2.

## Behavior Rules

- **Coexistence**: legacy and new systems coexist during the phased cutover; each commit
  leaves the build and full test suite green. Legacy classes are deleted only in the
  final removal commits, after their last consumer has been cut over (plan, Phases 1–6).
- **No new defaults**: no hardcoded fallback paths anywhere (contract §3 step 5). A
  `null` resolution surfaces a kind-specific hint (notification or `ExecutionException`);
  background/passive consumers (annotator, rockspec bridge) degrade silently.
- **No migration code**: no reading of legacy fields to seed new state, ever.
- **Events**: migrated consumers that cache resolution results subscribe to
  `LuaToolchainListener.TOPIC` (TOOLING-02), not `LuaSettingsChangedListener.TOPIC`,
  which remains for non-toolchain settings (target, source path).

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | TOOLING-05-01 | Registry has a `luacheck` tool at `/tmp/tools/luacheck` (usable), project binds kind `luacheck` to it; TOOLING-02 project option `luacheckArguments = "--std max"` | `newLuaCheckCommandLine(project, "a.lua", dir)` | Returned command line `exePath == "/tmp/tools/luacheck"`; parameters contain `--std`, `max`, `--codes`, `--ranges`, `a.lua`; class `LuaCheckSettings` no longer exists in `src/main` |
| 2 | TOOLING-05-01 | Empty registry, no bindings | `newLuaCheckCommandLine(project, "a.lua", dir)` | Returns `null` (no `/usr/local/bin/luacheck` default, no exception) |
| 3 | TOOLING-05-02 | No usable `stylua` resolves for the project | `StyluaFormattingService.canFormat(luaPsiFile)` | Returns `false`; with a usable bound stylua at path P, `createFormattingTask` produces a task whose exec-service request has executable P and args `["--stdin-filepath", <name>]` |
| 4 | TOOLING-05-03 | Project binds `busted` to `/tmp/tools/busted`; env builder yields PATH-prepend dir `/tmp/tools` | `LuaTestCommandLineState.buildCommandLine()` (framework=BUSTED) | `exePath == "/tmp/tools/busted"`; `environment["PATH"]` starts with `/tmp/tools` + path separator |
| 5 | TOOLING-05-03 | Empty registry | busted `buildCommandLine()` | Throws `ExecutionException` naming busted and how to configure it (no silent PATH fallback) |
| 6 | TOOLING-05-04 | Project binds `luarocks` to `/tmp/tools/luarocks` | `LuaRocksRunConfiguration.getState(...).startProcess()` command line | Executable is `/tmp/tools/luarocks` (not a `LuaRocksSettings` read — class deleted); `PATH` has env-builder dirs prepended |
| 7 | TOOLING-05-04 | Nothing usable resolves for `luarocks` | `LuaRocksEnvironment.resolveExecutable(project)` | Returns `null`; `LuaRocksSearchService.search` performs no process launch and surfaces the configure hint |
| 8 | TOOLING-05-04 | Project `rocksServerUrl = "https://x"`, resolver returns `/tmp/tools/luarocks` | `withServer(listOf("search","--porcelain","q"), resolveServer(project))` | `["--server","https://x","search","--porcelain","q"]` (precedence/injection unchanged) |
| 9 | TOOLING-05-05 | Registry has RUNTIME tools A(`/opt/lua54/bin/lua`), B(`/opt/luajit/bin/luajit`); project RUNTIME binding → B | Run-config editor combo model is built | Model contains B (initial selection, the project-resolved default) and A, de-duplicated by path; typing path `/x/lua` not in the registry keeps an ad-hoc entry selected **and** B stays in the model (ROCKS-16 follow-up semantics) |
| 10 | TOOLING-05-05 | Run config with empty `interpreter` option; project RUNTIME binding → B | `resolveInterpreter()` / `startProcess()` | Resolves B; command line `exePath == B.path`; `LUA_PATH`/`LUA_CPATH`/`PATH` come from `LuaExecutionEnvironmentBuilder.forProject(project)` |
| 11 | TOOLING-05-05 | Run config `interpreter` option = `/x/custom-lua` (not registered) | `resolveInterpreter()` | Resolves an ad-hoc runtime with path `/x/custom-lua` (explicit choice wins over bindings) |
| 12 | TOOLING-05-06 | Two environments E1, E2 in TOOLING-02 project state, each with its own provisioned `luarocks` tool | `RunMatrixAction` builds rows / `MatrixRunner.commandLineFor` | Row 1 executable == E1's luarocks path, row 2 == E2's; an env with no luarocks yields a FAIL row with exit `-1` and a “not provisioned” message, without aborting other rows |
| 13 | TOOLING-05-07 | Wizard: “provision” checked, Lua 5.4 selected | Project generated | A TOOLING-04 provision request (kind `lua` 5.4 + `luarocks`) is queued post-open; no `HererocksProvisioner`/`InterpreterMode` symbol exists |
| 14 | TOOLING-05-09 | `LuaProjectSettings.State` XML containing stale `<option name="hererocksEnvs">…`, `<option name="interpreterMode" value="HEREROCKS_MANAGED"/>`, `<option name="projectToolBindings">…` plus current fields | `XmlSerializer.deserialize` / `loadState` | Loads without exception; `languageLevel`, `sourcePath`, `rocksServerUrl` round-trip; stale tags are absent from the re-serialized state |
| 15 | TOOLING-05-08/-10/-12 | Final removal commits applied | `grep -rE "net\.internetisalie\.lunar\.tool\.|Hererocks|LuaInterpreter|InterpreterMode|LuaCheckSettings|LuaRocksSettings|LuaProcessUtil|executablePath" src/main src/main/resources/META-INF` | Zero matches; `tooling/gce-builder run test` green |
| 16 | TOOLING-05-11 | Migrated suite | `run test` | 0 failures; every deleted test file has its named replacement (design §6.4) present |

## Acceptance Criteria

- [x] TC 1–13 pass as automated tests (light fixtures; registry/bindings seeded per test).
- [x] TC 14 stale-XML tolerance test passes (TOOLING-05-09).
- [x] TC 15 grep gates pass; deletion inventory (design §6) fully executed (TOOLING-05-08/-10/-12).
- [x] TC 16: full suite green after the final removal commit (TOOLING-05-11) — verified via forced `run "test --rerun --no-build-cache"` (the remote build cache can mask a broken test) + `run build` (incl. :integrationTest/:checkStatus/:lintDocs/:koverVerify).
- [ ] **Deferred to TOOLING-06** (decision 2026-07-09): Live VNC bind→lint→run check. No UI-less tool-binding path exists in-tree until TOOLING-06 lands the settings UI; the automated DoD (full suite + integrationTest + `run build`) is comprehensively green, and this matches the criterion's own "or TOOLING-06 if landed" hedge. Exercise the manual bind→lint→run→matrix flow when TOOLING-06's Toolchain page is available.
- [x] `CHANGELOG.md` documents the clean break (settings must be re-created once).

## Non-Functional Requirements

- Resolution calls (`resolve`/`resolveRuntime`) are cheap state reads, safe on the EDT
  (contract §10); all process execution stays on background threads via TOOLING-03.
- No new hard references to `Project` in application-level code (project passed
  per-call, as today).
- Each cutover commit is atomic per consumer group and leaves the suite green
  (regression-relative gate).

## Dependencies

- **Blocked by**: TOOLING-01 (model/registry/discovery/probe), TOOLING-02
  (resolver/bindings/environments/options relocation/topic), TOOLING-03 (exec
  service/env builder/terminal), TOOLING-04 (provisioner + env lifecycle actions —
  required before the hererocks lifecycle deletion, Phase 5), TOOLING-00-06
  (serialization-tolerance spike).
- **Coordinates with**: TOOLING-06 (pages; this feature slims panels, 06 deletes them),
  TOOLING-07 (re-adds health surface after `tool/health/*` deletion).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Contract: [../tooling-architecture.md](../tooling-architecture.md)
