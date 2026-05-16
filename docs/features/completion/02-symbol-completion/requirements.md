---
folders:
  - "[[features/completion/requirements|requirements]]"
title: "02: Basic Symbol Completion"
priority: high
status: planned
---
# COMP-02: Basic Symbol Completion Requirements

Suggest local variables, parameters, and global symbols within the current scope.

## Scope

### In Scope
- Completion for local variables defined in the current function or outer blocks.
- Completion for function parameters.
- Completion for global symbols defined in the current file.
- Support for basic shadowing rules (suggest the closest definition).
- Trigger completion in expression contexts (assignments, function arguments, etc.).

### Out of Scope
- Cross-file completion (covered by COMP-03).
- Member completion (table.field) (covered by COMP-04).
- Type-based filtering (covered by COMP-04).

## Requirements Table

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `COMP-02-01` | **Local Variable Completion** | **M** | Suggest locals defined with `local` keyword in current or parent scopes. |
| `COMP-02-02` | **Parameter Completion** | **M** | Suggest parameters of the containing function. |
| `COMP-02-03` | **Local Shadowing** | **M** | Correctly handle shadowing; do not suggest shadowed locals. |
| `COMP-02-04` | **Global Symbol Completion** | **M** | Suggest globals defined in the current file. |
| `COMP-02-05` | **Iconography** | **S** | Display distinct icons for locals, parameters, and globals in the completion list. |
| `COMP-02-06` | **Scope Hints** | **S** | Display the origin scope (e.g., "local", "parameter") in the completion tail text to clarify shadowed or similar names. |

## Test Cases

| ID | Action | Expected Output |
| :--- | :--- | :--- |
| `TC-01` | Type `lo` inside a function where `local local_var = 1` is defined. | `local_var` appears in completion list. |
| `TC-02` | Type `pa` inside `function(param1)` | `param1` appears in completion list. |
| `TC-03` | Type `sh` in a scope where an inner `local shadow` hides an outer one. | Completion should point to the inner `shadow` definition. |
