---
id: RUN-06
title: "06: Debug Target Configuration"
type: feature
status: "todo"
priority: "medium"
parent_id: DEBUG/RUN
folders: ["[[features/debug/requirements|requirements]]"]
---

# 06: Debug Target Configuration

Decide, before a Lua debug session is launched, whether the configured target can actually be
debugged — and tell the user what is wrong at a severity the platform acts on.

> **ID note.** The front-matter `id` is `RUN-06` (unchanged), but every other document refers to
> this feature as **`DEBUG-06`** — the epic table (`docs/features/debug/requirements.md:23`) and
> [[RUN-04]]'s overview both do. Row IDs below follow the referenced name.

> **Status note.** This document moves the feature off `done`. The epic table still records
> `DEBUG-06` as **Full** — "Validate debug configurations before launching" — and that claim is
> false at its own headline: nothing this feature does prevents a launch (`DEBUG-06-02`). Three of
> eight `Must` rows are Not Implemented and two more are Partial. `in_progress` would be the
> accurate state, but `scripts/lint_planning.py` requires a `design*.md` alongside it and none
> exists, so `todo` is used, following [[DEBUG-07]]'s precedent for a feature whose recorded status
> outran its code. The epic table row was not edited here.

## How these requirements were derived

**Not from Lunar's code.** A specification read off its own implementation cannot fail — the defect
that left [[DEBUG-07]] marked shipped for months ([[BUG-450]] §4). The rows below were enumerated
from three sources that exist independently of this plugin, and only then checked against Lunar:

1. **The IntelliJ run-configuration contract.** `RunConfiguration.checkConfiguration()`
   (`platform/execution/src/com/intellij/execution/configurations/RunConfiguration.java:139-148`)
   defines a **three-tier severity ladder**, and the platform treats the tiers differently:
   - `RuntimeConfigurationError` — fatal. `RunManagerImpl.canRunConfiguration`
     (`platform/execution-impl/.../impl/RunManagerImpl.kt:154-167`) catches it and returns
     **`false`**: the launch is refused.
   - `RuntimeConfigurationException` (bare) — "a non-fatal error which the user should be warned
     about **but the execution should still be allowed**". The same call site swallows it and
     returns **`true`**.
   - `RuntimeConfigurationWarning` — as above, and additionally rendered yellow instead of red:
     `SingleConfigurationConfigurable.createValidationResult` passes
     `e instanceof RuntimeConfigurationWarning` as the `isWarning` flag
     (`platform/execution-impl/.../impl/SingleConfigurationConfigurable.java:333-343`).

   Getting this ladder wrong is the classic defect in this area, so it is the first thing the table
   asks about. The contract also fixes the **execution context**: `checkConfiguration()` "may be
   invoked on every change (i.e., after each character is typed in an input field)" and runs inside
   `ReadAction.nonBlocking` on a background thread (`RunnerAndConfigurationSettingsImpl.checkSettings`,
   `platform/execution-impl/.../impl/RunnerAndConfigurationSettingsImpl.kt:371-380`); the same result
   drives the combo-box "invalid" badge (`RunConfigurationIconAndInvalidCache.recalculateIcon:61-80`).
   `RunConfigurationProducer` is the contract's other half — how a target comes into existence from
   context at all.
2. **What can be wrong with a Lua debug target**, enumerated before reading Lunar: no runtime; a
   runtime path that is missing or not executable; a runtime whose Lua version does not match the
   project language level; a script that is unset, missing, or outside the debugger's base
   directory; a missing working directory; a debuggee (`mobdebug`) not reachable on `package.path`;
   a debug port already bound; an environment-file path that does not resolve.
3. **[[plugin-feature-comparison]]** — the *EXECUTION* rows. All five compared plugins ship a run
   configuration; only three ship a remote debugger, and Lunar is not one of them, which bounds what
   "target" can mean here (see `DEBUG-06-19`).

**Scope boundary.** [[RUN-04]] specifies *launch-time* validation inside `startProcess()`
(`ExecutionException`, run aborted). This feature is the **edit-time / pre-launch** half: what the
Run/Debug Configurations dialog and the platform's own launch gate know before a process exists.
Where a check exists only in [[RUN-04]]'s half, the row is **Partial**, not Full — a check that only
fires after the process is spawned did not validate the target.

**Live verification was not performed for this table.** Unlike [[DEBUG-02]], no VNC session backs
these rows; every claim is traced to source. Rows whose real subject is *what the user sees in a
dialog* are marked in the Verification section as un-assertable headlessly.

