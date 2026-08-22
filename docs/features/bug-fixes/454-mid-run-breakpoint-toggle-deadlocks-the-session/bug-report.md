---
id: "BUG-454"
title: "Toggling a breakpoint while the program is running wedges the debug session — every later command queues behind an answer that never comes"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-454: an unanswered `SETB` holds `writeMutex` for the rest of the session

Found 2026-08-22 by the [[DEBUG-01]] and [[DEBUG-13]] retroactive-requirements agents
independently; DEBUG-13's agent proved the no-reply half by driving a real debuggee.

## 1. Reproduction

1. Start a Lua debug session and let the program run (do not pause).
2. Click the gutter to add or remove a breakpoint while it is running.
3. Wait for it to hit a breakpoint, then press Resume or Step.

## 2. Expected vs actual

- **Expected**: the breakpoint takes effect without a restart, and Resume works.
- **Actual**: the IDE shows the session paused and **no button does anything**. Nothing is logged
  and nothing is shown to the user.

## 3. Root cause — two halves that only bite together

**mobdebug sends no reply.** A `SETB`/`DELB` that arrives while the program is running is applied
from inside the debug hook (`is_pending` then `handle_breakpoint`, `src/main/lua/mobdebug/init.lua`)
and **no response is written**. This is not a defect in mobdebug — upstream's own client carries
separate `asetb`/`adelb` commands that skip the receive, precisely because of it. Measured against
a real debuggee: 3-second timeout, no line.

**Lunar awaits inside the lock** (`run/LuaDebugConnection.kt:234-244`):

```kotlin
writeMutex.withLock {
    val deferred = CompletableDeferred<String>()
    deferred.await()          // inside the lock, and with no timeout
}
```

The command never completes, so the mutex is never released, so Resume, Step and the `STACK` fetch
for the next pause all block forever. `LuaDebugProcess.addBreakPoint` dispatches whenever
`controller.isReady`, which is true from connect onwards — nothing gates it to a paused session.

The pause event still arrives, because it falls through to the out-of-band branch. That is why the
symptom is "shows paused, nothing works" rather than a visible hang.

## 4. Fix strategy

**Use the async command form for mid-run breakpoint changes**, mirroring upstream's `asetb`/`adelb`:
send and do not await while the session is running. That is the shape the protocol was designed for.

Two hardening measures worth taking with it, each independently valuable:

- **Do not await inside `writeMutex`.** Hold the lock for the write, release it, then await. A
  command that never answers should cost that one command, not the session.
- **Give the await a timeout.** A response that never arrives is a state Lunar can observe and
  report; silence is currently indistinguishable from a hang.

## 5. Test strategy

`TestLuaDebugHarness` already launches a real debuggee, so this is directly expressible: set a
breakpoint mid-run, then assert a subsequent command completes. Mutation-proof it — reinstate the
synchronous send and confirm the test times out rather than passing.
