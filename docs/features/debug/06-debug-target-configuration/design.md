---
id: "DEBUG-06-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "RUN-06"
folders:
  - "[[features/debug/06-debug-target-configuration/requirements|requirements]]"
---

# Technical Design: DEBUG-06 — Debug Target Configuration

Realizes [requirements.md](requirements.md). Every symbol below is either grounded to a real
`file:line` in this repo / `~/Documents/src/lua/intellij-community`, or explicitly marked **NEW**.
Behavioural claims are backed by executed probes — §3.4 (`StatProbe`), §3.6 (`RelProbe`) and §3.7
(`PortProbe`, `ReuseProbe`), all under `~/.cache/claude-scratch/lunar/debug06/`. (There was no §4.3;
an earlier draft of this line pointed at a section that does not exist.)

**One behavioural claim in this design is deliberately NOT executed and is not asserted as settled:**
the wall-clock cost of a single `stat` on an unreachable network mount (§3.4). The design no longer
draws a latency conclusion from it; measuring it is `DEBUG-06-00-DR-01`, still `todo`.

## 1. Architecture Overview

### 1.1 Current State

`LuaRunConfiguration.checkConfiguration()` is nine lines with two branches
(`src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt:275-285`):

```kotlin
override fun checkConfiguration() {
    if (resolveInterpreter() == null) {
        throw RuntimeConfigurationException(              // :277  bare tier — launch ALLOWED
            "No Lua runtime is configured. Add one under " +
                "Settings | Languages & Frameworks | Lua | Toolchain.",
        )
    }
    if (options.scriptName.isNullOrEmpty()) {
        throw RuntimeConfigurationWarning("No script file configured")   // :283
    }
}
```

Three structural defects follow, and all three are requirement rows:

1. **Nothing can refuse a launch.** `RuntimeConfigurationError` is the only tier
   `RunManagerImpl.canRunConfiguration` turns into `return false`
   (`platform/execution-impl/src/com/intellij/execution/impl/RunManagerImpl.kt:154-168`), and it
   occurs **zero** times in `src/` (executed: `grep -rn RuntimeConfigurationError src/ | wc -l`
   → `0`). `DEBUG-06-02`, filed as [[BUG-455]].
2. **The first `throw` wins.** With `throw`-in-place there is no ordering guarantee between a
   warning and a fatal problem, so a benign warning discovered first hides an unrunnable target.
3. **The checks are welded to one configuration class.** `LuaTestRunConfiguration:287-294` and
   `LuaRedisRunConfiguration:242-252` each re-implement their own ladder, with their own wordings.

### 1.2 Prior Art in This Repo — extended, replaced, or left alone

| Existing component | `file:line` | Disposition |
| :-- | :-- | :-- |
| `LuaRunConfiguration.checkConfiguration()` | `run/LuaRunConfiguration.kt:275-285` | **Replaced** by a delegation to `LuaTargetValidator` (§2.6). The two existing conditions survive as checks 1 and 3 of §3.2 — the empty-script *warning* tier is preserved verbatim. |
| `LuaTestRunConfiguration.checkConfiguration()` | `run/test/LuaTestRunConfiguration.kt:287-294` | **Extended**: its runtime branch is rerouted through `LuaTargetMessages.noRuntimeConfigured()` (§3.3, `DEBUG-06-22`/`-23`). Its `testTarget` branch is untouched. |
| `LuaRedisRunConfiguration.checkConfiguration()` | `redis/run/LuaRedisRunConfiguration.kt:242-252` | **Left alone.** It validates a Redis connection and an FCALL name, not a Lua *target*. Out of scope; noted in [risks-and-gaps.md](risks-and-gaps.md) Gap 2.4. |
| `LuaToolResolver.notConfiguredMessage(kindId)` | `toolchain/resolve/LuaToolResolver.kt:93-97` | **Adopted as the single source** for `DEBUG-06-22`. Executed: `grep -rn notConfiguredMessage src/` returns the definition plus one *test* caller (`LuaToolResolverTest.kt:61`) — **zero production callers today**. |
| `LuaToolHealthChecker` / `LuaToolHealth` | `toolchain/health/LuaToolHealthChecker.kt:29-61`, `toolchain/model/LuaRegisteredTool.kt:24-33` | **Consumed, not duplicated.** `adHocRuntime` already computes `fileExists`/`executable` (`run/LuaRuntimeResolution.kt:41-48`); `DEBUG-06-07` is satisfied by *reading* `tool.health`, which currently nothing in `run/` does (executed: `grep -rn '\.health' src/main/kotlin/net/internetisalie/lunar/run/ \| wc -l` → `0`). |
| `isUsable` extension | `toolchain/model/LuaRegisteredTool.kt:32-33` | **Reused verbatim** as the usability predicate. Zero uses in `run/` today. |
| `LuaTestRunConfigurationProducer` | `run/test/LuaTestRunConfigurationProducer.kt:17-173` | **Not duplicated.** The new generic producer (§2.9) yields to it via `isPreferredConfiguration` rather than copying its `isTestFile` heuristic. |
| `LuaRedisRunConfigurationProducer` | `redis/run/LuaRedisRunConfigurationProducer.kt:21-53` | **Mirrored gating.** It declines on a non-REDIS target (`:31`); the new producer declines on a REDIS target, so exactly one offers. |
| `LuaToolHealthMonitor` | `toolchain/health/LuaToolHealthMonitor.kt:43-79` | **Not extended.** It watches *inventory* binaries over VFS. An ad-hoc path typed into a run configuration is not in the inventory, so it is not watched — which is why §2.4 exists. |
| RUN-04's `startProcess()` checks | `run/LuaRunConfiguration.kt:294-307` | **Untouched.** RUN-04 is `done`; its `ExecutionException`s stay as the last line of defence. This feature adds gates *earlier*, it does not move RUN-04's. |

### 1.3 Target State

One severity-aware, configuration-agnostic validation pipeline in a **new** package
`net.internetisalie.lunar.run.validation`:

```
LuaRunConfiguration.checkConfiguration()  ─┐
LuaTestRunConfiguration.checkConfiguration()─┼─► LuaTargetSpec ──► LuaTargetValidator.validate(spec, checks)
(DEBUG-05 attach configuration, later)    ─┘                          │
                                                                      ├─ List<LuaTargetCheck>  (pure, 1 arg each)
                                                                      ├─ LuaPathFacts          (TTL-memoised stat)
                                                                      └─ throws the HIGHEST-severity problem
```

Two things stay **outside** the edit-time pipeline, deliberately, because
`checkConfiguration()` "may be invoked on every change (i.e., after each character is typed in an
input field)" (`platform/execution/src/com/intellij/execution/configurations/RunConfiguration.java:141-150`)
and runs inside `ReadAction.nonBlocking` on a background thread
(`platform/execution-impl/src/com/intellij/execution/impl/RunnerAndConfigurationSettingsImpl.kt:371-385`):

- **the debug-port probe** (`DEBUG-06-15`) — a pre-spawn gate in `LuaDebugRunner.doExecute()` (§2.7);
- **the mobdebug asset check** (`DEBUG-06-13`) — the same pre-spawn gate (§2.7, and §10.4 for why it
  is not surfaced in the dialog).

## 2. Core Components

All classes in §2.1–§2.5 and §2.7–§2.9 are **NEW**. Files under
`src/main/kotlin/net/internetisalie/lunar/run/validation/`, matching the existing `run/test/` and
`run/console/` subpackages and the engineering contract's `run/` placement rule (§4).

### 2.1 `net.internetisalie.lunar.run.validation.LuaTargetSpec` (NEW)

- **Responsibility**: the immutable, framework-free snapshot of everything the checks need. Holds
  **no** `Project`, `Editor`, `PsiFile` or `VirtualFile` — contract §4 "heavy object retention".
- **Threading**: constructed inside `checkConfiguration()` on a pooled thread and discarded when it
  returns. No component retains one.
- **Contract note on the ≤3-argument cap**: this is the carrier the contract prescribes —
  *"Pass a dedicated configuration or execution context class if more parameter state is required"*
  (`docs/engineering-contract.md` §3). Its constructor is a data carrier, not a behavioural
  function; every *function* in this design takes ≤3 arguments.

```kotlin
data class LuaTargetSpec(
    val runtime: LuaRegisteredTool?,        // toolchain/model/LuaRegisteredTool.kt:6
    val runtimePath: String?,               // the RAW stored path; non-empty even when unusable
    val scriptPath: String?,
    val workingDirectory: String?,          // already resolved by the caller (effectiveWorkDirectory())
    val projectLanguageLevel: LuaLanguageLevel, // lang/LuaLanguageLevel.kt:19
    val envFilePaths: List<String>,
) {
    companion object {
        fun of(configuration: LuaRunConfiguration): LuaTargetSpec
        fun of(configuration: LuaTestRunConfiguration): LuaTargetSpec
    }
}
```

**Both factories are specified here in full; neither is left to inference.**

`getOptions()` is `protected` on both configuration classes (`run/LuaRunConfiguration.kt:194` overrides
without a modifier, so it inherits `RunConfigurationBase`'s `protected`; `run/test/LuaTestRunConfiguration.kt:191`
widens it to `public`). A companion factory therefore reads **only public accessors**, and
`runtimePath` is taken from the resolved tool rather than from `options.interpreter`:
`adHocRuntime` copies the stored path verbatim into `LuaRegisteredTool.path`
(`run/LuaRuntimeResolution.kt:30,35`) and a registry hit comes from `findByPath(path)`
(`:26`), so `runtime.path` **is** the raw stored path in both branches. This also means the runtime
is resolved exactly once per factory call, not twice.

```kotlin
fun of(configuration: LuaRunConfiguration): LuaTargetSpec {
    val resolvedRuntime = configuration.resolveInterpreter()          // run/LuaRunConfiguration.kt:219
    return LuaTargetSpec(
        runtime = resolvedRuntime,
        runtimePath = resolvedRuntime?.path,                          // toolchain/model/LuaRegisteredTool.kt:9
        scriptPath = configuration.scriptName,                        // run/LuaRunConfiguration.kt:196-200
        workingDirectory = configuration.effectiveWorkDirectory(),    // :228
        projectLanguageLevel =
            LuaProjectSettings.getInstance(configuration.project).state.languageLevel,
        envFilePaths = configuration.envFilePaths,                    // §2.8 (EnvFilesOptions)
    )
}

fun of(configuration: LuaTestRunConfiguration): LuaTargetSpec {
    val resolvedRuntime = configuration.resolveInterpreter()          // run/test/LuaTestRunConfiguration.kt:211
    return LuaTargetSpec(
        runtime = resolvedRuntime,
        runtimePath = resolvedRuntime?.path,
        scriptPath = null,
        workingDirectory = configuration.workingDirectory,            // :213-216
        projectLanguageLevel =
            LuaProjectSettings.getInstance(configuration.project).state.languageLevel,
        envFilePaths = emptyList(),
    )
}
```

Three asymmetries in the test factory, stated so they are not read as omissions:

- **`scriptPath = null`.** `LuaTestRunConfiguration` has no script; it has a `testTarget`
  (`run/test/LuaTestRunConfiguration.kt:291-293`), which keeps its own branch. `TEST_TARGET` runs
  only checks 1–2, so the field is never read for a test spec.
- **`workingDirectory` is the raw property, not an `effectiveWorkDirectory()`.** No such method
  exists on `LuaTestRunConfiguration` (`grep -n effectiveWorkDirectory src/main/kotlin/net/internetisalie/lunar/run/test/LuaTestRunConfiguration.kt`
  → no match); the only accessor is `var workingDirectory` at `:213-216`. Do **not** invent a
  `project.basePath` fallback for it — `TEST_TARGET` does not include `WORKDIR_MISSING`.
- **`envFilePaths = emptyList()`.** `LuaTestRunConfiguration` has no env-file property
  (`grep -n envFilePaths src/main/kotlin/net/internetisalie/lunar/run/test/LuaTestRunConfiguration.kt`
  → no match) and `EnvFilesOptions` is implemented only on `LuaRunConfiguration` (§2.8).

`LuaProjectSettings.getInstance(project).state.languageLevel` is grounded at
`settings/LuaProjectSettings.kt:52` (the field) with the accessor idiom at
`analysis/inspections/LuaLanguageLevelInspection.kt:156`.

### 2.2 `net.internetisalie.lunar.run.validation.LuaTargetSeverity` / `LuaTargetProblem` (NEW)

```kotlin
enum class LuaTargetSeverity(val rank: Int) {
    WARNING(0),   // -> RuntimeConfigurationWarning : yellow banner, launch ALLOWED
    ADVISORY(1),  // -> RuntimeConfigurationException: red banner,    launch ALLOWED
    FATAL(2),     // -> RuntimeConfigurationError   : red banner,     launch REFUSED
}

data class LuaTargetProblem(
    val message: String,
    val severity: LuaTargetSeverity,
    val quickFix: ConfigurationQuickFix? = null,
)
```

The three-way mapping is fixed by the platform and verified in source:

| Severity | Platform type | `file:line` | Platform behaviour |
| :-- | :-- | :-- | :-- |
| `FATAL` | `RuntimeConfigurationError` | `platform/execution/…/RuntimeConfigurationError.java:23-36` | `RunManagerImpl.kt:162-164` catches it → `return false`; Run/Debug refused. |
| `ADVISORY` | `RuntimeConfigurationException` | `platform/execution/…/RuntimeConfigurationException.java:15-26` | `RunManagerImpl.kt:165-166` swallows it → `return true`. |
| `WARNING` | `RuntimeConfigurationWarning` | `platform/execution/…/RuntimeConfigurationWarning.java:10-23` | as `ADVISORY`, plus `SingleConfigurationConfigurable.java:334-343` renders it yellow via `isWarning`. |

