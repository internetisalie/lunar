---
id: "MAINT-24"
title: "24: Debugger & Test-Runner Hardening"
type: "feature"
parent_id: "MAINT"
status: "todo"
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
| MAINT-24-01 | DBGp byte-accurate framing | M | Not Implemented | Read exactly N raw bytes then decode UTF-8; explicit charset on reader (#5) |
| MAINT-24-02 | Crash-proof payload parsing | M | Not Implemented | Remove `!!` from debugger payload paths; malformed data degrades gracefully; drop the rd-gen internal import (#7, #16, #17, §2.2) |
| MAINT-24-03 | Thread-safe controller state | M | Not Implemented | Concurrent maps + `@Volatile`/locking for breakpoint & connection state (#18) |
| MAINT-24-04 | Run to Cursor | S | Not Implemented | Implement via temporary SETB + RUN + DELB (#6) |
| MAINT-24-05 | Value/stack fidelity | S | Not Implemented | Positional index lookup (#52), `firstFrameIndex` (#53), 1-based line display (#59) |
| MAINT-24-06 | Run-config integrity | S | Not Implemented | Source-path write-back (#26), `checkConfiguration()` + basePath fallback (#56) |
| MAINT-24-07 | Busted runner correctness | S | Not Implemented | Lua-pattern rerun filter (#27), JSON scanner `"`-only delimiters (#54), streaming output (§2.5.7) |
| MAINT-24-08 | Robustness pass | C | Not Implemented | Configurable port, remove sleep-based races, graceful EXIT on stop (§2.5.7, §3) |

**Regression gate:** full suite + the REDIS-02 LDB integration tests (shared debugger seams);
live VNC debug-flow verification per MAINT-22's DoD precedent.
