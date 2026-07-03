---
id: "MAINT-03-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-03"
folders:
  - "[[features/maint/03-deprecation-cleanup/requirements|requirements]]"
---

# Technical Design: MAINT-03 — Deprecation Cleanup

## 1. Architecture Overview

### Current State
Four `@Deprecated`/soft-obsolete IntelliJ Platform API usages and one build-config drift remain:

| # | Site | Symbol | Status | Evidence |
|---|------|--------|--------|----------|
| A | `run/LuaDebugVariable.kt:81,83,90` | `DataManager.getDataContext()` (no-arg) | `@Deprecated` | `DataManager.java:46-49` |
| B | `run/LuaRunConfiguration.kt:313` | `createSingleLocalFileDescriptor()` | `@Deprecated` | `FileChooserDescriptorFactory.java:96-100` |
| B | `run/test/LuaTestRunConfiguration.kt:272` | `createSingleLocalFileDescriptor()` | `@Deprecated` | same |
| B | `rocks/run/LuaRocksRunConfiguration.kt:233` | `createSingleLocalFileDescriptor()` | `@Deprecated` | same |
| C | `run/LuaRunConfiguration.kt:318` | `createSingleFolderDescriptor()` | `@ApiStatus.Obsolete` | `FileChooserDescriptorFactory.java:126-130` |
| C | `run/test/LuaTestRunConfiguration.kt:277` | `createSingleFolderDescriptor()` | `@ApiStatus.Obsolete` | same |
| C | `tool/ui/LuaToolsConfigurable.kt:90` | `createSingleFileNoJarsDescriptor()` | `@ApiStatus.Obsolete` | `FileChooserDescriptorFactory.java:84-88` |
| D | `gradle/libs.versions.toml:10` | `intelliJPlatform = "2.5.0"` | outdated | — |
| E | `gradle.properties:36` vs `gradle-wrapper.properties:4` | `gradleVersion = 8.13` vs `gradle-8.14.4` | drift | `build.gradle.kts:133-135` |

### Prior Art in This Repo
No existing helper wraps `FileChooserDescriptorFactory` or `DataManager` — searched
`grep -rn "FileChooserDescriptor" src/main` (only the six call sites above) and
`grep -rn "DataManager" src/main` (only `LuaDebugVariable.kt`; `CoverageDataManager` in
`coverage/*` is an unrelated class). `LuaStackFrame` (`run/LuaStackFrame.kt:30-83`) already
owns the `Project` and is the sole constructor of `LuaDebugVariable`, so it is the natural
source for change A. This feature **edits** those existing classes in place; it creates **no
new production classes** and adds one new test. It **replaces** the old stub design/requirements,
which named a non-existent `DataConstants.PROJECT` symbol.

### Target State
Every targeted deprecated call is replaced by its non-deprecated equivalent, `LuaDebugVariable`
no longer touches `DataManager`, and the Gradle plugin/wrapper versions are current and
self-consistent. No behavior changes.

## 2. Core Components

All work is in-place edits to existing files; signatures below show the *post-change* state.

### 2.1 `net.internetisalie.lunar.run.LuaDebugVariable`
- **Responsibility**: XDebugger value node for a Lua local/upvalue; navigates to a variable's
  declaration on "Jump to source".
- **Threading**: Debugger compute thread; `computeSourcePosition` wraps the PSI declaration
  scan already present (unchanged from line 94 onward).
- **Collaborators**: `LuaStackFrame` (constructs it, supplies `Project`);
  `XDebuggerManager.getInstance(project).currentSession` (`XDebuggerManager` —
  `com.intellij.xdebugger.XDebuggerManager`, already imported at `LuaDebugVariable.kt:27`);
  `XDebuggerUtil` (already imported, line 28).
- **Key API** (constructor gains a trailing nullable `targetProject`; default keeps the 3-arg
  test callers valid):
  ```kotlin
  class LuaDebugVariable private constructor(
      name: String,
      private val parent: LuaDebugVariable?,
      private val value: LuaDebugValue,
      private val isIndex: Boolean,
      private val isLocal: Boolean,
      private val targetProject: Project?,
  ) : XNamedValue(name) {
      internal constructor(
          name: String,
          value: LuaDebugValue,
          isLocal: Boolean,
          targetProject: Project? = null,
      ) : this(name, null, value, false, isLocal, targetProject)

      override fun computeSourcePosition(navigatable: XNavigatable) { /* §3.1 */ }
  }
  ```
- **Removed imports**: `com.intellij.ide.DataManager` (line 19),
  `com.intellij.openapi.actionSystem.DataContext` (line 20),
  `com.intellij.openapi.actionSystem.PlatformDataKeys` (line 21).
- **Retained import**: `com.intellij.openapi.project.Project` (line 22).