`ConfigurationQuickFix` is `com.intellij.openapi.options.ConfigurationQuickFix`
(`platform/ide-core/src/com/intellij/openapi/options/ConfigurationQuickFix.java:9-11`), a single
method `void applyFix(@NotNull DataContext)`. Both `RuntimeConfigurationError` and
`RuntimeConfigurationWarning` take one in their two-argument constructors (lines `:28-31` and
`:15-18` respectively).

### 2.3 `net.internetisalie.lunar.run.validation.LuaTargetCheck` (NEW)

```kotlin
fun interface LuaTargetCheck {
    fun problem(spec: LuaTargetSpec): LuaTargetProblem?
}
```

One argument, no side effects other than `LuaPathFacts` memo population. A check that finds nothing
returns `null`.

### 2.4 `net.internetisalie.lunar.run.validation.LuaPathFacts` (NEW)

- **Responsibility**: bound the filesystem-touch frequency that `DEBUG-06-05` records as unbounded,
  and give `DEBUG-06-07` its data.
- **Threading**: app-level `@Service(Service.Level.APP)`, called only from pooled threads inside
  `checkConfiguration()`. Backed by a `ConcurrentHashMap`; no locking.

```kotlin
data class LuaPathFact(val exists: Boolean, val executable: Boolean)

@Service(Service.Level.APP)
class LuaPathFacts {
    fun of(path: String, nowNanos: Long = System.nanoTime()): LuaPathFact
    fun clear()

    companion object {
        const val TTL_NANOS: Long = 2_000_000_000L   // 2 s
        const val MAX_ENTRIES: Int = 64
        fun getInstance(): LuaPathFacts =
            ApplicationManager.getApplication().getService(LuaPathFacts::class.java)
    }
}
```

The `nowNanos` default parameter exists **so the TTL is testable without a clock hack** (§9,
TC-06-05a). Two arguments; within the cap.

**Two fields, not three.** An earlier draft carried an `isDirectory` flag. It is removed: no check in
§3.2 reads it (checks 4, 5 and 8 all test `exists` only, and check 5's subject — a working directory
— is `File.exists()` either way), and it cost a third `stat` on every miss. Do not add it back
speculatively; `DEBUG-06-12` asks whether the working directory *exists*, not whether it is a
directory, matching the platform's own `checkWorkingDirectoryExist`
(`platform/execution-impl/src/com/intellij/execution/util/ProgramParametersConfigurator.java:244-263`).

`adHocRuntime` (`run/LuaRuntimeResolution.kt:30-50`) is rewired to source its
`fileExists`/`executable` from `LuaPathFacts.getInstance().of(path)` instead of calling
`File.exists()` / `File.canExecute()` directly, so the memo covers the `DEBUG-06-05` site itself.

### 2.5 `net.internetisalie.lunar.run.validation.LuaTargetValidator` + `LuaTargetChecks` + `LuaTargetMessages` (NEW)

```kotlin
object LuaTargetValidator {
    @Throws(RuntimeConfigurationException::class)
    fun validate(spec: LuaTargetSpec, checks: List<LuaTargetCheck>)

    fun asException(problem: LuaTargetProblem): RuntimeConfigurationException
}

object LuaTargetChecks {
    val LOCAL_SCRIPT: List<LuaTargetCheck>   // the eight checks of §3.2, in that order
    val TEST_TARGET: List<LuaTargetCheck>    // RUNTIME_MISSING + RUNTIME_UNUSABLE only
}

object LuaTargetMessages {
    const val RUNTIME_KIND_ID: String = "lua"
    fun noRuntimeConfigured(): String
    fun runtimeMissing(path: String): String
    fun runtimeNotExecutable(path: String): String
    fun runtimeProbeFailed(path: String, reason: String?): String
    fun scriptMissing(path: String): String
    fun workingDirectoryMissing(path: String): String
    fun scriptOutsideBaseDirectory(relative: String): String
    fun languageLevelMismatch(runtime: LuaLanguageLevel, project: LuaLanguageLevel): String
    fun envFileMissing(path: String): String
    fun debugPortInUse(port: Int): String
}
```

`runtimeProbeFailed` is the third branch of check 2 (§3.2) and its text is §3.3's table row. Its
`path` is **non-null** — check 2 only fires when `spec.runtime != null`, and `LuaRegisteredTool.path`
is `String` (`toolchain/model/LuaRegisteredTool.kt:9`), the same type the two branches above it take.
`reason` **is** nullable: it is `LuaToolHealth.reason: String?` (`:29`), rendered with §3.3's
`?: "unavailable"` fallback. Do not widen `path` to `String?`.

### 2.6 Call sites in the existing configurations (MODIFIED)

`net.internetisalie.lunar.run.LuaRunConfiguration` — replaces `:275-285`:

```kotlin
override fun checkConfiguration() =
    LuaTargetValidator.validate(LuaTargetSpec.of(this), LuaTargetChecks.LOCAL_SCRIPT)
```

`net.internetisalie.lunar.run.test.LuaTestRunConfiguration` — replaces the runtime branch at `:288-290`:

```kotlin
override fun checkConfiguration() {
    LuaTargetValidator.validate(LuaTargetSpec.of(this), LuaTargetChecks.TEST_TARGET)
    if (options.testTarget.isNullOrEmpty()) {
        throw RuntimeConfigurationException("Test target is not defined")
    }
}
```

**This is a behaviour change for the test configuration in two directions, and it is intended.** The
shipped branch tests the *stored string* — `if (options.interpreter.isNullOrEmpty()) throw
RuntimeConfigurationException("Runtime is not defined")`
(`run/test/LuaTestRunConfiguration.kt:288-290`). `TEST_TARGET` tests the *resolved tool*
(`resolveInterpreter()`, `:211`, which is `resolveConfiguredRuntime` — stored path wins, otherwise the
project-resolved default, `run/LuaRuntimeResolution.kt:19-27`). So:

| Configuration state | Today | After |
| :-- | :-- | :-- |
| `interpreter` empty, **a project default runtime exists** | `RuntimeConfigurationException` — the dialog reports an error on a target that runs fine | resolves; **no problem reported** |
| `interpreter` empty, no project default | `RuntimeConfigurationException` (advisory tier, launch allowed) | check 1 → `RuntimeConfigurationError` (**launch refused**), shared message (§3.3) |
| `interpreter` set to a path that does not exist / is not executable | passes validation, fails at spawn | check 2 → `RuntimeConfigurationError` (**launch refused**) |

Row 1 is a bug fix (`DEBUG-06-06` — the check should ask whether a runtime *resolves*, not whether a
field is filled in). Rows 2 and 3 are `DEBUG-06-02`/`-07` reaching the test configuration too, and
they are the reason Risk 1.1 lists the test configuration alongside the main one. Anyone editing
`LuaTestRunConfigurationTest.kt:154` in Phase 2 should expect its behaviour to move, not just its
assertion.

### 2.7 `net.internetisalie.lunar.run.validation.LuaDebugPortProbe` (NEW) + `LuaDebugRunner` (MODIFIED)

```kotlin
object LuaDebugPortProbe {
    fun isAvailable(port: Int): Boolean
}
```

`LuaDebugRunner.doExecute` (`run/LuaDebugRunner.kt:69-83`) gains a pre-spawn gate as its **first**
statements, before `state.execute(...)` spawns the interpreter. `doExecute` is declared
`@Throws(ExecutionException::class)` upstream
(`platform/execution/src/com/intellij/execution/runners/GenericProgramRunner.kt:28-32`), so the
platform already renders the message and aborts.

**The gate takes values, not an `ExecutionEnvironment`.** This is the single most important shape
decision in §2.7 and it exists so the gate is testable at all: `checkDebugTargetReady` receives the
port and the plugin asset directory as **already-resolved arguments**, and `doExecute` does the
resolving. Two arguments, within the cap.

```kotlin
override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
    checkDebugTargetReady(                                        // NEW — first statement, before the spawn
        (environment.runProfile as? LuaRunConfiguration)?.debugPort
            ?: LuaRunConfigurationOptions.DEFAULT_DEBUG_PORT,
        LuaFileUtil.getPluginVirtualDirectoryChild("lua"),         // util/LuaFileUtil.kt:22-29
    )
    val executionResult = state.execute(environment.executor, this) ?: return null
    …unchanged…
}

/** `internal` is a deliberate test seam — TC-06-13a/b/c and TC-06-15b call this directly. */
internal fun checkDebugTargetReady(debugPort: Int, pluginLuaDirectory: VirtualFile?) {
    if (!LuaDebugPortProbe.isAvailable(debugPort)) {
        throw ExecutionException(LuaTargetMessages.debugPortInUse(debugPort))
    }
    val pluginLuaPath = pluginLuaDirectory
        ?: throw ExecutionException("Failed to locate plugin directory")
    pluginLuaPath.findChild(LuaRunConfiguration.DEBUGGER_PRELOADER_FILE)
        ?: throw ExecutionException("Failed to locate debugger preloader")
}
```

`internal`, **not** `private` — this is the visibility the plan's Phase 4 requires and the two must
not disagree. The idiom (an `internal` production function called from `src/test`) is established in
this repo: `PublishRockAction.isAuthFailure` is `internal` at `rocks/publish/PublishRockAction.kt:144`
and is called from `src/test/kotlin/net/internetisalie/lunar/rocks/publish/PublishRockAuthFailureTest.kt:14`.

Both asset messages are copied verbatim from RUN-04-03's existing strings
(`run/LuaRunConfiguration.kt:320-326`) so the two gates cannot drift.
`LuaRunConfiguration.DEBUGGER_PRELOADER_FILE` is `"debug.lua"` (`run/LuaRunConfiguration.kt:356`).

> **Why the arguments, and not `environment`.** An earlier draft passed the `ExecutionEnvironment` and
> then declared `DEBUG-06-13` untestable because `LuaFileUtil.pluginVirtualDirectory` resolves through
> `PluginManagerCore.getPlugin(…)` (`util/LuaFileUtil.kt:16-20`), which a light fixture cannot control.
> That was a claim about *the draft's own shape*, not about the platform — and this repo already ships
> the counterexample:
>
> ```kotlin
> fun testGetPluginVirtualDirectoryChildMissingReturnsNull() {
>     assertNull(LuaFileUtil.getPluginVirtualDirectoryChild("no-such-child-xyz"))
> }
> ```
>
> `src/test/kotlin/net/internetisalie/lunar/util/LuaFileUtilTest.kt:40-42`, a plain
> `BasePlatformTestCase`. That test does establish that the *null* return is observable from a light
> fixture, but it cannot distinguish "no plugin descriptor" from "descriptor present, no such child" —
> so it is not a seam this design can build a severity assertion on. Lifting the lookup into
> `doExecute` removes the dependency altogether: the gate's asset branches then take a `VirtualFile?`
> the test supplies directly (`null`, a real temp directory without `debug.lua`, and one with it),
> which is what TC-06-13a/b/c do. `DEBUG-06-13` is a `Must` and now has three test cases, not an
> untestability argument.

### 2.8 Environment files (`DEBUG-06-17`) — MODIFIED

`LuaRunConfiguration` implements `com.intellij.execution.EnvFilesOptions`
(`platform/execution-impl/src/com/intellij/execution/EnvFilesOptions.kt:4-7`), a **single**
property:

```kotlin
class LuaRunConfiguration(…) : RunConfigurationBase<LuaRunConfigurationOptions?>(…), EnvFilesOptions {
    override var envFilePaths: List<String>
        get() = options.envFilePaths.toList()
        set(value) { options.envFilePaths = value.toMutableList() }
}
```

`LuaRunConfigurationOptions` gains, alongside the existing `StoredProperty` declarations
(`run/LuaRunConfiguration.kt:65-122`), **both** the private property and the public accessor that
`options.envFilePaths` above requires — the accessor is not optional, and it follows the shape every
other option on that class uses (`var interpreter` at `:124-128`):

```kotlin
// inside class LuaRunConfigurationOptions : RunConfigurationOptions()
private val myEnvFilePaths: StoredProperty<MutableList<String>> =
    list<String>().provideDelegate(this, "envFilePaths")

var envFilePaths: MutableList<String>
    get() = myEnvFilePaths.getValue(this)
    set(value) {
        myEnvFilePaths.setValue(this, value)
    }
```

Grounding for the two halves: `BaseState.list()` is `protected fun <T : Any> list():
StoredPropertyBase<MutableList<T>>`
(`platform/projectModel-api/src/com/intellij/openapi/components/BaseState.kt:101`), so
`MutableList<String>` is the property's real type — the same shape as the existing
`map<String, String>()` property at `run/LuaRunConfiguration.kt:90-94`. The `getValue(this)` /
`setValue(this, value)` accessor pair is the idiom every other option on this class uses
(`var interpreter`, `:124-128`). `EnvFilesOptions.envFilePaths` is declared `List<String>`
(`platform/execution-impl/src/com/intellij/execution/EnvFilesOptions.kt:6`), which is why the
`LuaRunConfiguration` override converts in both directions.

`LuaRunSettingsEditor` replaces the **deprecated** no-`Project` widget at `:377`
(`EnvironmentVariablesTextFieldWithBrowseButton()`; deprecation at
`platform/execution-impl/…/EnvironmentVariablesTextFieldWithBrowseButton.java:54-57`) with

```kotlin
private val environmentVariablesField = EnvironmentVariablesComponent(project)
```

(`platform/execution-impl/…/EnvironmentVariablesComponent.java:48-56` — the non-deprecated
constructor). `resetEditorFrom` (`:414`) and `applyEditorTo` (`:425`) gain the env-file half using
the component's **public** accessors (`:78-84`):

```kotlin
// resetEditorFrom
environmentVariablesField.envData = runConfiguration.environmentVariables ?: EnvironmentVariablesData.DEFAULT
environmentVariablesField.setEnvFilePaths(runConfiguration.envFilePaths)
// applyEditorTo
runConfiguration.environmentVariables = environmentVariablesField.envData
runConfiguration.envFilePaths = environmentVariablesField.getEnvFilePaths().toList()
```

