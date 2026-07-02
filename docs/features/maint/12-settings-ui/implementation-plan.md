---
id: "MAINT-12-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-12"
folders:
  - "[[features/maint/12-settings-ui/requirements|requirements]]"
---

# MAINT-12: Implementation Plan

Coverage-only feature: all work is new test classes under `src/test/kotlin/...`. No production
source changes, no `plugin.xml` changes. Each phase = one test file.

## Phases

### Phase 1: Settings state round-trip (plain JUnit) [Must]
- **Goal**: Cover `LuaProjectSettings.State` (uncovered fields) + `LuaApplicationSettings.State`
  + `LuaCheckSettings` + `LuaRocksSettings` serialisation and defaults. Realizes MAINT-12-01,
  -02, -03.
- **File**: `src/test/kotlin/net/internetisalie/lunar/settings/LuaSettingsSerializationTest.kt`
  (plain JUnit, no fixture; mirror the style of `LuaProjectSettingsTest.kt`).
- **Test methods**:
  - `projectStateRoundTripsThroughLoadState` — TC-01 (populate `LuaProjectSettings.State`,
    `loadState`/`getState`, assert `rocksServerUrl`, `projectToolBindings`, `sourcePath`,
    `additionalGlobals`, `suppressUnderscorePrefixedGlobals`).
  - `projectStateRoundTripsThroughXmlSerializer` — TC-02 (`XmlSerializer.serialize` →
    `deserialize(LuaProjectSettings.State::class.java)`; assert the five fields).
  - `projectStateHasExpectedDefaults` — TC-03 (assert defaults incl.
    `PathConfiguration.DEFAULT_SOURCE_PATH`).
  - `applicationStateRoundTripsThroughLoadState` — TC-04 (assert `interpreters`,
    `globalToolBindings`, `includeAllFieldsInCompletions`, `enableTypeInference`; **not**
    `toolInventory` — already covered by `LuaToolManagerTest`).
  - `applicationStateRoundTripsThroughXmlSerializer` — TC-05 (whole-`State` round-trip;
    `toolInventory` size asserted incidentally since the `XmlSerializer` path is untested).
  - `applicationStateHasExpectedDefaults` — TC-06.
  - `luaCheckSettingsDefaults` — TC-07.
  - `luaCheckSettingsSetterPersists` — TC-08.
  - `luaRocksSettingsDefaults` — TC-09 (assert `serverUrl == ""`; the `executablePath`
    default is out of scope — already asserted at `TestLuaRocksRunConfiguration.kt:97`).
  - `luaRocksSettingsNotNullFallback` — TC-10 (`state.executablePath = null` → getter returns
    `DEFAULT_EXECUTABLE`).
- **Imports**: `com.intellij.util.xmlb.XmlSerializer`,
  `net.internetisalie.lunar.settings.{LuaProjectSettings, LuaApplicationSettings}`,
  `net.internetisalie.lunar.analysis.luacheck.LuaCheckSettings`,
  `net.internetisalie.lunar.rocks.run.LuaRocksSettings`,
  `net.internetisalie.lunar.platform.LuaInterpreter`,
  `net.internetisalie.lunar.lang.path.PathConfiguration`, `org.junit.jupiter.api.Test`,
  `kotlin.test.*`.
- **Exit criteria**: TC-01–TC-10 pass; file compiles.

### Phase 2: Notification + provider (BasePlatformTestCase) [Must / Should]
- **Goal**: Assert TOPIC publication and `PlatformLibraryProvider` output. Realizes
  MAINT-12-04 (Must) and MAINT-12-05 (Should).
- **File**:
  `src/test/kotlin/net/internetisalie/lunar/settings/LuaSettingsNotificationTest.kt`
  extending `com.intellij.testFramework.fixtures.BasePlatformTestCase`.
- **Test methods**:
  - `testSetTargetAndNotifyFiresTopic` — TC-11 (subscribe test listener to
    `LuaSettingsChangedListener.TOPIC` via `project.messageBus.connect(testRootDisposable)`;
    call `LuaProjectSettings.getInstance(project).setTargetAndNotify(...)` inside
    `EdtTestUtil.runInEdtAndWait`; assert flag true and `getState().getTarget()` matches).
  - `testSetProjectToolBindingAndNotifyFiresTopicAndMutatesState` — TC-12 (bind then clear;
    assert counter == 2 and map presence/absence).
  - `testGetSupportLibrariesReturnsPlatformLibraryForValidTarget` — TC-13
    (`setTargetAndNotify(Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4")))`; call
    `PlatformLibraryProvider().getSupportLibraries(project)` inside `runReadAction`; assert
    size 1 and `(single() as PlatformLibraryProvider.PlatformLibrary).getSourceRoots()`
    non-empty).
  - `testGetSupportLibrariesEmptyContractHolds` — TC-14 (call provider for a target whose
    library root does not resolve, or assert `size ≤ 1` and no throw per design §2.3).
- **Imports**: `com.intellij.testFramework.EdtTestUtil`,
  `com.intellij.openapi.application.runReadAction`,
  `net.internetisalie.lunar.settings.LuaSettingsChangedListener`,
  `net.internetisalie.lunar.settings.LuaProjectSettings`,
  `net.internetisalie.lunar.project.PlatformLibraryProvider`,
  `net.internetisalie.lunar.platform.{LuaPlatform}`,
  `net.internetisalie.lunar.platform.target.{Target, PlatformVersionRegistry, VersionEntry}`.
- **Exit criteria**: TC-11–TC-14 pass.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-12-01 | M | Phase 1 |
| MAINT-12-02 | M | Phase 1 |
| MAINT-12-03 | M | Phase 1 |
| MAINT-12-04 | M | Phase 2 |
| MAINT-12-05 | S | Phase 2 |

## Verification Tasks

- [ ] Phase 1: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaSettingsSerializationTest*"`.
- [ ] Phase 2: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaSettingsNotificationTest*"`.
- [ ] Full-suite regression: `tooling/gce-builder/gce-builder.sh run test` (must remain green
      relative to baseline).
- [ ] Lint touched files: `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"`.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Settings state round-trip | done | Must |
| Phase 2: Notification + provider | done | Must |
