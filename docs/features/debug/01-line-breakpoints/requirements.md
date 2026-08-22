---
id: DEBUG-01
title: "01: Line Breakpoints"
type: feature
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: DEBUG/RUN
folders: ["[[features/debug/requirements|requirements]]"]
---

# 01: Line Breakpoints

Let the user arm a line in a Lua file from the gutter, have the running debuggee stop there, and
have every breakpoint control the IDE offers — enable, mute, condition, log, temporary, dependent —
either work or be honestly absent.

## How these requirements were derived

**Not from Lunar's code.** A specification read off its own implementation cannot fail, which is the
defect that left [[DEBUG-07]] marked shipped for months (see [[BUG-450]] §4). The rows below come
from three external sources, and Lunar was checked *against* them afterwards:

1. **The IntelliJ platform contract** — every method `XLineBreakpointType`, `XBreakpointType`,
   `XLineBreakpoint` and `XBreakpointHandler` expose is a question this feature must answer, either
   "we provide it" or "we deliberately do not". Read from real source at
   `platform/xdebugger-api/src/com/intellij/xdebugger/breakpoints/`, plus the platform side of the
   bargain in `XDebugSessionImpl.kt` (`breakpointReached`, `MyBreakpointListener`,
   `handleBreakpoint`, `handleTemporaryBreakpointHit`) — which is what decides what a plugin gets
   for free and what it must do itself.
2. **The mobdebug wire protocol** — `src/main/lua/mobdebug/init.lua`. `SETB <file> <line>` and
   `DELB <file> <line>` are the *whole* breakpoint vocabulary: no condition, no hit count, no
   column. **A requirement the protocol cannot carry is a `Won't`, not a gap** — but a requirement
   the protocol *can* carry (via `EXEC`) and Lunar simply does not implement is a gap.
3. **[[plugin-feature-comparison]]** — the "Line breakpoints" row marks ✔ for all five plugins, so
   it discriminates nothing and was not used to set any priority. The neighbouring
   "Remote debugger ✗ (lunar)" row is what scopes `-22`.

Where a capability is unimplemented, the row distinguishes **Lunar does not implement it** from
**the user cannot do it**: the platform supplies working defaults for a surprising number of these
(temporary, dependent, muting, persistence, log-message, log-stack), and conflating the two would
manufacture gaps that are not there. Rows marked **[live]** are derived by reading the platform,
mobdebug and Lunar together and have *not* been confirmed against a running IDE.

