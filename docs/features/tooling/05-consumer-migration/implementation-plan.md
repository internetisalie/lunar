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
  - [ ] Create `toolchain.ui.LuaRuntimeComboBox` implementing the §3.1 model algorithm
        (+ renderer port) — design §2.5, §3.1
  - [ ] Cut over `LuaRunConfiguration.kt` (interpreter prop, `resolveInterpreter` §3.2,
        startProcess factory + env builder, editor combo) — design §2.5, §3.2
  - [ ] Cut over `LuaTestRunConfiguration.kt` (+ lunity branch in
        `LuaTestCommandLineState.kt:108-131`) and
        `LuaTestRunConfigurationProducer.kt:53-55,74-76` — design §2.5
  - [ ] Cut over `LuaConsoleRunner.kt:37` and `RockspecBridge.kt:35` — design §2.5
  - [ ] Delete `command/LuaCommandLine.kt` (jar branch parity in TOOLING-03 verified
        first — design §9.1-b); split `LuaCommandLineTest.kt` — design §6.1#14, §6.4
  - [ ] Rewrite `TestLuaRunConfiguration.kt`; move
        `LuaInterpreterSearchPathGlobTest.kt` coverage to TOOLING-01 discovery tests —
        design §6.4
- **Exit criteria**: TC 9–11 pass; debugger smoke (DBGp attach via run config) still
  works in sandbox; `grep -rn "newLuaInterpreterCommandLine" src/main` = 0; suite green.

### Phase 4: Wizard + settings-state deletion [Must] (requires TOOLING-04)
- **Goal**: rocks/init provisioning + bindings on the new stack; legacy state fields gone.
- **Tasks**:
  - [ ] Migrate `LuaRocksProjectSettings.kt`, `LuaRocksInterpreterInitializer.kt`,
        `LuaRocksGeneratorPeer.kt` onto provisioner + RUNTIME binding — design §2.8
  - [ ] Delete legacy fields/functions from `LuaProjectSettings.kt` (§6.3 list, incl.
        `InterpreterMode` + both migrations) and `LuaApplicationSettings.kt` (§6.2 list)
  - [ ] Excise interpreter parts from `LuaApplicationSettingsPanel.kt` /
        `LuaProjectSettingsPanel.kt`; delete `settings/LuaInterpretersTable.kt` —
        design §6.3 (pages remain for TOOLING-06)
  - [ ] Delete `platform/LuaInterpreterService.kt`, `platform/LuaInterpreter.kt`,
        `platform/LuaInterpreterComponent.kt` after §3.5 parity check against
        TOOLING-01's kind data — design §6.1#11-13, §3.5
  - [ ] Rewrite `LuaSettingsSerializationTest.kt` + add the stale-XML tolerance test —
        design §3.7, §6.4; rewrite `rocks/init` tests — TC 13
- **Exit criteria**: TC 13–14 pass; `grep -rnE "LuaInterpreter|InterpreterMode" src/main` = 0;
  suite green.

### Phase 5: Environments — matrix, widget, hererocks removal [Must] (requires TOOLING-04 UI)
- **Goal**: matrix + status-bar widget on TOOLING-02 environments; hererocks lifecycle gone.
- **Tasks**:
  - [ ] Move + retype matrix (`rocks/matrix/`): `MatrixRunner` on `LuaEnvironmentState`
        + `resolveIn`, exec-service stream; `RunMatrixAction` on `environments(project)`;
        re-register action/toolWindow (§7.2) — design §2.6, §3.6
  - [ ] Move + rewire `LuaEnvStatusBarWidget(Factory)` → `toolchain.ui` on TOOLING-02
        state + `LuaToolchainListener.TOPIC`; re-register factory (§7.2) — design §2.7
  - [ ] Delete `rocks/env/` hererocks classes (§6.1#17-18) + their plugin.xml entries:
        `HererocksDetectStartup` postStartupActivity (:433-435) and the
        `HererocksEnvGroup` actions block (:643-668)
  - [ ] Rewrite `MatrixRunnerTest.kt`, `LuaEnvStatusBarWidgetTest.kt`; delete the
        hererocks test suite (§6.4 rows) with replacement coverage confirmed present
        in 02/04
- **Exit criteria**: TC 12 passes; `grep -rin "hererocks" src/main` = 0; suite green.

### Phase 6: Final legacy removal + gates [Must]
- **Goal**: the `tool/` package and every residual legacy symbol deleted; grep gates hold.
- **Tasks**:
  - [ ] Delete `tool/` entirely (§6.1#1-10) + plugin.xml removals §7.1
        (LuaToolManager service, health monitor/startup/notification provider,
        LuaToolsConfigurable) — note the accepted health-surface gap until TOOLING-07
  - [ ] Delete `util/LuaProcessUtil.kt` (all callers cut in Phases 1–5) — design §6.1#15
  - [ ] Delete the legacy `tool/` test files (§6.4 rows 1–6)
  - [ ] Verify no residual consumers: run the TC 15 grep set over `src/main` +
        `META-INF` (incl. `LuaTerminalEnvironmentService`, design §2.9); verify
        `lunar-terminal.xml` points at the TOOLING-03 customizer
  - [ ] Update `CHANGELOG.md` (clean break: tools/interpreters must be re-registered,
        env re-provisioned once); `ktlintFormat ktlintCheck` on touched files
- **Exit criteria**: TC 15–16 pass; `run build` green (incl. :checkStatus/:lintDocs);
  live VNC verification (requirements → Acceptance Criteria).

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
| Phase 3: Lua runtime consumers | todo | Must |
| Phase 4: Wizard + settings-state deletion | todo | Must |
| Phase 5: Environments (matrix/widget/hererocks) | todo | Must |
| Phase 6: Final legacy removal + gates | todo | Must |
