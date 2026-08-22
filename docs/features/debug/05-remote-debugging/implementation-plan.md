---
id: "DEBUG-05-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "DEBUG-05"
folders:
  - "[[features/debug/05-remote-debugging/requirements|requirements]]"
---

# DEBUG-05: Implementation Plan

Sequences [design.md](design.md). Every task names the file it creates or edits and the design
section it realizes; no task requires a design decision.

## Preconditions

| Precondition | State | Blocks |
| :-- | :-- | :-- |
| [[DEBUG-06]] Phases 1–2 merged (`LuaTargetSpec`, `LuaTargetSeverity`, `LuaTargetProblem`, `LuaTargetCheck`, `LuaTargetValidator`, `LuaTargetChecks`, `LuaTargetMessages` — DEBUG-06 design §2.1–§2.5) | `planned`, not implemented | **Phase 3 only.** Phases 1, 2, 4 and 5 compile against nothing DEBUG-06 introduces. |
| [[BUG-456]] not separately fixed | `todo` | Nothing. **Phase 2** is its fix — `openListener(target)` binds `target.bindAddress` (§3.1) and deletes the wildcard bind at `run/LuaDebuggerController.kt:114-117`. If BUG-456 lands first as its own one-argument change, `openListener` preserves the fix and the plan is unchanged. |
| [[BUG-450]] | `todo` | Nothing, but it edits the same lines as task 1.4 (`run/LuaRemoteStack.kt:41-47`). Whichever lands second keeps the resolution lazy — see risks-and-gaps Risk 1.3. |

## Phases

### Phase 1: Path translation as a type, and the frame-path defect [Must]

- **Goal**: introduce the pure translation layer and use it to fix the one defect that exists in
  shipped **local** debugging today and can be *proved* fixed without a listener seam — truncated
  `short_src` frame paths (`DEBUG-05-09`). Nothing about remote attach is touched. This phase is
  independently shippable and is the reason `-09` is not a separate bug report (design §10.6).
  The other live local defect, the wildcard-bound listener ([[BUG-456]], `DEBUG-05-12`), is
  **Phase 2**, not this phase — see the note under the exit criteria.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.run.LuaPathMapper` in `run/LuaPathMapper.kt` — realizes
        design §2.1 and the `toWire`/`toLocalPath`/`baseDirArgument`/`isIdentity` algorithms of
        §3.2 — **including the `remoteRootPrefix` (`removeSuffix("/")`) guard**, without which a
        `/` root emits `BASEDIR //` and no breakpoint binds (TC-05-03d, §0 Probe O). Delegate all
        prefix arithmetic to `com.intellij.util.PathMappingSettings.PathMapping`.
  - [ ] Create `net.internetisalie.lunar.run.LuaFrameResolver` in `run/LuaFrameResolver.kt` —
        realizes design §2.2 and the five-step `resolve` of §3.8, including the leading-`=` guard
        and negative caching.
  - [ ] Edit `run/LuaPosition.kt` — add the `createRemotePosition(XSourcePosition, LuaPathMapper)`
        overload and re-express the existing `File?` overload over
        `LuaPathMapper.identity(workingDir)` (§3.8). Change `localPosition()` to
        `localPosition(mapper: LuaPathMapper)`; update its single production call site,
        `run/LuaDebuggerController.kt:326`, to
        **`pos.localPosition(LuaPathMapper.identity(workingDirectory()))`** —
        `workingDirectory(): File` already exists at `run/LuaDebuggerController.kt:72`, and Phase 2
        later replaces this expression with `target.mapper`. **Pass the mapper, not
        `LuaPathMapper(null, null)`.** With both roots null `toLocalPath` returns the wire path
        unchanged, [[BUG-463]] stays live in the pause path, and **no Phase-1 row would go red**:
        TC-05-08c pins `LuaPosition.localPosition` itself, not this call site.
  - [ ] Edit `run/LuaRemoteStack.kt` — replace the `virtualFiles: MutableMap<String, VirtualFile?>`
        field (`:14`) and the parameter threaded through `LuaRemoteStackEntry` (`:39`, `:51`) and
        `LuaRemoteStackFrame` (`:69`, `:85`) with a single `LuaFrameResolver`; **delete** the eager
        `init` block at `:41-47`; make `virtualFile` a `by lazy` keyed on `file` (index 1) rather
        than `path` (index 6). Realizes §3.8. **The exact signatures, verbatim from design §3.8:**
        `class LuaRemoteStack(stack: LuaTable?, private val resolver: LuaFrameResolver =
        LuaFrameResolver.identity(null))` — a **defaulted** second parameter, which is what keeps
        `TestLuaExecutionStack.kt:14`'s `LuaRemoteStack(null)` and
        `TestLuaRemoteStackFrames.kt:34,51`'s `LuaRemoteStack.create(myFixture.file)` compiling
        unmodified. Both existing `create` overloads keep their signatures and pass no resolver;
        add a third, `create(project, text, resolver)`. Under the default both roots are null, so
        `resolve` is a bare `findFileByPath(wireFile)` — design §3.8 records why that leaves all
        three tests in `TestLuaRemoteStackFrames` green (none asserts on `virtualFile`).
  - [ ] Edit `run/LuaDebuggerController.kt` — `variables()` (`:290-295`) constructs its
        `LuaRemoteStack` with `LuaFrameResolver.identity(workingDirectory())`; `addBreakPoint`
        (`:220`), `removeBreakPoint` (`:230`) and `run/LuaDebugProcess.kt:82` keep passing the
        working directory through the retained `File?` overload. **No behaviour change intended in
        this task** — it is the mechanical rewire.
