---
id: "REDIS-02"
parent_id: "REDIS"
type: "feature"
status: "done"
folders:
  - "[[features/redis/requirements|requirements]]"
title: "REDIS-02: LDB Debug Adapter"
---

# REDIS-02: LDB Debug Adapter

**Requirement**: An XDebugger adapter for the Redis/Valkey server-side Lua debugger (LDB),
enabling breakpoint debugging of scripts executed via the REDIS-01 run configuration.
**Priority**: Must
**Status**: Implemented

---

## Overview

Redis and Valkey ship a built-in remote Lua debugger: after `SCRIPT DEBUG YES|SYNC`, an
`EVAL` on the same connection enters a stepping session driven by simple commands — the
protocol `redis-cli --ldb` speaks. No IDE integrates it; this feature maps it onto the
IntelliJ debugging UI as a second debug adapter alongside the MobDebug one.

**Protocol note:** the LDB wire behavior is defined by the redis-cli implementation rather
than a formal spec. The adapter is implemented against the redis-cli source and pinned by
integration tests against dockerized Redis and Valkey (both flavors gate the DoD).

### LDB ↔ XDebugger mapping

| LDB primitive | IDE feature |
| :--- | :--- |
| `break <line>` / `break -<line>` / `break 0` | add / remove / clear line breakpoints |
| `redis.breakpoint()` in script | pre-set breakpoint (documented; also used for conditional support) |
| `step` / `next` / `continue` | Step Into / Step Over / Resume (LDB has no step-out; action disabled) |
| `print` | Variables view (all locals in frame) |
| `print <var>` / `eval <expr>` | watches + Evaluate dialog |
| `redis <cmd>` | "Redis command" console tab while paused |
| `list` / `whole` | source sync check (IDE already has the file; used for line validation) |
| `abort` / `restart` | Stop / Rerun |
| session mode `YES` (forked) vs `SYNC` | run-config toggle (see below) |

### Session modes

- **Forked (default, `SCRIPT DEBUG YES`)**: server forks; all writes are rolled back on
  session end. Safe against shared servers. The UI labels the session "changes rolled back".
- **Sync (`SCRIPT DEBUG SYNC`)**: blocks the server event loop and commits writes. Guarded
  by a run-config checkbox with an explicit warning, and refused with a confirmation dialog
  when the connection is not a session-local ("launch local") server.

## Acceptance Criteria

- [ ] "Debug" executor on the REDIS-01 run configuration starts an LDB session over the
      REDIS-01 RESP client (`SCRIPT DEBUG` + `EVAL`), with fork/sync mode from the config
- [ ] Line breakpoints in the script file are registered via `break <line>` before `EVAL`
      resumes, and can be added/removed while paused; unresolvable lines (non-code) are
      marked invalid in the UI
- [ ] Conditional breakpoints supported (evaluated IDE-side on pause via `eval`; resume when
      false)
- [ ] Step Into / Step Over / Resume / Stop / Rerun mapped to `step`/`next`/`continue`/
      `abort`/`restart`; Step Out disabled with a tooltip (LDB limitation)
- [ ] Variables view shows all frame locals from `print` output (including table values
      pretty-parsed into expandable nodes); watches and Evaluate dialog use `eval`
- [ ] A "Redis" console tab executes `redis <cmd>` in the paused session and renders the
      reply tree
- [ ] Errors (script compile errors, `eval` failures, connection loss) surface in the debug
      session UI — never as silent hangs or IDE fatal-error reports; rejected evaluations
      call `errorOccurred`
- [ ] Sync mode: warning text on the config; confirmation dialog when the target connection
      is not session-local; session banner states writes will be committed
- [ ] Forked mode session banner states writes are rolled back; forked-session server
      timeout surfaced as a session-ended message, not an exception
- [ ] Engineering-contract compliance verified in review: no EDT blocking, no `!!` in
      protocol parsing, explicit charsets, all waits cancellable
