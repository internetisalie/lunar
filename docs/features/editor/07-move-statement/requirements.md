---
id: EDITOR-07
title: "07: Move Statement / Element"
type: feature
parent_id: EDITOR
status: "todo"
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
