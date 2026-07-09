---
id: EDITOR-05
title: "05: Surround With"
type: feature
parent_id: EDITOR
status: "planned"
priority: "medium"
folders:
  - "[[features/editor/requirements|requirements]]"
---
# Specification: EDITOR-05 Surround With

Real `Surround With` action (Ctrl+Alt+T): wrap a selection of statements in a Lua block construct,
re-indenting the body. Complements — does not duplicate — the four `$SELECTION$` live-template
surrounds shipped in [`COMP-07`](../../completion/07-live-templates/requirements.md); this is the
`SurroundDescriptor` EP with a proper picker and caret placement.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `EDITOR-05-01` | **`if … end`** | Wrap the selected statements in `if <caret> then … end`, caret in the condition, body re-indented. | **M** | Not Implemented |
| `EDITOR-05-02` | **`while` / `for`** | Wrap in `while <caret> do … end` and numeric/generic `for … do … end` variants. | **S** | Not Implemented |
| `EDITOR-05-03` | **`function`** | Wrap in an anonymous `function() … end` (optionally IIFE-invoked). | **S** | Not Implemented |
| `EDITOR-05-04` | **`do … end`** | Wrap in a bare `do … end` scope block. | **S** | Not Implemented |
| `EDITOR-05-05` | **`pcall`** | Wrap in `pcall(function() … end)` capturing the protected body. | **C** | Not Implemented |

## 2. Technical Details
- EP: `com.intellij.lang.surroundDescriptor` (`SurroundDescriptor` + one `Surrounder` per template).
- Operates on the statement list PSI (`getElementsToSurround` returns whole statements); performs the
  edit in a `WriteCommandAction` and reformats via the existing formatter.
- Introduces block-structure PSI helpers reused by `EDITOR-06` (Unwrap).
- Reference: `intellij-community` Java `*SurroundDescriptor`/`*Surrounder`.
