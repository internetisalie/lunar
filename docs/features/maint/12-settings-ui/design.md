---
id: "MAINT-12-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-12"
folders:
  - "[[features/maint/12-settings-ui/requirements|requirements]]"
---

# Technical Design: MAINT-12 — Test Coverage: Settings & UI

This is a **test map**, not a production design. No production symbol is added or changed.
Every class/method/Topic named below was grep-verified to exist in this repo; `path:line`
citations follow each. Corrections to the original `requirements.md` stub are noted inline
(the stub referenced `LuaSettingsChangedEvent` and placed `LuaSettingsChangeListener` /
`PlatformLibraryProvider` under `settings/`; the real names/paths are used here).

## 1. Grounded Targets Under Test

### 1.1 `LuaProjectSettings` (project settings service)
`src/main/kotlin/net/internetisalie/lunar/settings/LuaProjectSettings.kt`
- Class: `@Service(Service.Level.PROJECT) @State(name="LuaProjectSettings", storages=[Storage("lunar.xml")])`,
  `PersistentStateComponent<State>` (`:14`–`:20`).
- `getState(): State` (`:119`), `loadState(state: State)` (`:123`).
- `fun setTargetAndNotify(newTarget: Target)` — calls `state.setTarget` then
  `project?.messageBus?.syncPublisher(LuaSettingsChangedListener.TOPIC)?.onSettingsChanged()`
  (`:134`–`:137`).
- `fun setProjectToolBindingAndNotify(typeName: String, toolId: String?)` — mutates
  `state.projectToolBindings`, publishes on the same TOPIC (`:144`–`:151`).
- `companion object.getInstance(project): LuaProjectSettings` (`:165`–`:169`).
- `State` fields under test: `sourcePath` (`:51`), `suppressUnderscorePrefixedGlobals` (`:52`),
  `additionalGlobals` (`:53`), `projectToolBindings: MutableMap<String,String>` (`:73`),
  `rocksServerUrl: String` (`:81`). `State.setTarget`/`getTarget` (`:102`–`:114`) already
  covered by `LuaProjectSettingsTest.kt` — not re-tested here.

### 1.2 `LuaApplicationSettings` (application settings service)
`src/main/kotlin/net/internetisalie/lunar/settings/LuaApplicationSettings.kt`
- Class: `@Service(Service.Level.APP) @State(name="LuaApplicationSettings", …)`,
  `PersistentStateComponent<State>` (`:29`–`:35`).
- `getState()` (`:58`), `loadState(state)` (`:62`).
- `State` fields under test: `includeAllFieldsInCompletions` (`:37`), `enableTypeInference`
  (`:38`), `interpreters: List<LuaInterpreter>` (`:39`), `globalToolBindings: MutableMap<String,String>`
  (`:53`). `toolInventory: MutableList<LuaTool>` (`:45`) is **already covered** by
  `LuaToolManagerTest.kt`'s `LuaApplicationSettingsToolInventoryTest` (`:53`, `loadState`
  round-trip) and is therefore excluded from the standalone `loadState`/`getState` assertion
  (it rides along only in the whole-object `XmlSerializer` round-trip, which that test does not
  exercise).

### 1.3 `LuaCheckSettings` (SimplePersistentStateComponent)
`src/main/kotlin/net/internetisalie/lunar/analysis/luacheck/LuaCheckSettings.kt`
- `class LuaCheckSettings : SimplePersistentStateComponent<State>(State())` (`:13`).
- `State : BaseState` with `executablePath by string("/usr/local/bin/luacheck")` (`:15`),
  `arguments by string("")` (`:16`).
- Getters `executablePath` / `arguments` use `StringUtil.notNullize` (`:19`–`:25`).

### 1.4 `LuaRocksSettings` (SimplePersistentStateComponent)
`src/main/kotlin/net/internetisalie/lunar/rocks/run/LuaRocksSettings.kt`
- `class LuaRocksSettings : SimplePersistentStateComponent<State>(State())` (`:28`).
- `State : BaseState` with `executablePath by string(DEFAULT_EXECUTABLE)` (`:30`),
  `serverUrl by string("")` (`:31`).
- Getters `executablePath` (`StringUtil.notNullize(…, DEFAULT_EXECUTABLE)`, `:34`) /
  `serverUrl` (`:38`). `companion.DEFAULT_EXECUTABLE = "luarocks"` (`:43`).

