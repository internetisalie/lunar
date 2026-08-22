---
id: DEBUG-03
title: "03: Step Over/Into/Out"
type: feature
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: DEBUG/RUN
folders: ["[[features/debug/requirements|requirements]]"]
---

# 03: Step Over/Into/Out

Drive a paused debug session forward one line, one call, or one frame at a time — and give every
other execution control the platform offers a definite answer: implemented, deliberately declined,
or impossible over this protocol.

## How these requirements were derived

**Not from Lunar's code.** A specification read off its own implementation cannot fail, which is the
defect that left [[DEBUG-07]] marked shipped for months (see [[BUG-450]] §4). The rows below come
from four external sources, applied in this order — Lunar's `LuaDebugProcess` /
`LuaDebuggerController` were read **last**, only to answer questions the sources had already posed:

1. **The `XDebugProcess` execution-control contract**, read from
   `platform/xdebugger-api/src/com/intellij/xdebugger/XDebugProcess.java` in `intellij-community`.
   Each of `startStepOver` / `startStepInto` / `startStepOut` / `startForceStepInto` / `resume` /
   `startPausing` / `runToPosition` / `stop`, plus `getSmartStepIntoHandler`, `getDropFrameHandler`,
   `getAlternativeSourceHandler`, `isLibraryFrameFilterSupported` and `checkCanPerformCommands`, is a
   question this feature must answer. **The defaults are not uniform, and the difference decides what
   "unimplemented" means for the user:**
   - `startStepOver` / `startStepInto` / `startStepOut` / `resume` / `stop` / `runToPosition` have
     **no** meaningful default — each delegates to a deprecated no-arg overload that
     `throw new AbstractMethodError()`. Not overriding one is a crash, not a gap.
   - `startForceStepInto` **does** have a meaningful default: it delegates to `startStepInto(context)`.
   - `startPausing` defaults to an empty body, but `XDebugSessionData.myPauseSupported` starts
     `false`, so `PauseAction.update` hides the action entirely until someone calls
     `XDebugSession.setPauseActionSupported(true)` — a silent no-op is *not* the failure mode.
   - `getSmartStepIntoHandler` / `getDropFrameHandler` / `getAlternativeSourceHandler` default to
     `null`, and each action's handler tests for it, so those actions disable themselves.
   - `isStepOutActionAllowed`, `isRunToCursorActionAllowed` and `isForceStepIntoActionAllowed` all
     default to **`true`** in `XDebugSessionImpl` — those three actions are live from the first pause
     whether or not the process backs them.
2. **mobdebug's stepping protocol**, read from `src/main/lua/mobdebug/init.lua`: `STEP` (step into,
   `step_into = true`), `OVER` (`step_over = true; step_level = stack_level`), `OUT` (as `OVER` but
   `step_level = stack_level - 1`), `RUN`, and the out-of-band `SUSPEND`. A control the protocol
   cannot support is a **Won't with a reason**, not a silent omission — and, symmetrically, a control
   the protocol *does* support is not excused by "mobdebug can't do it".
3. **[[plugin-feature-comparison]]** — which has **no stepping row at all**: its DEBUGGING block
   itemises Line breakpoints, Stack frames, Expression evaluation, Remote debugger and Profiler, and
   folds stepping into "Debugger ✔ (in-progress)". No competitive gap is derivable from it for this
   feature, which is itself worth recording rather than papering over.
4. **Executed protocol probes** (2026-08-22) — a Lua debuggee launched exactly as the plugin launches
   one (`LUA_INIT=@src/main/lua/debug.lua`, `LUNAR_DEBUGGER_PACKAGE=mobdebug`) against a hand-driven
   socket speaking the same command set as `LuaDebugConnection`. Every behavioural claim below that
   says "probe" was **run**, not read. Transcripts are quoted in the rows.

Where a capability is unimplemented, the row distinguishes **Lunar does not implement it** from
**the user cannot do it**: the platform supplies working defaults for several, and conflating the two
would manufacture gaps that are not there. Rows that a headless test cannot settle say so
explicitly — **most stepping behaviour is only observable in a live IDE session**, and admitting that
is the correct answer, not a hole in the specification.

