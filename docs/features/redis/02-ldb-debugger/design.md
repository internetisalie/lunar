---
id: "REDIS-02-DESIGN"
title: "Technical Design"
type: "design"
status: "todo"
parent_id: "REDIS-02"
folders:
  - "[[features/redis/02-ldb-debugger/requirements|requirements]]"
---

# Technical Design: REDIS-02 — LDB Debug Adapter

Realizes every acceptance criterion in [requirements.md](requirements.md). All symbols named below
are grounded to `file:line` in this repo (or a resolved platform jar) or explicitly marked **[NEW]**.
REDIS-01 seams this feature consumes are marked **[REDIS-01]** — they exist as of the (promoted,
`status: planned`) REDIS-01 design and MUST NOT be redefined here; see §11 for the one seam
amendment this feature requires of REDIS-01.

Package root is `net.internetisalie.lunar` (verified: `src/main/kotlin/net/internetisalie/lunar/`).
New debug-adapter code lives under `net.internetisalie.lunar.redis.debug` (**[NEW]** package),
alongside the REDIS-01 `net.internetisalie.lunar.redis.{resp,connection,run,console}` packages.

## 1. Architecture Overview

### Current State (Prior Art in This Repo)

Lunar already ships **one** complete XDebugger adapter — the MobDebug/DBGp adapter under
`run/` — which is the structural template for this feature. Grounded (real, with `file:line`):

- **XDebugProcess subclass** — `run/LuaDebugProcess.kt:42` `class LuaDebugProcess(session, executionResult) : XDebugProcess(session)`
  overrides `getEditorsProvider`, `startStepOver/Into/Out` (`:57-67`), `resume` (`:75`),
  `stop` (`:69`), `getBreakpointHandlers` (`:93`), `getEvaluator` (`:137`), `sessionInitialized` (`:97`),
  `createConsole` (`:87`). It owns a session `childScope` (`:46-47`) and drives a controller.
- **Program runner** — `run/LuaDebugRunner.kt:49` `class LuaDebugRunner : GenericProgramRunner<RunnerSettings>()`;
  `canRun` gates on `DefaultDebugExecutor.EXECUTOR_ID` + config type (`:53-56`); `doExecute` builds the
  session via `XDebuggerManager.getInstance(project).newSessionBuilder(XDebugProcessStarter{…}).environment(env).startSession().runContentDescriptor`
  (`:91-105`). Registered `plugin.xml:514` `<programRunner implementation="…run.LuaDebugRunner"/>`.
- **Breakpoint type + handler** — `run/LuaLineBreakpointType.kt:33` `class LuaLineBreakpointType : XLineBreakpointTypeBase("lua-line", …, LuaDebuggerEditorsProvider())`
  with a `canPutAt` that iterates the line via `XDebuggerUtil.getInstance().iterateLine(...)` and
  accepts only positions whose enclosing element is a `LuaStatement` (`:45-82`); registered
  `plugin.xml:515` `<xdebugger.breakpointType implementation="…run.LuaLineBreakpointType"/>`.
  `run/LuaLineBreakpointHandler.kt:7` `class LuaLineBreakpointHandler(process) : XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<*>>>(LuaLineBreakpointType::class.java)`.
- **Suspend context / stack / frame / value / evaluator** — `run/LuaSuspendContext.kt:24` `: XSuspendContext`;
  `run/LuaExecutionStack.kt` `: XExecutionStack`; `run/LuaStackFrame.kt:30` `: XStackFrame()`
  (`computeChildren` builds `XValueChildrenList` + `XValueGroup` "Locals"/"Upvalues", `:53-123`);
  `run/LuaDebugVariable.kt:37` `: XNamedValue(name)` (`computePresentation`/`computeChildren`, `:44-83`);
  `run/LuaDebuggerEvaluator.kt:35` `: XDebuggerEvaluator()` (`evaluate(...)` → `launchEvaluate`,
  `getExpressionRangeAtOffset`, `:36-83`).
- **Controller / transport** — `run/LuaDebuggerController.kt:48` owns a session `scope`, exposes
  `suspend connect()` (`:97`), `suspend stepInto/stepOver/resume/addBreakPoint/removeBreakPoint`
  (`:166-204`), `suspend variables()` (`:254`), `launchEvaluate(stmt, callback)` (`:243`), and a
  `DebugObserver` that calls `session.breakpointReached(bp, null, ctx)` / `session.positionReached(ctx)`
  / `session.reportError(...)` (`:263-304`). `run/LuaDebugConnection.kt` is the reader-loop +
  `CompletableDeferred`/`Mutex` request/response pattern (contract §2 canonical example).
- **This design EXTENDS the pattern** — REDIS-02 adds a **second, independent** XDebugger adapter
  (LDB over RESP) beside the MobDebug one. It does **not** replace `LuaDebugProcess`/`LuaDebugRunner`
  et al. (they serve the standard `LuaRunConfiguration`); the two runners are disjoint because each
  runner's `canRun` gates on its own run-configuration type. **No code is shared with
  `LuaDebugConnection`/`LuaProcessUtil`** (epic RISK-R09): the LDB transport is the REDIS-01
  `RespClient`, not a new socket stack.
- **Notification group** — `plugin.xml:532` `<notificationGroup id="notification.group.lunar.debugger" …>`
  (reused for connection-loss notifications).
- **Coroutine scope** — `util/LunarCoroutineScopeService.kt:18` `@Service(PROJECT) val scope: CoroutineScope`,
  `.childScope("…")` per session (same idiom as `LuaDebugProcess.kt:46-47`).
- **Version compare** — `toolchain/model/SemanticVersion.kt:3` `data class SemanticVersion(...) : Comparable<…>`.
- **`LuaIcons.ROCKET`** — `lang/LuaIcons.kt:7`; run-config icon reuse (`rocks/run/LuaRocksRunConfiguration.kt:56`).

### REDIS-01 Seams Consumed (EXISTING-AS-OF-REDIS-01)

Named by their REDIS-01 design section; grounded to `docs/features/redis/01-connections-run-config/design.md`:

