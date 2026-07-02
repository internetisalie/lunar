---
id: "MAINT-17-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-17"
folders:
  - "[[features/maint/17-utilities-commandline/requirements|requirements]]"
---

# Technical Design: MAINT-17 — Test Coverage: Utilities & Command Line

This is a **test map**, not a production design. No production code changes. Every target
symbol below is grounded with a `path:line` citation.

## 1. Target Inventory (grounded)

| Symbol | Kind | Location |
|---|---|---|
| `object LuaProcessUtil` | object | `src/main/kotlin/net/internetisalie/lunar/util/LuaProcessUtil.kt:11` |
| `LuaProcessUtil.capture(cmd, timeout)` | fun | `LuaProcessUtil.kt:17` |
| `LuaProcessUtil.doCapture` (private) | fun | `LuaProcessUtil.kt:27` |
| `LuaProcessUtil.listen(cmd, listener, timeout)` | fun | `LuaProcessUtil.kt:41` |
| `STANDARD_TIMEOUT` / `PROCESS_TIMEOUT_EXCEPTION_CODE` (-1) / `PROCESS_EXECUTION_EXCEPTION_CODE` (-2) | const | `LuaProcessUtil.kt:12-14` |
| `object LuaFileUtil` | object | `src/main/kotlin/net/internetisalie/lunar/util/LuaFileUtil.kt:15` |
| `LuaFileUtil.pluginVirtualDirectory` | val | `LuaFileUtil.kt:16` |
| `LuaFileUtil.getPluginVirtualDirectoryChild(vararg)` | fun | `LuaFileUtil.kt:22` |
| `LuaFileUtil.findLuaFilesInDir(dir)` | fun | `LuaFileUtil.kt:31` |
| `LuaFileUtil.findPsiFiles(project, files)` | fun | `LuaFileUtil.kt:44` |
| `newAppBackgroundTask(desc, action)` | top-level fun | `src/main/kotlin/net/internetisalie/lunar/util/LuaTaskUtil.kt:8` |
| `newProjectBackgroundTask(desc, project, action)` | top-level fun | `LuaTaskUtil.kt:15` |
| `newLuaDefaultInterpreterCommandLine()` | top-level fun | `src/main/kotlin/net/internetisalie/lunar/command/LuaCommandLine.kt:11` |
| `newProjectLuaInterpreterCommandLine(project)` | top-level fun | `LuaCommandLine.kt:18` |
| `newLuaInterpreterCommandLine(interpreter)` | top-level fun | `LuaCommandLine.kt:32` |
| `class LuaRunProfile(cmd)` + `commandLine` / `getName` / `getIcon` / `getState` | class | `src/main/kotlin/net/internetisalie/lunar/command/LuaRunProfile.kt:16-31` |
| `class LuaRunProfileState` | class | `LuaRunProfile.kt:33` |

### Supporting types (grounded, used to build fixtures)
- `data class LuaInterpreter(var path, …)` — `platform/LuaInterpreter.kt:11`; `val executable: VirtualFile?`
  reads `path` and resolves via `VfsUtil.findFile` — `LuaInterpreter.kt:52`. The `.jar` branch keys on
  `interpreterFile.extension == "jar"` — `LuaCommandLine.kt:39`.
- `LuaProjectSettings.getInstance(project).state.interpreter` — used at `LuaCommandLine.kt:19-20`;
  `state.expandSourcePath(project)` — `settings/LuaProjectSettings.kt:83` (trims + `expandMacros`).
- `LuaToolEnvironment.prependToolDirsToPath(cmd, project)` — invoked at `LuaCommandLine.kt:28`
  (`tool/LuaToolEnvironment.kt:40`); a `null`-project no-op path exists, but the project builders here
  always pass a real project. **Not** re-asserted (out of scope); its PATH effect may coexist with the
  `LUA_PATH` assertion but is not the thing under test.
- `LuaApplicationSettings.instance.state.interpreters` — used by `newLuaDefaultInterpreterCommandLine`
  (`LuaCommandLine.kt:12-14`). Application-level singleton — see §3.3 note.