> **Why the component and not the raw widget.** `EnvironmentVariablesTextFieldWithBrowseButton.getEnvFilePaths()`
> is **package-private** (`:245`, no modifier) — Lunar cannot call it. `EnvironmentVariablesComponent`
> is the public wrapper and re-exports it at `:82-84`. A design that called the widget's method
> directly would not compile; see [risks-and-gaps.md](risks-and-gaps.md) §3, Amplification A2.

### 2.8.1 One label, not two — the decision (contract §6, TEXT IS PART OF THE UI)

`EnvironmentVariablesComponent` **is a `LabeledComponent<TextFieldWithBrowseButton>`**
(`platform/execution-impl/…/EnvironmentVariablesComponent.java:26`) and its constructor sets its own
title at `:53` from `environment.variables.component.title` = `&Environment variables`
(`platform/execution/resources/messages/ExecutionBundle.properties:292`). `LabeledComponent`'s
default label constraint is `BorderLayout.NORTH`
(`platform/platform-api/src/com/intellij/openapi/ui/LabeledComponent.java:28`). Dropping it into the
existing `FormBuilder.addLabeledComponent("Environment variables", …)` row
(`run/LuaRunConfiguration.kt:402`) therefore renders the text **twice**: once in the form's left label
column and once above the field. The raw widget it replaces is not a `LabeledComponent`, so this is
new with §2.8 and the contract binds it.

**Decision: the `FormBuilder` label survives; the component's own title is cleared.** In the editor's
`init`, before the `FormBuilder` chain:

```kotlin
environmentVariablesField.labelLocation = BorderLayout.WEST   // LabeledComponent.java:109
environmentVariablesField.text = ""                           // LabeledComponent.java:56
```

and the form row keeps its label, with the colon the contract requires:

```kotlin
.addLabeledComponent("Environment variables:", environmentVariablesField)
```

**Grounded, not invented.** This is the platform's own idiom for placing an
`EnvironmentVariablesComponent` inside a grid that already supplies the label:
`plugins/sh/core/src/com/intellij/sh/run/ShRunConfigurationEditor.java:76-78` does
`new EnvironmentVariablesComponent()` → `setLabelLocation("East")` → `setText("")`, and again at
`:173-174`. `CommonProgramParametersPanel.java:113` is the precedent for moving the label off `NORTH`
(`setLabelLocation(BorderLayout.WEST)`). `WEST` is chosen over `NORTH` so the emptied label sits in
the row rather than occupying a line above the field.

