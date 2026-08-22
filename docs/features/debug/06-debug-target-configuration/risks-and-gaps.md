---
id: "DEBUG-06-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "RUN-06"
folders:
  - "[[features/debug/06-debug-target-configuration/requirements|requirements]]"
---

# DEBUG-06: Risks & Gaps

Three kinds of entry, kept apart on purpose:

- **§1 Critical Risks** — what can go wrong building [design.md](design.md).
- **§2 Design Gaps** — decisions the design took, or deliberately declined to take, that a reviewer
  should be able to challenge.
- **§3 Defects found in `requirements.md` itself** — recorded here and **not fixed in place**.
  Two are line-number citations proven wrong with `grep` and corrected below as citations only; the
  rest are substantive and are left standing so the requirement can still fail. A specification
  regenerated from the implementation cannot fail, which is the defect
  [[BUG-450]] §4 records against [[DEBUG-07]].

---

## 1. Critical Risks

### Risk 1.1: Raising the tier to `FATAL` refuses launches that used to start

- **Impact**: `RuntimeConfigurationError` makes `RunManagerImpl.canRunConfiguration` return `false`
  (`platform/execution-impl/src/com/intellij/execution/impl/RunManagerImpl.kt:162-164`), disabling
  Run *and* Debug. Today every Lunar validation is advisory, so any user whose configuration is
  wrong-but-workable — for instance a runtime path resolved through a symlink the plugin cannot
  `stat`, or a script created by a build step that runs before launch — goes from "it started" to
  "the button is grey". This is the intended behaviour change of `DEBUG-06-02`, and it is also the
  only way this feature can regress someone.
- **Likelihood**: medium.
- **Mitigation**: only four conditions are `FATAL` and each is provably unrunnable — no runtime
  resolves at all; the runtime path does not exist; the runtime path is not executable; the script
  path does not exist. Everything softer stays at `WARNING`/`ADVISORY` (design §3.2). The
  *working directory* check in particular is `WARNING`, matching the platform's own
  `ProgramParametersConfigurator.checkWorkingDirectoryExist`
  (`platform/execution-impl/src/com/intellij/execution/util/ProgramParametersConfigurator.java:244-263`),
  even though [[BUG-455]] §5 proposed `Error` for it — see Gap 2.1.
- **Residual**: a script generated at launch time by an external step is genuinely blocked. There is
  no way to distinguish it from a typo, and `DEBUG-06-10` is a `Must`.
