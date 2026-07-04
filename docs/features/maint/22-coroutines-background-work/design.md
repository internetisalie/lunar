---
id: "MAINT-22-design"
title: "MAINT-22 Design: Coroutines for Background Work"
type: "design"
parent_id: "MAINT-22"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-22 Design — Coroutines / Structured Concurrency

All symbols below are grounded against this repo at plan time (`file:line` where non-obvious). Platform
APIs (`readAction`, `Dispatchers.EDT`, `childScope`, service `CoroutineScope` injection) are 261-present;
their exact import paths are confirmed by the DR-01 compile smoke test (see [risks-and-gaps.md](risks-and-gaps.md)).

## 1. Build wiring (MAINT-22-01)

The IntelliJ Platform **bundles** `kotlinx-coroutines-core`. Plugins must compile against that copy and must
**not** add their own to the runtime classpath (two copies → `LinkageError`/`Already loaded` at runtime).

- Current [`build.gradle.kts:61`](../../../../build.gradle.kts) has `kotlinx-coroutines-core-jvm:1.7.1` only in
  **`integrationTestImplementation`** — leave that untouched (separate classpath).
- For `src/main`: **DR-01 — RESOLVED (Phase 0, 2026-07-04).** `import kotlinx.coroutines.CoroutineScope`
  compiles with **no** new dependency (`gce-builder run compileKotlin` → `BUILD SUCCESSFUL`); the platform
  artifact exposes coroutines transitively on the compile classpath. **No `build.gradle.kts` change** — and
  therefore **no double-bundle** (nothing was added to `implementation`/`api`; the platform's copy is the only
  one on the runtime classpath). The `compileOnly` fallback branch was not needed.

No `plugin.xml` change: the scope service is a **light `@Service`** (auto-registered).

## 2. Project scope service (MAINT-22-02)

New file `src/main/kotlin/net/internetisalie/lunar/util/LunarCoroutineScopeService.kt`:

```kotlin
package net.internetisalie.lunar.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class LunarCoroutineScopeService(val scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project): LunarCoroutineScopeService = project.service()
    }
}
```

The platform injects a project-lifecycle `CoroutineScope` into the light-service constructor (261 feature) and
cancels it on project dispose. Consumers either `launch` on `scope` directly (fire-and-forget prime) or derive a
`childScope` for a bounded sub-lifecycle (debug session).

`childScope` import: `com.intellij.platform.util.coroutines.childScope`
(signature `fun CoroutineScope.childScope(name: String, context: CoroutineContext = EmptyCoroutineContext, supervisor: Boolean = true): CoroutineScope`).

## 3. Debugger scope ownership (MAINT-22-05)

- [`LuaDebugProcess.sessionInitialized()`](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaDebugProcess.kt:92)
  currently wraps `controller.waitForConnect()` in a `Task.Backgroundable`. Replace with a launch on a
  **session child scope**:
  ```kotlin
  private val sessionScope =
      LunarCoroutineScopeService.getInstance(session.project).scope.childScope("LuaDebugSession")
  ```
  and pass `sessionScope` into `LuaDebuggerController`.
- `LuaDebuggerController.close()` calls `sessionScope.cancel()` (import `kotlinx.coroutines.cancel`) — this cancels
  the reader coroutine and fails all outstanding deferreds, **replacing** the manual
  `remaining.forEach { it.setError(...) }` sweep at [`LuaDebuggerController.kt:171-176`](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaDebuggerController.kt:171).
- We still surface connection progress via `withBackgroundProgress(project, "Connecting to debugger") { … }`
  (import `com.intellij.platform.ide.progress.withBackgroundProgress`) around `connect()`, preserving the UX of the
  removed `Task.Backgroundable`.

## 4. Transport rewrite — `LuaDebugConnection` (MAINT-22-03/-04)

Constructor gains the scope; the class keeps the same public *intent* but swaps primitives:

```kotlin
class LuaDebugConnection(
    private val socket: Socket,
    private val observer: LuaDebugObserver,
    private val scope: CoroutineScope,
) {
    private val reader = InputStreamReader(socket.inputStream).buffered(100 * 1024)
    private val writer = socket.outputStream
    private val charset = charset("UTF8")

    private val writeMutex = Mutex()                       // kotlinx.coroutines.sync.Mutex
    @Volatile private var pending: CompletableDeferred<String>? = null
    @Volatile private var pendingKind: DebugCommandKind? = null
    @Volatile private var running = false
    private var readerJob: Job? = null

    fun start() { readerJob = scope.launch(Dispatchers.IO) { readLoop() } }

    /** One command in flight at a time; suspends until the reader coroutine completes its deferred. */
    suspend fun send(command: DebugCommand): String = writeMutex.withLock {
        if (socket.isClosed) throw IOException("debugger connection closed")
        val deferred = CompletableDeferred<String>()
        pending = deferred
        pendingKind = command.kind
        withContext(Dispatchers.IO) {
            writer.write("$command\n".toByteArray(charset)); writer.flush()
        }
        deferred.await()
    }
```

