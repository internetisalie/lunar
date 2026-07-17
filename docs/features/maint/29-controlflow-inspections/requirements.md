---
id: "MAINT-29"
title: "29: Control-Flow & Inspection Accuracy"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "medium"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-29: Control-Flow & Inspection Accuracy

Coalesces the control-flow-graph defects and inspection/quick-fix correctness bugs from
[`docs/review.md`](../../../review.md) (re-verified 2026-07-17). Headline items are the two
**destructive quick fixes** (#8, #9) — user-facing actions that produce invalid Lua.

## Absorbed review findings

| Review # | Defect |
| :--- | :--- |
| 8 | `ReplaceIntegerDivisionFix` replaces the `//` operator leaf — produces `a math.floor(/) b` |
| 9 | "Make local" prepends `local ` to multi-target assignments — invalid syntax |
| 32 | CFG: spurious fall-through edges out of `return`; pending-edge leak into `elseif`; flat label map cross-wires sibling `::continue::` loops |
| 33 | Conditions never descended into — no READ instructions for names in conditions |
| 34 | Write-position references count as usages — assigned-only locals never flagged unused |
| 68 | All `Table` types deemed non-concatenable — false positive for `__concat` classes |
| 69 | `resolve()` on poly-variant refs drops ambiguous usages — false "unused" risk |

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-29-01 | Safe quick fixes | M | Not Implemented | Rebuild `math.floor(l / r)` from operands (#8); bail/split on multi-target "Make local" (#9) — with real-flow quick-fix tests |
| MAINT-29-02 | CFG correctness | M | Not Implemented | Pending-edge mechanics, per-branch scoping, block-scoped label resolution (#32); visit conditions (#33) |
| MAINT-29-03 | Unused-local accuracy | S | Not Implemented | Exclude simple write targets (#34); `multiResolve(false)` (#69) |
| MAINT-29-04 | Concat false positives | C | Not Implemented | Respect `__concat` metamethods (#68) |

**Note:** the redundant nested-write-action wrappers in these inspections' fixes (§3) should be
removed in the same pass (intention-preview hazard).
