---
id: EDITOR-07
title: "07: Move Statement / Element"
type: feature
parent_id: EDITOR
status: "planned"
priority: "low"
folders:
  - "[[features/editor/requirements|requirements]]"
---
# Specification: EDITOR-07 Move Statement / Element

Structural movement that respects Lua block boundaries: Ctrl+Shift+↑/↓ moves whole statements
(and jumps in/out of blocks), Ctrl+Alt+Shift+←/→ reorders arguments and list elements.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `EDITOR-07-01` | **Move statement up/down** | Move the current statement over its sibling, preserving indentation; stops at block edges rather than corrupting `end`s. | **M** | Not Implemented |
| `EDITOR-07-02` | **Enter/leave blocks** | Moving a statement into/out of an adjacent `if`/`for`/`while`/`function` body re-indents correctly. | **S** | Not Implemented |
| `EDITOR-07-03` | **Move element left/right** | Reorder an argument in a call, a field in a table constructor, or a name in a `local a, b` list, keeping separators valid. | **S** | Not Implemented |
| `EDITOR-07-04` | **Line-move fallback** | Where no structural move applies, fall through to the platform default line mover. | **C** | Not Implemented |

## 2. Technical Details
- EPs: `com.intellij.statementUpDownMover` (`StatementUpDownMover`) and
  `com.intellij.moveLeftRightHandler` (`MoveElementLeftRightHandler`).
- The mover computes source/target `LineRange`s from statement PSI; the left/right handler returns
  the ordered sibling `PsiElement[]` (args / fields / name-list entries).
- Reference: `intellij-community` Java/Groovy `*StatementMover` and `*MoveLeftRightHandler`.
- Design pins classes, algorithms, and registration: see [design.md](design.md),
  [implementation-plan.md](implementation-plan.md), [risks-and-gaps.md](risks-and-gaps.md).

## 3. Test Cases (real-flow, `CodeInsightTestFixture`)

Each TC: `myFixture.configureByText("a.lua", before)` → `myFixture.performEditorAction(actionId)` →
`myFixture.checkResult(after)`. `<caret>` marks the caret; actions are the platform constants
`ACTION_MOVE_STATEMENT_DOWN_ACTION` / `ACTION_MOVE_STATEMENT_UP_ACTION` / `MOVE_ELEMENT_RIGHT` /
`MOVE_ELEMENT_LEFT`.

| TC | Req | Action | Input (`before`) | Expected (`after`) |
| :-- | :-- | :-- | :-- | :-- |
| TC-01a | 07-01 | Down | `local a = 1<caret>\nlocal b = 2` | `local b = 2\nlocal a = 1<caret>` |
| TC-01b | 07-01 | Down | `repeat\n  print(1)<caret>\nuntil x` | unchanged (`until` never displaced; no-op / prohibit) |
| TC-01c | 07-01 | Up | `local a = 1<caret>` (only statement) | unchanged (no-op) |
| TC-02a | 07-02 | Down | `print(1)<caret>\nif x then\n  print(2)\nend` | `if x then\n  print(1)<caret>\n  print(2)\nend` |
| TC-02b | 07-02 | Down | `if x then\n  print(1)<caret>\nend\nprint(2)` | `if x then\nend\nprint(1)<caret>\nprint(2)` |
| TC-03a | 07-03 | Right | `f(a, <caret>b, c)` | `f(a, c, <caret>b)` |
| TC-03b | 07-03 | Right | `local t = {<caret>x, y, z}` | `local t = {y, <caret>x, z}` |
| TC-03c | 07-03 | Right | `for <caret>k, v in pairs(t) do end` | `for v, <caret>k in pairs(t) do end` |
| TC-03d | 07-03 | Right | `local <caret>a, b = 1, 2` | `local b, <caret>a = 1, 2` |
| TC-04a | 07-04 | Down | caret on a blank line between two statements | plain platform line swap (`LineMover`) |
| TC-04b | 07-04 | Down | caret inside a multi-line `[[ … ]]` string | plain line swap; literal not split |

Round-trip check (mirrors `GroovyMoveLeftRightHandlerTest`): for every TC-03 case, applying
`MOVE_ELEMENT_LEFT` after `MOVE_ELEMENT_RIGHT` restores `before`.
