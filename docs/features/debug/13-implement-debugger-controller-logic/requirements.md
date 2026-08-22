---
id: RUN-13
title: "13: Implement Debugger Controller Logic"
type: feature
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: DEBUG/RUN
folders: ["[[features/debug/requirements|requirements]]"]
---

# 13: Implement Debugger Controller Logic

The mobdebug wire-protocol layer that sits beneath every other DEBUG feature:
`LuaDebuggerController`, `LuaDebugConnection` and `DbgpFraming`. It owns the socket, the command
vocabulary, the response state machine, and the threading and cancellation rules that keep a
protocol loop off the EDT.

## Is this a feature? — verdict

**It is not a user-facing feature, and it should not be written as one.** Every sibling in this
epic names a capability the user can point at — Line Breakpoints, Stack Frames & Variables, Step
Over/Into/Out. "Implement Debugger Controller Logic" names a *task*, and a task-shaped spec is what
let this document sit as a one-line placeholder marked `done`. It is also not listed in
[`docs/roadmap.md`](../../../roadmap.md) or [`docs/features.md`](../../../features.md); it exists
only as a directory.

**It is, however, worth keeping — re-scoped as the protocol layer's contract**, for one reason that
survives scrutiny: *no document in this repository states what that contract is.* [[MAINT-22]]
rewrote the transport onto coroutines, [[MAINT-24]] fixed a list of defects in it, and [[MAINT-13]]
added unit tests to it — a refactor, a defect list and a coverage sweep, none of which enumerates
the commands the debuggee accepts or the responses it can emit. That enumeration is the thing
DEBUG-01/02/03/04/05 all silently depend on, and writing it down is what turned up the two
unrecorded defects at the bottom of this page.

**Front-matter caveat.** `status: done` here means *shipped*, in the same sense as [[DEBUG-02]],
which is also `done` and also lists unimplemented rows — not that the table below is all `Full`. It
is not: four **Must** rows are `Not Implemented` and three are `Partial`, two of them
session-breaking. The status vocabulary has no value for "shipped with a broken Must", and
`in_progress` is unavailable because `scripts/lint_planning.py` requires a `design.md` this
retroactive document deliberately does not have. **Read the table, not the front-matter** — and if
the two ever disagree, the table is the one that was checked against the wire.

So the rows below are about **what this layer owes its callers**, not about what a user sees. Where
a row exists only because a sibling feature needs it, it **cross-references** that feature rather
than restating its requirements. Rejected alternatives: `cancelled` (would delete the only place the
wire contract is written down) and merging into [[MAINT-24]] (that feature is `done` and its scope
is a fixed list of review findings — reopening it to absorb a living contract would misrepresent
both).

## How these requirements were derived

**Not from Lunar's code.** A specification read off its own implementation cannot fail — the defect
that left [[DEBUG-07]] marked shipped for months ([[BUG-450]] §4). The rows come from three sources
external to the Kotlin, and Lunar was checked against them afterwards:

1. **The mobdebug wire protocol**, from the vendored debuggee
   [`src/main/lua/mobdebug/init.lua`](../../../../src/main/lua/mobdebug/init.lua) — which is the
   authority, because it is the program on the other end of the socket. `debugger_loop` (lines
   800–1041) enumerates every command it accepts: `SETB`, `DELB`, `EXEC`, `LOAD`, `SETW`, `DELW`,
   `RUN`, `STEP`, `OVER`, `OUT`, `BASEDIR`, `SUSPEND`, `DONE`, `STACK`, `OUTPUT`, `EXIT`, and an
   `else` arm that answers `400 Bad Request`. Its response vocabulary is `200 OK [payload|index]`,
   `201 Started <file> <line>`, `202 Paused <file> <line>`, `203 Paused <file> <line> <watch>`,
   `204 Output <stream> <bytes>`, `400 Bad Request`, `401 Error in Expression <bytes>` and
   `401 Error in Execution <bytes>`. Each pair is a row. Each pair Lunar does not handle is a gap.
2. **The IntelliJ threading contract** — [`docs/engineering-contract.md`](../../../engineering-contract.md)
   §1 (never block the EDT; read PSI in a read action) and §2 (coroutine boundaries, cancellation
   exhaustiveness). A protocol loop is precisely the component that contract was written for, and
   this package has just had a read-action defect fixed in it ([[BUG-414]], 2026-08-22).
