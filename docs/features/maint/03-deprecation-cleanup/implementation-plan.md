---
id: "MAINT-03-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-03"
folders:
  - "[[features/maint/03-deprecation-cleanup/requirements|requirements]]"
---

# MAINT-03: Implementation Plan

## Phases

### Phase 0: Pre-flight API verification [Must] — GATE for Phases 2, 5, 7
- **Goal**: Confirm every replacement API exists in the resolved 2026.1.3 SDK before editing call
  sites; pin fallbacks / `@Suppress` where none exists. Executes the DR tasks in risks-and-gaps.md.
- **Tasks**:
  - [ ] **DR-00 (Group I):** Verify `singleFileOrDir()`, `singleDir()`, `singleFile()` resolve at
        2026.1.3 (grep the resolved SDK jars/sources, or scratch-compile). Realizes design §1, §6.
        If absent, use the ctor fallback `FileChooserDescriptor(true, true, false, false)` /
        `(false, true, false, false)` / `(true, false, false, false)` (requirements Behavior Rules).
  - [ ] **DR-01 (Group II):** Verify the exact `runReadActionBlocking` import
        (`com.intellij.openapi.application.runReadActionBlocking`) exists and is non-deprecated at 261.
  - [ ] **DR-02/DR-03 (Group IV):** Determine + verify the non-deprecated replacement for
        `TailType.SPACE`, `StubBasedPsiElementBase.getElementType()`, `DataManager.dataContext`,
        `createTextAttributesKey(String, TextAttributes)`, and the `LuaDocSearchEverywhereContributor:111,112`
        pair (design §9). Where none exists, record an `@Suppress` + rationale.
  - [ ] **DR-04 (Group IV):** Confirm the single-arg `TemplateContextType(name)` ctor + that the
        `templateContextType` EP registration in `plugin.xml` still supplies the `"LUA"` contextId.
- **Exit criteria**: every Group-I/II/IV replacement is confirmed available (or a documented
  fallback/`@Suppress` is chosen); risks-and-gaps.md DR rows all marked resolved.

### Phase 1: Remove deprecated `DataManager` usage [Must]
- **Goal**: `LuaDebugVariable` obtains its `Project` from `LuaStackFrame`; no `DataManager`.
- **Tasks**:
  - [ ] Edit `net.internetisalie.lunar.run.LuaDebugVariable` — add `private val targetProject: Project?`
        to the private primary constructor and a defaulted `targetProject: Project? = null` to the
        internal 3→4-arg constructor; rewrite `computeSourcePosition` per design §3.1; remove the
        `DataManager`/`DataContext`/`PlatformDataKeys` imports. Realizes design §2.1, §3.1.
  - [ ] Edit `LuaDebugVariable.computeChildren` to forward `targetProject = targetProject` when
        constructing nested-table children. Realizes design §2.3.
  - [ ] Edit `net.internetisalie.lunar.run.LuaStackFrame` — pass `project` as the 4th arg at both
        `LuaDebugVariable(...)` sites (lines 61, 73). Realizes design §2.2.
- **Exit criteria**: `LuaDebugVariable.kt` has zero `DataManager`/`DataContext`/`PlatformDataKeys`
  references (TC 2); `TestLuaDebugVariable` still compiles and passes (TC 3, TC 8).

### Phase 2: Replace deprecated & obsolete file-chooser factories [Must / Should]
- **Goal**: All six `FileChooserDescriptorFactory` call sites use terse modern methods.
- **Tasks**:
  - [ ] `run/LuaRunConfiguration.kt:313,318` → `singleFileOrDir()`, `singleDir()`. Realizes design §2.4. **[Must]**
  - [ ] `run/test/LuaTestRunConfiguration.kt:272,277` → `singleFileOrDir()`, `singleDir()`. Realizes design §2.4. **[Must]**
  - [ ] `rocks/run/LuaRocksRunConfiguration.kt:233` → `singleFileOrDir()`. Realizes design §2.4. **[Must]**
  - [ ] `tool/ui/LuaToolsConfigurable.kt:90` → `singleFile()`. Realizes design §2.4. **[Should]**
  - [ ] Remove the two `// TODO: Clean up deprecation:` comments
        (`LuaRunConfiguration.kt:312`, `LuaRocksRunConfiguration.kt:232`).
- **Exit criteria**: TC 4 and TC 5 return 0 matches; all four files compile.

### Phase 3: Modernize build configuration [Must]
- **Goal**: IntelliJ Platform Gradle Plugin on `2.17.0`; wrapper version self-consistent.
- **Tasks**:
  - [ ] `gradle/libs.versions.toml:10` → `intelliJPlatform = "2.17.0"`. Realizes design §2.5.
  - [ ] `gradle.properties:36` → `gradleVersion = 8.14.4`. Realizes design §2.5.
- **Exit criteria**: `gce-builder run build` prints `BUILD SUCCESSFUL` (TC 6); `gradleVersion`
  and the wrapper `distributionUrl` agree, and the `wrapper` task is idempotent (TC 7).