### 4.1 Reader loop — replaces `run()` + `receive()` (preserve the exact state machine)

The current [`receive()`](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaDebugConnection.kt:266)
branches are reproduced **line-for-line** in `handleLine`; only the surrounding poll loop and `synchronized`
disappear. The reader coroutine is the **sole** owner of `reader`, so `readExactly` extended-payload reads stay inline.

```kotlin
private suspend fun readLoop() {
    try {
        while (currentCoroutineContext().isActive && !socket.isClosed) {
            val line = reader.readLine() ?: break        // blocking read on Dispatchers.IO — no sleep/poll
            handleLine(line)
        }
    } catch (e: IOException) {
        log.warn("readLoop IO: ${e.message}")
    } finally {
        pending?.completeExceptionally(IOException("connection closed"))
        observer.onDisconnected()
    }
}

private fun handleLine(line: String) {
    val status = DebuggerStatus.entries.firstOrNull { line.startsWith(it.message) }
        ?: throw IOException("unknown status: ${line.take(80)}")
    val data = line.removePrefix(status.message).removePrefix(" ").trimEnd('\n')

    val deferred = pending
    val kind = pendingKind
    val declared = kind?.responses ?: emptyMap()

    // Case A: response to the in-flight command (mirrors receive() lines 274-288)
    if (deferred != null && declared.containsKey(status)) {
        val payload = if (declared[status] == DebuggerResponseDataKind.Extended)
            reader.readExactly(data.toInt()) else data
        pending = null; pendingKind = null
        if (kind?.group == DebugCommandGroup.Run) running = true
        if (status.isError) deferred.completeExceptionally(DebuggerError(status, payload))
        else deferred.complete(payload)
        return
    }

    // Case B: out-of-band while running (mirrors receive() lines 290-334, unchanged parsing)
    if (running) {
        when (status) {
            DebuggerStatus.PausedBreakpoint -> { running = false; /* breakpointDataPattern → observer.onPauseBreakpoint */ }
            DebuggerStatus.PausedWatchpoint  -> { running = false; /* watchpointDataPattern → observer.onPauseWatchpoint */ }
            DebuggerStatus.ErrorInExecution  -> { running = false; val ext = reader.readExactly(data.toInt()); observer.onRunExecutionError(ext) }
            else -> log.error("unexpected running status: ${status.message}")
        }
        return
    }
    log.error("unhandled response: status=$status")
}
```

- `readExactly` (private extension, [lines 358-367](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaDebugConnection.kt:358)) is retained verbatim.
- `queue()` / the `commands: ArrayDeque` / `send()`/`current`/`started` fields are **deleted** — the `Mutex` +
  `pending` deferred subsumes them.
- New `DebuggerError(status, data): Exception` inner type carries the error status/payload for
  `deferred.completeExceptionally`, so the controller can map it to `XEvaluationCallback.errorOccurred`.

## 5. `LuaDebuggerController` rewrite (MAINT-22-04/-05/-06)

- Constructor: `class LuaDebuggerController(private val session: XDebugSession, private val scope: CoroutineScope)`.
- **Delete** `requests: MutableMap<DebugCommand, AsyncPromise<String>>` (line 53), `queueRequest`, `queueCommand`,
  and the `DebugObserver.onCommandComplete/onCommandCancelled` promise-resolution (lines 300-326) — correlation now
  lives entirely in `LuaDebugConnection` via the deferred.
- `waitForConnect()` → **`suspend fun connect()`** (MAINT-22-06):
  ```kotlin
  suspend fun connect() {
      val server = withContext(Dispatchers.IO) { ServerSocket(serverPort) }
      serverSocket = server
      val client = withTimeout(CONNECT_TIMEOUT_MS) { withContext(Dispatchers.IO) { server.accept() } }
      clientAddress = client.inetAddress
      val conn = LuaDebugConnection(client, DebugObserver(), scope).also { it.start() }
      connection = conn
      printToConsole("Debugger connected at $clientAddress", SYSTEM_OUTPUT)
      isReady = true
      setBaseDir()                                        // suspends until BASEDIR OK — no Thread.sleep(1000)
  }
  ```
- Command methods become **`suspend`** and delegate to `connection.send(...)`:
  - `suspend fun stepInto()/stepOver()/stepOut()/resume()` → `connection?.send(DebugCommand(STEP|OVER|OUT|RUN))`.
  - `suspend fun setBaseDir()` → `send(DebugCommand(BASEDIR, listOf(baseDir)))`.
  - `suspend fun addBreakPoint(bp)/removeBreakPoint(bp)` → keep the `LuaPosition` mapping (lines 227-245), then `send(SETB|DELB, pos.args())`.
  - `suspend fun execute(statement): LuaDebugValue` → `send(EXEC…)` then the existing `runReadAction { LuaDebugValueParser… }`
    body (lines 250-282) unchanged; return the value.
  - `suspend fun variables(): LuaRemoteStack` → `send(STACK)` then `runReadAction { LuaRemoteStack.create(...) }`.
