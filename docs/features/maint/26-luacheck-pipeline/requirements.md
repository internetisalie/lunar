---
id: "MAINT-26"
title: "26: Luacheck Pipeline Correctness"
type: "feature"
parent_id: "MAINT"
status: "todo"
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

| Review # | Defect |
| :--- | :--- |
| 28 | `args.distinct()` on the flat token list drops repeated value tokens — corrupts the command |
| 29 | Output listener parses first-match-per-chunk with trailing-`\n` requirement — loses problems |
| 30 | Runs against the on-disk file while offsets index the editor document — misplaced ranges; IOOBE kills the pass |
| 31 | `---@diagnostic enable: <unrelated>` closes **all** open disable blocks (ignores names) |
| 60 | Inline `-- luacheck: ignore` over-suppresses the following line |
| 61 | Lua 5.1 global `arg` missing from `DELTA_51` — false "undeclared" |
| §2.1 | 5 s `waitFor` neither kills the process nor polls the indicator; PSI read from the process listener without a read action |
| §2.5.6 | `ExecutionException` swallowed silently — missing binary reads as "no problems"; exit ≥ 3 reads as clean |

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-26-01 | Command-line fidelity | M | Not Implemented | De-dupe whole flag-value pairs, not flat tokens (#28) |
| MAINT-26-02 | Robust output parsing | M | Not Implemented | Buffer + split on newlines + `findAll`; parse remainder at termination (#29) |
| MAINT-26-03 | Editor-accurate offsets | M | Not Implemented | Feed editor text via stdin (`luacheck -`) or clamp against the current document (#30) |
| MAINT-26-04 | Suppression scoping | S | Not Implemented | Name-intersection block closing (#31); same-line-only inline ignore (#60) |
| MAINT-26-05 | Stdlib accuracy | S | Not Implemented | Add `arg` to `DELTA_51` (#61) |
| MAINT-26-06 | Process hygiene | S | Not Implemented | Cancellable wait + kill on timeout; surface launch failures & crash exit codes; PSI reads under read action (§2.1, §2.5.6) |
