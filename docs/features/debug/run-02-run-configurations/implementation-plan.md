---
id: "RUN-02-PLAN"
title: "Implementation Plan"
type: "plan"
status: "done"
parent_id: "RUN-02"
folders:
  - "[[features/debug/run-02-run-configurations/requirements|requirements]]"
---

# RUN-02: Implementation Plan

> **Status note**: the feature is already implemented in
> `src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt` and registered in
> `plugin.xml:406-407`. Implementation tasks below are checked `[x]`. Test/verification
> tasks are checked only where real coverage exists; uncovered tests are left `[ ]` and
> tracked in `risks-and-gaps.md`.

## Phases

### Phase 1: Configuration type & persistence [Must]
- **Goal**: A registered "Lua" run-configuration type with serialized options.
- **Tasks**:
  - [x] Create `LuaRunConfigurationType` (`run/LuaRunConfiguration.kt`) — realizes design §2.1.
  - [x] Create `LuaRunConfigurationFactory` — realizes design §2.2.
  - [x] Create `LuaRunConfigurationOptions` with all `StoredProperty` fields — realizes design §2.3.
  - [x] Register `<configurationType>` in `plugin.xml` — realizes design §7.
- **Exit criteria**: Type appears in the dialog; options round-trip (TC 1, TC 2).

### Phase 2: Execution state [Must]
- **Goal**: Launch a process from the configuration.
- **Tasks**:
  - [x] Implement `LuaRunConfiguration` typed accessors + `getOptions` — realizes design §2.4.
  - [x] Implement interpreter resolution getter — realizes design §3.4.
  - [x] Implement `getState` → `CommandLineState.startProcess` command assembly — realizes design §3.1.
  - [x] Wire `EnvironmentVariablesData` mapping + `configureCommandLine` — realizes design §3.5, §3.1 step 7.
  - [x] Implement `LUA_PATH` resolution (config → project fallback) — realizes design §3.3.
  - [x] Implement REPL fallback (`-v -i`) — realizes design §3.1 step 4.
- **Exit criteria**: Running a config streams interpreter output (TC 5, TC 6, TC 7, TC 9).

### Phase 3: Settings editor UI [Should]
- **Goal**: Editable form for all options.
- **Tasks**:
  - [x] Implement `LuaRunSettingsEditor` (FormBuilder rows, choosers, combo) — realizes design §2.5.
  - [x] Implement `resetEditorFrom` / `applyEditorTo` — realizes design §2.5.
- **Exit criteria**: All fields editable and persisted on apply (TC 10, manual; persistence
  round-trip also covered by TC 1).

### Phase 4: Debug executor integration [Should]
- **Goal**: Same config debuggable under the Debug executor.
- **Tasks**:
  - [x] Inject `mobdebug` preloader env under `DefaultDebugExecutor.EXECUTOR_ID` — realizes design §3.2.
- **Exit criteria**: Debug launch sets the three `LUNAR_*`/`LUA_INIT` env vars (TC 8).

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| RUN-02-01 | M | Phase 1 |
| RUN-02-02 | M | Phase 3 |
| RUN-02-03 | M | Phase 1 |
| RUN-02-04 | M | Phase 2 |
| RUN-02-05 | S | Phase 2 |
| RUN-02-06 | S | Phase 2 |
| RUN-02-07 | S | Phase 4 |
| RUN-02-08 | C | Phase 2 |
| RUN-02-09 | S | Phase 2 |

## Verification Tasks
- [x] Options persistence test — `TestLuaRunConfiguration.testOptionsPersistence` covers TC 1
      (`scriptName` / `workingDirectory` / `programArguments` round-trip).
- [ ] Type registration test — covers TC 2 (id/display/factory). *Not yet written (Gap 3.1).*
- [ ] Interpreter resolution tests — cover TC 3, TC 4. *Not yet written (Gap 3.1).*
- [ ] Command-line assembly tests (no-interpreter error, arg ordering, REPL fallback) —
      cover TC 5, TC 6, TC 7. *Not yet written (Gap 3.1).*
- [ ] Debug env injection test — covers TC 8. *Not yet written (Gap 3.1).*
- [ ] LUA_PATH resolution test — covers TC 9. *Not yet written (Gap 3.1).*
- [ ] Settings-editor manual check — covers **TC 10** (RUN-02-02): all editor rows present,
      editable, and persisted. Manual UI case; steps in
      [human-verification-checklists.md](human-verification-checklists.md) (TC 10).
      *Not yet performed.*
- [ ] Run the full [human-verification-checklists.md](human-verification-checklists.md)
      (settings editor TC 10 + live launch/debug in GoLand). *Not yet performed.*

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Configuration type & persistence | done | Must |
| Phase 2: Execution state | done | Must |
| Phase 3: Settings editor UI | done | Should |
| Phase 4: Debug executor integration | done | Should |
</content>