3. **Robustness cases the protocol forces**, not chosen by us: the debuggee can die mid-command, a
   response can arrive for a command already cancelled, a frame can be malformed or truncated, a
   payload can be larger than expected, and a remote debuggee can want to reconnect.

### Executed protocol evidence

Claims about how mobdebug *behaves* were not read off the Lua — they were produced by driving a
real debuggee with a hand-written controller (`lua 5.4.7` + luasocket, 2026-08-22, four scenarios).
The transcripts settle several things a code read would have got wrong:

```text
--> BASEDIR /…/proto/                       <-- 200 OK
--> SETB target.lua 12                      <-- 200 OK            (sent while PAUSED)
--> RUN                                     <-- 200 OK            (ack)
                                            <-- 202 Paused target.lua 12   (second frame, later)
--> STACK                                   <-- 200 OK do local _={{{nil,"target.lua",…   (10067 bytes, INLINE)
--> STACK -- {maxlevel=1}                   <-- 200 OK do local _={{{nil,"target.lua",…   ( 2454 bytes)
--> EXEC return 1+1                         <-- 200 OK 29  +  29-byte body
--> EXEC return nosuchvar()                 <-- 401 Error in Expression 81  +  81-byte body
--> BOGUS                                   <-- 400 Bad Request
--> DONE                                    <-- (no response; socket closes)
--> SUSPEND                                 <-- (no direct response)  202 Paused target2.lua 7
--> SETW acc > 100                          <-- 200 OK 1
--> DELW 99                                 <-- 400 Bad Request
--> SETB target2.lua 10   (sent while RUNNING)  <-- NO LINE (3 s timeout) — never acknowledged
                                            <-- 204 Output stdout 16  + 16-byte body
```

Four of these are not inferable from the Kotlin: `STACK` answers **inline on the status line**
while `EXEC` answers **length-prefixed** (Lunar models this correctly); `maxlevel` really does bound
the payload, 10067 → 2454 bytes, so [[BUG-450]] is a matter of sending a parameter, not of patching
the debuggee; `SETB` while the debuggee is *running* is applied silently by `handle_breakpoint`
(init.lua:512–543) **with no reply at all**; and `204 Output` frames interleave with command
responses. In the probe transcript the un-consumed 204 body desynchronised every later frame by
one — the same failure mode Lunar's `handleLine` would hit.

## Requirements & Status

