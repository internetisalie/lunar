---
id: "MAINT-29"
title: "29: Control-Flow & Inspection Accuracy"
type: "feature"
parent_id: "MAINT"
status: "in_progress"
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
| MAINT-29-01 | Safe quick fixes | M | Full | Rebuild `math.floor(l / r)` from operands (#8); bail/split on multi-target "Make local" (#9) — with real-flow quick-fix tests |
| MAINT-29-02 | CFG correctness | M | Full | Pending-edge mechanics, per-branch scoping, block-scoped label resolution (#32); visit conditions (#33) |
| MAINT-29-03 | Unused-local accuracy | S | Full | Exclude simple write targets (#34); `multiResolve(false)` (#69) |
| MAINT-29-04 | Concat false positives | C | Not Implemented | Respect `__concat` metamethods (#68) |

**Note:** the redundant nested-write-action wrappers in these inspections' fixes (§3) were the
subject of a same-pass cleanup — **now moot**: MAINT-31 (`status: done`) already removed them, and
`grep -rn WriteCommandAction` across all four target files returns zero hits. No work remains for
this item; the design records it and forbids re-introducing a nested write action (design §6).

## Test Cases

Inputs are Lua source configured via `myFixture.configureByText`. Quick-fix cases assert
before→after buffer text via `findSingleIntention` + `launchAction` + `checkResult` (not a bare
`getAllQuickFixes` presence check). CFG cases assert on `ControlFlowCache.getControlFlow` +
`isReachable`; inspection cases on `enableInspections` + `doHighlighting`.

| TC | Requirement | Input | Action | Expected |
| :-- | :-- | :-- | :-- | :-- |
| TC-01 | MAINT-29-01 (#8) | `local n = 7 // 2` @ Lua 5.1 | Apply "Replace // with / and math.floor()" | Buffer = `local n = math.floor(7 / 2)` |
| TC-02 | MAINT-29-01 (#8) | `local n = (a+b) // c` @ Lua 5.1 | Apply the fix | Buffer = `local n = math.floor((a+b) / c)` (operands/parens preserved) |
| TC-03 | MAINT-29-01 (#9) | `x = 1` (single simple target global) | Query intentions | "Make Local" **is** offered; applying yields `local x = 1` |
| TC-04 | MAINT-29-01 (#9) | `x, t.f = 1, 2` | Query intentions | "Make Local" is **not** offered (only "Add to globals"); no invalid `local x, t.f = ...` |
| TC-05 | MAINT-29-02 (#32a) | `function f(x) if x then return 1 else return 2 end print("u") end` | Build CFG for `f` | `print("u")` instruction `isReachable == false` (no spurious edge out of the returns) |
| TC-06 | MAINT-29-02 (#32c) | Two sibling `for` loops each with `::continue::` and `goto continue` | Build CFG | Each `goto continue` edges to its **own** loop's label (no cross-wire) |
| TC-07 | MAINT-29-02 (#33) | `local a = 1 if a then end` | Build CFG for file | A READ `LuaReadWriteInstruction` with `variableName == "a"` exists for the condition |
| TC-08 | MAINT-29-02 (#32b) | `if c1 then return elseif c2 then return end print("r")` (both branches abrupt only when reached) | `doHighlighting` | Reachability of `print("r")` matches Lua semantics (no elseif-leak false negative) |
| TC-09 | MAINT-29-03 (#34) | `local flag; flag = true` (no read of `flag`) | `doHighlighting` | "Unused local variable 'flag'" reported |
| TC-10 | MAINT-29-03 (#69) | Shadowed locals where a usage resolves ambiguously to two decls | `doHighlighting` | Neither decl falsely flagged unused |
| TC-11 | MAINT-29-04 (#68) | `---@class Vec` with `Vec.__concat`; `local v = ... .. Vec_instance` | `doHighlighting` | No "Suspicious concatenation" on the `__concat` operand (membership via `localMembers`/`resolveMember` per DR-01 — NOT the nonexistent `fields`); a plain `{}` operand **is** flagged |
| TC-12 | MAINT-29-02 (#32c) | `do ::top:: for i=1,2 do goto top end end` (goto targets a label in an ENCLOSING block) | Build CFG | The `goto top` edges to the enclosing block's `::top::` label (ascent through parent blocks resolves; no unresolved-goto) |