## Requirements & Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `DEBUG-06-01` | **Edit-time validation exists** | **M** | **Full** | `LuaRunConfiguration.checkConfiguration()` is overridden (`run/LuaRunConfiguration.kt:275-285`), so the dialog validates as the user types and the invalid-config badge is computed. Note that [[RUN-04]]'s requirements and risks still assert this override does **not** exist — that document is stale, not this one. |
| `DEBUG-06-02` | **Fatal problems refuse the launch** | **M** | **Not Implemented** | A target with no resolvable runtime cannot run, yet `checkConfiguration` throws a **bare** `RuntimeConfigurationException` (`:277`). Per `RunManagerImpl.kt:154-167` that is the "execution should still be allowed" tier, so Run/Debug stays enabled and the failure is deferred to `startProcess()`'s `ExecutionException`. `RuntimeConfigurationError` appears **nowhere** in Lunar (`grep -rn RuntimeConfigurationError src/` → 0 hits), so no Lunar run configuration of any type can block a launch. |
| `DEBUG-06-03` | **Non-fatal problems warn, not error** | **S** | **Partial** | The one deliberate non-fatal case is correct: an empty script name throws `RuntimeConfigurationWarning("No script file configured")` (`:283`), matching the intentional `lua -v -i` REPL fallback ([[RUN-04]]-04). Every other check sits at the wrong tier, so the ladder is used but not used correctly. |
| `DEBUG-06-04` | **Validation throws only the contract's types** | **M** | **Full** | Both branches throw `RuntimeConfigurationException` subtypes. This matters because `SingleConfigurationConfigurable.getValidateAction` catches only `ConfigurationException` (`:317-331`); anything else escapes the non-blocking read action and is reported as an IDE error instead of a validation result. |
| `DEBUG-06-05` | **Validation is cheap enough for per-keystroke use** | **S** | **Partial** | `resolveInterpreter()` → `LuaToolResolver.resolveRuntimeDetailed` is in-memory registry work (`toolchain/resolve/LuaToolResolver.kt:63-84`). But on the explicit-path miss branch it calls `adHocRuntime` (`run/LuaRuntimeResolution.kt:29-49`), which performs two filesystem `stat`s (`File.exists()`, `File.canExecute()`) **per keystroke in any field of the editor**. Correct thread (pooled, read action), unbounded I/O frequency. |
| `DEBUG-06-06` | **No runtime configured is detected** | **M** | **Partial** | Detected (`:276-281`) — but see `-02` for the severity and `-22` for the message. Detection without a blocking verdict is half the requirement. |
| `DEBUG-06-07` | **Runtime path missing or not executable is rejected** | **M** | **Not Implemented** | `resolveConfiguredRuntime` returns a **non-null** ad-hoc tool for any non-empty stored path, existing or not (`run/LuaRuntimeResolution.kt:22-26`). `adHocRuntime` *computes* `LuaToolHealth(fileExists = …, executable = …)` at `:43-48` and then nothing reads it — `grep -rn '\.health' src/main/kotlin/net/internetisalie/lunar/run/` returns nothing. A path pointing at a deleted or non-executable binary passes validation and fails as a process-spawn error. The data needed for this check is already in hand and is discarded. |
| `DEBUG-06-08` | **Runtime version matches the project language level** | **C** | **Not Implemented** | `LuaRegisteredTool` carries `languageLevel` (`toolchain/model/LuaRegisteredTool.kt:38`) and the project carries its own, but nothing compares them in any run/debug path (`grep languageLevel` over `run/` → 0 hits). Debugging 5.4 sources under a 5.1 binary fails at the first `goto`/integer-division token with a runtime syntax error, not a validation message. Priority **C** because the mismatch is loud when it happens. |
| `DEBUG-06-09` | **Script file is configured** | **M** | **Full** | `:282-284`, as a warning — the correct tier, because unset means "interactive REPL", not "broken". |
| `DEBUG-06-10` | **Script file exists** | **M** | **Not Implemented** | A non-empty `scriptName` is passed to the command line verbatim (`:304-306`) with no existence check at either edit time or launch time. Deferred deliberately in [[RUN-04]]'s risks-and-gaps (Gap 2.1, DR-02) as "future work"; it is still open, and for the **Debug** executor it is worse than for Run — a missing script means the process exits before `mobdebug` ever connects, so the user sees a connect timeout, not a missing file. |
| `DEBUG-06-11` | **Script is reachable from the debugger base directory** | **S** | **Not Implemented** | `LuaDebuggerController` derives `baseDir` from the working directory (`run/LuaDebuggerController.kt:75-90`) and every breakpoint position is resolved relative to it (`LuaPosition.createRemotePosition`). A script outside that directory therefore debugs with breakpoints that never bind, and nothing warns. This is the debug-specific analogue of `-10` and has no [[RUN-04]] counterpart. |
| `DEBUG-06-12` | **Working directory exists** | **S** | **Not Implemented** | `effectiveWorkDirectory()` falls back to `project.basePath` (`:228`) and is applied unchecked (`:314-315`). The platform ships this exact check, at the exact tier: `ProgramParametersConfigurator.checkWorkingDirectoryExist` throws `RuntimeConfigurationWarning` (`platform/execution-impl/.../util/ProgramParametersConfigurator.java:246-263`). It is not called. |
| `DEBUG-06-13` | **The mobdebug debuggee is reachable** | **M** | **Partial** | The plugin injects its own `package.path` template and `LUA_INIT` preloader (`:320-335`), so the debuggee does not depend on the user's `package.path` — good design, and the reason this is not a user-facing configuration field. But the "are the assets actually there" check (`Failed to locate plugin directory` / `Failed to locate debugger preloader`) lives in `startProcess()` as an `ExecutionException` ([[RUN-04]]-03). At edit time the configuration reports valid. |
| `DEBUG-06-14` | **Debug port is in range** | **S** | **Full** | `JBIntSpinner(DEFAULT_DEBUG_PORT, 1, 65535)` (`:380`) makes an out-of-range port unrepresentable in the editor, which is stronger than validating it. Default 8172 round-trips (`TestLuaRunConfiguration.testDebugPortRoundTripsThroughEditor`). |
| `DEBUG-06-15` | **Debug port is available** | **S** | **Not Implemented** | Nothing probes the port. `LuaDebuggerController.connect()` binds `ServerSocket(serverPort)` (`:113-116`) **after** the interpreter process has already been spawned; a `BindException` is caught in `LuaDebugProcess` (`:121-131`), which kills the process, calls **`log.error`**, and shows `"Unable to establish connection with debugger:\nAddress already in use"`. Two defects fall out: a user-configuration problem is logged at `error` (an IDE internal-error report), and the message names neither the port nor the field to change. Two Lunar debug sessions on the default 8172 hit this every time. |
| `DEBUG-06-16` | **Environment file path is valid** | **C** | **Won't** | There is nothing to validate — the field is inert. `LuaRunConfiguration` persists `environmentFile` (`:95-99`, `:242-259`) but `EnvironmentVariablesData.configureCommandLine` ignores it entirely (`platform/execution-impl/.../configuration/EnvironmentVariablesData.java:122-127`), and `getEnvironmentFile()` has no consumer anywhere in the platform — it is a carrier for configurations implementing `EnvFilesOptions`, which Lunar does not. Validating a path that is never read would manufacture a check for a feature that does not exist. |
| `DEBUG-06-17` | **Environment files chosen in the editor are applied** | **S** | **Not Implemented** | The prerequisite `-16` is waiting on. `EnvironmentVariablesTextFieldWithBrowseButton` keeps env-file paths in its own `myEnvFilePaths` list, exposed via `getEnvFilePaths()` and surfaced by a disk-icon extension once the typed text parses as an env-file reference (`platform/execution-impl/.../configuration/EnvironmentVariablesTextFieldWithBrowseButton.java:45,83-107,238-246`). `LuaRunSettingsEditor` reads only `.data` (`:414`, `:425`), so anything the user selects there is silently dropped. Lunar also constructs the widget through its **deprecated** no-`Project` constructor (`:377`; deprecation at that file's `:50-57`), leaving the env-file chooser without a project. |
| `DEBUG-06-18` | **A debug target can be created from context** | **S** | **Not Implemented** | No `RunConfigurationProducer` exists for `LuaRunConfiguration`; `plugin.xml:604-605` and `:613-614` register producers only for the **test** and **Redis** types. Right-clicking a `.lua` file offers no *Debug 'x.lua'*, so the only way to reach a debug target is to build one by hand and type the script path — which is what makes `-10` and `-11` reachable in the first place. Already tracked as [[RUN-02]] risks-and-gaps Gap 2.1; repeated here because it is this feature's entry point, not a separate concern. |
| `DEBUG-06-19` | **A remote / attach debug target** | **C** | **Won't** | `LuaDebugRunner.canRun` accepts only `DefaultDebugExecutor` + `LuaRunConfiguration` (`run/LuaDebugRunner.kt:52-58`), and only one `<configurationType>` exists for Lua execution (`plugin.xml:600-601`). There is no attach-to-running-process target to configure, matching [[plugin-feature-comparison]]'s *Remote debugger* row (Lunar ✗). "Debug target" here always means "a local script this IDE launches". Not a gap in this feature; a bound on it. |
| `DEBUG-06-20` | **A fixable problem offers a quick fix** | **C** | **Not Implemented** | `RuntimeConfigurationError`/`Warning` both take a `ConfigurationQuickFix` and the dialog wires it to a button (`SingleConfigurationConfigurable.getQuickFix:345-356`). Lunar passes one nowhere (`grep -rn ConfigurationQuickFix src/` → 0 hits); "no runtime configured" instead ships prose telling the user which menu path to walk. |
| `DEBUG-06-21` | **Messages use the ratified toolchain vocabulary** | **S** | **Full** | Post-[[BUG-378]] the user-visible strings say *runtime*, not *interpreter*: `"No Lua runtime is configured…"` (`:277-280`), the editor labels `"Runtime"` (`:398`) and `"Runtime arguments"` (`:403`). The persisted option key is still `interpreter` (`:72-76`), which is correct — renaming it would discard saved configurations. |
| `DEBUG-06-22` | **One source for the not-configured message** | **C** | **Not Implemented** | `LuaToolResolver.notConfiguredMessage(kindId)` exists for exactly this (`toolchain/resolve/LuaToolResolver.kt:93-97`) and produces *"No usable Lua configured. Add or bind one under Settings \| Languages & Frameworks \| Lua \| Toolchain."* `LuaRunConfiguration` hand-rolls a near-duplicate literal instead (`:277-280`), and `LuaTestRunConfiguration` a third variant, *"Runtime is not defined"* (`run/test/LuaTestRunConfiguration.kt:289`). Three wordings for one condition is what [[BUG-378]] swept and what will drift again. |
| `DEBUG-06-23` | **The message tells the user what to do** | **S** | **Partial** | The main configuration's message names the exact settings path; the test configuration's *"Runtime is not defined"* names nothing, and [[BUG-387]]'s standard — a binding label must distinguish "no default" from "nothing resolved" — is not met by either. Whether the named page then **resolves** the condition is the part this document cannot assert: [[BUG-381]] is the precedent for a settings page that is reachable and still leaves the user's target unusable. See Verification. |

## Verification

**Existing coverage.** `TestLuaRunConfiguration`
(`src/test/kotlin/net/internetisalie/lunar/run/TestLuaRunConfiguration.kt`) is the only test that
touches this feature: `testCheckConfigurationThrowsWithoutRuntime` (`:177-187`) for `-06` and
`testDebugPortRoundTripsThroughEditor` (`:155-174`) for `-14`. `LuaTestRunConfigurationTest`
(`src/test/kotlin/net/internetisalie/lunar/run/test/LuaTestRunConfigurationTest.kt:154`) covers the
test configuration's equivalent. Everything else in the table is uncovered.

**The one existing validation test cannot fail on the defect this table found.** It asserts
`assertFailsWith<RuntimeConfigurationException>`, and `RuntimeConfigurationError` and
`RuntimeConfigurationWarning` are both **subclasses** of that type
(`platform/execution/.../RuntimeConfigurationError.java:23`,
`platform/execution/.../RuntimeConfigurationWarning.java:10`). The assertion therefore passes at
every rung of the ladder and is blind to `-02` and `-03` — exactly the requirements that are wrong.
A test for `-02` must assert the **exact** class.

**What a unit test can and cannot settle.** `-02`, `-04`, `-07`, `-10`, `-12`, `-14`, `-16`, `-17`
and `-22` are decidable headlessly with `BasePlatformTestCase`: construct the configuration, call
`checkConfiguration()`, assert the thrown class and the message. `-15` becomes decidable by binding
the port first and asserting the pre-launch verdict — once such a check exists.

`-03`, `-20`, `-21` and `-23` are about **what renders in the Run/Debug Configurations dialog**: a
red banner versus a yellow one, a quick-fix button, whether the wording is intelligible. A unit test
can assert the exception that produces the banner; it cannot assert that the user sees anything
useful. Those rows are graded on the exception and remain **unverified as UX** — the live check
belongs to the `verify-in-ide` gate, which was not run for this document.

**Rows found by writing this table and recorded nowhere else in the repo:** `-02` (no Lunar run
configuration ever raises the platform's blocking severity), `-07` (health flags computed and
discarded), `-11`, `-15` (the port is never probed, and the failure is reported through `log.error`),
`-17` (env files silently dropped) and `-22`. None has a bug report. `-10` and `-18` are already
tracked in [[RUN-04]] and [[RUN-02]] respectively.
