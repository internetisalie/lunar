---
id: "BUG-414"
title: "Debugger variable navigation walks PSI with no read action"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-414: `LuaDebugVariable.computeSourcePosition` reads PSI without a read action

*Source: codebase review [`docs/review.md`](../../../review.md) finding **#73** (P1, second pass
2026-08-06).*

## 1. Reproduction

1. Start a Lua debug session and pause at a breakpoint.
2. In the Variables pane, invoke **Jump to Source** on a local.
3. While the position is being computed, edit the source file (any write action races the walk).

The window is small, so this reproduces intermittently; the defect is visible by inspection
regardless.

## 2. Expected vs Actual Behavior

- **Expected**: all PSI access happens inside `runReadAction { }` (engineering contract rule 1 —
  "READING PSI: Use `runReadAction { }`").
- **Actual**: `run/LuaDebugVariable.kt:84-130` performs, with no read action anywhere in the
  method:
  - `XDebuggerUtil.getInstance().findContextElement(...)` (`:93`),
  - a full `processDeclarations` scope walk up the PSI tree, block by block (`:104-125`),
  - `XDebuggerUtil.getInstance().createPositionByElement(processor.result)` (`:128`).

  `computeSourcePosition` is a platform callback invoked off the EDT and is not guaranteed to
  hold a read lock. Under a concurrent write the walk can see an inconsistent tree or throw.

## 3. Notes

Sibling defect worth fixing in the same change (review #87, `run/LuaStackFrame.kt:59-85`): that
method wraps its `LuaDebugVariable`/`LuaDebugValue` construction in `runReadAction` although the
block touches no PSI or VFS at all. The subsystem has the read action exactly where it is not
needed and omits it where it is — fixing only one half leaves the pattern looking deliberate.

While in the file, review #82 applies to the same class: `isIndex` is `false` at every
construction site and `parent` is never read; both exist only for the commented-out
`evaluationExpression` block at `:132-138`. Either delete all three or restore the feature —
`XNamedValue.getEvaluationExpression()` is left at its default, so check first whether "Add to
Watches" on a nested table child currently works; if it does not, restoring the block is the fix
and the dead fields become live again.