### 2.2 `net.internetisalie.lunar.run.LuaStackFrame`
- **Responsibility**: Stack frame node; builds the local/upvalue `LuaDebugVariable` children.
- **Threading**: builds children inside `runReadAction { }` (`LuaStackFrame.kt:59`) — unchanged.
- **Change**: pass its `project` field (`LuaStackFrame.kt:31`, `val project: Project?`) as the
  new 4th argument at both construction sites (`LuaStackFrame.kt:61` and `:73`):
  ```kotlin
  LuaDebugVariable(it.name, LuaDebugValue(it.value, it.displayValue ?: "", AllIcons.Nodes.Variable), true,  project) // locals
  LuaDebugVariable(it.name, LuaDebugValue(it.value, it.displayValue ?: "", AllIcons.Nodes.Variable), false, project) // upvalues
  ```

### 2.3 `LuaDebugVariable` nested-table child propagation
`computeChildren` (`LuaDebugVariable.kt:59-68`) creates child `LuaDebugVariable`s via the
private 6-arg constructor. It must forward the parent's project:
```kotlin
LuaDebugVariable(
    name = key,
    parent = this,
    value = debugValue,
    isIndex = false,
    isLocal = true,
    targetProject = targetProject,
)
```

### 2.4 File-chooser call-site edits (`FileChooserDescriptorFactory`)
Pure argument swaps; the descriptor object is passed to the non-deprecated
`addBrowseFolderListener(project, descriptor)` (`TextFieldWithBrowseButton.java:55`) or
`FileChooser.chooseFile(...)` exactly as before.

| File:line | Before | After |
|-----------|--------|-------|
| `LuaRunConfiguration.kt:313` | `createSingleLocalFileDescriptor()` | `singleFileOrDir()` |
| `LuaRunConfiguration.kt:318` | `createSingleFolderDescriptor()` | `singleDir()` |
| `LuaTestRunConfiguration.kt:272` | `createSingleLocalFileDescriptor()` | `singleFileOrDir()` |
| `LuaTestRunConfiguration.kt:277` | `createSingleFolderDescriptor()` | `singleDir()` |
| `LuaRocksRunConfiguration.kt:233` | `createSingleLocalFileDescriptor()` | `singleFileOrDir()` |
| `LuaToolsConfigurable.kt:90` | `createSingleFileNoJarsDescriptor()` | `singleFile()` |

The imports (`import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory` at
`LuaRunConfiguration.kt:15`, `LuaTestRunConfiguration.kt:20`, `LuaRocksRunConfiguration.kt:20`,
`LuaToolsConfigurable.kt:5`) are **retained** — the terse methods are static members of the
same `FileChooserDescriptorFactory` class.

### 2.5 Build configuration (`gradle/libs.versions.toml`, `gradle.properties`)
- `gradle/libs.versions.toml:10`: `intelliJPlatform = "2.5.0"` → `intelliJPlatform = "2.17.0"`.
- `gradle.properties:36`: `gradleVersion = 8.13` → `gradleVersion = 8.14.4`.
  No change to `gradle/wrapper/gradle-wrapper.properties` (already `gradle-8.14.4-bin.zip`);
  the property is brought up to match the wrapper, not vice-versa (never downgrade).

## 3. Algorithms

### 3.1 `LuaDebugVariable.computeSourcePosition` — post-change control flow
- **Input → Output**: `(navigatable: XNavigatable)` → side effect: `navigatable.setSourcePosition(...)`
  set to the variable's declaration, or no-op / `super` fallback.
- **Steps**:
  1. `val currentProject: Project = targetProject ?: run { super.computeSourcePosition(navigatable); return }`
     — replaces the old `DataManager.getInstance().dataContext` → `PlatformDataKeys.PROJECT`
     lookup (`LuaDebugVariable.kt:81-90`). The `null` project branch preserves the exact
     `super`-fallback the old `dataContext == null` branch had. Note `super` here is
     `XValue.computeSourcePosition` (`XValue.java:86-88`) — `XNamedValue` does not override it —
     whose body is `navigatable.setSourcePosition(null)`. So the fallback is observable: it calls
     `setSourcePosition` once with `null` (it does not leave the navigatable untouched). This is the
     unchanged pre-existing behavior and is what TC 1 asserts.
  2. `val debugSession = XDebuggerManager.getInstance(currentProject).currentSession ?: return`
     — unchanged (was line 91).
  3. `val currentPosition = debugSession.currentPosition ?: return` — unchanged (was line 92).
  4. Lines 94-131 (context-element resolution, `LuaScopeProcessor` scope walk,
     `navigatable.setSourcePosition`) are **byte-for-byte unchanged**.
- **Rules / edge handling**: null `targetProject` → `super`; null session/position → early
  `return` (leaves navigatable untouched), identical to today.