### 1.5 Change-notification channel
`src/main/kotlin/net/internetisalie/lunar/settings/LuaSettingsChangedEvent.kt`
- `interface LuaSettingsChangedListener { fun onSettingsChanged() }` (`:22`–`:27`).
- `companion object.TOPIC: Topic<LuaSettingsChangedListener> = Topic.create("lua.settings.changed", …)`
  (`:29`–`:32`).
- **Correction:** the stub called this `LuaSettingsChangedEvent` / `LuaSettingsChangeListener`;
  the interface is `LuaSettingsChangedListener` and the topic is `LuaSettingsChangedListener.TOPIC`.
- Existing subscriber precedent for the test's own subscriber:
  `LuaTerminalEnvironmentService` (`tool/LuaTerminalEnvironmentService.kt:40`–`:48`) connects
  via `project.messageBus.connect().subscribe(LuaSettingsChangedListener.TOPIC, object : …)`.

### 1.6 `PlatformLibraryProvider` (synthetic library registration)
`src/main/kotlin/net/internetisalie/lunar/project/PlatformLibraryProvider.kt`
- `class PlatformLibraryProvider : AdditionalLibraryRootsProvider()` (`:41`).
- `getAdditionalProjectLibraries(project): Collection<SyntheticLibrary>` (`:42`).
- `getSupportLibraries(project): Collection<SyntheticLibrary>` — delegates to
  `PlatformLibraryIndex.getPlatformLibrary(project)`; returns `emptyList()` when null, else a
  single `PlatformLibrary(level, folder)` (`:49`–`:52`).
- Inner `PlatformLibrary : SyntheticLibrary` with `getSourceRoots()` (`:83`).
- `object PlatformLibraryIndex.getPlatformLibrary(project): Pair<LuaLanguageLevel, VirtualFile>?`
  (`:111`–`:118`) resolves via `RuntimeLibraryProvider(project).getLibraryRoot(target)`.
- **Correction:** the stub placed this under `settings/`; it lives under `project/`.

### 1.7 Supporting data / registry types (already grounded, reused verbatim)
- `LuaProjectSettings.getInstance` / `LuaApplicationSettings.instance` accessors (above).
- `Target(platform, version)` + `PlatformVersionRegistry.getVersions/findVersion/defaultVersion`
  (`platform/target/`), used exactly as in `LuaProjectSettingsTest.kt:27`–`:29`.
- `LuaInterpreter(var path, …)` data class (`platform/LuaInterpreter.kt:11`).
- `LuaTool` — settable via no-arg constructor per its `@State`-serialised contract
  (`tool/LuaTool.kt`); construct minimally for collection round-trip (size + id assertions).

## 2. Test Approach Per Target

| Target | Harness | Technique |
|--------|---------|-----------|
| `LuaProjectSettings.State` (1.1) | Plain JUnit (no fixture) | Populate `State`, `loadState`/`getState` identity; `XmlSerializer.serialize`→`deserialize` field equality |
| `LuaApplicationSettings.State` (1.2) | Plain JUnit | Same round-trip; assert collection sizes + element fields |
| `LuaCheckSettings` (1.3) | Plain JUnit | Instantiate `LuaCheckSettings()`, read defaults, set, re-read |
| `LuaRocksSettings` (1.4) | Plain JUnit | Same, plus notnull-fallback via `state.executablePath = null` |
| Notify methods (1.1 + 1.5) | `BasePlatformTestCase` (needs a real `project` messageBus) | Subscribe test listener to TOPIC via `project.messageBus.connect()`; call notify-method on EDT; assert flag/counter |
| `PlatformLibraryProvider` (1.6) | `BasePlatformTestCase` (needs project) | Call `getSupportLibraries(project)` inside `runReadAction`; assert size + `getSourceRoots()` non-empty |

### 2.1 XmlSerializer round-trip (TC-02, TC-05)
Use the platform serializer already available on the test classpath:
```kotlin
val element = com.intellij.util.xmlb.XmlSerializer.serialize(original)
val restored = com.intellij.util.xmlb.XmlSerializer.deserialize(element, State::class.java)
```
Assert per-field equality. Precedent: `XmlSerializer`/state-serialisation tests exist under
`src/test/kotlin/...` (`LuaStubSerializationTest.kt`, `TestLuaRocksRunConfiguration.kt`).

### 2.2 TOPIC subscriber (TC-11, TC-12)
```kotlin
var fired = false
project.messageBus.connect(testRootDisposable).subscribe(
    LuaSettingsChangedListener.TOPIC,
    object : LuaSettingsChangedListener { override fun onSettingsChanged() { fired = true } },
)
```
`syncPublisher` in `setTargetAndNotify` delivers synchronously, so `fired` is observable
immediately after the call returns (no pumping needed). Run the notify call on the EDT via
`EdtTestUtil.runInEdtAndWait` (precedent: `LibraryLoadingAfterTargetChangeTest.kt:52`), because
`setTargetAndNotify` is used from EDT elsewhere and TC-11 also reads `getTarget()`.