## Requirements & Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `DEBUG-01-01` | **Type registered and scoped to Lua files** | **M** | **Full** | `<xdebugger.breakpointType implementation="…run.LuaLineBreakpointType"/>` at `plugin.xml:656`. Extends `XLineBreakpointTypeBase` with id `lua-line`, title `Lua Line Breakpoints`. `canPutAt` returns `false` unless `PsiManager.findFile(file)` is a `LuaFile`, so no other file type offers it. |
| `DEBUG-01-02` | **Executable statement lines are breakable** | **M** | **Full** | `canPutAt` walks up from each leaf on the line while the ancestor still *starts* on that line, and accepts if the outermost such ancestor is a `LuaStatement`. The platform calls it under an asserted read action (`XDebuggerUtilImpl.toggleAndReturnLineBreakpoint` → `assertReadAccessAllowed()`), so the PSI access is contract-safe. |
| `DEBUG-01-03` | **Comment lines are not breakable** | **M** | **Full** | Any leaf with a `PsiComment` ancestor is skipped, so a whole-line `--` comment and a trailing comment on an otherwise empty line both refuse the gutter. |
| `DEBUG-01-04` | **Blank lines are not breakable** | **M** | **Full** | Two independent reasons, both real: a whitespace-only line hits the `PsiWhiteSpace` guard, and a *truly* empty line never reaches the guard at all — `XDebuggerUtilImpl.iterateLine` computes `lineStart == lineEnd` and its `while (offset < endOffset)` loop never invokes the processor, leaving the `Ref` at its `false` default. |
| `DEBUG-01-05` | **Continuation lines of a multi-line statement are breakable** | **S** | **Not Implemented** | The gutter refuses `a = 1,` inside a multi-line table constructor, and refuses a wrapped argument line, because the outermost ancestor *starting on that line* is a field/expression rather than a `LuaStatement`. Lua's `line` hook does fire on those lines, so mobdebug would honour a breakpoint there. The sibling `redis/debug/LuaLdbBreakpointType` — written later, from this one — accepts them, using `PsiTreeUtil.getParentOfType(element, LuaStatement::class.java, false)` instead of the line-bounded walk. **[live]**; no test exercises `LuaLineBreakpointType.canPutAt` at all. |
| `DEBUG-01-06` | **Breakpoint is described in the Breakpoints dialog** | **S** | **Full** | `getDisplayText` renders `Line <n> in file <path>` with a 1-based line and a system-dependent path, overriding the platform's ellipsised default. Covered by `TestLuaLineBreakpointType.testGetDisplayTextIsOneBased`. |
| `DEBUG-01-07` | **Breakpoints set before launch are armed at session start** | **M** | **Full** | The platform registers every existing breakpoint at session init, before the socket exists; `LuaDebugProcess.addBreakPoint` queues into `installedBreaks` while `controller.isReady` is false and `drainInstalledBreakpoints()` flushes them after `connect()`. Asymmetric, and worth recording: `removeBreakPoint` has **no** `isReady` guard and does not drain the queue, so a breakpoint deleted during the connect window both throws `IOException("debugger connection closed")` inside `sessionScope` and is still armed by the drain. **[live]** |
| `DEBUG-01-08` | **Add/remove while paused takes effect without a restart** | **M** | **Full** | `XDebugSessionImpl.MyBreakpointListener` calls `processAllHandlers` on add/remove, and `breakpointChanged` is implemented as remove-then-add, so `LuaLineBreakpointHandler` receives every mid-session edit. While the debuggee is parked in mobdebug's `debugger_loop`, `SETB`/`DELB` are answered `200 OK` (`init.lua:800-816`) and `LuaDebugConnection.send` completes normally. |
| `DEBUG-01-09` | **Add/remove while the debuggee is *running* takes effect** | **M** | **Not Implemented** | The protocol supports it and Lunar's transport cannot use it. mobdebug applies a mid-run `SETB`/`DELB` from inside the debug hook (`is_pending` → `handle_breakpoint`, `init.lua:494,668`) and **sends no reply** — which is why upstream's own client has separate `asetb`/`adelb` commands that skip the `receive`. `LuaDebugConnection.send` publishes a `CompletableDeferred` and awaits it *while holding* `writeMutex`, so the unanswered `SETB` never completes and every later command — including Resume and Step — blocks behind the mutex for the rest of the session. The pause event itself still arrives (it falls through to the out-of-band branch), so the symptom is "the IDE shows paused and no button works". **[live]**; recorded nowhere else in the repo. |
| `DEBUG-01-10` | **Enable/disable a breakpoint, and Mute All Breakpoints** | **S** | **Full** | Not implemented by Lunar and not needed: `isBreakpointActive` gates on `!areBreakpointsMuted() && isEnabled()`, and the platform re-drives `registerBreakpoint`/`unregisterBreakpoint` accordingly. Correct *because* `unregisterBreakpoint` ignores its `temporary` flag and always sends a real `DELB` — a "disable rather than remove" optimisation has nothing to hook into here. Inherits `-09`'s constraint: safe while paused. |
| `DEBUG-01-11` | **Conditional breakpoint** | **S** | **Not Implemented** | Worse than absent: the **Condition** field is present and Lua-aware, because `XLineBreakpointTypeBase` is constructed with `LuaDebuggerEditorsProvider`, so the user gets completion and highlighting while typing a condition that is never read. Nothing in `run/` references `conditionExpression`; `DebugObserver.onPause` calls `session.breakpointReached(bp, null, ctx)` unconditionally. **The protocol is not the obstacle** — `SETB` has no condition slot, but the platform expects the *process* to gate, and `redis/debug/LuaLdbController.conditionHolds` (line 285) already does exactly that IDE-side for the LDB adapter: eval, treat `false`/`nil` as not-holding, treat an eval failure as holding. `EXEC` is the mobdebug equivalent. |
| `DEBUG-01-12` | **"Evaluate and log" expression** | **S** | **Not Implemented** | `XDebugSessionImpl.breakpointReached` prints `evaluatedLogExpression` if the process supplies one — the platform never evaluates it itself. Lunar passes `null`, so a log expression typed into the breakpoint dialog produces no console output. Same `EXEC` round-trip as `-11` would supply it. |
| `DEBUG-01-13` | **"Breakpoint hit" message in the console** | **C** | **Full** | Not implemented by Lunar — `breakpointReached` prints `xbreakpoint.reached.text` plus a hyperlink to the source position when `isLogMessage()`. Depends on `-23`: an unattributed hit takes the `positionReached` path and prints nothing. |
| `DEBUG-01-14` | **Log the stack when hit** | **C** | **Full** | Not implemented by Lunar — `XDebugProcess.logStack` defaults to `XDebuggerUtil.getInstance().logStack(...)`, which walks the suspend context Lunar already builds (`LuaSuspendContext` → `LuaExecutionStack`, [[DEBUG-02]]). `LuaDebugProcess` does not override it. |
| `DEBUG-01-15` | **A non-suspending breakpoint lets the program continue** | **S** | **Not Implemented** | The **Suspend** checkbox is always shown — `XSuspendPolicyPanel` hides only the All/Thread radios when `isSuspendThreadSupported()` is false — so a user can set `SuspendPolicy.NONE`. The platform then returns `false` from `breakpointReached` and expects the process to resume. Lunar discards the return value, so the debuggee stays halted in `debugger_loop` while the IDE never shows a paused state: a "log only" breakpoint hangs the program. |
| `DEBUG-01-16` | **Suspend one thread rather than all** | **W** | **Won't** | `XLineBreakpointTypeBase`'s two-argument super leaves `isSuspendThreadSupported()` false, which is the right answer: mobdebug hooks one debuggee, and Lua coroutines are not OS threads the debugger can suspend independently. The platform hides the radio group accordingly. |
| `DEBUG-01-17` | **Temporary breakpoint (removed once hit)** | **S** | **Full** | Not implemented by Lunar — `handleTemporaryBreakpointHit` installs a session listener that removes the breakpoint on resume, which reaches `unregisterBreakpoint` → `DELB`. Distinct from `runToCursor`, which Lunar implements separately with its own one-shot `DELB` in `DebugObserver.onPause`. The removal is dispatched from `doResume()` *before* `debugProcess.resume`, but both go through `sessionScope.launch`, so the ordering that keeps it on the safe side of `-09` is the scope's FIFO dispatch rather than anything explicit. **[live]** |
| `DEBUG-01-18` | **Dependent breakpoints ("disabled until X is hit")** | **C** | **Full** | Not implemented by Lunar — `getVisibleStandardPanels()` includes `DEPENDENCY` by default, and `processDependencies` drives `processAllHandlers` for slave breakpoints, so the `SETB`/`DELB` pair carries the semantics. Inherits `-09`. |
| `DEBUG-01-19` | **Breakpoints survive an IDE restart** | **S** | **Full** | Not implemented by Lunar — `XBreakpointManager` persists line breakpoints by file URL and line in the workspace; `createBreakpointProperties` returns `null` (via `XLineBreakpointTypeBase`), which is correct because Lunar stores nothing beyond file and line. |
| `DEBUG-01-20` | **The gutter distinguishes an armed breakpoint from an unarmed one** | **C** | **Won't** | `setBreakpointVerified` / `setBreakpointInvalid` / `getPendingIcon` exist for engines that can answer "is this line real?". mobdebug cannot: `set_breakpoint` stores whatever `file`/`line` it is given and answers `200 OK` for any well-formed pair, reserving `400 Bad Request` for a command whose regex does not match. A verified badge would assert something the protocol never establishes. Harmless today because `getPendingIcon()` defaults to `null`, so no misleading pending state is shown either. |
| `DEBUG-01-21` | **Inline (column) breakpoints on one line** | **C** | **Won't** | `computeVariants`, `getHighlightRange` and `supportsInterLinePlacement` are left at their defaults, which is the only correct choice: `SETB` addresses a `(file, line)` pair and mobdebug's `breakpoints[line][file]` table has no column dimension. |
| `DEBUG-01-22` | **Correct file path for a differently-rooted debuggee** | **S** | **Not Implemented** | `LuaPosition.createRemotePosition` makes the path relative to the run configuration's `workingDirectory`, forward-slashed, falling back to the absolute path when no relative path exists. That matches the debuggee only because `LuaDebuggerController.setBaseDir` sends the *same* IDE-side directory as `BASEDIR`, which mobdebug's `removebasedir` then strips from its own `debug.getinfo` source. For a debuggee rooted anywhere else — a container, another host — `BASEDIR` names a path that does not exist there, nothing is stripped, and every breakpoint silently never fires. There is no local↔remote root mapping anywhere in `run/` (`git grep -i 'pathmap\|remoteRoot\|localRoot'` is empty), and [[DEBUG-05]] is a placeholder, so this is unowned rather than deferred. |
| `DEBUG-01-23` | **A hit is attributed to the breakpoint that caused it** | **M** | **Partial** | `breakpointAt` is a `ConcurrentHashMap` lookup keyed on the `LuaPosition` data class, so attribution needs the path in `202 Paused <file> <line>` to be *byte-identical* to the path sent in `SETB`. It is, for a local run under `-22`'s assumption. When it is not, the failure is quiet rather than loud: `onPause` falls through to `session.positionReached`, so the IDE still stops at the right line but the platform never learns which breakpoint fired — costing `-13`, `-12`, `-17`'s auto-removal and `-15`'s policy check in one go. |
| `DEBUG-01-24` | **A gutter click picks the right one of two Lua breakpoint types** | **C** | **Not Implemented** | Two `<xdebugger.breakpointType>`s accept the same `.lua` lines — `lua-line` (`plugin.xml:656`) and `redis-lua-line` (`:657`) — and neither overrides `getPriority()`. `XDebuggerUtilImpl.getBreakpointTypeByPosition` compares with a strict `>` over `XBreakpointType.EXTENSION_POINT_NAME.extensionList`, i.e. declaration order, so the first-declared `lua-line` always wins and a gutter click can never produce a Redis-LDB breakpoint. [[REDIS-02]]'s design §9 explicitly chose a distinct type so the two adapters' breakpoints stay independent; that intent is not achieved at the gutter. **[live]** |
| `DEBUG-01-25` | **Hit counts / pass counts** | **W** | **Won't** | Not a platform standard panel — Java implements it through a custom properties panel plus engine support. mobdebug counts nothing: `has_breakpoint` is a boolean table lookup, so the count would have to be maintained IDE-side across round-trips the protocol does not provide. |

