---
id: "MAINT-44-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-44"
folders:
  - "[[features/maint/44-verifier-reported-api-usages/requirements|requirements]]"
---

# MAINT-44: Implementation Plan

Phase 1 ships on its own: it removes usages and changes no gate. Phase 2 depends on Phase 1,
because its gate fails while any replaceable usage remains. Both phases can go in one
PR. Every code change is in design.md. First, on the rebased branch, re-run DR-02's baseline
`verifyPlugin` and record the counts (risks-and-gaps, "Re-measure at implementation").

## Phases

### Phase 1: Public replacements [Must]
- **Goal**: the replaceable usages are gone (MAINT-44-01 to -06).
- **Tasks**:
  - [x] Add `LuaCoverageRunner.getInstance()` and switch its call sites — design §2.1.
  - [x] Create `lang/doc/LuaTargetPresentation.kt` and switch both `computePresentation`s — design §2.2.
  - [x] Call `provision` directly in `scheduleProvision`, and update the class KDoc — design §2.3.
  - [x] Replace the `pluginVirtualDirectory` getter — design §2.4.
  - [x] Use `ActionUtil.performAction` in `invokeProvisionAction` — design §2.5.
  - [x] Add `LuaCovReportImportAction.reportFileDescriptor()` and use it — design §2.6.
  - [x] Tests (each with the input, expectation and mutation of its requirements.md row):
    - TC-01: new `src/test/kotlin/net/internetisalie/lunar/coverage/LuaCoverageRunnerTest.kt`
      (`BasePlatformTestCase`).
    - TC-02: new `src/test/kotlin/net/internetisalie/lunar/lang/doc/LuaTargetPresentationTest.kt`
      (`BasePlatformTestCase`; elements found with `PsiTreeUtil.findChildOfType`, first of each type;
      register the colour provider in `setUp` exactly as TC-02 states, so the colour is non-null).
    - TC-03: a new method in `src/test/kotlin/net/internetisalie/lunar/rocks/init/LuaRocksInterpreterInitializerTest.kt`.
      Reserve `FileUtil.toCanonicalPath(File("/tmp/wizard-proj/.lua").absolutePath)` with
      `LuaToolProvisioner.getInstance().tryReserve(...)` before the call, ignoring its return value
      (a reservation left by another test leads to the same balloon), and `release(...)` it in a
      `finally`. Capture balloons as `LuaRedisFlavorWarningTest.captureBalloons` does (`:56-67`).
    - TC-04: a new method in `src/test/kotlin/net/internetisalie/lunar/util/LuaFileUtilTest.kt`.
    - TC-06: a new method in `src/test/kotlin/net/internetisalie/lunar/coverage/LuaCoverageReportTest.kt`,
      using `com.intellij.testFramework.LightVirtualFile`.
- **Exit criteria**: TC-01 to TC-04 and TC-06 pass. `test --rerun` has no failure other than BUG-529.
  `verifyPlugin` (still on the old gate) reports `2 usages of internal API` per IDE and no
  override-only or non-extendable clause (TC-10's counts).

### Phase 2: Gate [Must]
- **Goal**: new usages fail `verifyPlugin` (MAINT-44-07 to -09).
- **Tasks**:
  - [x] Replace the `failureLevel` block and its comment — design §2.8.
  - [x] Add the `verifyPlugin { doLast { … } }` block — design §2.9.
  - [x] Add `gradle/verifier-internal-api-allowlist.txt` verbatim — design §2.9.
  - [x] Add a `CHANGELOG.md` entry with the `changelog` skill: the tag build now fails on new
        internal, override-only or non-extendable platform API usage.
- **Exit criteria**: TC-10 passes. TC-08, TC-09 and TC-11 (both variants) were run as mutations and
  then reverted, and each log shows the expected `BUILD FAILED` text.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-44-01 to -06 | M | Phase 1 |
| MAINT-44-07, -08, -09 | M | Phase 2 |
| MAINT-44-10, -11 | W | not delivered (requirements.md says why) |

## Verification Tasks
- [x] `tooling/gce-builder/gce-builder.sh run "test --rerun"` (or `builder-run`): no failure other
      than BUG-529's `LuaRenameConflictTest.testCancellationIsCheckedPerFileNameRefNotPerCollisionsCall`.
- [x] `verifyPlugin`: TC-10. Then the TC-08, TC-09 and TC-11 mutations, and a revert. The logs are
      in `~/.cache/claude-scratch/lunar/maint-44/impl/` and cited in the PR.
- [x] `ktlintCheck`. Format with `ktlintFormat` on the VM and pull the result back, as the project
      guide describes; never run `ktlintFormat ktlintCheck` together.
- [x] Sandbox IDE checks, on `runIde` (2026.2.3); HV-02 and HV-03 again on `runIde261` (2026.1.3,
      the floor) — risks-and-gaps "Premises examined":
  - **HV-01** (MAINT-44-05): open a project, click the Lua environment status-bar widget, and
    choose the add-environment item. The *Provision Lua Toolchain* dialog opens.
  - **HV-02** (Risk 1.2): debug a Lua script with a Lua run configuration. The session starts, with
    no "Failed to locate plugin directory" or "Failed to locate debugger preloader" error.
  - **HV-03** (Risk 1.1): *New Project → LuaRocks* with environment provisioning on. Provisioning
    runs to completion, the status-bar widget shows the new environment, and no "found … but not
    registered" balloon appears.
  - **HV-04** (MAINT-44-06): *Analyze → Import LuaCov Report…*. A `.out` file can be chosen; a
    `.lua` file is not offered (design §2.6, visibility change).
  - **HV-05** (MAINT-44-02): Quick Documentation on a `---@class` local. The popup renders.
  - Result: all pass, HV-04 after the BUG-540 fix (before it, `main.lua` was imported as coverage).
    Screenshots in `~/.cache/claude-scratch/lunar/maint-44/impl/hv/`.
- [x] When the feature closes, delete its row from `docs/roadmap.md`.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Public replacements | done | Must |
| Phase 2: Gate | done | Must |
