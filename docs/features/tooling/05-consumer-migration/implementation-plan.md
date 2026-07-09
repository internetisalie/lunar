---
id: TOOLING-05-PLAN
title: "Implementation Plan"
type: plan
priority: "high"
parent_id: TOOLING-05
folders:
  - "[[features/tooling/05-consumer-migration/requirements|requirements]]"
---

# TOOLING-05: Implementation Plan

Precondition: TOOLING-01/02/03 merged (04 required from Phase 4 on; TOOLING-00-06 spike
done). **Coexistence rule**: until Phase 6, legacy (`tool/*`, `platform/LuaInterpreter*`,
`rocks/env/*`, per-tool settings) and new (`toolchain.*`) systems compile and run side by
side; each phase is one or more atomic commits, each leaving
`tooling/gce-builder/gce-builder.sh run test` green (regression-relative gate). During
coexistence the two systems share **no state** — a consumer reads either the old or the
new model, never both; a consumer is "cut over" when none of its code paths touch a
legacy symbol. plugin.xml removals ride the commit that deletes the class they register.

## Phases

### Phase 1: Simple tool consumers → resolver + exec service [Must]
- **Goal**: luacheck, stylua, busted, luacov resolve/execute through the new stack.
- **Tasks**:
  - [x] Cut over `analysis/luacheck/LuaCheckCommandLine.kt` + `LuaCheckInvoker.kt`
        (resolver, TOOLING-02 args option, exec-service capture + §3.4 line parse) —
        realizes design §2.1, §3.4
  - [x] Slim `LuaCheckSettingsPanel.kt` (drop exe row + download link; rebind args) —
        design §2.1
  - [x] Delete `analysis/luacheck/LuaCheckSettings.kt` + its `applicationService`
        registration (plugin.xml:518); fix `TestLuaAttributesParser.kt:16` /
        `TestLuaNumeralAnnotator.kt:15` (drop the disable line) — design §6.1#19, §6.4
  - [x] Cut over `StyluaFormattingService.kt` / `StyluaFormattingTask.kt` (resolver +
        stdin-capable exec capture); rewrite `StyluaFormattingServiceTest.kt` seeding —
        design §2.2, §6.4
  - [x] Cut over busted (`LuaTestCommandLineState.kt:55,61-64,133-145`) and luacov
        (`LuaCoverageProgramRunner.kt:29`) onto resolver + env builder — design §2.3
  - [x] Rewrite `LuaTestRunnerTest.kt` tool seeding
        (`LuaTestRunConfigurationTest.kt` has no busted-tool seeding — no-op) — design §6.4
- **Exit criteria**: TC 1–5 pass; `grep -rn "LuaCheckSettings" src/main` = 0
        (only the design-mandated `LuaCheckSettingsPanel` survivor matches the substring);
        suite green.

### Phase 2: LuaRocks consumers [Must]
- **Goal**: one nullable, resolver-backed luarocks resolution; PATH prepend fixed.
- **Tasks**:
  - [x] Rewrite `rocks/LuaRocksEnvironment.resolveExecutable` (nullable, resolver) and
        `resolveServer` app fallback onto the TOOLING-02 option — design §2.4
  - [x] Cut over `LuaRocksRunConfiguration.kt:208` (+ env-builder PATH prepend),
        `LuaRocksActionHandler.kt:33,57`, `LuaRocksMetadataService.kt:30`,
        `WorkspaceBuildRunner.kt:29`; add null branches to `LuaRocksSearchService` /
        `PublishRockAction` — design §2.4, §3.3
  - [x] Slim `LuaRocksSettingsConfigurable` (drop exe row; rebind server) — design §2.4
  - [x] Delete `rocks/run/LuaRocksSettings.kt` + registration (plugin.xml:501-502)
  - [x] Rewrite tests: `LuaRocksEnvironmentTest.kt`, `TestLuaRocksRunConfiguration.kt`,
        `WorkspaceBuildRunnerTest.kt` — design §6.4