## Verification

`TestLuaLineBreakpointType` covers exactly one row, `-06` (TC-05d, the 1-based line in
`getDisplayText`); its other test asserts that the constructor returns non-null.
`TestLuaLineBreakpointHandler` asserts that `LuaLineBreakpointHandler::class.java` is not null — it
names no method on the class it is named after and **cannot fail**, so `-08` through `-10` have no
automated coverage at all. `TestLuaPosition` is the real coverage for the wire format behind `-22`
and `-23`: 0-based↔1-based line conversion, `args()` ordering, and the null-working-directory
fallback to an absolute path.

`-02` through `-05` — the whole `canPutAt` contract, and the only rows a user meets before starting
a session — are untested. The nearest thing is `TestLuaLdbBreakpointType` (TC-LDB-BP-1), which
asserts statement/comment/blank behaviour against `LuaLdbBreakpointType`; those assertions do **not**
transfer, because that type uses an unbounded `getParentOfType` where this one uses a line-bounded
walk, which is precisely the difference `-05` records.

Nothing here has been confirmed against a running IDE. `-05`, `-07`, `-09`, `-17` and `-24` are
reasoned from platform source, `mobdebug/init.lua` and Lunar's transport read side by side, and
`-09` in particular predicts a wedged session that a live MobDebug run would settle in one attempt.

**`-05`, `-07`, `-09`, `-11`, `-12`, `-15`, `-22`, `-23` and `-24` were found by writing this table**
and are recorded nowhere else in the repo. None has a bug report yet.