- [ ] Integration tests against dockerized `redis:8` **and** `valkey/valkey:8`: breakpoint
      hit, step, locals, eval, redis command, abort, both session modes

### Acceptance-criterion IDs

The ten criteria above are referenced as **AC-1 … AC-10** in top-to-bottom order (AC-1 = Debug
executor starts an LDB session; AC-2 = line-breakpoint registration; AC-3 = conditional
breakpoints; AC-4 = step/resume/stop/rerun mapping + disabled Step Out; AC-5 = Variables view /
watches / Evaluate; AC-6 = Redis console tab; AC-7 = error surfacing; AC-8 = sync-mode guarding;
AC-9 = forked-mode banner + timeout; AC-10 = dual-flavor integration tests).

## Test Cases

Concrete input → expected output for every `Must` acceptance criterion. Each TC seeds the
automated test named in [implementation-plan.md](implementation-plan.md) "Verification Tasks";
the design section that specifies the behavior is cited in the last column. Purely-interactive
behaviors that cannot be exercised without a live IDE UI (breakpoint-hit visuals, banner text,
confirmation dialog, session lifecycle) are pinned in
[human-verification-checklists.md](human-verification-checklists.md) and cross-referenced from the
matrix. The LDB wire behavior itself is defined by the `redis-cli --ldb` implementation, so the
live-protocol TCs (`TC-INT-*`) run against dockerized servers and are the compatibility contract
(epic RISK-R01); the unit TCs pin the codec and state machine against a scripted fake transport.