- **Exit criteria**: TC-05-03a, TC-05-03b, **TC-05-03d**, TC-05-07a, TC-05-07b, TC-05-08a, TC-05-08b, TC-05-08c,
  TC-05-09a, TC-05-09b green; `TestLuaPosition`, `TestLuaRemoteStackFrames`,
  `TestLuaExecutionStack` and `TestLuaDebugHarness` still green **unmodified** — which the defaulted
  `LuaRemoteStack` resolver parameter in task 4 is what makes possible; full suite green.
  TC-05-08c is [[BUG-463]]'s regression row and its fixture must be nested, not root-level.
- **Why the loopback fix is not here.** The shipped bind is inline inside `connect()`
  (`run/LuaDebuggerController.kt:114-117`), and `connect()` blocks on `accept()`; no test in the
  repo constructs a `LuaDebuggerController` and calls it (`TestLuaDebuggerEvaluator.kt:50` builds
  one only to hand it to `LuaDebuggerEvaluator`, and `LuaDebugHarness.kt:30` opens its **own**
  `ServerSocket(8172)` rather than the controller's). Changing that line in Phase 1 would therefore
  ship a behaviour change with **no** test that can go red — and Phase 2 deletes the line outright,
  so it would never acquire one. `openListener(target)` (§3.1) *is* the fix, TC-05-12a is its
  proof, and both land together in Phase 2.

### Phase 2: One target per session, differing roots, and a cancellable wait [Must]

- **Goal**: give a session an explicit `(bindAddress, port, deadline, mapper)` description, make
  breakpoints and frames cross a root boundary, make the listen window unbounded and cancellable,
  and bind the listener to loopback. Delivers `DEBUG-05-03`, `-06`, `-07`, `-08` at the controller
  level and closes [[BUG-456]] (`DEBUG-05-12`); the UI that reaches them arrives in Phase 3.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.run.LuaDebugTarget` in `run/LuaDebugTarget.kt` — realizes
        design §2.3 including `of(LuaRunConfiguration)`, `fallback()` and `debuggeeHost()`.
        (`of(LuaAttachRunConfiguration)` is added in Phase 3.)
  - [ ] Edit `run/LuaDebuggerController.kt` — replace the fields `serverPort` (`:56`), `baseDir`
        (`:69`) and `workingDir` (`:70`) and the `init` derivation (`:77-90`) with a single
        `private val target: LuaDebugTarget`; add `pathMapper()` and `port()`; change
        `workingDirectory()` to return `File?`. Realizes §2.4.
  - [ ] Edit `run/LuaDebuggerController.kt` — split `connect()` (`:111-131`) into `connect()`,
        `internal fun openListener(target)` (§3.1) and
        `internal suspend fun awaitClient(server, deadlineMs)` (§3.3), plus
        `private fun rejectForeignPeer(clientSocket)` (§3.4). Add
        `const val ACCEPT_POLL_MS: Int = 500` **and `const val LISTEN_BACKLOG: Int = 1`** beside
        `CONNECT_TIMEOUT_MS` (`:348`). `openListener` is verbatim design §3.1 —
        `ServerSocket(target.port, LISTEN_BACKLOG, target.bindAddress)` — so the wildcard bind at
        `:114-117` disappears with it: **this task is [[BUG-456]]'s fix**, and `internal` is what
        gives TC-05-12a the seam Phase 1 had none of. **Do not use `runInterruptible`** — design
        §10.4 records the measurement that rules it out.
  - [ ] Edit `run/LuaDebuggerController.kt` — `setBaseDir()` (`:199-201`) sends
        `target.mapper.baseDirArgument()` and returns without sending when it is `null`;
        `addBreakPoint`/`removeBreakPoint`/`runToCursor` and `variables()` use `target.mapper`;
        `DebugObserver.onPause` (`:326`) uses `pos.localPosition(target.mapper)`.
  - [ ] Edit `run/LuaDebugProcess.kt:78-84` — `runToPosition` uses
        `LuaPosition.createRemotePosition(position, controller.pathMapper())` in place of
        `controller.workingDirectory()` (`:82`). **Unpinned by design**: no unit row covers this line.
        `LuaDebugProcess` needs a live `XDebugSession` + `ExecutionResult` to construct, so a mutation
        here (leaving the `File?` overload in place) turns nothing red — under differing roots it
        silently sends an un-translated Run-to-Cursor. **HV-08** is its only gate; the mechanical
        rewire is the whole task.
  - [ ] Create `net.internetisalie.lunar.run.LuaRootMismatch` in `run/LuaRootMismatch.kt` —
        realizes the four-step `detect` of design §3.5.
  - [ ] Edit `run/LuaDebugConnection.kt:93-100` — `STACK` gains `maxArgs = 1`. **This is a
        declaration-consistency edit with no behavioural effect, and it deliberately has no exit
        criterion.** `minArgs`/`maxArgs` are never read in production — the only repo-wide readers are
        `TestLuaDebugConnectionParsing.kt:64-65`, asserting `SETB`'s declared pair — so the
        `-- {maxlevel=1}` argument of §4.3 is sent correctly with or without it and **no mutation of
        this line can turn any row red**. Do not write a test that merely re-asserts the constant;
        that would be the decoration §9's mutation column exists to prevent. No other change to the
        command model.
  - [ ] Edit `run/LuaDebuggerController.kt` — add `private suspend fun handshake()` (§3.7) calling
        `setBaseDir()` then `detectRootMismatch()`; `connect()` calls it in place of the bare
        `setBaseDir()` at `:130`. `clearRemoteBreakpoints()` and `redirectOutput()` are added to
        `handshake()` in Phase 4.
  - [ ] Edit `src/test/kotlin/net/internetisalie/lunar/run/LuaDebugHarness.kt` — add
        `LuaHarnessSpec` and the two-overload `startLuaDebugHarness` of design §2.9;
        `.directory(spec.workingDirectory)` on the `ProcessBuilder` and a loopback-bound,
        caller-chosen port on the `ServerSocket`. **The child must be told the port**: the existing
        `.apply { … }` block (`:35-41`) sets no `MOBDEBUG_PORT` and the preloader falls back to
        `8172` (`src/main/lua/lunar/debug.lua:13`), so add
        `environment()["MOBDEBUG_PORT"] = spec.port.toString()` and then
        `environment().putAll(spec.environment)` — without them `spec.port` binds a listener the
        debuggee never dials and TC-05-07d cannot connect (design §2.9).
- **Exit criteria**: TC-05-03c, TC-05-06a, TC-05-06b, TC-05-07c, TC-05-07d, **TC-05-12a** green; `TestLuaDebugHarness`'s
  existing `testBreakpointAndExec` green **unmodified** (it calls the retained one-line overload);
  full suite green.

### Phase 3: The attach configuration [Must]

- **Goal**: a Run/Debug configuration that listens for a Lua process the IDE did not start.
  Delivers `DEBUG-05-04`, `-05`, and the capability-B half of `-12`.
- **Blocked by**: DEBUG-06 Phases 1–2 (see Preconditions).
- **Tasks**:
  - [ ] Create `run/attach/LuaAttachRunConfiguration.kt` containing
        `LuaAttachRunConfigurationType`, `LuaAttachRunConfigurationFactory`,
        `LuaAttachRunConfigurationOptions`, `LuaAttachRunConfiguration` and
        `LuaAttachSettingsEditor` — realizes design §2.5 and §2.7. The configuration implements
        `RunConfigurationWithSuppressedDefaultRunAction` and `RemoteRunProfile`. The editor supplies
        all three `SettingsEditor` overrides — `createEditor(): JComponent`,
        `resetEditorFrom(runConfiguration: LuaAttachRunConfiguration)` and
        `applyEditorTo(runConfiguration: LuaAttachRunConfiguration)` — written out in full in §2.7.
        **Every labelled row is built with `net.internetisalie.lunar.ui.addMnemonicLabeledComponent`
        (`src/main/kotlin/net/internetisalie/lunar/ui/LuaFormBuilders.kt:25`) and every checkbox with
        `withMnemonic()` (`:39`)** — never `FormBuilder`'s own `addLabeledComponent(String, …)`,
        which leaks `U+001B` and sets no mnemonic (design §2.7; contract
        `docs/engineering-contract.md:151`). The timeout row's unit and `0` semantics go in
        `FormBuilder.addTooltip("In seconds. 0 waits until you cancel.")`
        (`platform/platform-api/src/com/intellij/util/ui/FormBuilder.java:125`; in-repo
        `redis/run/LuaRedisRunConfiguration.kt:348`) — **`FormBuilder` has no `comment(...)`**.
  - [ ] Create `net.internetisalie.lunar.run.attach.LuaAttachState` in the same file — realizes
        design §2.6 (`DefaultDebugProcessHandler` + `TextConsoleBuilderFactory` +
        `DefaultExecutionResult`).
  - [ ] Edit `run/LuaDebuggerController.kt` **and** `run/LuaDebugProcess.kt` — realize the
        disconnect bridge of design §2.4 verbatim. In the controller: add
        `private var disconnectListener: (() -> Unit)? = null` and
        `fun onDisconnect(listener: () -> Unit) { disconnectListener = listener }`, and append
        `disconnectListener?.invoke()` after the existing `close()` in
        `DebugObserver.onDisconnected` (`run/LuaDebuggerController.kt:341-344`; `DebugObserver` is
        already a public `inner class` at `:297`, so TC-05-05c can call
        `controller.DebugObserver().onDisconnected()`). The `if (target.autoRestart) {
        restartListener(); return }` line that design §3.9 puts above it is **Phase 5's**, not this
        task's. In `LuaDebugProcess`: immediately before the `sessionScope.launch` at
        `run/LuaDebugProcess.kt:110`, inside `sessionInitialized` (`:96-134`), install
        `controller.onDisconnect { if (!myClosing) executionResult.processHandler?.destroyProcess() }`
        — `myClosing` is the flag already declared at `:50`. **This is not optional and it is not
        cosmetic**: an attach session owns no OS process, so the `processTerminated` listener at
        `:100-108` never fires and, without the bridge, a debuggee that disconnects leaves a live
        session over a dead socket (§2.4). TC-05-05c is its proof, and the named mutation is
        deleting the `disconnectListener?.invoke()` line.
  - [ ] Edit `run/LuaDebugRunner.kt:52-57` — `canRun` accepts
        `LuaRunConfiguration || LuaAttachRunConfiguration`. `doExecute` is **unchanged**
        (design §10.5 records why).
  - [ ] Edit `run/LuaDebugTarget.kt` — add `of(configuration: LuaAttachRunConfiguration)` and
        `resolveBindAddress(host)` (design §2.3, §3.1).
  - [ ] Edit `run/LuaDebuggerController.kt` — the `init` `when` gains the
        `is LuaAttachRunConfiguration` arm (§2.4).
  - [ ] Edit `run/LuaRunConfiguration.kt` — add `const val ENV_MOBDEBUG_HOST = "MOBDEBUG_HOST"`
        in the existing `companion object` (`:356-363`) beside `ENV_MOBDEBUG_PORT` (`:362`) — a
        companion member, which is what makes TC-05-04a's `LuaRunConfiguration.debuggerEnvironment(…)`
        resolve — and extract
        `internal fun debuggerEnvironment(pluginLuaPath: String, preloaderPath: String, target: LuaDebugTarget): Map<String, String>`
        from `startProcess`'s `:321-336`, adding the `MOBDEBUG_HOST` entry. Realizes §2.6.
        **Three arguments, both paths `String`** — the two `?: throw ExecutionException(...)`
        resolutions at `:322-327` stay in `startProcess` and are passed in, so the helper needs no
        `!!` and no VFS (contract §1). The call site becomes
        `val target = LuaDebugTarget.of(this@LuaRunConfiguration)` followed by
        `commandLine.withEnvironment(debuggerEnvironment(pluginLuaPath.path, debuggerPreloaderFile.path, target))`,
        replacing the four `withEnvironment` calls at `:329-335`. **`target` is derived right there,
        from the qualified receiver** — `startProcess` is the body of the anonymous
        `CommandLineState` inside `getState` (`:292-293`), so `this@LuaRunConfiguration` is in scope,
        as `:315`'s `effectiveWorkDirectory()` and `:335`'s `debugPort` already demonstrate
        (design §2.6).
  - [ ] Edit `run/validation/LuaTargetSpec.kt` (DEBUG-06) — add the third factory
        `of(configuration: LuaAttachRunConfiguration)` per design §2.8.
  - [ ] Edit `run/validation/LuaTargetChecks.kt` (DEBUG-06) — add
        `fun attach(configuration: LuaAttachRunConfiguration): List<LuaTargetCheck>` returning
        `listOf(WORKDIR_MISSING, remoteRootUnset(configuration), bindHostUnresolvable(configuration))`.
        **Do not add checks 1 or 3** — design §2.8 and DEBUG-06 design §2.10 state why.
  - [ ] Edit `run/validation/LuaTargetMessages.kt` (DEBUG-06) — add `remoteRootUnset()` and
        `bindHostUnresolvable(host)` with the exact strings in design §2.8.
  - [ ] Edit `src/main/resources/net/internetisalie/lunar/LuaBundle.properties` — add the nine
        `debug.attach.*` keys of design §2.7 under the existing `# debugging` section (`:109-110`),
        with the exact values in that table — **including the `&` mnemonic marker**, which is part of
        the value and needs no escaping in a `.properties` file. `LuaAttachSettingsEditor` and
        `LuaAttachRunConfigurationType` read every label through `LuaBundle.message(...)`; **no
        control label is a literal.** Prior art: `settings/LuaApplicationSettingsPanel.kt:37-39`.
        The existing `LuaBundleCasingTest`
        (`src/test/kotlin/net/internetisalie/lunar/LuaBundleCasingTest.kt:42-55`) picks the new keys up
        automatically — **add no exclusion for them**; design §2.7 shows all nine pass it as written.
  - [ ] Edit `src/test/kotlin/net/internetisalie/lunar/ui/RunConfigurationEditorTextTest.kt` — TC-05-07f.
        Add `"Lua Remote" to register(LuaAttachSettingsEditor(project))` to the hard-coded `editors()`
        list (`:140-146`), raise `AUDITED_ROW_COUNT` 27 → **32** (`:159`), and widen
        `test every checkbox carries a mnemonic` (`:123-127`) from `checkBoxesOf(redisEditor())` to
        `editors().flatMap { (_, editor) -> checkBoxesOf(editor) }`. **This edit is the mnemonic gate**
        — that file enumerates four editors by name, so a fifth is invisible to every assertion in it
        and the suite stays green on an editor with no colons and no mnemonics.
  - [ ] Edit `src/main/resources/META-INF/plugin.xml` — insert the single `<configurationType>`
        of design §7 after `:615-616`.
- **Exit criteria**: TC-05-04a, TC-05-05a, TC-05-05b, **TC-05-05c**, TC-05-07e, **TC-05-07f**, TC-05-12b,
  TC-05-12c green; the
  **Lua Remote (Mobdebug)** template appears in *Run → Edit Configurations → +* with no Run action
  (HV-01), and an attached session **ends when the debuggee disconnects** (**HV-06** — the
  `LuaDebugProcess` half of the §2.4 bridge, which no unit row reaches); full suite green.

### Phase 4: Console output and stale breakpoints [Should]

- **Goal**: an attached session's `print` reaches the IDE console, and a reconnecting debuggee does
  not carry breakpoints from a previous session. Delivers `DEBUG-05-10` and `-11`.
- **Tasks**:
  - [ ] Edit `run/LuaDebugConnection.kt:52-156` — add the `OUTPUT` command kind of design §3.6a.
  - [ ] Edit `run/LuaDebugConnection.kt:179-191` — add `fun onOutput(stream: String, text: String)`
        to `LuaDebugObserver`; implement it in `LuaDebuggerController.DebugObserver` (`:297`) and
        with an empty body in the test observer at `TestLuaDebugHarness.kt:33-47`.
  - [ ] Edit `run/LuaDebugConnection.kt:266-278` — insert the `Output` branch of design §3.6b as
        the **first** statement after the status/data split, before the Case A test at `:277`.
        Realizes §4.1's parse. **The ordering is the requirement** — Probe K (design §0) is the
        measurement that makes it so.
  - [ ] Edit `run/LuaDebuggerController.kt` — add `private suspend fun redirectOutput()` (§3.6d)
        and `private suspend fun clearRemoteBreakpoints()` (§3.7 step 2); call both from
        `handshake()` in the §3.7 order (`setBaseDir` → `clearRemoteBreakpoints` → `redirectOutput`
        → `detectRootMismatch`). The four constants — `OUTPUT_STREAM`, `OUTPUT_MODE_REDIRECT`,
        `DELB_ALL_FILES`, `DELB_ALL_LINES` — go in the **existing `companion object`** (`:347-351`),
        beside `CONNECT_TIMEOUT_MS` (`:348`), declared **`internal const val`, not `private`**
        (design §3.7). The visibility is load-bearing, not stylistic: TC-05-10c and TC-05-11a must
        send *these* values rather than retyping them, or mutating a constant leaves both harness
        rows green.
  - [ ] Append **TC-05-11b** to
        `src/test/kotlin/net/internetisalie/lunar/run/TestLuaDebuggerListener.kt` — the scripted
        fake debuggee of design §9, asserting `connect()` puts exactly
        `["BASEDIR /srv/app/", "DELB * 0", "OUTPUT stdout r"]` on the wire, **in that order**.
        This is the only check that covers the task above; the harness rows cannot reach it, because
        `LuaDebugHarness.kt:52` builds its own `LuaDebugConnection` and never constructs a
        `LuaDebuggerController`. Socket mechanics measured in design §0, Probe P.
- **Exit criteria**: TC-05-10a, TC-05-10b, TC-05-10c, TC-05-11a, **TC-05-11b** green; full suite
  green. **TC-05-11b is the one that can fail if the fourth task is skipped** — without it this
  phase's criteria are all satisfiable with `redirectOutput()`/`clearRemoteBreakpoints()`
  unimplemented, which is exactly how `DEBUG-05-11` would ship unproven.

### Phase 5: Keep listening after the debuggee disconnects [Could]

- **Goal**: successive runs of the debuggee reattach without restarting the IDE session. Delivers
  `DEBUG-05-13`.
- **Tasks**:
  - [ ] Edit `run/LuaDebuggerController.kt:341-344` — `DebugObserver.onDisconnected` gains the
        `if (target.autoRestart) { restartListener(); return }` line **above** the
        `close(); disconnectListener?.invoke()` body Phase 3 left there (design §3.9 step 1;
        the §2.4 callback is the fall-through and is not removed), delegating to a new
        `internal fun restartListener()` (§3.9 step 2) which closes **only** the `LuaDebugConnection`,
        prints the "still listening" line and relaunches `connect()` on `scope`. `internal`, not
        `private`, so TC-05-13a can reach it.
  - [ ] Edit `run/LuaDebuggerController.kt` — add
        `internal fun listener(): ServerSocket = serverSocket ?: openListener(target).also { serverSocket = it }`
        and make it `connect()`'s first statement (§3.9 step 3). The `serverSocket ?:` guard is the
        whole point — TC-05-13b asserts `assertSame` across two calls.
- **Exit criteria**: TC-05-13a and TC-05-13b green; full suite green.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
| :-- | :-: | :-- |
| `DEBUG-05-01` | M | Phase 2 (listener shape preserved through the `LuaDebugTarget` refactor) |
| `DEBUG-05-02` | M | Phase 2 (`LuaDebugTarget.of` carries `debugPort`); Phase 3 for the attach editor's spinner |
| `DEBUG-05-03` | M | Phase 1 (`baseDirArgument`), Phase 2 (`setBaseDir` sends it) |
| `DEBUG-05-04` | M | Phase 3 |
| `DEBUG-05-05` | M | Phase 3 |
| `DEBUG-05-06` | M | Phase 2 (mechanism); Phase 3 (the field that reaches it) |
| `DEBUG-05-07` | M | Phase 1 (`toWire`), Phase 2 (wiring + `LuaRootMismatch`) |
| `DEBUG-05-08` | M | Phase 1 (`LuaFrameResolver`), Phase 2 (non-identity mapper) |
| `DEBUG-05-09` | S | **Phase 1** |
| `DEBUG-05-10` | S | Phase 4 (handler); Phase 3 supplies the console |
| `DEBUG-05-11` | S | Phase 4 |
| `DEBUG-05-12` | S | **Phase 2** (loopback default via `openListener`, [[BUG-456]]; origin check); Phase 3 (explicit bind host) |
| `DEBUG-05-13` | C | Phase 5 |
| `DEBUG-05-14` | S | No phase — a statement about REDIS-02, restated in design §1.2 as *left alone* |
| `DEBUG-05-15` | C | No phase — **Won't** |
| `DEBUG-05-16` | W | No phase — **Won't** |

## Verification Tasks

- [ ] Create `src/test/kotlin/net/internetisalie/lunar/run/TestLuaPathMapper.kt` — TC-05-03a/b/d,
      TC-05-07a/b. Built in **Phase 1**.
- [ ] Create `src/test/kotlin/net/internetisalie/lunar/run/TestLuaFrameResolver.kt` — TC-05-08a/b/c,
      TC-05-09a/b. Refresh real files into the VFS with the
      `LuaFileUtilTest.kt:30-33` idiom before asserting.
- [ ] Create `src/test/kotlin/net/internetisalie/lunar/run/TestLuaDebuggerListener.kt`, extending
      **`BaseDocumentTest`** (a JUnit5 plain class, so **every method needs `@Test`**) — the
      controller-level rows need `myFixture.project` for their `fakeSession` and for constructing an
      attach configuration — TC-05-06a/b, TC-05-12a, TC-05-07c in **Phase 2** (the phase that creates `LuaDebugTarget` and
      `openListener`, which every one of those fixtures calls); **TC-05-05c appended in Phase 3**
      (the phase that adds `onDisconnect`); **TC-05-11b appended in Phase 4** (the phase that adds
      `clearRemoteBreakpoints()`/`redirectOutput()`, whose scripted fake debuggee reuses this file's
      copied `fakeSession` and `Proxy` console); TC-05-13a/b appended in Phase 5. `fakeSession` is
      `private` in `TestLuaDebuggerEvaluator.kt:76` and therefore **not callable from another
      file** — copy the anonymous `XDebugSession` into this file and vary only `getRunProfile()`.
      TC-05-13a installs a `ConsoleView`
      `java.lang.reflect.Proxy` (`redis/debug/TestLuaLdbController.kt:97-101`) before touching
      `restartListener()`, and cancels the controller's scope in a `finally`.
- [ ] Create `src/test/kotlin/net/internetisalie/lunar/run/TestLuaDebugTarget.kt` — TC-05-03c. Built
      in **Phase 2**, with `LuaRunConfiguration(myFixture.project,
      LuaRunConfigurationFactory(LuaRunConfigurationType()), "cfg")` per `TestLuaRunConfiguration.kt:158-159`.
- [ ] Create `src/test/kotlin/net/internetisalie/lunar/run/TestLuaDebugOutputFrames.kt` — TC-05-10a/b.
- [ ] Create `src/test/kotlin/net/internetisalie/lunar/run/attach/TestLuaAttachRunConfiguration.kt` —
      TC-05-04a, TC-05-05a/b, TC-05-07e, TC-05-12b/c. It extends **`BaseDocumentTest`**
      (`TestLuaRunConfiguration.kt:26`), a JUnit5 plain class, so **every method needs `@Test`**.
- [ ] Extend `src/test/kotlin/net/internetisalie/lunar/run/TestLuaDebugHarness.kt` —
      `testBreakpointBindsAcrossDifferingRoots` (TC-05-07d), `testOutputRedirectReachesObserver`
      (TC-05-10c), `testWildcardDelbClearsBreakpoints` (TC-05-11a). All three launch the child from
      a directory the harness creates, using `LuaHarnessSpec`. The last two **must build their
      `DebugCommand` arguments from `LuaDebuggerController.OUTPUT_STREAM` /
      `OUTPUT_MODE_REDIRECT` / `DELB_ALL_FILES` / `DELB_ALL_LINES`, never from literals** — with
      literals neither row can be turned red by any change to Lunar (design §9).
- [ ] Run the **full** suite through `tooling/gce-builder/gce-builder.sh run test` at the end of
      every phase. A green `test --tests *Lua*Mapper*` proves nothing about the suite
      (`.agents/AGENTS.md`, *Isolated `--tests` masks full suite*).
- [ ] Run `tooling/gce-builder/gce-builder.sh run ktlintCheck` (check only — never pair it with
      `ktlintFormat` in one invocation, [[BUG-445]]).
- [ ] **Author `human-verification-checklists.md`** (it does not exist yet — risks-and-gaps Gap 2.4,
      `DEBUG-05-00-DR-04`) and run one `verify-in-ide` pass after Phase 3 and one after Phase 4,
      covering at minimum: **HV-01** the *Lua Remote (Mobdebug)* template appears under
      *Run → Edit Configurations → +* and offers **Debug** but **no Run** action; **HV-02** the
      attach editor renders **five labelled rows** in the left label column plus **two checkbox
      rows**, sentence case, every leading label ending in a colon, no label carrying a
      parenthetical, and — with the dialog focused and **Alt** held — **all seven** rows showing an
      underlined mnemonic letter (`H D T L R O K`), each Alt+letter moving focus to that row's
      control (`docs/engineering-contract.md:151`; the unit gate TC-05-07f asserts the *model* —
      `displayedMnemonic`, `labelFor` — but a painted underline and a working focus traversal are
      look-and-feel outcomes only a real window shows, as `RunConfigurationEditorTextTest`'s own
      header says at `src/test/kotlin/net/internetisalie/lunar/ui/RunConfigurationEditorTextTest.kt:24-26`).
      Check in the same pass that neither `Alt+S` (*Store as project file*) nor `Alt+U` (*Allow
      multiple instances*) is stolen by a row; **HV-03** an attached debuggee's `print("x")` reaches the console as
      `"x"` (quoted — risks-and-gaps Risk 1.6), so a reviewer does not file it as a bug; **HV-04** a
      mismatched remote root produces the §3.5 console message instead of a silently dead
      breakpoint; **HV-05** with `listenTimeoutSeconds = 0` and no debuggee, the *Connecting to
      debugger* background-progress item shows a **Cancel** control, and pressing it ends the
      listen within a second and terminates the session — the user-facing half of `DEBUG-05-06`
      (**M**), which TC-05-06a cannot reach because it cancels the `Job` programmatically
      (design §5.2 step 4); **HV-06** an attached session whose debuggee exits (or is killed) ends
      in the IDE — the *Stop* action greys out and the Debug tool window reports the process
      terminated — instead of leaving a live session over a dead socket; **HV-07** in a project whose
      Lua sources sit in a **nested** directory (never the filesystem root), pause a *launched*
      session at a breakpoint and then **Step Over**: the editor follows the step to the next line of
      the same file rather than the Frames pane showing a frame with no source — the end-to-end half
      of [[BUG-463]], which TC-05-08c pins only at `LuaPosition.localPosition` and not at the
      `run/LuaDebuggerController.kt:326` call site that reaches it; **HV-08** in an *attach* session
      with `localRoot != remoteRoot`, **Run to Cursor** on a line in a nested file stops on that line
      — the only gate on the `run/LuaDebugProcess.kt:78-84` rewire, which no unit row can reach
      because `LuaDebugProcess` needs a live `XDebugSession` and `ExecutionResult`. HV-06 is the
      `LuaDebugProcess` half of the design §2.4 disconnect bridge
      (`controller.onDisconnect { if (!myClosing) executionResult.processHandler?.destroyProcess() }`):
      TC-05-05c proves only the **controller** half (that the callback fires), and the installation
      side needs a real `XDebugSession` + `ExecutionResult`, so it has no unit row. Note the
      asymmetry HV-06 closes — `DEBUG-05-13` (a **Could**) has two dedicated tests, while this
      component of a **Must** requirement (`-05`) otherwise has none.
      `LuaAttachSettingsEditor` is a **new** visible surface and
      `docs/engineering-contract.md:159-163` makes the screenshot pass the gate for one.

## Task Summary

| Phase | Status | Priority |
| :-- | :-- | :-- |
| Phase 1: Path translation + the `-09` frame-path defect | todo | Must |
| Phase 2: `LuaDebugTarget`, differing roots, cancellable wait, loopback bind (`-12`, [[BUG-456]]) | todo | Must |
| Phase 3: The attach configuration | todo | Must |
| Phase 4: Console output and stale breakpoints | todo | Should |
| Phase 5: Keep listening after disconnect | todo | Could |
