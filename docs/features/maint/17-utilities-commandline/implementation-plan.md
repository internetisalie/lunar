---
id: "MAINT-17-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-17"
folders:
  - "[[features/maint/17-utilities-commandline/requirements|requirements]]"
---

# MAINT-17: Implementation Plan

Coverage-only. Each phase adds one net-new test class under
`src/test/kotlin/net/internetisalie/lunar/...`. All classes extend `BasePlatformTestCase`
(platform types are involved in every target). Temp-dir/VFS idioms follow
`platform/LuaInterpreterSearchPathGlobTest.kt`.

## Phases

### Phase 1: Process capture tests [Must] — MAINT-17-01
- **File**: `src/test/kotlin/net/internetisalie/lunar/util/LuaProcessUtilTest.kt`
- **Class**: `class LuaProcessUtilTest : BasePlatformTestCase()`
- **Test methods**:
  - [x] `testCaptureReturnsStdoutAndZeroExit` — TC-01 (skip on `SystemInfo.isWindows`;
        `/bin/sh -c "printf lunar-ok"`).
  - [x] `testCaptureTimeoutMapsToTimeoutExitCode` — TC-02 (`sh -c "sleep 5"`, timeout 200 →
        `PROCESS_TIMEOUT_EXCEPTION_CODE`, `isTimeout`).
  - [x] `testCaptureUnresolvableCommandThrowsExecutionException` — TC-03 (non-existent binary →
        the `ExecutionException` is thrown at handler construction, *outside* `doCapture`'s try,
        so it propagates rather than mapping to `PROCESS_EXECUTION_EXCEPTION_CODE`; test asserts
        the real propagated behaviour — see design deviation).
- **Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaProcessUtilTest*"`

### Phase 2: File-util tests [Must] — MAINT-17-02
- **File**: `src/test/kotlin/net/internetisalie/lunar/util/LuaFileUtilTest.kt`
- **Class**: `class LuaFileUtilTest : BasePlatformTestCase()` (temp dir in `setUp`, cleaned in
  `tearDown`).
- **Test methods**:
  - [ ] `testFindLuaFilesInDirReturnsOnlyLuaRecursively` — TC-04 (tree `a.lua`, `sub/b.lua`,
        `c.txt`; refresh into VFS; assert names `{a.lua, b.lua}`).
  - [ ] `testGetPluginVirtualDirectoryChildMissingReturnsNull` — TC-05.
  - [ ] `testFindPsiFilesMapsAndSkipsUnmappable` — TC-06 (configured `.lua` fixture + temp-dir
        `VirtualFile`; assert size 1 and contains the real `PsiFile`).
- **Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaFileUtilTest*"`

### Phase 3: Task-factory tests [Must] — MAINT-17-03
- **File**: `src/test/kotlin/net/internetisalie/lunar/util/LuaTaskUtilTest.kt`
- **Class**: `class LuaTaskUtilTest : BasePlatformTestCase()`
- **Test methods**:
  - [ ] `testNewProjectBackgroundTaskCarriesFieldsAndRunsAction` — TC-07 (`AtomicInteger` action;
        assert `title`, `project` identity, action count == 1 with passed `EmptyProgressIndicator`).
  - [ ] `testNewAppBackgroundTaskRunsAction` — TC-08 (assert action invoked once; task is
        `Task.Backgroundable`).
- **Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaTaskUtilTest*"`

### Phase 4: Command-line builder tests [Must] — MAINT-17-04
- **File**: `src/test/kotlin/net/internetisalie/lunar/command/LuaCommandLineTest.kt`
- **Class**: `class LuaCommandLineTest : BasePlatformTestCase()` (temp `lua` + `foo.jar` fixtures).
- **Test methods**:
  - [ ] `testSystemBinaryBuildsPlainCommandLine` — TC-09 (`exePath` ends `lua`, no `-cp`).
  - [ ] `testJarInterpreterBuildsJavaClasspathCommand` — TC-10
        (`exePath == "java"`, params `["-cp", <jar>, "lua"]`).
  - [ ] `testNullExecutableReturnsNull` — TC-11.
  - [ ] `testProjectCommandLineInjectsLuaPath` — TC-12 (set `LuaProjectSettings` interpreter +
        macro-free `sourcePath`; assert `environment["LUA_PATH"]`).
  - [ ] `testProjectCommandLineNullInterpreterReturnsNull` — TC-13.
- **Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaCommandLineTest*"`

### Phase 5: Run-profile tests [Should] — MAINT-17-05
- **File**: `src/test/kotlin/net/internetisalie/lunar/command/LuaRunProfileTest.kt`
- **Class**: `class LuaRunProfileTest : BasePlatformTestCase()`
- **Test methods**:
  - [ ] `testRunProfileExposesCommandLineNameAndIcon` — TC-14 (`commandLine` identity, non-blank
        `getName()`, non-null `getIcon()`; `getState` type check best-effort per design §2.5).
- **Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaRunProfileTest*"`

## Full-suite gate
After all phases:
`tooling/gce-builder/gce-builder.sh run "test --tests *LuaProcessUtilTest* --tests *LuaFileUtilTest* --tests *LuaTaskUtilTest* --tests *LuaCommandLineTest* --tests *LuaRunProfileTest*"`
then `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"` before committing.

## Definition of Done
- [ ] All five test classes exist and pass on gce-builder (0 failures).
- [ ] Every TC-01..TC-14 has a corresponding test method.
- [ ] No production source modified (coverage-only feature).
- [ ] ktlint clean on the new files (match surrounding IntelliJ-formatter style).
</content>
