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

Remove the plugin's **`src/main` usages of `@Deprecated` IntelliJ Platform APIs** and modernize the
build configuration. A `compileKotlin compileTestKotlin --no-build-cache --rerun-tasks` run on
GoLand 2026.1 (261) emits **279 `is deprecated` warnings — 33 in `src/main`, 246 in `src/test`**.
This feature covers the **33 main-code sites** plus **retiring Lunar's own deprecated
`LuaProjectSettings.State.platform` property**, and keeps the build-modernization work (Gradle-plugin
bump + wrapper reconcile). The 246 test-code idiom deprecations (dominated by 105
`runInEdtAndWait(ThrowableRunnable)` + 118 `runReadAction {}`, the pattern `.agents/AGENTS.md`
documents for tests) are **out of scope**, except the `platform`-property callers migrated by
MAINT-03-08. Behavior-preserving throughout: no user-visible change.

The 33 main sites group as (file:line grounded against a fresh `--rerun-tasks` compile):

- **Group I — file-chooser + DataManager + build** (the original MAINT-03 scope; MAINT-03-01…06).
- **Group II — deprecated `runReadAction {}` free fn (14):** replace with the behavior-identical
  **`runReadActionBlocking {}`** (non-cancellable equivalent), *not* `ReadAction.nonBlocking`
  (which changes threading semantics — see risks R-2). Sites:
  `run/test/LuaTestRunConfigurationProducer.kt:80,121,132,144`, `run/test/LuaTestLocator.kt:43`,
  `run/console/LuaChunkCompletion.kt:17`, `lang/doc/LuaDocSearchItem.kt:33,45`,
  `lang/doc/LuaDocSearchEverywhereContributor.kt:108,122`,
  `lang/completion/LuaAutoImportInsertHandler.kt:37,39,45,63`.
- **Group III — retire internal `platform` prop (5 main + 17 test):** declared
  `@Deprecated("Use target.platform instead", ReplaceWith("target?.platform"))` at
  `LuaProjectSettings.kt:46`. The 5 main warnings are its own legacy-settings **migration shim**
  (`migrateFromLegacySettings()` reads `:96–99`; `setTarget()` writes `:112`). The field anchors
  backward-compat deserialization and **must not be deleted** — retirement means removing all
  *external* callers (17 test) and confining the field to a single `@Suppress("DEPRECATION")` shim
  (see design §8, risks R-1).
- **Group IV — misc "Deprecated in Java" singletons (~8):** each replaced with its verified
  non-deprecated equivalent (design §9 table): `createTextAttributesKey(String, TextAttributes)` ×2
  (`coverage/report/LuaCovReportHighlight.kt:24,27`), `CodeStyleSettingsCustomizable.WRAP_OPTIONS/WRAP_VALUES`
  (`lang/format/LuaCodeStyleSettings.kt:112,120`), `TemplateContextType(String, String)`
  (`lang/completion/templates/LuaTemplateContextType.kt:7`), `TailType.SPACE`
  (`lang/LuaCompletionContributor.kt:44`), `DaemonCodeAnalyzer.restart()`
  (`project/LuaSettingsChangeListener.kt:28`), `StubBasedPsiElementBase.getElementType()`
  (`lang/psi/LuaBaseElements.kt:160`), and the `LuaDocSearchEverywhereContributor.kt:111,112` pair.

## Scope

### In Scope
- All 33 `src/main` deprecation sites (Groups I–IV) + the Gradle-plugin bump and wrapper reconcile.
- Migrating the 17 **test** callers of `LuaProjectSettings.State.platform` to the `target` API
  (Group III completion) so the deprecated field has no external readers/writers.

### Out of Scope
- The 246 test-code idiom deprecations **other than** the `platform` callers — the 105
  `runInEdtAndWait(ThrowableRunnable)` and 118 test `runReadAction {}` sites. Deferred (possible
  follow-up feature).
