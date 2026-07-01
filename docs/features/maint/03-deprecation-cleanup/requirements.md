---
id: "MAINT-03"
title: "MAINT-03: Deprecation Cleanup"
type: "feature"
status: "planned"
priority: "low"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-03: Deprecation Cleanup

## Overview

Remove the plugin's remaining usages of `@Deprecated` IntelliJ Platform APIs and modernize
the two soft-obsolete `FileChooserDescriptorFactory` call sites, then update the
`org.jetbrains.intellij.platform` Gradle plugin from `2.5.0` to `2.17.0` and reconcile the
Gradle wrapper version so `./gradlew wrapper` no longer downgrades the wrapper. This is a
behavior-preserving maintenance chore under the [MAINT epic](../requirements.md): no
user-visible feature changes, only removal of deprecation warnings and build modernization.

## Scope

### In Scope
- Removing the single deprecated `DataManager.getDataContext()` usage in
  `LuaDebugVariable.computeSourcePosition` by threading the `Project` from the owning
  `LuaStackFrame`.
- Replacing the three `@Deprecated` `FileChooserDescriptorFactory.createSingleLocalFileDescriptor()`
  call sites with the terse modern factory method `singleFileOrDir()`.
- Replacing the three `@ApiStatus.Obsolete` `createSingleFolderDescriptor()` /
  `createSingleFileNoJarsDescriptor()` call sites with `singleDir()` / `singleFile()`.
- Bumping the IntelliJ Platform Gradle Plugin version in `gradle/libs.versions.toml`
  from `2.5.0` to `2.17.0`.
- Reconciling `gradle.properties` `gradleVersion` (`8.13`) with the actual wrapper
  distribution (`gradle-8.14.4`).

### Out of Scope
- A repository-wide sweep of *all* deprecated APIs. The codebase predates real linting and
  carries many pre-existing warnings; this feature targets only the three named API families
  plus the build config. Other deprecations are tracked separately under MAINT.
- `DataConstants` removal: **there are zero `DataConstants` usages** in `src/main`
  (`grep -rn "DataConstants" src/main` → 0 hits). The old stub named this symbol in error;
  nothing to remove.
- The `DataContext` *parameter* overrides (`LuaEnterHandler`, `LuaEnterHandlerDelegate`,
  `LuaEnterBetweenBlockHandler`, `LuaTypeHierarchyProvider`, `LuaIntroduceVariableHandler`):
  these receive `DataContext` from the platform as a method argument and call the non-deprecated
  `CommonDataKeys.*.getData(...)` idiom. They are **not** deprecated and are not touched.
- Bumping the IDE `platformVersion`/`testVersion` (`2026.1.3`). Only the *Gradle plugin*
  version changes; the compiled-against SDK stays at `2026.1.3`.
- Migrating `addBrowseFolderListener(project, descriptor)`: this 2-arg overload is the modern,
  non-deprecated API (`TextFieldWithBrowseButton.java:55`). Only the descriptor argument changes.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| MAINT-03-01 | **Remove deprecated `DataManager.getDataContext()`** | M | `LuaDebugVariable.computeSourcePosition` must obtain the `Project` from the owning `LuaStackFrame` instead of the deprecated no-arg `DataManager.getDataContext()`; the `DataManager`/`DataContext`/`PlatformDataKeys` imports and usages are removed. |
| MAINT-03-02 | **Replace `@Deprecated` file-chooser factory** | M | All three `FileChooserDescriptorFactory.createSingleLocalFileDescriptor()` calls are replaced with the behavior-identical `singleFileOrDir()`. |
| MAINT-03-03 | **Modernize obsolete file-chooser factory** | S | The `createSingleFolderDescriptor()` (×2) and `createSingleFileNoJarsDescriptor()` (×1) calls are replaced with `singleDir()` / `singleFile()`. |
| MAINT-03-04 | **Update IntelliJ Platform Gradle Plugin** | M | `intelliJPlatform` in `gradle/libs.versions.toml` is `2.17.0`; `gce-builder run build` succeeds. |
| MAINT-03-05 | **Reconcile Gradle wrapper version** | M | `gradle.properties` `gradleVersion` matches the wrapper's `distributionUrl` Gradle version so the `wrapper` task no longer downgrades. |
| MAINT-03-06 | **No behavior regression** | M | The full unit-test suite remains green; run-config editors and debug-variable navigation behave exactly as before. |

