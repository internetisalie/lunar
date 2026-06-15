---
id: ROCKS-04-PLAN
title: "Implementation Plan"
type: plan
parent_id: ROCKS-04
status: "planned"
priority: "high"
folders:
  - "[[features/rocks/04-task-execution/requirements|requirements]]"
---

# Implementation Plan: Task Execution & Run Configurations (ROCKS-04)

Implements `design.md` by cloning the existing `net.internetisalie.lunar.run.LuaRunConfiguration`
pattern for `luarocks`. Phases map to requirement IDs.

## Phase 1: Settings + Type Registration [Must] — ROCKS-04-01
- [ ] `LuaRocksSettings` app service (§2.1, clone of `LuaCheckSettings`) +
      `<applicationService>` registration.
- [ ] `LuaRocksRunConfigurationType` (id `LuaRocksRunConfiguration`) + `…Factory` (§2.2–2.3) +
      `<configurationType>` registration.
- [ ] `LuaRocksRunConfigurationOptions` with the exact `StoredProperty` tags in §2.4 / §4.1.
- [ ] Unit test: TC-ROCKS-04-03 (serialization round-trip).

## Phase 2: Configuration + Command Line [Must] — ROCKS-04-02/04/05/08
- [ ] `LuaRocksRunConfiguration` with typed accessors + the `EnvironmentVariablesData` bridge
      (clone of `LuaRunConfiguration`).
- [ ] `getState`/`startProcess` per §3.1 (`GeneralCommandLine`, flags→command→args→rockspec,
      `configureCommandLine(cmd, true)`, `createColoredProcessHandler`).
- [ ] Default `environmentProcess = "true"` for C-build env (§3.3, ROCKS-04-08).
- [ ] Unit test: TC-ROCKS-04-04 (argv assembly).

## Phase 3: Editor UI [Must/Should] — ROCKS-04-02/03/05
- [ ] `LuaRocksRunSettingsEditor` (`FormBuilder`): command `ComboBox` (editable,
      `LUAROCKS_COMMANDS`), arguments/global-flags `RawCommandLineEditor`, rockspec
      `TextFieldWithBrowseButton`, env `EnvironmentVariablesTextFieldWithBrowseButton`.
- [ ] `resetEditorFrom`/`applyEditorTo` wiring (clone of `LuaRunSettingsEditor`).

## Verification Tasks
- Unit: serialization round-trip (Phase 1), argv assembly (Phase 2).
- Manual: TC-ROCKS-04-01 (`make`), TC-ROCKS-04-05 (Before Launch), TC-ROCKS-04-06 (C build);
  see `human-verification-checklists.md`.
- Note: ROCKS-04-06 needs **no** code — the platform's built-in "Run Another Configuration"
  before-launch provider covers it once the type is registered (§3.2).