- **`RespClient`** [REDIS-01 §2.3] — `suspend fun command(args: List<ByteArray>): RespValue`,
  `suspend fun command(vararg args: String): RespValue`, `companion object { suspend fun open(connection, timeouts): RespClient }`,
  `Disposable`. The LDB transport opens this same client and issues every LDB command over it
  (REDIS-01 risks-and-gaps.md "Public Seams" names this as REDIS-02's debug transport). See §11 for
  the **one required amendment** (a synchronous read for out-of-band LDB stop replies).
- **`RespValue`** [REDIS-01 §2.1] — the decoded reply model (`Simple`/`Error`/`Integer`/`Bulk`/`Array`/…);
  LDB replies arrive as `RespValue.Array` of `RespValue.Simple`/`Bulk` status lines (§3.3).
- **`RespCodec.encodeCommand(args: List<ByteArray>)`** [REDIS-01 §2.2] — used indirectly through
  `RespClient.command`; byte-accurate array-of-bulk framing (no re-implementation here).
- **`LuaRedisServerConnection` / `LuaRedisConnectionSettings`** [REDIS-01 §2.4/§2.5] — the debug
  session resolves its connection by id (`findById`) exactly as the run configuration does.
- **`LuaRedisServerLauncher.launch(provisioning): LaunchedServer`** [REDIS-01 §2.12] — the
  "debug against local" escape hatch (epic RISK-R02) reuses this launcher unchanged.
- **`LuaRedisRunConfiguration` / `LuaRedisRunConfigurationOptions` / `LuaRedisExecMode`** [REDIS-01 §2.8] —
  the Debug executor runs the **same** run configuration; this feature adds two option fields
  (`debugMode`, plus reuse of the existing `readOnly`) — see §2.9 (amends REDIS-01 options, additive).
- **`RespReplyTreeConsole`** [REDIS-01 §2.6] — reused verbatim to render the mid-pause `redis <cmd>`
  reply tree in the debug "Redis" console tab (§2.7).
- **`RespException`** [REDIS-01 §2.10] — `Timeout`/`Protocol`/`Io` surface transport failures to the
  debug UI (§3.6).

### Target State

```
LuaRedisDebugRunner (GenericProgramRunner, canRun = DefaultDebugExecutor + LuaRedisRunConfiguration)
  └─ doExecute → state.execute(...) → XDebuggerManager.newSessionBuilder(starter).startSession()
        starter.start(session) → LuaRedisDebugProcess(session, executionResult, config)
              ├─ getBreakpointHandlers() → [LuaLdbBreakpointHandler]
              ├─ getEditorsProvider() → LuaDebuggerEditorsProvider  (REUSED from run/)
              ├─ sessionInitialized() → sessionScope.launch { controller.connect() }
              └─ controller: LuaLdbController(session, scope, config)
                    ├─ transport: LuaLdbTransport(RespClient [REDIS-01])   ← SCRIPT DEBUG + EVAL + LDB cmds
                    ├─ wire:      LdbWire (encode) + LdbReplyParser + LdbPrintParser
                    ├─ machine:   LdbSessionMachine (HANDSHAKE→ARMED→PAUSED⇄RUNNING→TERMINATED)
                    └─ on pause:  session.breakpointReached / positionReached with
                                  LuaLdbSuspendContext → LuaLdbExecutionStack → LuaLdbStackFrame
                                        → LuaLdbValue (: XNamedValue)   ← from LdbPrintParser
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.redis.debug.LuaRedisDebugRunner` **[NEW]**
- **Responsibility**: `GenericProgramRunner` that starts an LDB debug session for a `LuaRedisRunConfiguration`
  under the Debug executor (mirrors `run/LuaDebugRunner.kt:49`).
- **Threading**: `doExecute` on EDT (platform contract); returns a `RunContentDescriptor`.
- **Collaborators**: `XDebuggerManager` [grounded, `intellij.platform.debugger.jar`], `XDebugProcessStarter`,
  `LuaRedisDebugProcess` (§2.2), `LuaRedisRunConfiguration` [REDIS-01 §2.8].
- **Key API**:
  ```kotlin
  class LuaRedisDebugRunner : GenericProgramRunner<RunnerSettings>() {
      override fun getRunnerId(): String = "redis.ldb.debugrunner"
      override fun canRun(executorId: String, runProfile: RunProfile): Boolean =
          executorId == DefaultDebugExecutor.EXECUTOR_ID && runProfile is LuaRedisRunConfiguration
      override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor?
  }
  ```
  `doExecute` body mirrors `LuaDebugRunner.createDebugSession` (`run/LuaDebugRunner.kt:91-105`):
  `state.execute(executor, this) ?: return null`; `newSessionBuilder(starter).environment(env).startSession().runContentDescriptor`.

### 2.2 `net.internetisalie.lunar.redis.debug.LuaRedisDebugProcess` **[NEW]**
- **Responsibility**: `XDebugProcess` bridging XDebugger actions to `LuaLdbController`. Maps
  step/resume/stop; disables Step Out; owns the session `childScope`.
- **Threading**: platform calls its overrides on EDT; each override `sessionScope.launch { controller.… }`
  onto the pooled session scope (identical to `run/LuaDebugProcess.kt:57-78`).
- **Collaborators**: `LuaLdbController` (§2.3), `LuaLdbBreakpointHandler` (§2.4),
  `LuaDebuggerEditorsProvider` [REUSED, `run/LuaDebuggerEditorsProvider.kt`], `LunarCoroutineScopeService`
  [`util/LunarCoroutineScopeService.kt:18`].
- **Key API**:
  ```kotlin
  class LuaRedisDebugProcess(
      session: XDebugSession,
      private val executionResult: ExecutionResult,
      private val config: LuaRedisRunConfiguration,
  ) : XDebugProcess(session) {
      private val sessionScope = LunarCoroutineScopeService.getInstance(session.project).scope.childScope("RedisLdbSession")
      private val controller = LuaLdbController(session, sessionScope, config)
      private val breakpointHandler = LuaLdbBreakpointHandler(this)
      override fun getEditorsProvider(): XDebuggerEditorsProvider = LuaDebuggerEditorsProvider()
      override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>?> = arrayOf(breakpointHandler)
      override fun getEvaluator(): XDebuggerEvaluator? = session.currentStackFrame?.evaluator
      override fun startStepInto(context: XSuspendContext?) { sessionScope.launch { controller.step() } }
      override fun startStepOver(context: XSuspendContext?) { sessionScope.launch { controller.next() } }
      override fun startStepOut(context: XSuspendContext?) { /* no-op — LDB has no step-out (§3.5) */ }
      override fun resume(context: XSuspendContext?) { sessionScope.launch { controller.continueRun() } }
      override fun stop() { sessionScope.launch { controller.abort() } }
      override fun runToPosition(position: XSourcePosition, context: XSuspendContext?) { /* unsupported */ }
      override fun sessionInitialized()                              // §3.5 connect flow
      override fun createConsole(): ExecutionConsole                  // wraps executionResult console + Redis tab (§2.7)
      fun addBreakpoint(breakpoint: XBreakpoint<*>)                   // delegates to controller (armed/paused)
      fun removeBreakpoint(breakpoint: XBreakpoint<*>)
  }
  ```
  **Step Out unsupported surfacing (AC-4, TC-LDB-STEPOUT-1)**: `session.setPauseActionSupported(false)`
  is set in the controller init (as `run/LuaDebuggerController.kt:67`). Step Out is disabled by
  overriding `startStepOut` as a no-op AND by not advertising it — the XDebugger UI shows Step Out
  grayed because `LuaRedisDebugProcess` does not implement a step-out capability path; the tooltip
  "Step Out is not supported by the Redis Lua debugger" is provided via `session.reportMessage(...)`
  the first time it is attempted (guarded once). (LDB genuinely lacks `finish`/step-out.)

### 2.3 `net.internetisalie.lunar.redis.debug.LuaLdbController` **[NEW]**
- **Responsibility**: Owns the LDB session lifecycle: drives handshake + `EVAL`, translates
  controller calls into `LdbCommand`s, feeds replies to `LdbSessionMachine`, and raises XDebugger
  pause/resume/error events. The REDIS-02 analogue of `run/LuaDebuggerController.kt` — same shape,
  RESP transport instead of DBGp sockets.
- **Threading**: all suspend methods run on the session `scope` (pooled); UI/session callbacks are
  XDebugger-thread-safe (`session.breakpointReached`/`positionReached`/`reportError` are called off-EDT
  in the MobDebug controller, `run/LuaDebuggerController.kt:282-297`). PSI reads (breakpoint line
  mapping) use suspend `readAction { }` (contract §2).
- **Collaborators**: `LuaLdbTransport` (§2.10), `LdbWire`/`LdbReplyParser`/`LdbPrintParser` (§2.8/§2.9),
  `LdbSessionMachine` (§2.11), `RespClient` [REDIS-01 §2.3] via the transport, `LuaRedisServerLauncher`
  [REDIS-01 §2.12], `LuaRedisConnectionSettings.findById` [REDIS-01 §2.5].
- **Key API**:
  ```kotlin
  class LuaLdbController(
      private val session: XDebugSession,
      private val scope: CoroutineScope,
      private val config: LuaRedisRunConfiguration,
  ) {
      val isArmed: Boolean                                   // machine.state >= ARMED
      suspend fun connect()                                  // §3.5: resolve connection, launch-local if needed,
                                                             //        RespClient.open, SCRIPT DEBUG <mode>, EVAL
      suspend fun addBreakpoint(breakpoint: XBreakpoint<*>)  // §3.5 (break <line>)
      suspend fun removeBreakpoint(breakpoint: XBreakpoint<*>)
      suspend fun step()                                     // LdbCommand.Step
      suspend fun next()                                     // LdbCommand.Next
      suspend fun continueRun()                              // LdbCommand.Continue
      suspend fun abort()                                    // LdbCommand.Abort + teardown
      suspend fun evaluate(expression: String): LuaLdbValue  // LdbCommand.Eval → LdbPrintParser
      fun launchEvaluate(expression: String, callback: XDebuggerEvaluator.XEvaluationCallback) // bridge (§2.6)
      suspend fun readLocals(): List<LuaLdbLocal>            // LdbCommand.Print → LdbPrintParser (§3.4)
      suspend fun redisCommand(args: List<String>): RespValue // LdbCommand.RedisCmd (mid-pause, §2.7)
  }
  ```
  The `connect → run` sequence mirrors `LuaDebugProcess.sessionInitialized` (`run/LuaDebugProcess.kt:97-133`):
  `withBackgroundProgress(project, "Starting Redis debug session") { controller.connect(); drainPendingBreakpoints(); … }`.

### 2.4 `net.internetisalie.lunar.redis.debug.LuaLdbBreakpointHandler` **[NEW]**
- **Responsibility**: Registers/unregisters LDB line breakpoints; delegates to `LuaRedisDebugProcess`
  (mirrors `run/LuaLineBreakpointHandler.kt:7`).
- **Threading**: platform calls `registerBreakpoint`/`unregisterBreakpoint` on EDT; delegates to the
  process which launches onto the session scope.
- **Key API**:
  ```kotlin
  class LuaLdbBreakpointHandler(private val process: LuaRedisDebugProcess)
      : XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<*>>>(LuaLdbBreakpointType::class.java) {
      override fun registerBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>) { process.addBreakpoint(breakpoint) }
      override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>, temporary: Boolean) { process.removeBreakpoint(breakpoint) }
  }
  ```

### 2.5 `net.internetisalie.lunar.redis.debug.LuaLdbBreakpointType` **[NEW]**
- **Responsibility**: The Redis-LDB line breakpoint type (a distinct type so Redis-script breakpoints
  are visually and behaviorally separate from MobDebug ones). Same `canPutAt` logic as
  `run/LuaLineBreakpointType.kt` but **without** the trailing `!!` (contract §1 — the MobDebug one at
  `LuaLineBreakpointType.kt:81` uses `result.get()!!`; this replaces it with an Elvis default).
- **Threading**: `canPutAt` on EDT/read context (platform-invoked); reads PSI via the platform's
  `iterateLine`.
- **Collaborators**: `XLineBreakpointTypeBase` [grounded], `XDebuggerUtil.getInstance().iterateLine`
  [grounded, `run/LuaLineBreakpointType.kt:50`], `LuaFile`/`LuaStatement` [`lang/psi/`, grounded],
  `LuaDebuggerEditorsProvider` [REUSED, `run/`].
- **Key API**:
  ```kotlin
  class LuaLdbBreakpointType : XLineBreakpointTypeBase(
      "redis-lua-line", "Redis Lua Line Breakpoints", LuaDebuggerEditorsProvider()) {
      override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean  // §3.9
      override fun getDisplayText(breakpoint: XLineBreakpoint<XBreakpointProperties<*>?>): String
  }
  ```

### 2.6 `net.internetisalie.lunar.redis.debug.LuaLdbEvaluator` **[NEW]**
- **Responsibility**: `XDebuggerEvaluator` for the LDB frame; routes `evaluate` to `LdbCommand.Eval`
  via the controller (mirrors `run/LuaDebuggerEvaluator.kt:35`, incl. the `return <expr>` wrapping).
- **Threading**: `evaluate` invoked on EDT; delegates to `controller.launchEvaluate` which runs on the
  session scope and calls `callback.evaluated(...)`/`callback.errorOccurred(...)`
  (as `run/LuaDebuggerController.kt:243-252`).
- **Key API**:
  ```kotlin
  class LuaLdbEvaluator(private val controller: LuaLdbController) : XDebuggerEvaluator() {
      override fun evaluate(expression: String, callback: XEvaluationCallback, expressionPosition: XSourcePosition?) {
          controller.launchEvaluate(expression, callback)
      }
      override fun getExpressionRangeAtOffset(project: Project, document: Document, offset: Int, sideEffectsAllowed: Boolean): TextRange?
          // reuse the exact algorithm in run/LuaDebuggerEvaluator.kt:56-97 (LuaExpr parent walk)
  }
  ```

### 2.7 Redis console tab (mid-pause `redis <cmd>`)
- **Responsibility**: A console tab labeled "Redis" that runs `LdbCommand.RedisCmd` in the paused
  session and renders replies via the REDIS-01 `RespReplyTreeConsole` [REDIS-01 §2.6].
- **Component**: `net.internetisalie.lunar.redis.debug.LuaLdbRedisConsoleTab` **[NEW]** — a
  `com.intellij.execution.configurations.AdditionalTabComponentManager` tab (grounded via
  `XDebugSessionTab`/`RunnerLayoutUi.createContent`, see §7 for the registration path) hosting an
  input field + the reused `RespReplyTreeConsole`.
- **Threading**: input on EDT; the send runs on the session scope; the reply tree updates via
  `withContext(Dispatchers.EDT)` (as REDIS-01 §2.6).
- **Key API**:
  ```kotlin
  class LuaLdbRedisConsoleTab(project: Project, private val controller: LuaLdbController) : Disposable {
      val component: JComponent           // input field + RespReplyTreeConsole.component
      fun submit(commandLine: String)     // tokenizes → controller.redisCommand(args) → tree.showReply
      override fun dispose()
  }
  ```

### 2.8 `net.internetisalie.lunar.redis.debug.LdbWire` + `LdbCommand` **[NEW]**
- **Responsibility**: The LDB command vocabulary and its RESP encoding. Pure/stateless.
- **Threading**: thread-agnostic (pure functions).
- **Collaborators**: `RespCodec.encodeCommand` [REDIS-01 §2.2] (via `RespClient.command`, so `LdbWire`
  only produces the `List<ByteArray>` argument vector, never touches sockets).
- **Key API**:
  ```kotlin
  sealed interface LdbCommand {
      data class EnterDebug(val mode: LuaRedisDebugMode) : LdbCommand   // SCRIPT DEBUG YES|SYNC
      data class Eval(val expression: String) : LdbCommand             // eval <expr>
      object Step : LdbCommand                                          // step
      object Next : LdbCommand                                         // next
      object Continue : LdbCommand                                     // continue
      object Abort : LdbCommand                                        // abort
      data class Break(val line: Int) : LdbCommand                     // break <line>
      data class RemoveBreak(val line: Int) : LdbCommand               // break -<line>
      object ClearBreaks : LdbCommand                                  // break 0
      data class Print(val varName: String?) : LdbCommand             // print  |  print <var>
      data class RedisCmd(val args: List<String>) : LdbCommand         // redis <cmd> <args…>
      object ListSource : LdbCommand                                   // whole  (source-sync check)
  }
  enum class LuaRedisDebugMode { FORKED, SYNC }
  object LdbWire {
      fun encode(command: LdbCommand): List<ByteArray>                 // §3.2 (UTF-8 bytes per token)
  }
  ```

### 2.9 `net.internetisalie.lunar.redis.debug.LdbReplyParser` + `LdbPrintParser` **[NEW]**
- **Responsibility**: Parse LDB reply blocks (RESP arrays of status lines) into typed events; parse
  `print` output into a value tree. Pure/stateless; **no `!!`** in parsing (contract §1; RISK-R09).
- **Threading**: called on the session reader path (pooled).
- **Collaborators**: `RespValue.Array`/`Simple`/`Bulk` [REDIS-01 §2.1].
- **Key API**:
  ```kotlin
  sealed interface LdbEvent {
      data class Stop(val serverLine: Int, val reason: StopReason, val sourceLine: String?) : LdbEvent
      data class Error(val kind: LdbErrorKind, val message: String, val scriptLine: Int?) : LdbEvent
      data class SessionEnded(val reason: EndReason) : LdbEvent
      data class Redis(val reply: RespValue) : LdbEvent
      object Ack : LdbEvent                                            // +OK / no-op status
  }
  enum class StopReason { BREAKPOINT, STEP, NEXT }
  enum class LdbErrorKind { COMPILE, RUNTIME, EVAL_FAILED }
  enum class EndReason { ENDED, FORK_TIMEOUT, ABORTED }
  object LdbReplyParser {
      fun parse(reply: RespValue): LdbEvent                           // §3.3
  }
  data class LuaLdbLocal(val name: String, val value: LdbValueNode)
  sealed interface LdbValueNode {
      data class Scalar(val text: String, val truncated: Boolean = false) : LdbValueNode
      data class Table(val entries: List<Pair<String, LdbValueNode>>, val truncated: Boolean = false) : LdbValueNode
  }
  object LdbPrintParser {
      fun parseLocals(reply: RespValue): List<LuaLdbLocal>            // §3.4
      fun parseValue(reply: RespValue): LdbValueNode                  // single eval result
  }
  ```
- **`LuaRedisDebugMode` on the run config**: REDIS-01's `LuaRedisRunConfigurationOptions` [REDIS-01 §2.8]
  gains **one additive** `string()` StoredProperty `debugMode` (`"FORKED"|"SYNC"`, default `"FORKED"`);
  `LuaRedisRunConfiguration` exposes `var debugMode: LuaRedisDebugMode` bridging it (same String↔enum
  pattern REDIS-01 uses for `execMode`). This is additive to REDIS-01 (§11 amendment A2, non-breaking).

### 2.10 `net.internetisalie.lunar.redis.debug.LuaLdbTransport` **[NEW]**
- **Responsibility**: Owns the `RespClient` for the debug session and implements the LDB request/reply
  discipline: a command written on the connection is answered by one or more reply blocks; **stop/step
  replies arrive on the same connection as the response to the resuming command** (LDB is synchronous
  per connection — `redis-cli --ldb` reads the next reply after each command). `Disposable`.
- **Threading**: all I/O on the session scope via `RespClient.command`/`readReply` (suspend); no EDT.
- **Collaborators**: `RespClient` [REDIS-01 §2.3] — including the §11 amendment `readReply()`.
- **Key API**:
  ```kotlin
  class LuaLdbTransport(private val client: RespClient) : Disposable {
      suspend fun enterDebug(mode: LuaRedisDebugMode): RespValue      // SCRIPT DEBUG YES|SYNC
      suspend fun eval(scriptBody: String, keys: List<String>, argv: List<String>): RespValue // the debugged EVAL
      suspend fun send(command: LdbCommand): RespValue                // one LDB command → its reply block
      override fun dispose()                                          // client.dispose()
  }
  ```

### 2.11 `net.internetisalie.lunar.redis.debug.LdbSessionMachine` **[NEW]**
- **Responsibility**: The explicit debug-session state machine (§3.5). Guards command legality (no
  `step` on a `TERMINATED` session) and tracks the current paused line.
- **Threading**: confined to the session scope; mutated only from the controller's suspend methods
  (single-threaded per session → no cross-thread state, unlike the MobDebug defect RISK-R09).
- **Key API**:
  ```kotlin
  enum class LdbState { HANDSHAKE, ARMED, RUNNING, PAUSED, TERMINATED }
  class LdbSessionMachine {
      val state: LdbState
      val currentLine: Int?                                          // set on PAUSED
      fun onCommandSent(command: LdbCommand): Boolean                // false if illegal in current state (§3.5)
      fun onEvent(event: LdbEvent)                                   // drives transitions (§3.5)
  }
  ```

### 2.12 Suspend context / stack / frame / value **[NEW]** (structural mirrors of `run/`)
- `LuaLdbSuspendContext(project, controller, position, locals) : XSuspendContext` — mirrors
  `run/LuaSuspendContext.kt:24`; `getActiveExecutionStack()` returns the single LDB frame stack.
- `LuaLdbExecutionStack(project, controller, frame) : XExecutionStack` — mirrors
  `run/LuaExecutionStack.kt` (LDB exposes one active frame; `computeStackFrames` yields that frame).
- `LuaLdbStackFrame(project, controller, position, locals) : XStackFrame` — mirrors
  `run/LuaStackFrame.kt:30`; `getEvaluator()` returns `LuaLdbEvaluator(controller)`; `computeChildren`
  builds an `XValueChildrenList` with a "Locals" `XValueGroup` from `locals` (§2.9 model).
- `LuaLdbValue(local: LuaLdbLocal) : XNamedValue(local.name)` — mirrors `run/LuaDebugVariable.kt:37`;
  `computePresentation` renders `LdbValueNode.Scalar` inline (truncation marker if `truncated`),
  `computeChildren` expands `LdbValueNode.Table` entries into child `LuaLdbValue`s. **[grounded
  supertypes: `XSuspendContext`/`XExecutionStack`/`XStackFrame`/`XNamedValue`/`XValueChildrenList`/
  `XValueGroup` all in `intellij.platform.debugger.jar`].**

### 2.13 `net.internetisalie.lunar.redis.debug.LuaLdbSyncGuard` **[NEW]**
- **Responsibility**: Decide whether sync-mode debugging against a given connection needs a
  confirmation dialog, and produce the banner text. Pure decision + a small EDT dialog helper.
- **Threading**: `requiresConfirmation` is pure; `confirm(...)` shows a `Messages.showYesNoDialog`
  on EDT (grounded, `com.intellij.openapi.ui.Messages`, used at `toolchain/provision/LuaToolchainActions.kt:55`).
- **Key API**:
  ```kotlin
  object LuaLdbSyncGuard {
      fun requiresConfirmation(config: LuaRedisRunConfiguration, connection: LuaRedisServerConnection): Boolean // §3.8
      fun bannerText(mode: LuaRedisDebugMode): String   // FORKED → "changes rolled back"; SYNC → "server blocked; writes committed"
      suspend fun confirm(project: Project, connection: LuaRedisServerConnection): Boolean  // EDT dialog
  }
  ```

## 3. Algorithms

### 3.1 LDB wire model (grounding the protocol as message structure)

LDB is driven over the **same RESP connection** used for `EVAL`. The sequence (per the `redis-cli --ldb`
reference implementation, epic RISK-R01 — pinned by `TC-INT-*`):

1. `SCRIPT DEBUG YES` (fork) or `SCRIPT DEBUG SYNC` → `+OK`.
2. `EVAL <script> <numkeys> <keys…> <argv…>` — this **enters the stepping session**. The server does
   **not** immediately return the script result; instead it returns the **first debug reply block**
   (an initial stop, typically at the first line), as a RESP `Array` of status `Simple`/`Bulk` lines.
3. Each subsequent LDB command (`step`, `next`, `continue`, `break`, `print`, `eval`, `redis`, `abort`)
   is sent as a normal RESP command whose **first argument is the LDB verb** and is answered by exactly
   one reply block. The reply to `continue`/`step`/`next` is the **next stop block** (or a
   session-end block, or the final `EVAL` result when the script completes).
4. When the script finishes, the reply block to the resuming command is the **actual `EVAL` result**
   (a normal `RespValue`), immediately followed by a session-end line. When aborted, the reply is a
   session-end block.

Every debug verb is encoded as a RESP array-of-bulk (§3.2). Reply blocks are RESP arrays of status
lines beginning with a leading sentinel: `*` (status/stop), `<value>` (print output), or a Redis
reply (for `redis`). §3.3 specifies the parse.

### 3.2 Command encoding (`LdbWire.encode`)
- **Input → Output**: `LdbCommand` → `List<ByteArray>` (UTF-8 tokens; framed by `RespCodec` [REDIS-01]).
- **Mapping** (one row = one command, tokens are the RESP array elements):

  | `LdbCommand` | tokens |
  |--------------|--------|
  | `EnterDebug(FORKED)` | `["SCRIPT","DEBUG","YES"]` |
  | `EnterDebug(SYNC)` | `["SCRIPT","DEBUG","SYNC"]` |
  | `Step` | `["step"]` |
  | `Next` | `["next"]` |
  | `Continue` | `["continue"]` |
  | `Abort` | `["abort"]` |
  | `Break(n)` | `["break", n.toString()]` |
  | `RemoveBreak(n)` | `["break", "-" + n.toString()]` |
  | `ClearBreaks` | `["break", "0"]` |
  | `Print(null)` | `["print"]` |
  | `Print(v)` | `["print", v]` |
  | `Eval(e)` | `["eval", e]` |
  | `RedisCmd(args)` | `["redis"] + args` |
  | `ListSource` | `["whole"]` |
- **Rules**: each token → `token.toByteArray(Charsets.UTF_8)`; the vector is passed to
  `RespClient.command(args)` [REDIS-01 §2.3] which frames it. No local byte framing (RISK-R09).

### 3.3 Reply-block parsing (`LdbReplyParser.parse`)
- **Input → Output**: `RespValue` (one reply block) → `LdbEvent`.
- **Steps**:
  1. Normalize the block to a `List<String>` of status lines: if `RespValue.Array`, map each item to
     its string form (`Simple.text` / `Bulk.asString() ?: ""`); if a scalar `Simple`/`Bulk`, a
     singleton list; if `RespValue.Error`, → `LdbEvent.Error(RUNTIME, error.message, scriptLine=extractUserScriptLine(error.message))`.
  2. Let `first = lines.firstOrNull()?.trim() ?: return LdbEvent.Ack`.
  3. **Session-end** (checked first): `first.startsWith("* Forked debugging session")` →
     `SessionEnded(FORK_TIMEOUT)`; `first.contains("<endsession>")` (case-insensitive) →
     `SessionEnded(ENDED)`; `first.contains("session ended")` (case-insensitive) →
     `SessionEnded(ENDED)`; `first.contains("Aborted")` → `SessionEnded(ABORTED)`.
     > **Live-framing confirmation (Phase 5 / DR-01, redis:8 + valkey:8):** the real servers signal
     > every session end — normal completion, end-of-script, and `abort` — with a `["<endsession>"]`
     > block; the assumed `"* Lua debugging session ended"` / `"* Forked debugging session was closed"`
     > text is **never emitted** by these versions. The real `EVAL` result (`:N`) or the abort error
     > (`-ERR script aborted…`) is a **separate trailing block** on the same connection, drained via
     > `RespClient.readReply` (§11 A1). `<endsession>` recognition is the production fix that landed in
     > Phase 5; the older strings are retained for forward/other-version compat. Both `step` and `next`
     > report `stop reason = step over` and breakpoints `stop reason = break point` — step 5's
     > `reasonFrom` degrades to STEP/BREAKPOINT respectively (reason is cosmetic; the stop line/advance
     > are correct). `redis <cmd>` replies render as `<redis> …` / `<reply> …` status lines.
  4. **Compile error**: `first.startsWith("* Error compiling")` → `Error(COMPILE, msgAfterColon, extractUserScriptLine(first))`.
  5. **Stop**: `Regex("""\*\s*Stopped at (\d+)""").find(first)` matches → `Stop(serverLine = group1.toInt(),
     reason = <reasonFromText>, sourceLine = stripGutter(lines.getOrNull(1)))`, where `reason` is
     derived from a `"stop reason = <r>"` fragment on the same line (`step`→STEP, `next`→NEXT, else
     BREAKPOINT).
  6. **Redis reply**: if the block was produced in response to a `RedisCmd` (caller-tagged), →
     `Redis(reply)` (the raw `RespValue` is passed straight to `RespReplyTreeConsole`).
  7. Otherwise → `Ack`.
- **`stripGutter(line)`**: LDB content lines are `"<N>   <source>"`; strip a leading
  `Regex("""^\s*\d+\s+""")`. Returns the source text or `null`.
- **`extractUserScriptLine(text)`**: `Regex("""user_script:(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()`.
- **Rules / edge handling**: never `!!` — every `getOrNull`/`toIntOrNull`/Elvis. An unrecognized block
  is `Ack` (no crash). A `RespValue.Error` from the transport layer (not a status line) is surfaced by
  the controller as `session.reportError` (§3.6), distinct from an in-band `LdbEvent.Error`.

### 3.4 Print parsing (`LdbPrintParser.parseLocals` / `parseValue`)
- **Input → Output**: `RespValue` print block → `List<LuaLdbLocal>` (or a single `LdbValueNode`).
- **Format** (redis-cli `print` with no arg lists every local as one line): each line is
  `"<value> <name> = <repr>"` where `<repr>` is a scalar (`10`, `"str"`, `nil`, `true`) or a table
  rendered as a brace/JSON-ish structure that LDB truncates at the server `maxlen`.
- **Steps**:
  1. Normalize block to `List<String>` (as §3.3 step 1).
  2. For each line: strip a leading `"<value> "` sentinel if present; split on the **first** `" = "`
     into `(name, repr)`; skip lines without a ` = ` (status noise).
  3. `LuaLdbLocal(name.trim(), parseRepr(repr))`.
- **`parseRepr(repr)`** (recursive-descent over the table repr):
  1. `t = repr.trim()`. If `t` starts with `{` (table) → parse a brace/bracket-delimited list of
     `key: value` or `value` entries by a single left-to-right scan tracking brace depth and quotes;
     each entry recurses via `parseRepr`. Keys quoted → unquoted string; unquoted integer keys →
     `[<i>]`. Return `LdbValueNode.Table(entries, truncated = t.endsWith("(truncated)") || depthUnbalanced)`.
  2. Else → `LdbValueNode.Scalar(t.removeSuffix(" (truncated)").trim(), truncated = t.endsWith("(truncated)"))`.
- **Rules / edge handling** (TC-LDB-PRINT-2): an unterminated/truncated table (unbalanced braces
  because `maxlen` cut it) sets `truncated = true` on the partially-parsed node and returns what was
  parsed — the scanner never throws, never `!!`. Depth is bounded by the brace nesting in the repr;
  a hard cap of 64 levels prevents pathological input from stack-overflowing (return the node as-is at
  the cap).

### 3.5 Session state machine (`LdbSessionMachine`) + connect flow
- **States**: `HANDSHAKE → ARMED → RUNNING ⇄ PAUSED → TERMINATED`.
- **`connect()` flow** (`LuaLdbController.connect`, mirrors `LuaDebugProcess.sessionInitialized`
  `run/LuaDebugProcess.kt:97-133`):
  1. Resolve connection: `LuaRedisConnectionSettings.getInstance(project).findById(config.connectionId)` [REDIS-01 §2.5];
     null → `ExecutionException` surfaced (AC-7).
  2. **Sync guard** (§3.8): if `config.debugMode == SYNC` and `LuaLdbSyncGuard.requiresConfirmation(...)`,
     await `LuaLdbSyncGuard.confirm(...)`; on decline → abort connect, stop session.
  3. Launch-local if provisioning ≠ Remote: `LuaRedisServerLauncher(project).launch(connection.provisioning)`
     [REDIS-01 §2.12] → endpoint.
  4. `RespClient.open(connection-at-endpoint, timeouts)` [REDIS-01 §2.3]; wrap in `LuaLdbTransport`.
  5. `transport.enterDebug(config.debugMode)` → expect `+OK`; machine `HANDSHAKE → ARMED`.
  6. Read the script body via suspend `readAction { }` (contract §2); send the debugged `EVAL`
     (`transport.eval(body, config.keys, config.argv)` [REDIS-01 config seams]) — but **first**
     drain installed breakpoints (`break <line>` for each) so they are set before the script runs
     (AC-2). Machine `ARMED → RUNNING`.
  7. The `EVAL` reply is the first stop block → `LdbReplyParser.parse` → `Stop`; machine
     `RUNNING → PAUSED`, `currentLine = serverLine`; raise the XDebugger pause (below).
- **`onCommandSent(command)`** (legality gate): `Step`/`Next`/`Continue`/`Print`/`Eval`/`RedisCmd`
  legal only in `PAUSED`; `Break`/`RemoveBreak`/`ClearBreaks` legal in `ARMED`+`PAUSED`; `Abort` legal
  in any non-`TERMINATED` state. Illegal → returns `false` (controller no-ops, logs; never throws to
  the UI) — this is the guard for TC-LDB-SM-2.
- **`onEvent(event)`** transitions: `Stop` → `PAUSED(currentLine)`; on a resuming command the next
  `Stop` re-enters `PAUSED`; `SessionEnded`/`Error(COMPILE)` → `TERMINATED`. `RUNNING` is the transient
  between sending a resume command and receiving its reply.
- **Raising the pause** (in the controller, after `PAUSED`): map `serverLine` (1-based) to an
  `XSourcePosition` on the script file (`XDebuggerUtil.getInstance().createPosition(scriptVFile, serverLine - 1)`
  [grounded]); if the paused line has a registered breakpoint → `session.breakpointReached(bp, null, ctx)`,
  else `session.positionReached(ctx)` — exactly the branch in `run/LuaDebuggerController.kt:274-289`,
  with `ctx = LuaLdbSuspendContext(project, controller, position, readLocals())`.
- **Step-out**: `startStepOut` is a no-op (LDB has no step-out verb — the wire vocabulary in §3.2 has
  no `finish`/`out`); the disabled-tooltip surfacing is in §2.2.

### 3.6 Error & lifecycle handling (AC-7, AC-9)
- **Compile error** (bad script): the `EVAL` reply block parses to `Error(COMPILE, msg, scriptLine)`
  → `session.reportError(msg)` + machine `TERMINATED` + `session.stop()` (mirrors
  `run/LuaDebuggerController.kt:292-298`). Never an IDE fatal-error report.
- **Eval/watch failure**: `evaluate` gets `Error(EVAL_FAILED, msg, …)` → `callback.errorOccurred(msg)`
  (never `evaluated`, never swallowed) — TC-LDB-ERR-1, mirrors `run/LuaDebuggerController.kt:246-250`.
- **Connection loss / transport failure**: a `RespException.Io`/`Timeout` [REDIS-01 §2.10] thrown by
  `RespClient.command` is caught in the controller's command wrapper → `session.reportError(...)` +
  a `notification.group.lunar.debugger` [grounded, `plugin.xml:532`] error notification + teardown.
  No hang (the read is cancellable via the session scope) — TC-LDB-ERR-2.
- **Forked-session timeout** (AC-9): the server closes an idle forked session; the next reply block (or
  an unsolicited one) parses to `SessionEnded(FORK_TIMEOUT)` → a clean "Redis debug session ended by
  the server (forked-session timeout)" message via `session.reportMessage`/console print, then session
  stop — **not** an exception. TC-INT-3.
- **Teardown**: `abort()` sends `LdbCommand.Abort` (best-effort, ignore failure), then
  `transport.dispose()` (closes the `RespClient`) and `scope.cancel()` — the launcher's `stop()`
  [REDIS-01 §2.12] runs on session dispose (idempotent). Mirrors `run/LuaDebuggerController.kt:117-154`.

### 3.7 Conditional breakpoints (AC-3, TC-LDB-COND-1)
- **Input → Output**: a `PAUSED` at `serverLine` with a registered `XLineBreakpoint` carrying a
  non-null `conditionExpression` → resume-or-pause decision.
- **Steps** (evaluated **IDE-side**, since LDB has no server-side conditional breakpoints):
  1. On `Stop` at a line that maps to a breakpoint `bp`, read `bp.conditionExpression?.expression`
     [grounded: `XLineBreakpoint.getConditionExpression(): XExpression?`].
  2. If null/blank → pause normally (§3.5).
  3. Else `val result = controller.evaluate(condition)`; interpret truthiness: a
     `LdbValueNode.Scalar` whose text is `"false"` or `"nil"` → **false**; anything else → **true**
     (Lua truthiness).
  4. false → `controller.continueRun()` silently (no UI pause); true → raise the pause (§3.5).
- **Rules**: an eval error in the condition → treat as **true** (pause) and surface the eval error in
  the frame, so a broken condition never silently skips a breakpoint.

### 3.8 Sync-mode guard (`LuaLdbSyncGuard`) (AC-8, TC-LDB-SYNC-1)
- **`requiresConfirmation(config, connection)`**:
  1. If `config.debugMode == FORKED` → `false` (fork is always safe).
  2. If `SYNC` and `connection.provisioning` is `LocalBinary` or `Docker` (session-local, launched by
     this run) → `false` (the server is disposable/ours).
  3. If `SYNC` and `connection.provisioning == Remote` → `true` (a shared server could be blocked).
- **`bannerText(mode)`**: `FORKED` → "Forked debug session: all writes are rolled back when the
  session ends."; `SYNC` → "SYNC debug session: the server event loop is BLOCKED while paused and
  writes are COMMITTED."
- **`confirm(...)`**: `Messages.showYesNoDialog(project, "<consequence text>", "Redis Sync Debugging", …)`
  on EDT; returns `true` on Yes.
- Optional hard gate (epic RISK-R03): honor an application setting `redis.debug.allowSyncOnRemote`
  (default false) — when false, `requiresConfirmation` step 3 instead **refuses** (returns a sentinel
  the controller turns into an `ExecutionException`). Setting lives in REDIS-01's connection settings
  service scope; wiring is a Should (§8 coverage notes it under AC-8).

### 3.9 Breakpoint line validity (`LuaLdbBreakpointType.canPutAt`) (AC-2)
- **Input → Output**: `(VirtualFile, line, Project)` → `Boolean`.
- **Steps**: identical to `run/LuaLineBreakpointType.kt:45-82` — `PsiManager.findFile(file) as? LuaFile`
  (else false); get the `Document`; `XDebuggerUtil.getInstance().iterateLine(project, document, line) { … }`
  walking up to the enclosing element whose offset is still on `line`; accept iff that element is a
  `LuaStatement` [`lang/psi/`, grounded]. **Difference from the MobDebug type**: return
  `result.get() == true` (Elvis/`== true`) instead of the `result.get()!!` at
  `run/LuaLineBreakpointType.kt:81` (contract §1 — no `!!`). This is the only behavioral change; a
  non-code line (blank/comment) → `false` (TC-LDB-BP-1).

## 4. External Data & Parsing

### 4.1 LDB reply blocks over RESP
- **Format**: a RESP `Array` (occasionally a scalar) of status/content lines. Representative blocks:
  - Stop: `*3\r\n$29\r\n* Stopped at 3, stop reason = step\r\n$18\r\n3   local x = 1\r\n…`
    (decodes to `["* Stopped at 3, stop reason = step", "3   local x = 1", …]`).
  - Print: lines like `<value> x = 10`, `<value> t = {["a"]=1, ["b"]={["c"]=2}}`.
  - Compile error: `* Error compiling script (new function): user_script:2: '=' expected near 'x'`.
  - Session end: `* Lua debugging session ended` / `* Forked debugging session was closed`.
- **Parse strategy**: §3.3 (`LdbReplyParser`) and §3.4 (`LdbPrintParser`) — line-oriented, regex-pinned,
  no `!!`. The RESP framing itself is decoded by REDIS-01's `RespCodec` [§2.2] (not re-implemented).
- **Maps to**: `LdbEvent` / `LuaLdbLocal` / `LdbValueNode`.
- **Failure handling**: unrecognized block → `LdbEvent.Ack`; malformed RESP → `RespException.Protocol`
  [REDIS-01 §2.10] surfaced as a transport error (§3.6).

### 4.2 `SCRIPT DEBUG` capability probe (epic RISK-R02)
- **Format**: `SCRIPT DEBUG YES` returns `+OK`, or `-ERR ...` on a server that forbids it (managed
  offerings).
- **Parse strategy**: a `RespValue.Error` reply to `enterDebug` → treat as "debugging not permitted".
- **Maps to**: an actionable `ExecutionException` ("this server does not permit script debugging —
  debug against a local server instead") with the launch-local hint (epic RISK-R02); surfaced via
  `session.reportError`. (Covered as an AC-7 behavior; the one-click switch UI is deferred — §10/risks.)

## 5. Data Flow

### Example 1: Breakpoint hit → step → resume (forked, remote)
1. User clicks Debug on a Redis Script run config → `LuaRedisDebugRunner.doExecute` →
   `state.execute` → `newSessionBuilder(starter).startSession()`; `starter.start` returns
   `LuaRedisDebugProcess`.
2. `sessionInitialized` → `sessionScope.launch { controller.connect() }`: resolve connection,
   `RespClient.open`, `SCRIPT DEBUG YES` (+OK → ARMED), `break 3`, `EVAL <script>` → first stop.
3. First stop at line 3 maps to the breakpoint → `session.breakpointReached(bp, null, ctx)`; `ctx`'s
   frame `computeChildren` renders locals from `controller.readLocals()` (`print` → `LdbPrintParser`).
4. User clicks Step Over → `startStepOver` → `controller.next()` → `LdbCommand.Next` →
   next stop block → `session.positionReached(ctx')`.
5. User clicks Resume → `controller.continueRun()` → `LdbCommand.Continue`; script completes → the
   reply is the final `EVAL` result + session-end → machine `TERMINATED`, session ends, forked writes
   rolled back; banner already showed "changes rolled back".

### Example 2: Sync-mode against a remote server
1. Config `debugMode = SYNC`, `connection.provisioning = Remote`.
2. `connect()` step 2: `LuaLdbSyncGuard.requiresConfirmation → true` → `confirm(...)` dialog. Decline →
   session stops before any `SCRIPT DEBUG`. Accept → proceed; banner states "server blocked; writes
   committed".

### Example 3: Mid-pause Redis command
1. Paused at a breakpoint; user types `HGETALL user:1` in the "Redis" tab → `LuaLdbRedisConsoleTab.submit`
   → `controller.redisCommand(["HGETALL","user:1"])` → `LdbCommand.RedisCmd` → reply block →
   `RespReplyTreeConsole.showReply` [REDIS-01 §2.6] renders the tree. TC-INT-2.

## 6. Edge Cases
- **Step Out attempted** → no-op + one-time tooltip (§2.2); machine unchanged.
- **Breakpoint on a non-code line** → `canPutAt` false (§3.9); never sent as `break`.
- **Conditional breakpoint eval error** → treat as true, pause, show eval error (§3.7).
- **`SCRIPT DEBUG` rejected** (managed server) → actionable error, launch-local hint (§4.2).
- **Truncated/cyclic table in `print`** → partial parse with `truncated = true`, no throw (§3.4).
- **Forked-session server timeout** → `SessionEnded(FORK_TIMEOUT)`, clean message (§3.6).
- **Connection loss mid-session** → `RespException.Io` → reportError + teardown, no hang (§3.6).
- **Command issued after session end** → `onCommandSent` returns false, no-op (§3.5, TC-LDB-SM-2).
- **Rerun (restart)** → the platform starts a fresh session (new `RespClient`); LDB `restart` is not
  used (a clean new `EVAL` is simpler and avoids LDB's `restart` state quirks). Documented in §9.

## 7. Integration Points

```xml
<!-- plugin.xml (append near the existing debug block, plugin.xml:511-515) -->
<extensions defaultExtensionNs="com.intellij">
  <programRunner
      implementation="net.internetisalie.lunar.redis.debug.LuaRedisDebugRunner"/>
  <xdebugger.breakpointType
      implementation="net.internetisalie.lunar.redis.debug.LuaLdbBreakpointType"/>
</extensions>
```
- `LuaRedisDebugRunner` **[NEW]** registered as a `<programRunner>` (shape from `plugin.xml:514`
  `LuaDebugRunner`); its `canRun` gates on `DefaultDebugExecutor` + `LuaRedisRunConfiguration`
  [REDIS-01 §2.8], so it is selected only for the Debug executor on a Redis Script config and never
  collides with `LuaDebugRunner` (standard Lua) or the coverage runner.
- `LuaLdbBreakpointType` **[NEW]** registered as `<xdebugger.breakpointType>` (shape from
  `plugin.xml:515` `LuaLineBreakpointType`); a **distinct** type id `"redis-lua-line"` so Redis-script
  breakpoints are managed independently of MobDebug's `"lua-line"`.
- **Reused, no new registration**: `LuaDebuggerEditorsProvider` [`run/`], `RespReplyTreeConsole`
  [REDIS-01 §2.6], `notification.group.lunar.debugger` [`plugin.xml:532`].
- **Redis console tab**: the "Redis" tab is added programmatically in `LuaRedisDebugProcess.createConsole`
  via the session's `RunnerLayoutUi` (`session.ui` / `RunContentDescriptor`), not a plugin.xml EP —
  matching how the debugger UI adds custom content (`com.intellij.execution.ui.RunnerLayoutUi.createContent`
  [grounded, `app.jar`]).
- **Run-config options amendment**: REDIS-01's `LuaRedisRunConfigurationOptions` gains one `string()`
  `debugMode` field (§2.9) — additive; see §11 amendment A2.

## 8. Requirement Coverage

| Acceptance criterion | Priority | Implemented by (section) | Tests |
|----------------------|----------|--------------------------|-------|
| AC-1 Debug executor starts LDB session (SCRIPT DEBUG + EVAL, fork/sync) | M | §2.1, §2.2, §2.3, §2.10, §3.1, §3.2, §3.5 | TC-LDB-ENC-1, TC-LDB-SYNC-2, TC-LDB-DEC-1..3, TC-LDB-SM-1, TC-INT-1 |
| AC-2 Line breakpoints (`break <line>`, add/remove, invalid lines) | M | §2.4, §2.5, §3.2, §3.5, §3.9 | TC-LDB-ENC-2, TC-LDB-BP-1, TC-INT-1 |
| AC-3 Conditional breakpoints (IDE-side eval) | M | §3.7 | TC-LDB-COND-1 |
| AC-4 Step/Resume/Stop/Rerun; Step Out disabled | M | §2.2, §3.5 | TC-LDB-SM-1, TC-LDB-SM-2, TC-LDB-STEPOUT-1, TC-INT-1 |
| AC-5 Variables view from `print`; watches/Evaluate via `eval` | M | §2.6, §2.9, §2.12, §3.4 | TC-LDB-PRINT-1, TC-LDB-PRINT-2, TC-INT-1 |
| AC-6 Redis console tab (`redis <cmd>`, reply tree) | M | §2.7, §3.2 | TC-INT-2 (+ REDIS-01 TC-CON-1 tree) |
| AC-7 Error surfacing (compile/eval/connection-loss) | M | §2.2, §3.3, §3.6, §4.2 | TC-LDB-DEC-3, TC-LDB-ERR-1, TC-LDB-ERR-2 |
| AC-8 Sync-mode guarding (warning/confirm/banner; optional hard gate) | M | §2.13, §3.8 | TC-LDB-SYNC-1, TC-LDB-SYNC-2; checklist §3 |
| AC-9 Forked-mode banner + server-timeout as session-ended | M | §2.13, §3.6 | TC-LDB-DEC-2, TC-INT-3; checklist §3 |
| AC-10 Dual-flavor integration tests | M | §3.1, Phase 5 (impl-plan) | TC-INT-1, TC-INT-2, TC-INT-3 |

## 9. Alternatives Considered
- **Reuse the MobDebug `LuaDebugConnection` transport** — rejected (epic RISK-R09): LDB speaks RESP,
  not DBGp; the REDIS-01 `RespClient` is the correct, already-hardened transport. Sharing DBGp code
  would import its byte/char-framing defects.
- **Server-side conditional breakpoints** — not available in LDB; IDE-side `eval` gate (§3.7) is the
  only option.
- **LDB `restart` for Rerun** — rejected in favor of a fresh `EVAL` (§6) to avoid LDB restart-state
  quirks and keep the state machine linear.
- **One shared breakpoint type with MobDebug (`"lua-line"`)** — rejected: a distinct `"redis-lua-line"`
  type keeps the two adapters' breakpoints independent (a user debugging a Redis script shouldn't have
  MobDebug breakpoints silently applied and vice versa).
- **Poll for out-of-band stop events on a second connection** — rejected: LDB is synchronous per
  connection (§3.1); the stop reply *is* the response to the resuming command, so a single connection
  (with the §11 `readReply` amendment) suffices.

## 10. Open Questions

_None — feature has cleared the planning bar._ Deferred, tracked items (the one-click "switch to
local" affordance for RISK-R02; the `redis.debug.allowSyncOnRemote` hard-gate as a Should; live LDB
framing confirmation) are recorded in [risks-and-gaps.md](risks-and-gaps.md) as de-risking tasks
(DR-06/DR-07) and the epic register (RISK-R01/R02/R03).

## 11. Required REDIS-01 Seam Amendments

REDIS-02 needs the following from REDIS-01's public seams. Both are **additive and non-breaking**;
they are flagged here rather than silently redefined (per the briefing).

- **A1 — `RespClient.readReply()` (blocking, out-of-turn read).** REDIS-01 §2.3 exposes
  `suspend fun command(args): RespValue` (one write → one read). The LDB flow needs to read the
  **next reply block without sending a command** in one case: after the debugged `EVAL` is sent, the
  *first* stop block is the reply to `EVAL` (covered by `command`), but on some server/version
  variants an extra status line is emitted. To keep parsing robust, add
  `suspend fun readReply(): RespValue` to `RespClient` (a bare `RespCodec.decode(input)` on the same
  reader, cancellable, timeout-bounded) so `LuaLdbTransport` can read a trailing block. **Amendment
  request to REDIS-01 §2.3**: add `suspend fun readReply(): RespValue`. Low risk (it is the read half
  of `command`, already implemented internally). If REDIS-01 declines, `LuaLdbTransport` can instead
  send a no-op `LdbCommand.ListSource` (`whole`) to force a reply — a documented fallback, so this
  amendment is a *preference*, not a hard blocker.
- **A2 — `LuaRedisRunConfigurationOptions.debugMode`.** Add one `string("FORKED")` StoredProperty
  `debugMode` to REDIS-01's options (§2.8) and a `var debugMode: LuaRedisDebugMode` bridge on
  `LuaRedisRunConfiguration`, plus a "Debug mode: Forked / Sync (danger)" control in the REDIS-01
  `LuaRedisSettingsEditor`. Additive; the Run executor ignores it, the Debug executor reads it.
  **Amendment request to REDIS-01 §2.8** (and its settings editor). No behavior change to Run.

## See Also
- Requirements: [requirements.md](requirements.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Human checklists: [human-verification-checklists.md](human-verification-checklists.md)
- REDIS-01 design (seams): [../01-connections-run-config/design.md](../01-connections-run-config/design.md)
- Epic risks: [../redis-risks-and-gaps.md](../redis-risks-and-gaps.md)
</content>
</invoke>
