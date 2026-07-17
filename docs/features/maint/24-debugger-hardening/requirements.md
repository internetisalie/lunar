---
id: "MAINT-24"
title: "24: Debugger & Test-Runner Hardening"
type: "feature"
parent_id: "MAINT"
status: "done"
priority: "medium"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-24: Debugger & Test-Runner Hardening

Coalesces the remaining DBGp-debugger, run-configuration, and busted test-runner defects from
[`docs/review.md`](../../../review.md) (2026-07 codebase review; remediation status re-verified
2026-07-17) into one hardening feature. This is the **"MobDebug hardening" prerequisite the
roadmap already names for AI-03** (Debugger Toolset). REDIS-02's LDB work already fixed the
evaluator error path (#13) and the connect timeout (#55), and removed the breakpoint spin-wait
(#4); this feature finishes the job on the DBGp side.

## Absorbed review findings

| Review # | Defect |
| :--- | :--- |
| 5 | DBGp framing byte/char confusion — multi-byte UTF-8 desyncs the protocol permanently |
| 6 | "Run to Cursor" throws `AbstractMethodError` |
| 7 | `nullIfEmpty` imported from an rd-gen codegen internal — `NoClassDefFoundError` risk on value presentation |
| 16 | `FileUtil.getRelativePath(...)!!` NPE kills breakpoint registration |
| 17 | `checkTable()!!` / `exprList[index]` crashes on malformed stack payloads |
| 18 | Unsynchronized breakpoint maps + non-volatile connection state (EDT vs connection thread race) |
| 26 | Run config `applyEditorTo` never persists the Source-path field |
| 27 (rest) | Rerun-failed-tests filter uses Java `Regex.escape` — busted takes Lua patterns (action itself now wired) |
| 52 | Numeric bracket indexing ignores `table.indexed` — `t[1]` evaluates to nil |
| 53 (rest) | `computeStackFrames` ignores `firstFrameIndex` (duplicate frames) |
| 54 | `findTopLevelJson` treats `'` as a JSON string delimiter — apostrophes swallow the test report |
| 56 | No `checkConfiguration()`; empty `workingDirectory` passed verbatim |
| 59 | Breakpoint `getDisplayText` prints 0-based line |
| §2.2 | `!!` sweep across debugger payload parsing (~20 sites) — malformed remote data must not crash |
| §2.5.7 | Robustness pass: hard-coded port 8172, post-connect `Thread.sleep(1000)`, buffered-until-exit busted output |

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-24-01 | DBGp byte-accurate framing | M | Full | Read exactly N raw bytes then decode UTF-8; explicit charset on reader (#5) |
| MAINT-24-02 | Crash-proof payload parsing | M | Full | Remove `!!` from debugger payload paths; malformed data degrades gracefully; drop the rd-gen internal import (#7, #16, #17, §2.2) |
| MAINT-24-03 | Thread-safe controller state | M | Full | Controller breakpoint maps become private `ConcurrentHashMap`s (lock-free, coroutine-idiomatic — design §3.3 explicitly rejects bolting `synchronized`/`@Volatile` onto the coroutine model); connection state is already `@Volatile` since MAINT-22, no new locking (#18) |
| MAINT-24-04 | Run to Cursor | S | Full | Implement via temporary SETB + RUN + DELB (#6) |
| MAINT-24-05 | Value/stack fidelity | S | Full | Positional index lookup (#52), `firstFrameIndex` (#53), 1-based line display (#59) |
| MAINT-24-06 | Run-config integrity | S | Full | Source-path write-back (#26), `checkConfiguration()` + basePath fallback (#56) |
| MAINT-24-07 | Busted runner correctness | S | Full | Lua-pattern rerun filter (#27), JSON scanner `"`-only delimiters (#54), streaming output (§2.5.7) |
| MAINT-24-08 | Robustness pass | C | Full | Configurable port, remove sleep-based races, graceful EXIT on stop (§2.5.7, §3) |

## Scope

### In Scope
- `src/main/kotlin/net/internetisalie/lunar/run/**` — DBGp transport, payload parsing, run
  configurations, and the busted/lunity test runner.
- `src/main/lua/lunar/debug.lua` — mobdebug preloader (configurable port only).

### Out of Scope
- Type engine, lexer/parser, LuaCATS, luacheck, completion (MAINT-25/26/27/28).
- The REDIS-02 LDB debugger (`redis/debug/**`) — untouched except that the shared regression gate
  runs its integration tests.
- The ~20 non-`run/` `!!` sites from review §2.2 (owned by other MAINT features).

## Test Cases

Concrete input→output cases per requirement. `TC-*` are automated (unit); `HV-*` are
`human-verification-checklists.md` scenarios (VNC-gated, MAINT-22 DoD precedent).

| TC | Req | Input | Action | Expected |
| :-- | :-- | :-- | :-- | :-- |
| TC-01a | 01 | `ByteArrayInputStream` of a 6-byte UTF-8 payload `café!` (5 chars, 6 bytes) with byte-count 6 | `DbgpFraming.readExactly(input, 6)` | returns `"café!"` (no under-read) |
| TC-01b | 01 | bytes `abc\r\ndef\n` | two `DbgpFraming.readLine` | `"abc"`, then `"def"` (CR skipped) |
| TC-01c | 01 | stream that EOFs after 3 of 6 bytes | `readExactly(input, 6)` | throws `IOException` "connection closed after 3 of 6 bytes" |
| HV-04 | 01,04 | live debug session, variable `x = "café"`, Run to Cursor | pause at breakpoint / cursor | variable panel shows `café`; Run to Cursor pauses at line, no `AbstractMethodError` dialog |
| TC-02a | 02 | `LuaValue(kind=Function)` as a table key | build `LuaDebugValue` children | key renders `[function]` via `toDisplayString`, no crash |
| TC-02b | 02 | breakpoint file on a different root than `workingDir` | `LuaPosition.createRemotePosition` | returns the absolute path (no NPE) |
| TC-02c | 02 | remote stack chunk `local a, b = 1` | `LuaRemoteResultFactory.create` | no `IndexOutOfBoundsException`; `b` skipped |
| TC-02d | 02 | — | `grep -c '!!' run/` | 0 |
| TC-03a | 03 | breakpoint toggled while reader thread reads maps | inspection + HV-04 | `myBreakpoints2Pos`/`myPos2Breakpoints` are `ConcurrentHashMap`, private |
| TC-05a | 05 | table `{10, 20}`, expression `t[1]` | `evaluateVarSuffixIndex` | `10` (positional `indexed[0]`), not nil |
| TC-05b | 05 | stack with 3 frames, `firstFrameIndex = 1` | `computeStackFrames` | frames 1..2 only (frame 0 dropped) |
| TC-05c | 05 | C frame `{ nil, "=[C]", … }` | `computeStackFrames` | recognized as internal-C frame |
| TC-05d | 05 | breakpoint at 0-based line 41 | `getDisplayText` | `"Line 42 in file …"` |
| TC-06a | 06 | editor sourcePath `foo;bar`, Apply | `applyEditorTo` then reload | `runConfiguration.sourcePath == "foo;bar"` |
| TC-06b | 06 | run config with empty `workingDirectory` | `getState.startProcess` | work dir = `project.basePath` |
| TC-06c | 06 | run config with no runtime | `checkConfiguration()` | throws `RuntimeConfigurationException` |
| TC-07a | 07 | failed tests `["a.b", "c-d"]` | `configureBustedTargets` | two params `--filter=a%.b`, `--filter=c%-d` |
| TC-07b | 07 | busted stdout containing `print("doesn't")` around the JSON report | `findTopLevelJson` | full JSON object returned intact |
| HV-06 | 07 | live busted run with `print` output | run tests | output appears live in console during the run |
| TC-08a | 08 | run config `debugPort = 9000` | apply/reload + `getState` | option round-trips 9000; `MOBDEBUG_PORT=9000` on the command line |
| HV-07 | 08 | debug session on port 9000 | start debug | mobdebug binds 9000; breakpoint hits |
| HV-08 | 08 | active debug session | Stop | debuggee receives `EXIT` (graceful), process ends |

**Regression gate:** full suite (baseline 2123 tests / 0 failures / 1 skipped, `main` 2026-07-17) +
the REDIS-02 LDB integration tests (shared debugger seams); live VNC debug-flow verification per
MAINT-22's DoD precedent.