- Any behavioural change; any new PSI type / index / extension point.
- The `DataContext` *parameter* overrides (`LuaEnterHandlerDelegate`, `LuaTypeHierarchyProvider`,
  `LuaIntroduceVariableHandler`, …) — these receive `DataContext` from the platform and call the
  non-deprecated `CommonDataKeys.*.getData(...)`; they are **not** deprecated and are not touched.
- Force-migrating any deprecated API with **no** non-deprecated 261 replacement — if Phase 0 DR
  finds one, it is `@Suppress`-ed with a rationale comment, not rewritten.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| MAINT-03-01 | **Remove deprecated `DataManager.getDataContext()`** | M | `LuaDebugVariable.computeSourcePosition` obtains the `Project` from the owning `LuaStackFrame` instead of the deprecated no-arg `DataManager.getInstance().dataContext`; the `DataManager`/`DataContext`/`PlatformDataKeys` imports are removed. |
| MAINT-03-02 | **Replace `@Deprecated` file-chooser factory** | M | The three `FileChooserDescriptorFactory.createSingleLocalFileDescriptor()` calls become the behavior-identical `singleFileOrDir()` (or the ctor fallback per Phase 0). |
| MAINT-03-03 | **Modernize obsolete file-chooser factory** | S | `createSingleFolderDescriptor()` (×2) and `createSingleFileNoJarsDescriptor()` (×1) become `singleDir()` / `singleFile()`. |
| MAINT-03-04 | **Update IntelliJ Platform Gradle Plugin** | M | `intelliJPlatform` in `gradle/libs.versions.toml` is `2.17.0`; `run build` succeeds. |
| MAINT-03-05 | **Reconcile Gradle wrapper version** | M | `gradle.properties gradleVersion` matches the wrapper `distributionUrl` so the `wrapper` task no longer downgrades. |
| MAINT-03-07 | **Group II: `runReadActionBlocking` swap** | M | All 14 main `runReadAction {}` calls become `runReadActionBlocking {}` (behavior-preserving); the deprecated `runReadAction` import is dropped. No `ReadAction.nonBlocking`. |
| MAINT-03-08 | **Group III: retire `platform` prop external use** | M | The 17 test callers (and any non-migration main caller) of `LuaProjectSettings.State.platform` move to `target`/`setTarget`; the remaining legitimate legacy reads/writes are confined to one `@Suppress("DEPRECATION")` migration shim so the field survives only for backward-compat deserialization. |
| MAINT-03-09 | **Group IV: misc singleton replacements** | M | Each Group-IV site uses its verified non-deprecated equivalent (design §9), or `@Suppress` + rationale where DR finds no replacement. |
| MAINT-03-10 | **Main-code deprecation count → 0 (or documented)** | M | After the change, a `--rerun-tasks` compile emits **0** `is deprecated` warnings from `src/main`, except any explicitly `@Suppress`-ed with a rationale. |
| MAINT-03-06 | **No behavior regression** | M | Full `run build` + `run test` green; run-config editors, debug navigation, doc search, completion, coverage highlighting, and settings persistence behave exactly as before. |

## Detailed Specifications

### MAINT-03-01 / -02 / -03 / -04 / -05 (Group I)
Unchanged from the original grounded design — see design.md §1–§6 and §2.1–§2.5. `DataManager`
removal threads `Project?` through the `LuaDebugVariable` ctor; the file-chooser swaps are
behavior-identical descriptor factories; the build bump is `2.5.0 → 2.17.0` with wrapper `8.14.4`.

### MAINT-03-07 (Group II)
Replace `com.intellij.openapi.application.runReadAction { … }` with `runReadActionBlocking { … }` at
the 14 sites. This is the **non-cancellable blocking** equivalent (same semantics, non-deprecated
name); it is **not** `ReadAction.nonBlocking` (async/cancellable — a behavior change). `LuaChunkCompletion.kt:17`
additionally sits next to deprecated `ProgressIndicatorUtils.yieldToPendingWriteActions()` /
`runInReadActionWithWriteActionPriority(...)` (design §9) — handle as one cluster. DR-01 verifies the
exact `runReadActionBlocking` import.