Priority is the layer's own: **M** means a sibling feature is incorrect without it.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `DEBUG-13-01` | **Base-directory handshake** | **M** | **Full** | `BASEDIR <dir>/` is sent immediately after accept, before any breakpoint. mobdebug strips this prefix from every path it reports, so the trailing slash is load-bearing; `LuaDebuggerController.init` appends it. Answered `200 OK`. |
| `DEBUG-13-02` | **Breakpoints applied while paused** | **M** | **Full** | `SETB <file> <line>` / `DELB <file> <line>`, 1-based line, path relative to `BASEDIR`. Both answer `200 OK` synchronously; malformed arguments answer `400 Bad Request`. Serves [[DEBUG-01]]. |
| `DEBUG-13-03` | **Breakpoints applied while running** | **M** | **Not Implemented** | The debuggee accepts `SETB`/`DELB` mid-run through `handle_breakpoint`, which applies them and **sends nothing back** — proved by probe (3 s, no line). `LuaDebugConnection.send` awaits a `CompletableDeferred` *while holding `writeMutex`*, so a breakpoint toggled during execution never completes and every later command — resume, step, `STACK` for the next pause — blocks behind it. See "Gaps recorded nowhere else" §1. Needs a live/harness session to confirm the Kotlin half. |
| `DEBUG-13-04` | **Execution control is two-phase** | **M** | **Full** | `RUN`/`STEP`/`OVER`/`OUT` answer `200 OK` at once, then a *second, later* frame when the debuggee stops. `handleLine` completes the command's deferred on the ack and sets `running = true`; the pause arrives through `LuaDebugObserver`. A one-response-per-command transport would deadlock here. Serves [[DEBUG-03]]. |
| `DEBUG-13-05` | **Stack retrieval** | **M** | **Full** | `STACK` answers `200 OK <serpent-serialised table>` **inline on the status line** (serpent `compact = true`, so never multi-line). Modelled as `DebuggerResponseDataKind.Immediate` — correct, and the one place the protocol is asymmetric with `EXEC`. Serves [[DEBUG-02]]. |
| `DEBUG-13-06` | **Payloads are bounded at the source** | **M** | **Not Implemented** | `STACK` accepts a trailing `-- {maxlevel=N, maxnum=N, maxlength=N, nocode=…, sparse=…}` table; Lunar sends `STACK` bare, so the debuggee serialises to unbounded depth. Measured: 10067 bytes bare vs 2454 with `maxlevel=1` on a trivial script. [[BUG-450]] is the evidence and owns the fix. |
| `DEBUG-13-07` | **Expression evaluation** | **M** | **Full** | `EXEC <chunk>` answers `200 OK <bytes>` + body, or `401 Error in Expression <bytes>` + body, which `handleLine` converts into a `DebuggerError`. Newlines in the chunk must be sent as the protocol's safe-whitespace escape, never raw. Serves [[DEBUG-04]]. |
| `DEBUG-13-08` | **Evaluate against a chosen frame** | **S** | **Not Implemented** | `EXEC` takes the same trailing-table syntax with a `stack=N` key, and the debuggee re-captures variables at that level (`capture_vars(stack-1, coro_debugee)`). Lunar always sends `EXEC` bare, so a watch or evaluation is answered against the *innermost* frame regardless of which frame is selected in the Frames pane. |
| `DEBUG-13-09` | **Suspend a running debuggee** | **S** | **Not Implemented** | `SUSPEND` takes no reply of its own; the pending run command's second frame becomes `202 Paused` — probe-confirmed, and it landed within one tick. `SUSPEND` is absent from `DebugCommandKind` and `LuaDebuggerController.init` calls `session.setPauseActionSupported(false)`, so the Pause button is greyed out although the protocol supports it. Caveat: the debuggee only polls the socket every `mobdebug.checkcount` (200) line events, so a suspend is not instantaneous and never arrives inside a blocking C call — an implementation needs a timeout and user feedback, not just the command. |
| `DEBUG-13-10` | **Watch expressions** | **C** | **Not Implemented** | `SETW <expr>` answers `200 OK <index>`; `DELW <index>` answers `200 OK`, or `400 Bad Request` out of range; a fired watch pauses with `203 Paused <file> <line> <index>`. `DebugCommandKind.SETW`/`DELW` are declared with the right response shapes and `handleLine` decodes `203` into `onPauseWatchpoint` — but **nothing in the plugin ever sends either command**, so no watch can fire. Watches in the IDE go through `EXEC` re-evaluation instead ([[DEBUG-04]]); this row is about the debuggee-side conditional stop, which does not exist. |
| `DEBUG-13-11` | **Debuggee output over the debug channel** | **C** | **Not Implemented** | `OUTPUT stdout d\|c\|r` redirects the debuggee's `print` into `204 Output <stream> <bytes>` frames. Lunar never sends it and reads the process's own stdout pipe instead, which is a defensible choice — but it also means a `204` from `mobdebug.output(…)`, which any user script may call, is unhandled. See `DEBUG-13-14`. |
| `DEBUG-13-12` | **Session termination** | **M** | **Partial** | `EXIT` answers `200 OK` and is what `terminate()` sends — correct. `DONE` is also declared in `DebugCommandKind` **with a `200 OK` response it never gets**: the debuggee's `DONE` arm yields and returns without replying (probe: no line, socket closed). Nothing sends `DONE` today, so this is latent, but the enum states a contract the wire does not honour. |
| `DEBUG-13-13` | **Push a chunk to the debuggee (`LOAD`)** | **W** | **Won't** | `LOAD <bytes> <name>` (+ `201 Started <file> <line>`) exists for editors that run the buffer being edited rather than a file on disk — ZeroBrane's model. Lunar always launches a real script through a run configuration, so there is nothing to push. `DebuggerStatus.Started` is consequently unreachable dead code. |
| `DEBUG-13-14` | **Out-of-band frames are demultiplexed** | **M** | **Partial** | `202`, `203` and `401 Error in Execution` are correctly routed to the observer when `running`. `204 Output` is **not**: it matches a `DebuggerStatus` entry but no command declares it and the `running` branch has no arm, so it falls to `log.error` **without consuming its length-prefixed body** — the body bytes are then read as the next status line and the stream is permanently off by one. Reproduced in the probe transcript. `201 Started` falls through the same way (harmless: no body). |
| `DEBUG-13-15` | **Length prefixes count bytes, not characters** | **M** | **Full** | `DbgpFraming.readExactly` reads N raw bytes then decodes UTF-8 once, and loops until the count is satisfied. Reading characters under-reads a multibyte payload and desyncs permanently — [[MAINT-24]]-01, review finding #5. |
| `DEBUG-13-16` | **Inline and prefixed payloads are distinguished per command** | **M** | **Full** | `DebuggerResponseDataKind` is declared per `(command, status)` pair: `STACK`→`Immediate`, `EXEC`→`Extended`, `SETW`→`Immediate`, `RUN`/`STEP`/`OVER`/`OUT`→`None`. The mapping matches the debuggee for every command Lunar sends. |
| `DEBUG-13-17` | **Error statuses fail the command, never hang it** | **M** | **Full** | `DebuggerStatus.isError` (`code >= 400`) makes `handleLine` complete the deferred *exceptionally* with a `DebuggerError` carrying status and payload, for `400 Bad Request` and both `401` variants. The caller sees an exception, not a stalled coroutine. |
| `DEBUG-13-18` | **An unparseable frame terminates instead of desyncing** | **S** | **Partial** | An unrecognised status line throws from `handleLine`, the reader coroutine's `catch (IOException)` exits the loop and `onDisconnected()` tears the session down — the right shape, since a desynced stream is unrecoverable. But it is logged at `info` with the same message as an ordinary close, so a protocol fault is indistinguishable from the debuggee exiting normally, and the user is told nothing. A malformed *length* (`data.toInt()`) fails the same way through the broader `catch (Exception)`. |
| `DEBUG-13-19` | **No protocol I/O on the EDT** | **M** | **Full** | The reader loop is `scope.launch(Dispatchers.IO) { readLoop() }`; `accept()` and the socket write are inside `withContext(Dispatchers.IO)`. Every caller enters through `sessionScope.launch { … }` from a `childScope("LuaDebugSession")` of `LunarCoroutineScopeService`. Contract §1/§2. |
| `DEBUG-13-20` | **Payload parsing takes a read action** | **M** | **Partial** | `execute()` and `variables()` do wrap `LuaDebugValueParser`/`LuaRemoteStack.create` in a read action, so the [[BUG-414]] class of defect is closed. But they use the **blocking** `ApplicationManager.getApplication().runReadAction<T>` from inside a suspend function, which parks a dispatcher thread behind a pending write action and is not cancellable; contract §2 names suspend `readAction { }` as the coroutine-correct form. |
| `DEBUG-13-21` | **Teardown cancels everything in flight** | **M** | **Full** | `close()` closes the server socket, closes the connection (unblocking the reader), then cancels the session scope last, which fails every outstanding `CompletableDeferred`. `readLoop`'s `finally` additionally completes the pending deferred exceptionally before notifying `onDisconnected()`. Idempotent via `@Synchronized`. |
| `DEBUG-13-22` | **Connecting is bounded** | **M** | **Full** | `ServerSocket.soTimeout = CONNECT_TIMEOUT_MS` (5 000). A blocking `accept()` is not interruptible by coroutine cancellation, so the socket timeout — not the scope — is the real bound; the timeout surfaces through `LuaDebugProcess.sessionInitialized`'s error dialog. |
| `DEBUG-13-23` | **Every command is bounded** | **M** | **Not Implemented** | `send()` is `writeMutex.withLock { … deferred.await() }` with **no timeout**. Any response the debuggee declines to send — `DEBUG-13-03` today, `DONE` if it is ever wired (`DEBUG-13-12`) — stalls the session with no error and no recovery short of stopping it. A bound belongs on the await, not on the caller. |
| `DEBUG-13-24` | **A cancelled command's late response is discarded** | **M** | **Not Implemented** | mobdebug carries no request identifier, so correlation is positional and the transport must police it. If `deferred.await()` is cancelled, `pending`/`pendingKind` are left set; the next `send()` overwrites them, and the *stale* response is then matched against the *new* command and delivered as its result — every subsequent response off by one. Derived by reading `LuaDebugConnection`; not yet exercised by a test. |
| `DEBUG-13-25` | **Debuggee death mid-command is reported** | **M** | **Full** | `DbgpFraming.readLine` returns null at EOF and `readExactly` throws `"connection closed after N of M bytes"` on a truncated body; either way `readLoop` exits, fails the pending deferred with `IOException("connection closed")` and calls `onDisconnected()`. `LuaDebugProcess` separately listens for `processTerminated` and calls `controller.terminated()`, so both orderings converge on `close()`. |
| `DEBUG-13-26` | **Reconnection** | **C** | **Not Implemented** | `connect()` accepts exactly one client; the `ServerSocket` is never re-armed and is closed on teardown. A remote debuggee that restarts, or a second one that attaches, is left in the backlog and never accepted — with no diagnostic. Relevant to [[DEBUG-05]], whose value is attaching to a process Lunar did not launch. |
| `DEBUG-13-27` | **One command in flight** | **M** | **Full** | `writeMutex` serialises `send()`, which is what makes positional correlation sound: the protocol has no request ids and mobdebug processes commands strictly in order. The single `pending`/`pendingKind` pair is a deliberate consequence, not an oversight — but it is also why `DEBUG-13-03` and `DEBUG-13-23` are session-wide failures rather than one lost command. |

