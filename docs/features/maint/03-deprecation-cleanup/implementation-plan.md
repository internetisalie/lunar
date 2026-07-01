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

### Phase 0: Pre-flight API verification [Must]
- **Goal**: Confirm the terse `FileChooserDescriptorFactory` methods exist in the resolved
  2026.1.3 SDK before editing call sites; pin the fallback if not.
- **Tasks**:
  - [ ] Verify `singleFileOrDir()`, `singleDir()`, `singleFile()` resolve against the
        `platformVersion = 2026.1.3` SDK (grep the resolved `app.jar`/sources for
        `FileChooserDescriptorFactory`, or attempt a scratch compile). Realizes design §1, §6.
  - [ ] If any is absent, record that the constructor fallback
        (`FileChooserDescriptor(true, true, false, false)` / `(false, true, false, false)` /
        `(true, false, false, false)`) is used instead — per requirements "Behavior Rules".
- **Exit criteria**: The three replacement methods (or their documented constructor fallbacks)
  are confirmed available at 2026.1.3.

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

### Phase 4: Regression verification [Must]
- **Goal**: Prove behavior preservation and a clean build.
- **Tasks**:
  - [ ] `tooling/gce-builder/gce-builder.sh run test` — full unit suite green (TC 8).
  - [ ] `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"` on the edited files
        (match surrounding style; do not mass-reformat).
  - [ ] Confirm `src/main` compiles with no *new* deprecation warnings for the three API families.
- **Exit criteria**: build + tests green; no new deprecation warnings.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-03-01 | M | Phase 1 |
| MAINT-03-02 | M | Phase 2 |
| MAINT-03-03 | S | Phase 2 |
| MAINT-03-04 | M | Phase 3 |
| MAINT-03-05 | M | Phase 3 |
| MAINT-03-06 | M | Phase 4 |

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
| Phase 0: Pre-flight API verification | todo | Must |
| Phase 1: Remove deprecated `DataManager` usage | todo | Must |
| Phase 2: Replace file-chooser factories | todo | Must |
| Phase 3: Modernize build configuration | todo | Must |
| Phase 4: Regression verification | todo | Must |