- **Exit criteria**: TC 6–8 pass; suite green. Grep-gate nuance (as in Phase 1): the literal
        `grep -rn "LuaRocksSettings" src/main = 0` cannot hold while the interim
        `LuaRocksSettingsConfigurable` survives until TOOLING-06 (substring match). The real
        Phase 2 gate is `LuaRocksSettings.kt` (the service) deleted and zero
        `LuaRocksSettings.getInstance` / `.executablePath` / `.serverUrl` reads remaining —
        the slimmed `LuaRocksSettingsConfigurable` is the sole allowed residual substring:
        `grep -rn "LuaRocksSettings" src/main | grep -v LuaRocksSettingsConfigurable` = 0.

### Phase 3: Lua runtime consumers [Must]
- **Goal**: run/debug/console/test/producer/bridge resolve RUNTIME via resolver; combo
  lists RUNTIME tools + project default.
- **Tasks**:
  - [x] Create `toolchain.ui.LuaRuntimeComboBox` implementing the §3.1 model algorithm
        (+ renderer port) — design §2.5, §3.1
  - [x] Cut over `LuaRunConfiguration.kt` (interpreter prop, `resolveInterpreter` §3.2,
        startProcess factory + env builder, editor combo) — design §2.5, §3.2
  - [x] Cut over `LuaTestRunConfiguration.kt` (+ lunity branch in
        `LuaTestCommandLineState.kt:108-131`) and
        `LuaTestRunConfigurationProducer.kt:53-55,74-76` — design §2.5
  - [x] Cut over `LuaConsoleRunner.kt:37` and `RockspecBridge.kt:35` — design §2.5
  - **Deferred to Phase 4** (ordering correction 2026-07-09): `command/LuaCommandLine.kt`
        cannot be deleted in Phase 3 because `platform/LuaInterpreterService.kt:86` (a
        Phase-4 deletion) still calls `newLuaInterpreterCommandLine`. Deleting the factory
        now breaks compilation. The `command/LuaCommandLine.kt` deletion + the
        `LuaCommandLineTest.kt` split move to Phase 4, bundled with the
        `LuaInterpreterService` deletion (its last live caller). Phase 3 only stops the
        *live runtime consumers* from calling the legacy factory.
  - [x] Rewrite `TestLuaRunConfiguration.kt`; `LuaInterpreterSearchPathGlobTest.kt` tests
        `expandSearchPath` on the surviving `LuaInterpreterService.kt`, so its coverage is
        **left in place** and moves to TOOLING-01 discovery tests in Phase 4 — design §6.4
- **Exit criteria** (corrected): TC 9–11 pass; debugger smoke (DBGp attach via run config)
  still works in sandbox; the live runtime consumers (LuaRunConfiguration,
  LuaTestRunConfiguration, LuaConsoleRunner, RockspecBridge, LuaTestRunConfigurationProducer,
  LuaTestCommandLineState lunity branch) contain zero references to `newLua*CommandLine` or
  `platform.LuaInterpreter`; the sole residual `newLuaInterpreterCommandLine` reference is
  the legacy `platform/LuaInterpreterService.kt` (+ `command/LuaCommandLine.kt` itself),
  both deleted in Phase 4; suite green.

### Phase 4: Wizard + settings-state deletion [Must] (requires TOOLING-04)
- **Goal**: rocks/init provisioning + bindings on the new stack; the Phase-4-deletable legacy
  state fields gone. **Corrected deletion schedule (2026-07-09, build-arbiter rule):** the plan's
  original "delete ALL legacy state/classes in Phase 4" is an ordering defect — several symbols are
  hard-referenced by packages that only die in Phase 5/6. A field/file is deleted here **only** when
  every surviving-package reader is gone; the rest are deferred and recorded below.
