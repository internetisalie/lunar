---
id: EDITOR-06
title: "06: Unwrap / Remove"
type: feature
parent_id: EDITOR
status: "todo"
priority: "medium"
folders:
  - "[[features/editor/requirements|requirements]]"
---
# Specification: EDITOR-06 Unwrap / Remove

The inverse of `EDITOR-05`: Ctrl+Shift+Delete opens an "Unwrap/Remove" picker that either removes a
surrounding block (hoisting its body to the parent scope) or deletes the construct entirely, with a
live preview highlight.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `EDITOR-06-01` | **Unwrap block** | Remove the enclosing `if`/`while`/`for`/`do`/`function` keyword+`end`, hoisting the body to the parent, re-indented. | **M** | Not Implemented |
| `EDITOR-06-02` | **Unwrap `else`/`elseif`** | Collapse an `if/else` branch appropriately (remove branch, keep the chosen body). | **S** | Not Implemented |
| `EDITOR-06-03` | **Remove construct** | Delete the whole construct including its body. | **S** | Not Implemented |
| `EDITOR-06-04` | **Preview highlight** | Each offered option highlights the affected range in the editor before the user confirms. | **S** | Not Implemented |

## 2. Technical Details
- EP: `com.intellij.lang.unwrapDescriptor` (`UnwrapDescriptor` + `Unwrapper` per construct).
- Reuses the block-structure PSI helpers introduced by `EDITOR-05` (soft dependency, shared code —
  not a blocking edge).
- Edits under `WriteCommandAction`; body hoist must preserve local scoping and reformat.
- Reference: `intellij-community` Java `*UnwrapDescriptor`/`*Unwrapper`.
