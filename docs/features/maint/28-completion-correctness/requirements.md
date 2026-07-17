---
id: "MAINT-28"
title: "28: Completion Correctness & Performance"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "medium"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-28: Completion Correctness & Performance

Coalesces the completion-stack defects from [`docs/review.md`](../../../review.md) (re-verified
2026-07-17): the copy-file/original-file confusion that silently disables the cross-file phase,
duplicate symbol passes, ranking mis-extraction, and the per-session performance sinks the
review's systemic analysis attributed to this stack.

## Absorbed review findings

| Review # | Defect |
| :--- | :--- |
| 24 (rest) | Uses the completion **copy** file, not `parameters.originalFile` — index queries hit a never-indexed file (cross-file phase returns nothing); proximity tiers unreachable. (`extractRequires` caching already fixed.) |
| 25 | `LuaEnterBetweenBlockHandler` guard unsatisfiable — COMP-08-04 never fires |
| 39 | `addSymbolCompletions` invoked up to 3× per completion (three call sites) |
| 40 | `extractFuncDeclName` surfaces method *receivers* as standalone global candidates |
| 62 | `hasPrefix` cannot detect a typed prefix (dummy-identifier merge); shadowed variable |
| §2.5.5 | Full snapshot incl. `checkTypes` per completion session; `StubIndex.getAllKeys` ×2 per invocation (cache on `PsiModificationTracker`) |

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-28-01 | Original-file discipline | M | Not Implemented | Index/proximity work against `parameters.originalFile` (#24) |
| MAINT-28-02 | Single symbol pass | S | Not Implemented | One `addSymbolCompletions` call site; fold the identifier provider in (#39) |
| MAINT-28-03 | Ranking accuracy | S | Not Implemented | Skip method-separator decls in global ranking (#40); fix `hasPrefix` (#62) |
| MAINT-28-04 | Enter-between-blocks | C | Not Implemented | Fix the off-by-one guard (#25) — real-flow DoD test required |
| MAINT-28-05 | Session cost | S | Not Implemented | Modification-tracked key caching; avoid full `checkTypes` snapshot per session (§2.5.5) |

**DoD note:** completion features gate on real-flow tests (`completeBasic()`), per the roadmap's
DoD clause — engine-only tests hid exactly this class of bug (#24) for a full wave.