## Verification

`TestLuaDebugHarness` is the only test that speaks the real protocol: it launches a Lua subprocess
with mobdebug injected, then drives `BASEDIR` → `SETB` (paused) → `RUN` → out-of-band pause →
`EXEC` → `RUN` over a live socket. It covers `-01`, `-02`, `-04`, `-05`, `-07` end to end and is the
natural home for the missing cases below. It needs a real `lua` on `PATH`.

`TestDbgpFraming` covers `-15` and the truncation half of `-25` without a socket: byte-count reads
of a multibyte payload, short-read failure, CR handling, EOF, zero-length bodies.
`TestLuaDebugConnection` and `TestLuaDebugConnectionParsing` cover the static half of `-02`, `-04`,
`-14` and `-16` — command serialisation, the `202`/`203` data patterns including paths with spaces
and rejection of malformed ones, and the `DebugCommandKind` response/arg model.

**Not covered by any test**, and each needing either a harness extension or a live session:

- `-03` — send `SETB` from `TestLuaDebugHarness` *after* `RUN` and before the pause, and assert
  `send()` completes. It currently would not; this is the reproduction for §1 below.
- `-06` — assert the `STACK` line carries a `-- {…}` parameter table, and measure the payload.
- `-14` — feed a `204 Output` frame into `handleLine` and assert the next frame parses.
- `-23`, `-24` — both are cancellation/timeout behaviours of `send()`; both are unit-testable
  against a scripted `InputStream` without a subprocess.