- **Also in scope: the *test* configuration.** §2.6 reroutes `LuaTestRunConfiguration`'s runtime
  branch through the same ladder, so a test configuration whose `interpreter` points at a deleted
  binary goes from "it started" to a refused launch too. In the other direction that change is a
  relaxation: an empty `interpreter` with a project default runtime stops reporting an error it
  should never have reported (design §2.6's table).

### Risk 1.2: `File.canExecute()` is always `true` for root, so TC-06-07b can silently skip

- **Impact**: the non-executable-runtime test cannot construct its fixture when the test JVM runs as
  root — `setExecutable(false)` succeeds and `canExecute()` still returns `true`. A skipped test
  reads as coverage.
- **Likelihood**: medium. `gce-builder` runs as `builder`, and the `lunar-ci` per-VM pool runs as a
  normal user, but `ubuntu-latest` is tier2's shared **dind** pool where a root container is
  plausible.
- **Mitigation**: design §9 requires `Assumptions.assumeTrue(!file.canExecute())` as the guard, so
  the case **skips loudly** rather than passing vacuously. It is not an `if (…) return`.
- **Residual**: on a root runner, `-07`'s executable half is covered only by TC-06-07a's sibling
  (existence) and by the `verify-in-ide` pass. DR-02 measures which runners are affected.

### Risk 1.3: `ConfigurationQuickFix` is `@ApiStatus.Experimental`

- **Impact**: `DEBUG-06-20`'s quick fix binds to
  `com.intellij.openapi.options.ConfigurationQuickFix`, marked `@ApiStatus.Experimental`
  (`platform/ide-core/src/com/intellij/openapi/options/ConfigurationQuickFix.java:8`). A signature
  change breaks the build on a platform bump.
- **Likelihood**: low — the interface is one method (`void applyFix(@NotNull DataContext)`) and both
  `RuntimeConfigurationError` (`:28-31`) and `RuntimeConfigurationWarning` (`:15-18`) have taken it
  in their public constructors since 2021.
- **Mitigation**: the fix is a single 4-line class (design §3.8) attached to one check. If the API
  moves, drop the second constructor argument; `DEBUG-06-20` is priority **C** and its loss does not
  affect any other row.

### Risk 1.4: `EnvFilesOptions` and `CommonProgramRunConfigurationParameters` live in `execution-impl`

- **Impact**: `com.intellij.execution.EnvFilesOptions`
  (`platform/execution-impl/src/com/intellij/execution/EnvFilesOptions.kt:4-7`) and
  `parseEnvFile` (`platform/execution-impl/src/com/intellij/execution/envFile/EnvFileParser.kt:4`)
  are in an `-impl` module, which carries no compatibility promise.
- **Likelihood**: low. Lunar already imports two `execution-impl` types in this very file —
  `com.intellij.execution.configuration.EnvironmentVariablesData` and
  `EnvironmentVariablesTextFieldWithBrowseButton` (`run/LuaRunConfiguration.kt:5-6`) — so the
  exposure is pre-existing, not new.
- **Mitigation**: `EnvFilesOptions` is one property and `parseEnvFile` is one pure function; both are
  reimplementable in under 30 lines if they move. Recorded so the choice is deliberate rather than
  inherited.

### Risk 1.5: The `LuaPathFacts` TTL makes a `chmod +x` invisible for up to 2 s

- **Impact**: a user who fixes a permission or creates a missing file may see the dialog disagree
  with the filesystem for up to two seconds.
- **Likelihood**: certain, by construction.
- **Mitigation**: two seconds is under one edit round-trip, and the memo is keyed per path so any
  *other* edit is unaffected. The alternative — VFS-driven invalidation via
  `LuaToolHealthMonitor` (`toolchain/health/LuaToolHealthMonitor.kt:43-79`) — does not apply,
  because that monitor watches only *inventory* binaries and an ad-hoc run-configuration path is not
  in the inventory (design §10.6).

### Risk 1.6: The new context producer competes with two existing producers

- **Impact**: `LuaTestRunConfigurationProducer.isTestFile` matches any file whose name merely
  *contains* `"test"` or `"spec"`, or whose text contains `"busted"`/`"lunity"`
  (`run/test/LuaTestRunConfigurationProducer.kt:141-152`). A second, unconditional Lua producer
  could push the test entry out of the context menu.
- **Likelihood**: medium if the guard is omitted.
- **Mitigation**: design **§3.9.3** specifies `isPreferredConfiguration` as a numbered predicate
  that yields to both `LuaTestRunConfiguration` and `LuaRedisRunConfiguration` rather than
  re-deriving the heuristic, and design **§3.9.1 step 5** makes the new producer decline outright on
  a REDIS target — the exact mirror of `LuaRedisRunConfigurationProducer.kt:31`. The **tests** are
  **TC-06-18c** (a `.lua` file on a REDIS target ⇒ `LuaRunConfigurationProducer` returns null;
  mutation: delete step 5) and **TC-06-18d** (`yieldsTo(LuaTestRunConfiguration)` is true; mutation:
  narrow `yieldsTo` to the Redis arm only).
- **Correction — the earlier mitigation tested something else.** An earlier draft of this row offered
  *"the exit criteria of Phase 6 require `TestLuaRedisRunConfigurationProducer` to stay green"*. That
  is not a guard against this risk. That suite instantiates `LuaRedisRunConfigurationProducer()`
  directly (`src/test/kotlin/net/internetisalie/lunar/redis/run/TestLuaRedisRunConfigurationProducer.kt:28,43`)
  and never references `LuaRunConfigurationProducer` at all, so it is green whether or not the new
  producer also offers on a REDIS target — the precise failure this risk describes. It remains a
  required exit criterion (nothing here should break it); it is simply not evidence about this risk.

---

## 2. Design Gaps

### Gap 2.1: `DEBUG-06-12` and [[BUG-455]] §5 disagree on the working-directory tier

- **Question**: [[BUG-455]] §5 lists "missing working directory" among the conditions that should
  throw `RuntimeConfigurationError`. `DEBUG-06-12` calls for a `RuntimeConfigurationWarning` and
  cites the platform method that produces one.
- **Decision (taken, not deferred)**: `WARNING`, per the requirement. The platform's own
  `checkWorkingDirectoryExist` throws `RuntimeConfigurationWarning`
  (`ProgramParametersConfigurator.java:249,261`), and a missing working directory does not make a
  target unrunnable — `effectiveWorkDirectory()` already falls back to `project.basePath`
  (`run/LuaRunConfiguration.kt:228`). Requirements outrank a bug report's fix-strategy prose.
- **Consequence for BUG-455**: its §5 sentence is wrong on this one condition. Do not "fix" it by
  raising the tier; fix the bug report when it is closed.

### Gap 2.2: `DEBUG-06-13` is satisfied at the pre-launch tier, not at edit time

- **Question**: `-13` is a `Must` graded **Partial** because the mobdebug-asset check lives in
  `startProcess()`. Should the design surface it in the dialog?
- **Decision (taken)**: no. Argued in design §10.4 — a missing `lua/debug.lua` in the plugin
  distribution is a plugin-integrity failure no configuration edit can repair, and
  `checkConfiguration()` receives no executor, so a Run-only configuration would show a red banner
  about a Debug-only asset. The check is hoisted into `LuaDebugRunner.doExecute()` **before** the
  interpreter spawns, which satisfies this feature's own scope sentence ("the edit-time /
  **pre-launch** half"). A reviewer who disagrees should challenge §10.4, not the code.
- **Coverage**: the hoisted gate is unit-tested — TC-06-13a/b/c against §2.7's two-argument
  `internal` seam. `-13` is a `Must` and does **not** rest on a manual checklist for its logic; only
  its packaging half does (see Test Case Gaps).

### Gap 2.3: `DEBUG-06-08` warns only when the runtime is *older* than the project level

- **Question**: should a *newer* runtime than the project's language level also warn?
- **Decision (taken)**: no (design §3.5 step 4). Running 5.1 sources on a 5.4 binary is the normal
  upgrade path; 5.4 sources on a 5.1 binary fails at the first `goto` or `//`. TC-06-08b is the test
  that pins the asymmetry, and its named mutation (`< 0` → `!= 0`) is exactly this question.

### Gap 2.4: `LuaRedisRunConfiguration` is not folded into the pipeline

- **Question**: three configurations override `checkConfiguration()`
  (`run/LuaRunConfiguration.kt:275`, `run/test/LuaTestRunConfiguration.kt:287`,
  `redis/run/LuaRedisRunConfiguration.kt:242`). Two are converted; why not the third?
- **Decision (taken)**: the Redis configuration validates a *connection* and an FCALL function name
  against a library file (`:242-252` and `validateFunctionNameAgainstLibrary`), not a Lua execution
  target. It shares no check with `LOCAL_SCRIPT`. Converting it would mean inventing a fourth check
  list to hold one-off Redis logic. If a future REDIS feature wants the severity ladder, the
  pipeline is already parameterised on the check list (design §2.10) — that is a one-line adoption
  at that time.

### Gap 2.5: [[BUG-454]] is out of scope, and the design agrees with that assessment

- **Question**: the dispatcher flagged [[BUG-454]] (a mid-run `SETB` holds `writeMutex` and wedges
  the session) as "probably out of scope".
- **Decision (taken)**: **agreed, out of scope.** BUG-454 is a *session-protocol* defect in
  `run/LuaDebugConnection.kt:234-244` — a request/response await inside a lock, against a debuggee
  that sends no reply. It is reachable only after a debug session is running, and no
  `checkConfiguration()` verdict, port probe, or asset check can prevent or detect it. The only
  file this feature and BUG-454 both touch is `run/LuaDebugProcess.kt`, and at different lines
  (`:122`/`:127` here, the `writeMutex` path there), so the two can land in either order.

### Gap 2.6: no `human-verification-checklists.md` is authored yet

- **Question**: `-03`, `-20`, `-21` and `-23` are graded on the exception a unit test can observe,
  but their real subject is what renders in the Run/Debug Configurations dialog — a red banner
  versus a yellow one, whether the quick-fix button appears, whether the wording is intelligible.
  `-18`'s real subject is a context-menu entry.
- **Resolved by**: DR-03. The checklist must be written before the feature can be called done, and
  the `verify-in-ide` pass (V6 in [implementation-plan.md](implementation-plan.md)) is the gate.

### Gap 2.7: the debug-port probe binds loopback; the real listener binds the wildcard

- **Question**: `LuaDebugPortProbe.isAvailable` binds `InetAddress.getLoopbackAddress()` (design
  §3.7), but `LuaDebuggerController.connect()` binds `ServerSocket(serverPort)`
  (`run/LuaDebuggerController.kt:116`) — the wildcard address. Should the probe match?
- **Decision (taken)**: no. The mismatch narrows coverage in one direction only — a port held on a
  *non-loopback* interface passes the probe and still fails the real bind, falling through to the
  existing `LuaDebugProcess` catch. It never blocks a launch that would have worked. Probing the
  wildcard would go the other way and refuse a launch because some unrelated service holds the port
  on one interface. The case `DEBUG-06-15` actually names — two Lunar debug sessions on 8172 — is a
  loopback-and-wildcard collision the probe **does** catch.
- **Note**: this is a *scope* residual and is distinct from the *timing* race also accepted in §3.7.
  An earlier draft recorded only the latter.

---

## 3. Defects found in `requirements.md`

Recorded, **not** edited into the requirement rows. Each is grounded with an executed `grep` or a
`file:line` in `~/Documents/src/lua/intellij-community`.

### 3.1 Two provably-wrong `file:line` citations — corrected here as citations only

| Row | Cited | Actual | Evidence |
| :-- | :-- | :-- | :-- |
| `DEBUG-06-18` | "`plugin.xml:604-605` and `:613-614` register producers only for the **test** and **Redis** types" | the producers are at **`:606-607`** and **`:615-616`**; `:604-605` and `:613-614` are the two `<configurationType>` elements | `grep -n "runConfigurationProducer" src/main/resources/META-INF/plugin.xml` → `606`, `615` |
| `DEBUG-06-19` | "only one `<configurationType>` exists for Lua execution (`plugin.xml:600-601`)" | the Lua run type is at **`:602-603`**; `:600` is the tail of a `<projectConfigurable>` element | `grep -n "LuaRunConfigurationType" src/main/resources/META-INF/plugin.xml` → `603` |

The *substance* of both rows is unaffected: there is still no producer for `LuaRunConfiguration`,
and there is still only one Lua-execution configuration type. Two further citations are off by one
because they include a KDoc line in the range (`-05`'s `LuaRuntimeResolution.kt:29-49` for a
function spanning `30-50`; `-07`'s `:22-26` for a body spanning `23-27`); both are within tolerance
and are not corrected.

### 3.2 `DEBUG-06-17` is wrong about how env-file paths are reached — twice

The row says the paths are "exposed via `getEnvFilePaths()` and surfaced by a disk-icon extension
once the typed text parses as an env-file reference".

1. **`getEnvFilePaths()` is package-private.** `platform/execution-impl/…/EnvironmentVariablesTextFieldWithBrowseButton.java:245`
   declares `@NotNull List<String> getEnvFilePaths()` with **no** access modifier, so it is visible
   only inside `com.intellij.execution.configuration`. Lunar cannot call it. The public route is
   `EnvironmentVariablesComponent.getEnvFilePaths()` (`EnvironmentVariablesComponent.java:82-84`),
   which delegates to it. **An implementer following the row literally writes code that does not
   compile** — design §2.8 routes through the component instead.
2. **Typing does not create an env-file entry.** `addEnvFilesExtension()` is private and its only
   call site is inside `setEnvFilePaths` (`:83` declared, `:241` called). The text-field listener
   calls `updateEnvFiles(...)`, which returns immediately when `myEnvFilePaths.isEmpty()` (`:250`).
   So the disk icon appears only after `setEnvFilePaths(...)` has been called at least once — which
   is why design §2.8 requires calling it from `resetEditorFrom` **even with an empty list**.

The row's *conclusion* — that anything the user selects there is silently dropped because
`LuaRunSettingsEditor` reads only `.data` (`:414`, `:425`) — is correct, and TC-06-17a is the test.

### 3.3 `DEBUG-06-22`'s quoted message is reachable only with the right `kindId`

The row states that `LuaToolResolver.notConfiguredMessage(kindId)` "produces *No usable Lua
configured…*". True for `kindId = "lua"`. But the id the resolver itself reports on failure is the
**synthetic** `"runtime-capability"` (`toolchain/resolve/LuaToolResolver.kt:14`, returned at `:83`
inside `LuaToolResolution.Unresolved`), and `LuaToolKindRegistry.BUILT_IN` contains no kind with
that id — its ids are `lua`, `luajit`, `tarantool`, `luarocks`, `luacheck`, `stylua`, `luacov`,
`busted`, `redis-server`, `valkey-server`, `lua-language-server`
(`registry/LuaToolKindRegistry.kt:19,48,66,84,98,110,122,134,146,158,170`). `notConfiguredMessage`
falls back to the raw id when `findById` misses (`:94`), so the naive wiring renders

> No usable **runtime-capability** configured. Add or bind one under Settings | Languages & Frameworks | Lua | Toolchain.

Design §3.3 pins the constant and TC-06-22a's second assertion is the test that catches it. This is
a trap the requirement's wording walks an implementer into; it is not a wrong requirement.

### 3.4 `DEBUG-06-22` undercounts the hand-rolled sites (3 wordings, but **5** sites)

The row names two sites plus the unused canonical function. Executed
(`grep -rn "No Lua runtime is configured" src/`):

| Wording | Sites |
| :-- | :-- |
| `"No Lua runtime is configured. Add one under Settings \| … \| Toolchain."` | `run/LuaRunConfiguration.kt:277` (edit-time) · `run/LuaRunConfiguration.kt:296` (launch-time) · `run/test/LuaTestCommandLineState.kt:124` · `run/console/LuaConsoleRunner.kt:40` |
| `"Runtime is not defined"` | `run/test/LuaTestRunConfiguration.kt:289` |
| `LuaToolResolver.notConfiguredMessage()` | defined at `toolchain/resolve/LuaToolResolver.kt:93`; **zero production callers** (`grep -rn notConfiguredMessage src/` returns the definition and `LuaToolResolverTest.kt:61` only) |

The two `ExecutionException` sites (`LuaTestCommandLineState`, `LuaConsoleRunner`) plus
`LuaRunConfiguration.kt:296` are **launch-time**, so they are outside this feature's scope
(design §3.3). They are listed here so the sweep is not lost — see TBD-2.

### 3.5 The Verification section overstates the test-configuration coverage

It says `LuaTestRunConfigurationTest.kt:154` "covers the test configuration's equivalent" of
`testCheckConfigurationThrowsWithoutRuntime`. It does not. That method sets
`config.options.interpreter = "/usr/bin/lua5.4"` — i.e. it exercises the **`testTarget`** branch,
never the runtime branch — and its assertion is:

```kotlin
try { config.checkConfiguration(); fail("Expected RuntimeConfigurationException") }
catch (e: Exception) { /* Expected */ }
```

which accepts **any** `Exception`, including an `NPE`. It is a third instance of the [[BUG-461]] §3
species and belongs in that report. Phase 2 replaces it with an exact-class assertion as part of the
`-22`/`-23` work.

### 3.6 `DEBUG-06-08`'s phrasing puts `languageLevel` on the wrong type

"`LuaRegisteredTool` carries `languageLevel` (`toolchain/model/LuaRegisteredTool.kt:38`)". The
`file:line` is right; the sentence is not. Line 38 is inside **`LuaRuntimeInfo`**
(`data class LuaRuntimeInfo(…, val languageLevel: LuaLanguageLevel, …)`, `:35-41`), reached as
`tool.runtime?.languageLevel`. `LuaRegisteredTool` itself (`:6-16`) has no such field. This matters
in exactly one place: an **ad-hoc** tool has `runtime = null` (`run/LuaRuntimeResolution.kt:38`), so
there is no level to compare and check 7 must short-circuit (design §3.2). A reader of the
requirement alone would expect the level always to be available.

### 3.7 `DEBUG-06-14`'s cited test does not test what the row claims

The row grades `-14` **Full** on the argument that `JBIntSpinner(DEFAULT_DEBUG_PORT, 1, 65535)`
(`:380`) makes an out-of-range port unrepresentable — which is sound — and then cites
`TestLuaRunConfiguration.testDebugPortRoundTripsThroughEditor` as evidence. That test sets `9000`
and asserts it survives a `resetFrom`/`applyTo` cycle (`TestLuaRunConfiguration.kt:155-174`); it
never attempts a value outside `1..65535`, so it is evidence for *round-tripping*, not for *range*.
The grade is right; the citation supports a different claim. No new test is proposed — design §6
records that adding a range check would be dead code.

### 3.8 Collateral: two neighbouring documents are stale, and are not edited here

- **[[RUN-04]] is stale at two points.** Its `requirements.md` Out-of-Scope section states "The main
  run config does **not** override `checkConfiguration()`", and its `risks-and-gaps.md` Gap 2.1
  repeats it with a grep as evidence. Both are false against `main`: the override is at
  `run/LuaRunConfiguration.kt:275-285`, which is `DEBUG-06-01`. RUN-04's `design.md` §RUN-04-02 is
  stale in a third way — it names `newLuaInterpreterCommandLine(interpreter)` and
  `command/LuaCommandLine.kt:32`, while the shipped code calls
  `LuaInterpreterCommandLines.forBinary(Path.of(interpreter.path))`
  (`run/LuaRunConfiguration.kt:302`) and no `command/LuaCommandLine.kt` exists
  (`find src/main -name LuaCommandLine.kt` → nothing). RUN-04 is `status: done` and is **not edited
  by this feature** — recorded per the dispatcher's instruction.
- **The epic table (`docs/features/debug/requirements.md:23`) still records `DEBUG-06` as `Full`**,
  which `-02` refutes, and `DEBUG-05` ("Remote Debugging") as **Full** at `:22`, which `-19`
  refutes — `LuaDebugRunner.canRun` accepts only `DefaultDebugExecutor` + `LuaRunConfiguration`
  (`run/LuaDebugRunner.kt:52-57`) and there is no attach target. Not edited here; DEBUG-05 is being
  planned separately and owns its own row.

---

## Technical Debt & Future Work

- **TBD-1: `.sh` / `.bat` environment scripts are not executed.** Design §10.3 declines the
  platform's `configureEnvsFromFiles(config, parse = true)` on two grounds — it is
  `@ApiStatus.Experimental` (`platform/execution-impl/src/com/intellij/execution/util/EnvFilesUtil.kt:36`),
  and at `:45-46` it calls `ProgressManager.getGlobalProgressIndicator()` (declared `@Nullable`)
  followed by `indicator.withPushPop { … }`, an extension on a **non-null** `ProgressIndicator`
  (`platform/core-impl/src/com/intellij/openapi/progress/ProgressIndicatorEx.kt:12`), so a call with
  no global indicator NPEs. Revisit when the API leaves `@Experimental`.
- **TBD-2: the launch-time message sweep.** The three `ExecutionException` sites of §3.4
  (`run/LuaRunConfiguration.kt:296`, `run/test/LuaTestCommandLineState.kt:124`,
  `run/console/LuaConsoleRunner.kt:40`) still hand-roll the runtime message. Routing them through
  `LuaTargetMessages.noRuntimeConfigured()` is a two-line change per site but touches RUN-04's and
  RUN-03's shipped behaviour, so it is deliberately out of DEBUG-06.
- **TBD-3: the `LuaRunSettingsEditor` label sweep.** 27 of 27 labelled rows across the four
  run-config editors lack the trailing colon the platform's own `Name:` row has
  (`docs/engineering-contract.md` §6, COLONS). Phase 5 fixes the one label it edits; the remaining
  seven in this editor and the other three editors are a separate pass — `docs/engineering-contract.md:164-166`
  (`- **SCOPE:**`) says in terms: *"Do not open a retroactive sweep; the surviving `FormBuilder`
  run-config editors are acceptable until touched. Fix a surface when you are already editing it."*
- **TBD-4: `environmentFile` stays inert.** `DEBUG-06-16` is a `Won't`; the `StoredProperty` at
  `run/LuaRunConfiguration.kt:95-99` is retained so existing `.idea/runConfigurations/*.xml` do not
  lose a field. Delete it only alongside a deliberate settings break.
- **TBD-5: ten new user-visible strings sit outside `LuaBundle` — a convention split, deliberately
  taken.** Design §3.3 decides `LuaTargetMessages` holds literals. That decision is now made on a
  *corrected* premise: an earlier revision of §3.3 claimed Lunar has no message bundle and backed it
  with two executed commands that searched the wrong names. Lunar does have one —
  `src/main/kotlin/net/internetisalie/lunar/LuaBundle.kt:15-18`,
  `src/main/resources/net/internetisalie/lunar/LuaBundle.properties` (145 lines, `# debugging` at
  `:109`), 11 caller files / 22 live call sites including `run/LuaExecutionStack.kt:28`, and nine
  `plugin.xml` references (`:468,470,475,480,494,496,510,682,686`). §3.3 records the corrected facts
  and the four reasons for staying on literals. The residual to carry forward:
  - **The split is real.** `run/` will hold ten bundle-less diagnostics beside one bundle-backed
    label. It is not a capability limit: the bundle already holds prose
    (`refactoring.rename.unsupported`, `LuaBundle.properties:145`) and interpolates (`vararg
    params`, `LuaBundle.kt:25`).
  - **Any future migration must START at `LuaToolResolver.notConfiguredMessage`
    (`toolchain/resolve/LuaToolResolver.kt:93-97`)**, not at `LuaTargetMessages`. That message is
    composed at call time from `LuaToolKindRegistry` display names, so a migration that skips it
    reproduces exactly the partial convention §3.3 reason 1 declines. It is a `toolchain/` change,
    outside DEBUG-06's scope — which is why the migration is deferred rather than performed here.
  - **Its real scope** is the whole diagnostic surface, not these ten: the literal
    `checkConfiguration` / `ExecutionException` sites across `run/` and `redis/` enumerated in §3.3
    reason 4, plus TBD-2's three launch-time sites. Bundling ten and leaving twenty is a worse end
    state than either extreme.
  - **Contract §6 does not block this.** `docs/engineering-contract.md:163` posits *a bundle
    assertion that no control label is Title Case*; executed, `grep -rln LuaBundle src/test/kotlin/`
    returns one file (`lang/insight/LuaLineMarkerTest.kt`) — the assertion does not exist yet, and
    when written it will cover **control labels**, which these validation messages are not.
  - **This discharges [[DEBUG-05]]'s `DEBUG-05-00-DR-01`**, which asked for exactly two things: the
    correction to §3.3 (made) and a once-only literals-vs-bundle decision (taken, as literals).
    DEBUG-05's own leaning was the same option, so DEBUG-05 §2.8's two literal messages —
    `remoteRootUnset()` and `bindHostUnresolvable(host)` — stand unchanged and the two features do
    not diverge. That file is under review by another agent and is **not edited from here**; closing
    DR-01 is its owner's call.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
| :-- | :-- | :-- | :-- |
| DEBUG-06-00-DR-01 | On the builder VM, add a temporary run configuration whose runtime path points into an unreachable NFS/autofs mount and measure (a) the wall time of **one** `stat` on that path, and (b) the wall time of a typing burst of 20 keystrokes with and without `LuaPathFacts`. **What it settles:** (b) is the memo's claim and is expected to improve ~20× ; (a) is the part the memo does **not** improve at all, and is the number that decides whether §3.4 needs off-thread staleness instead of a TTL. Design §3.4 no longer asserts a latency bound, so this task measures an open question rather than confirming a stated one. Revert with the `temporary-edits` skill. | Risk 1.5, design §3.4 | todo |
| DEBUG-06-00-DR-02 | Determine the UID the test JVM runs as on each of the three lanes (`gce-builder`, `lunar-ci`, `ubuntu-latest` dind) — `id -u` in a throwaway job. If any is root, record it against Risk 1.2 so TC-06-07b's skip is understood rather than discovered. | Risk 1.2 | todo |
| DEBUG-06-00-DR-03 | Author `human-verification-checklists.md` and run one `verify-in-ide` pass covering: red banner for a missing runtime, yellow banner for an unset script, the quick-fix button opening the Toolchain page, and *Debug 'main.lua'* on the `.lua` context menu. | Gap 2.6; `-03`, `-18`, `-20`, `-21`, `-23` | todo |
| DEBUG-06-00-DR-04 | **Confirmation only — the decision is taken in design §2.8.1.** The doubled label is not a possibility to investigate; it is certain (`EnvironmentVariablesComponent extends LabeledComponent`, `EnvironmentVariablesComponent.java:26`, setting its own title at `:53` from `ExecutionBundle.properties:292`). §2.8.1 resolves it: keep the `FormBuilder` label (with its colon), clear the component's own with `labelLocation = WEST` + `text = ""`, per `ShRunConfigurationEditor.java:76-78`. DR-04 is the `verify-in-ide` screenshot that confirms exactly one *Environment variables:* label renders and that the emptied label leaves no gap. | Phase 5, contract §6 (`docs/engineering-contract.md:141-143`, `:164-166`) | todo |

## Test Case Gaps

- **`-13`'s asset branch IS unit-testable, and is tested — TC-06-13a/b/c.** An earlier draft of this
  bullet claimed the opposite, on the grounds that `LuaFileUtil.pluginVirtualDirectory` resolves via
  `PluginManagerCore.getPlugin(PluginId.getId(LuaPlugin.ID))` (`util/LuaFileUtil.kt:16-20`), which a
  light fixture cannot control. That was a claim made from reading, and this repo ships the
  counterexample: `LuaFileUtilTest.testGetPluginVirtualDirectoryChildMissingReturnsNull`
  (`src/test/kotlin/net/internetisalie/lunar/util/LuaFileUtilTest.kt:40-42`) exercises the null
  return from a plain `BasePlatformTestCase`. It is admittedly a weak seam on its own — a null return
  there does not distinguish "no descriptor" from "descriptor present, no such child" — so design
  §2.7 removes the dependency instead of testing around it: `checkDebugTargetReady(debugPort,
  pluginLuaDirectory)` takes the asset directory as an **argument**, and `doExecute` resolves it. The
  three cases (`null`, a real directory without `debug.lua`, one with it) are then trivially
  constructible with `LocalFileSystem.getInstance().refreshAndFindFileByNioFile(...)` — the idiom at
  `LuaFileUtilTest.kt:31,48`. This is the same seam Phase 4 already needed for TC-06-15b, so it costs
  nothing extra.
- **What remains a DR-03 checklist item for `-13`** is packaging, not logic: that the *distributed*
  plugin really contains `lua/debug.lua`. The test JVM's plugin path is the build output, not the
  packaged zip, so no unit test can assert it.
- **`-01` has no test of its own** and is asserted structurally: every TC in design §9 calls
  `checkConfiguration()` and would fail if the override were removed.
- **The race in §3.7 is untested.** No test binds the port *between* the probe and
  `LuaDebuggerController.connect()`'s bind. It is accepted (design §3.7) and the existing
  `LuaDebugProcess` catch remains the backstop.
- **`-05`'s bound is tested through `LuaPathFacts` (TC-06-05a), not through `checkConfiguration()`.**
  A test that counted syscalls across a `checkConfiguration()` pass would need an injected
  filesystem; the memo's contract is the thing worth pinning, and it is pinned with an injected clock.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- [[BUG-455]] — retired by Phase 2 · [[BUG-461]] — §1/§3 partially retired by Phase 2 · [[BUG-454]] — out of scope (Gap 2.5)