- **Complexity / bounds**: unchanged — one upward PSI scope walk bounded by the enclosing file.

There are no other non-trivial algorithms: changes B–E are mechanical symbol/version swaps
whose replacements are proven behavior-identical in §1 (each terse factory method's body is the
descriptor the deprecated method returned; `FileChooserDescriptorFactory.java:17-27,84-88,126-130`).

## 4. External Data & Parsing
This feature consumes no CLI/text/file/network input. The only external artifacts read are the
Gradle version catalog and properties files, which are edited by literal string replacement
(§2.5), not parsed. Section intentionally otherwise empty.

## 5. Data Flow

### Example 1: Jump-to-source on a debug variable (change A)
1. User pauses at a breakpoint; `LuaStackFrame.computeChildren` builds
   `LuaDebugVariable("count", value, true, project)` (project now threaded, §2.2).
2. User right-clicks the `count` node → "Jump to source" → platform calls
   `computeSourcePosition(navigatable)`.
3. `targetProject` is non-null → `XDebuggerManager.getInstance(project).currentSession.currentPosition`
   → `LuaScopeProcessor("count")` walks scopes → `navigatable.setSourcePosition(declaration)`.
   Result identical to the pre-change path, minus the deprecated `DataManager` focus lookup.

### Example 2: Browsing for a script file (changes B/C)
1. User opens a Lua run configuration; `LuaRunConfiguration` init calls
   `scriptPathField.addBrowseFolderListener(project, singleFileOrDir())` (§2.4).
2. `singleFileOrDir()` returns `FileChooserDescriptor(true, true, false, false)` — the same
   descriptor `createSingleLocalFileDescriptor()` returned — so the chooser still permits a file
   or a directory. No user-visible change.

## 6. Edge Cases
- **Debug variable with no active session / detached project**: `targetProject == null` (e.g.
  the 3-arg test constructor) → `super.computeSourcePosition` (= `XValue.computeSourcePosition`,
  which invokes `setSourcePosition(null)`), no exception, no declaration navigation. Covered by TC 1.
- **Nested table children**: children created in `computeChildren` inherit the parent's
  `targetProject` (§2.3), so deep table nodes navigate too.
- **Terse factory method absent at 2026.1.3**: mitigated by the Phase-0 verification and the
  deterministic constructor fallback (requirements "Behavior Rules"); not an open question.
- **Gradle 8.14.4 vs plugin 2.17.0 compatibility**: IJPGP 2.x requires Gradle ≥ 8.5; satisfied.

## 7. Integration Points
No `plugin.xml` changes. All six edited classes are already-registered components
(`LuaRunConfiguration`/`LuaTestRunConfiguration`/`LuaRocksRunConfiguration` settings editors,
`LuaToolsConfigurable`, and the `LuaDebugVariable`/`LuaStackFrame` XDebugger nodes); their
registrations are untouched. No new extension points, IDs, groups, indexes, or settings keys.

```xml
<!-- plugin.xml: no changes required for MAINT-03 -->
```

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-03-01 | M | §2.1, §2.2, §2.3, §3.1 |
| MAINT-03-02 | M | §2.4 (three `singleFileOrDir()` rows) |
| MAINT-03-03 | S | §2.4 (`singleDir()` ×2, `singleFile()` ×1) |
| MAINT-03-04 | M | §2.5 (`libs.versions.toml`) |
| MAINT-03-05 | M | §2.5 (`gradle.properties`) |
| MAINT-03-07 | M | §11 §8-II (`runReadActionBlocking`) |
| MAINT-03-08 | M | §11 §8-III (retire `platform` prop) |
| MAINT-03-09 | M | §11 §9 (Group IV per-site table) |
| MAINT-03-10 | M | §11 (0 main-code warnings) |
| MAINT-03-06 | M | §3.1 (behavior-identical control flow) + §1 (proven-equivalent swaps) |

## 9. Alternatives Considered
- **Change A via `getDataContextFromFocusAsync()`**: the modern async `DataManager` replacement
  (`DataManager.java:66`) returns a `Promise<DataContext>`, incompatible with the synchronous
  `computeSourcePosition` contract and still an indirect way to fetch a project the frame already
  holds. Rejected in favor of threading the owned `Project`.
- **Change B via `singleFile()`**: the javadoc suggests `singleFile()`, but that returns
  `(true, false, false, false)` — it *disallows directories*, changing behavior. `singleFileOrDir()`
  `(true, true, false, false)` is the exact behavioral twin of `createSingleLocalFileDescriptor()`
  and is chosen to keep this a pure cleanup.
- **Change E by editing the wrapper down to 8.13**: rejected — never downgrade a working wrapper;
  bring the stale property up to the wrapper's 8.14.4 instead.

## 11. Expanded scope: Groups II–IV (full main-code deprecation cleanup)

