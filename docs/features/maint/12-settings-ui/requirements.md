---
id: MAINT-12
title: "MAINT-12: Test Coverage - Settings & UI Configuration"
type: feature
parent_id: MAINT
status: planned
priority: medium
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-12: Test Coverage - Settings & UI Configuration

## Overview

The plugin's configuration surface is a set of `PersistentStateComponent` services under
`settings/` (`LuaProjectSettings`, `LuaApplicationSettings`), `analysis/luacheck`
(`LuaCheckSettings`), and `rocks/run` (`LuaRocksSettings`), plus a single change-broadcast
channel (`LuaSettingsChangedListener.TOPIC`) whose only in-repo subscribers are
`LuaSettingsChangeListener` (`project/`) and `LuaTerminalEnvironmentService` (`tool/`).
Synthetic library registration derives from settings via `PlatformLibraryProvider`
(`project/`).

`LuaProjectSettings.State` is already heavily unit-tested by
`src/test/kotlin/net/internetisalie/lunar/settings/LuaProjectSettingsTest.kt` (target
migration / `TargetState` round-trips). The **coverage gaps** this feature closes, purely by
adding tests (no production change), are:

1. **Notification wiring** — `setTargetAndNotify` and `setProjectToolBindingAndNotify` publish
   on `LuaSettingsChangedListener.TOPIC`; no test asserts a subscriber actually receives the
   callback.
2. **`LuaApplicationSettings`** — `getState`/`loadState` for the `interpreters` /
   `globalToolBindings` / `includeAllFieldsInCompletions` / `enableTypeInference` fields, and
   any `XmlSerializer` round-trip of `State`, have zero tests. (`toolInventory` is already
   covered by `LuaToolManagerTest.LuaApplicationSettingsToolInventoryTest`, so it is excluded
   from the standalone `loadState`/`getState` assertion.)
3. **`SimplePersistentStateComponent` settings** — `LuaCheckSettings` and `LuaRocksSettings`
   getter/setter notnull-normalisation and `BaseState` defaults are untested.
4. **`PlatformLibraryProvider`** — the `SyntheticLibrary` collection it returns for a given
   target (support + external libraries) is untested at the provider level.

Parent epic: [MAINT](../requirements.md).

## Scope

### In Scope
* Unit tests asserting `LuaProjectSettings.setTargetAndNotify` and
  `setProjectToolBindingAndNotify` fire `onSettingsChanged()` on a subscriber connected to
  `LuaSettingsChangedListener.TOPIC`.
* Unit tests round-tripping `LuaApplicationSettings.State` through `getState`/`loadState`
  (`interpreters`, `globalToolBindings`, `includeAllFieldsInCompletions`, `enableTypeInference`
  — **not** `toolInventory`, already covered by
  `LuaToolManagerTest.LuaApplicationSettingsToolInventoryTest`) and through the IntelliJ
  `XmlSerializer` (serialize → deserialize) to prove no field is lost.
* Unit tests for `LuaCheckSettings` and `LuaRocksSettings` (`SimplePersistentStateComponent`):
  `BaseState` defaults, getter notnull-normalisation, setter persistence.
* Unit tests for `PlatformLibraryProvider.getSupportLibraries` /
  `getAdditionalProjectLibraries` returning a `PlatformLibrary` whose `getSourceRoots()` is
  non-empty for a valid target, and empty when no runtime library resolves.
* Unit tests for `LuaProjectSettings` per-project fields not already covered
  (`rocksServerUrl`, `projectToolBindings`) round-tripping through `getState`/`loadState`.

### Out of Scope
* Swing layout / rendering code inside panels and tables
  (`LuaProjectSettingsPanel`, `LuaInterpretersTable`, `*Configurable` `createComponent`) —
  beyond the state-binding logic already exercised by
  `LuaProjectSettingsPanelLogicTest`.
* Any change to production behaviour. This is a **coverage-only** feature.
* Re-testing `LuaProjectSettings.State` target migration / `TargetState` (already covered by
  `LuaProjectSettingsTest.kt`).
* Re-testing `LuaApplicationSettings.State.toolInventory` via a standalone `loadState`/`getState`
  assertion (already covered by
  `LuaToolManagerTest.LuaApplicationSettingsToolInventoryTest`). It is asserted only incidentally
  in the whole-object `XmlSerializer` round-trip, which that test does not exercise.
* Re-testing the `LuaCheckSettings` / `LuaRocksSettings` `executablePath` *default* (the
  `"luarocks"` default is already asserted at `TestLuaRocksRunConfiguration.kt:97`).
* End-to-end index rebuild / `DaemonCodeAnalyzer.restart()` side-effects of
  `LuaSettingsChangeListener.onSettingsChanged()` (requires a heavy platform harness and is
  already indirectly exercised by `LibraryLoadingAfterTargetChangeTest`); this feature asserts
  the **listener is invoked**, not the downstream reindex.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-12-01 | **Project state round-trip** | Must | planned | `LuaProjectSettings.getState`/`loadState` and `XmlSerializer` preserve `rocksServerUrl`, `projectToolBindings`, `sourcePath`, `suppressUnderscorePrefixedGlobals`, `additionalGlobals`. |