**Why not the other way round** (drop the `FormBuilder` label with `addComponent(...)` and keep the
component's own): the component's label would render at `NORTH`, above the field, out of the left
label column the other seven rows share — and its bundle text is Title-less but mnemonic-bearing
(`&Environment variables`), so it would also be the only row in the editor carrying a mnemonic. This
is a decision, not a DR item; DR-04 is reduced to a screenshot **confirmation** of it.

> **The disk-icon extension is installed only by `setEnvFilePaths`.** `addEnvFilesExtension()` is
> private and its sole call site is inside `setEnvFilePaths` (`:83`, called from `:241`). Typing an
> env-file path into the text field does **not** create one: `updateEnvFiles` returns immediately
> when `myEnvFilePaths.isEmpty()` (`:250`). So calling `setEnvFilePaths(...)` from `resetEditorFrom`
> — even with an empty list — is what makes the chooser reachable at all.

The `environmentFile` `StoredProperty` at `:95-99` is **left in place, unused** — `DEBUG-06-16` is a
`Won't` and removing the property would discard saved configurations. §10.2 records why.

### 2.9 `net.internetisalie.lunar.run.LuaRunConfigurationProducer` (NEW) — `DEBUG-06-18`

Mirrors `LuaTestRunConfigurationProducer` (`run/test/LuaTestRunConfigurationProducer.kt:17-42`) and
`LuaRedisRunConfigurationProducer` (`redis/run/LuaRedisRunConfigurationProducer.kt:21-53`).

```kotlin
class LuaRunConfigurationProducer : LazyRunConfigurationProducer<LuaRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory
    override fun setupConfigurationFromContext(
        configuration: LuaRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean
    override fun isConfigurationFromContext(
        configuration: LuaRunConfiguration,
        context: ConfigurationContext,
    ): Boolean
    override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext?): Boolean

    internal fun yieldsTo(other: RunConfiguration?): Boolean     // the §3.9.3 predicate, as a test seam
    private fun isRedisTarget(context: ConfigurationContext): Boolean
}
```

- `getConfigurationFactory()` = `ConfigurationTypeUtil.findConfigurationType(LuaRunConfigurationType::class.java).configurationFactories[0]`
  — the accessor idiom already used at `rocks/init/LuaRocksScaffolder.kt:88`. (`LuaRunConfigurationType`
  has no `getInstance()`, unlike `LuaTestRunConfigurationType:55`.)
- `isPreferredConfiguration` is declared on `RunConfigurationProducer`
  (`platform/lang-api/src/com/intellij/execution/actions/RunConfigurationProducer.java:157-159`),
  default `return true`. Its contract, verbatim from `:147-154`: *"When two configurations are created
  from the same context by two different producers, checks if the configuration created by this
  producer should be discarded in favor of the other one … return true if the configuration created by
  this producer is at least as good as the other one; false if this configuration should be discarded"*.
  The platform reaches it through `ConfigurationFromContextImpl.isPreferredTo` (`:37-38`), compared
  pairwise in `ConfigurationFromContext.java:146-155`.
- `yieldsTo` is `internal` **as a deliberate test seam**, so TC-06-18d can assert the predicate without
  constructing two `ConfigurationFromContext` instances (`ConfigurationFromContext` is abstract and its
  only implementation needs a `RunnerAndConfigurationSettings`). The idiom — an `internal` production
  helper called directly from `src/test` — is established: `PublishRockAction.isAuthFailure` is
  `internal` at `rocks/publish/PublishRockAction.kt:144` and is called from
  `src/test/kotlin/net/internetisalie/lunar/rocks/publish/PublishRockAuthFailureTest.kt:14`.

**All three overrides are specified as numbered predicates in §3.9.** None is left to inference.

### 2.10 Extensibility for DEBUG-05 (attach / remote)

`DEBUG-06-19` is a `Won't`; no attach configuration is designed here. The model is extensible to one
by construction, and the extension points are:

1. **`LuaTargetSpec` names no configuration class.** An attach configuration builds one with
   `runtime = null`, `runtimePath = null`, `scriptPath = null` and its own `workingDirectory`
   (the source-mapping base a remote debuggee reports against).
2. **`LuaTargetValidator.validate(spec, checks)` takes the check list as a parameter.** DEBUG-05
   adds `LuaTargetChecks.ATTACH` and nothing else changes in `LuaTargetValidator`,
   `LuaTargetSeverity` or `LuaTargetProblem`. Precisely which of the eight existing checks it may
   contain, because DEBUG-05 is being planned against this paragraph:

   ```kotlin
   val ATTACH: List<LuaTargetCheck> = listOf(WORKDIR_MISSING) + /* DEBUG-05's own host/port checks */
   ```

   - **`WORKDIR_MISSING` (check 5) is reused verbatim.** It reads only `spec.workingDirectory`
     (§3.2), which point 1's attach spec supplies.
   - **`SCRIPT_REACHABLE` (check 6) must NOT be included.** §3.6 step 1 returns `null` when
     `scriptPath.isNullOrEmpty()`, and point 1's attach spec sets `scriptPath = null` — so the check
     is *inert*, not "reused unchanged". Including it would put a permanently-silent entry in the
     list, which reads as coverage and is not. If DEBUG-05 wants source-mapping reachability for a
     remote debuggee it must supply a non-null `scriptPath`, or write its own check; either is a
     DEBUG-05 decision, not a DEBUG-06 one.
   - **Checks 2, 4 and 7 are dropped for clarity, not for correctness** — each is guarded and
     returns `null` on point 1's attach spec, so including one would be inert rather than harmful.
     Re-derived against §3.2 rather than asserted: check 2 fires only when
     `spec.runtime != null && !spec.runtime.isUsable`, and `runtime` is null; check 4 fires only when
     `scriptPath` is non-empty, and `scriptPath` is null; check 7 returns `null` when
     `spec.runtime?.runtime == null`, which a null `runtime` satisfies. Drop them because a
     permanently-silent entry reads as coverage, exactly as for check 6.
   - **Checks 1 and 3 MUST be dropped, and these two are for correctness — each *fires* on the
     attach spec's normal state rather than returning `null`.**
     - **Check 1 (`RUNTIME_MISSING`)** fires when `spec.runtime == null` (§3.2 row 1), which is
       precisely what point 1 sets, and its severity is `FATAL`. Including it would raise a
       `RuntimeConfigurationError` on *every* attach configuration, which
       `RunManagerImpl.canRunConfiguration` catches to `return false`
       (`platform/execution-impl/src/com/intellij/execution/impl/RunManagerImpl.kt:162-164`) —
       i.e. every attach launch refused. An attach configuration has no local runtime **by
       definition**; if DEBUG-05 needs to validate a *remote* one it must write its own check.
     - **Check 3 (`SCRIPT_UNSET`)** fires on `scriptPath.isNullOrEmpty()`, which is likewise the
       attach spec's normal state, so leaving it in would make every attach configuration warn
       *"No script file configured"*. Severity `WARNING`, so it degrades the dialog rather than
       blocking the launch — but it is still a permanent false positive.
   - **Check 8 (`ENV_FILES`) is reusable** if DEBUG-05's spec carries `envFilePaths`; it iterates an
     empty list harmlessly otherwise (§6).
3. **New checks are `fun interface` values**, so adding one is appending a lambda to a list — never
   editing an `if`/`else` ladder inside a configuration class.
4. **`LuaDebugPortProbe` is configuration-free** (`isAvailable(port)`), so an attach configuration
   that binds a listener locally reuses it verbatim, and one that *connects* out adds a sibling
   `isReachable(host, port)` without touching it.

## 3. Algorithms

### 3.1 Severity resolution — collect all, throw the worst

**Input → Output**: `(LuaTargetSpec, List<LuaTargetCheck>)` → `Unit`, or throws exactly one
`RuntimeConfigurationException` subtype.

**Steps** (`LuaTargetValidator.validate`, ≤12 logic lines):

1. `val problems = checks.mapNotNull { it.problem(spec) }`.
2. If `problems.isEmpty()`, return.
3. `val worst = problems.maxByOrNull { it.severity.rank }` — Kotlin's `maxByOrNull` returns the
   **first** maximal element, so ties break by the declaration order of §3.2. That is the tie-break
   rule; it is not left to the implementer.
4. `throw asException(worst)`.

`asException(problem)` (≤8 logic lines):

```kotlin
fun asException(problem: LuaTargetProblem): RuntimeConfigurationException =
    when (problem.severity) {
        LuaTargetSeverity.FATAL   -> RuntimeConfigurationError(problem.message, problem.quickFix)
        LuaTargetSeverity.ADVISORY -> RuntimeConfigurationException(problem.message)
        LuaTargetSeverity.WARNING -> RuntimeConfigurationWarning(problem.message, problem.quickFix)
    }
```

**Why collect-then-throw and not throw-in-place.** A fatal problem found by check 4 must outrank a
warning found by check 3. Throw-in-place makes the ladder depend on declaration order, which is the
defect `DEBUG-06-03` describes ("the ladder is used but not used correctly"). Collect-then-throw
makes order matter only for ties.

**Edge / error path.** A check that throws (rather than returning a problem) is a programming error;
`validate` does **not** catch it. `RuntimeConfigurationException` extends `ConfigurationException`
(`platform/execution/…/RuntimeConfigurationException.java:15`), and
`SingleConfigurationConfigurable.java:327-331` catches only `ConfigurationException` — anything else
escapes the `ReadAction.nonBlocking` and is reported as an IDE error. `DEBUG-06-04` is satisfied
because `asException` is total over the enum and is the only throw site.

### 3.2 The check list — `LuaTargetChecks.LOCAL_SCRIPT`

Declared in this exact order. Each cell is the whole rule.

| # | Name | Fires when | Severity | Message |
| :-: | :-- | :-- | :-- | :-- |
| 1 | `RUNTIME_MISSING` | `spec.runtime == null` | `FATAL` | `LuaTargetMessages.noRuntimeConfigured()` + `LuaToolchainSettingsQuickFix` |
| 2 | `RUNTIME_UNUSABLE` | `spec.runtime != null && !spec.runtime.isUsable` | `FATAL` | Three-way, in this order — see below |
| 3 | `SCRIPT_UNSET` | `spec.scriptPath.isNullOrEmpty()` | `WARNING` | `"No script file configured"` (verbatim from `:283`) |
| 4 | `SCRIPT_MISSING` | `scriptPath` non-empty **and** `!LuaPathFacts.of(scriptPath).exists` | `FATAL` | `scriptMissing(path)` |
| 5 | `WORKDIR_MISSING` | `workingDirectory` non-empty **and** `!LuaPathFacts.of(workingDirectory).exists` | `WARNING` | `workingDirectoryMissing(path)` |
| 6 | `SCRIPT_REACHABLE` | §3.6 predicate is true | `WARNING` | `scriptOutsideBaseDirectory(relative)` |
| 7 | `RUNTIME_LEVEL` | §3.5 predicate is true | `WARNING` | `languageLevelMismatch(runtimeLevel, projectLevel)` |
| 8 | `ENV_FILES` | any `p` in `envFilePaths` with `!LuaPathFacts.of(p).exists` (first such `p`) | `ADVISORY` | `envFileMissing(p)` |

**Check 2's message is three-way, because `isUsable` is a three-term conjunction.**
`isUsable` is `health.fileExists && health.executable && health.probeOk != false`
(`toolchain/model/LuaRegisteredTool.kt:32-33`), so a two-way `if (!fileExists) … else …` renders
*"not executable"* for a tool that exists and **is** executable but failed its probe. Evaluate in
exactly this order and stop at the first match:

| # | Condition | Message |
| :-: | :-- | :-- |
| 1 | `!health.fileExists` | `runtimeMissing(path)` |
| 2 | `!health.executable` | `runtimeNotExecutable(path)` |
| 3 | otherwise (`health.probeOk == false`) | `runtimeProbeFailed(path, health.reason)` |

Branch 3 is **reachable in production, not a theoretical third case.** `spec.runtime` comes from
`resolveConfiguredRuntime` (`run/LuaRuntimeResolution.kt:19-27`), and only its *last* fallback —
`adHocRuntime` — pins `probeOk = null` (`:45`). The other two sources return **registry** tools whose
stored health can carry `probeOk = false`: a stored path that hits `findByPath(path)` (`:26`), and
the project-bound default from `LuaToolResolver.getInstance().resolveRuntime(project)` (`:25`).
TC-06-07c covers it.

**Short-circuits, stated so they are not invented:**

- Check 2 returns `null` when `runtime == null` (check 1 owns that case).
- Checks 4 and 6 return `null` when `scriptPath.isNullOrEmpty()` (check 3 owns that case).
- **Check 6 returns `null` when `workingDirectory` is null/empty or does not exist.** A reachability
  verdict derived from a base directory that is itself missing is noise on top of check 5's report.
  This is load-bearing for TC-06-12a.
- Check 7 returns `null` when `spec.runtime?.runtime == null` — an ad-hoc, never-probed tool carries
  `runtime = null` (`run/LuaRuntimeResolution.kt:38`) and has no language level to compare.

`LuaTargetChecks.TEST_TARGET` is `listOf(RUNTIME_MISSING, RUNTIME_UNUSABLE)`. The test configuration
has no script, working directory or env files of the kind these checks model.

**Severity rationale for check 5 (WARNING, not FATAL).** [[BUG-455]] §5 lists "missing working
directory" among the conditions that should raise `RuntimeConfigurationError`. `DEBUG-06-12`
overrides it, and names the authority: the platform's own
`ProgramParametersConfigurator.checkWorkingDirectoryExist` throws `RuntimeConfigurationWarning`
(`platform/execution-impl/src/com/intellij/execution/util/ProgramParametersConfigurator.java:244-263`).
Requirements win over a bug report's fix-strategy prose; the discrepancy is recorded in
[risks-and-gaps.md](risks-and-gaps.md) Gap 2.1.

### 3.3 Message construction — one source (`DEBUG-06-22`, `-23`)

```kotlin
object LuaTargetMessages {
    const val RUNTIME_KIND_ID: String = "lua"

    fun noRuntimeConfigured(): String =
        LuaToolResolver.getInstance().notConfiguredMessage(RUNTIME_KIND_ID)
    …
}
```

Producing, verbatim from `toolchain/resolve/LuaToolResolver.kt:93-97`:

> `No usable Lua configured. Add or bind one under Settings | Languages & Frameworks | Lua | Toolchain.`

**The trap an implementer must not fall into.** `notConfiguredMessage` looks the kind up with
`LuaToolKindRegistry.findById(kindId)` and falls back to the **raw id** when it misses (`:94`).
`LuaToolResolver.resolveRuntimeDetailed` returns `LuaToolResolution.Unresolved(RUNTIME_KIND_ID, …)`
where that constant is the *synthetic* `"runtime-capability"` (`LuaToolResolver.kt:14`, `:83`), and
`LuaToolKindRegistry.BUILT_IN` contains **no kind with that id** — its ids are `lua`, `luajit`,
`tarantool`, `luarocks`, `luacheck`, `stylua`, `luacov`, `busted`, `redis-server`,
`valkey-server`, `lua-language-server` (`registry/LuaToolKindRegistry.kt:19,48,66,84,98,110,122,134,146,158,170`).
Passing the unresolved kind id would render **"No usable runtime-capability configured."** in the
dialog. Always pass the literal `LuaTargetMessages.RUNTIME_KIND_ID`; never
`(resolution as Unresolved).kindId`. TC-06-22a is the test that catches it.

The remaining messages are **fixed strings in `LuaTargetMessages`** — a decision, taken below on a
corrected premise, not a default.

**Correcting the record: Lunar HAS a message bundle.** An earlier revision of this section asserted
it had none and pasted two executed commands as proof. Both searched names Lunar does not use, and
the claim survived three review rounds because the reviewer independently re-ran the same wrong
names and recorded the result as verified. **Executed evidence is only as good as the name you
search for.** What is actually there:

- `src/main/kotlin/net/internetisalie/lunar/LuaBundle.kt:15-18` — `object LuaBundle` over
  `private const val BUNDLE: String = "net.internetisalie.lunar.LuaBundle"`, exposing
  `message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): String`
  (`:20-26`), which wraps `BundleBase.message` (`:3`, `:26`) — it does not extend `DynamicBundle`.
- `src/main/resources/net/internetisalie/lunar/LuaBundle.properties` — 145 lines, with a
  `# debugging` section at `:109`.
- **11 caller files** and **22 live call sites.** Executed: `grep -rln LuaBundle src/main/kotlin/`
  → 12 paths, one of which is `LuaBundle.kt` itself; `grep -rn 'LuaBundle\.message'
  src/main/kotlin/ | wc -l` → `24`, of which two are commented out
  (`lang/format/LuaCodeStyleSettings.kt:96`, `:102`). One caller is inside **`run/` itself**:
  `run/LuaExecutionStack.kt:21,28` — `XExecutionStack(LuaBundle.message("debug.stack.thread.main"))`.
- `src/main/resources/META-INF/plugin.xml` names it on nine lines — `:468,470,475,480,494,496,510`
  (declarative inlay providers and their options) and `:682,686` (notification groups).

The two wrong commands, re-run for the record: `ls -d src/main/resources/messages` → `ls: cannot
access 'src/main/resources/messages': No such file or directory` (true, and irrelevant — the bundle
lives under `src/main/resources/net/internetisalie/lunar/`), and `grep -rn
'LunarBundle\|DynamicBundle' src/main/kotlin/` → no output, exit 1 (also true, also irrelevant —
the object is `LuaBundle` and it subclasses nothing).

**Decision: `LuaTargetMessages` holds literal strings, not `LuaBundle` keys.** Re-taken with the
bundle option genuinely live. Reasons, in order of weight:

1. **The headline message cannot be a key.** `DEBUG-06-22`'s message — the one this section is named
   for — is not a `LuaTargetMessages` string at all. `noRuntimeConfigured()` delegates to
   `LuaToolResolver.notConfiguredMessage(kindId)`, which **composes** it at call time from
   `LuaToolKindRegistry.findById(kindId)?.displayName ?: kindId`
   (`toolchain/resolve/LuaToolResolver.kt:93-97`). Migrating the nine in the table below would
   leave the tenth — the requirement's own subject — outside the bundle, in a package (`toolchain/`)
   this feature does not otherwise touch. A convention that excludes its own headline is a split,
   not a convention.
2. **`DEBUG-06-22` asks for FEWER sources for a message, not more.** Its complaint is three wordings
   for one condition across five sites (`requirements.md:100`;
   [risks-and-gaps.md](risks-and-gaps.md) §3.4). Adding a second storage mechanism for the sibling
   messages while the headline stays in `LuaToolResolver` pushes against the requirement it serves.
3. **Contract §6's text rules reach control labels, not validation prose.** Its bundle mention —
   *"a bundle assertion that no control label is Title Case"* (`docs/engineering-contract.md:163`) —
   is a **casing** assertion over **control labels**, and every clause under TEXT IS PART OF THE UI
   (CASE, COLONS, NO IDENTIFIERS AS DISPLAY TEXT, EXPLANATION IN `comment()`, DISPLAY NAMES,
   MNEMONICS — `:138-151`) governs labels and controls. All ten of these are
   `RuntimeConfigurationException` / `RuntimeConfigurationWarning` messages rendered in the editor's
   validation banner, not labels.
   The §6 clause that *does* reach this feature is COLONS, and §2.8.1 applies it to the one row it
   restructures. §6's SCOPE bullet (`:164-166`) binds new and restructured **UI surfaces**; it is
   satisfied by §2.8/§2.8.1, and no §6 clause states where message text is stored. (The assertion §6
   posits does not exist yet — executed: `grep -rln LuaBundle src/test/kotlin/` returns one file,
   `lang/insight/LuaLineMarkerTest.kt`.)
4. **The rest of this surface is literals — checked, not inherited.** Every `checkConfiguration` /
   `ExecutionException` message in `run/` and `redis/` is a literal: `run/LuaRunConfiguration.kt:277`,
   `:283`, `:323`, `:326`; `run/test/LuaTestRunConfiguration.kt:289`, `:292`;
   `redis/run/LuaRedisRunConfiguration.kt:244`, `:247`, `:256`;
   `redis/run/LuaRedisRunProfileState.kt:144`, `:196`, `:200`. `run/`'s single bundle use
   (`LuaExecutionStack.kt:28`) is a *label* — a debugger thread name — not a diagnostic. One rule
   for the whole diagnostic surface beats a package in which the ten new messages resolve one way
   and every message already there resolves another.

**What this decision does NOT rest on.** Not *"there is no bundle"* — there is. Not *"a bundle cannot
interpolate"* — it can (`vararg params`, `LuaBundle.kt:25`), and nine of these ten take parameters.
Not precedent alone: reason 4 is supporting, and reasons 1–3 stand without it. The bundle is also
demonstrably capable of holding prose rather than only labels — `refactoring.rename.unsupported`
(`LuaBundle.properties:145`) is a two-clause sentence — so capability is not the objection either.
The residual this leaves is recorded as [risks-and-gaps.md](risks-and-gaps.md) **TBD-5**.

**No message text changes as a result.** The table below is byte-for-byte what the prior revision
specified, and §9's acceptance rows assert these exact strings.

| Function | Exact text |
| :-- | :-- |
| `runtimeMissing(path)` | `The configured Lua runtime does not exist: $path` |
| `runtimeNotExecutable(path)` | `The configured Lua runtime is not executable: $path` |
| `runtimeProbeFailed(path, reason)` | `The configured Lua runtime failed its health check: $path (${reason ?: "unavailable"})` — the `reason ?: "unavailable"` fallback is verbatim the idiom at `toolchain/health/LuaToolEditorNotificationProvider.kt:74`, which renders the same nullable `LuaToolHealth.reason` (`toolchain/model/LuaRegisteredTool.kt:29`) |
| `scriptMissing(path)` | `Script file does not exist: $path` |
| `workingDirectoryMissing(path)` | `Working directory does not exist: $path` |
| `scriptOutsideBaseDirectory(relative)` | `The script is outside the working directory ($relative). Breakpoints will not bind during a debug session; set the working directory to the script's directory or an ancestor of it.` |
| `languageLevelMismatch(runtime, project)` | `The configured runtime is $runtime but the project language level is $project. Sources using $project syntax will fail to load.` (`LuaLanguageLevel.toString()` renders `Lua 5.4` — `lang/LuaLanguageLevel.kt:31`) |
| `envFileMissing(path)` | `Environment file not found: $path` |
| `debugPortInUse(port)` | `Debug port $port is already in use. Change "Debug port" in this run configuration, or stop the other Lua debug session.` |

`DEBUG-06-21` (ratified vocabulary) is preserved: no message says *interpreter*; the editor labels
`"Runtime"`/`"Runtime arguments"` (`:398`, `:403`) are untouched; the persisted option key stays
`interpreter` (`:72-76`).

`LuaTestCommandLineState.kt:124` and `LuaConsoleRunner.kt:40` also hand-roll the same literal at
launch time. They are **out of scope** here — they are `ExecutionException` sites, not
`checkConfiguration` — but they are listed in [risks-and-gaps.md](risks-and-gaps.md) §3
Amplification A1 so the sweep is not lost.

### 3.4 Bounding the filesystem — `LuaPathFacts.of` (`DEBUG-06-05`)

**Input → Output**: `(String, Long) → LuaPathFact`.

**Steps** (≤14 logic lines):

1. `val cached = entries[path]`.
2. If `cached != null && nowNanos - cached.stampNanos in 0 until TTL_NANOS`, return `cached.fact`.
   (The `>= 0` half is deliberate: `System.nanoTime()` may be read on different cores; a negative
   delta is treated as a miss, never as a valid hit.)
3. `val target = File(path)`; `val fact = LuaPathFact(target.exists(), target.canExecute())` — exactly
   two `stat`s, the same pair `adHocRuntime` performs today at `run/LuaRuntimeResolution.kt:43-44`.
4. If `entries.size >= MAX_ENTRIES`, `entries.clear()` — a whole-map flush, not an eviction policy.
   A validation pass touches at most four distinct paths, so 64 entries is 16 concurrently-edited
   configurations; overrunning it is not a correctness event.
5. `entries[path] = Entry(fact, nowNanos)`; return `fact`.

**Why a memo, given that the stats are cheap.** Measured on this host with a warm page cache
(`~/.cache/claude-scratch/lunar/debug06/StatProbe.java`, corretto-21):

```
File.exists  (present) ns/op: 1291      Files.exists (present) ns/op: 1307
File.exists  (absent)  ns/op:  703      Files.exists (absent)  ns/op: 1340
File.canExecute(present) ns/op: 946
4-path pass ns: 4498
```

**4.5 µs per keystroke is not the risk, and a cache "for speed" would be unexamined ceremony.** So
the service is not justified on latency. It is justified on the two grounds that survive, and the
third — the pathological-mount argument — is explicitly *not* one of them.

1. **It is the data source `DEBUG-06-07` needs, not a cache in front of one.** `-07` is a `Must`
   whose whole content is that `fileExists`/`executable` are computed and then read by nothing
   (`grep -rn '\.health' src/main/kotlin/net/internetisalie/lunar/run/` → `0`). Checks 4, 5, 6 and 8
   (§3.2; check 6 via §3.6 step 2) each need `exists` for a path, and `adHocRuntime` needs `exists`
   **and** `executable` for the runtime path (§2.4). `LuaPathFact` is that value; a `LuaPathFacts`
   lookup is how each obtains it. Without the service every one of those call sites touches
   `java.io.File` itself and the pair is recomputed per check.

   **Check 2 is not in that list — it reads stored health, not `LuaPathFacts`.** Its three branches
   are `health.fileExists`, `health.executable`, `health.probeOk` (§3.2), i.e.
   `spec.runtime.health` (`toolchain/model/LuaRegisteredTool.kt:24-30`). The memo reaches the runtime
   path only through §2.4's rewiring of `adHocRuntime`, which is the **ad-hoc** branch of
   `resolveConfiguredRuntime` (`run/LuaRuntimeResolution.kt:26,43-44`); a registry hit
   (`findByPath`, `:26`) or the project default (`:25`) returns persisted state verbatim
   (`exactMatch.toModel()`, `toolchain/registry/LuaToolchainRegistry.kt:178-181`) and is never
   re-stat'd by `checkConfiguration()`. §6's stale-registry-health row states the bound that leaves.
   Both readings agree on every fixture in §9 — §3.2 is the normative one.
2. **It de-duplicates a `stat` pair this feature would otherwise multiply.** `adHocRuntime` performs
   `File.exists()` + `File.canExecute()` on **every** keystroke in **any** field of the editor today
   (`run/LuaRuntimeResolution.kt:43-44`) — that is exactly the defect `DEBUG-06-05` records. This
   feature adds four more path-touching checks. Rewiring `adHocRuntime` through the memo (§2.4) and
   routing checks 4/5/6/8 through it means one validation pass over `n` distinct paths costs `2n`
   `stat`s per 2 s window instead of `2n` per keystroke — and, critically, the design does not raise
   the per-keystroke count from 2 to 10 in the course of "fixing" `-05`.

**What the TTL does and does not bound — stated precisely, because an earlier draft got this
backwards.** A TTL memo bounds **frequency, not latency**. On a pathological path — a run
configuration pointing into an unreachable autofs/NFS/SMB mount — the *first* `stat` of every TTL
window still blocks for the full mount timeout, inside `ReadAction.nonBlocking`, holding a read
action and stalling both the dialog's validation and `RunConfigurationIconAndInvalidCache`'s badge
recompute. The memo reduces how *many* such stalls a typing burst produces (once per path per 2 s
rather than once per character); it does not shorten any single one of them, and nothing in this
design does. That residual is **not measured** — DR-01 in [risks-and-gaps.md](risks-and-gaps.md) is
the tracked task that measures it, and it is `todo`. If DR-01 shows a single stall is itself
unacceptable, the fix is off-thread staleness (compute on a pooled task, serve the last-known fact
immediately), not a shorter or longer TTL; that is a change to §3.4 alone.

The TTL is short rather than long for a UX reason unrelated to the above: a user who runs `chmod +x`
or creates the missing file sees the dialog agree within one edit round-trip (Risk 1.5).

### 3.5 Language-level comparison (`DEBUG-06-08`)

**Input → Output**: `LuaTargetSpec` → `LuaTargetProblem?`.

1. `val runtimeLevel = spec.runtime?.runtime?.languageLevel ?: return null`
   (`LuaRuntimeInfo.languageLevel`, `toolchain/model/LuaRegisteredTool.kt:38`).
2. `val projectLevel = spec.projectLanguageLevel`.
3. Compare on `(major, minor)` lexicographically — **not** on `ordinal`, so a future reordering of
   `LuaLanguageLevel` (`lang/LuaLanguageLevel.kt:19-32`) cannot silently invert the rule:
   ```kotlin
   val runtimeOlder = compareValuesBy(runtimeLevel, projectLevel, { it.major }, { it.minor }) < 0
   ```
4. Return a `WARNING` problem **only when `runtimeOlder`**. A *newer* runtime than the project level
   is not reported: running 5.1 sources on a 5.4 binary is the normal upgrade path, whereas 5.4
   sources on a 5.1 binary fails at the first `goto` or `//`.
5. Otherwise `null`.

**Why `WARNING` and not `FATAL`.** `DEBUG-06-08` is priority **C** and its own rationale is that
"the mismatch is loud when it happens". Blocking a launch on a heuristic comparison of a *probed*
version would refuse legitimate targets (a 5.4 binary a user deliberately runs 5.1 sources under).

### 3.6 Script reachability from the debugger base directory (`DEBUG-06-11`)

`LuaDebuggerController` derives `baseDir` from the working directory
(`run/LuaDebuggerController.kt:80-90`) and every breakpoint is translated with
`LuaPosition.createRemotePosition(sourcePosition, workingDir)`
(`run/LuaDebuggerController.kt:220,230`, `run/LuaDebugProcess.kt:82`), which is
`FileUtil.getRelativePath(workingDir, target) ?: target.path` (`run/LuaPosition.kt:38-48`).

**Predicate** (`LuaTargetChecks.SCRIPT_REACHABLE`):

1. Return `null` if `scriptPath.isNullOrEmpty()` or `workingDirectory.isNullOrEmpty()`.
2. Return `null` if `!LuaPathFacts.of(workingDirectory).exists` (check 5 owns that case).
3. `val relative = FileUtil.getRelativePath(File(workingDirectory), File(scriptPath))`.
4. Fire when `relative == null || relative.startsWith("..")`.

**Executed evidence** for step 4. `FileUtilRt.getRelativePath`
(`platform/util-rt/src/com/intellij/openapi/util/io/FileUtilRt.java:400-428`) emits `../` segments
for every path outside the base and returns `null` only when the two share no common prefix at all
(different Windows drives). Transcribed verbatim and run
(`~/.cache/claude-scratch/lunar/debug06/RelProbe.java`):

```
base=/home/u/proj     file=/home/u/proj/main.lua          -> main.lua
base=/home/u/proj     file=/home/u/proj/src/deep/main.lua -> src/deep/main.lua
base=/home/u/proj     file=/home/u/other/main.lua         -> ../other/main.lua
base=/home/u/proj     file=/tmp/main.lua                  -> ../../../tmp/main.lua
base=/home/u/proj     file=/home/u/projekt/main.lua       -> ../projekt/main.lua
base=/home/u/proj/    file=/home/u/proj/main.lua          -> main.lua
```

Row 5 is the one that would have bitten a naïve `startsWith(basePath)` string test: `/home/u/projekt`
is not under `/home/u/proj`, and `getRelativePath` reports it correctly because it appends a
separator to the base before comparing (`FileUtilRt.java:401`). Row 6 shows a trailing separator on
the base is harmless, which matters because `LuaDebuggerController:87` appends one.

### 3.7 Debug-port availability (`DEBUG-06-15`)

```kotlin
fun isAvailable(port: Int): Boolean =
    try {
        ServerSocket().use { probe ->
            probe.reuseAddress = false
            probe.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1)
        }
        true
    } catch (_: IOException) {
        false
    }
```

`ServerSocket` implements `Closeable`, so `use` closes the probe before the interpreter is spawned.

**Executed evidence** (`~/.cache/claude-scratch/lunar/debug06/PortProbe.java`, `ReuseProbe.java`):

```
probe free 8172 (nothing bound): true          held port 33031
probe free 8172 (held):          false           bind with reuseAddress=false -> false
probe free 8172 (after close):   true            bind with reuseAddress=true  -> false
100 probes took: 2.25 ms                       after close, reuseAddress=false -> true
probe(8173)=true then real ServerSocket(8173) bound OK
```

Three facts this pins down, none of which is safe to assume:

1. **Probe-then-release does not poison the port.** The probe never `accept()`s, so there is no
   `TIME_WAIT`; the real `ServerSocket(serverPort)` in `LuaDebuggerController.connect()`
   (`run/LuaDebuggerController.kt:114-117`) binds immediately afterwards.
2. **`setReuseAddress` is not load-bearing.** On Linux `SO_REUSEADDR` does not permit binding over a
   live listener (that is `SO_REUSEPORT`), so `reuseAddress = false` is defensive, not the mechanism.
   Do not write a test whose named mutation is flipping it — it would stay green.
3. **A probe costs ~22 µs.** Cheap in absolute terms, and still not run per keystroke: `DEBUG-06-15`
   asks for a *pre-launch* verdict, and a probe in `checkConfiguration()` would open a loopback
   socket after every character typed in every field of the dialog.

**Two residuals, both accepted, and they are different things.**

1. **A timing race.** Another process may take the port between the probe and the real bind. Nothing
   short of holding the socket across the spawn removes it, and holding it would prevent
   `LuaDebuggerController.connect()` from binding at all.
2. **A bind-scope mismatch.** The probe binds `InetAddress.getLoopbackAddress()`;
   `LuaDebuggerController.connect()` binds `ServerSocket(serverPort)`
   (`run/LuaDebuggerController.kt:116`), the **wildcard** address. A port already held on a
   *non-loopback* interface therefore passes the probe and still fails the real bind. This is a
   narrowing of coverage, not a false positive: every port the probe rejects would also fail the
   wildcard bind, so the gate never blocks a launch that would have worked. It is accepted rather
   than fixed because the case `DEBUG-06-15` names — *"Two Lunar debug sessions on the default 8172
   hit this every time"* — is a loopback-and-wildcard collision that the probe **does** catch, and
   because probing the wildcard would make the gate fail on a port some unrelated service holds on
   one interface. The existing catch in `LuaDebugProcess` remains the backstop for both residuals.

**`LuaDebugProcess` (MODIFIED).** `run/LuaDebugProcess.kt:121-131` currently reports a bind failure
with `log.error("Failed to connect to debugger", e)` (`:122`), which the platform escalates to an
IDE internal-error report for a *user configuration* problem. Change `:122` to `log.warn(...)` and
prefix the dialog text at `:127` with the port:

```kotlin
Messages.showErrorDialog(
    "Unable to establish connection with debugger on port ${controller.port()}:\n${e.message}",
    "Connecting to Debugger",
)
```

`LuaDebuggerController` gains `fun port(): Int = serverPort`, mirroring the existing
`fun workingDirectory(): File = workingDir` (`run/LuaDebuggerController.kt:72`).

### 3.8 The quick fix (`DEBUG-06-20`)

```kotlin
class LuaToolchainSettingsQuickFix : ConfigurationQuickFix {
    override fun applyFix(dataContext: DataContext) {
        val targetProject = CommonDataKeys.PROJECT.getData(dataContext) ?: return
        ShowSettingsUtil.getInstance()
            .showSettingsDialog(targetProject, LuaToolchainConfigurable::class.java)
    }
}
```

- **Threading**: `applyFix` runs on the EDT (a dialog button press); `showSettingsDialog` is an EDT
  call. The instance is *constructed* on the pooled validation thread and holds no `Project` — the
  project comes from the `DataContext` at fix time, which is why §2.1's spec needs no `Project`
  field and the contract's memory rule is satisfied without a `WeakReference`.
- Grounded: the `ShowSettingsUtil.getInstance().showSettingsDialog(project, LuaToolchainConfigurable::class.java)`
  idiom is already used at `toolchain/health/LuaToolEditorNotificationProvider.kt:89` and
  `rocks/browser/PackageDetailPane.kt:339`.
- Attached to check 1 only. Checks 2, 4, 5 name a path the user must fix in a field; there is no
  single action that repairs them.

### 3.9 The context producer's three predicates (`DEBUG-06-18`)

`LuaRunConfigurationProducer` (§2.9). Each override is a numbered rule below; none is narrated.
The shape mirrors `redis/run/LuaRedisRunConfigurationProducer.kt:21-53` and
`run/test/LuaTestRunConfigurationProducer.kt:21-42` — the two producers already registered
(`plugin.xml:606-607`, `:615-616`).

#### 3.9.1 `setupConfigurationFromContext(configuration, context, sourceElement): Boolean`

**Input → Output**: a `ConfigurationContext` → `false` (decline, no configuration is offered), or
`true` with `configuration` mutated.

1. `val targetLocation = context.location ?: return false`
   (`LuaRedisRunConfigurationProducer.kt:26`).
2. `val targetVirtualFile = targetLocation.virtualFile ?: return false` (`:28`).
3. `val targetFile = targetLocation.psiElement.containingFile ?: return false` (`:29`). A
   `PsiDirectory` declines here, because `PsiDirectoryImpl.getContainingFile()` returns `null`
   (`platform/core-impl/src/com/intellij/psi/impl/file/PsiDirectoryImpl.java:301-303`) — verified,
   not assumed. That is deliberate: the test producer owns directories, handling them *before* this
   line (`LuaTestRunConfigurationProducer.kt:30-34`), and a *Run* target for a directory has no
   meaning here (`LuaRunConfiguration` launches exactly one script).
4. `if (targetFile.fileType.name != "Lua") return false` — the identical guard both existing
   producers carry (`LuaRedisRunConfigurationProducer.kt:30`,
   `LuaTestRunConfigurationProducer.kt:37`). TC-06-18b.
5. **`if (isRedisTarget(context)) return false`** — the REDIS decline. This is the exact mirror of
   `LuaRedisRunConfigurationProducer.kt:31` (`if (!isRedisTarget(context)) return false`), and it is
   grounded the same way, by the same private helper (`:48-53`):

   ```kotlin
   private fun isRedisTarget(context: ConfigurationContext): Boolean =
       LuaProjectSettings
           .getInstance(context.project)
           .state
           .getTarget()
           .platform == LuaPlatform.REDIS
   ```

   `LuaProjectSettings.State.getTarget()` is at `settings/LuaProjectSettings.kt:125-131`;
   `LuaPlatform.REDIS` at `platform/LuaPlatform.kt:11`. With the two guards inverted relative to each
   other, **exactly one** of the two producers offers on a `.lua` file for any project target. This
   is what §1.2's "Mirrored gating" row asserts and what TC-06-18c tests.
6. `configuration.scriptName = targetVirtualFile.path` — the public accessor at
   `run/LuaRunConfiguration.kt:196-200`. Note the name: it is `scriptName` on the configuration and
   `scriptPath` on `LuaTargetSpec` (§2.1); the value is a full path in both.
7. `configuration.name = targetVirtualFile.name` — the bare file name, e.g. `main.lua`, so the
   action reads *Run 'main.lua'* / *Debug 'main.lua'*. **No prefix**, unlike the Redis producer's
   `"Redis Script: ${name}"` (`:34`) and the test producer's `"Lua Tests in ${name}"`
   (`LuaTestRunConfigurationProducer.kt:51`): those two disambiguate a *specialised* target, and this
   producer creates the ordinary one. TC-06-18a asserts this exact string.
8. `sourceElement.set(targetFile)` (`LuaRedisRunConfigurationProducer.kt:36`).
9. `return true`.

**Fields deliberately left unset**, so an implementer does not add them:

- **`workingDirectory`** — left null. `effectiveWorkDirectory()` already falls back to
  `project.basePath` (`run/LuaRunConfiguration.kt:228`). Pre-filling the script's own directory would
  make check 6 (`SCRIPT_REACHABLE`, §3.6) unable to ever fire for a context-created configuration,
  which is the opposite of what `DEBUG-06-11` asks for.
- **`interpreter`** — left null. `resolveInterpreter()` tracks the project default dynamically
  (`run/LuaRunConfiguration.kt:213-219`); freezing a snapshot is what that method's own KDoc declines
  to do. (The test producer sets it at `LuaTestRunConfigurationProducer.kt:52-55` for the *directory*
  case only; that is not a precedent for this one.)

#### 3.9.2 `isConfigurationFromContext(configuration, context): Boolean`

1. `val targetVirtualFile = context.location?.virtualFile ?: return false`.
2. `return configuration.scriptName == targetVirtualFile.path`.

Verbatim in shape from `LuaRedisRunConfigurationProducer.kt:44-45`, with `scriptName` in place of
`scriptPath`. It answers "does this existing configuration already target this file", which is how
the platform avoids creating a duplicate (`RunConfigurationProducer.java:140-145`).

#### 3.9.3 `isPreferredConfiguration(self, other): Boolean`

```kotlin
override fun isPreferredConfiguration(
    self: ConfigurationFromContext,
    other: ConfigurationFromContext?,
): Boolean = !yieldsTo(other?.configuration)

internal fun yieldsTo(other: RunConfiguration?): Boolean =
    other is LuaTestRunConfiguration || other is LuaRedisRunConfiguration
```

1. `other == null` → `yieldsTo(null)` is `false` → return `true`. Nothing to yield to.
2. `other.configuration` (`ConfigurationFromContext.java:40-42`) is a `LuaTestRunConfiguration`
   (`run/test/LuaTestRunConfiguration.kt`) → return `false`: this producer is discarded and the test
   entry wins. This is §1.2's "yields to it via `isPreferredConfiguration` rather than copying its
   `isTestFile` heuristic" — the heuristic at `LuaTestRunConfigurationProducer.kt:141-152` is **not**
   re-derived here, which is Risk 1.6's mitigation.
3. `other.configuration` is a `LuaRedisRunConfiguration` (`redis/run/LuaRedisRunConfiguration.kt`) →
   return `false`. Belt and braces: step 5 of §3.9.1 already means the two never co-occur, so this
   arm is unreachable through the registered producers and exists so that removing one guard does not
   silently produce two entries.
4. Anything else (including another plugin's configuration) → return `true`, the platform default
   (`RunConfigurationProducer.java:158`).

`shouldReplace` is **not** overridden — the default `return false`
(`RunConfigurationProducer.java:173-175`) is correct: this producer never wants the other entry
removed, only to lose to it.

## 4. External Data & Parsing

### 4.1 Environment files (`DEBUG-06-17`)

- **Format**: `KEY=VALUE` lines, `export ` prefix stripped, `#` comments, double-quoted values that
  may span lines. Lunar does **not** write a parser: the platform's `parseEnvFile(text: String): Map<String, String>`
  (`platform/execution-impl/src/com/intellij/execution/envFile/EnvFileParser.kt:4`) is a public,
  non-experimental top-level function and is used verbatim.
- **Applied at launch** in `startProcess()`, around
  `environmentVariables?.configureCommandLine(commandLine, true)` (`run/LuaRunConfiguration.kt:317`):

  ```kotlin
  private fun envFileVariables(): Map<String, String> =
      envFilePaths
          .filterNot { it.substringAfterLast('.').lowercase() in ENV_SCRIPT_EXTENSIONS }
          .mapNotNull { path -> runCatching { parseEnvFile(Path.of(path).readText()) }.getOrNull() }
          .fold(mutableMapOf()) { acc, envs -> acc.apply { putAll(envs) } }
  // ENV_SCRIPT_EXTENSIONS = setOf("sh", "bat")
  ```

  Later files win over earlier ones (the `fold` `putAll`s in list order), and a run-configuration
  `envs` entry set in the table wins over a file. **Apply the file map first, so the table overwrites
  it**, in exactly this order:

  ```kotlin
  commandLine.withEnvironment(envFileVariables())
  environmentVariables?.configureCommandLine(commandLine, true)
  ```
- **Validated at edit time** by check 8 (§3.2), existence only, at the `ADVISORY` tier — which is the
  tier the platform's own `checkEnvFiles` produces: it converts a `ParametersConfiguratorException`
  into a bare `RuntimeConfigurationException`
  (`platform/execution-impl/src/com/intellij/execution/util/EnvFilesUtil.kt:23-33`).
- **Failure handling**: a missing file is `ADVISORY` at edit time and silently skipped at launch
  (`runCatching { … }.getOrNull()`); a malformed line is dropped by `parseEnvFile` itself (it
  `continue`s on any line without `=`, `EnvFileParser.kt:9`).
- **`.sh`/`.bat` env *scripts* are out of scope** — see §10.3 for why, and
  [risks-and-gaps.md](risks-and-gaps.md) TBD-1.

There is no other external or unstructured input in this feature.

## 5. Data Flow

### 5.1 A user types a runtime path that does not exist

1. `SingleConfigurationConfigurable` fires validation →
   `RunnerAndConfigurationSettingsImpl.checkSettings` wraps it in `ReadAction.nonBlocking` on a
   pooled thread (`RunnerAndConfigurationSettingsImpl.kt:371-385`).
2. `LuaRunConfiguration.checkConfiguration()` → `LuaTargetSpec.of(this)`.
   `resolveInterpreter()` → `resolveConfiguredRuntime` → registry miss → `adHocRuntime(path)`
   (`run/LuaRuntimeResolution.kt:26,30`), which now asks `LuaPathFacts` and gets
   `LuaPathFact(exists = false, executable = false, …)` — **one** `stat` pair for the next 2 s of
   typing, not one per character.
3. Check 1 passes (`runtime != null` — `adHocRuntime` always returns an object). Check 2 fires:
   `!isUsable` because `health.fileExists == false` → `FATAL`, `runtimeMissing(path)`.
4. `validate` collects `[FATAL]`, `maxByOrNull` picks it, `asException` yields
   `RuntimeConfigurationError`.
5. The dialog shows a **red** banner; `RunManagerImpl.canRunConfiguration` catches
   `RuntimeConfigurationError` and returns `false` (`RunManagerImpl.kt:162-164`); Run and Debug are
   disabled. This is the behaviour change `DEBUG-06-02` asks for.

### 5.2 A fatal problem and a warning coexist

Runtime unset **and** script name empty. Check 1 → `FATAL`; check 3 → `WARNING`. `problems` has two
entries; `maxByOrNull { rank }` returns the `FATAL` regardless of list order. The user sees the
blocking error, not the cosmetic one. Under the old throw-in-place code this depended on which `if`
came first.

### 5.3 Debug is pressed with 8172 already held

1. `LuaDebugRunner.doExecute` resolves the port and the asset directory, then calls
   `checkDebugTargetReady(8172, pluginLuaDirectory)` (§2.7) **before** `state.execute(...)`.
2. `LuaDebugPortProbe.isAvailable(8172)` binds loopback:8172, catches `BindException`, returns
   `false`.
3. `ExecutionException(debugPortInUse(8172))` → the platform aborts the launch and names the port
   and the field.
4. **No interpreter process is spawned**, so there is no orphan to kill, no `log.error`, and no
   `Messages.showErrorDialog` from `LuaDebugProcess`. Today the process is spawned first
   (`LuaDebugRunner.kt:73`) and the bind happens afterwards
   (`LuaDebuggerController.kt:114-117`).

## 6. Edge Cases

| Case | Handling |
| :-- | :-- |
| Stored runtime path is non-empty, **not in the registry**, and the file was deleted after the config was saved | Check 2 → `FATAL`. `adHocRuntime` returns a non-null tool for *any* non-empty path (`LuaRuntimeResolution.kt:26`) and stats it on every resolve (`:43-44`, through `LuaPathFacts` per §2.4); usability, not nullity, is the test. |
| **Registry** runtime whose file was deleted after its last probe | Check 2 does **not** fire, and this is a stated bound rather than a fix. `findByPath` returns the stored tool verbatim (`exactMatch.toModel()`, `toolchain/registry/LuaToolchainRegistry.kt:178-181`), so `health.fileExists` is the *probe-time* value and `isUsable` stays `true`; `checkConfiguration()` never re-stats the runtime path on that branch (§3.4). Refreshing stored health belongs to `toolchain/health/LuaToolHealthMonitor.kt`, which revalidates on VFS events (`:69,239-249`) and writes through `updateToolCheck` (`:130-134`) — the stale window closes when it next runs, and a deletion the VFS never observes leaves it open. Out of scope for DEBUG-06: adding a fresh `stat` here would re-introduce the per-keystroke I/O `DEBUG-06-05` exists to bound. The launch still fails, later, at `ExecutionException` time. |
| Runtime resolves from the registry and is healthy | `isUsable` is `true` (`LuaRegisteredTool.kt:32-33`); check 2 returns `null`. No new I/O — registry lookups are in-memory (`LuaToolResolver.kt:60-84`). |
| Ad-hoc runtime, never probed | `runtime.runtime == null` (`LuaRuntimeResolution.kt:38`) → check 7 returns `null`. No level comparison against a level we do not know. |
| Script unset (interactive REPL) | Check 3 `WARNING` only — the deliberate `lua -v -i` fallback (`run/LuaRunConfiguration.kt:304-306`, RUN-04-04). Checks 4 and 6 short-circuit. |
| Working directory unset | `effectiveWorkDirectory()` already falls back to `project.basePath` (`:228`), so `spec.workingDirectory` is non-empty whenever the project has a base path. On a project with a null base path it is `null`, and checks 5 and 6 return `null`. |
| Debug port out of range | Unrepresentable: `JBIntSpinner(DEFAULT_DEBUG_PORT, 1, 65535)` (`:380`). `DEBUG-06-14` is already `Full`; **no check is added** — validating what the widget cannot produce would be dead code. |
| Two problems of equal severity | First in the §3.2 declaration order wins (`maxByOrNull` semantics, §3.1 step 3). |
| `System.nanoTime()` goes backwards across cores | §3.4 step 2 treats a negative delta as a miss. |
| Env file list empty | Check 8 iterates nothing and returns `null`; `envFileVariables()` folds to an empty map. |
| Test configuration | `TEST_TARGET` runs only checks 1–2; the `testTarget` branch keeps its own bare `RuntimeConfigurationException` (`LuaTestRunConfiguration.kt:291-293`), unchanged. |
| Redis configuration | Untouched (§1.2). |

## 7. Integration Points

Exactly **one** new `plugin.xml` registration. The `<configurationType>` for
`LuaRunConfigurationType` already exists at `src/main/resources/META-INF/plugin.xml:602-603`, and
`<programRunner implementation="net.internetisalie.lunar.run.LuaDebugRunner"/>` at `:656`. Both are
edited in place only by modifying their Kotlin classes.

Insert immediately after the existing test producer at `:606-607`, inside the same
`<extensions defaultExtensionNs="com.intellij">` block:

```xml
<!-- DEBUG-06-18: Debug/Run 'x.lua' from the editor or Project view context menu -->
<runConfigurationProducer
        implementation="net.internetisalie.lunar.run.LuaRunConfigurationProducer"/>
```

`LuaPathFacts` is a light `@Service(Service.Level.APP)` obtained via
`ApplicationManager.getApplication().getService(...)`; per the platform's light-services rule it
needs **no** `plugin.xml` entry — the same pattern as `LuaToolResolver`
(`toolchain/resolve/LuaToolResolver.kt:16,174-176`), which is likewise unregistered (executed:
`grep -n LuaToolResolver src/main/resources/META-INF/plugin.xml` → no match).

No new indexes, no new settings keys. The only new persisted state is the `envFilePaths` list
property on `LuaRunConfigurationOptions` (§2.8), which serialises through the existing
`RunConfigurationOptions` `BaseState` mechanism.

## 8. Requirement Coverage

| Requirement | Priority | Status in requirements.md | Implemented by |
| :-- | :-: | :-- | :-- |
| `DEBUG-06-01` | M | Full | §2.6 — the override survives, now delegating |
| `DEBUG-06-02` | M | Not Implemented | §2.2, §3.1, §3.2 check 1–2 (`FATAL` → `RuntimeConfigurationError`) |
| `DEBUG-06-03` | S | Partial | §3.1 (collect-then-throw), §3.2 severity column |
| `DEBUG-06-04` | M | Full | §3.1 `asException` — total over the enum, sole throw site |
| `DEBUG-06-05` | S | Partial | §2.4, §3.4 (`LuaPathFacts`; `adHocRuntime` rewired) |
| `DEBUG-06-06` | M | Partial | §3.2 check 1 |
| `DEBUG-06-07` | M | Not Implemented | §3.2 check 2 (reads `tool.health` via `isUsable`) |
| `DEBUG-06-08` | C | Not Implemented | §3.2 check 7, §3.5 |
| `DEBUG-06-09` | M | Full | §3.2 check 3 (tier preserved verbatim) |
| `DEBUG-06-10` | M | Not Implemented | §3.2 check 4 |
| `DEBUG-06-11` | S | Not Implemented | §3.2 check 6, §3.6 |
| `DEBUG-06-12` | S | Not Implemented | §3.2 check 5 |
| `DEBUG-06-13` | M | Partial | §2.7 — hoisted from `startProcess()` to a pre-spawn gate, taking the asset directory as an argument so it is testable (TC-06-13a/b/c); §10.4 for why not edit-time |
| `DEBUG-06-14` | S | Full | §6 — no change; already unrepresentable via `JBIntSpinner(…, 1, 65535)` |
| `DEBUG-06-15` | S | Not Implemented | §2.7, §3.7 (probe) + `LuaDebugProcess` log/message fix |
| `DEBUG-06-16` | C | **Won't** | §10.2 — no design; the `environmentFile` property is left inert |
| `DEBUG-06-17` | S | Not Implemented | §2.8, §2.8.1 (the single-label decision), §4.1 |
| `DEBUG-06-18` | S | Not Implemented | §2.9 (shape), §3.9 (all three predicates), §7 (registration) |
| `DEBUG-06-19` | C | **Won't** | §2.10 — no attach target designed; extensibility only |
| `DEBUG-06-20` | C | Not Implemented | §3.8 |
| `DEBUG-06-21` | S | Full | §3.3 (vocabulary preserved; option key unchanged) |
| `DEBUG-06-22` | C | Not Implemented | §3.3 (`notConfiguredMessage`, plus the `runtime-capability` trap) |
| `DEBUG-06-23` | S | Partial | §3.3 + §3.8 (message names the page; quick fix opens it) |

Every `Must` row (`-01`, `-02`, `-04`, `-06`, `-07`, `-09`, `-10`, `-13`) has a section above and a
test case below.

## 9. Acceptance Test Cases

`requirements.md` has no TC table (it is a retroactive SRS), so acceptance lives here. **Every row
names the mutation that turns it red, and every mutation is reachable from that row's own fixture** —
the failure mode `requirements.md`'s own Verification section demonstrates
(`testCheckConfigurationThrowsWithoutRuntime` asserts `assertFailsWith<RuntimeConfigurationException>`,
which passes at all three rungs because `Error` and `Warning` both subclass it).

**Files.**
- `src/test/kotlin/net/internetisalie/lunar/run/validation/LuaTargetValidationTest.kt` (**NEW**),
  extending `net.internetisalie.lunar.BaseDocumentTest` with `@Test` from `kotlin.test` — the idiom
  of `TestLuaRunConfiguration.kt:27,57`. `BaseDocumentTest` is a plain `open class`, so **every**
  method needs `@Test` or it is silently never collected ([[BUG-461]] §1).
- `src/test/kotlin/net/internetisalie/lunar/run/LuaRunConfigurationProducerTest.kt` (**NEW**),
  extending `BasePlatformTestCase` — the idiom of `TestLuaRedisRunConfigurationProducer.kt:16-47`.
  Holds TC-06-18a/b/c/d, and reuses that file's `setTarget(platform)` helper (`:17-20`) verbatim.

`LuaDebugRunner.checkDebugTargetReady` is `internal` (§2.7), and Kotlin's `internal` is
**module**-scoped, not package-scoped — so TC-06-13a/b/c and TC-06-15a/b live in
`LuaTargetValidationTest.kt` alongside the rest despite being in a different package. Same for
`LuaRunConfigurationProducer.yieldsTo` from `LuaRunConfigurationProducerTest.kt`. Prior art:
`PublishRockAuthFailureTest.kt:14` calls `PublishRockAction.isAuthFailure`, `internal` at
`rocks/publish/PublishRockAction.kt:144`, from a different package.
- `src/test/kotlin/net/internetisalie/lunar/run/TestLuaRunConfiguration.kt` (**MODIFIED**) — gains
  exactly **one** new case, **TC-06-17a**, the env-file editor round-trip, because it reuses the
  `LuaRunSettingsEditor` reset/apply fixture already in that file
  (`testDebugPortRoundTripsThroughEditor`, `:156-175`); and the Phase 2 tightening of the existing
  `testCheckConfigurationThrowsWithoutRuntime` (`:177-187`). **TC-06-17a's home is this file, not
  `LuaTargetValidationTest`** — its sibling TC-06-17b (check 8's severity) does live in
  `LuaTargetValidationTest`. Both classes extend `BaseDocumentTest`, so this is placement, not
  capability.

**Shared fixture helpers** in `LuaTargetValidationTest` (real files, not the light fixture's
in-memory `temp://` VFS, because the checks call `java.io.File`):

```kotlin
private lateinit var scratch: File               // Files.createTempDirectory("debug06").toFile()
@AfterEach fun cleanScratch() { FileUtil.delete(scratch); LuaPathFacts.getInstance().clear() }
private fun executableAt(name: String): File     // create, setExecutable(true)
private val HEALTHY = LuaToolHealth(fileExists = true, executable = true, probeOk = true, probedAtMtime = 1L, reason = null)
private fun seedRuntime(
    path: String,
    level: LuaLanguageLevel = LuaLanguageLevel.LUA54,
    health: LuaToolHealth = HEALTHY,
): LuaRegisteredTool
    // exactly TestLuaRunConfiguration.kt:33-55, with the level threaded into LuaRuntimeInfo (:41)
    // and `health` replacing the literal at :44-51. Three parameters, at the contract's cap:
    // probeOk/reason travel inside LuaToolHealth rather than as two more arguments.
private fun configWith(scriptPath: String?, workDir: String?): LuaRunConfiguration
```

**Assertion discipline.** Every severity assertion uses `assertEquals(X::class, thrown::class)` on
the *exact* class, never `assertFailsWith<RuntimeConfigurationException>`.

| TC | Requirement | Fixture (input) | Assertion (output) | Mutation that turns it red — reachable from THIS fixture |
| :-- | :-- | :-- | :-- | :-- |
| TC-06-02a | `-02`, `-06` | Empty registry + empty project bindings (`loadState(LuaToolchainAppState())`, `loadState(LuaToolchainProjectState())` as at `TestLuaRunConfiguration.kt:180-181`); `interpreter` unset; script unset | `assertEquals(RuntimeConfigurationError::class, thrown::class)` | Change check 1's severity `FATAL` → `ADVISORY`. The fixture has **no** runtime, so check 1 is the firing check; the thrown class becomes `RuntimeConfigurationException` and the exact-class assert fails. |
| TC-06-02b | `-03` | Same as TC-06-02a: **no runtime (FATAL) *and* empty script name (WARNING)** — two problems | `assertEquals(RuntimeConfigurationError::class, thrown::class)` | Replace `maxByOrNull { it.severity.rank }` with `minByOrNull { … }` in §3.1 step 3. The fixture produces exactly one FATAL and one WARNING, so the mutant throws `RuntimeConfigurationWarning`. (A fixture with only one problem could not distinguish the two.) |
| TC-06-03a | `-03`, `-09` | `seedRuntime(executableAt("lua").path)` registered and bound; `scriptName = ""` | `assertEquals(RuntimeConfigurationWarning::class, thrown::class)`; `assertEquals("No script file configured", thrown.message)` | Change check 3's severity `WARNING` → `FATAL`. The fixture's only problem is the unset script, so the thrown class flips to `RuntimeConfigurationError`. |
| TC-06-04a | `-04` | Table-driven over `LuaTargetSeverity.entries`, calling `LuaTargetValidator.asException(LuaTargetProblem("m", severity))` | For each severity, the **exact** class per §2.2's table: `assertEquals(RuntimeConfigurationError::class, asException(FATAL)::class)`, `assertEquals(RuntimeConfigurationException::class, asException(ADVISORY)::class)`, `assertEquals(RuntimeConfigurationWarning::class, asException(WARNING)::class)`. **No `assertIs<RuntimeConfigurationException>` anywhere in this row** — `asException` is *declared* to return that type, so such an assertion is guaranteed by the signature and asserts nothing. | Map `ADVISORY -> RuntimeConfigurationWarning(problem.message)`. It compiles (`RuntimeConfigurationWarning` *is* a `RuntimeConfigurationException`, `RuntimeConfigurationWarning.java:10`), it is reachable — `ADVISORY` is one of the three entries the fixture iterates — and the `ADVISORY` row's exact-class assertion goes red because `::class` is `RuntimeConfigurationWarning`, not `RuntimeConfigurationException`. (The mutation an earlier draft named, `ADVISORY -> ConfigurationException("m")`, **does not compile**: `RuntimeConfigurationException extends ConfigurationException` — `platform/execution/…/RuntimeConfigurationException.java:15` — so the mutant returns a supertype of the declared return type.) |
| TC-06-05a | `-05` | `val p = executableAt("lua").path`; `LuaPathFacts.getInstance()` | `of(p, 0L).exists` is `true`; **delete the file**; `of(p, 1_000_000L).exists` is still `true`; `of(p, 3_000_000_000L).exists` is `false` | (a) `TTL_NANOS = 0` → the second call re-stats and returns `false`, red at step 3. (b) Return the cached fact unconditionally (drop the TTL test) → the fourth call still returns `true`, red at step 4. Both mutants are reachable because the fixture deletes the file *between* calls and drives the clock explicitly. |
| TC-06-07a | `-07` | `interpreter = "${scratch}/never-created"`; registry empty so `findByPath` misses → `adHocRuntime` | `assertEquals(RuntimeConfigurationError::class, thrown::class)`; `assertTrue(thrown.message.contains("never-created"))`; `assertTrue(thrown.message.contains("does not exist"))` | Delete check 2 entirely. The fixture's tool is non-null (`adHocRuntime` never returns null), so check 1 does not fire and the mutant throws only the script warning — exact-class assert fails. |
| TC-06-07b | `-07` | A **real** file with `setExecutable(false)`, guarded by `Assumptions.assumeTrue(!file.canExecute())` (a root test runner cannot express a non-executable file) | `assertEquals(RuntimeConfigurationError::class, thrown::class)`; `assertTrue(thrown.message.contains("not executable"))` | Weaken check 2 from `!isUsable` to `!health.fileExists`. **Unreachable from TC-06-07a's fixture** (that file does not exist at all) — this fixture is the only one where `fileExists && !executable`, which is why both rows exist. |
| TC-06-07c | `-07` | `seedRuntime(executableAt("lua").path, health = HEALTHY.copy(probeOk = false, reason = "exit code 1"))` — `LuaToolHealth` is a `data class` (`toolchain/model/LuaRegisteredTool.kt:24-30`) so `copy` is available, and `seedRuntime` registers via `registerProvisioned` (`TestLuaRunConfiguration.kt:53`); `interpreter` = that same path, so `findByPath` **hits** the registry tool rather than falling through to `adHocRuntime`. Script exists inside the working dir | `assertEquals(RuntimeConfigurationError::class, thrown::class)`; `assertTrue(thrown.message.contains("failed its health check"))`; `assertTrue(thrown.message.contains("exit code 1"))` | Collapse §3.2's three-way message back to the two-way `if (!health.fileExists) runtimeMissing(path) else runtimeNotExecutable(path)`. Reachable from **this** fixture only: it is the sole row where `fileExists && executable && probeOk == false`, so TC-06-07a (file absent) and TC-06-07b (present, not executable) both stay green under that mutant while this row's message assertions go red. |
| TC-06-08a | `-08` | `seedRuntime(path, LuaLanguageLevel.LUA51)`; `LuaProjectSettings.getInstance(myFixture.project).state.languageLevel = LuaLanguageLevel.LUA54`; script = a real file inside the working dir | `assertEquals(RuntimeConfigurationWarning::class, thrown::class)`; message contains `"Lua 5.1"` and `"Lua 5.4"` | Flip §3.5 step 3's comparison to `> 0`. The fixture is runtime **older** than project, so the mutant finds no problem and `checkConfiguration()` throws nothing. |
| TC-06-08b | `-08` | `seedRuntime(path, LuaLanguageLevel.LUA55)`; project level `LUA51`; script as above | `checkConfiguration()` throws nothing | Change §3.5 step 3 to `!= 0`. The fixture is runtime **newer**, which the correct rule ignores and the mutant reports. TC-06-08a alone cannot catch this. |
| TC-06-10a | `-10` | Runtime healthy; `scriptName = "${scratch}/absent.lua"` (never created); working dir = `scratch` | `assertEquals(RuntimeConfigurationError::class, thrown::class)`; message contains `"absent.lua"` | Delete check 4. No other check fires for this fixture: the script name is non-empty (check 3 silent), the working directory exists (check 5 silent), and §3.6 yields the relative path `absent.lua` with no `..` (check 6 silent). So the mutant throws nothing at all and `assertFailsWith` itself fails. |
| TC-06-11a | `-11` | **Runtime healthy** (`seedRuntime(executableAt("lua").path)` registered and bound, as TC-06-10a and TC-06-12a); `workDir = scratch/a` (exists); script = `scratch/b/main.lua` (exists) | `assertEquals(RuntimeConfigurationWarning::class, thrown::class)`; message contains `"../b/main.lua"` | Weaken §3.6 step 4 to `relative == null`. Per the §3.6 probe this fixture yields `../b/main.lua`, never `null`, so the mutant reports nothing. |
| TC-06-11b | `-11` | **Runtime healthy** (as TC-06-11a); `workDir = scratch/a`; script = `scratch/a/sub/main.lua` | `checkConfiguration()` throws nothing | Change §3.6 step 4 to `relative != null`. The fixture yields `sub/main.lua`, which the mutant reports. TC-06-11a alone cannot catch an over-firing predicate. |
| TC-06-12a | `-12` | Runtime healthy; script = `scratch/main.lua` (exists); `workingDirectory = "${scratch}/gone"` (never created) | `assertEquals(RuntimeConfigurationWarning::class, thrown::class)`; `assertEquals(LuaTargetMessages.workingDirectoryMissing("${scratch}/gone"), thrown.message)` | (a) Severity `WARNING` → `FATAL` → exact-class assert fails. (b) Delete §3.2's "check 6 returns null when the working directory does not exist" short-circuit → the fixture then also produces a reachability warning, and `maxByOrNull`'s tie-break by declaration order still picks check 5 — so assert on the **message**, which the mutant leaves unchanged. Use mutation (a) as the primary; record (b) as covered by TC-06-12b. |
| TC-06-12b | `-12`, `-11` | Same fixture as TC-06-12a | `assertEquals(1, LuaTargetChecks.LOCAL_SCRIPT.mapNotNull { it.problem(spec) }.size)` — validate the *problem list*, not the throw | Delete the check-6 short-circuit → the list has 2 entries. Reachable: this fixture has a missing working directory **and** a script that is not under it. |
| TC-06-13a | `-13` | `LuaDebugRunner().checkDebugTargetReady(freePort, null)` — the `internal` seam of §2.7; `freePort` from a closed `ServerSocket(0)` so the port branch passes | `assertFailsWith<ExecutionException>`; `assertEquals("Failed to locate plugin directory", thrown.message)` | Change the plugin-directory guard from `?: throw ExecutionException(...)` to `?: return`. It compiles (the function returns `Unit`), and this fixture passes `null`, so the mutant throws nothing and `assertFailsWith` fails. **Do not** name "delete the `?: throw`" — that leaves a nullable receiver on the next line and does not compile. |
| TC-06-13b | `-13` | `checkDebugTargetReady(freePort, assetDir)` where `assetDir` is a real temp directory with **no** `debug.lua` child, obtained with `LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)` — the idiom at `LuaFileUtilTest.kt:31,48` | `assertFailsWith<ExecutionException>`; `assertEquals("Failed to locate debugger preloader", thrown.message)` | Delete the `pluginLuaPath.findChild(DEBUGGER_PRELOADER_FILE) ?: throw …` statement. Reachable: this fixture's directory resolves (so TC-06-13a's branch does not fire) and has no `debug.lua`, so the deleted line is the only thing that throws. |
| TC-06-13c | `-13` | `checkDebugTargetReady(freePort, assetDir)` where `assetDir` **does** contain a `debug.lua` file (write it, then `refresh`) | `checkDebugTargetReady(...)` returns normally — no exception | Invert the preloader guard to `if (pluginLuaPath.findChild(DEBUGGER_PRELOADER_FILE) != null) throw ExecutionException("Failed to locate debugger preloader")`. Reachable: this is the only fixture where the child is present, so TC-06-13a/b alone cannot catch an over-firing asset check. |
| TC-06-15a | `-15` | `ServerSocket(0)` held open on an ephemeral port `p` | `assertFalse(LuaDebugPortProbe.isAvailable(p))`; after `close()`, `assertTrue(LuaDebugPortProbe.isAvailable(p))` | Change §3.7's `catch (_: IOException) { false }` to `{ true }`. The held socket makes the bind throw `BindException` (an `IOException`), so the fixture takes exactly that branch. **Do not** use `reuseAddress` as the mutation — measured (§3.7) to make no difference on Linux, so that mutant stays green. |
| TC-06-15b | `-15` | `LuaDebugRunner().checkDebugTargetReady(p, assetDir)` — the `internal` two-argument seam of §2.7, so **no `ExecutionEnvironment` is constructed**. Port `p` held open by `ServerSocket(0)` as in TC-06-15a; `assetDir` = a temp directory containing a `debug.lua` child, so the asset branches pass and the port branch is the one under test | `assertFailsWith<ExecutionException>`; `assertTrue(thrown.message.orEmpty().contains(p.toString()))` | Drop `$port` from `LuaTargetMessages.debugPortInUse`. Reachable: the fixture asserts on the message text of the very exception this path throws. |
| TC-06-17a | `-17` | `config.envFilePaths = listOf("${scratch}/a.env")`; `LuaRunSettingsEditor(myFixture.project)` → `resetFrom(config)` → `applyTo(other)`, disposed in `finally` (the pattern at `TestLuaRunConfiguration.kt:156-175`) | `assertEquals(listOf("${scratch}/a.env"), other.envFilePaths)` | Delete the `envFilePaths` line from `applyEditorTo`. This is *exactly* the shipped defect `-17` names ("`LuaRunSettingsEditor` reads only `.data`") — red because `other.envFilePaths` is empty. |
| TC-06-17b | `-17` | Runtime healthy, script exists, `envFilePaths = listOf("${scratch}/absent.env")` | `assertEquals(RuntimeConfigurationException::class, thrown::class)` — **exactly** the bare class, so `Error` and `Warning` both fail | Change check 8's severity `ADVISORY` → `FATAL`. Reachable: this fixture's only problem is the missing env file. |
| TC-06-18a | `-18` | `BasePlatformTestCase`; `myFixture.configureByText("main.lua", "print(1)")`; `ConfigurationContext(psiFile)` | `producer.createConfigurationFromContext(context)` is non-null; `config.scriptName == psiFile.virtualFile.path`; `config.name == "main.lua"` | Make `setupConfigurationFromContext` return `false`. Red — the fixture is a plain `.lua` file on a non-Redis target, the exact positive case. |
| TC-06-18b | `-18` | `myFixture.configureByText("notes.txt", "hello")` | `assertNull(producer.createConfigurationFromContext(context))` | Delete the `targetFile.fileType.name != "Lua"` guard (the guard `LuaTestRunConfigurationProducer.kt:37` and `LuaRedisRunConfigurationProducer.kt:30` both carry). Red — the mutant fabricates a Lua config for a text file. |
| TC-06-18c | `-18` | `BasePlatformTestCase`; `setTarget(LuaPlatform.REDIS)` via `LuaProjectSettings.getInstance(project).state.setTarget(Target(REDIS, PlatformVersionRegistry.getVersions(REDIS).first()))` — verbatim the helper at `TestLuaRedisRunConfigurationProducer.kt:17-20`; `myFixture.configureByText("main.lua", "print(1)")` | `assertNull(LuaRunConfigurationProducer().createConfigurationFromContext(context))` | Delete §3.9.1 step 5 (`if (isRedisTarget(context)) return false`). Red — this fixture is a `.lua` file on a REDIS target, which every other guard in §3.9.1 passes, so step 5 is the only thing declining. **This is the test Risk 1.6 needs**: `TestLuaRedisRunConfigurationProducer` never instantiates `LuaRunConfigurationProducer` (`:28,43`) and stays green whether or not the new producer also offers here. |
| TC-06-18d | `-18` | `LuaRunConfigurationProducer().yieldsTo(x)` — the `internal` seam of §2.9 — for three values of `x`: a `LuaTestRunConfiguration`, a `LuaRedisRunConfiguration`, and a `LuaRunConfiguration`, each built with its own factory as at `TestLuaRunConfiguration.kt:159` | `assertTrue(yieldsTo(testConfig))`; `assertTrue(yieldsTo(redisConfig))`; `assertFalse(yieldsTo(runConfig))` | Change `yieldsTo` to `other is LuaRedisRunConfiguration` only. Reachable: the `LuaTestRunConfiguration` value is in this fixture's own three-way table, and its assertion flips to false. (The third value pins the converse — a `yieldsTo` that returned `true` unconditionally would make the producer lose to *every* other plugin's configuration.) |
| TC-06-20a | `-20` | Fixture of TC-06-02a | `assertNotNull((thrown as RuntimeConfigurationError).configurationQuickFix)` — the accessor is `getConfigurationQuickFix()` (`platform/ide-core/…/ConfigurationException.java:90-92`) | Drop the quick-fix argument from check 1's `LuaTargetProblem`. Red — the fixture's firing check is check 1, the only one that carries a fix. |
| TC-06-22a | `-22`, `-23` | Two configurations, both with an empty registry: `LuaRunConfiguration` and `LuaTestRunConfiguration` | (i) `assertEquals(runMessage, testMessage)`; (ii) `assertTrue(runMessage.startsWith("No usable Lua configured."))` | (a) Revert either configuration to its hand-rolled literal (`"No Lua runtime is configured…"` / `"Runtime is not defined"`) → assertion (i) fails. (b) Pass `(resolution as Unresolved).kindId` (`"runtime-capability"`) to `notConfiguredMessage` → assertion (ii) fails on `"No usable runtime-capability configured."`. Assertion (ii) exists **because** (i) alone is self-referential: both sides would move together if only the shared constant changed. |

**28 rows.** Count them before claiming a number: `git grep -c '^| TC-06-' docs/features/debug/06-debug-target-configuration/design.md`.

**Rows with no test, and why** — `-01` is structurally asserted by every row above (a delegating
override that did not exist would fail all of them); `-14` is unrepresentable in the widget and adds
no code; `-16` and `-19` are `Won't`; `-21` is asserted incidentally by TC-06-22a's literal. That is
the whole list: **every other `Must` row, `-13` included, has at least one test case.** `-13`'s
three (TC-06-13a/b/c) are reachable because §2.7 passes the asset directory in as an argument rather
than resolving it inside the gate — see the note under §2.7 for why the earlier "not unit-testable"
argument was a property of that draft's shape, not of `PluginManagerCore`.

What DR-03 still owns for `-13` is the *live* half: that the real plugin distribution actually ships
`lua/debug.lua`, which no unit test can assert because the test JVM's plugin path is the build
output, not the packaged plugin.

## 10. Alternatives Considered

### 10.1 Implement `CommonProgramRunConfigurationParameters` and call the platform's checks

`ProgramParametersUtil.checkWorkingDirectoryExist(configuration, project, module)`
(`platform/execution-impl/src/com/intellij/execution/util/ProgramParametersUtil.java:24-33`) and
`checkEnvFiles(configuration)` (`EnvFilesUtil.kt:23-33`) both take
`CommonProgramRunConfigurationParameters`
(`platform/execution-impl/src/com/intellij/execution/CommonProgramRunConfigurationParameters.java:24-45`),
which `LuaRunConfiguration` does not implement. **Rejected**: the interface demands five
getter/setter pairs including `getProgramParameters()`/`setProgramParameters()`, which collide
semantically with Lunar's `programArguments`, and implementing it changes what platform machinery
treats the configuration as (`RunConfigurationTypeUsagesCollector.java:133` and the fragment-based
editors both branch on it) for two checks that are five lines each. The two checks are reimplemented
at the platform's own tiers with the platform cited as the authority (§3.2 check 5, §4.1). The
narrower `EnvFilesOptions` — a **single** property — *is* implemented (§2.8), because
`-17` genuinely needs persisted state and there is no cost to it.

### 10.2 Validate `environmentFile` (`DEBUG-06-16`)

**Not done, and the property is not removed.** `EnvironmentVariablesData.configureCommandLine`
(`platform/execution-impl/…/EnvironmentVariablesData.java:122-127`) sets only the parent-env type
and `myEnvs`; `environmentFile` is never read. Validating a path nothing consumes manufactures a
check for a feature that does not exist. The `StoredProperty` at `run/LuaRunConfiguration.kt:95-99`
stays because deleting it would drop the field from existing `.idea/runConfigurations/*.xml`.

### 10.3 Execute `.sh`/`.bat` environment scripts at launch

The platform's `configureEnvsFromFiles(configuration, parse = true)`
(`platform/execution-impl/src/com/intellij/execution/util/EnvFilesUtil.kt:37-71`) runs `.sh`/`.bat`
env files through `ShellEnvironmentReader`. **Rejected on two grounds.** It is
`@ApiStatus.Experimental` (`:36`); and at `:45-46` it does
`ProgressManager.getGlobalProgressIndicator()` followed by `indicator.withPushPop { … }`, where
`withPushPop` is an extension on a **non-null** `ProgressIndicator`
(`platform/core-impl/src/com/intellij/openapi/progress/ProgressIndicatorEx.kt:12`) and the getter is
`@Nullable` — a call with no global indicator NPEs. Lunar parses declarative env files with the
public `parseEnvFile` (§4.1) and skips script env files. Tracked as TBD-1.

### 10.4 Surface the mobdebug asset check in the editor dialog (`DEBUG-06-13`)

**Rejected.** A missing `lua/debug.lua` inside the plugin distribution is a plugin-integrity failure
that no edit to the run configuration can repair, so a red banner in the Run/Debug Configurations
dialog would point the user at a field that is not the problem. It is also not a *Run*-executor
concern at all — `LuaRunConfiguration` only reads the assets under `DefaultDebugExecutor`
(`run/LuaRunConfiguration.kt:320-326`), while `checkConfiguration()` receives no executor. Hoisting
it into the pre-spawn gate (§2.7) satisfies this feature's own scope statement — "the **edit-time /
pre-launch** half" — by moving it from *after* the interpreter is spawned to *before*.

### 10.5 Probe the debug port inside `checkConfiguration()`

**Rejected.** It would open a loopback socket after every character typed in every field of the
dialog, and `DEBUG-06-15` asks for a pre-launch verdict, not an edit-time one. Measured cost is
~22 µs per probe (§3.7) — cheap, and still the wrong place.

### 10.6 A `CachedValuesManager`-backed cache instead of `LuaPathFacts`

**Rejected.** `CachedValuesManager` keys on PSI/VFS modification trackers; a run configuration's
runtime path is frequently *outside* the project content roots and generates no VFS events there.
`LuaToolHealthMonitor` (`toolchain/health/LuaToolHealthMonitor.kt:43-79`) is the VFS-driven
invalidation path and it watches only *inventory* binaries, not ad-hoc paths. A short TTL is the
honest bound for a value with no invalidation signal.

## 11. Open Questions

_None — every deferral is a tracked TBD or DR task in [risks-and-gaps.md](risks-and-gaps.md)._