- **Tasks**:
  - [x] Migrate `LuaRocksProjectSettings.kt` (`flavor: HererocksFlavor` → `kindId: "lua"|"luajit"`
        + version; `provisionHererocks`→`provisionEnvironment`), `LuaRocksInterpreterInitializer.kt`
        (inline the `HererocksEnvState.toTarget` mapping; explicit path → `setBinding("lua", tool.id)`;
        provision path → `upsertEnvironmentAndActivate` + `LuaToolProvisioner.provision`),
        `LuaRocksGeneratorPeer.kt` (`ComboBox<LuaRegisteredTool>` via `LuaRuntimeComboBox.customize`)
        onto provisioner + RUNTIME binding — design §2.8
  - [x] Delete the **Phase-4-deletable** fields/functions from `LuaApplicationSettings.kt`
        (`interpreters`, `findInterpreter`, `validInterpreters`, `getTool`, `LuaInterpreter` import —
        §6.2; `getTool` has zero readers) and the `migrateInterpreterMode` migration fn from
        `LuaProjectSettings.kt` (clean break). **DEFERRED to Phase 5** (pinned by `rocks/env/*`, esp.
        `HererocksEnvBinder`): `LuaProjectSettings.State.interpreter`/`explicitInterpreter`/
        `explicitTarget` (read by the InterpreterMode overlay machinery), `interpreterMode`(+`Migrated`),
        `hererocksEnv(s)`, `activeEnvId`, the `InterpreterMode` enum, and every env-helper
        (`resolveAllEnvs`/`activeEnv`/`addEnv`/`removeEnv`/`upsertAndActivate`/`setActiveEnvAndNotify`/
        `setInterpreterAndNotify`/`setInterpreterModeAndNotify`/`restoreExplicitOverlay`/`interpreterMode`
        val/`migrateLegacyEnv`). **DEFERRED to Phase 6** (pinned by `tool/LuaToolManager`):
        `LuaApplicationSettings.State.toolInventory`/`globalToolBindings`,
        `LuaProjectSettings.State.projectToolBindings`.
  - [x] Excise the interpreters-table section from `LuaApplicationSettingsPanel.kt` and the
        interpreter-combo + hererocks-managed-checkbox + mode apply/reset logic from
        `LuaProjectSettingsPanel.kt` (platform/version/level + source path + rocksServerUrl stay);
        delete `settings/LuaInterpretersTable.kt` — design §6.3 (pages remain for TOOLING-06)
  - [x] §3.5 parity check PASSED (datum-by-datum in handoff). Delete
        `platform/LuaInterpreterComponent.kt` (combo/renderer, replaced by
        `toolchain.ui.LuaRuntimeComboBox`). **DEFERRED to Phase 5**:
        `platform/LuaInterpreterService.kt` + `platform/LuaInterpreter.kt` — `rocks/env/HererocksEnvBinder`
        (a Phase-5 deletion) hard-references `LuaInterpreter`/`LuaInterpreterService.identify`, and
        `LuaProjectSettings.State.interpreter` (also Phase-5-deferred) is typed `LuaInterpreter?`.
        Deleting them now breaks compilation — design §6.1#11-13, §3.5
  - [x] **(deferred from Phase 3)** **`command/LuaCommandLine.kt` re-DEFERRED to Phase 5**: its last
        live code caller is `LuaInterpreterService.kt:86` (`newLuaInterpreterCommandLine`), and
        `LuaInterpreterService` itself defers to Phase 5 (above). The `tool/*` references to it are
        doc-comment only. `LuaCommandLineTest.kt` split defers with it — design §6.1#14, §6.4
  - [x] **(deferred from Phase 3)** Port `LuaInterpreterSearchPathGlobTest.kt` (`expandSearchPath`)
        fixtures into the TOOLING-01 `LuaToolDiscoveryTest` (`LuaToolDiscovery.expandSearchPath`,
        byte-for-byte-equivalent) — coverage now duplicated so no loss. The platform glob test file is
        **deleted with `LuaInterpreterService` in Phase 5** (it targets that surviving symbol) — design §6.4
  - [x] Rewrite `LuaSettingsSerializationTest.kt` (drop Phase-4-deleted-field round-trips) + add the
        stale-XML tolerance test (TC 14) — design §3.7, §6.4; rewrite `rocks/init` tests (TC 13)
- **Exit criteria (corrected)**: TC 13–14 pass; wizard provisions via `LuaToolProvisioner` + binds via
  TOOLING-02 (no `HererocksProvisioner`/`InterpreterMode` symbol in `rocks/init`);
  `grep -rn "newLuaInterpreterCommandLine" src/main` still has ONE residual pair
  (`LuaInterpreterService.kt` caller + `command/LuaCommandLine.kt` itself — both Phase-5); the
  Phase-4-deletable app fields + `LuaInterpreterComponent` gone while the Phase-5/6 deferrals (incl.
  `platform/LuaInterpreterService`/`LuaInterpreter`, `command/LuaCommandLine`, `tool/*`, `rocks/env/*`)
  still compile; suite green. NOTE: the full `grep -rnE "LuaInterpreter|InterpreterMode" src/main` = 0
  and `newLuaInterpreterCommandLine` = 0 gates belong to **Phase 5**, not Phase 4.

