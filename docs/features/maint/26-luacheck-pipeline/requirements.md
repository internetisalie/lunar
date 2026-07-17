---
id: "MAINT-26"
title: "26: Luacheck Pipeline Correctness"
type: "feature"
parent_id: "MAINT"
status: "in_progress"
priority: "medium"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-26: Luacheck Pipeline Correctness

Coalesces the luacheck-integration defects from [`docs/review.md`](../../../review.md)
(re-verified 2026-07-17): command-line corruption, fragile output parsing, disk/editor offset
skew, suppression-scope bugs, and the error-swallowing/cancellation gaps the review's systemic
sections flagged for this subsystem.

## Absorbed review findings

Re-verified against the current tree (2026-07-17). MAINT-31 already migrated the transport onto
`LuaToolExecutionService`, so several findings shifted or were partly resolved — the **current**
file:line and residual state are recorded here (full evidence in [design.md](design.md) §1).

| Review # | Defect | Current file:line | State now |
| :--- | :--- | :--- | :--- |
| 28 | `args.distinct()` on the flat token list drops repeated value tokens — corrupts the command | `LuaCheckCommandLine.kt:23` | Live |
| 29 | First-match-per-chunk parse requiring trailing `\n` | `LuaCheckInvoker.kt:28-30` | Already fixed (now `stdout.lineSequence().mapNotNull`); §3.2 formalizes + hardens |
| 30 | Runs against the on-disk file while offsets index the editor document — misplaced ranges; IOOBE kills the pass | `LuaCheckAnnotator.kt:43-44` | Live |
| 31 | `---@diagnostic enable: <unrelated>` closes **all** open disable blocks (ignores names) | `LuaInspectionSuppression.kt:112-124` | Partly fixed — `closeBlocks` takes `names` but never applies the intersection; §3.4 completes it |
| 60 | Inline `-- luacheck: ignore` over-suppresses the following line | `LuaInspectionSuppression.kt:135` | Live |
| 61 | Lua 5.1 global `arg` missing from `DELTA_51` — false "undeclared" | `LuaStandardGlobals.kt:23-25` | Live |
| §2.1 | 5 s `waitFor` neither kills the process nor polls the indicator; PSI read from the process listener | `LuaToolExecutionService.kt:66-74`; `LuaCheckInvoker.kt:16-31` | Structurally fixed — exec service kills on timeout, polls the indicator, has no `ProcessListener`; invoker reads PSI before launch, not in a listener |
| §2.5.6 | Launch failure & exit ≥ 2 read as clean | `LuaCheckInvoker.kt:22-31` | Live — `capture` returns `outcome`/`exitCode`, but the invoker reads only `.stdout` and has a dead `catch (ExecutionException)` |

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-26-01 | Command-line fidelity | M | Full | Replace flat-token `distinct()` with pair-aware de-dup preserving repeated value tokens (#28) |
| MAINT-26-02 | Robust output parsing | M | Not Implemented | Formalize accumulate-and-split parse over the full captured stdout; ANSI-strip; ignore non-matching lines (#29) |
| MAINT-26-03 | Editor-accurate offsets | M | Not Implemented | Feed the live editor document via stdin (`luacheck - --filename <name>`); clamp all offset math to the document (#30) |
| MAINT-26-04 | Suppression scoping | S | Not Implemented | Close only name-intersecting disable blocks on `enable` (#31); scope inline `-- luacheck: ignore` to its own line (#60) |
| MAINT-26-05 | Stdlib accuracy | S | Full | Add `arg` (stand-alone script args table) to `DELTA_51` (#61) |
| MAINT-26-06 | Process hygiene | S | Not Implemented | Classify `LuaExecResult.outcome`/`exitCode`; surface launch failure & exit ≥ 2 as a WARNING annotation + log; keep all PSI reads out of `doAnnotate` (§2.5.6; §2.1 residual) |

## Test Cases

Each `Must` (and each `Should`) has a concrete input → output case. Pure-function cases are unit
tests; annotator cases run under `BasePlatformTestCase` (`configureByText` + the DR-02 fake-exec seam).

| TC | Requirement | Input | Expected output |
| :--- | :--- | :--- | :--- |
| TC1 | 26-01 | `dedupePairs(["--ignore","611","--max-line-length","611","--codes","--ranges"])` | `["--ignore","611","--max-line-length","611","--codes","--ranges"]` (both `611`s kept) |
| TC2 | 26-01 | `dedupePairs(["--codes","--codes","--std","max","--std","max"])` | `["--codes","--std","max"]` (duplicate lone flag and duplicate pair collapsed) |
| TC3 | 26-03 | Unsaved buffer `local x = 1\n`; fake luacheck emits `test.lua:1:7-7: (W211) unused variable 'x'` | One WARNING annotation over `TextRange(6,7)` (the `x`) — placed correctly on the never-saved buffer |
| TC4 | 26-03 | Document has 1 line; fake luacheck emits a problem at line `5` | No `IndexOutOfBoundsException`; the range is clamped into the single line |
| TC5 | 26-06 | Resolved luacheck path does not exist → `capture` → `START_FAILED` | Exactly one file-wide WARNING annotation "Could not execute luacheck"; `LOG.warn` called; no silent green |
| TC6 | 26-04 | `---@diagnostic disable: undefined-global` … `---@diagnostic disable: unused` … `---@diagnostic enable: undefined-global` | The `undefined-global` block closes at the enable; the `unused` block stays open past it |
| TC7 | 26-04 | `local y = 1 -- luacheck: ignore` on line 1; an `undefined-global` on line 2 | Line 2 is **not** suppressed (inline ignore is line-1-only) |
| TC8 | 26-05 | `LuaStandardGlobals.contains("arg", LUA51)` | `true` (also `LUA50`) |
| TC9 | 26-05 | `LuaStandardGlobals.contains("arg", LUA54)` | `false` (5.4 has no global `arg`; unchanged) |
| TC10 | 26-06 | `classify(LuaExecResult(outcome=START_FAILED), "f.lua")` | `Failure(LAUNCH_FAILED, ...)` |
| TC11 | 26-06 | `classify(LuaExecResult(outcome=COMPLETED, exitCode=2, stderr="bad std"), "f.lua")` | `Failure(CRASHED, "bad std")` |
| TC12 | 26-02, 26-06 | `classify` of `COMPLETED` exit 1 with two problem lines + one summary line in stdout | `Problems([<2 parsed problems>])` (summary line ignored) |
