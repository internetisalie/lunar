---
id: "REFACT-03"
title: "03: Safe Delete Refactoring"
type: "feature"
status: "done"
priority: "medium"
parent_id: "REFACT/INTENT"
folders: ["[[features/refactoring/requirements|requirements]]"]
---

# REFACT-03: Safe Delete Refactoring

Delete a symbol declaration only after confirming it has no remaining usages.

## Scope
- **In Scope**: Safe Delete for local variables, parameters, and global function/variable
  declarations; pre-delete usage search; conflict dialog when usages remain.
- **Out of Scope**: cascading delete of dependent declarations; field-level safe delete.

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| `REFACT-03-01` | **Enable for declarations** | **M** | Full | Safe Delete is offered on local/parameter/global symbol declarations. |
| `REFACT-03-02` | **Usage search** | **M** | Full | Before deleting, search for usages (via the references engine); if none, delete the declaration. |
| `REFACT-03-03` | **Conflict prompt** | **S** | Full | If usages remain, show the standard "usages found" dialog before proceeding. |

## Test Cases

### TC-REFACT-03-01: Unused local deleted
- **Input**: `local x = 1` with no usages.
- **Action**: Safe Delete on `x`.
- **Output**: the `local x = 1` statement is removed; no prompt.

### TC-REFACT-03-02: Used local prompts
- **Input**: `local x = 1; print(x)`.
- **Action**: Safe Delete on `x`.
- **Output**: the "1 usage found" conflict dialog is shown (deletion not silent).

### TC-REFACT-03-03: Unavailable target
- **Input**: caret on a keyword/literal.
- **Action**: query `isSafeDeleteAvailable`.
- **Output**: `false` (Safe Delete not offered).