| MAINT-12-02 | **Application state round-trip** | Must | planned | `LuaApplicationSettings.getState`/`loadState` preserve `interpreters`, `globalToolBindings`, `includeAllFieldsInCompletions`, `enableTypeInference` (`toolInventory` already covered by `LuaToolManagerTest`); `XmlSerializer` preserves the whole `State` (incidentally including `toolInventory`). |
| MAINT-12-03 | **Simple settings defaults & normalisation** | Must | planned | `LuaCheckSettings` / `LuaRocksSettings` expose correct `BaseState` defaults and their getters notnull-normalise; setters persist to `state`. |
| MAINT-12-04 | **Change notification fires** | Must | planned | `setTargetAndNotify` and `setProjectToolBindingAndNotify` publish `onSettingsChanged()` on a `LuaSettingsChangedListener.TOPIC` subscriber. |
| MAINT-12-05 | **Library provider registration** | Should | planned | `PlatformLibraryProvider.getSupportLibraries` returns a `PlatformLibrary` with non-empty `getSourceRoots()` for a resolvable target, and empty when no library resolves. |

## Test Cases (Given / When / Then)

| TC | Req | Given | When | Then |
|----|-----|-------|------|------|
| TC-01 | 12-01 | A `LuaProjectSettings()` service and a `State` with `rocksServerUrl="redis://x"`, `projectToolBindings["LUACHECK"]="uuid-1"`, `sourcePath="/p"`, `additionalGlobals=["G"]`, `suppressUnderscorePrefixedGlobals=false` | `loadState(state)` then `getState()` | Returned state has all five field values unchanged |
| TC-02 | 12-01 | The same populated `LuaProjectSettings.State` | Serialize via `XmlSerializer.serialize(state)` then `XmlSerializer.deserialize(el, State::class.java)` | Deserialized `State` has equal `rocksServerUrl`, `projectToolBindings`, `sourcePath`, `additionalGlobals`, `suppressUnderscorePrefixedGlobals` |
| TC-03 | 12-01 | A default `LuaProjectSettings.State()` | Read defaults | `rocksServerUrl == ""`, `projectToolBindings` empty, `sourcePath == PathConfiguration.DEFAULT_SOURCE_PATH`, `suppressUnderscorePrefixedGlobals == true`, `additionalGlobals` empty |
| TC-04 | 12-02 | A `LuaApplicationSettings()` and a `State` with one `LuaInterpreter(path="/usr/bin/lua")`, `globalToolBindings["LUACHECK"]="uuid-2"`, `includeAllFieldsInCompletions=true`, `enableTypeInference=false` | `loadState(state)` then `getState()` | Returned state preserves `interpreters`, `globalToolBindings`, and both booleans (`toolInventory` is out of scope here — covered by `LuaToolManagerTest`) |
| TC-05 | 12-02 | The same populated `LuaApplicationSettings.State` (plus one `LuaTool` in `toolInventory`) | `XmlSerializer.serialize` → `deserialize(State::class.java)` | Deserialized state's `interpreters[0].path == "/usr/bin/lua"`, `globalToolBindings` size preserved, booleans preserved; `toolInventory` size preserved incidentally (the `XmlSerializer` path is not exercised elsewhere) |
| TC-06 | 12-02 | A default `LuaApplicationSettings.State()` | Read defaults | `includeAllFieldsInCompletions == false`, `enableTypeInference == true`, `interpreters` empty, `toolInventory` empty, `globalToolBindings` empty |
| TC-07 | 12-03 | A fresh `LuaCheckSettings()` (via `SimplePersistentStateComponent`) | Read `executablePath` and `arguments` getters | `executablePath == "/usr/local/bin/luacheck"`, `arguments == ""` (notnull-normalised) |
| TC-08 | 12-03 | A fresh `LuaCheckSettings()` | `executablePath = "/x/luacheck"`; then read getter and `getState().executablePath` | Getter and state both return `"/x/luacheck"` |
| TC-09 | 12-03 | A fresh `LuaRocksSettings()` | Read `serverUrl` getter | `serverUrl == ""` (the `executablePath == "luarocks"` default is out of scope — already asserted at `TestLuaRocksRunConfiguration.kt:97`) |
| TC-10 | 12-03 | A fresh `LuaRocksSettings()` with `state.executablePath = null` (blank) | Read `executablePath` getter | Returns `DEFAULT_EXECUTABLE` (notnull fallback) |
| TC-11 | 12-04 | A light project fixture; a subscriber connected to `LuaSettingsChangedListener.TOPIC` recording a boolean flag | `LuaProjectSettings.getInstance(project).setTargetAndNotify(Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4")))` on EDT | The subscriber's `onSettingsChanged()` ran (flag true); `getState().getTarget()` reflects the new target |
| TC-12 | 12-04 | A light project fixture; a `TOPIC` subscriber counting invocations | `setProjectToolBindingAndNotify("LUACHECK", "uuid-3")` then `setProjectToolBindingAndNotify("LUACHECK", null)` | Subscriber invoked twice; `getState().projectToolBindings` has the key after the first call and lacks it after the second |
| TC-13 | 12-05 | A light project fixture with default target (STANDARD 5.4) | `PlatformLibraryProvider().getSupportLibraries(project)` on EDT+read action | Returns exactly one `PlatformLibrary` whose `getSourceRoots()` is non-empty |
| TC-14 | 12-05 | A light project fixture whose target resolves to no runtime library root (target label with no library folder) | `PlatformLibraryProvider().getSupportLibraries(project)` | Returns an empty collection (no crash) |

## Acceptance Criteria

* **AC-12-01**: TC-01–TC-03 pass — project state survives `loadState`/`getState` and
  `XmlSerializer` round-trips with correct defaults.
* **AC-12-02**: TC-04–TC-06 pass — application state (collections + booleans) survives
  round-trip with correct defaults.
* **AC-12-03**: TC-07–TC-10 pass — simple settings defaults and notnull normalisation hold.
* **AC-12-04**: TC-11–TC-12 pass — both notify-methods reach a `TOPIC` subscriber.
* **AC-12-05**: TC-13–TC-14 pass — `PlatformLibraryProvider` yields the expected synthetic
  libraries and degrades to empty gracefully.