### MAINT-03-08 (Group III)
The `platform` field is the deserialization anchor for pre-`target` settings. Retirement = (a)
migrate all 17 test callers to `setTarget(...)`/`getTarget()`; (b) confine the two legitimate
migration touch-points (`migrateFromLegacySettings` read, `setTarget` write) to a single
`@Suppress("DEPRECATION")`-annotated private helper so the field persists but no un-suppressed code
references it. Do **not** delete the field (would break loading old `.idea` settings). See risks R-1.

### MAINT-03-09 (Group IV)
Per-site replacements — the grounded ones and the DR-gated ones are tabulated in design §9. Grounded
now: `WRAP_OPTIONS/WRAP_VALUES` → `CodeStyleSettingsCustomizableOptions.getInstance().WRAP_OPTIONS/.WRAP_VALUES`;
`DaemonCodeAnalyzer.restart()` → `restart(reason: Any)`; `TemplateContextType("LUA","Lua")` →
single-arg `TemplateContextType("Lua")` (verify contextId via the `templateContextType` EP —
DR-04). DR-gated: `TailType.SPACE`, `getElementType()`, `DataManager.dataContext`,
`createTextAttributesKey(String, TextAttributes)`, and the doc-search pair (DR-02/03).

## Test Cases

| # | Requirement | Given | When | Then |
|---|-------------|-------|------|------|
| 1–8 | MAINT-03-01…06 | (unchanged) | (unchanged) | Per the original design (DataManager removal TCs, file-chooser greps, build/wrapper, regression). |
| 9 | MAINT-03-07 | `src/main` tree | `grep -rn "import com.intellij.openapi.application.runReadAction$" src/main` | 0 matches; `runReadActionBlocking` used at all 14 sites. |
| 10 | MAINT-03-08 | `src/test` + `src/main` | `grep -rn "\.platform\b" src/test`; `grep -n "\.platform\b" LuaProjectSettings.kt` | No test references; only the single suppressed migration shim reads/writes the legacy field. |
| 11 | MAINT-03-09 | Each Group-IV site | manual vs design §9 table | Each calls its non-deprecated replacement or carries `@Suppress` + rationale. |
| 12 | MAINT-03-10 | full compile | `run "compileKotlin --no-build-cache --rerun-tasks"` → `grep "is deprecated" | grep /src/main/ | wc -l` | 0 (excluding documented `@Suppress`). |

## Acceptance Criteria
- [ ] MAINT-03-01…05: original Group-I criteria (TC 1–7) pass.
- [ ] MAINT-03-07: TC 9 passes; 14 sites on `runReadActionBlocking`, no semantics change.
- [ ] MAINT-03-08: TC 10 passes; deprecated `platform` field has no external caller.
- [ ] MAINT-03-09: TC 11 passes; every Group-IV site replaced or documented.
- [ ] MAINT-03-10: TC 12 passes; 0 main-code deprecation warnings (or `@Suppress`-documented).
- [ ] MAINT-03-06: full `run build` + `run test` green; human-verification checklists CL1–CL5 pass.
- [ ] All Phase 0 DR tasks (risks-and-gaps.md DR-01…DR-04) resolved before their dependent phases run.

## Non-Functional Requirements
- **Threading**: Group II preserves blocking read-action semantics (no EDT/cancellability change).
- **Persistence**: Group III keeps backward-compat deserialization of pre-`target` settings.
- **Build**: no change to `pluginSinceBuild` (261) or the compiled SDK (2026.1.3); only the Gradle plugin.

## Dependencies
- Parent epic: [MAINT](../requirements.md). Deprecation baseline: a fresh `--rerun-tasks` compile.
- `~/Documents/src/lua/intellij-community` for verifying each 261 replacement (DR-01…DR-04).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Manual checklists: [human-verification-checklists.md](human-verification-checklists.md)