- `-08`, `-09`, `-10`, `-11`, `-26` — unimplemented; nothing to test yet.
- `-18`, `-20` — the shortfalls are diagnostics and threading discipline respectively, neither
  observable from a unit test. `-20` needs contention with a write action to show itself at all.

`TestLuaLineBreakpointHandler`, `TestLuaPosition` and `TestDebug04AcceptanceCriteria` exercise the
callers of this layer, not the layer itself, and are listed here only so they are not mistaken for
protocol coverage.

## Gaps recorded nowhere else

Both were found by writing this table, and neither has a bug report.

1. **A breakpoint toggled while the program is running bricks the session** (`DEBUG-13-03`,
   `DEBUG-13-23`). `LuaDebugProcess.addBreakPoint` dispatches straight to the controller whenever
   `controller.isReady`, which is true from connect onwards — including mid-run. mobdebug applies
   such a breakpoint silently and answers nothing (probe: 3 s, no line). `send()` holds `writeMutex`
   across an unbounded `await()`, so the resume, the step, and the `STACK` fetch for the *next*
   pause all queue behind a command that will never complete. The user sees a debugger that stops
   responding, with nothing in the log. The debuggee-side reply is not Lunar's to add; the fix is to
   treat `SETB`/`DELB` as fire-and-forget while `running`, which is exactly the distinction
   mobdebug's own reference controller draws between its `setb` and `asetb` commands.
2. **`204 Output` desynchronises the stream** (`DEBUG-13-14`). The status is recognised but its
   length-prefixed body is never consumed, so the body is read as the next status line. Latent
   today only because Lunar never sends `OUTPUT` — but `mobdebug.output` is public API that any
   debuggee script may call, and the probe shows what follows: every subsequent frame off by one,
   the `STACK` result delivered to the `RUN` that came after it.

Two smaller inconsistencies worth folding into whichever fix lands first: `DebugCommandKind.DONE`
declares a `200 OK` the wire never sends (`DEBUG-13-12`), and `SETW`/`DELW` are fully modelled but
unreachable because nothing sends them (`DEBUG-13-10`).