### §8-II — Group II: `runReadAction {}` → `runReadActionBlocking {}` (MAINT-03-07)
Replace the deprecated free fn `com.intellij.openapi.application.runReadAction(() -> T)` with
`runReadActionBlocking(() -> T)` — the **non-cancellable blocking** equivalent (identical semantics,
non-deprecated name). **Not** `ReadAction.nonBlocking` (async/cancellable — a behaviour change). 14
sites (requirements §MAINT-03-07): swap the call + import, keep the lambda body verbatim.
`LuaChunkCompletion.kt:17` is a cluster — its `else` branch also calls deprecated
`ProgressIndicatorUtils.yieldToPendingWriteActions()` / `runInReadActionWithWriteActionPriority(...)`
(handled in Group IV / DR-02, or `@Suppress`-ed if no clean replacement). DR-01 pins the exact import.

### §8-III — Group III: DELETE internal `platform` property (MAINT-03-08)
`LuaProjectSettings.State.platform` (`:46`, `@Deprecated(..., ReplaceWith("target?.platform"))`) exists
only as the deserialization anchor for pre-`target` settings. **There is no installed user base**, so
no such legacy `.idea/lunar.xml` exists — the field is simply **deleted**:
1. Remove the `@Deprecated var platform: LuaPlatform` field (`:46`).
2. Rework `migrateFromLegacySettings()` (`:87–101`), which reads `platform` to look up a version, to
   build the default `Target` from `LuaPlatform.STANDARD` (the field's former default) + `languageLevel`;
   rename it to reflect that it now only constructs a fresh-state default (e.g. `buildDefaultTarget()`).
   `getTarget()`'s `target == null → build default → setTarget` path is otherwise unchanged.
3. Delete the `platform = newTarget.platform` writeback in `setTarget()` (`:112`); keep the
   `target`/`languageLevel` writes.
4. Migrate the 17 **test** callers (which set `state.platform = …` directly) to
   `setTarget(Target(platform, version))` / `getTarget()`.

Result: the field and all 22 references (5 main + 17 test) are gone; 0 warnings; no `@Suppress`
needed. `target` remains the single source of truth (with `languageLevel` derived via
`getImplicitLanguageLevel()`).

### §9-IV — Group IV: misc "Deprecated in Java" singleton replacements (MAINT-03-09)

| Site | Deprecated API | Replacement | Status |
|---|---|---|---|
| `lang/format/LuaCodeStyleSettings.kt:112,120` | `CodeStyleSettingsCustomizable.WRAP_OPTIONS`/`WRAP_VALUES` | `CodeStyleSettingsCustomizableOptions.getInstance().WRAP_OPTIONS`/`.WRAP_VALUES` (verified `CodeStyleSettingsCustomizable.java:328`) | **grounded** |
| `project/LuaSettingsChangeListener.kt:28` | `DaemonCodeAnalyzer.restart()` | `restart(reason: Any)` (non-deprecated, `DaemonCodeAnalyzer.java:64`), reason `"MAINT-03: settings changed"` | **grounded** |
| `lang/completion/templates/LuaTemplateContextType.kt:7` | `TemplateContextType(String id, String name)` | single-arg `TemplateContextType(name)` (`TemplateContextType.java:27`); confirm `"LUA"` contextId comes from the `templateContextType` EP in `plugin.xml` | grounded pending **DR-04** |
| `lang/LuaCompletionContributor.kt:44` | `TailType.SPACE` | likely `com.intellij.codeInsight.TailTypes.spaceType()` | **DR-02** |
| `lang/psi/LuaBaseElements.kt:160` | `StubBasedPsiElementBase.getElementType()` | likely `elementType` / `getIElementType()` | **DR-02** |
| `run/LuaDebugVariable.kt:83` | `DataManager.dataContext` (no-arg) | superseded by MAINT-03-01 (Project threaded from `LuaStackFrame`); the site is deleted, not replaced | grounded (via §2.1) |
| `lang/doc/LuaDocSearchEverywhereContributor.kt:111,112` | (two "Deprecated in Java" — API TBD) | determine from the exact call | **DR-03** |
| `coverage/report/LuaCovReportHighlight.kt:24,27` | `createTextAttributesKey(String, TextAttributes)` | non-deprecated overload, or `@Suppress` — sites deliberately hard-code colors for non-inheriting themes (in-code comment) | **DR-03** |

DR-gated rows resolve in Phase 0 (risks-and-gaps.md DR-02/03/04); where 261 offers no clean
replacement, the site is `@Suppress("DEPRECATION")`-ed with a one-line rationale (per MAINT-03-09).

## 10. Open Questions

None — deferred replacement verifications are tracked as de-risking tasks DR-01…DR-04 in risks-and-gaps.md and gate Phases 2/5/7 via Phase 0.
