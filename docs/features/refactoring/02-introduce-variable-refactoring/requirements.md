---
id: "REFACT-02"
title: "02: Introduce Variable Refactoring"
type: "feature"
status: "done"
priority: "medium"
parent_id: "REFACT/INTENT"
folders: ["[[features/refactoring/requirements|requirements]]"]
---

# REFACT-02: Introduce Variable Refactoring

Extract a selected expression into a `local` variable, replacing the occurrence(s).

## Scope
- **In Scope**: Extract an expression to `local <name> = <expr>` inserted before the enclosing
  statement; replace the selected occurrence; optionally replace all occurrences in scope; name
  suggestion + inline rename of the new variable.
- **Out of Scope**: introduce field/parameter/constant (separate refactorings).

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| `REFACT-02-01` | **Extract expression** | **M** | Full | Insert `local <name> = <selected expr>` before the enclosing statement and replace the selection with `<name>`. |
| `REFACT-02-02` | **Replace all occurrences** | **S** | Full | Offer to replace all identical occurrences of the expression in the enclosing scope. |
| `REFACT-02-03` | **Name suggestion + inline rename** | **S** | Full | Suggest a name and start an inline rename template on the introduced variable. |

## Test Cases

### TC-REFACT-02-01: Single occurrence
- **Input**: `print(1 + 2)` with `1 + 2` selected.
- **Action**: Introduce Variable (`RefactoringActionHandler.invoke`).
- **Output**:
  ```lua
  local sum = 1 + 2
  print(sum)
  ```

### TC-REFACT-02-02: Replace all
- **Input**: `print(x*2); return x*2` with `x*2` selected, "replace all" chosen.
- **Action**: Introduce Variable.
- **Output**: both `x*2` replaced by the new `local`.

### TC-REFACT-02-03: Inside a block
- **Input**: `if a then return f(a) end` with `f(a)` selected.
- **Action**: Introduce Variable.
- **Output**: `local <name> = f(a)` inserted inside the `if` block before `return`.