## Requirements & Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `DEBUG-03-01` | **Step Over** | **M** | **Full** | `LuaDebugProcess.startStepOver` → `LuaDebuggerController.stepOver()` → `OVER`. Probe: paused at `prog.lua:10` (`local y = outer(x)`), `OVER` → `202 Paused prog.lua 11` — the call is entered and returned from without stopping inside it. |
| `DEBUG-03-02` | **Step Into** | **M** | **Full** | `startStepInto` → `STEP`. Probe from `prog.lua:10`: three `STEP`s → lines `6`, `2`, `3` — into `outer`, then into `inner`. `STEP` is unconditional "next line event anywhere", so it descends into any Lua function called on the current line. |
| `DEBUG-03-03` | **Step Out** | **M** | **Full** | `startStepOut` → `OUT`, which mobdebug implements as `OVER` with `step_level = stack_level - 1`. Probe: paused at `prog.lua:3` inside `inner`, `OUT` → `202 Paused prog.lua 7`, back in the caller `outer`. |
| `DEBUG-03-04` | **Resume** | **M** | **Full** | `resume` → `RUN`. Also the first command after connect (`sessionInitialized` calls `controller.resume()` once breakpoints are drained), so the same path is exercised on every session start. |
| `DEBUG-03-05` | **Stop the session** | **M** | **Full** | `stop()` sends `EXIT`, then destroys the process handler. The session itself is ended by the platform: `XDebugSessionImpl` registers its own `processTerminated` listener that calls `stopImpl()`, so `LuaDebugProcess` does not have to. |
| `DEBUG-03-06` | **Step Out at the outermost frame** | **M** | **Full** | `step_level` becomes lower than any reachable stack depth, so no `202 Paused` is ever sent and the program runs to completion — Step Out at the top frame is Resume-to-end, which is the platform-conventional behaviour. Probe: breakpoint at `prog.lua:9` (main chunk), `OUT` → no pause, program printed `result 4` and exited. |
| `DEBUG-03-07` | **Breakpoints still fire during a step** | **M** | **Full** | mobdebug's `getin` condition ORs `has_breakpoint(file, line)` with the step state, so a step is interruptible. Two probes: `OUT` from `prog.lua:9` with a breakpoint at `11` → `202 Paused prog.lua 11` (not run-to-end); `OVER` at `prog.lua:10` with a breakpoint at `2`, inside the function being stepped over → `202 Paused prog.lua 2`. |
| `DEBUG-03-08` | **Debuggee terminates mid-step** | **M** | **Full** | The step's `200 OK` completes the in-flight `CompletableDeferred`; the awaited `202 Paused` never arrives and the socket closes instead. `LuaDebugConnection.readLoop` sees EOF, fails any pending deferred with `IOException("connection closed")`, and calls `onDisconnected` → `LuaDebuggerController.close()`; the platform's own process listener ends the session. No orphaned coroutine: `close()` cancels the session `childScope`. |
| `DEBUG-03-09` | **Execution point moves to the stepped-to line** | **M** | **Partial** | **Needs a live IDE session to confirm; flagged as a probable defect, not asserted.** A breakpoint pause goes through `session.breakpointReached(bp, …)` and uses the breakpoint's own position, so it highlights. A *step* goes through `positionReached` with `LuaPosition.localPosition()`, which is `LocalFileSystem.findFileByPath(path)` on the **basedir-stripped, relative** path mobdebug returns — probe-confirmed as bare `prog.lua` whenever the script sits under the working directory `BASEDIR` sends. That position becomes `LuaSuspendContext`'s top frame, and `XDebugSessionImpl.updateSuspendContext` takes `currentStackFrame` / `getTopFramePosition()` straight from it. The **Frames pane is unaffected** — `LuaExecutionStack.computeStackFrames` resolves each frame from `LuaRemoteStackFrame.path`, index 6 of the `STACK` payload, which is absolute. So the predicted symptom is narrow and easy to miss: correct frames, no editor navigation on step. |
| `DEBUG-03-10` | **No control blocks the EDT** | **M** | **Full** | Every override is `sessionScope.launch { … }` onto the `LunarCoroutineScopeService` child scope; the socket write is `withContext(Dispatchers.IO)` inside `LuaDebugConnection.send`, serialized by `writeMutex`. Required by the engineering contract, which forbids DBGp/TCP loops on the EDT. |
| `DEBUG-03-11` | **Stack and variables refresh after each step** | **M** | **Full** | `DebugObserver.onPause` issues `STACK` (`controller.variables()`) before constructing the `LuaSuspendContext`, so the frames and scopes shown are the post-step ones. The tree's *expansion state* does not survive the step — that is [[DEBUG-02]] `-09` (`XStackFrame.getEqualityObject` is never overridden), not a defect of this feature. |
| `DEBUG-03-12` | **A control is disabled, not silently inert, when it cannot work** | **M** | **Partial** | Mostly platform-supplied and correct: `XDebuggerSuspendedActionHandler.isEnabled` gates Step Over/Into/Out, Force Step Into and Run to Cursor on `session.isSuspended`, so none is clickable while running. **The gap is `checkCanPerformCommands`, which Lunar does not override and which therefore always returns `true`.** Once the connection has dropped but the session still believes it is suspended, a step is a silent no-op — `sessionScope` is already cancelled by `close()`, so the `launch` body never runs and nothing reaches the console or the session. The honest window is small (the platform stops the session on process termination) but it is not empty for a remote target ([[DEBUG-05]]) that outlives its socket. |
| `DEBUG-03-13` | **Run to Cursor** | **S** | **Partial** | Implemented, and not by the platform: `runToPosition` → `controller.runToCursor(pos)` arms a temporary `SETB` and sends `RUN`, then `DebugObserver.onPause` removes it with `DELB`. **The one-shot removal is conditional on `pendingRunToCursor == pos`, so any pause that is *not* the cursor line leaves the temporary breakpoint armed and `pendingRunToCursor` set.** Probe reproducing exactly that command sequence: user breakpoint at `6` and `2`, paused at `6`, `SETB prog.lua 7` + `RUN` → `202 Paused prog.lua 2` (the user breakpoint), then a plain `RUN` → `202 Paused prog.lua 7` — a pause on a line with no breakpoint in the gutter, and the arming is never undone. |
| `DEBUG-03-14` | **Pause a running program** | **S** | **Not Implemented** | `startPausing` is not overridden, and `LuaDebuggerController.init` explicitly calls `session.setPauseActionSupported(false)` — so the Pause action is hidden rather than broken, which is the right failure mode for something unimplemented. **The protocol is not the obstacle:** probe — with the debuggee in a long loop under `RUN`, sending `SUSPEND` returned `202 Paused busy.lua 5`. There is no `SUSPEND` entry in `DebugCommandKind`, so this is missing at the transport layer too. Two real caveats for whoever implements it: mobdebug only polls the socket every `mobdebug.checkcount` (200) line events, and a debuggee blocked in a C call emits no line events at all, so pause is best-effort. |
| `DEBUG-03-15` | **Step into a coroutine** | **S** | **Not Implemented** | Probe: `STEP` at `coroutine.resume(co, 5)` went `6` → `7`, stepping *over* the coroutine body. A breakpoint inside that body (line `2`) did not fire either. mobdebug ships the fix — `mobdebug.coro()` wraps `coroutine.create` so each coroutine calls `mobdebug.on()` — but `src/main/lua/lunar/debug.lua` calls only `debugger.start(host, port)` and never `coro()`. Note this is *not* the same as `OVER`/`OUT` being coroutine-scoped (`step_over == (coroutine.running() or 'main')`), which is correct behaviour and should be preserved. |
| `DEBUG-03-16` | **Stepping never descends into the debugger itself** | **S** | **Full** | mobdebug's `debug_hook` returns early on `in_debugger()`, so `STEP` cannot land in `mobdebug/init.lua` or `lunar/debug.lua`. Observed across every probe: no pause was ever reported in a debugger source file. Supplied by the vendored debuggee, not by Lunar — a future replacement transport would have to re-establish it. |
| `DEBUG-03-17` | **Force Step Into** | **C** | **Full** | *Not* implemented by Lunar — `startForceStepInto` is left at the platform default, which delegates to `startStepInto(context)`, and `isForceStepIntoActionAllowed` defaults to `true` so the action is live. That is the correct semantics here: "force" means entering a *suppressed* call, and Lunar declares no suppression (`isLibraryFrameFilterSupported` is left at `false`, see `-19`), so there is nothing for it to force past. Recorded as Full because the requirement is about the user; recorded here as a caveat because an override would replace working behaviour with a duplicate. |
| `DEBUG-03-18` | **Force Run to Cursor** (ignore breakpoints) | **C** | **Partial** | **Needs a live IDE session to confirm.** `ForceRunToCursorAction` reaches `XDebugSessionImpl.runToPosition(position, ignoreBreakpoints = true)`, which calls `setBreakpointsDisabledTemporarily(true)` — unregistering every breakpoint through `LuaLineBreakpointHandler` → `DELB` — *before* `debugProcess.runToPosition`, and re-registers them on the next `positionReached`. Lunar ignores the flag, which is correct division of labour. The hazard is ordering: each `DELB` and the `SETB`+`RUN` are **separate `sessionScope.launch` calls**, and `writeMutex` serializes them without ordering them, so the `RUN` may be written before the last `DELB`. |
| `DEBUG-03-19` | **Smart Step Into** | **C** | **Not Implemented** | `getSmartStepIntoHandler` is left at `null`, so `XDebuggerSmartStepIntoHandler.isEnabled` (`session.smartStepIntoHandlerEntry != null`) disables the action; plain Step Into is unaffected, since `XDebuggerStepIntoHandler.handleSimpleCases` falls through to `session.stepInto()` when fewer than two targets are offered. mobdebug has no targeted step — only "next line anywhere" — so an implementation would have to emulate one by repeated `STEP` round-trips until the desired frame is entered, at one network round-trip per Lua line. Cost unmeasured. |
| `DEBUG-03-20` | **Library-frame step filters** | **C** | **Won't** | `isLibraryFrameFilterSupported` is left at the platform default `false`, so the *Show Library Frames* toggle does not appear and stepping has no notion of a filtered frame. Lua has no library/user partition to derive one from: a `require`d module is ordinary Lua source, indistinguishable from project code except by path convention. Reconsider only if [[ROCKS]] gives the IDE an authoritative "this file came from a rock" signal. |
| `DEBUG-03-21` | **Drop Frame / Reset Frame** | **W** | **Won't** | `getDropFrameHandler` is left at `null`, so the action is absent. mobdebug exposes no command that unwinds a frame and resumes at the call site, and Lua's `debug` library offers no supported way to re-enter a running function — implementing it means patching the vendored debuggee, the same bar `DEBUG-02-14` declined for heap referrers. |
| `DEBUG-03-22` | **Stepping in an alternative source view** | **W** | **Won't** | `getAlternativeSourceHandler` models stepping through a disassembly or decompiled view alongside the source. Lua bytecode has no stable public listing format across 5.1–5.5, no line mapping back from `luac -l` output, and no user demand recorded anywhere in the roadmap. |