## Detailed Specifications

### MAINT-03-01: Remove deprecated `DataManager.getDataContext()`
Today `LuaDebugVariable.computeSourcePosition` (`src/main/.../run/LuaDebugVariable.kt:80-90`)
obtains the current `Project` via the `@Deprecated` no-arg
`DataManager.getInstance().dataContext` (deprecated at
`intellij-community/platform/editor-ui-api/src/com/intellij/ide/DataManager.java:46-49`), then
reads `PlatformDataKeys.PROJECT`. The owning `LuaStackFrame` already holds the `Project`
(`LuaStackFrame.kt:31`, `val project: Project?`) and constructs every `LuaDebugVariable`
(`LuaStackFrame.kt:61,73`). The `Project` must be threaded through the `LuaDebugVariable`
constructor as a nullable trailing parameter (default `null`, to keep the existing 3-arg
callers in `TestLuaDebugVariable` compiling) and used directly. When the project is `null`,
`computeSourcePosition` falls back to `super.computeSourcePosition(navigatable)` — the same
graceful degradation the current `dataContext == null` branch provides.

### MAINT-03-02: Replace `@Deprecated` file-chooser factory
`FileChooserDescriptorFactory.createSingleLocalFileDescriptor()` is `@Deprecated`
(`FileChooserDescriptorFactory.java:96-100`) and returns
`new FileChooserDescriptor(true, true, false, false)`. The modern `singleFileOrDir()`
(`FileChooserDescriptorFactory.java:25-27`) returns the identical descriptor
`(true, true, false, false)`, so it is a **behavior-preserving** drop-in. The three sites are:
`LuaRunConfiguration.kt:313`, `LuaTestRunConfiguration.kt:272`, `LuaRocksRunConfiguration.kt:233`.

### MAINT-03-03: Modernize obsolete file-chooser factory
`createSingleFolderDescriptor()` (`FileChooserDescriptorFactory.java:126-130`, returns
`singleDir()`) and `createSingleFileNoJarsDescriptor()`
(`FileChooserDescriptorFactory.java:84-88`, returns `singleFile()`) are `@ApiStatus.Obsolete`
(IDE strikethrough, no compiler deprecation warning). They are replaced with the exact
delegates they already return: `singleDir()` (`LuaRunConfiguration.kt:318`,
`LuaTestRunConfiguration.kt:277`) and `singleFile()` (`LuaToolsConfigurable.kt:90`).

### MAINT-03-04: Update IntelliJ Platform Gradle Plugin
`gradle/libs.versions.toml:10` currently pins `intelliJPlatform = "2.5.0"`; it becomes
`"2.17.0"`. The IntelliJ Platform Gradle Plugin 2.x requires Gradle ≥ 8.5; the reconciled
wrapper (`8.14.4`, MAINT-03-05) satisfies this.

### MAINT-03-05: Reconcile Gradle wrapper version
`gradle/wrapper/gradle-wrapper.properties:4` already resolves
`gradle-8.14.4-bin.zip`, but `gradle.properties:36` declares `gradleVersion = 8.13`, and the
`wrapper` task (`build.gradle.kts:133-135`) sets
`gradleVersion = providers.gradleProperty("gradleVersion")`. Running the `wrapper` task
therefore *downgrades* the wrapper to 8.13. The fix sets `gradle.properties`
`gradleVersion = 8.14.4` so the property and the resolved wrapper agree, making the `wrapper`
task idempotent.

## Behavior Rules
- **Behavior preservation is mandatory.** Every API swap chosen in this feature returns a
  descriptor / project identical to the one it replaces; no run-config browse dialog, debug
  navigation, or tool-chooser behavior may change.