| # | AC | Given (input) | When (action) | Then (expected) | Design |
|---|-----|---------------|---------------|-----------------|--------|
| TC-LDB-ENC-1 | AC-1 | `LdbCommand.Break(line = 12)`, `LdbCommand.Step`, `LdbCommand.Continue`, `LdbCommand.Print(varName = null)`, `LdbCommand.Eval("1+1")`, `LdbCommand.RedisCmd(listOf("GET","k"))`, `LdbCommand.Abort` | `LdbWire.encode(cmd)` for each | RESP array-of-bulk lines: `["break","12"]`, `["step"]`, `["continue"]`, `["print"]`, `["eval","1+1"]`, `["redis","GET","k"]`, `["abort"]` — encoded via the REDIS-01 `RespCodec.encodeCommand` (byte framing) | §3.2 |
| TC-LDB-ENC-2 | AC-2 | `LdbCommand.Break(line = 12)` then `LdbCommand.RemoveBreak(line = 12)` | `LdbWire.encode(cmd)` | `["break","12"]` then `["break","-12"]` (negative line = delete); clear-all = `["break","0"]` | §3.2, §3.5 |
| TC-LDB-DEC-1 | AC-1 | LDB step reply block (RESP array of status lines) `["* Stopped at 3, stop reason = step", "3   local x = 1"]` | `LdbReplyParser.parse(reply)` | `LdbStop(line = 3, reason = STEP, sourceLine = "local x = 1")` — the `* Stopped at <N>` line yields the 1-based server line; content line stripped of its leading line-number gutter | §3.3 |
| TC-LDB-DEC-2 | AC-1 | LDB session-end reply `["* Lua debugging session ended"]` (fork timeout variant: `["* Forked debugging session was closed"]`) | `LdbReplyParser.parse(reply)` | `LdbSessionEnded(reason = ENDED)` for the first; `LdbSessionEnded(reason = FORK_TIMEOUT)` for the second (matched by the `Forked` prefix) | §3.3, §3.6 |
| TC-LDB-DEC-3 | AC-1 | LDB compile-error reply `["* Error compiling script (new function): user_script:2: '=' expected near 'x'"]` | `LdbReplyParser.parse(reply)` | `LdbError(kind = COMPILE, message = "user_script:2: '=' expected near 'x'", scriptLine = 2)` — the `user_script:<N>` token is extracted for the error position | §3.3 |
| TC-LDB-PRINT-1 | AC-5 | LDB `print` reply lines `["<value> x = 10", "<value> t = {\"a\": 1, \"b\": {\"c\": 2}}"]` (redis-cli `<varname> = <value>` shape; tables as JSON-ish) | `LdbPrintParser.parseLocals(reply)` | `[LdbLocal(name="x", value=Scalar("10")), LdbLocal(name="t", value=Table([("a",Scalar("1")),("b",Table([("c",Scalar("2"))]))]))]` — nested table parsed into an expandable value tree | §3.4 |
| TC-LDB-PRINT-2 | AC-5 | LDB `print` reply for a deep/cyclic table truncated by server `maxlen` ending `… (truncated)` | `LdbPrintParser.parseLocals(reply)` | a `LdbLocal` whose value node carries `truncated = true`; the parser never throws on the incomplete suffix and never uses `!!` (contract §1) | §3.4, §6 |
| TC-LDB-SM-1 | AC-1, AC-4 | a fresh `LdbSessionMachine` in `HANDSHAKE`, fed the reply sequence: `SCRIPT DEBUG YES` → `+OK`; `EVAL …` → step-stop at line 1 | `machine.onReply(...)` for each | transitions `HANDSHAKE → ARMED` on `+OK`, then `ARMED → PAUSED(line=1)` on the first stop; exposes `currentLine = 1` | §3.5 |
| TC-LDB-SM-2 | AC-4 | a `PAUSED` `LdbSessionMachine`, then `LdbCommand.Continue` sent and the server replies `LdbSessionEnded(ENDED)` | `machine.onReply(...)` | transitions `PAUSED → RUNNING → TERMINATED`; a subsequent `step`/`eval` is rejected with `IllegalStateException`-free guarded no-op returning `false` (not sent on a dead session) | §3.5, §3.6 |
| TC-LDB-COND-1 | AC-3 | a conditional line breakpoint `condition = "x > 5"`; on pause the IDE issues `LdbCommand.Eval("x > 5")` and the parsed result is `false` (then a second pause where it is `true`) | `LuaLdbController.onPause(...)` (condition gate) | on `false` → controller auto-sends `LdbCommand.Continue` (no UI pause); on `true` → `session.breakpointReached(...)` is invoked | §3.7 |
| TC-LDB-STEPOUT-1 | AC-4 | the `LuaLdbDebugProcess` | inspect `startStepOut` and the executor/action availability | `startStepOut` is a guarded no-op (LDB has no step-out) and the Step Out affordance is reported unsupported with the tooltip text "Step Out is not supported by the Redis Lua debugger" | §2.2, §3.5 |
| TC-LDB-SYNC-1 | AC-8 | a `LuaRedisRunConfiguration` with `debugMode = SYNC` and a `provisioning = Remote` connection (not session-local) | `LuaLdbSyncGuard.requiresConfirmation(config, connection)` | returns `true` (a confirmation is required); for a `LocalBinary`/`Docker` (session-local) connection returns `false` | §3.8 |
| TC-LDB-SYNC-2 | AC-1, AC-8 | `debugMode = FORKED` vs `debugMode = SYNC` | `LdbWire.encode(LdbCommand.EnterDebug(mode))` | `["SCRIPT","DEBUG","YES"]` for FORKED; `["SCRIPT","DEBUG","SYNC"]` for SYNC | §3.2, §3.5 |
| TC-LDB-ERR-1 | AC-7 | an `LdbError(kind = EVAL_FAILED, message = "attempt to call a nil value")` produced while evaluating a watch | `LuaLdbEvaluator.evaluate(expr, callback, pos)` | `callback.errorOccurred("attempt to call a nil value")` is invoked (never `evaluated`, never a silent swallow) | §2.6, §3.7 |
| TC-LDB-ERR-2 | AC-7 | a mid-session `RespException.Io` from the transport (connection loss) | the reader coroutine surfaces the failure | `session.reportError(...)` is called and the session stops cleanly; no `ProcessCanceledException`/`IDE fatal-error` leak, no hang | §2.2, §3.6 |
| TC-LDB-BP-1 | AC-2 | a `.lua` script with a breakpoint requested on a blank/comment line vs a statement line | `LuaLdbBreakpointType.canPutAt(file, line, project)` | `false` for the blank/comment line, `true` for the statement line — reuses the REDIS-01/MobDebug `iterateLine` + `LuaStatement` check without the trailing `!!` | §2.3 |
| TC-INT-1 | AC-1, AC-2, AC-4, AC-5, AC-10 | dockerized `redis:8` **and** `valkey/valkey:8`; a 5-line script with a breakpoint on line 3 | `RedisDebugIntegrationTest`: `SCRIPT DEBUG YES` → `EVAL` → break line 3 → step → print locals → continue, on each flavor | on both flavors: the session pauses at line 3, `step` advances one line, `print` returns the expected locals, `continue` runs the script to completion and returns the reply | Phase 5 |
| TC-INT-2 | AC-6, AC-10 | dockerized `redis:8` **and** `valkey/valkey:8`; a paused session | `RedisDebugIntegrationTest`: send `LdbCommand.RedisCmd(listOf("SET","k","1"))` then `RedisCmd(listOf("GET","k"))` mid-pause | the `redis` command executes in the paused session and the `GET` returns `Bulk("1")` on both flavors | Phase 5 |
| TC-INT-3 | AC-9, AC-10 | dockerized `redis:8` **and** `valkey/valkey:8` | `RedisDebugIntegrationTest`: start a **forked** session, then `abort`; separately start and let a forked session sit idle past the server timeout | `abort` ends the session and rolls writes back (a write inside the aborted script is not visible after); the idle session surfaces `LdbSessionEnded(FORK_TIMEOUT)` (no exception) | §3.6, Phase 5 |