### Prior art: existing tests
`grep`/`glob` over `src/test`, `src/integrationTest` found **no** existing tests for
`LuaProcessUtil`, `LuaFileUtil`, `LuaTaskUtil`, `LuaCommandLine`, or `LuaRunProfile` (searched
`*Util*Test*`, `*Process*Test*`, `*Thread*Test*`, `*File*Test*`, `*CommandLine*Test*`,
`*RunProfile*Test*`, `*Task*Test*`). These are net-new test classes. `BasePlatformTestCase` usage
and temp-dir/VFS fixture idioms are modeled on
`src/test/kotlin/net/internetisalie/lunar/platform/LuaInterpreterSearchPathGlobTest.kt`.

## 2. Test Approach Per Class

### 2.1 `LuaProcessUtil` — `LuaProcessUtilTest` (BasePlatformTestCase)
Requires the platform `Application` (`capture` branches on `ApplicationManager.getApplication()`
at `LuaProcessUtil.kt:18`), so it must be a `BasePlatformTestCase`.

- **Hermetic commands only.** Do **not** shell out to a real Lua interpreter. Use short,
  self-terminating OS commands built as `GeneralCommandLine`:
  - stdout capture (TC-01): a portable print command. On non-Windows use
    `GeneralCommandLine("/bin/sh", "-c", "printf lunar-ok")` (guarded by
    `Assume`/`SystemInfo.isWindows` skip on Windows, matching the "no OS-specific shell" out-of-scope
    rule). Assert `output.stdout.contains("lunar-ok")` and `output.exitCode == 0`.
  - timeout (TC-02): `GeneralCommandLine("/bin/sh", "-c", "sleep 5")` with
    `capture(cmd, timeout = 200)`. The runner raises `TimeoutException` → mapped at
    `LuaProcessUtil.kt:32-33`. Assert `exitCode == PROCESS_TIMEOUT_EXCEPTION_CODE` and `isTimeout`.
  - execution failure (TC-03): `GeneralCommandLine("this-binary-does-not-exist-xyz")`. The
    `CapturingProcessHandler` construction (inside `doCapture`'s try) launches the process and throws
    `ExecutionException` → mapped at `LuaProcessUtil.kt:34-35`. Assert
    `exitCode == PROCESS_EXECUTION_EXCEPTION_CODE` and `!isTimeout`.
- `listen` is not directly tested (needs a live streaming process + `ProcessListener`), kept out of
  scope to stay hermetic; `capture` exercises the same `runProcess` core.

### 2.2 `LuaFileUtil` — `LuaFileUtilTest` (BasePlatformTestCase)
Needs VFS + `PsiManager`, so `BasePlatformTestCase`.

- **findLuaFilesInDir (TC-04):** create a temp dir tree (`Files.createTempDirectory`, per the glob
  test idiom), write `a.lua`, `sub/b.lua`, `c.txt`, refresh into VFS via
  `VfsUtil.findFile(path, true)` / `LocalFileSystem.getInstance().refreshAndFindFileByNioFile`, then
  assert the returned list maps to exactly `{a.lua, b.lua}` by name. `LuaFileType` membership is the
  filter (`LuaFileUtil.kt:35`).
- **getPluginVirtualDirectoryChild (TC-05):** assert
  `getPluginVirtualDirectoryChild("no-such-child-xyz")` is `null`. In the headless test sandbox
  `pluginVirtualDirectory` may itself be `null`; the loop at `LuaFileUtil.kt:24-27` short-circuits and
  still returns `null`, which is the asserted outcome (no NPE). This validates the null-guard path.
- **findPsiFiles (TC-06):** `myFixture.configureByText("m.lua", "local x = 1")`, take
  `myFixture.file.virtualFile`, plus one clearly-unmappable `VirtualFile` (e.g. the temp-dir root
  directory `VirtualFile`, for which `PsiManager.findFile` returns `null`). Assert the result contains
  the real `PsiFile` and has size 1 (`mapNotNull` drops the null — `LuaFileUtil.kt:46`).

### 2.3 `LuaTaskUtil` — `LuaTaskUtilTest` (BasePlatformTestCase)
`Task.Backgroundable` and `ProjectManager.getInstance().defaultProject` require the platform.

- **newProjectBackgroundTask (TC-07):** pass `project` (the fixture project), a description, and an
  `action` incrementing an `AtomicInteger`. Assert `task.title == description` and
  `task.project === project` (constructor at `LuaTaskUtil.kt:20`). Call `task.run(mockIndicator)` with
  an `EmptyProgressIndicator`; assert the counter == 1 and the received indicator is passed through
  (`LuaTaskUtil.kt:22`).
- **newAppBackgroundTask (TC-08):** assert it delegates to the default project
  (`ProjectManager.getInstance().defaultProject`, `LuaTaskUtil.kt:12`) and that `run` invokes the
  action once. Assert on action invocation, not identity of the default project (which is a platform
  singleton).

### 2.4 `LuaCommandLine.kt` — `LuaCommandLineTest` (BasePlatformTestCase)
`LuaProjectSettings.getInstance(project)` and VFS resolution need the platform.

- Fixtures: create a temp executable file `<tmp>/lua` and a temp jar `<tmp>/foo.jar` with
  `Files.createFile`, then refresh so `LuaInterpreter(path=…).executable` (VFS lookup at
  `LuaInterpreter.kt:52-57`) resolves. The `.jar` extension drives the branch at `LuaCommandLine.kt:39`.
- **newLuaInterpreterCommandLine system binary (TC-09):** assert `cmd.exePath` ends with `lua` and
  `cmd.parametersList.list` does not contain `-cp`.
- **newLuaInterpreterCommandLine jar (TC-10):** assert `cmd.exePath == "java"` and
  `cmd.parametersList.list == listOf("-cp", <jarPath>, "lua")` (`LuaCommandLine.kt:40-42`).
- **null executable (TC-11):** `LuaInterpreter(path = "/no/such/lua")` → `executable` null →
  function returns null at `LuaCommandLine.kt:33`.
- **newProjectLuaInterpreterCommandLine LUA_PATH (TC-12):** set
  `LuaProjectSettings.getInstance(project).state.interpreter` to a valid temp interpreter and set
  `state.sourcePath` to a literal (macro-free) path so `expandSourcePath` returns it verbatim
  (`LuaProjectSettings.kt:83-84`). Assert `cmd.environment["LUA_PATH"]` == that path
  (`LuaCommandLine.kt:22-24`). Use a macro-free sourcePath to keep `expandMacros` a no-op and the
  assertion deterministic.
- **null interpreter (TC-13):** with `state.interpreter == null`, function returns null
  (`LuaCommandLine.kt:20`).
- `newLuaDefaultInterpreterCommandLine` (§3.3) is **not** directly asserted — it reads the app-level
  `LuaApplicationSettings` singleton, which is shared mutable state across tests; mutating it risks
  cross-test bleed. Its non-null branch is structurally identical to
  `newLuaInterpreterCommandLine`, which TC-09/10/11 already cover.

### 2.5 `LuaRunProfile` — `LuaRunProfileTest` (BasePlatformTestCase)
`RunProfile`/`LuaBundle`/`LuaIcons` are platform types.

- **TC-14:** construct `LuaRunProfile(GeneralCommandLine("java"))`; assert `commandLine === cmd`
  (`LuaRunProfile.kt:29-30`), `getName()` is non-blank (bundle key `lua.run.profile.name`,
  `LuaRunProfile.kt:22`), `getIcon()` is non-null (`LuaIcons.FILE`, `:26`), and `getState(...)` returns
  a `LuaRunProfileState` (`:18`). `getState` needs an `Executor` + `ExecutionEnvironment`; if
  constructing a real `ExecutionEnvironment` headless is impractical, assert only the four
  accessor/identity properties (`getName`/`getIcon`/`commandLine`) and treat the `getState` type
  assertion as best-effort — the accessor assertions alone satisfy MAINT-17-05's "Should".

## 3. Hermeticity & Environment Notes

- **§3.1 No real processes beyond trivial shell built-ins.** TC-01/02/03 use `/bin/sh` built-ins
  (`printf`/`sleep`) or a non-existent binary — no Lua, no network, sub-second. On Windows the shell
  cases are skipped (out-of-scope OS-specific shells); TC-03 (non-existent binary) is portable.
- **§3.2 Temp dirs cleaned in `tearDown`** via `base.toFile().deleteRecursively()`, exactly as
  `LuaInterpreterSearchPathGlobTest` does.
- **§3.3 App-settings isolation.** Avoid mutating `LuaApplicationSettings` state; prefer the
  per-project `LuaProjectSettings` (fixture-scoped) for TC-12/13.
- **§3.4 Fonts.** These tests do not initialize an editor color scheme, so the `Fontconfig head is
  null` pitfall does not apply; the gce-builder bootstrap installs fonts regardless.

## 4. Open Questions
None.
</content>