### Phase 5: Group II — `runReadActionBlocking` swap [Must] (gated by DR-01)
- **Goal**: 14 `runReadAction {}` → `runReadActionBlocking {}`, imports updated, semantics unchanged.
- **Tasks**:
  - [ ] Swap the 14 sites (requirements §MAINT-03-07 list); update the import; keep the lambda body
        verbatim. Handle the `LuaChunkCompletion.kt:17` cluster (adjacent deprecated
        `yieldToPendingWriteActions`/`runInReadActionWithWriteActionPriority`) per design §9.
- **Exit criteria**: TC 9 (0 deprecated `runReadAction` imports in `src/main`); compiles.

### Phase 6: Group III — delete internal `platform` prop [Must]
- **Goal**: the deprecated field and all 22 references are gone (no user base ⇒ no migration).
- **Tasks**:
  - [ ] Delete the `@Deprecated var platform` field (`LuaProjectSettings.kt:46`); rework
        `migrateFromLegacySettings()` → `buildDefaultTarget()` using `LuaPlatform.STANDARD` +
        `languageLevel` (no field read); delete the `setTarget()` writeback (`:112`). Design §8-III.
  - [ ] Migrate the 17 test callers (`state.platform = …`) to `setTarget(...)`/`getTarget()`.
- **Exit criteria**: TC 10 (field deleted; 0 references in `src/main`/`src/test`; compiles).

### Phase 7: Group IV — misc singleton replacements [Must] (gated by DR-02/03/04)
- **Goal**: each Group-IV site on its non-deprecated equivalent or documented `@Suppress`.
- **Tasks**:
  - [ ] Apply the design §9 per-site replacements: `WRAP_OPTIONS/WRAP_VALUES` →
        `CodeStyleSettingsCustomizableOptions.getInstance()`; `restart()` → `restart(reason)`;
        `TemplateContextType` single-arg; plus the DR-02/03/04 outcomes.
- **Exit criteria**: TC 11; compiles.

### Phase 4: Regression verification [Must] — FINAL
- **Goal**: Prove behavior preservation, a clean build, and zero main-code deprecations.
- **Tasks**:
  - [ ] `tooling/gce-builder/gce-builder.sh run "clean build"` and `run test` — green (TC 6, TC 8).
  - [ ] `run "compileKotlin --no-build-cache --rerun-tasks"` → **0** `is deprecated` in `src/main`
        (excluding documented `@Suppress`) — TC 12 / MAINT-03-10.
  - [ ] `run "ktlintFormat ktlintCheck"` on edited files (match surrounding style; no mass reformat).
- **Exit criteria**: build + tests green; 0 main-code deprecation warnings (or `@Suppress`-documented).

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-03-01 | M | Phase 1 |
| MAINT-03-02 | M | Phase 2 |
| MAINT-03-03 | S | Phase 2 |
| MAINT-03-04 | M | Phase 3 |
| MAINT-03-05 | M | Phase 3 |
| MAINT-03-07 | M | Phase 5 |
| MAINT-03-08 | M | Phase 6 |
| MAINT-03-09 | M | Phase 7 |
| MAINT-03-10 | M | Phase 4 (final) |
| MAINT-03-06 | M | Phase 4 (final) |

## Verification Tasks
- [ ] Add a unit test to `TestLuaDebugVariable` constructing `LuaDebugVariable("x", value, true)`
      (null project) and invoking `computeSourcePosition` with a fake `XNavigatable` that records
      `setSourcePosition` calls; assert the `super` fallback runs — exactly one recorded call whose
      argument is `null` — and no exception (`super` is `XValue.computeSourcePosition`, which calls
      `setSourcePosition(null)`; `XNamedValue` does not override it) — covers TC 1.
- [ ] Add a unit test constructing `LuaDebugVariable("x", value, true, myFixture.project)` and
      asserting `name == "x"` — covers TC 3.
- [ ] Grep assertions for TC 2, TC 4, TC 5 (0 matches in `src/main`).
- [ ] Build + full test run for TC 6, TC 8; wrapper idempotency check for TC 7.
- [ ] Run [`human-verification-checklists.md`](human-verification-checklists.md): CL1–CL4 open
      each of the three run-config editors and the Lua Tools settings, click every browse button,
      and confirm the chooser opens and accepts the same file/dir selection as before; CL5 confirms
      a live debug-variable **Jump to Source** still navigates (exercises change A's non-null
      `targetProject` path that TC 1 cannot). Manual proof of behavior preservation for MAINT-03-06.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 0: Pre-flight API verification (DR gate) | todo | Must |
| Phase 1: Remove deprecated `DataManager` usage | todo | Must |
| Phase 2: Replace file-chooser factories | todo | Must |
| Phase 3: Modernize build configuration | todo | Must |
| Phase 5: Group II — `runReadActionBlocking` swap | todo | Must |
| Phase 6: Group III — retire `platform` prop | done | Must |
| Phase 7: Group IV — misc singleton replacements | todo | Must |
| Phase 4: Regression verification (FINAL) | todo | Must |