### AC → TC coverage matrix

| AC | Requirement | Test Case(s) |
|----|-------------|--------------|
| AC-1 | Debug executor starts an LDB session (`SCRIPT DEBUG` + `EVAL`, fork/sync) | TC-LDB-ENC-1, TC-LDB-SYNC-2, TC-LDB-DEC-1..3, TC-LDB-SM-1, TC-INT-1 |
| AC-2 | Line-breakpoint registration (`break <line>`, add/remove, invalid lines) | TC-LDB-ENC-2, TC-LDB-BP-1, TC-INT-1 |
| AC-3 | Conditional breakpoints (IDE-side `eval`, resume when false) | TC-LDB-COND-1 |
| AC-4 | Step/Resume/Stop/Rerun mapping; Step Out disabled | TC-LDB-SM-1, TC-LDB-SM-2, TC-LDB-STEPOUT-1, TC-INT-1 |
| AC-5 | Variables view from `print`; watches/Evaluate via `eval` | TC-LDB-PRINT-1, TC-LDB-PRINT-2, TC-INT-1 |
| AC-6 | Redis console tab (`redis <cmd>` mid-pause, reply tree) | TC-INT-2 (reply tree rendering reuses REDIS-01 TC-CON-1) |
| AC-7 | Error surfacing (compile/eval/connection-loss → UI, `errorOccurred`) | TC-LDB-DEC-3, TC-LDB-ERR-1, TC-LDB-ERR-2 |
| AC-8 | Sync-mode guarding (warning, confirmation, banner) | TC-LDB-SYNC-1, TC-LDB-SYNC-2; banner/dialog UX: human checklist §3 |
| AC-9 | Forked-mode banner + server-timeout as session-ended | TC-LDB-DEC-2, TC-INT-3; banner UX: human checklist §3 |
| AC-10 | Dual-flavor integration tests | TC-INT-1, TC-INT-2, TC-INT-3 |

> **Interactive-only behaviors** (not unit-testable) covered by
> [human-verification-checklists.md](human-verification-checklists.md): breakpoint-hit gutter/frame
> visuals (§1), Variables/Watch/Evaluate panels (§2), sync-mode warning + confirmation dialog and
> both session banners (§3), mid-debug Redis console tab (§4), fork-vs-sync lifecycle (§5).