### 2.3 PlatformLibraryProvider (TC-13, TC-14)
`getSupportLibraries` reads `LuaProjectSettings` state + VFS, so wrap in `runReadAction`. For
TC-13 set target to `Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4"))` via `setTargetAndNotify`
(same as `LibraryLoadingAfterTargetChangeTest`) so a real bundled library folder resolves; the
returned collection has size 1 and `(it as PlatformLibrary).getSourceRoots()` is non-empty. For
TC-14 use a target whose `RuntimeLibraryProvider.getLibraryRoot` returns null (a platform/label
with no bundled folder) and assert the collection is empty. Determine the empty case at
implementation time by picking the first `(platform, version)` from `PlatformVersionRegistry`
for which `PlatformLibraryIndex.getPlatformLibrary(project)` returns null; if every registry
target resolves, assert TC-14 via a spy is unnecessary — instead assert the `null →
emptyList()` contract by confirming `getSupportLibraries` never throws and returns `size ≤ 1`.

## 3. Fixtures & Test Data

- **No new fixture files.** All state objects are constructed in-code.
- **Light project** for TC-11–TC-14: `BasePlatformTestCase` (JUnit3-style) which provides
  `project`, `myFixture`, and `testRootDisposable`. This matches the CLAUDE.md guidance to
  prefer `BasePlatformTestCase` over hand-built mocks. `LibraryLoadingAfterTargetChangeTest`
  uses a manual `CodeInsightTestFixture`; either is acceptable — prefer `BasePlatformTestCase`
  for the new file to keep it light.
- **Fonts:** these tests do not initialise an editor color scheme, so the fontconfig caveat
  does not apply; the gce-builder harness has fonts installed regardless.

## 4. Current Coverage Gaps (why each test is new)

- `LuaProjectSettingsTest.kt` covers `State.getTarget/setTarget/TargetState` exhaustively and
  `getState/loadState` for `languageLevel`/`sourcePath`/`target`, but **not** `rocksServerUrl`,
  `projectToolBindings`, `additionalGlobals`, `suppressUnderscorePrefixedGlobals`, and does
  **no** `XmlSerializer` round-trip. → MAINT-12-01.
- `LuaApplicationSettings.State.toolInventory` **is already covered**: `LuaToolManagerTest.kt`
  contains `class LuaApplicationSettingsToolInventoryTest` (`:53`) which round-trips
  `toolInventory` via `loadState`/`getState` (`loadState round-trip preserves toolInventory`,
  `:109`; `toolInventory preserves tool IDs across loadState`, `:128`). No test round-trips the
  **other** application-settings fields (`interpreters`, `globalToolBindings`,
  `includeAllFieldsInCompletions`, `enableTypeInference`), and none exercises the
  `XmlSerializer` path for `LuaApplicationSettings.State`. → MAINT-12-02 targets only those
  genuinely-uncovered fields; `toolInventory` is asserted only incidentally as part of the
  whole-object `XmlSerializer` round-trip (which `LuaToolManagerTest` does not exercise), never
  as a standalone `loadState`/`getState` assertion.
- `LuaCheckSettings` / `LuaRocksSettings` `executablePath` is referenced in existing tests
  (`TestLuaNumeralAnnotator.kt:15`, `TestLuaAttributesParser.kt:16`, `WorkspaceBuildRunnerTest.kt:21`/`:43`),
  and `TestLuaRocksRunConfiguration.kt:97` already asserts the `"luarocks"` default. What is
  **not** covered is a *dedicated defaults / notNull-normalisation* test — the `serverUrl == ""`
  default and the `executablePath` notNull fallback have no assertion anywhere. →
  MAINT-12-03 covers exactly those (it does not restate the `:97` default).
- No test in `src/test` uses `messageBus` / `subscribe` / `syncPublisher`; the notify-methods'
  publication is unverified (`setTargetAndNotify` is called by other tests but only for its
  state side-effect, never asserting the callback). → MAINT-12-04.
- `PlatformLibraryProvider` is exercised indirectly by library-loading tests but never called
  at the provider API level to assert its returned `SyntheticLibrary` collection. → MAINT-12-05.

## 5. Registration / Extension Points

None. This feature adds only test classes under `src/test/kotlin/...`; it registers nothing in
`plugin.xml` and modifies no production code.

## Open Questions

None.
