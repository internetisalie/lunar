---
id: "MAINT-03"
title: "MAINT-03: Deprecation Cleanup"
type: "feature"
status: "in_progress"
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
- **Group III — DELETE internal `platform` prop (5 main + 17 test):** declared
  `@Deprecated("Use target.platform instead", ReplaceWith("target?.platform"))` at
  `LuaProjectSettings.kt:46`. The 5 main warnings are its own settings shim
  (`migrateFromLegacySettings()` reads it `:96–99`; `setTarget()` writes it back `:112`). **There is
  no installed user base**, so there is no legacy `.idea/lunar.xml` to deserialize — the field's only
  reason to exist is gone. Retirement is therefore a **straight delete**: remove the `@Deprecated`
  `platform` field, rework `migrateFromLegacySettings()` to build the default `Target` from
  `LuaPlatform.STANDARD` (its former default) + `languageLevel` without reading the field (rename it
  e.g. `buildDefaultTarget()`), drop the `setTarget()` writeback, and migrate the 17 test callers to
  `setTarget(...)`/`getTarget()`. No `@Suppress` shim needed (see design §8-III; former risk R-1 is moot).
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
| MAINT-03-04 | **Update IntelliJ Platform Gradle Plugin** | M | **Deferred (blocked).** `intelliJPlatform = "2.17.0"` needs Gradle 9.0.0+ (repo wrapper is 8.14.4); the highest Gradle-8.14-compatible 2.x (`2.6.0`) regresses the whole test suite (`PathManager.getHomeDir` at `BasePlatformTestCase.setUp`). A **2026-07-03 spike** (Gradle 9.1.0 wrapper + IJPGP 2.17.0 + qodana 2025.1.1 / kover 0.9.2 / changelog 2.4.0) proved the tooling upgrade itself is clean — the build configures and compiles with **zero** `build.gradle.kts` changes — but `:test` fails **1080/1459** (1075 fan-out from one `TestLoggerFactory.reconfigure` → `NoSuchMethodError: JulLogger.configureLogFileAndConsole(...)` in `BasePlatformTestCase.setUp`). Root cause: **IJPGP 2.17.0's test framework is built against a platform newer than the pinned GoLand build 261**, so it calls a `JulLogger` overload absent from the 261 platform jars. **MAINT-03-04 therefore cannot land independently — it is coupled to a compiled-SDK bump past 261.** And as of 2026-07-03 **there is no stable platform past build 261**: 2026.1.3 is current and **2026.2 (build 262) is not yet released**, so IJPGP 2.17.0 is built *ahead* of the current stable platform (it targets the unreleased 2026.2 line). Landing it now would require pinning a **2026.2 EAP/pre-release** SDK — a moving target — and setting `pluginSinceBuild` to an unreleased build the plugin would ship against while **no user is on it**. So this is **not "deferred pending an SDK bump" but gated on 2026.2 actually shipping** (and the plugin choosing to adopt it). Kept at `2.5.0` (suite green, 0 regressions). **Remains in scope for MAINT-03 — parked/blocked**; MAINT-03 stays `in_progress` until 2026.2 ships and the SDK is bumped, at which point 2.17.0 (+ Gradle 9.1, qodana/kover/changelog) should land. See design §2.5. |
| MAINT-03-05 | **Reconcile Gradle wrapper version** | M | `gradle.properties gradleVersion` matches the wrapper `distributionUrl` so the `wrapper` task no longer downgrades. |
| MAINT-03-07 | **Group II: `runReadActionBlocking` swap** | M | All 14 main `runReadAction {}` calls become `runReadActionBlocking {}` (behavior-preserving); the deprecated `runReadAction` import is dropped. No `ReadAction.nonBlocking`. |
| MAINT-03-08 | **Group III: delete `platform` prop** | M | Delete the `@Deprecated` `LuaProjectSettings.State.platform` field entirely; rework `migrateFromLegacySettings()` to build the default `Target` from `LuaPlatform.STANDARD` + `languageLevel` (no field read); drop the `setTarget()` writeback; migrate the 17 test callers to `setTarget(...)`/`getTarget()`. No user base ⇒ no deserialization concern. |
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
**No installed user base ⇒ no legacy settings to preserve**, so the field is simply deleted:
(a) remove the `@Deprecated var platform` field (`:46`); (b) rework `migrateFromLegacySettings()` —
which today reads `platform` to pick a version — to build the default `Target` from
`LuaPlatform.STANDARD` (the field's former default) + `languageLevel`, and rename it to reflect that
it now only constructs a fresh-state default (e.g. `buildDefaultTarget()`); (c) delete the
`platform = newTarget.platform` writeback in `setTarget()` (`:112`); (d) migrate the 17 test callers
(which set `state.platform = …`) to `setTarget(Target(platform, version))`/`getTarget()`. See design §8-III.

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
| 10 | MAINT-03-08 | `src/test` + `src/main` | `grep -rn "State.*\.platform\b\|state.platform\b" src/test src/main`; `grep -n "var platform" LuaProjectSettings.kt` | 0 matches — the `platform` field is gone; no caller references it; `LuaProjectSettings` compiles using `target`/`languageLevel` only. |
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