- `DebugObserver.onPause` (lines 341-356) launches on the scope to fetch the stack without blocking the reader coroutine:
  ```kotlin
  fun onPause(pos: LuaPosition) = scope.launch {
      val stack = variables()
      val bp = myPos2Breakpoints[pos]
      if (bp != null) session.breakpointReached(bp, null, LuaSuspendContext(session.project, controller, bp, stack))
      else session.positionReached(LuaSuspendContext(session.project, controller, pos.localPosition(), stack))
  }
  ```
- `close()` (lines 151-177): unchanged socket teardown **plus** `scope.cancel()`; delete the AsyncPromise sweep.

## 6. Consumer integration

- [`LuaDebugProcess`](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaDebugProcess.kt):
  - Owns `sessionScope` (§3); constructs `LuaDebuggerController(session, sessionScope)`.
  - `sessionInitialized()`: replace the `Task.Backgroundable` block (lines 104-135) with
    `sessionScope.launch { withBackgroundProgress(session.project, "Connecting to debugger") { controller.connect(); session.rebuildViews(); registerBreakpoints(); controller.resume() } }` wrapped in the existing try/catch
    (error path keeps `Messages.showErrorDialog`, now via `withContext(Dispatchers.EDT)` instead of `SwingUtilities.invokeLater`).
  - `startStepOver/Into/Out`, `resume` (lines 52-71): `sessionScope.launch { controller.stepOver() }`, etc.
  - `registerBreakpoints()` (lines 145-163): the `while (!controller.isReady) Thread.sleep(100)` busy-wait is removed —
    `connect()` sets `isReady` before `resume()` in the same suspend chain, so breakpoints registered after connect are
    ordered by suspension, not by sleep. Pending breakpoints collected before connect are drained inside the connect launch.
  - `addBreakPoint/removeBreakPoint` (lines 165-177): `sessionScope.launch { controller.addBreakPoint(pos) }`.
- [`LuaDebuggerEvaluator`](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaDebuggerEvaluator.kt):
  the three `myController.execute(...).then { … callback.evaluated }` sites (lines 42, 55, 61) become
  ```kotlin
  scope.launch {
      try { callback.evaluated(myController.execute("return $expression")) }
      catch (e: DebuggerError) { callback.errorOccurred(e.message ?: "evaluation error") }
  }
  ```
  The evaluator takes the session scope via its constructor (currently `LuaDebuggerEvaluator(myController)`; add `scope`).

## 7. Rockspec prime (MAINT-22-07)

In [`RockspecSourcePathProvider`](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/RockspecSourcePathProvider.kt:23),
the EDT branch changes only its scheduling call:

```kotlin
if (app.isDispatchThread && !app.isUnitTestMode) {
    LunarCoroutineScopeService.getInstance(project).scope.launch {
        readAction { forceRefreshTracker.incModificationCount(); cache.value }
    }
    CachedValueProvider.Result.create(emptyList<SourcePathPattern>() to emptyList<CModuleRock>(),
        PsiModificationTracker.getInstance(project), forceRefreshTracker)
} else { /* unchanged */ }
```

`readAction` import: `com.intellij.openapi.application.readAction`. The `AppExecutorUtil`/`ReadAction` imports are
dropped from this file. **Not changed:** `LuaRockspecDiscoveryService.compute()`'s
`ReadAction.nonBlocking(...).executeSynchronously()` (it returns into a non-suspend `CachedValueProvider`; leave as-is,
add a one-line comment pointing here).

## 8. Data-model / API summary

| Symbol | Kind | Change |
|--------|------|--------|
| `LunarCoroutineScopeService` | new `@Service(PROJECT)` | holds injected `CoroutineScope` |
| `LuaDebugConnection` | rewritten | `+scope` ctor; `start()`, `suspend send()`, `readLoop()`, `handleLine()`; `-run/queue/receive/send/current/started/commands`; `+DebuggerError` |
| `LuaDebuggerController` | rewritten | `+scope` ctor; `suspend connect()` (was `waitForConnect`); command methods `→ suspend`; `-requests` map, `-queueRequest/queueCommand` |
| `LuaDebugProcess` | edited | `+sessionScope`; `Task.Backgroundable`→`launch{withBackgroundProgress}`; step/resume/bp `→ launch`; `-Thread.sleep` |
| `LuaDebuggerEvaluator` | edited | `+scope`; `Promise.then`→`launch{callback.evaluated}` |
| `RockspecSourcePathProvider` | edited | prime `→ scope.launch{readAction{}}` |
| `docs/engineering-contract.md` | edited | `+`"Coroutines & structured concurrency" section |

## Open Questions
_None._ (Coroutines Gradle wiring is tracked as de-risking task MAINT-22-00-DR-01 in risks-and-gaps.md, resolved in Phase 0 — not an implementer decision.)