### Phase 5: Environments — matrix, widget, hererocks removal [Must] (requires TOOLING-04 UI)
- **Goal**: matrix + status-bar widget on TOOLING-02 environments; hererocks lifecycle gone.
- **Tasks**:
  - [x] Move + retype matrix (`rocks/matrix/`): `MatrixRunner` on `LuaEnvironmentState`
        + `resolveIn`, exec-service stream; `RunMatrixAction` on `environments(project)`;
        re-register action/toolWindow (§7.2). `BatchProvisionAction`/`BatchProvisionDialog`
        deleted (§6.1#18) — design §2.6, §3.6
  - [x] Move + rewire `LuaEnvStatusBarWidget(Factory)` → `toolchain.ui` on TOOLING-02
        state + `LuaToolchainListener.TOPIC`; re-register factory (§7.2) — design §2.7
  - [x] Delete `rocks/env/` hererocks classes (§6.1#17). (The `HererocksDetectStartup`
        postStartupActivity + `HererocksEnvGroup` actions block were already de-registered
        in Phase 4; only the RunMatrix id/class re-point remained.)
  - [x] **(absorbed Phase-4 deferrals)** Deleted `platform/LuaInterpreterService.kt`,
        `platform/LuaInterpreter.kt`, `command/LuaCommandLine.kt` (last reader
        `HererocksEnvBinder` died here); deleted `LuaProjectSettings.State`
        `interpreter`/`explicitInterpreter`/`explicitTarget`/`interpreterMode`(+`Migrated`)/
        `hererocksEnv(s)`/`activeEnvId`, the `InterpreterMode` enum, and the env-helper/mode
        machinery. **KEPT** `projectToolBindings`+`setProjectToolBindingAndNotify`,
        `rocksServerUrl`, `target`, and `LuaApplicationSettings` `toolInventory`/
        `globalToolBindings` (Phase-6, read by `tool/LuaToolManager`). Tidied dangling KDoc
        refs in `LuaRuntimeComboBox.kt` + `LuaInterpreterCommandLines.kt`.
  - [x] Rewrite `MatrixRunnerTest.kt` (→ `rocks/matrix/`, TC 12), `LuaEnvStatusBarWidgetTest.kt`
        (→ `toolchain/ui`); deleted the hererocks test suite + batch/glob/command deferred
        tests (§6.4) — replacement coverage confirmed in TOOLING-02
        (`LuaToolchainProjectSettingsTest`/`LuaToolResolverTest`/`LuaEnvironmentDetectorTest`) +
        TOOLING-04 (`LuaToolProvisionerTest`/`LuaToolchainActionRegistrationTest`) + exec
        (`LuaInterpreterCommandLinesTest`) + discovery (`LuaToolDiscoveryTest` glob parity).
        Retargeted `LuaToolchainActionRegistrationTest` + strengthened the TC 14 stale-XML
        test onto the now-real deleted tag names.
- **Exit criteria**: TC 12 passes; `grep -rin "hererocks" src/main` = 0;
  `grep -rnE "platform.LuaInterpreter|InterpreterMode|newLuaInterpreterCommandLine" src/main` = 0;
  matrix/widget re-registered under new packages (same IDs), RunMatrix under
  `Lunar.Toolchain.EnvironmentGroup`; `tool/*` + tool-state fields still compile (Phase 6);
  suite green.

### Phase 6: Final legacy removal + gates [Must]
- **Goal**: the `tool/` package and every residual legacy symbol deleted; grep gates hold.
- **Tasks**:
  - [x] **Migrated the 4 residual `LuaProcessUtil` callers** onto `LuaToolExecutionService`
        *before* deleting the util (the plan wrongly assumed all callers were cut in Phases 1–5):
        `toolchain/probe/LuaToolProbeImpl` (per-kind `timeoutMs` preserved via `captureWithMillis`;
        `PROCESS_TIMEOUT_EXCEPTION_CODE`→`TIMED_OUT`/"Timeout",
        `PROCESS_EXECUTION_EXCEPTION_CODE`→`START_FAILED`/"Not executable"),
        `rocks/browser/LuaRocksSearchService` (COMMAND, ×2), `rocks/publish/PublishRockAction`
        (NETWORK, with the task indicator), `rocks/RockspecBridge` (PROBE). No init cycle
        (probe captures a plain command line — no env-builder).
  - [x] Delete `tool/` entirely (§6.1#1-10) + plugin.xml removals §7.1
        (LuaToolManager service, health monitor/startup/notification provider,
        LuaToolsConfigurable) — accepted health-surface gap until TOOLING-07
  - [x] Delete `util/LuaProcessUtil.kt` (§6.1#15); delete `platform/Banner.kt` +
        `settings/TestBanner.kt` (their only readers died with `tool/`)
  - [x] Delete the Phase-6 legacy state fields: `LuaApplicationSettings.State`
        `toolInventory`/`globalToolBindings`; `LuaProjectSettings.State.projectToolBindings`
        + `setProjectToolBindingAndNotify` (sole reader `tool/LuaToolManager` deleted)
  - [x] Delete the legacy `tool/` test files (§6.4 rows 1–6) + `util/LuaProcessUtilTest.kt`
        (→ covered by `toolchain/exec/LuaToolExecutionServiceTest`); trim
        `LuaSettingsNotificationTest`/`LuaSettingsSerializationTest` off the deleted fields
        (deleted-field round-trips reshaped into stale-XML tolerance assertions, TC 14)
  - [x] Verify no residual consumers: TC 15 grep set over `src/main` + `META-INF` (incl.
        `LuaTerminalEnvironmentService`, design §2.9 — reworded the 2 KDoc-only refs);
        `lunar-terminal.xml` already points at `toolchain.terminal.LuaShellExecOptionsCustomizer`
  - [x] Update `CHANGELOG.md` (clean break: tools/interpreters must be re-registered,
        env re-provisioned once); `ktlintFormat ktlintCheck` on touched files
- **Exit criteria**: TC 15–16 pass; `run build` green (incl. :checkStatus/:lintDocs);
  live VNC bind→lint verification deferred to supervisor/TOOLING-06 (no UI-less bind path
  in-tree yet — requirements → Acceptance Criteria).

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| TOOLING-05-01 | M | Phase 1 |
| TOOLING-05-02 | M | Phase 1 |
| TOOLING-05-03 | M | Phase 1 |
| TOOLING-05-04 | M | Phase 2 |
| TOOLING-05-05 | M | Phase 3 |
| TOOLING-05-06 | M | Phase 5 |
| TOOLING-05-07 | M | Phase 4 |
| TOOLING-05-08 | M | Phases 3–6 (final: 6) |
| TOOLING-05-09 | M | Phase 4 |
| TOOLING-05-10 | M | Phases 1,2,5,6 (final: 6) |
| TOOLING-05-11 | M | every phase (final check: 6) |
| TOOLING-05-12 | S | Phase 6 |

## Verification Tasks
- [ ] Automated: TC 1–5 (Phase 1), TC 6–8 (Phase 2), TC 9–11 (Phase 3), TC 13–14
      (Phase 4), TC 12 (Phase 5) as light-fixture tests seeding registry/bindings.
- [ ] Grep gates TC 15 scripted in the Phase 6 commit message for reproducibility.
- [ ] Full suite via gce-builder after every phase; final `run build` (Phase 6).
- [ ] Debugger smoke in sandbox IDE after Phase 3 (DBGp attach unchanged).
- [ ] Live VNC verification of bind→lint→run→matrix flows after Phase 6
      (verify-in-ide skill) — the real-flow DoD gate.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Simple tool consumers | done | Must |
| Phase 2: LuaRocks consumers | done | Must |
| Phase 3: Lua runtime consumers | done | Must |
| Phase 4: Wizard + settings-state deletion | done | Must |
| Phase 5: Environments (matrix/widget/hererocks) | done | Must |
| Phase 6: Final legacy removal + gates | done | Must |