- **Fallback determinism.** If the resolved 2026.1.3 SDK lacks the terse
  `singleFileOrDir()`/`singleDir()`/`singleFile()` methods (verified in Phase 0), the
  implementer substitutes the non-deprecated constructor
  `FileChooserDescriptor(true, true, false, false)` / `(false, true, false, false)` /
  `(true, false, false, false)` respectively — no other choice is permitted.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | MAINT-03-01 | `LuaDebugVariable("x", LuaDebugValue("number","42",null), true)` (3-arg call, project defaults to `null`) + a fake `XNavigatable` recording `setSourcePosition` calls | Invoke `computeSourcePosition(fakeNavigatable)` | No exception; the null-project branch delegates to `super.computeSourcePosition`, which resolves to `XValue.computeSourcePosition` (`XValue.java:86-88` — `XNamedValue` does not override it) and calls `setSourcePosition` **exactly once with `null`** (no navigation to a declaration occurs); the class no longer references `DataManager`. |
| 2 | MAINT-03-01 | Source file `src/main/.../run/LuaDebugVariable.kt` | `grep -nE "DataManager|com\.intellij\.openapi\.actionSystem\.DataContext|PlatformDataKeys" LuaDebugVariable.kt` | 0 matches. |
| 3 | MAINT-03-01 | `LuaDebugVariable("x", value, true, myFixture.project)` (new 4-arg constructor) | Read `variable.name` | Equals `"x"` (new trailing `Project?` parameter accepted; existing 3-arg constructor still compiles). |
| 4 | MAINT-03-02 | `src/main` tree | `grep -rnE "createSingleLocalFileDescriptor\(\)|createSingleFileDescriptor\(\)" src/main` | 0 matches; the three editors compile with `singleFileOrDir()`. |
| 5 | MAINT-03-03 | `src/main` tree | `grep -rnE "createSingleFolderDescriptor|createSingleFileNoJarsDescriptor" src/main` | 0 matches; replaced by `singleDir()` / `singleFile()`. |
| 6 | MAINT-03-04 | `gradle/libs.versions.toml` | Read line 10 | `intelliJPlatform = "2.17.0"`; `gce-builder run build` prints `BUILD SUCCESSFUL`. |
| 7 | MAINT-03-05 | `gradle.properties` + `gradle/wrapper/gradle-wrapper.properties` | Compare `gradleVersion` and the wrapper `distributionUrl` version | Both reference `8.14.4`; re-running the `wrapper` task leaves `gradle-wrapper.properties` unchanged (no downgrade). |
| 8 | MAINT-03-06 | Full unit suite | `tooling/gce-builder/gce-builder.sh run test` | 0 failures; `TestLuaDebugVariable`, `TestLuaStackFrame`, `TestLuaRunConfiguration` still pass. |

## Acceptance Criteria
- [ ] MAINT-03-01: `LuaDebugVariable` compiles with no `DataManager`/`DataContext`/`PlatformDataKeys`
      references; TC 1–3 pass.
- [ ] MAINT-03-02: TC 4 passes; the three run-config editors use `singleFileOrDir()`.
- [ ] MAINT-03-03: TC 5 passes; the two folder/no-jars sites use `singleDir()`/`singleFile()`.
- [ ] MAINT-03-04: TC 6 passes; build green on `2.17.0`.
- [ ] MAINT-03-05: TC 7 passes; wrapper task is idempotent.
- [ ] MAINT-03-06: TC 8 passes; the manual browse-button and jump-to-source checks in
      [human-verification-checklists.md](human-verification-checklists.md) (CL1–CL5) all pass —
      choosers still open and accept the same file/dir selections, debug jump-to-source still navigates.
- [ ] `src/main` compiles with no *new* deprecation warnings for the three targeted API families.

## Non-Functional Requirements
- **Threading**: `LuaDebugVariable.computeSourcePosition` runs on the debugger's compute thread
  as before; the change removes an EDT-focused `DataManager` focus lookup and reads the already-held
  `Project` reference, so no new EDT/read-action constraints are introduced.
- **Memory**: The threaded `Project?` lives only on transient, session-scoped `LuaDebugVariable`
  value nodes (recreated per `computeChildren`), not on any long-lived service — consistent with
  the engineering contract's heavy-object-retention rule.
- **Build**: No change to `pluginSinceBuild` (261) or the compiled SDK version (2026.1.3).

## Dependencies
- Parent epic: [MAINT: Maintenance & Refactoring](../requirements.md).
- No feature dependencies; independent of other Wave-12 MAINT items.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Manual checklists: [human-verification-checklists.md](human-verification-checklists.md)