## Verification

There is **no automated test of stepping**, and no honest reading of the suite says otherwise.
`TestLuaDebugConnection.testDebugCommandToString` and
`TestLuaDebugConnectionParsing.testCommandToStringSingleAndArgs` /
`testCommandKindModel` assert only that `DebugCommand(DebugCommandKind.STEP)` serialises to `"STEP"`,
that `OVER` serialises to `"OVER"`, and that `STEP.group == DebugCommandGroup.Run` — string
formatting and enum shape, not stepping. `TestLuaDebuggerEvaluator` mentions `stepOver`/`stepInto`/
`stepOut` only as `TODO("not used")` stubs in a fake `XDebugSession`. Nothing else in
`src/test/kotlin` or `src/integrationTest/kotlin` sends `OVER`, `OUT` or `RUN`.

`TestLuaDebugHarness` is the gap-closer that already exists: `startLuaDebugHarness` launches a real
`~/bin/lua` debuggee with mobdebug injected and hands back a live `LuaDebugConnection`, but its one
test (`testBreakpointAndExec`) only sets a breakpoint and evaluates. Every probe transcript quoted
above — `-01`, `-02`, `-03`, `-06`, `-07`, `-13`, `-14`, `-15` — was produced by driving that same
protocol by hand and is directly expressible as a harness test.

`-09`, `-12` and `-18` cannot be settled headlessly at all: they are about editor navigation, action
enablement and coroutine launch ordering inside a running IDE, and each needs a VNC session of the
kind that produced [[DEBUG-02]]'s live evidence. Recording them as unconfirmed is the accurate state,
not a placeholder.

**`-09`, `-13`, `-15` and the unwired `mobdebug.coro()` were found by writing this table** and are
recorded nowhere else in the repo. None has a bug report yet.
